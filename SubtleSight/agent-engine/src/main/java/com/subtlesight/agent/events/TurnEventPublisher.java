package com.subtlesight.agent.events;

/**
 * Publishes {@link TurnEvent} instances to subscribers keyed by turnId.
 * Implementations typically delegate to an SSE hub or message queue.
 */
@FunctionalInterface
public interface TurnEventPublisher {
    /** Send a turn event to all subscribers for {@code event.turnId()}. */
    void publish(TurnEvent event);
}
