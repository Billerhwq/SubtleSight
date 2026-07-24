package com.subtlesight.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.document.DocumentProcessor;
import com.subtlesight.domain.Hashing;
import com.subtlesight.domain.TraceableQa.IndexStatus;
import com.subtlesight.domain.TraceableQa.KnowledgeUnit;
import com.subtlesight.domain.TraceableQa.ResourceType;
import com.subtlesight.domain.TraceableQa.ResourceVersionKey;
import com.subtlesight.domain.TraceableQa.ScopeRef;
import com.subtlesight.domain.TraceableQa.ScopeType;
import com.subtlesight.domain.TraceableQa.UnitType;
import com.subtlesight.search.LocalFeatureEmbeddingProvider;
import com.subtlesight.search.LuceneLexicalUnitIndex;
import com.subtlesight.search.LuceneSemanticUnitIndex;
import com.subtlesight.storage.sqlite.SqliteDataSourceFactory;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeDocument;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeDocumentVersion;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeFile;
import com.subtlesight.storage.sqlite.SqliteTraceableQaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeUnitIndexingServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    @TempDir Path temp;

    @Test
    void indexesDocumentParagraphsAndDrawNodesIntoBothSearchProjections() throws Exception {
        var dataSource = SqliteDataSourceFactory.create(temp.resolve("qa-index.db"));
        var json = new ObjectMapper();
        var knowledge = new SqliteKnowledgeRepository(dataSource);
        var repository = new SqliteTraceableQaRepository(
                dataSource, json, Clock.fixed(NOW, ZoneOffset.UTC));
        var embeddings = new LocalFeatureEmbeddingProvider();
        UUID documentId = UUID.randomUUID();
        KnowledgeDocument document = new KnowledgeDocument(
                documentId, null, "Release review",
                "<h2 data-block-id=\"summary\">Release status</h2>"
                        + "<p data-block-id=\"delay\">The API freeze moved to Friday and compressed the test window.</p>",
                "{\"nodes\":["
                        + "{\"id\":\"api\",\"kind\":\"note\",\"x\":10,\"y\":20,\"width\":180,\"height\":90,\"label\":\"API freeze Friday\"},"
                        + "{\"id\":\"test\",\"kind\":\"note\",\"x\":260,\"y\":20,\"width\":180,\"height\":90,\"label\":\"Test window compressed\"}],"
                        + "\"edges\":[{\"id\":\"e1\",\"from\":\"api\",\"to\":\"test\"}]}",
                1, NOW, NOW);
        knowledge.insertDocument(document);
        knowledge.insertDocumentVersion(new KnowledgeDocumentVersion(
                documentId, 1, document.title(), document.contentHtml(), document.drawingJson(), "create", NOW));

        try (var lexical = new LuceneLexicalUnitIndex(temp.resolve("lexical"));
             var semantic = new LuceneSemanticUnitIndex(temp.resolve("semantic"))) {
            var service = new KnowledgeUnitIndexingService(
                    knowledge, repository, lexical, semantic, embeddings,
                    new DocumentProcessor(Clock.fixed(NOW, ZoneOffset.UTC)), json,
                    temp.resolve("knowledge"), Clock.fixed(NOW, ZoneOffset.UTC));

            List<KnowledgeUnitIndexingService.IndexResult> results = service.indexDocument(documentId, 1);

            ResourceVersionKey documentKey = new ResourceVersionKey(ResourceType.DOCUMENT, documentId, "1");
            ResourceVersionKey drawKey = new ResourceVersionKey(ResourceType.DRAW_NODE, documentId, "1");
            assertThat(results).extracting(KnowledgeUnitIndexingService.IndexResult::unitCount)
                    .containsExactly(2, 2);
            assertThat(repository.indexStatus(documentKey)).isEqualTo(IndexStatus.READY);
            assertThat(repository.indexStatus(drawKey)).isEqualTo(IndexStatus.READY);

            List<KnowledgeUnit> documentUnits = repository.units(documentKey);
            List<KnowledgeUnit> drawUnits = repository.units(drawKey);
            assertThat(documentUnits).extracting(KnowledgeUnit::stableLocator)
                    .containsExactlyInAnyOrder("block:summary", "block:delay");
            assertThat(drawUnits).allMatch(unit -> unit.unitType() == UnitType.NODE)
                    .extracting(KnowledgeUnit::stableLocator)
                    .containsExactlyInAnyOrder("node:api", "node:test");
            assertThat(drawUnits.stream().filter(unit -> unit.stableLocator().equals("node:api"))
                    .findFirst().orElseThrow().metadataJson())
                    .contains("\"x\":10.0", "\"neighbors\":[\"Test window compressed\"]");

            Set<UUID> documentScope = Set.copyOf(documentUnits.stream().map(KnowledgeUnit::id).toList());
            assertThat(lexical.search("API freeze", documentScope, 5))
                    .extracting(hit -> hit.unitId()).contains(documentUnits.get(1).id());
            assertThat(semantic.search(embeddings.profile(), embeddings.embed(List.of("Friday test delay")).getFirst(),
                    documentScope, 5)).isNotEmpty();

            var resolved = repository.resolveScope(List.of(
                    new ScopeRef(ScopeType.DOCUMENT, documentId, null, List.of()),
                    new ScopeRef(ScopeType.DRAW_NODE, null, documentId, List.of("api"))));
            assertThat(resolved.resources()).contains(documentKey, drawKey);
            assertThat(resolved.unitIds()).containsAll(documentUnits.stream().map(KnowledgeUnit::id).toList())
                    .contains(drawUnits.stream().filter(unit -> unit.stableLocator().equals("node:api"))
                            .findFirst().orElseThrow().id())
                    .doesNotContain(drawUnits.stream().filter(unit -> unit.stableLocator().equals("node:test"))
                            .findFirst().orElseThrow().id());
        }
    }

    @Test
    void extractsAndIndexesAnUploadedFileWithVersionProtection() throws Exception {
        var dataSource = SqliteDataSourceFactory.create(temp.resolve("qa-file-index.db"));
        var json = new ObjectMapper();
        var knowledge = new SqliteKnowledgeRepository(dataSource);
        var repository = new SqliteTraceableQaRepository(
                dataSource, json, Clock.fixed(NOW, ZoneOffset.UTC));
        var embeddings = new LocalFeatureEmbeddingProvider();
        Path storage = Files.createDirectories(temp.resolve("file-storage"));
        String contents = "Customer interviews confirm that offline review is required before launch.";
        UUID fileId = UUID.randomUUID();
        String storagePath = fileId + ".txt";
        Files.writeString(storage.resolve(storagePath), contents);
        KnowledgeFile file = new KnowledgeFile(
                fileId, null, "interviews.txt", "txt", "text/plain",
                contents.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                Hashing.sha256(contents), storagePath, NOW, NOW);
        knowledge.insertFile(file);

        try (var lexical = new LuceneLexicalUnitIndex(temp.resolve("file-lexical"));
             var semantic = new LuceneSemanticUnitIndex(temp.resolve("file-semantic"))) {
            var service = new KnowledgeUnitIndexingService(
                    knowledge, repository, lexical, semantic, embeddings,
                    new DocumentProcessor(Clock.fixed(NOW, ZoneOffset.UTC)), json,
                    storage, Clock.fixed(NOW, ZoneOffset.UTC));

            var result = service.indexFile(fileId, file.sha256());
            List<KnowledgeUnit> units = repository.units(result.resource());
            assertThat(result.unitCount()).isEqualTo(1);
            assertThat(repository.indexStatus(result.resource())).isEqualTo(IndexStatus.READY);
            assertThat(units.getFirst().text()).contains("offline review is required");
            assertThat(units.getFirst().metadataJson()).contains("interviews.txt", "text/plain");
            Set<UUID> scope = Set.of(units.getFirst().id());
            assertThat(lexical.search("offline review", scope, 5)).hasSize(1);
            assertThat(semantic.search(embeddings.profile(), embeddings.embed(List.of("launch review")).getFirst(),
                    scope, 5)).hasSize(1);

            assertThat(org.assertj.core.api.Assertions.catchThrowable(
                    () -> service.indexFile(fileId, "outdated-sha")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("version changed");
        }
    }
}
