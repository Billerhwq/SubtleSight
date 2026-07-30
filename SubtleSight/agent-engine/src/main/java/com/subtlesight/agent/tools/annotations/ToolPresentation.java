package com.subtlesight.agent.tools.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Optional UI metadata for consistent progress presentation. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolPresentation {
    String label();
    String category() default "Agent";
    String icon() default "wrench";
    String queued() default "准备执行";
    String running() default "正在执行";
    String verifying() default "正在验证结果";
    String succeeded() default "执行完成";
}
