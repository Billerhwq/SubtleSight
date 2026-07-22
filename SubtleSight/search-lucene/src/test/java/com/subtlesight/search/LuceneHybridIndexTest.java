package com.subtlesight.search;

import com.subtlesight.domain.Hashing;
import com.subtlesight.domain.Models.DocumentVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LuceneHybridIndexTest {
    @TempDir Path temp;
    @Test void searchesChineseWithHybridProjection() {
        try (var index = new LuceneHybridIndex(temp)) {
            UUID id = UUID.randomUUID();
            index.index(new DocumentVersion(id, UUID.randomUUID(), "人工智能监管新规", "author", Instant.now(), "zh",
                    "https://example.com", "监管部门发布人工智能模型安全评估要求", Hashing.sha256("text"), null, "v1", null, false, Instant.now()),
                    Set.of("监管部门"), Set.of("AI监管"));
            index.commit();
            assertThat(index.search("人工智能 安全", Set.of("document"), 10)).extracting(r -> r.id()).contains(id);
            assertThat(index.search("人工智能", Set.of("story"), 10)).isEmpty();
        }
    }
}
