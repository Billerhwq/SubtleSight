package com.subtlesight.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Stable domain contracts shared by application ports and engine modules. */
public final class Models {
    private Models() {}

    public enum SourceType { RSS, WEBSITE, SITEMAP, GITHUB, ARXIV, HN, HUGGING_FACE, PRODUCT_HUNT, VIDEO, REDDIT, NEWS_API, NEWSLETTER, CUSTOM_API, UPLOAD }
    public enum SourceKind { PERSISTENT, DISCOVERED, EPHEMERAL }
    public enum SourceHealth { HEALTHY, DEGRADED, DOWN, DISABLED }
    public enum SourceTier { PRIMARY, PROFESSIONAL, COMMUNITY, SOCIAL, UNKNOWN }
    public enum DocumentStatus { STORED, PARSED, INDEXED, FAILED, RESTRICTED }
    public enum StoryStatus { ACTIVE, SUPERSEDED, RETRACTED, DELETED }
    public enum ViewType { FOR_YOU, EMERGING, IMPORTANT, LATEST, SAVED }
    public enum EvidenceRelation { SUPPORTS, REFUTES, QUALIFIES }
    public enum ClaimStatus { UNVERIFIED, SUPPORTED, REFUTED, DISPUTED, OUTDATED }
    public enum ResearchMode { VERIFY, STANDARD, DEEP, CONTINUOUS }
    public enum ResearchStatus { CREATED, SCOPING, PLANNING, SEARCHING, READING, EXTRACTING, REFLECTING, WRITING, VERIFYING, COMPLETED, PARTIAL, PAUSED, CANCELLED, FAILED }
    public enum WatchType { ENTITY, TOPIC, QUERY, CLAIM, COMPARISON_SET }
    public enum InteractionType { SAVE, HIDE, NOT_RELEVANT, FOLLOW, RESEARCH, INCORRECT_FACT, USEFUL, UNDO }
    public enum PublicationStatus { REQUESTED, PUBLISHING, PUBLISHED, RECONCILE, FAILED, CANCELLED }
    public enum JobStatus { QUEUED, RUNNING, RETRY_WAIT, PAUSED, COMPLETED, FAILED, CANCELLED }
    public enum ChangeSeverity { LOW, MEDIUM, HIGH, CRITICAL }

    public record Source(
            UUID id, String name, SourceType type, SourceKind kind, String endpoint,
            String schedule, String cursor, SourceTier tier, SourceHealth health,
            Set<String> topics, boolean enabled, int version, Instant createdAt, Instant updatedAt) {
        public Source {
            Objects.requireNonNull(id); requireText(name, "source name"); Objects.requireNonNull(type);
            Objects.requireNonNull(kind); requireText(endpoint, "source endpoint"); Objects.requireNonNull(tier);
            Objects.requireNonNull(health); topics = topics == null ? Set.of() : Set.copyOf(topics);
            Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        }
    }

    public record FetchRun(UUID id, UUID sourceId, String status, int fetchedCount, String cursorBefore,
                           String cursorAfter, String errorCode, Instant startedAt, Instant finishedAt) {}

    public record RawDocument(
            UUID id, UUID sourceId, UUID fetchRunId, String externalId, String originalUrl,
            String canonicalUrl, String mimeType, String charset, String contentHash, String blobHash,
            long contentLength, int httpStatus, String license, DocumentStatus status,
            Instant observedAt, Instant createdAt) {
        public RawDocument {
            Objects.requireNonNull(id); Objects.requireNonNull(sourceId); requireText(canonicalUrl, "canonicalUrl");
            requireText(contentHash, "contentHash"); Objects.requireNonNull(status);
            Objects.requireNonNull(observedAt); Objects.requireNonNull(createdAt);
        }
    }

