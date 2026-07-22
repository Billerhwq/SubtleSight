package com.subtlesight.discovery;

import com.subtlesight.domain.Models.QueryPlan;
import com.subtlesight.domain.Models.QuerySpec;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class DiscoveryPlanner {
    private final Clock clock;
    public DiscoveryPlanner(Clock clock) { this.clock=clock; }
    public QueryPlan plan(String seedType, String seedValue, int maxResults) {
        if (seedValue == null || seedValue.isBlank()) throw new IllegalArgumentException("discovery seed must not be blank");
        String q = seedValue.trim();
        List<QuerySpec> specs = new ArrayList<>();
        specs.add(new QuerySpec("GENERAL", q, Set.of(), Set.of()));
        specs.add(new QuerySpec("OFFICIAL", q + " official announcement", Set.of(), Set.of("reddit.com","x.com")));
        specs.add(new QuerySpec("CODE_PAPER", q + " GitHub OR arXiv OR paper", Set.of("github.com","arxiv.org"), Set.of()));
        specs.add(new QuerySpec("NEWS", q + " latest news", Set.of(), Set.of()));
        specs.add(new QuerySpec("COMMUNITY", q + " discussion experience", Set.of("news.ycombinator.com","reddit.com"), Set.of()));
        specs.add(new QuerySpec("COUNTER_EVIDENCE", q + " criticism limitation contradiction", Set.of(), Set.of()));
        return new QueryPlan(UUID.randomUUID(), seedType, q, specs, Math.min(Math.max(maxResults, 10), 200), clock.instant());
    }
}

