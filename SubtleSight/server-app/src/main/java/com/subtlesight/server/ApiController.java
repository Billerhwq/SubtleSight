package com.subtlesight.server;

import com.subtlesight.application.SubtleSightFacade;
import com.subtlesight.application.Ports.BlobStore;
import com.subtlesight.application.Ports.IntelligenceRepository;
import com.subtlesight.domain.Models.*;
import com.subtlesight.jobs.DurableJobQueue;
import com.subtlesight.observability.BackupService;
import com.subtlesight.provider.search.WebhookPublisher;
import com.subtlesight.report.ReportService;
import com.subtlesight.watchlist.WatchlistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ApiController {
    private final SubtleSightWorkflowService workflow;private final SubtleSightFacade facade;private final IntelligenceRepository repository;private final WatchlistService watchlist;
    private final ReportService reports;private final DurableJobQueue jobs;private final BackupService backup;private final ObjectMapper json;private final SseHub sse;private final Path dataDir;private final BlobStore blobs;
    public ApiController(SubtleSightWorkflowService workflow,SubtleSightFacade facade,IntelligenceRepository repository,WatchlistService watchlist,ReportService reports,DurableJobQueue jobs,BackupService backup,ObjectMapper json,SseHub sse,Path dataDirectory,BlobStore blobs){this.workflow=workflow;this.facade=facade;this.repository=repository;this.watchlist=watchlist;this.reports=reports;this.jobs=jobs;this.backup=backup;this.json=json;this.sse=sse;this.dataDir=dataDirectory;this.blobs=blobs;}
    @GetMapping("/auth/status") Map<String,Object> auth(CsrfToken csrfToken){csrfToken.getToken();return Map.of("authenticated",true,"user","local");}
    @GetMapping("/events") SseEmitter events(){return sse.subscribe();}
    @GetMapping("/system/summary") Map<String,Object> summary(){return Map.of("sources",repository.count("sources"),"watchlists",repository.count("watch_targets"),"reports",repository.count("report_versions"),"jobs",repository.count("jobs"));}
    @GetMapping("/workspace/overview") Map<String,Object> workspaceOverview(){
        Map<String,Object> overview=new LinkedHashMap<>();
        overview.put("summary",summary());
        overview.put("sources",repository.listSources());
        overview.put("watchlists",repository.listWatchTargets());
        overview.put("reports",repository.listReports(20));
        overview.put("views",repository.listViews());
        overview.put("jobs",jobs.list(200));
        overview.put("feed",workspaceFeed());
        overview.put("trends",workspaceTrends());
        return overview;
    }
    @GetMapping("/workspace/trends") List<Map<String,Object>> trends(){return workspaceTrends();}

    @GetMapping("/sources") List<Source> sources(){return repository.listSources();}
    @PostMapping("/sources") Source createSource(@Valid @RequestBody SourceRequest request){return facade.registerSource(request.name(),request.type(),request.kind(),request.endpoint(),request.schedule(),request.tier(),request.topics());}
    @DeleteMapping("/sources/{id}") void deleteSource(@PathVariable UUID id){repository.disableSource(id,Instant.now());}
    @PostMapping("/sources/{id}/collect") List<SubtleSightWorkflowService.IngestResult> collect(@PathVariable UUID id,@RequestParam(defaultValue="12")int maxItems){return workflow.collect(id,maxItems);}
    @PostMapping("/sources/bootstrap-real") List<Source> bootstrapRealSources(){return workflow.bootstrapRealSources();}
    @PostMapping("/sources/collect-real") SubtleSightWorkflowService.CollectReport collectRealSources(@RequestParam(defaultValue="8")int maxSources,@RequestParam(defaultValue="8")int maxItems){return workflow.collectRealSources(maxSources,maxItems);}
    @PostMapping(value="/documents/upload",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) SubtleSightWorkflowService.IngestResult upload(@RequestParam UUID sourceId,@RequestPart MultipartFile file)throws Exception{return workflow.ingest(sourceId,"https://local.upload/"+java.net.URLEncoder.encode(file.getOriginalFilename()==null?"document":file.getOriginalFilename(),StandardCharsets.UTF_8),file.getContentType()==null?"application/octet-stream":file.getContentType(),file.getBytes(),Instant.now());}
    @PostMapping("/documents/ingest") SubtleSightWorkflowService.IngestResult ingest(@Valid @RequestBody IngestRequest request){return workflow.ingest(request.sourceId(),request.url(),request.mediaType(),request.content().getBytes(StandardCharsets.UTF_8),request.observedAt());}

    @GetMapping("/stories") List<Story> stories(@RequestParam(defaultValue="100")int limit){return repository.listStories(limit);}
    @GetMapping("/stories/{id}") Map<String,Object> storyDetail(@PathVariable UUID id){
        Story story=repository.findStory(id).orElse(null);
        if(story==null) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Story not found");
        Map<String,Object> detail=new LinkedHashMap<>();
        detail.put("story",story);
        // Enrich timeline with document version details
        List<Map<String,Object>> timeline=new ArrayList<>();
        for(StoryMember member:repository.storyMembers(id)){
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("storyId",member.storyId().toString());
            item.put("documentVersionId",member.documentVersionId().toString());
            item.put("role",member.role());
            item.put("sourceFamily",member.sourceFamily());
            item.put("similarity",member.similarity());
            item.put("addedAt",member.addedAt().toString());
            repository.findDocumentVersion(member.documentVersionId()).ifPresent(dv->{
                item.put("title",dv.title());
                item.put("summary",dv.summary()!=null?dv.summary():"");
                item.put("text",dv.text()!=null?dv.text():"");
                item.put("canonicalUrl",dv.canonicalUrl()!=null?dv.canonicalUrl():"");
                item.put("publishedAt",dv.publishedAt()!=null?dv.publishedAt().toString():"");
                item.put("author",dv.author()!=null?dv.author():"");
                item.put("language",dv.language()!=null?dv.language():"");
            });
            timeline.add(item);
        }
        detail.put("timeline",timeline);
        detail.put("interactions",List.of());
        return detail;
    }

    @GetMapping("/feed/{view}") List<FeedItem> feed(@PathVariable ViewType view,@RequestParam(required=false)UUID savedViewId,@RequestParam(defaultValue="30")int limit,@RequestParam(required=false)String cursor){return facade.feed(view,savedViewId,limit,cursor);}

    @GetMapping("/views") List<SavedView> views(){return repository.listViews();}
    @PostMapping("/views") SavedView view(@RequestBody ViewRequest request){return workflow.saveView(request.name(),request.expression());}
    @DeleteMapping("/views/{id}") void deleteView(@PathVariable UUID id){repository.deleteView(id);}

    @GetMapping("/watchlists") List<WatchTarget> watches(){return repository.listWatchTargets();}
    @PostMapping("/watchlists") WatchTarget watch(@RequestBody WatchRequest request){return watchlist.create(request.type(),request.name(),request.expression(),request.baseline());}
    @GetMapping("/watchlists/{id}/changes") List<ChangeEvent> changes(@PathVariable UUID id){return repository.listChanges(id,100);}
    @PostMapping("/watchlists/{id}/detect") List<ChangeEvent> detect(@PathVariable UUID id,@RequestBody DetectRequest request){return watchlist.detect(id,request.current(),request.source());}
    @PostMapping("/watchlists/{id}/confirm") WatchTarget confirm(@PathVariable UUID id,@RequestBody Map<String,Object> baseline){return watchlist.confirm(id,baseline,java.time.Duration.ofHours(24));}

    @GetMapping("/reports") List<ReportVersion> reportList(){return repository.listReports(100);}
    @PostMapping("/reports") ReportVersion report(@RequestBody ReportRequest request){return reports.create(request.researchId(),request.type(),request.title());}
    @GetMapping("/reports/{id}/export/{format}") ResponseEntity<ByteArrayResource> export(@PathVariable UUID id,@PathVariable String format){byte[] bytes=reports.export(id,format);String contentType=switch(format){case"pdf"->"application/pdf";case"html"->"text/html";case"json"->"application/json";default->"text/markdown";};return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename("subtlesight-report-"+id+"."+format).build().toString()).contentType(MediaType.parseMediaType(contentType)).body(new ByteArrayResource(bytes));}
    @PostMapping("/reports/{id}/publish") Publication publish(@PathVariable UUID id,@RequestBody PublishRequest request){return reports.publish(id,request.destinationId(),new WebhookPublisher(URI.create(request.endpoint()),json),request.confirmed());}
    @GetMapping("/media/{hash}") ResponseEntity<ByteArrayResource> media(@PathVariable String hash,@RequestParam(defaultValue="video/mp4")String type)throws IOException{try(var input=blobs.open(hash).orElseThrow()){byte[] bytes=input.readAllBytes();String safeType=type==null||!type.contains("/")?"video/mp4":type;String ext=safeType.contains("webm")?"webm":safeType.contains("ogg")?"ogv":"mp4";return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.inline().filename(hash+"."+ext).build().toString()).header(HttpHeaders.CACHE_CONTROL,"private, max-age=86400").contentType(MediaType.parseMediaType(safeType)).body(new ByteArrayResource(bytes));}}

    @GetMapping("/jobs") Object jobList(){return jobs.list(200);}
    @PostMapping("/jobs/{id}/cancel") void cancelJob(@PathVariable UUID id){jobs.cancel(id);}
    @PostMapping("/admin/backup") Map<String,Object> backup(){Path target=dataDir.resolve("backups").resolve("subtlesight-"+Instant.now().toEpochMilli()+".insightpack");backup.create(target);return Map.of("file",target.getFileName().toString(),"created",true);}

    public record SourceRequest(@NotBlank String name,@NotNull SourceType type,@NotNull SourceKind kind,@NotBlank String endpoint,String schedule,@NotNull SourceTier tier,Set<String> topics){}
    public record IngestRequest(@NotNull UUID sourceId,@NotBlank String url,@NotBlank String mediaType,@NotBlank String content,Instant observedAt){}
    public record ViewRequest(@NotBlank String name,@NotBlank String expression){}
    public record WatchRequest(@NotNull WatchType type,@NotBlank String name,@NotBlank String expression,Map<String,Object> baseline){public WatchRequest{if(baseline==null)baseline=Map.of();}}
    public record DetectRequest(Map<String,Object> current,String source){public DetectRequest{if(current==null)current=Map.of();}}
    public record ReportRequest(@NotNull UUID researchId,@NotBlank String type,@NotBlank String title){}
    public record PublishRequest(@NotBlank String destinationId,@NotBlank String endpoint,boolean confirmed){}

    private List<Map<String,Object>> workspaceFeed(){
        List<Map<String,Object>> rows=new java.util.ArrayList<>();
        for(FeedItem item:facade.feed(ViewType.IMPORTANT,null,100,null)){
            SourceContext context=sourceContext(item);
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("story",item.story());
            row.put("signal",item.signal());
            row.put("saved",item.saved());
            row.put("hidden",item.hidden());
            row.put("timeline",item.timeline());
            row.put("domain",context.family());
            row.put("source",context.sourceName());
            row.put("sourceName",context.sourceName());
            row.put("sourceType",context.sourceType());
            row.put("sourceId",context.sourceId()==null?"":context.sourceId().toString());
            row.put("platform",context.platform());
            row.put("mediaType",context.mediaType());
            row.put("blobHash",context.blobHash());
            row.put("contentLength",context.contentLength());
            if(context.mediaUrl()!=null){row.put("mediaUrl",context.mediaUrl());row.put("downloaded",true);}
            row.put("observedAt",item.story().firstObservedAt());
            row.put("updatedAt",item.story().updatedAt());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String,Object>> workspaceTrends(){
        List<Map<String,Object>> rows=new java.util.ArrayList<>();
        for(FeedItem item:facade.feed(ViewType.IMPORTANT,null,50,null)){
            SourceContext context=sourceContext(item);
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("title",item.story().title());
            row.put("summary",item.story().summary()==null?"":item.story().summary());
            row.put("domain",context.family());
            row.put("source",context.sourceName());
            row.put("sourceName",context.sourceName());
            row.put("sourceType",context.sourceType());
            row.put("sourceId",context.sourceId()==null?"":context.sourceId().toString());
            row.put("platform",context.platform());
            row.put("mediaType",context.mediaType());
            row.put("blobHash",context.blobHash());
            row.put("contentLength",context.contentLength());
            if(context.mediaUrl()!=null){row.put("mediaUrl",context.mediaUrl());row.put("downloaded",true);}
            row.put("score",Math.round(item.signal().score()*100));
            row.put("metric","score "+Math.round(item.signal().score()*100));
            row.put("metricLabel","综合信号");
            row.put("reason",String.join(" / ",item.signal().reasonCodes()));
            row.put("url","#");
            row.put("observedAt",item.story().firstObservedAt());
            row.put("updatedAt",item.story().updatedAt());
            rows.add(row);
        }
        return rows;
    }

    private SourceContext sourceContext(FeedItem item){
        StoryMember member=item.timeline().isEmpty()?null:item.timeline().getFirst();
        String family=member==null?"local":member.sourceFamily();
        Source source=null;
        RawDocument raw=null;
        if(member!=null){
            var document=repository.findDocumentVersion(member.documentVersionId()).orElse(null);
            if(document!=null)raw=repository.findRawDocument(document.rawDocumentId()).orElse(null);
            if(raw!=null)source=repository.findSource(raw.sourceId()).orElse(null);
        }
        String sourceName=source==null?family:source.name();
        String sourceType=source==null?"UNKNOWN":source.type().name();
        return new SourceContext(family,sourceName,sourceType,platformLabel(sourceType,sourceName,family),source==null?null:source.id(),raw==null?null:raw.mimeType(),raw==null?null:raw.blobHash(),raw==null?0:raw.contentLength(),mediaUrl(raw));
    }

    private String platformLabel(String sourceType,String sourceName,String family){
        return switch(sourceType){
            case "GITHUB" -> "GitHub";
            case "HN" -> "Hacker News";
            case "ARXIV" -> "arXiv";
            case "HUGGING_FACE" -> "Hugging Face";
            case "PRODUCT_HUNT" -> "Product Hunt";
            case "VIDEO" -> "Internet Archive";
            default -> {
                String value=(sourceName+" "+family).toLowerCase(java.util.Locale.ROOT);
                if(value.contains("archive.org")||value.contains("internet archive")) yield "Internet Archive";
                if(value.contains("techcrunch")) yield "TechCrunch";
                if(value.contains("lobster")) yield "Lobsters";
                if(value.contains("producthunt")||value.contains("product hunt")) yield "Product Hunt";
                if(value.contains("huggingface")||value.contains("hugging face")) yield "Hugging Face";
                if(value.contains("github")) yield "GitHub";
                if(value.contains("arxiv")) yield "arXiv";
                yield "RSS";
            }
        };
    }

    private String mediaUrl(RawDocument raw){if(raw==null||raw.blobHash()==null||raw.mimeType()==null||!raw.mimeType().toLowerCase(java.util.Locale.ROOT).startsWith("video/"))return null;return "/api/v1/media/"+raw.blobHash()+"?type="+URLEncoder.encode(raw.mimeType(),StandardCharsets.UTF_8);}

    private record SourceContext(String family,String sourceName,String sourceType,String platform,UUID sourceId,String mediaType,String blobHash,long contentLength,String mediaUrl){}
}
