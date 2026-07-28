package com.subtlesight.agent.planner;

import com.subtlesight.agent.orchestrator.OrchestratorModels.Plan;

import java.util.Map;

/**
 * Converts a user's natural-language message into a structured {@link Plan}.
 * <p>
 * Implementations may use LLM, keyword routing, or any other strategy.
 */
public interface Planner {

    /**
     * Generate a plan from a user message, page context, and conversation history.
     *
     * @param message  user's natural-language message
     * @param context  current page context (e.g. {"page":"/discover"})
     * @param history  recent conversation turns (JSON array string, or empty)
     * @return a plan with 1–5 steps
     */
    Plan plan(String message, Map<String, Object> context, String history);

    /** Convenience overload without conversation history. */
    default Plan plan(String message, Map<String, Object> context) {
        return plan(message, context, "");
    }
}
