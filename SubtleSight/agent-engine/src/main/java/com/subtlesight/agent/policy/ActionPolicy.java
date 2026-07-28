package com.subtlesight.agent.policy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified permission matrix for agent tool execution.
 * <p>
 * Each tool is governed by a {@link PolicyRule} that defines:
 * <ul>
 *   <li>Whether the tool is allowed at all</li>
 *   <li>Whether it requires user confirmation</li>
 *   <li>Rate limits (max calls per time window)</li>
 * </ul>
 */
public final class ActionPolicy {

    private final Map<String, PolicyRule> rules;
    private final Map<String, Deque<Instant>> rateWindows = new ConcurrentHashMap<>();
    private final Clock clock;

    public ActionPolicy(Map<String, PolicyRule> rules, Clock clock) {
        this.rules = Map.copyOf(rules);
        this.clock = clock;
    }

    /** Convenience constructor with default rules. */
    public static ActionPolicy withDefaults(Clock clock, Set<String> highRiskTools) {
        Map<String, PolicyRule> rules = new LinkedHashMap<>();

        // Always-allowed tools
        for (String tool : Set.of("search_local", "discover_web", "get_story", "get_document",
                "list_folders", "search_documents", "get_research_status",
                "list_watch_targets", "list_watch_changes", "submit_feedback",
                "ask_question", "create_saved_view")) {
            rules.put(tool, PolicyRule.allow());
        }

        // Medium-risk: rate-limited
        rules.put("create_document", PolicyRule.rateLimit(20, Duration.ofMinutes(1)));
        rules.put("update_document", PolicyRule.rateLimit(10, Duration.ofMinutes(1)));
        rules.put("add_watch_target", PolicyRule.rateLimit(10, Duration.ofMinutes(5)));
        rules.put("start_research", PolicyRule.rateLimit(5, Duration.ofHours(1)));

        // Draw tools: rate-limited
        for (String tool : Set.of("draw_add_node", "draw_add_edge", "draw_update_node",
                "draw_remove_node", "draw_auto_layout", "draw_diagram")) {
            rules.put(tool, PolicyRule.rateLimit(30, Duration.ofMinutes(1)));
        }

        // High-risk: require confirmation (from ToolRegistry annotations)
        for (String tool : highRiskTools) {
            rules.put(tool, PolicyRule.requireConfirm());
        }

        // Explicitly denied
        for (String tool : Set.of("modify_provider", "override_settings", "exec_shell")) {
            rules.put(tool, PolicyRule.deny());
        }

        return new ActionPolicy(rules, clock);
    }

    /** Evaluate whether a tool execution is permitted. */
    public PolicyDecision evaluate(String toolName, Map<String, Object> params) {
        PolicyRule rule = rules.getOrDefault(toolName, PolicyRule.deny());

        // 1. Deny check
        if (rule.isDenied()) {
            return PolicyDecision.deny("工具 " + toolName + " 不允许 Agent 调用");
        }

        // 2. Confirm check
        if (rule.needsConfirm()) {
            return PolicyDecision.needsConfirmation("工具 " + toolName + " 需要用户确认后才能执行");
        }

        // 3. Rate limit check
        if (rule.maxCalls() > 0 && rule.window() != null) {
            String key = toolName;
            Deque<Instant> timestamps = rateWindows.computeIfAbsent(key, k -> new ArrayDeque<>());
            synchronized (timestamps) {
                Instant cutoff = clock.instant().minus(rule.window());
                while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(cutoff)) {
                    timestamps.pollFirst();
                }
                if (timestamps.size() >= rule.maxCalls()) {
                    return PolicyDecision.deny("工具 " + toolName + " 已达速率限制: "
                            + rule.maxCalls() + " 次 / " + rule.window().toMinutes() + " 分钟");
                }
                timestamps.addLast(clock.instant());
            }
        }

        return PolicyDecision.allow();
    }

    /** Which tools require user confirmation. */
    public Set<String> confirmationTools() {
        Set<String> result = new LinkedHashSet<>();
        for (var entry : rules.entrySet()) {
            if (entry.getValue().needsConfirm()) result.add(entry.getKey());
        }
        return Set.copyOf(result);
    }

    /** Which tools are completely denied. */
    public Set<String> deniedTools() {
        Set<String> result = new LinkedHashSet<>();
        for (var entry : rules.entrySet()) {
            if (entry.getValue().isDenied()) result.add(entry.getKey());
        }
        return Set.copyOf(result);
    }
}
