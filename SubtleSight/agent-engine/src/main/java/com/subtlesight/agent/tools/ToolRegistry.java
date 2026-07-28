package com.subtlesight.agent.tools;

import com.subtlesight.agent.tools.annotations.AgentTool;
import com.subtlesight.agent.tools.ToolModels.RiskLevel;
import com.subtlesight.agent.tools.ToolModels.ToolDefinition;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Typed tool registry. Scans beans for {@code @AgentTool} methods and provides
 * schema export, parameter validation, and reflective invocation.
 */
public final class ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();

    /** Register all {@code @AgentTool} methods on the given bean instance. */
    public void register(Object bean) {
        for (Method m : bean.getClass().getMethods()) {
            AgentTool anno = m.getAnnotation(AgentTool.class);
            if (anno == null) continue;
            ToolDefinition def = ToolDefinition.from(m, anno, bean);
            tools.put(anno.name(), def);
        }
    }

    /** Look up a tool by name. */
    public Optional<ToolDefinition> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    /** All registered tools. */
    public List<ToolDefinition> all() {
        return List.copyOf(tools.values());
    }

    /** All tool names. */
    public Set<String> toolNames() {
        return Set.copyOf(tools.keySet());
    }

    /** Tools whose risk == HIGH (require confirmation). */
    public Set<String> highRiskTools() {
        Set<String> result = new LinkedHashSet<>();
        for (ToolDefinition def : tools.values()) {
            if (def.risk() == RiskLevel.HIGH) result.add(def.name());
        }
        return Set.copyOf(result);
    }

    /** Execute a named tool with the given arguments. */
    public Map<String, Object> execute(String toolName, Map<String, Object> args) {
        ToolDefinition def = tools.get(toolName);
        if (def == null) {
            return Map.of("error", "unknown tool: " + toolName);
        }
        return def.invoke(args);
    }

    /** Export all tools as LLM function-calling schemas. */
    public List<Map<String, Object>> functionSchemas() {
        return tools.values().stream().map(ToolDefinition::toFunctionSchema).toList();
    }

    /** Export tool summary for planner prompts. */
    public String toolSummary() {
        StringBuilder sb = new StringBuilder();
        for (ToolDefinition def : tools.values()) {
            sb.append("- ").append(def.name()).append(": ").append(def.description());
            if (def.risk() == RiskLevel.HIGH) sb.append(" [高风险]");
            sb.append("\n");
        }
        return sb.toString();
    }
}
