package com.subtlesight.discovery;

import com.subtlesight.application.Ports.WebSearchProvider;
import com.subtlesight.domain.CanonicalUrl;
import com.subtlesight.domain.Models.QueryPlan;
import com.subtlesight.domain.Models.QuerySpec;
import com.subtlesight.domain.Models.SearchHit;
import com.subtlesight.domain.Models.SourceTier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DiscoveryEngine {
    private final List<WebSearchProvider> providers;
    private final Clock clock;
    public DiscoveryEngine(List<WebSearchProvider> providers, Clock clock) { this.providers=List.copyOf(providers); this.clock=clock; }
    public List<SearchHit> execute(QueryPlan plan) {
        Map<String,SearchHit> merged = new LinkedHashMap<>();
        for (QuerySpec query : plan.queries()) for (WebSearchProvider provider : providers) {
            List<SearchHit> hits;
            try { hits = provider.search(query, Math.max(10, plan.maxResults()/providers.size())); }
            catch (RuntimeException degraded) { continue; }
            for (SearchHit hit : hits) {
                String canonical;
                try { canonical = CanonicalUrl.normalize(hit.url()); } catch (RuntimeException invalid) { continue; }
                SearchHit scored = new SearchHit(provider.name(), query.type(), hit.title(), canonical, hit.snippet(), hit.publishedAt(), hit.tier(), candidateScore(hit, query));
                merged.merge(canonical, scored, (a,b)->a.score()>=b.score()?a:b);
            }
        }
        List<SearchHit> ordered = merged.values().stream().sorted(Comparator.comparingDouble(SearchHit::score).reversed()).toList();
        List<SearchHit> balanced = capCommunity(ordered, plan.maxResults());
        return balanced;
    }
    double candidateScore(SearchHit hit, QuerySpec query) {
        double score = Math.max(0, Math.min(1, hit.score())) * 0.45;
        score += switch (hit.tier()) { case PRIMARY -> .30; case PROFESSIONAL -> .22; case COMMUNITY -> .10; case SOCIAL -> .05; case UNKNOWN -> .08; };
        if (query.type().equals("COUNTER_EVIDENCE")) score += .08;
        if (hit.publishedAt()!=null) {
            long days=Math.max(0,Duration.between(hit.publishedAt(),clock.instant()).toDays()); score += .17*Math.exp(-days/30d);
        }
        return Math.min(1,score);
    }
    private List<SearchHit> capCommunity(List<SearchHit> hits, int limit) {
        List<SearchHit> selected=new ArrayList<>(); int social=0; int socialLimit=Math.max(1,(int)Math.floor(limit*.30));
        for(SearchHit hit:hits){ boolean community=hit.tier()==SourceTier.COMMUNITY||hit.tier()==SourceTier.SOCIAL;
            if(community&&social>=socialLimit) continue; selected.add(hit); if(community) social++; if(selected.size()>=limit) break; }
        return List.copyOf(selected);
    }
}

