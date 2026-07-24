package com.subtlesight.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.application.TraceableQaPorts.EmbeddingProvider;
import com.subtlesight.application.TraceableQaPorts.LexicalUnitIndex;
import com.subtlesight.application.TraceableQaPorts.Repository;
import com.subtlesight.application.TraceableQaPorts.SemanticUnitIndex;
import com.subtlesight.document.DocumentProcessor;
import com.subtlesight.domain.Hashing;
import com.subtlesight.domain.TraceableQa.*;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeDocumentVersion;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeFile;
import org.apache.tika.Tika;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class KnowledgeUnitIndexingService {
    private static final int MAX_EXTRACTED_CHARS = 4_000_000;
    private final SqliteKnowledgeRepository knowledge;
    private final Repository repository;
    private final LexicalUnitIndex lexical;
    private final SemanticUnitIndex semantic;
    private final EmbeddingProvider embeddings;
    private final DocumentProcessor processor;
    private final ObjectMapper json;
    private final Path storageDir;
    private final Clock clock;
    private final Tika tika = new Tika();

    public KnowledgeUnitIndexingService(SqliteKnowledgeRepository knowledge, Repository repository,
                                        LexicalUnitIndex lexical, SemanticUnitIndex semantic,
                                        EmbeddingProvider embeddings, DocumentProcessor processor,
                                        ObjectMapper json, Path storageDir, Clock clock) {
        this.knowledge = knowledge;
        this.repository = repository;
        this.lexical = lexical;
        this.semantic = semantic;
        this.embeddings = embeddings;
        this.processor = processor;
        this.json = json;
        this.storageDir = storageDir;
        this.clock = clock;
    }

    public IndexResult indexFile(UUID fileId, String expectedSha256) {
        KnowledgeFile file = knowledge.findFile(fileId).orElseThrow(() -> new IllegalArgumentException("file not found"));
        if (!file.sha256().equals(expectedSha256)) throw new IllegalArgumentException("file version changed");
        ResourceVersionKey key = new ResourceVersionKey(ResourceType.FILE, file.id(), file.sha256());
        repository.markIndexState(key, IndexStatus.INDEXING, null, 0);
        try {
            Path path = storageDir.resolve(file.storagePath()).normalize();
            if (!path.startsWith(storageDir) || !Files.isRegularFile(path)) throw new IllegalArgumentException("stored file missing");
            String text = tika.parseToString(path);
            List<KnowledgeUnit> units = fileUnits(file, limit(text));
            project(key, units);
            return new IndexResult(key, units.size());
        } catch (Exception failure) {
            repository.markIndexState(key, IndexStatus.ERROR, errorCode(failure), 0);
            throw new IllegalStateException("file indexing failed", failure);
        }
    }

    public List<IndexResult> indexDocument(UUID documentId, int version) {
        KnowledgeDocumentVersion snapshot = knowledge.findDocumentVersion(documentId, version)
                .orElseThrow(() -> new IllegalArgumentException("document version not found"));
        UUID folderId = knowledge.findDocument(documentId).map(SqliteKnowledgeRepository.KnowledgeDocument::folderId).orElse(null);
        ResourceVersionKey documentKey = new ResourceVersionKey(ResourceType.DOCUMENT, documentId, String.valueOf(version));
        ResourceVersionKey drawKey = new ResourceVersionKey(ResourceType.DRAW_NODE, documentId, String.valueOf(version));
        List<IndexResult> results = new ArrayList<>();
        results.add(index(documentKey, documentUnits(snapshot, folderId)));
        results.add(index(drawKey, drawUnits(snapshot, folderId)));
        return List.copyOf(results);
    }

    private IndexResult index(ResourceVersionKey key, List<KnowledgeUnit> units) {
        repository.markIndexState(key, IndexStatus.INDEXING, null, 0);
        try {
            project(key, units);
            return new IndexResult(key, units.size());
        } catch (RuntimeException failure) {
            repository.markIndexState(key, IndexStatus.ERROR, errorCode(failure), 0);
            throw failure;
        }
    }

    private void project(ResourceVersionKey key, List<KnowledgeUnit> units) {
        repository.upsertUnits(key, units);
        lexical.upsert(units);
        lexical.commit();
        EmbeddingProfile profile = embeddings.profile();
        repository.saveEmbeddingProfile(profile);
        List<float[]> vectors = embeddings.embed(units.stream().map(unit -> unit.text() + "\n" + unit.contextText()).toList());
        if (vectors.size() != units.size()) throw new IllegalStateException("embedding result size mismatch");
        semantic.upsert(profile, units, vectors);
        semantic.commit();
        for (KnowledgeUnit unit : units)
            repository.markEmbeddingIndexed(unit.id(), profile.id(), unit.contentHash(), pointId(profile.id(), unit.id()).toString());
        repository.markIndexState(key, IndexStatus.READY, null, units.size());
    }

    private List<KnowledgeUnit> fileUnits(KnowledgeFile file, String text) {
        List<String> chunks = processor.chunk(normalize(text), 900, 120);
        ResourceVersionKey key = new ResourceVersionKey(ResourceType.FILE, file.id(), file.sha256());
        List<KnowledgeUnit> units = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String locator = "chunk:" + String.format("%04d", i + 1);
            String context = context(chunks, i);
            units.add(unit(key, file.folderId(), UnitType.SECTION, locator, chunks.get(i), context,
                    Map.of("fileName", file.name(), "mimeType", String.valueOf(file.mimeType()), "ordinal", i + 1)));
        }
        return List.copyOf(units);
    }

    private List<KnowledgeUnit> documentUnits(KnowledgeDocumentVersion snapshot, UUID folderId) {
        var document = Jsoup.parseBodyFragment(snapshot.contentHtml());
        List<Element> blocks = document.select("h1,h2,h3,h4,h5,h6,p,li,blockquote,pre,td,th").stream()
                .filter(element -> !element.text().isBlank()).toList();
        List<String> texts = blocks.stream().map(Element::text).map(String::strip).toList();
        ResourceVersionKey key = new ResourceVersionKey(ResourceType.DOCUMENT, snapshot.documentId(), String.valueOf(snapshot.version()));
        List<KnowledgeUnit> units = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            Element block = blocks.get(i);
            String blockId = block.attr("data-block-id");
            String locator = blockId.isBlank() ? "block:" + String.format("%04d", i + 1) : "block:" + blockId;
            units.add(unit(key, folderId, UnitType.PARAGRAPH, locator, texts.get(i), context(texts, i),
                    Map.of("documentId", snapshot.documentId(), "version", snapshot.version(),
                            "tag", block.tagName(), "blockId", locator.substring(6), "ordinal", i + 1)));
        }
        if (units.isEmpty()) {
            String text = document.text().strip();
            if (!text.isBlank()) units.add(unit(key, folderId, UnitType.SECTION, "block:0001", text, "",
                    Map.of("documentId", snapshot.documentId(), "version", snapshot.version(), "ordinal", 1)));
        }
        return List.copyOf(units);
    }

    private List<KnowledgeUnit> drawUnits(KnowledgeDocumentVersion snapshot, UUID folderId) {
        ResourceVersionKey key = new ResourceVersionKey(ResourceType.DRAW_NODE, snapshot.documentId(), String.valueOf(snapshot.version()));
        try {
            JsonNode drawing = json.readTree(snapshot.drawingJson());
            Map<String, String> labels = new LinkedHashMap<>();
            for (JsonNode node : drawing.path("nodes")) labels.put(node.path("id").asText(), node.path("label").asText());
            Map<String, List<String>> neighbors = new LinkedHashMap<>();
            for (JsonNode edge : drawing.path("edges")) {
                String from = edge.path("from").asText(), to = edge.path("to").asText();
                neighbors.computeIfAbsent(from, ignored -> new ArrayList<>()).add(labels.getOrDefault(to, to));
                neighbors.computeIfAbsent(to, ignored -> new ArrayList<>()).add(labels.getOrDefault(from, from));
            }
            List<KnowledgeUnit> units = new ArrayList<>();
            for (JsonNode node : drawing.path("nodes")) {
                String id = node.path("id").asText(), label = node.path("label").asText().strip();
                if (id.isBlank() || label.isBlank()) continue;
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("documentId", snapshot.documentId()); metadata.put("version", snapshot.version());
                metadata.put("nodeId", id); metadata.put("kind", node.path("kind").asText());
                metadata.put("x", node.path("x").asDouble()); metadata.put("y", node.path("y").asDouble());
                metadata.put("width", node.path("width").asDouble()); metadata.put("height", node.path("height").asDouble());
                metadata.put("neighbors", neighbors.getOrDefault(id, List.of()));
                units.add(unit(key, folderId, UnitType.NODE, "node:" + id, label,
                        String.join("；", neighbors.getOrDefault(id, List.of())), metadata));
            }
            return List.copyOf(units);
        } catch (Exception e) { throw new IllegalArgumentException("invalid drawing snapshot", e); }
    }

    private KnowledgeUnit unit(ResourceVersionKey key, UUID folderId, UnitType type, String locator,
                               String text, String context, Map<String, ?> metadata) {
        String clean = normalize(text);
        UUID id = deterministic(key.externalKey() + ":" + locator);
        try {
            return new KnowledgeUnit(id, key.type(), key.resourceId(), key.version(), folderId, type, locator,
                    clean, context == null ? "" : normalize(context), json.writeValueAsString(metadata),
                    Hashing.sha256(clean), clock.instant());
        } catch (Exception e) { throw new IllegalStateException("cannot create knowledge unit", e); }
    }

    private static String context(List<String> texts, int index) {
        List<String> values = new ArrayList<>();
        if (index > 0) values.add(texts.get(index - 1));
        if (index + 1 < texts.size()) values.add(texts.get(index + 1));
        return String.join("\n", values);
    }
    private static String normalize(String text) { return text == null ? "" : text.replace('\u00a0', ' ').replaceAll("[\\t\\r ]+", " ").replaceAll("\\n{3,}", "\n\n").strip(); }
    private static String limit(String text) { String clean = normalize(text); return clean.length() <= MAX_EXTRACTED_CHARS ? clean : clean.substring(0, MAX_EXTRACTED_CHARS); }
    private static UUID deterministic(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)); }
    private static UUID pointId(String profileId, UUID unitId) { return deterministic(profileId + ":" + unitId); }
    private static String errorCode(Throwable failure) { return failure.getClass().getSimpleName().toUpperCase(java.util.Locale.ROOT); }

    public record IndexResult(ResourceVersionKey resource, int unitCount) {}
}
