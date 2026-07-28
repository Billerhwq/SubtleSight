package com.subtlesight.agent.policy;

import java.util.Objects;

/** Outcome of an {@link ActionPolicy} evaluation. */
public record PolicyDecision(boolean allowed, boolean confirmationRequired, String reason) {

    public static PolicyDecision allow() { return new PolicyDecision(true, false, ""); }

    public static PolicyDecision deny(String reason) {
        return new PolicyDecision(false, false, Objects.requireNonNull(reason));
    }

    public static PolicyDecision needsConfirmation(String reason) {
        return new PolicyDecision(true, true, Objects.requireNonNull(reason));
    }
}
