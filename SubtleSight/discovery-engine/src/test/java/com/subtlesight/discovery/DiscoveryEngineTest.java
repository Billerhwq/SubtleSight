package com.subtlesight.discovery;

import com.subtlesight.application.Ports.WebSearchProvider;
import com.subtlesight.domain.Models.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class DiscoveryEngineTest {
  private final Clock clock=Clock.fixed(Instant.parse("2026-07-16T00:00:00Z"),ZoneOffset.UTC);
  @Test void mergesCanonicalUrlsScoresAllTiersSkipsBadProvidersAndCapsCommunity(){QuerySpec q=new QuerySpec("COUNTER_EVIDENCE","q",Set.of(),Set.of());List<SearchHit> rows=new ArrayList<>();for(SourceTier tier:SourceTier.values())rows.add(new SearchHit("raw","",tier.name(),"https://example.com/"+tier+"?utm_source=x","s",clock.instant().minus(Duration.ofDays(2)),tier,.8));rows.add(new SearchHit("raw","","duplicate","https://example.com/PRIMARY","s",null,SourceTier.UNKNOWN,.1));rows.add(new SearchHit("raw","","bad","file:///x","s",null,SourceTier.SOCIAL,.9));WebSearchProvider ok=new WebSearchProvider(){public String name(){return"ok";}public List<SearchHit> search(QuerySpec ignored,int limit){return rows;}};WebSearchProvider bad=new WebSearchProvider(){public String name(){return"bad";}public List<SearchHit> search(QuerySpec ignored,int limit){throw new IllegalStateException();}};QueryPlan plan=new QueryPlan(UUID.randomUUID(),"USER","q",List.of(q),4,clock.instant());List<SearchHit> found=new DiscoveryEngine(List.of(ok,bad),clock).execute(plan);assertThat(found).hasSize(4).isSortedAccordingTo(Comparator.comparingDouble(SearchHit::score).reversed());assertThat(found).extracting(SearchHit::url).doesNotHaveDuplicates().allMatch(u->u.startsWith("https://"));assertThat(found.stream().filter(h->h.tier()==SourceTier.COMMUNITY||h.tier()==SourceTier.SOCIAL).count()).isLessThanOrEqualTo(1);}
  @Test void plannerBoundsResultsAndRejectsBlank(){DiscoveryPlanner p=new DiscoveryPlanner(clock);assertThat(p.plan("USER"," seed ",1).queries()).extracting(QuerySpec::type).containsExactly("GENERAL","OFFICIAL","CODE_PAPER","NEWS","COMMUNITY","COUNTER_EVIDENCE");assertThat(p.plan("USER","x",1).maxResults()).isEqualTo(10);assertThat(p.plan("USER","x",999).maxResults()).isEqualTo(200);assertThatThrownBy(()->p.plan("USER"," ",10)).isInstanceOf(IllegalArgumentException.class);}
}
