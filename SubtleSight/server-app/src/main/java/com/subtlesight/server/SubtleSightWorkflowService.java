package com.subtlesight.server;

import com.subtlesight.application.SubtleSightFacade;
import com.subtlesight.application.Ports.BlobStore;
import com.subtlesight.application.Ports.IntelligenceRepository;
import com.subtlesight.connectors.ConnectorRegistry;
import com.subtlesight.connectors.SourceConnector;
import com.subtlesight.connectors.SourceConnector.RawPayload;
import com.subtlesight.domain.CanonicalUrl;
import com.subtlesight.domain.Hashing;
import com.subtlesight.discovery.DiscoveryEngine;
import com.subtlesight.discovery.DiscoveryPlanner;
import com.subtlesight.document.DocumentProcessor;
import com.subtlesight.domain.Models.*;
import com.subtlesight.jobs.DurableJobQueue;
import com.subtlesight.report.ReportService;
import com.subtlesight.research.DeepResearchService;
import com.subtlesight.signal.SavedViewParser;
import com.subtlesight.signal.SignalEngine;
import com.subtlesight.story.EntityTopicExtractor;
import com.subtlesight.story.StoryClusterEngine;
import com.subtlesight.watchlist.WatchlistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class SubtleSightWorkflowService {
    private final SubtleSightFacade facade;private final IntelligenceRepository repository;private final BlobStore blobs;private final ConnectorRegistry connectors;
    private final DocumentProcessor documents;private final StoryClusterEngine clusters;private final EntityTopicExtractor entities;private final SignalEngine signals;
    private final DiscoveryPlanner planner;private final DiscoveryEngine discovery;private final DeepResearchService research;private final WatchlistService watchlist;
    private final ReportService reports;private final DurableJobQueue jobs;private final ObjectMapper json;private final Clock clock;private final SseHub sse;private final int maxItemsPerSource;private final boolean openCliVideoEnabled;
    public SubtleSightWorkflowService(SubtleSightFacade facade,IntelligenceRepository repository,BlobStore blobs,ConnectorRegistry connectors,DocumentProcessor documents,StoryClusterEngine clusters,EntityTopicExtractor entities,SignalEngine signals,DiscoveryPlanner planner,DiscoveryEngine discovery,DeepResearchService research,WatchlistService watchlist,ReportService reports,DurableJobQueue jobs,ObjectMapper json,Clock clock,SseHub sse,@Value("${subtlesight.collect.max-items-per-source:12}") int maxItemsPerSource,@Value("${subtlesight.opencli.video.enabled:false}") boolean openCliVideoEnabled){this.facade=facade;this.repository=repository;this.blobs=blobs;this.connectors=connectors;this.documents=documents;this.clusters=clusters;this.entities=entities;this.signals=signals;this.planner=planner;this.discovery=discovery;this.research=research;this.watchlist=watchlist;this.reports=reports;this.jobs=jobs;this.json=json;this.clock=clock;this.sse=sse;this.maxItemsPerSource=Math.max(1,maxItemsPerSource);this.openCliVideoEnabled=openCliVideoEnabled;}
    public IngestResult ingest(UUID sourceId,String url,String mediaType,byte[] bytes,Instant observedAt){
        Source source=repository.findSource(sourceId).orElseThrow();
        SourceConnector.ExternalReference reference=new SourceConnector.ExternalReference(url,URI.create(url),url,observedAt,Map.of("mediaType",mediaType==null?"application/octet-stream":mediaType));
        RawPayload payload=new RawPayload(reference,URI.create(url),200,mediaType==null?"application/octet-stream":mediaType,bytes,null,null,List.of());
        return ingestPayload(source,payload);
    }
    private IngestResult ingestPayload(Source source,RawPayload payload){
        Instant observed=payload.reference().publishedAt()==null?clock.instant():payload.reference().publishedAt();
        RawDocument raw=facade.storeRaw(source.id(),null,payload.reference().externalId(),payload.finalUri().toString(),payload.mediaType(),payload.content(),isVideoPayload(payload)?"public-video":"fetched",observed);
        DocumentVersion document=repository.listDocumentVersions(1000).stream().filter(d->d.rawDocumentId().equals(raw.id())).findFirst().orElseGet(()->isVideoPayload(payload)?videoDocument(source,raw,payload):documents.process(raw,payload.content()));
        return integrate(source,raw,document);
    }
    private IngestResult integrate(Source source,RawDocument raw,DocumentVersion document){
        var extraction=entities.extract(document,source.topics());
        facade.storeDocument(document,extraction.entities(),extraction.topics());
        List<StoryClusterEngine.Candidate> candidates=new ArrayList<>();
        for(Story story:repository.listStories(100)){
            var member=repository.storyMembers(story.id()).stream().findFirst();
            member.flatMap(m->repository.findDocumentVersion(m.documentVersionId())).ifPresent(d->candidates.add(new StoryClusterEngine.Candidate(story,d,false)));
        }
        var decision=clusters.decide(document,candidates);
        if(decision.decision()==StoryClusterEngine.Decision.EXACT_DUPLICATE)return new IngestResult(raw,document,decision.story(),true);
        String family=family(document.canonicalUrl());
        Story story;
        if(decision.decision()==StoryClusterEngine.Decision.SAME_STORY){
            Story old=decision.story();
            Set<String> allEntities=new LinkedHashSet<>(old.entities());allEntities.addAll(extraction.entities());
            Set<String> allTopics=new LinkedHashSet<>(old.topics());allTopics.addAll(extraction.topics());
            Set<String> families=new LinkedHashSet<>();repository.storyMembers(old.id()).forEach(m->families.add(m.sourceFamily()));families.add(family);
            story=new Story(old.id(),old.title(),old.summary(),old.status(),old.firstObservedAt(),clock.instant(),old.sourceCount()+1,families.size(),allEntities,allTopics,old.manualOverride(),clock.instant());
        }else{
            story=new Story(UUID.randomUUID(),document.title(),document.summary(),StoryStatus.ACTIVE,raw.observedAt(),raw.observedAt(),1,1,extraction.entities(),extraction.topics(),false,clock.instant());
        }
        StoryMember member=new StoryMember(story.id(),document.id(),source.tier()==SourceTier.PRIMARY?"PRIMARY":"COVERAGE",family,decision.similarity(),clock.instant());
        facade.saveStory(story,List.of(member));projectSignals(story);sse.publish("story.updated",Map.of("storyId",story.id(),"title",story.title()));
        return new IngestResult(raw,document,story,false);
    }
    private DocumentVersion videoDocument(Source source,RawDocument raw,RawPayload payload){
        Map<String,String> metadata=payload.reference().metadata()==null?Map.of():payload.reference().metadata();
        String sourceUrl=firstText(metadata.get("itemUrl"),payload.finalUri().toString(),raw.canonicalUrl());
        String title=firstText(payload.reference().title(),metadata.get("title"),source.name(),"公开视频");
        String summary=truncate(firstText(metadata.get("description"),"公开视频已下载到本机，可在视频热榜中直接播放。"),320);
        String text=String.join("\n",List.of(
                title,
                summary,
                "Platform: "+firstText(metadata.get("platform"),"Internet Archive"),
                "Source: "+sourceUrl,
                "Media type: "+firstText(metadata.get("mediaType"),payload.mediaType(),raw.mimeType()),
                "Downloads: "+firstText(metadata.get("downloads"),"0"),
                "Duration: "+firstText(metadata.get("duration"),"unknown"),
                "Blob hash: "+raw.blobHash()
        ));
        return new DocumentVersion(UUID.randomUUID(),raw.id(),title,source.name(),payload.reference().publishedAt()==null?raw.observedAt():payload.reference().publishedAt(),"en",sourceUrl,text,Hashing.sha256(text),summary,"video-ingest/1.0",null,false,clock.instant());
    }
    public List<IngestResult> collect(UUID sourceId){return collect(sourceId,maxItemsPerSource);}
    public List<IngestResult> collect(UUID sourceId,int requestedMaxItems){Source source=repository.findSource(sourceId).orElseThrow();if(!source.enabled())return List.of();int max=Math.max(1,Math.min(requestedMaxItems,maxItemsPerSource));var connector=connectors.require(source.type());var batch=connector.discover(source,source.cursor());List<IngestResult> results=new ArrayList<>();for(var ref:batch.references().stream().limit(max).toList())try{var payload=connector.fetch(ref);if(payload.status()>=200&&payload.status()<300)results.add(ingestPayload(source,payload));}catch(Exception ignored){}String nextCursor=batch.nextCursor()==null?source.cursor():batch.nextCursor();repository.saveSource(new Source(source.id(),source.name(),source.type(),source.kind(),source.endpoint(),source.schedule(),nextCursor,source.tier(),source.health(),source.topics(),source.enabled(),source.version()+1,source.createdAt(),clock.instant()));sse.publish("source.collected",Map.of("sourceId",source.id(),"count",results.size()));return List.copyOf(results);}
    public List<Source> bootstrapRealSources(){Instant since=clock.instant().minus(Duration.ofDays(7));String pushed=since.toString().substring(0,10);List<Source> created=new ArrayList<>();created.add(registerDefault("Hacker News Top Stories",SourceType.HN,"https://hacker-news.firebaseio.com/v0/topstories.json","0 */30 * * * *",SourceTier.COMMUNITY,Set.of("technology","startups","ai")));created.add(registerDefault("GitHub AI Trending Search",SourceType.GITHUB,"https://api.github.com/search/repositories?q="+enc("topic:ai pushed:>"+pushed)+"&sort=updated&order=desc&per_page=20","0 0 */1 * * *",SourceTier.COMMUNITY,Set.of("github","open-source","ai")));created.add(registerDefault("arXiv AI Recent Papers",SourceType.ARXIV,"https://export.arxiv.org/api/query?search_query=cat:cs.AI+OR+cat:cs.CL+OR+cat:cs.LG+OR+all:agent&sortBy=submittedDate&sortOrder=descending&max_results=20","0 15 */3 * * *",SourceTier.PRIMARY,Set.of("paper","ai","research")));created.add(registerDefault("Hugging Face Trending Models",SourceType.HUGGING_FACE,"https://huggingface.co/api/models?limit=20","0 20 */2 * * *",SourceTier.COMMUNITY,Set.of("models","ai","hugging-face")));created.add(registerDefault("Product Hunt Daily Feed",SourceType.PRODUCT_HUNT,"https://www.producthunt.com/feed","0 40 */2 * * *",SourceTier.COMMUNITY,Set.of("product","startup","launch")));created.add(registerDefault("Lobsters Technology RSS",SourceType.RSS,"https://lobste.rs/rss","0 10 */2 * * *",SourceTier.COMMUNITY,Set.of("technology","programming","security")));created.add(registerDefault("TechCrunch AI RSS",SourceType.RSS,"https://techcrunch.com/category/artificial-intelligence/feed/","0 25 */3 * * *",SourceTier.PROFESSIONAL,Set.of("ai","news","startup")));created.add(registerDefault("Internet Archive Public Video Recent",SourceType.VIDEO,internetArchiveVideoSearch(),"0 35 */6 * * *",SourceTier.COMMUNITY,Set.of("video","public-domain","archive")));if(openCliVideoEnabled)created.add(registerDefault("OpenCLI Bilibili Hot Videos",SourceType.VIDEO,"opencli://bilibili/hot?limit=10&download=true","0 5 */6 * * *",SourceTier.SOCIAL,Set.of("video","bilibili","opencli")));return List.copyOf(created);}
    public synchronized CollectReport collectRealSources(int maxSources,int maxItemsPerRun){List<Source> sources=bootstrapRealSources().stream().filter(Source::enabled).limit(Math.max(1,maxSources)).toList();List<SourceCollectResult> results=new ArrayList<>();for(Source source:sources){try{List<IngestResult> ingested=collect(source.id(),Math.max(1,maxItemsPerRun));results.add(new SourceCollectResult(source.id(),source.name(),source.type().name(),true,ingested.size(),null));}catch(Exception ex){results.add(new SourceCollectResult(source.id(),source.name(),source.type().name(),false,0,ex.getClass().getSimpleName()));}}return new CollectReport(results.stream().mapToInt(SourceCollectResult::ingested).sum(),results);}
    private Source registerDefault(String name,SourceType type,String endpoint,String schedule,SourceTier tier,Set<String> topics){String canonicalEndpoint=endpoint.startsWith("http")?CanonicalUrl.normalize(endpoint):endpoint.trim();Instant now=clock.instant();Optional<Source> existing=repository.listSources().stream().filter(source->source.name().equals(name)&&source.type()==type).findFirst();if(existing.isPresent()){Source old=existing.get();boolean endpointChanged=!old.endpoint().equals(canonicalEndpoint);boolean metadataChanged=endpointChanged||!java.util.Objects.equals(old.schedule(),schedule)||old.tier()!=tier||!old.topics().equals(topics)||!old.enabled();if(metadataChanged){Source updated=new Source(old.id(),name,type,SourceKind.PERSISTENT,canonicalEndpoint,schedule,endpointChanged?null:old.cursor(),tier,old.health(),topics,true,old.version()+1,old.createdAt(),now);return repository.saveSource(updated);}return old;}return facade.registerSource(name,type,SourceKind.PERSISTENT,endpoint,schedule,tier,topics);}
    private static String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private static String internetArchiveVideoSearch(){return "https://archive.org/advancedsearch.php?q="+enc("mediatype:movies AND collection:opensource_movies AND item_size:[1 TO 50000000]")+"&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=description&fl%5B%5D=date&fl%5B%5D=publicdate&fl%5B%5D=downloads&fl%5B%5D=item_size&sort%5B%5D=publicdate+desc&rows=12&page=1&output=json";}
    private static boolean isVideoPayload(RawPayload payload){String media=firstText(payload.mediaType(),payload.reference().metadata()==null?null:payload.reference().metadata().get("mediaType")).toLowerCase(java.util.Locale.ROOT);return media.startsWith("video/");}
    private static String firstText(String... values){for(String value:values)if(value!=null&&!value.isBlank())return value.trim();return "";}
    private static String truncate(String value,int max){if(value==null)return "";String compact=value.replaceAll("\\s+"," ").trim();return compact.length()<=max?compact:compact.substring(0,Math.max(0,max-1))+"…";}
    private void projectSignals(Story story){for(ViewType view:List.of(ViewType.LATEST,ViewType.EMERGING,ViewType.IMPORTANT,ViewType.FOR_YOU)){var context=new SignalEngine.Context(.65,repository.storyMembers(story.id()).stream().anyMatch(m->"PRIMARY".equals(m.role()))?.85:.55,.8,.6,story.sourceCount(),story.sourceFamilyCount(),.8,story.sourceFamilyCount(),0);repository.saveSignal(signals.project(story,view,null,context));}}
    public List<SearchHit> discover(String seed,int limit){var plan=planner.plan("USER",seed,limit);var hits=discovery.execute(plan);sse.publish("discovery.completed",Map.of("seed",seed,"count",hits.size()));return hits;}
    public SavedView saveView(String name,String expression){var ast=new SavedViewParser().parse(expression);Instant now=clock.instant();try{String astJson=json.writeValueAsString(ast);SavedView view=new SavedView(UUID.randomUUID(),name,expression,astJson,true,1,now,now);repository.saveView(view);return view;}catch(Exception e){throw new IllegalArgumentException("cannot serialize view",e);}}
    public ResearchRun startResearch(UUID storyId,String question,ResearchMode mode){ResearchBudget budget=switch(mode){case VERIFY->new ResearchBudget(4,8,10_000,new BigDecimal("2"),180);case STANDARD->new ResearchBudget(12,30,50_000,new BigDecimal("10"),720);case DEEP,CONTINUOUS->new ResearchBudget(30,80,150_000,new BigDecimal("40"),2400);};ResearchRun run=research.create(storyId,question,mode,budget);try{jobs.submit("RESEARCH",100,json.writeValueAsString(Map.of("researchId",run.id().toString())),"research:"+run.id(),3,null);}catch(Exception e){throw new IllegalStateException(e);}sse.publish("research.requested",Map.of("researchId",run.id()));return run;}
    public Map<String,Object> executeTool(String tool,String message,Map<String,Object> context){return switch(tool){case "search_local"->Map.of("results",facade.searchLocal(message,Set.of(),20));case "discover_web"->Map.of("results",discover(message,30));case "create_saved_view"->Map.of("view",saveView(String.valueOf(context.getOrDefault("name","Agent View")),String.valueOf(context.getOrDefault("expression","topic:\"AI\""))));case "add_watch_target"->Map.of("watch",watchlist.create(WatchType.QUERY,String.valueOf(context.getOrDefault("name",message)),message,Map.of()));case "start_research"->{UUID storyId=context.get("storyId")==null?null:UUID.fromString(context.get("storyId").toString());yield Map.of("research",startResearch(storyId,message,ResearchMode.STANDARD));}case "get_research_status"->Map.of("research",repository.findResearch(UUID.fromString(context.get("researchId").toString())).orElseThrow());case "create_report"->Map.of("report",reports.create(UUID.fromString(context.get("researchId").toString()),"DEEP_RESEARCH",String.valueOf(context.getOrDefault("title","研究报告"))));case "submit_feedback"->Map.of("feedback",facade.feedback(UUID.fromString(context.get("storyId").toString()),null,InteractionType.NOT_RELEVANT,message,null));case "get_story"->Map.of("story",repository.findStory(UUID.fromString(context.get("storyId").toString())).orElseThrow());case "request_publish"->throw new IllegalArgumentException("publisher destination must be selected in the report workspace");default->throw new SecurityException("unregistered tool");};}
    private String family(String url){try{String host=URI.create(url).getHost();return host==null?"unknown":host;}catch(Exception e){return "unknown";}}
    public record IngestResult(RawDocument rawDocument,DocumentVersion document,Story story,boolean duplicate){}
    public record SourceCollectResult(UUID sourceId,String name,String type,boolean ok,int ingested,String error){}
    public record CollectReport(int totalIngested,List<SourceCollectResult> sources){public CollectReport{sources=sources==null?List.of():List.copyOf(sources);}}
}
