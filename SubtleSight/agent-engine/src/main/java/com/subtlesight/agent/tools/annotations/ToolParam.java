package com.subtlesight.agent.tools.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Describes a tool method parameter. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ToolParam {
    /** Parameter name exposed to planners. */
    String name();
    /** Human-readable parameter description. */
    String description();
    /** Whether this parameter must be provided. Default true. */
    boolean required() default true;
}
