package com.subtlesight.agent.tools.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a method as a callable agent tool. Methods must return {@code Map<String,Object>}. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AgentTool {
    /** Unique tool name (e.g. "search_local"). */
    String name();
    /** Human-readable description for planners and LLM function calling. */
    String description();
    /** Risk level. {@code HIGH} requires user confirmation. */
    RiskLevel risk() default RiskLevel.LOW;

    enum RiskLevel { LOW, MEDIUM, HIGH }
}
