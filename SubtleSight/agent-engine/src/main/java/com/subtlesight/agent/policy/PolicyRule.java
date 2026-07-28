package com.subtlesight.agent.policy;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable rule for a single tool in the {@link ActionPolicy} matrix.
 */
public record PolicyRule(boolean isDenied, boolean needsConfirm, int maxCalls, Duration window) {

    public PolicyRule {
        if (maxCalls < 0) throw new IllegalArgumentException("maxCalls must be >= 0");
        if (isDenied && needsConfirm)
            throw new IllegalArgumentException("denied and confirm are mutually exclusive");
    }

    /** Tool is always allowed with no restrictions. */
    public static PolicyRule allow() { return new PolicyRule(false, false, 0, null); }

    /** Tool always requires user confirmation. */
    public static PolicyRule requireConfirm() { return new PolicyRule(false, true, 0, null); }

    /** Tool is completely denied for agent use. */
    public static PolicyRule deny() { return new PolicyRule(true, false, 0, null); }

    /** Tool is allowed but rate-limited. */
    public static PolicyRule rateLimit(int maxCalls, Duration window) {
        return new PolicyRule(false, false, maxCalls, Objects.requireNonNull(window));
    }
}
