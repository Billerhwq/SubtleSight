package com.subtlesight.research;

import com.subtlesight.application.Ports.AiProvider;
import com.subtlesight.application.Ports.IntelligenceRepository;
import com.subtlesight.application.Ports.WebSearchProvider;
import com.subtlesight.domain.BudgetLedger;
import com.subtlesight.domain.ResearchStateMachine;
import com.subtlesight.domain.Models.*;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Checkpointed Java research state machine. Model calls provide content, never control transitions or budgets. */
public final class DeepResearchService {
    private final IntelligenceRepository repository;
    private final List<WebSearchProvider> searchProviders;
    private final AiProvider ai;
    private final MaterialProvider materialProvider;
    private final Clock clock;
    public DeepResearchService(IntelligenceRepository repository,List<WebSearchProvider> searchProviders,AiProvider ai,MaterialProvider materialProvider,Clock clock){
        this.repository=repository;this.searchProviders=List.copyOf(searchProviders);this.ai=ai;this.materialProvider=materialProvider;this.clock=clock;}

    public ResearchRun create(UUID storyId,String question,ResearchMode mode,ResearchBudget budget){Instant now=clock.instant();ResearchRun run=new ResearchRun(UUID.randomUUID(),storyId,question,mode,ResearchStatus.CREATED,budget,ResearchUsage.zero(),null,null,null,List.of(),now,now);repository.saveResearch(run);repository.appendOutbox("research",run.id(),"research.requested","{}",now);return run;}
    public ResearchRun execute(UUID id){ResearchRun run=repository.findResearch(id).orElseThrow();if(run.status()==ResearchStatus.COMPLETED||run.status()==ResearchStatus.PARTIAL)return run;
        BudgetLedger ledger=new BudgetLedger(run.budget());if(run.usage().queries()+run.usage().pages()+run.usage().tokens()>0)ledger.reserve(run.usage().queries(),run.usage().pages(),run.usage().tokens(),run.usage().cost(),run.usage().durationSeconds());
        try{
            run=move(run,ResearchStatus.SCOPING,"{\"question\":"+quote(run.question())+",\"mode\":"+quote(run.mode().name())+"}",null,null,List.of());
            List<QuerySpec> plan=plan(run.question(),run.mode());run=move(run,ResearchStatus.PLANNING,run.scopeJson(),toJsonPlan(plan),null,List.of());
            run=move(run,ResearchStatus.SEARCHING,run.scopeJson(),run.planJson(),"{\"step\":\"search\"}",List.of());
            List<SearchHit> hits=search(plan,ledger);
            run=withUsage(run,ledger.usage());
            run=move(run,ResearchStatus.READING,run.scopeJson(),run.planJson(),"{\"step\":\"read\",\"hits\":"+hits.size()+"}",List.of());
            int pageBudget=Math.max(0,run.budget().maxPages()-ledger.usage().pages());List<DocumentVersion> documents=materialProvider.materialize(run.question(),hits,pageBudget);
            if(documents.size()>pageBudget)documents=documents.subList(0,pageBudget);if(!documents.isEmpty())ledger.reserve(0,documents.size(),0,BigDecimal.ZERO,0);run=withUsage(run,ledger.usage());
            run=move(run,ResearchStatus.EXTRACTING,run.scopeJson(),run.planJson(),"{\"step\":\"extract\",\"documents\":"+documents.size()+"}",List.of());
            int claims=extract(run,documents);
            List<String> gaps=new ArrayList<>();if(claims==0)gaps.add("No source-backed claims were extractable");if(distinctFamilies(documents)<2)gaps.add("Independent source diversity below two");
            run=move(run,ResearchStatus.REFLECTING,run.scopeJson(),run.planJson(),"{\"step\":\"reflect\"}",gaps);
            if(!gaps.isEmpty()&&run.mode()!=ResearchMode.VERIFY&&ledger.canReserve(1,0,0,BigDecimal.ZERO,0)){
                ledger.reserve(1,0,0,BigDecimal.ZERO,0);QuerySpec gap=new QuerySpec("GAP","counter evidence "+run.question(),Set.of(),Set.of());List<SearchHit> extra=searchProviders.stream().flatMap(p->{try{return p.search(gap,5).stream();}catch(Exception e){return java.util.stream.Stream.empty();}}).toList();
                if(!extra.isEmpty()){List<DocumentVersion> more=materialProvider.materialize(run.question(),extra,Math.max(0,run.budget().maxPages()-ledger.usage().pages()));if(!more.isEmpty()){ledger.reserve(0,more.size(),0,BigDecimal.ZERO,0);claims+=extract(run,more);gaps.clear();}}
                run=withUsage(run,ledger.usage());
            }
            run=move(run,ResearchStatus.WRITING,run.scopeJson(),run.planJson(),"{\"step\":\"write\"}",gaps);
            if(ai!=null&&ledger.canReserve(0,0,512,new BigDecimal("0.50"),0)){AiProvider.AiResult result=ai.complete(new AiProvider.AiRequest("research_write","Use only supplied evidence.",outline(run.id()),"{report:string}",512,0));ledger.reserve(0,0,result.inputTokens()+result.outputTokens(),new BigDecimal("0.01"),0);run=withUsage(run,ledger.usage());}
            run=move(run,ResearchStatus.VERIFYING,run.scopeJson(),run.planJson(),"{\"step\":\"verify\"}",gaps);
            boolean criticalUnsupported=repository.researchClaims(run.id()).stream().filter(Claim::critical).anyMatch(c->repository.claimEvidence(c.id()).isEmpty());
            ResearchStatus terminal=criticalUnsupported||claims==0?ResearchStatus.PARTIAL:ResearchStatus.COMPLETED;run=move(run,terminal,run.scopeJson(),run.planJson(),"{\"step\":\"done\"}",gaps);
            repository.appendOutbox("research",run.id(),"research.completed","{}",clock.instant());return run;
        }catch(IllegalStateException budgetOrState){ResearchRun latest=repository.findResearch(id).orElse(run);if(!isTerminal(latest.status())){latest=move(latest,ResearchStatus.PARTIAL,latest.scopeJson(),latest.planJson(),latest.checkpointJson(),append(latest.gaps(),budgetOrState.getMessage()));}return latest;}
    }
    private List<SearchHit> search(List<QuerySpec> plan,BudgetLedger ledger){Map<String,SearchHit> hits=new LinkedHashMap<>();for(QuerySpec query:plan){if(!ledger.canReserve(1,0,0,BigDecimal.ZERO,0))break;ledger.reserve(1,0,0,BigDecimal.ZERO,0);for(WebSearchProvider p:searchProviders)try{for(SearchHit h:p.search(query,10))hits.putIfAbsent(com.subtlesight.domain.CanonicalUrl.normalize(h.url()),h);}catch(Exception ignored){}}return List.copyOf(hits.values());}
    private int extract(ResearchRun run,List<DocumentVersion> docs){int count=0;for(DocumentVersion d:docs){String sentence=firstSentence(d.text());if(sentence.length()<12)continue;Claim claim=new Claim(UUID.randomUUID(),run.id(),sentence,ClaimStatus.SUPPORTED,true,d.publishedAt(),null,clock.instant());repository.saveClaim(claim);int start=d.text().indexOf(sentence);Evidence evidence=new Evidence(UUID.randomUUID(),d.id(),sentence,start,start+sentence.length(),"text="+start+":"+(start+sentence.length()),d.textHash(),family(d),EvidenceRelation.SUPPORTS,.8,clock.instant());repository.saveEvidence(evidence);repository.linkClaimEvidence(new ClaimEvidence(claim.id(),evidence.id(),EvidenceRelation.SUPPORTS));count++;}return count;}
    private ResearchRun move(ResearchRun run,ResearchStatus target,String scope,String plan,String checkpoint,List<String> gaps){ResearchStateMachine.requireTransition(run.status(),target);ResearchRun next=new ResearchRun(run.id(),run.storyId(),run.question(),run.mode(),target,run.budget(),run.usage(),scope,plan,checkpoint,gaps,run.createdAt(),clock.instant());return repository.saveResearch(next);}
    private ResearchRun withUsage(ResearchRun r,ResearchUsage usage){ResearchRun n=new ResearchRun(r.id(),r.storyId(),r.question(),r.mode(),r.status(),r.budget(),usage,r.scopeJson(),r.planJson(),r.checkpointJson(),r.gaps(),r.createdAt(),clock.instant());return repository.saveResearch(n);}
    private List<QuerySpec> plan(String q,ResearchMode mode){List<QuerySpec> p=new ArrayList<>();p.add(new QuerySpec("GENERAL",q,Set.of(),Set.of()));p.add(new QuerySpec("OFFICIAL",q+" official primary source",Set.of(),Set.of("reddit.com")));p.add(new QuerySpec("COUNTER",q+" contradiction limitations",Set.of(),Set.of()));if(mode==ResearchMode.DEEP||mode==ResearchMode.CONTINUOUS)p.add(new QuerySpec("ACADEMIC",q+" paper repository",Set.of("arxiv.org","github.com"),Set.of()));return p;}
    private String outline(UUID id){return repository.researchClaims(id).stream().map(c->"Claim: "+c.statement()+"\nEvidence: "+repository.claimEvidence(c.id()).stream().map(Evidence::exactQuote).findFirst().orElse("MISSING")).collect(java.util.stream.Collectors.joining("\n"));}
    private String toJsonPlan(List<QuerySpec> p){return "{\"queries\":["+p.stream().map(q->quote(q.query())).collect(java.util.stream.Collectors.joining(","))+"]}";}
    private String quote(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    private String firstSentence(String text){if(text==null)return "";int end=text.length();for(char c:new char[]{'。','.','!','！','?','？'}){int p=text.indexOf(c);if(p>=0)end=Math.min(end,p+1);}return text.substring(0,Math.min(end,800)).trim();}
    private String family(DocumentVersion d){try{return java.net.URI.create(d.canonicalUrl()).getHost();}catch(Exception e){return "document:"+d.id();}}
    private int distinctFamilies(List<DocumentVersion> docs){return (int)docs.stream().map(this::family).distinct().count();}
    private List<String> append(List<String> values,String value){List<String> out=new ArrayList<>(values);out.add(value);return List.copyOf(out);}
    private boolean isTerminal(ResearchStatus s){return Set.of(ResearchStatus.COMPLETED,ResearchStatus.PARTIAL,ResearchStatus.CANCELLED,ResearchStatus.FAILED).contains(s);}
    @FunctionalInterface public interface MaterialProvider{List<DocumentVersion> materialize(String question,List<SearchHit> hits,int maxPages);}
}