    public record DocumentVersion(
            UUID id, UUID rawDocumentId, String title, String author, Instant publishedAt, String language,
            String canonicalUrl, String text, String textHash, String summary, String algorithmVersion,
            String modelVersion, boolean promptInjection, Instant createdAt) {
        public DocumentVersion {
            Objects.requireNonNull(id); Objects.requireNonNull(rawDocumentId); requireText(title, "title");
            requireText(textHash, "textHash"); requireText(algorithmVersion, "algorithmVersion");
            Objects.requireNonNull(createdAt); text = text == null ? "" : text; language = language == null ? "und" : language;
        }
    }

    public record Story(
            UUID id, String title, String summary, StoryStatus status, Instant firstObservedAt,
            Instant lastObservedAt, int sourceCount, int sourceFamilyCount, Set<String> entities,
            Set<String> topics, boolean manualOverride, Instant updatedAt) {
        public Story {
            Objects.requireNonNull(id); requireText(title, "story title"); Objects.requireNonNull(status);
            entities = entities == null ? Set.of() : Set.copyOf(entities); topics = topics == null ? Set.of() : Set.copyOf(topics);
            Objects.requireNonNull(firstObservedAt); Objects.requireNonNull(lastObservedAt); Objects.requireNonNull(updatedAt);
        }
    }

    public record StoryMember(UUID storyId, UUID documentVersionId, String role, String sourceFamily,
                              double similarity, Instant addedAt) {}

    public record SignalFeatures(
            double relevance, double impact, double novelty, double velocity, double change,
            double sourceQuality, double independence, double confidence, double disagreement,
            double freshness) {
        public SignalFeatures {
            for (double value : new double[]{relevance, impact, novelty, velocity, change, sourceQuality,
                    independence, confidence, disagreement, freshness}) {
                if (!Double.isFinite(value) || value < 0 || value > 1) {
                    throw new IllegalArgumentException("signal features must be finite values in [0,1]");
                }
            }
        }
    }

    public record Signal(UUID id, UUID storyId, ViewType viewType, UUID savedViewId,
                         SignalFeatures features, double score, List<String> reasonCodes,
                         Instant computedAt) {
        public Signal {
            Objects.requireNonNull(id); Objects.requireNonNull(storyId); Objects.requireNonNull(viewType);
            Objects.requireNonNull(features); reasonCodes = reasonCodes == null ? List.of() : List.copyOf(reasonCodes);
            Objects.requireNonNull(computedAt);
        }
    }

    public record FeedItem(Story story, Signal signal, boolean saved, boolean hidden, List<StoryMember> timeline) {
        public FeedItem { timeline = timeline == null ? List.of() : List.copyOf(timeline); }
    }

    public record SavedView(UUID id, String name, String expression, String astJson, boolean enabled,
                            int version, Instant createdAt, Instant updatedAt) {}

    public record SearchHit(String provider, String queryType, String title, String url, String snippet,
                            Instant publishedAt, SourceTier tier, double score) {}
    public record QuerySpec(String type, String query, Set<String> preferredDomains, Set<String> excludedDomains) {}
    public record QueryPlan(UUID id, String seedType, String seedValue, List<QuerySpec> queries,
                            int maxResults, Instant createdAt) {}

    public record ResearchBudget(int maxQueries, int maxPages, long maxTokens, BigDecimal maxCost,
                                 long maxDurationSeconds) {
        public ResearchBudget {
            if (maxQueries <= 0 || maxPages <= 0 || maxTokens <= 0 || maxCost == null || maxCost.signum() < 0
                    || maxDurationSeconds <= 0) throw new IllegalArgumentException("invalid research budget");
        }
    }

    public record ResearchUsage(int queries, int pages, long tokens, BigDecimal cost, long durationSeconds) {
        public ResearchUsage {
            if (queries < 0 || pages < 0 || tokens < 0 || cost == null || cost.signum() < 0 || durationSeconds < 0)
                throw new IllegalArgumentException("usage cannot be negative");
        }
        public static ResearchUsage zero() { return new ResearchUsage(0, 0, 0, BigDecimal.ZERO, 0); }
    }

