package com.subtlesight.domain;

import java.math.BigDecimal;

import static com.subtlesight.domain.Models.ResearchBudget;
import static com.subtlesight.domain.Models.ResearchUsage;

public final class BudgetLedger {
    private final ResearchBudget budget;
    private ResearchUsage usage = ResearchUsage.zero();
    public BudgetLedger(ResearchBudget budget) { this.budget = budget; }
    public synchronized boolean canReserve(int queries, int pages, long tokens, BigDecimal cost, long seconds) {
        return usage.queries() + queries <= budget.maxQueries()
                && usage.pages() + pages <= budget.maxPages()
                && usage.tokens() + tokens <= budget.maxTokens()
                && usage.cost().add(cost).compareTo(budget.maxCost()) <= 0
                && usage.durationSeconds() + seconds <= budget.maxDurationSeconds();
    }
    public synchronized ResearchUsage reserve(int queries, int pages, long tokens, BigDecimal cost, long seconds) {
        if (queries < 0 || pages < 0 || tokens < 0 || seconds < 0 || cost.signum() < 0 || !canReserve(queries, pages, tokens, cost, seconds))
            throw new IllegalStateException("research hard budget exceeded");
        usage = new ResearchUsage(usage.queries() + queries, usage.pages() + pages, usage.tokens() + tokens,
                usage.cost().add(cost), usage.durationSeconds() + seconds);
        return usage;
    }
    public synchronized ResearchUsage usage() { return usage; }
}
