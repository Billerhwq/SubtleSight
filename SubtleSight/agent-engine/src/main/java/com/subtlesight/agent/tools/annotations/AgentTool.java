package com.subtlesight.agent.tools.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a method as a callable agent tool. Methods must return {@code Map<String,Object>}. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AgentTool {
    /** Stable internal identifier, normally domain.resource.action. */
    String id() default "";
    /** Unique tool name (e.g. "search_local"). */
    String name();
    /** Independently versioned tool contract. */
    String version() default "1.0.0";
    /** Human-readable description for planners and LLM function calling. */
    String description();
    /** Request strict JSON Schema conformance when the generated schema supports it. */
    boolean strict() default true;
    /** Risk level. {@code HIGH} requires user confirmation. */
    RiskLevel risk() default RiskLevel.LOW;

    enum RiskLevel { LOW, MEDIUM, HIGH }
}
