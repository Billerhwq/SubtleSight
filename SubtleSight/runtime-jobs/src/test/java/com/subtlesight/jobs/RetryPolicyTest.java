package com.subtlesight.jobs;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {
    @Test void honorsRetryAfterAndNeverRetriesAuthentication() {
        var policy = new RetryPolicy(Duration.ofSeconds(1), Duration.ofMinutes(5), 7);
        assertThat(policy.delay(3, Duration.ofSeconds(17))).isEqualTo(Duration.ofSeconds(17));
        assertThat(policy.retryable("AUTH_401", 1, 3)).isFalse();
        assertThat(policy.retryable("HTTP_503", 1, 3)).isTrue();
    }
    @Test void jitterStaysInsideTwentyPercent() {
        var policy = new RetryPolicy(Duration.ofSeconds(10), Duration.ofMinutes(5), 8);
        var delay = policy.delay(1, null);
        assertThat(delay).isBetween(Duration.ofSeconds(8), Duration.ofSeconds(12));
    }
}
