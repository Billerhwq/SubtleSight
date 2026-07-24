package com.subtlesight.storage.sqlite;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.application.TraceableQaPorts.Repository;
import com.subtlesight.domain.TraceableQa.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SqliteTraceableQaRepository implements Repository {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public SqliteTraceableQaRepository(DataSource dataSource, ObjectMapper json, Clock clock) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.json = json;
        this.clock = clock;
    }

    private static final RowMapper<KnowledgeUnit> UNIT_MAPPER = (rs, n) -> new KnowledgeUnit(
            UUID.fromString(rs.getString("id")), ResourceType.valueOf(rs.getString("resource_type")),
            UUID.fromString(rs.getString("resource_id")), rs.getString("resource_version"),
            nullableUuid(rs.getString("folder_id")), UnitType.valueOf(rs.getString("unit_type")),
            rs.getString("stable_locator"), rs.getString("text"), rs.getString("context_text"),
            rs.getString("metadata_json"), rs.getString("content_hash"), Instant.parse(rs.getString("created_at")));

    private final RowMapper<QaAnswer> answerMapper = (rs, n) -> new QaAnswer(
            UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("conversation_id")),
            nullableUuid(rs.getString("parent_answer_id")), rs.getString("question"),
            AnswerStatus.valueOf(rs.getString("status")), rs.getString("direct_answer"),
            readStrings(rs.getString("gaps_json")), rs.getString("provider"), rs.getString("model"),
            rs.getString("error_code"), Instant.parse(rs.getString("created_at")),
            nullableInstant(rs.getString("completed_at")));

    private static final RowMapper<QaClaim> CLAIM_MAPPER = (rs, n) -> new QaClaim(
            UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("answer_id")),
            rs.getInt("ordinal"), ClaimType.valueOf(rs.getString("claim_type")),
            rs.getString("statement"), VerificationStatus.valueOf(rs.getString("verification_status")));

    private static final RowMapper<QaCitation> CITATION_MAPPER = (rs, n) -> new QaCitation(
            UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("claim_id")),
            CitationRelation.valueOf(rs.getString("relation")), ResourceType.valueOf(rs.getString("resource_type")),
            UUID.fromString(rs.getString("resource_id")), rs.getString("resource_version"),
            UUID.fromString(rs.getString("unit_id")), rs.getString("locator_json"), rs.getString("exact_quote"),
            rs.getString("snapshot_hash"), rs.getString("source_family"), rs.getInt("rank"),
            Instant.parse(rs.getString("created_at")));

    @Override
    public void upsertUnits(ResourceVersionKey version, List<KnowledgeUnit> units) {
        for (KnowledgeUnit unit : units) {
            if (!unit.versionKey().equals(version)) throw new IllegalArgumentException("unit version mismatch");
            jdbc.update("""
                    INSERT INTO knowledge_units(id,resource_type,resource_id,resource_version,folder_id,unit_type,
                      stable_locator,text,context_text,metadata_json,content_hash,created_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                    ON CONFLICT(id) DO UPDATE SET text=excluded.text,context_text=excluded.context_text,
                      metadata_json=excluded.metadata_json,content_hash=excluded.content_hash
                    """, unit.id().toString(), unit.resourceType().name(), unit.resourceId().toString(),
                    unit.resourceVersion(), unit.folderId() == null ? null : unit.folderId().toString(),
                    unit.unitType().name(), unit.stableLocator(), unit.text(), unit.contextText(),
                    unit.metadataJson(), unit.contentHash(), unit.createdAt().toString());
        }
    }

    @Override public List<KnowledgeUnit> units(ResourceVersionKey version) {
        return jdbc.query("SELECT * FROM knowledge_units WHERE resource_type=? AND resource_id=? AND resource_version=? ORDER BY stable_locator",
                UNIT_MAPPER, version.type().name(), version.resourceId().toString(), version.version());
    }

    @Override public List<KnowledgeUnit> unitsByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        String sql = "SELECT * FROM knowledge_units WHERE id IN (" + placeholders(ids.size()) + ")";
        return jdbc.query(sql, UNIT_MAPPER, ids.stream().map(UUID::toString).toArray());
    }

    @Override public Optional<KnowledgeUnit> unit(UUID id) {
        return jdbc.query("SELECT * FROM knowledge_units WHERE id=?", UNIT_MAPPER, id.toString()).stream().findFirst();
    }

    @Override
    public ResolvedScope resolveScope(List<ScopeRef> refs) {
        if (refs == null || refs.isEmpty()) throw new IllegalArgumentException("at least one scope is required");
        Map<String, ResourceVersionKey> resources = new LinkedHashMap<>();
        Set<UUID> unitIds = new LinkedHashSet<>();
        List<ExcludedResource> excluded = new ArrayList<>();
        for (ScopeRef ref : refs) resolveOne(ref, resources, unitIds, excluded);
        return new ResolvedScope(List.copyOf(resources.values()), List.copyOf(unitIds), excluded);
    }

    private void resolveOne(ScopeRef ref, Map<String, ResourceVersionKey> resources,
                            Set<UUID> unitIds, List<ExcludedResource> excluded) {
        switch (ref.type()) {
            case FILE -> addCurrent(ResourceType.FILE, ref.id(), null, resources, unitIds, excluded);
            case DOCUMENT -> addCurrent(ResourceType.DOCUMENT, ref.id(), null, resources, unitIds, excluded);
            case DRAWING -> addCurrent(ResourceType.DRAW_NODE, ref.id(), null, resources, unitIds, excluded);
            case DRAW_NODE -> addCurrent(ResourceType.DRAW_NODE, ref.resourceId(), ref.nodeIds(), resources, unitIds, excluded);
            case FOLDER -> resolveFolder(ref.id(), resources, unitIds, excluded);
            case KNOWLEDGE_BASE -> resolveKnowledgeBase(resources, unitIds, excluded);
        }
    }

    private void resolveFolder(UUID folderId, Map<String, ResourceVersionKey> resources,
                               Set<UUID> unitIds, List<ExcludedResource> excluded) {
        Integer exists = jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_folders WHERE id=?", Integer.class, folderId.toString());
        if (exists == null || exists == 0) { excluded.add(new ExcludedResource("FOLDER:" + folderId, "NOT_FOUND")); return; }
        List<String> folders = jdbc.queryForList("""
                WITH RECURSIVE descendants(id) AS (
                  SELECT id FROM knowledge_folders WHERE id=?
                  UNION ALL SELECT f.id FROM knowledge_folders f JOIN descendants d ON f.parent_id=d.id
                ) SELECT id FROM descendants
                """, String.class, folderId.toString());
        for (String folder : folders) {
            jdbc.queryForList("SELECT id FROM knowledge_files WHERE folder_id=?", String.class, folder)
                    .forEach(id -> addCurrent(ResourceType.FILE, UUID.fromString(id), null, resources, unitIds, excluded));
            jdbc.queryForList("SELECT id FROM knowledge_documents WHERE folder_id=?", String.class, folder)
                    .forEach(id -> {
                        UUID documentId = UUID.fromString(id);
                        addCurrent(ResourceType.DOCUMENT, documentId, null, resources, unitIds, excluded);
                        addCurrent(ResourceType.DRAW_NODE, documentId, null, resources, unitIds, excluded);
                    });
        }
    }

    private void resolveKnowledgeBase(Map<String, ResourceVersionKey> resources,
                                      Set<UUID> unitIds, List<ExcludedResource> excluded) {
        jdbc.queryForList("SELECT id FROM knowledge_files", String.class)
                .forEach(id -> addCurrent(ResourceType.FILE, UUID.fromString(id), null, resources, unitIds, excluded));
        jdbc.queryForList("SELECT id FROM knowledge_documents", String.class).forEach(id -> {
            UUID documentId = UUID.fromString(id);
            addCurrent(ResourceType.DOCUMENT, documentId, null, resources, unitIds, excluded);
            addCurrent(ResourceType.DRAW_NODE, documentId, null, resources, unitIds, excluded);
        });
    }

    private void addCurrent(ResourceType type, UUID id, List<String> locators,
                            Map<String, ResourceVersionKey> resources, Set<UUID> unitIds,
                            List<ExcludedResource> excluded) {
        Optional<String> current = currentVersion(type, id);
        String reference = type + ":" + id;
        if (current.isEmpty()) { excluded.add(new ExcludedResource(reference, "NOT_FOUND")); return; }
        ResourceVersionKey key = new ResourceVersionKey(type, id, current.get());
        if (indexStatus(key) != IndexStatus.READY) { excluded.add(new ExcludedResource(reference, "NOT_READY")); return; }
        List<KnowledgeUnit> candidates = units(key);
        if (locators != null && !locators.isEmpty()) {
            Set<String> requested = new LinkedHashSet<>(locators);
            candidates = candidates.stream().filter(unit -> requested.contains(stripNodePrefix(unit.stableLocator()))).toList();
        }
        if (candidates.isEmpty()) { excluded.add(new ExcludedResource(reference, "NO_EXTRACTABLE_CONTENT")); return; }
        resources.putIfAbsent(key.externalKey(), key);
        candidates.forEach(unit -> unitIds.add(unit.id()));
    }

    @Override public Optional<String> currentVersion(ResourceType type, UUID resourceId) {
        if (type == ResourceType.FILE)
            return jdbc.queryForList("SELECT sha256 FROM knowledge_files WHERE id=?", String.class, resourceId.toString()).stream().findFirst();
        return jdbc.queryForList("SELECT CAST(version AS TEXT) FROM knowledge_documents WHERE id=?", String.class, resourceId.toString()).stream().findFirst();
    }

    @Override public void markIndexState(ResourceVersionKey version, IndexStatus status, String errorCode, int unitCount) {
        jdbc.update("""
                INSERT INTO knowledge_index_state(resource_type,resource_id,resource_version,status,error_code,unit_count,updated_at)
                VALUES(?,?,?,?,?,?,?)
                ON CONFLICT(resource_type,resource_id,resource_version) DO UPDATE SET
                  status=excluded.status,error_code=excluded.error_code,unit_count=excluded.unit_count,updated_at=excluded.updated_at
                """, version.type().name(), version.resourceId().toString(), version.version(), status.name(),
                errorCode, unitCount, clock.instant().toString());
    }

    @Override public IndexStatus indexStatus(ResourceVersionKey version) {
        return jdbc.queryForList("SELECT status FROM knowledge_index_state WHERE resource_type=? AND resource_id=? AND resource_version=?",
                        String.class, version.type().name(), version.resourceId().toString(), version.version())
                .stream().findFirst().map(IndexStatus::valueOf).orElse(IndexStatus.PENDING);
    }

    @Override public void saveEmbeddingProfile(EmbeddingProfile profile) {
        jdbc.update("""
                INSERT INTO embedding_profiles(id,provider,model,model_revision,dimensions,distance,chunk_schema_version,active,created_at)
                VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET active=excluded.active
                """, profile.id(), profile.provider(), profile.model(), profile.modelRevision(), profile.dimensions(),
                profile.distance(), profile.chunkSchemaVersion(), profile.active() ? 1 : 0, profile.createdAt().toString());
    }

    @Override public void markEmbeddingIndexed(UUID unitId, String profileId, String contentHash, String externalPointId) {
        jdbc.update("""
                INSERT INTO unit_embedding_state(unit_id,profile_id,content_hash,external_point_id,status,indexed_at)
                VALUES(?,?,?,?,?,?) ON CONFLICT(unit_id,profile_id) DO UPDATE SET
                  content_hash=excluded.content_hash,external_point_id=excluded.external_point_id,status=excluded.status,indexed_at=excluded.indexed_at,error_code=NULL
                """, unitId.toString(), profileId, contentHash, externalPointId, "READY", clock.instant().toString());
    }

    @Override public QaConversation saveConversation(QaConversation value) {
        jdbc.update("""
                INSERT INTO qa_conversations(id,title,created_at,updated_at) VALUES(?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET title=excluded.title,updated_at=excluded.updated_at
                """, value.id().toString(), value.title(), value.createdAt().toString(), value.updatedAt().toString());
        return value;
    }

    @Override public Optional<QaConversation> conversation(UUID id) {
        return jdbc.query("SELECT * FROM qa_conversations WHERE id=?", (rs, n) -> new QaConversation(
                UUID.fromString(rs.getString("id")), rs.getString("title"), Instant.parse(rs.getString("created_at")),
                Instant.parse(rs.getString("updated_at"))), id.toString()).stream().findFirst();
    }

    @Override public QaAnswer saveAnswer(QaAnswer value, List<UUID> scopeUnitIds, String scopeSnapshotJson) {
        jdbc.update("""
                INSERT INTO qa_answers(id,conversation_id,parent_answer_id,question,status,scope_snapshot_json,direct_answer,
                  gaps_json,provider,model,error_code,created_at,completed_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET status=excluded.status,direct_answer=excluded.direct_answer,
                  gaps_json=excluded.gaps_json,provider=excluded.provider,model=excluded.model,
                  error_code=excluded.error_code,completed_at=excluded.completed_at
                """, value.id().toString(), value.conversationId().toString(), uuid(value.parentAnswerId()), value.question(),
                value.status().name(), scopeSnapshotJson == null ? "{}" : scopeSnapshotJson, value.directAnswer(),
                write(value.gaps()), value.provider(), value.model(), value.errorCode(), value.createdAt().toString(),
                value.completedAt() == null ? null : value.completedAt().toString());
        if (scopeUnitIds != null) for (UUID unitId : scopeUnitIds)
            jdbc.update("INSERT INTO qa_answer_units(answer_id,unit_id) VALUES(?,?) ON CONFLICT DO NOTHING",
                    value.id().toString(), unitId.toString());
        return value;
    }

    @Override public Optional<QaAnswer> answer(UUID id) {
        return jdbc.query("SELECT * FROM qa_answers WHERE id=?", answerMapper, id.toString()).stream().findFirst();
    }

    @Override public List<UUID> answerUnitIds(UUID answerId) {
        return jdbc.query("SELECT unit_id FROM qa_answer_units WHERE answer_id=? ORDER BY unit_id",
                (rs, n) -> UUID.fromString(rs.getString(1)), answerId.toString());
    }

    @Override public String answerScopeSnapshot(UUID answerId) {
        return jdbc.queryForObject("SELECT scope_snapshot_json FROM qa_answers WHERE id=?", String.class, answerId.toString());
    }

    @Override public QaClaim saveClaim(QaClaim value) {
        jdbc.update("""
                INSERT INTO qa_claims(id,answer_id,ordinal,claim_type,statement,verification_status) VALUES(?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET claim_type=excluded.claim_type,statement=excluded.statement,
                  verification_status=excluded.verification_status
                """, value.id().toString(), value.answerId().toString(), value.ordinal(), value.type().name(),
                value.statement(), value.verificationStatus().name());
        return value;
    }

    @Override public Optional<QaClaim> claim(UUID id) {
        return jdbc.query("SELECT * FROM qa_claims WHERE id=?", CLAIM_MAPPER, id.toString()).stream().findFirst();
    }

    @Override public QaCitation saveCitation(QaCitation value) {
        jdbc.update("""
                INSERT INTO qa_citations(id,claim_id,relation,resource_type,resource_id,resource_version,unit_id,
                  locator_json,exact_quote,snapshot_hash,source_family,rank,created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET exact_quote=excluded.exact_quote,snapshot_hash=excluded.snapshot_hash
                """, value.id().toString(), value.claimId().toString(), value.relation().name(),
                value.resourceType().name(), value.resourceId().toString(), value.resourceVersion(),
                value.unitId().toString(), value.locatorJson(), value.exactQuote(), value.snapshotHash(),
                value.sourceFamily(), value.rank(), value.createdAt().toString());
        return value;
    }

    @Override public List<QaClaim> claims(UUID answerId) {
        return jdbc.query("SELECT * FROM qa_claims WHERE answer_id=? ORDER BY ordinal", CLAIM_MAPPER, answerId.toString());
    }

    @Override public List<QaCitation> citationsForClaim(UUID claimId) {
        return jdbc.query("SELECT * FROM qa_citations WHERE claim_id=? ORDER BY rank", CITATION_MAPPER, claimId.toString());
    }

    @Override public Optional<QaCitation> citation(UUID id) {
        return jdbc.query("SELECT * FROM qa_citations WHERE id=?", CITATION_MAPPER, id.toString()).stream().findFirst();
    }

    private List<String> readStrings(String raw) {
        try { return raw == null ? List.of() : json.readValue(raw, STRING_LIST); }
        catch (Exception e) { throw new IllegalStateException("invalid stored JSON", e); }
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("cannot serialize JSON", e); }
    }
    private static String uuid(UUID value) { return value == null ? null : value.toString(); }
    private static UUID nullableUuid(String value) { return value == null ? null : UUID.fromString(value); }
    private static Instant nullableInstant(String value) { return value == null ? null : Instant.parse(value); }
    private static String placeholders(int count) { return String.join(",", java.util.Collections.nCopies(count, "?")); }
    private static String stripNodePrefix(String locator) { return locator.startsWith("node:") ? locator.substring(5) : locator; }
}
