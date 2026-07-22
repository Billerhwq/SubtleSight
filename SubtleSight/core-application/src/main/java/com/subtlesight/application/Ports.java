package com.subtlesight.application;

import com.subtlesight.domain.Models.*;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Inbound/outbound ports. Engine modules never reach network, SQLite or filesystem without one of these contracts. */
public final class Ports {
    private Ports() {}

    public interface IntelligenceRepository {
        Source saveSource(Source source);
        List<Source> listSources();
        Optional<Source> findSource(UUID id);
        void disableSource(UUID id, Instant now);

        RawDocument saveRawDocument(RawDocument raw);
        Optional<RawDocument> findRawDocument(UUID id);
        Optional<RawDocument> findRawByCanonicalUrl(String canonicalUrl);
        Optional<RawDocument> findRawByHash(String contentHash);
        DocumentVersion saveDocumentVersion(DocumentVersion document);
        Optional<DocumentVersion> findDocumentVersion(UUID id);
        List<DocumentVersion> listDocumentVersions(int limit);

        Story saveStory(Story story);
        StoryMember addStoryMember(StoryMember member);
        Optional<Story> findStory(UUID id);
        List<Story> listStories(int limit);
        List<StoryMember> storyMembers(UUID storyId);

        Signal saveSignal(Signal signal);
        List<FeedItem> feed(ViewType viewType, UUID savedViewId, int limit, String cursor);
        Interaction saveInteraction(Interaction interaction);
        List<Interaction> interactions(UUID storyId);

        SavedView saveView(SavedView view);
        List<SavedView> listViews();
        void deleteView(UUID id);

        ResearchRun saveResearch(ResearchRun run);
        Optional<ResearchRun> findResearch(UUID id);
        List<ResearchRun> listResearch(int limit);
        Claim saveClaim(Claim claim);
        Evidence saveEvidence(Evidence evidence);
        void linkClaimEvidence(ClaimEvidence link);
        List<Claim> researchClaims(UUID researchId);
        List<Evidence> claimEvidence(UUID claimId);

        WatchTarget saveWatchTarget(WatchTarget target);
        Optional<WatchTarget> findWatchTarget(UUID id);
        List<WatchTarget> listWatchTargets();
        ChangeEvent saveChange(ChangeEvent change);
        List<ChangeEvent> listChanges(UUID watchTargetId, int limit);

        ReportVersion saveReport(ReportVersion report);
        Optional<ReportVersion> findReport(UUID id);
        List<ReportVersion> listReports(int limit);
        Publication savePublication(Publication publication);
        Optional<Publication> findPublicationByKey(String idempotencyKey);

        void appendOutbox(String aggregateType, UUID aggregateId, String eventType, String payloadJson, Instant now);
        void appendAudit(String actor, String action, String targetType, String targetId, String detailJson, Instant now);
        void recordUsage(UsageEntry entry);
        long count(String table);
    }

    public interface BlobStore {
        BlobRef put(byte[] bytes, String mediaType);
        BlobRef put(InputStream input, String mediaType, long maxBytes);
        Optional<InputStream> open(String hash);
        boolean exists(String hash);
        void verify(String hash);
        record BlobRef(String hash, long size, String mediaType) {}
    }

    public interface SearchIndex extends AutoCloseable {
        void index(DocumentVersion document, Set<String> entities, Set<String> topics);
        void index(Story story);
        void delete(String type, UUID id);
        List<SearchResult> search(String query, Set<String> types, int limit);
        void commit();
        record SearchResult(String type, UUID id, String title, String snippet, float score) {}
    }

    public interface WebSearchProvider {
        String name();
        List<SearchHit> search(QuerySpec query, int limit);
    }

    public interface ReaderProvider {
        ReadResult read(URI uri);
        record ReadResult(URI finalUri, int status, String mediaType, byte[] content, String license, List<URI> redirects) {}
    }

    public interface AiProvider {
        AiResult complete(AiRequest request);
        record AiRequest(String purpose, String system, String prompt, String schema, long maxTokens, double temperature) {}
        record AiResult(String content, long inputTokens, long outputTokens, String model, String provider) {}
    }

    public interface Publisher {
        String destinationType();
        PublishReceipt publish(ReportVersion report, String destinationId, String idempotencyKey);
        Optional<PublishReceipt> reconcile(String destinationId, String idempotencyKey);
        record PublishReceipt(String remoteId, String status, String receiptJson) {}
    }

    public interface ClockPort { Instant now(); }
}
