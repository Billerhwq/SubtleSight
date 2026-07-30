package com.subtlesight.agent.tools.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Runtime policy kept separate from the model-facing tool contract. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ToolRuntime {
    Location location() default Location.SERVER;
    SideEffect sideEffect() default SideEffect.READ;
    String[] contextRequirements() default {};
    String[] permissions() default {};
    Confirmation confirmation() default Confirmation.POLICY;
    long timeoutMs() default 30_000;
    int maxAttempts() default 1;
    long retryBackoffMs() default 0;
    String[] retryableErrorCodes() default {};
    Idempotency idempotency() default Idempotency.NOT_SUPPORTED;
    long idempotencyTtlSeconds() default 0;
    Concurrency concurrency() default Concurrency.PARALLEL;
    boolean supportsCancellation() default true;

    enum Location { SERVER, CLIENT, WORKER }
    enum SideEffect { NONE, READ, WRITE, EXTERNAL }
    enum Confirmation { NEVER, POLICY, ALWAYS }
    enum Idempotency { NOT_SUPPORTED, OPTIONAL, REQUIRED }
    enum Concurrency { PARALLEL, SERIAL_PER_RESOURCE, GLOBAL_SERIAL }
}
