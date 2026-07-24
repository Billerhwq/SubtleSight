package com.subtlesight.domain;

import com.subtlesight.domain.TraceableQa.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceableQaTest {
    @Test void validatesScopeVersionUnitsAndEmbeddingProfiles() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new ResourceVersionKey(ResourceType.FILE, id, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScopeRef(ScopeType.DRAW_NODE, null, id, List.of())).isInstanceOf(IllegalArgumentException.class);
        ScopeRef scope = new ScopeRef(ScopeType.DRAW_NODE, null, id, List.of("node-a"));
        assertThat(scope.nodeIds()).containsExactly("node-a");
        assertThatThrownBy(() -> new EmbeddingProfile("x", "p", "m", "1", 0, "COSINE", 1, true, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
        KnowledgeUnit unit = new KnowledgeUnit(UUID.randomUUID(), ResourceType.DOCUMENT, id, "3", null,
                UnitType.PARAGRAPH, "block:a", "可信内容", "", "{}", Hashing.sha256("可信内容"), Instant.EPOCH);
        assertThat(unit.versionKey().externalKey()).isEqualTo("DOCUMENT:" + id + ":3");
    }
}
