package com.subtlesight.storage.sqlite;

import com.subtlesight.application.Ports.IntelligenceRepository;
import com.subtlesight.domain.Models.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SqliteIntelligenceRepository implements IntelligenceRepository {
    private static final TypeReference<Set<String>> STRING_SET = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public SqliteIntelligenceRepository(DataSource dataSource, ObjectMapper mapper) {
        this.jdbc = JdbcClient.create(dataSource);
        this.json = mapper.copy().findAndRegisterModules();
    }

    @Override public Source saveSource(Source s) {
        jdbc.sql("""
                INSERT INTO sources(id,name,type,kind,endpoint,schedule,cursor,tier,health,topics_json,enabled,version,created_at,updated_at)
                VALUES(:id,:name,:type,:kind,:endpoint,:schedule,:cursor,:tier,:health,:topics,:enabled,:version,:created,:updated)
                ON CONFLICT(id) DO UPDATE SET name=excluded.name,type=excluded.type,kind=excluded.kind,
                endpoint=excluded.endpoint,schedule=excluded.schedule,cursor=excluded.cursor,tier=excluded.tier,
                health=excluded.health,topics_json=excluded.topics_json,enabled=excluded.enabled,
                version=excluded.version,updated_at=excluded.updated_at
                """).params(java.util.Map.ofEntries(
                entry("id", s.id()), entry("name", s.name()), entry("type", s.type()), entry("kind", s.kind()),
                entry("endpoint", s.endpoint()), entry("schedule", s.schedule()), entry("cursor", s.cursor()),
                entry("tier", s.tier()), entry("health", s.health()), entry("topics", write(s.topics())),
                entry("enabled", s.enabled()), entry("version", s.version()), entry("created", s.createdAt()), entry("updated", s.updatedAt())
        )).update();
        return s;
    }

    @Override public List<Source> listSources() {
        return jdbc.sql("SELECT * FROM sources ORDER BY name").query(this::source).list();
    }
    @Override public Optional<Source> findSource(UUID id) {
        return jdbc.sql("SELECT * FROM sources WHERE id=:id").param("id", id.toString()).query(this::source).optional();
    }
    @Override public void disableSource(UUID id, Instant now) {
        jdbc.sql("UPDATE sources SET enabled=0,health='DISABLED',version=version+1,updated_at=:now WHERE id=:id")
                .param("now", now.toString()).param("id", id.toString()).update();
    }

    @Override public RawDocument saveRawDocument(RawDocument d) {
        jdbc.sql("""
                INSERT INTO raw_documents(id,source_id,fetch_run_id,external_id,original_url,canonical_url,mime_type,charset,
                content_hash,blob_hash,content_length,http_status,license,status,observed_at,created_at)
                VALUES(:id,:source,:fetch,:external,:original,:canonical,:mime,:charset,:hash,:blob,:length,:http,:license,:status,:observed,:created)
                ON CONFLICT(content_hash) DO NOTHING
                """).params(java.util.Map.ofEntries(
                entry("id", d.id()), entry("source", d.sourceId()), entry("fetch", d.fetchRunId()), entry("external", d.externalId()),
                entry("original", d.originalUrl()), entry("canonical", d.canonicalUrl()), entry("mime", d.mimeType()), entry("charset", d.charset()),
                entry("hash", d.contentHash()), entry("blob", d.blobHash()), entry("length", d.contentLength()), entry("http", d.httpStatus()),
                entry("license", d.license()), entry("status", d.status()), entry("observed", d.observedAt()), entry("created", d.createdAt())
        )).update();
        return findRawByHash(d.contentHash()).orElse(d);
    }
    @Override public Optional<RawDocument> findRawDocument(UUID id) {
        return jdbc.sql("SELECT * FROM raw_documents WHERE id=:id").param("id", id.toString()).query(this::raw).optional();
    }
    @Override public Optional<RawDocument> findRawByCanonicalUrl(String url) {
        return jdbc.sql("SELECT * FROM raw_documents WHERE canonical_url=:value").param("value", url).query(this::raw).optional();
    }
    @Override public Optional<RawDocument> findRawByHash(String hash) {
        return jdbc.sql("SELECT * FROM raw_documents WHERE content_hash=:value").param("value", hash).query(this::raw).optional();
    }

    @Override public DocumentVersion saveDocumentVersion(DocumentVersion d) {
        jdbc.sql("""
                INSERT INTO document_versions(id,raw_document_id,title,author,published_at,language,canonical_url,text,text_hash,
                summary,algorithm_version,model_version,prompt_injection,created_at)
                VALUES(:id,:raw,:title,:author,:published,:language,:url,:text,:hash,:summary,:algorithm,:model,:injection,:created)
                ON CONFLICT(raw_document_id,text_hash,algorithm_version) DO UPDATE SET summary=excluded.summary,model_version=excluded.model_version
                """).params(java.util.Map.ofEntries(
                entry("id", d.id()), entry("raw", d.rawDocumentId()), entry("title", d.title()), entry("author", d.author()),
                entry("published", d.publishedAt()), entry("language", d.language()), entry("url", d.canonicalUrl()), entry("text", d.text()),
                entry("hash", d.textHash()), entry("summary", d.summary()), entry("algorithm", d.algorithmVersion()), entry("model", d.modelVersion()),
                entry("injection", d.promptInjection()), entry("created", d.createdAt())
        )).update();
        return d;
    }
    @Override public Optional<DocumentVersion> findDocumentVersion(UUID id) {
        return jdbc.sql("SELECT * FROM document_versions WHERE id=:id").param("id", id.toString()).query(this::document).optional();
    }
    @Override public List<DocumentVersion> listDocumentVersions(int limit) {
        return jdbc.sql("SELECT * FROM document_versions ORDER BY created_at DESC LIMIT :limit").param("limit", limit).query(this::document).list();
    }

    @Override public Story saveStory(Story s) {
        jdbc.sql("""
                INSERT INTO stories(id,title,summary,status,first_observed_at,last_observed_at,source_count,source_family_count,
                entities_json,topics_json,manual_override,updated_at)
                VALUES(:id,:title,:summary,:status,:first,:last,:sources,:families,:entities,:topics,:override,:updated)
                ON CONFLICT(id) DO UPDATE SET title=excluded.title,summary=excluded.summary,status=excluded.status,
                last_observed_at=excluded.last_observed_at,source_count=excluded.source_count,
                source_family_count=excluded.source_family_count,entities_json=excluded.entities_json,
                topics_json=excluded.topics_json,manual_override=MAX(stories.manual_override,excluded.manual_override),updated_at=excluded.updated_at
                """).params(java.util.Map.ofEntries(
                entry("id", s.id()), entry("title", s.title()), entry("summary", s.summary()), entry("status", s.status()),
                entry("first", s.firstObservedAt()), entry("last", s.lastObservedAt()), entry("sources", s.sourceCount()),
                entry("families", s.sourceFamilyCount()), entry("entities", write(s.entities())), entry("topics", write(s.topics())),
                entry("override", s.manualOverride()), entry("updated", s.updatedAt())
        )).update();
        return s;
    }
    @Override public StoryMember addStoryMember(StoryMember m) {
        jdbc.sql("""
                INSERT INTO story_members(story_id,document_version_id,role,source_family,similarity,added_at)
                VALUES(:story,:document,:role,:family,:similarity,:added) ON CONFLICT DO NOTHING
                """).params(java.util.Map.ofEntries(entry("story", m.storyId()), entry("document", m.documentVersionId()),
                entry("role", m.role()), entry("family", m.sourceFamily()), entry("similarity", m.similarity()), entry("added", m.addedAt()))).update();
        return m;
    }
    @Override public Optional<Story> findStory(UUID id) {
        return jdbc.sql("SELECT * FROM stories WHERE id=:id").param("id", id.toString()).query(this::story).optional();
    }
    @Override public List<Story> listStories(int limit) {
        return jdbc.sql("SELECT * FROM stories WHERE status!='DELETED' ORDER BY last_observed_at DESC LIMIT :limit")
                .param("limit", limit).query(this::story).list();
    }
    @Override public List<StoryMember> storyMembers(UUID id) {
        return jdbc.sql("SELECT * FROM story_members WHERE story_id=:id ORDER BY added_at")
                .param("id", id.toString()).query(this::member).list();
    }

    @Override public Signal saveSignal(Signal s) {
        jdbc.sql("""
                INSERT INTO signals(id,story_id,view_type,saved_view_id,features_json,score,reason_codes_json,computed_at)
                VALUES(:id,:story,:view,:saved,:features,:score,:reasons,:computed)
                ON CONFLICT(story_id,view_type,saved_view_id) DO UPDATE SET features_json=excluded.features_json,
                score=excluded.score,reason_codes_json=excluded.reason_codes_json,computed_at=excluded.computed_at
                """).params(java.util.Map.ofEntries(entry("id", s.id()), entry("story", s.storyId()), entry("view", s.viewType()),
                entry("saved", s.savedViewId() == null ? "" : s.savedViewId()), entry("features", write(s.features())), entry("score", s.score()),
                entry("reasons", write(s.reasonCodes())), entry("computed", s.computedAt()))).update();
        return s;
    }
    @Override public List<FeedItem> feed(ViewType view, UUID savedViewId, int limit, String cursor) {
        String sql = """
                SELECT s.*, g.id signal_id,g.view_type,g.saved_view_id,g.features_json,g.score,g.reason_codes_json,g.computed_at,
                EXISTS(SELECT 1 FROM interactions i WHERE i.story_id=s.id AND i.type='SAVE' AND NOT EXISTS(SELECT 1 FROM interactions u WHERE u.undo_of=i.id)) saved,
                EXISTS(SELECT 1 FROM interactions i WHERE i.story_id=s.id AND i.type IN ('HIDE','NOT_RELEVANT') AND NOT EXISTS(SELECT 1 FROM interactions u WHERE u.undo_of=i.id)) hidden
                FROM signals g JOIN stories s ON s.id=g.story_id
                WHERE g.view_type=:view AND (:saved IS NULL OR g.saved_view_id=:saved) AND s.status='ACTIVE'
                AND NOT EXISTS(SELECT 1 FROM interactions i WHERE i.story_id=s.id AND i.type IN ('HIDE','NOT_RELEVANT') AND NOT EXISTS(SELECT 1 FROM interactions u WHERE u.undo_of=i.id))
                AND (:cursor IS NULL OR (g.score < CAST(substr(:cursor,1,instr(:cursor,'|')-1) AS REAL)
                     OR (g.score = CAST(substr(:cursor,1,instr(:cursor,'|')-1) AS REAL) AND s.id > substr(:cursor,instr(:cursor,'|')+1))))
                ORDER BY g.score DESC,s.id LIMIT :limit
                """;
        return jdbc.sql(sql).param("view", view.name()).param("saved", savedViewId == null ? null : savedViewId.toString())
                .param("cursor", cursor).param("limit", limit).query((rs, row) -> {
                    Story st = story(rs, row);
                    Signal sig = new Signal(UUID.fromString(rs.getString("signal_id")), st.id(), ViewType.valueOf(rs.getString("view_type")),
                            uuid(rs.getString("saved_view_id")), read(rs.getString("features_json"), SignalFeatures.class), rs.getDouble("score"),
                            read(rs.getString("reason_codes_json"), STRING_LIST), instant(rs.getString("computed_at")));
                    return new FeedItem(st, sig, rs.getBoolean("saved"), rs.getBoolean("hidden"), storyMembers(st.id()));
                }).list();
    }
    @Override public Interaction saveInteraction(Interaction i) {
        jdbc.sql("INSERT INTO interactions(id,story_id,target_id,type,reason,undo_of,created_at) VALUES(:id,:story,:target,:type,:reason,:undo,:created)")
                .params(java.util.Map.ofEntries(entry("id", i.id()), entry("story", i.storyId()), entry("target", i.targetId()), entry("type", i.type()),
                        entry("reason", i.reason()), entry("undo", i.undoOf()), entry("created", i.createdAt()))).update();
        return i;
    }
    @Override public List<Interaction> interactions(UUID storyId) {
        return jdbc.sql("SELECT * FROM interactions WHERE story_id=:id ORDER BY created_at DESC").param("id", storyId.toString())
                .query((rs,row)->new Interaction(uuid(rs.getString("id")), uuid(rs.getString("story_id")), uuid(rs.getString("target_id")),
                        InteractionType.valueOf(rs.getString("type")), rs.getString("reason"), uuid(rs.getString("undo_of")), instant(rs.getString("created_at")))).list();
    }

    @Override public SavedView saveView(SavedView v) {
        jdbc.sql("""
                INSERT INTO saved_views(id,name,expression,ast_json,enabled,version,created_at,updated_at)
                VALUES(:id,:name,:expression,:ast,:enabled,:version,:created,:updated)
                ON CONFLICT(id) DO UPDATE SET name=excluded.name,expression=excluded.expression,ast_json=excluded.ast_json,
                enabled=excluded.enabled,version=excluded.version,updated_at=excluded.updated_at
                """).params(java.util.Map.ofEntries(entry("id", v.id()), entry("name", v.name()), entry("expression", v.expression()),
                entry("ast", v.astJson()), entry("enabled", v.enabled()), entry("version", v.version()), entry("created", v.createdAt()), entry("updated", v.updatedAt()))).update();
        return v;
    }
    @Override public List<SavedView> listViews() {
        return jdbc.sql("SELECT * FROM saved_views ORDER BY name").query((rs,row)->new SavedView(uuid(rs.getString("id")), rs.getString("name"),
                rs.getString("expression"), rs.getString("ast_json"), rs.getBoolean("enabled"), rs.getInt("version"),
                instant(rs.getString("created_at")), instant(rs.getString("updated_at")))).list();
    }
    @Override public void deleteView(UUID id) { jdbc.sql("DELETE FROM saved_views WHERE id=:id").param("id", id.toString()).update(); }

    @Override public ResearchRun saveResearch(ResearchRun r) {
        jdbc.sql("""
                INSERT INTO research_runs(id,story_id,question,mode,status,budget_json,usage_json,scope_json,plan_json,checkpoint_json,gaps_json,created_at,updated_at)
                VALUES(:id,:story,:question,:mode,:status,:budget,:usage,:scope,:plan,:checkpoint,:gaps,:created,:updated)
                ON CONFLICT(id) DO UPDATE SET status=excluded.status,usage_json=excluded.usage_json,scope_json=excluded.scope_json,
                plan_json=excluded.plan_json,checkpoint_json=excluded.checkpoint_json,gaps_json=excluded.gaps_json,updated_at=excluded.updated_at
                """).params(java.util.Map.ofEntries(entry("id", r.id()), entry("story", r.storyId()), entry("question", r.question()), entry("mode", r.mode()),
                entry("status", r.status()), entry("budget", write(r.budget())), entry("usage", write(r.usage())), entry("scope", r.scopeJson()),
                entry("plan", r.planJson()), entry("checkpoint", r.checkpointJson()), entry("gaps", write(r.gaps())), entry("created", r.createdAt()), entry("updated", r.updatedAt()))).update();
        return r;
    }
    @Override public Optional<ResearchRun> findResearch(UUID id) {
        return jdbc.sql("SELECT * FROM research_runs WHERE id=:id").param("id", id.toString()).query(this::research).optional();
    }
    @Override public List<ResearchRun> listResearch(int limit) {
        return jdbc.sql("SELECT * FROM research_runs ORDER BY updated_at DESC LIMIT :limit").param("limit", limit).query(this::research).list();
    }
    @Override public Claim saveClaim(Claim c) {
        jdbc.sql("""
                INSERT INTO claims(id,research_run_id,statement,status,critical,valid_from,valid_to,created_at)
                VALUES(:id,:research,:statement,:status,:critical,:validFrom,:validTo,:created)
                ON CONFLICT(id) DO UPDATE SET statement=excluded.statement,status=excluded.status,valid_from=excluded.valid_from,valid_to=excluded.valid_to
                """).params(java.util.Map.ofEntries(entry("id", c.id()), entry("research", c.researchRunId()), entry("statement", c.statement()),
                entry("status", c.status()), entry("critical", c.critical()), entry("validFrom", c.validFrom()), entry("validTo", c.validTo()), entry("created", c.createdAt()))).update();
        return c;
    }
    @Override public Evidence saveEvidence(Evidence e) {
        jdbc.sql("""
                INSERT INTO evidence(id,document_version_id,exact_quote,start_offset,end_offset,locator,snapshot_hash,source_family,relation,quality,created_at)
                VALUES(:id,:document,:quote,:start,:end,:locator,:hash,:family,:relation,:quality,:created)
                ON CONFLICT(id) DO NOTHING
                """).params(java.util.Map.ofEntries(entry("id", e.id()), entry("document", e.documentVersionId()), entry("quote", e.exactQuote()),
                entry("start", e.startOffset()), entry("end", e.endOffset()), entry("locator", e.locator()), entry("hash", e.snapshotHash()),
                entry("family", e.sourceFamily()), entry("relation", e.relation()), entry("quality", e.quality()), entry("created", e.createdAt()))).update();
        return e;
    }
    @Override public void linkClaimEvidence(ClaimEvidence l) {
        jdbc.sql("INSERT INTO claim_evidence(claim_id,evidence_id,relation) VALUES(:claim,:evidence,:relation) ON CONFLICT DO NOTHING")
                .param("claim", l.claimId().toString()).param("evidence", l.evidenceId().toString()).param("relation", l.relation().name()).update();
    }
    @Override public List<Claim> researchClaims(UUID researchId) {
        return jdbc.sql("SELECT * FROM claims WHERE research_run_id=:id ORDER BY created_at").param("id", researchId.toString()).query(this::claim).list();
    }
    @Override public List<Evidence> claimEvidence(UUID claimId) {
        return jdbc.sql("SELECT e.* FROM evidence e JOIN claim_evidence ce ON ce.evidence_id=e.id WHERE ce.claim_id=:id ORDER BY e.quality DESC")
                .param("id", claimId.toString()).query(this::evidence).list();
    }

    @Override public WatchTarget saveWatchTarget(WatchTarget w) {
        jdbc.sql("""
                INSERT INTO watch_targets(id,type,name,expression,baseline_json,baseline_version,enabled,cooldown_until,created_at,updated_at)
                VALUES(:id,:type,:name,:expression,:baseline,:version,:enabled,:cooldown,:created,:updated)
                ON CONFLICT(id) DO UPDATE SET name=excluded.name,expression=excluded.expression,baseline_json=excluded.baseline_json,
                baseline_version=excluded.baseline_version,enabled=excluded.enabled,cooldown_until=excluded.cooldown_until,updated_at=excluded.updated_at
                """).params(java.util.Map.ofEntries(entry("id", w.id()), entry("type", w.type()), entry("name", w.name()), entry("expression", w.expression()),
                entry("baseline", w.baselineJson()), entry("version", w.baselineVersion()), entry("enabled", w.enabled()), entry("cooldown", w.cooldownUntil()),
                entry("created", w.createdAt()), entry("updated", w.updatedAt()))).update();
        return w;
    }
    @Override public Optional<WatchTarget> findWatchTarget(UUID id) {
        return jdbc.sql("SELECT * FROM watch_targets WHERE id=:id").param("id", id.toString()).query(this::watch).optional();
    }
    @Override public List<WatchTarget> listWatchTargets() { return jdbc.sql("SELECT * FROM watch_targets ORDER BY updated_at DESC").query(this::watch).list(); }
    @Override public ChangeEvent saveChange(ChangeEvent c) {
        jdbc.sql("""
                INSERT INTO change_events(id,watch_target_id,field,old_value,new_value,source,rule,confidence,severity,status,detected_at)
                VALUES(:id,:watch,:field,:old,:new,:source,:rule,:confidence,:severity,:status,:detected) ON CONFLICT DO NOTHING
                """).params(java.util.Map.ofEntries(entry("id", c.id()), entry("watch", c.watchTargetId()), entry("field", c.field()),
                entry("old", c.oldValue()), entry("new", c.newValue()), entry("source", c.source()), entry("rule", c.rule()),
                entry("confidence", c.confidence()), entry("severity", c.severity()), entry("status", c.status()), entry("detected", c.detectedAt()))).update();
        return c;
    }
    @Override public List<ChangeEvent> listChanges(UUID targetId, int limit) {
        return jdbc.sql("SELECT * FROM change_events WHERE watch_target_id=:id ORDER BY detected_at DESC LIMIT :limit")
                .param("id", targetId.toString()).param("limit", limit).query(this::change).list();
    }

    @Override public ReportVersion saveReport(ReportVersion r) {
        jdbc.sql("""
                INSERT INTO report_versions(id,research_run_id,report_type,version,title,content_json,markdown,html,snapshot_hash,citations_verified,created_at)
                VALUES(:id,:research,:type,:version,:title,:content,:markdown,:html,:hash,:verified,:created)
                ON CONFLICT(id) DO UPDATE SET title=excluded.title,content_json=excluded.content_json,markdown=excluded.markdown,html=excluded.html,
                snapshot_hash=excluded.snapshot_hash,citations_verified=excluded.citations_verified
                """).params(java.util.Map.ofEntries(entry("id", r.id()), entry("research", r.researchRunId()), entry("type", r.reportType()), entry("version", r.version()),
                entry("title", r.title()), entry("content", r.contentJson()), entry("markdown", r.markdown()), entry("html", r.html()),
                entry("hash", r.snapshotHash()), entry("verified", r.citationsVerified()), entry("created", r.createdAt()))).update();
        return r;
    }
    @Override public Optional<ReportVersion> findReport(UUID id) { return jdbc.sql("SELECT * FROM report_versions WHERE id=:id").param("id", id.toString()).query(this::report).optional(); }
    @Override public List<ReportVersion> listReports(int limit) { return jdbc.sql("SELECT * FROM report_versions ORDER BY created_at DESC LIMIT :limit").param("limit", limit).query(this::report).list(); }
    @Override public Publication savePublication(Publication p) {
        jdbc.sql("""
                INSERT INTO publications(id,report_version_id,destination_id,idempotency_key,status,remote_id,receipt_json,error_code,created_at,updated_at)
                VALUES(:id,:report,:destination,:key,:status,:remote,:receipt,:error,:created,:updated)
                ON CONFLICT(idempotency_key) DO UPDATE SET status=excluded.status,remote_id=COALESCE(excluded.remote_id,publications.remote_id),
                receipt_json=COALESCE(excluded.receipt_json,publications.receipt_json),error_code=excluded.error_code,updated_at=excluded.updated_at
                """).params(java.util.Map.ofEntries(entry("id", p.id()), entry("report", p.reportVersionId()), entry("destination", p.destinationId()),
                entry("key", p.idempotencyKey()), entry("status", p.status()), entry("remote", p.remoteId()), entry("receipt", p.receiptJson()),
                entry("error", p.errorCode()), entry("created", p.createdAt()), entry("updated", p.updatedAt()))).update();
        return findPublicationByKey(p.idempotencyKey()).orElse(p);
    }
    @Override public Optional<Publication> findPublicationByKey(String key) {
        return jdbc.sql("SELECT * FROM publications WHERE idempotency_key=:key").param("key", key).query(this::publication).optional();
    }

    @Override public void appendOutbox(String aggregateType, UUID aggregateId, String eventType, String payloadJson, Instant now) {
        jdbc.sql("INSERT INTO outbox_events(id,aggregate_type,aggregate_id,event_type,payload_json,created_at) VALUES(:id,:type,:aggregate,:event,:payload,:created)")
                .param("id", UUID.randomUUID().toString()).param("type", aggregateType).param("aggregate", aggregateId.toString())
                .param("event", eventType).param("payload", payloadJson).param("created", now.toString()).update();
    }
    @Override public void appendAudit(String actor, String action, String targetType, String targetId, String detailJson, Instant now) {
        jdbc.sql("INSERT INTO audit_log(id,actor,action,target_type,target_id,detail_json,created_at) VALUES(:id,:actor,:action,:type,:target,:detail,:created)")
                .param("id", UUID.randomUUID().toString()).param("actor", actor).param("action", action).param("type", targetType)
                .param("target", targetId).param("detail", detailJson).param("created", now.toString()).update();
    }
    @Override public void recordUsage(UsageEntry u) {
        jdbc.sql("INSERT INTO usage_ledger(id,provider,model,purpose,input_tokens,output_tokens,cost,currency,occurred_at) VALUES(:id,:provider,:model,:purpose,:input,:output,:cost,:currency,:at)")
                .params(java.util.Map.ofEntries(entry("id", u.id()), entry("provider", u.provider()), entry("model", u.model()), entry("purpose", u.purpose()),
                entry("input", u.inputTokens()), entry("output", u.outputTokens()), entry("cost", u.cost().toPlainString()), entry("currency", u.currency()), entry("at", u.occurredAt()))).update();
    }
    @Override public long count(String table) {
        Set<String> allowed = Set.of("sources","raw_documents","document_versions","stories","research_runs","watch_targets","report_versions","publications","jobs","outbox_events");
        if (!allowed.contains(table)) throw new IllegalArgumentException("unknown table");
        return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }

    private Source source(ResultSet r, int row) throws SQLException {
        return new Source(uuid(r.getString("id")), r.getString("name"), SourceType.valueOf(r.getString("type")), SourceKind.valueOf(r.getString("kind")),
                r.getString("endpoint"), r.getString("schedule"), r.getString("cursor"), SourceTier.valueOf(r.getString("tier")),
                SourceHealth.valueOf(r.getString("health")), read(r.getString("topics_json"), STRING_SET), r.getBoolean("enabled"), r.getInt("version"),
                instant(r.getString("created_at")), instant(r.getString("updated_at")));
    }
    private RawDocument raw(ResultSet r, int row) throws SQLException {
        return new RawDocument(uuid(r.getString("id")), uuid(r.getString("source_id")), uuid(r.getString("fetch_run_id")), r.getString("external_id"),
                r.getString("original_url"), r.getString("canonical_url"), r.getString("mime_type"), r.getString("charset"), r.getString("content_hash"),
                r.getString("blob_hash"), r.getLong("content_length"), r.getInt("http_status"), r.getString("license"),
                DocumentStatus.valueOf(r.getString("status")), instant(r.getString("observed_at")), instant(r.getString("created_at")));
    }
    private DocumentVersion document(ResultSet r, int row) throws SQLException {
        return new DocumentVersion(uuid(r.getString("id")), uuid(r.getString("raw_document_id")), r.getString("title"), r.getString("author"),
                instant(r.getString("published_at")), r.getString("language"), r.getString("canonical_url"), r.getString("text"), r.getString("text_hash"),
                r.getString("summary"), r.getString("algorithm_version"), r.getString("model_version"), r.getBoolean("prompt_injection"), instant(r.getString("created_at")));
    }
    private Story story(ResultSet r, int row) throws SQLException {
        return new Story(uuid(r.getString("id")), r.getString("title"), r.getString("summary"), StoryStatus.valueOf(r.getString("status")),
                instant(r.getString("first_observed_at")), instant(r.getString("last_observed_at")), r.getInt("source_count"), r.getInt("source_family_count"),
                read(r.getString("entities_json"), STRING_SET), read(r.getString("topics_json"), STRING_SET), r.getBoolean("manual_override"), instant(r.getString("updated_at")));
    }
    private StoryMember member(ResultSet r, int row) throws SQLException { return new StoryMember(uuid(r.getString("story_id")), uuid(r.getString("document_version_id")), r.getString("role"), r.getString("source_family"), r.getDouble("similarity"), instant(r.getString("added_at"))); }
    private ResearchRun research(ResultSet r, int row) throws SQLException {
        return new ResearchRun(uuid(r.getString("id")), uuid(r.getString("story_id")), r.getString("question"), ResearchMode.valueOf(r.getString("mode")),
                ResearchStatus.valueOf(r.getString("status")), read(r.getString("budget_json"), ResearchBudget.class), read(r.getString("usage_json"), ResearchUsage.class),
                r.getString("scope_json"), r.getString("plan_json"), r.getString("checkpoint_json"), read(r.getString("gaps_json"), STRING_LIST),
                instant(r.getString("created_at")), instant(r.getString("updated_at")));
    }
    private Claim claim(ResultSet r, int row) throws SQLException { return new Claim(uuid(r.getString("id")), uuid(r.getString("research_run_id")), r.getString("statement"), ClaimStatus.valueOf(r.getString("status")), r.getBoolean("critical"), instant(r.getString("valid_from")), instant(r.getString("valid_to")), instant(r.getString("created_at"))); }
    private Evidence evidence(ResultSet r, int row) throws SQLException { return new Evidence(uuid(r.getString("id")), uuid(r.getString("document_version_id")), r.getString("exact_quote"), r.getInt("start_offset"), r.getInt("end_offset"), r.getString("locator"), r.getString("snapshot_hash"), r.getString("source_family"), EvidenceRelation.valueOf(r.getString("relation")), r.getDouble("quality"), instant(r.getString("created_at"))); }
    private WatchTarget watch(ResultSet r, int row) throws SQLException { return new WatchTarget(uuid(r.getString("id")), WatchType.valueOf(r.getString("type")), r.getString("name"), r.getString("expression"), r.getString("baseline_json"), r.getInt("baseline_version"), r.getBoolean("enabled"), instant(r.getString("cooldown_until")), instant(r.getString("created_at")), instant(r.getString("updated_at"))); }
    private ChangeEvent change(ResultSet r, int row) throws SQLException { return new ChangeEvent(uuid(r.getString("id")), uuid(r.getString("watch_target_id")), r.getString("field"), r.getString("old_value"), r.getString("new_value"), r.getString("source"), r.getString("rule"), r.getDouble("confidence"), ChangeSeverity.valueOf(r.getString("severity")), r.getString("status"), instant(r.getString("detected_at"))); }
    private ReportVersion report(ResultSet r, int row) throws SQLException { return new ReportVersion(uuid(r.getString("id")), uuid(r.getString("research_run_id")), r.getString("report_type"), r.getInt("version"), r.getString("title"), r.getString("content_json"), r.getString("markdown"), r.getString("html"), r.getString("snapshot_hash"), r.getBoolean("citations_verified"), instant(r.getString("created_at"))); }
    private Publication publication(ResultSet r, int row) throws SQLException { return new Publication(uuid(r.getString("id")), uuid(r.getString("report_version_id")), r.getString("destination_id"), r.getString("idempotency_key"), PublicationStatus.valueOf(r.getString("status")), r.getString("remote_id"), r.getString("receipt_json"), r.getString("error_code"), instant(r.getString("created_at")), instant(r.getString("updated_at"))); }

    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception ex) { throw new IllegalArgumentException("json serialization failed", ex); } }
    private <T> T read(String value, Class<T> type) { try { return json.readValue(value, type); } catch (Exception ex) { throw new IllegalArgumentException("json deserialization failed", ex); } }
    private <T> T read(String value, TypeReference<T> type) { try { return json.readValue(value, type); } catch (Exception ex) { throw new IllegalArgumentException("json deserialization failed", ex); } }
    private static Instant instant(String value) { return value == null ? null : Instant.parse(value); }
    private static UUID uuid(String value) { return value == null || value.isBlank() ? null : UUID.fromString(value); }
    private static java.util.Map.Entry<String,Object> entry(String key, Object value) {
        Object converted = value == null ? new org.springframework.jdbc.core.SqlParameterValue(java.sql.Types.VARCHAR, null)
                : value instanceof UUID u ? u.toString() : value instanceof Enum<?> e ? e.name() : value instanceof Instant i ? i.toString() : value;
        return new java.util.AbstractMap.SimpleEntry<>(key, converted);
    }
}
