package com.subtlesight.agent.tools.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares the resource effect a successful tool call must produce. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolEffectSpec {
    Type type();
    String resourceType();
    String[] changedFields() default {};
    boolean requiredOnSuccess() default true;
    boolean verifiable() default true;

    enum Type { RESOURCE_CREATED, RESOURCE_UPDATED, RESOURCE_DELETED, EXTERNAL_DISPATCHED }
}
