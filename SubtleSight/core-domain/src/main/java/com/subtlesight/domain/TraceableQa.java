package com.subtlesight.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class TraceableQa {
    private TraceableQa() {}

    public enum ResourceType { FILE, DOCUMENT, DRAW_NODE }
    public enum UnitType { PARAGRAPH, PAGE, SECTION, ROW, SLIDE, NODE }
    public enum ScopeType { FILE, DOCUMENT, DRAWING, DRAW_NODE, FOLDER, KNOWLEDGE_BASE }
    public enum IndexStatus { PENDING, INDEXING, READY, ERROR }
    public enum AnswerStatus { QUEUED, RETRIEVING, GENERATING, VERIFYING, COMPLETED, INSUFFICIENT, FAILED }
    public enum ClaimType { SOURCE_FACT, SYNTHESIS, INFERENCE, INSUFFICIENT }
    public enum VerificationStatus { VERIFIED, DISPUTED, UNVERIFIED }
    public enum CitationRelation { SUPPORTS, REFUTES, QUALIFIES }

    public record ResourceVersionKey(ResourceType type, UUID resourceId, String version) {
        public ResourceVersionKey {
            Objects.requireNonNull(type);
            Objects.requireNonNull(resourceId);
            requireText(version, "resource version");
        }
        public String externalKey() { return type + ":" + resourceId + ":" + version; }
    }

    public record ScopeRef(ScopeType type, UUID id, UUID resourceId, List<String> nodeIds) {
        public ScopeRef {
            Objects.requireNonNull(type);
            nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
            if (type == ScopeType.DRAW_NODE && (resourceId == null || nodeIds.isEmpty()))
                throw new IllegalArgumentException("DRAW_NODE scope requires resourceId and nodeIds");
            if (type != ScopeType.KNOWLEDGE_BASE && type != ScopeType.DRAW_NODE && id == null)
                throw new IllegalArgumentException(type + " scope requires id");
        }
    }

    public record KnowledgeUnit(UUID id, ResourceType resourceType, UUID resourceId, String resourceVersion,
                                UUID folderId, UnitType unitType, String stableLocator, String text,
                                String contextText, String metadataJson, String contentHash, Instant createdAt) {
        public KnowledgeUnit {
            Objects.requireNonNull(id); Objects.requireNonNull(resourceType); Objects.requireNonNull(resourceId);
            requireText(resourceVersion, "resourceVersion"); Objects.requireNonNull(unitType);
            requireText(stableLocator, "stableLocator"); requireText(text, "unit text");
            contextText = contextText == null ? "" : contextText;
            metadataJson = metadataJson == null || metadataJson.isBlank() ? "{}" : metadataJson;
            requireText(contentHash, "contentHash"); Objects.requireNonNull(createdAt);
        }
        public ResourceVersionKey versionKey() { return new ResourceVersionKey(resourceType, resourceId, resourceVersion); }
    }

    public record ExcludedResource(String reference, String reason) {}

    public record ResolvedScope(List<ResourceVersionKey> resources, List<UUID> unitIds,
                                List<ExcludedResource> excluded) {
        public ResolvedScope {
            resources = resources == null ? List.of() : List.copyOf(resources);
            unitIds = unitIds == null ? List.of() : List.copyOf(unitIds);
            excluded = excluded == null ? List.of() : List.copyOf(excluded);
        }
    }

    public record EmbeddingProfile(String id, String provider, String model, String modelRevision,
                                   int dimensions, String distance, int chunkSchemaVersion, boolean active,
                                   Instant createdAt) {
        public EmbeddingProfile {
            requireText(id, "profile id"); requireText(provider, "embedding provider");
            requireText(model, "embedding model"); requireText(modelRevision, "model revision");
            requireText(distance, "distance");
            if (dimensions < 1 || chunkSchemaVersion < 1) throw new IllegalArgumentException("invalid embedding profile");
            Objects.requireNonNull(createdAt);
        }
    }

    public record UnitHit(UUID unitId, double score, String channel) {
        public UnitHit { Objects.requireNonNull(unitId); requireText(channel, "retrieval channel"); }
    }

    public record QaConversation(UUID id, String title, Instant createdAt, Instant updatedAt) {}

    public record QaAnswer(UUID id, UUID conversationId, UUID parentAnswerId, String question,
                           AnswerStatus status, String directAnswer, List<String> gaps,
                           String provider, String model, String errorCode,
                           Instant createdAt, Instant completedAt) {
        public QaAnswer {
            Objects.requireNonNull(id); Objects.requireNonNull(conversationId); requireText(question, "question");
            Objects.requireNonNull(status); directAnswer = directAnswer == null ? "" : directAnswer;
            gaps = gaps == null ? List.of() : List.copyOf(gaps); Objects.requireNonNull(createdAt);
        }
    }

    public record QaClaim(UUID id, UUID answerId, int ordinal, ClaimType type, String statement,
                          VerificationStatus verificationStatus) {
        public QaClaim {
            Objects.requireNonNull(id); Objects.requireNonNull(answerId); Objects.requireNonNull(type);
            requireText(statement, "claim statement"); Objects.requireNonNull(verificationStatus);
            if (ordinal < 0) throw new IllegalArgumentException("claim ordinal must be non-negative");
        }
    }

    public record QaCitation(UUID id, UUID claimId, CitationRelation relation, ResourceType resourceType,
                             UUID resourceId, String resourceVersion, UUID unitId, String locatorJson,
                             String exactQuote, String snapshotHash, String sourceFamily, int rank,
                             Instant createdAt) {
        public QaCitation {
            Objects.requireNonNull(id); Objects.requireNonNull(claimId); Objects.requireNonNull(relation);
            Objects.requireNonNull(resourceType); Objects.requireNonNull(resourceId);
            requireText(resourceVersion, "citation resource version"); Objects.requireNonNull(unitId);
            requireText(locatorJson, "locator"); requireText(exactQuote, "exact quote");
            requireText(snapshotHash, "snapshot hash"); Objects.requireNonNull(createdAt);
            if (rank < 0) throw new IllegalArgumentException("citation rank must be non-negative");
        }
    }

    public record CitationResolution(QaCitation citation, KnowledgeUnit unit, boolean stale,
                                     String currentVersion, boolean locatorValid) {}

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
    }
}