    public record ResearchRun(UUID id, UUID storyId, String question, ResearchMode mode, ResearchStatus status,
                              ResearchBudget budget, ResearchUsage usage, String scopeJson, String planJson,
                              String checkpointJson, List<String> gaps, Instant createdAt, Instant updatedAt) {
        public ResearchRun {
            Objects.requireNonNull(id); requireText(question, "research question"); Objects.requireNonNull(mode);
            Objects.requireNonNull(status); Objects.requireNonNull(budget); Objects.requireNonNull(usage);
            gaps = gaps == null ? List.of() : List.copyOf(gaps); Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        }
    }

    public record Claim(UUID id, UUID researchRunId, String statement, ClaimStatus status, boolean critical,
                        Instant validFrom, Instant validTo, Instant createdAt) {
        public Claim { Objects.requireNonNull(id); requireText(statement, "claim statement"); Objects.requireNonNull(status); }
    }

    public record Evidence(UUID id, UUID documentVersionId, String exactQuote, int startOffset, int endOffset,
                           String locator, String snapshotHash, String sourceFamily, EvidenceRelation relation,
                           double quality, Instant createdAt) {
        public Evidence {
            Objects.requireNonNull(id); Objects.requireNonNull(documentVersionId); requireText(exactQuote, "exactQuote");
            requireText(snapshotHash, "snapshotHash"); Objects.requireNonNull(relation);
            if (startOffset < 0 || endOffset < startOffset || quality < 0 || quality > 1) throw new IllegalArgumentException("invalid evidence bounds");
        }
    }

    public record ClaimEvidence(UUID claimId, UUID evidenceId, EvidenceRelation relation) {}

    public record WatchTarget(UUID id, WatchType type, String name, String expression, String baselineJson,
                              int baselineVersion, boolean enabled, Instant cooldownUntil,
                              Instant createdAt, Instant updatedAt) {}

    public record ChangeEvent(UUID id, UUID watchTargetId, String field, String oldValue, String newValue,
                              String source, String rule, double confidence, ChangeSeverity severity,
                              String status, Instant detectedAt) {}

    public record Interaction(UUID id, UUID storyId, UUID targetId, InteractionType type, String reason,
                              UUID undoOf, Instant createdAt) {}

    public record ReportVersion(UUID id, UUID researchRunId, String reportType, int version, String title,
                                String contentJson, String markdown, String html, String snapshotHash,
                                boolean citationsVerified, Instant createdAt) {}

    public record Publication(UUID id, UUID reportVersionId, String destinationId, String idempotencyKey,
                              PublicationStatus status, String remoteId, String receiptJson, String errorCode,
                              Instant createdAt, Instant updatedAt) {}

    public record Job(UUID id, String type, JobStatus status, int priority, String payloadJson, String dedupKey,
                      int attempt, int maxAttempts, Instant runAfter, Instant leaseUntil, Instant heartbeatAt,
                      String checkpointJson, boolean cancelRequested, String errorCode,
                      Instant createdAt, Instant startedAt, Instant finishedAt) {}

    public record UsageEntry(UUID id, String provider, String model, String purpose, long inputTokens,
                             long outputTokens, BigDecimal cost, String currency, Instant occurredAt) {}

    public record AgentRequest(String message, boolean confirmed, Map<String, Object> context, java.util.UUID turnId, java.util.UUID sessionId) {
        public AgentRequest(String message, boolean confirmed, Map<String, Object> context) { this(message, confirmed, context, null, null); }
        public AgentRequest { requireText(message, "message"); context = context == null ? Map.of() : Map.copyOf(context); }
    }
    public record AgentResponse(String message, List<String> tools, Map<String, Object> result, boolean confirmationRequired, java.util.UUID turnId, java.util.UUID sessionId) {
        public AgentResponse(String message, List<String> tools, Map<String, Object> result, boolean confirmationRequired) { this(message, tools, result, confirmationRequired, null, null); }
        public AgentResponse { tools = tools == null ? List.of() : List.copyOf(tools); result = result == null ? Map.of() : Map.copyOf(result); }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
