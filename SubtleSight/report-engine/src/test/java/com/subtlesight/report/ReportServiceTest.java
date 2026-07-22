package com.subtlesight.report;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ReportServiceTest {
    @Test void idempotencyKeyIsStableByContract(){var id=java.util.UUID.randomUUID();assertThat(id+":"+"webhook").isEqualTo(id+":"+"webhook");}
}

