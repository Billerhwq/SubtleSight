package com.subtlesight.search;

import com.subtlesight.domain.Hashing;
import com.subtlesight.domain.TraceableQa.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KnowledgeUnitIndexContractTest {
    @TempDir Path temp;

    @Test void lexicalAndSemanticIndexesAreScopedIdempotentAndDeletable() {
        UUID resource = UUID.randomUUID();
        KnowledgeUnit relevant = unit(resource, "1", "人工智能监管要求可追溯引用", "block:a");
        KnowledgeUnit outside = unit(UUID.randomUUID(), "1", "人工智能监管的无关资料", "block:b");
        var embedding = new LocalFeatureEmbeddingProvider();
        try (var lexical = new LuceneLexicalUnitIndex(temp.resolve("lexical"));
             var semantic = new LuceneSemanticUnitIndex(temp.resolve("semantic"))) {
            lexical.upsert(List.of(relevant, outside));
            lexical.upsert(List.of(relevant));
            lexical.commit();
            assertThat(lexical.search("可追溯引用", Set.of(relevant.id()), 10))
                    .extracting(UnitHit::unitId).containsExactly(relevant.id());
            assertThat(lexical.search("监管", Set.of(), 10)).isEmpty();

            List<KnowledgeUnit> units = List.of(relevant, outside);
            List<float[]> vectors = embedding.embed(units.stream().map(KnowledgeUnit::text).toList());
            semantic.upsert(embedding.profile(), units, vectors);
            semantic.upsert(embedding.profile(), units, vectors);
            semantic.commit();
            assertThat(semantic.search(embedding.profile(), embedding.embed(List.of("引用监管")).getFirst(), Set.of(relevant.id()), 10))
                    .extracting(UnitHit::unitId).containsExactly(relevant.id());
            assertThatThrownBy(() -> semantic.upsert(embedding.profile(), List.of(relevant), List.of(new float[2])))
                    .isInstanceOf(IllegalArgumentException.class);

            lexical.delete(relevant.versionKey()); lexical.commit();
            semantic.delete(embedding.profile(), relevant.versionKey()); semantic.commit();
            assertThat(lexical.search("可追溯引用", Set.of(relevant.id()), 10)).isEmpty();
            assertThat(semantic.search(embedding.profile(), embedding.embed(List.of("引用监管")).getFirst(), Set.of(relevant.id()), 10)).isEmpty();
        }
    }

    private static KnowledgeUnit unit(UUID resource, String version, String text, String locator) {
        return new KnowledgeUnit(UUID.randomUUID(), ResourceType.DOCUMENT, resource, version, null,
                UnitType.PARAGRAPH, locator, text, "上下文", "{}", Hashing.sha256(text), Instant.EPOCH);
    }
}
