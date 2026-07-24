package com.subtlesight.application;

import com.subtlesight.domain.TraceableQa.*;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class TraceableQaPorts {
    private TraceableQaPorts() {}

    public interface Repository {
        void upsertUnits(ResourceVersionKey version, List<KnowledgeUnit> units);
        List<KnowledgeUnit> units(ResourceVersionKey version);
        List<KnowledgeUnit> unitsByIds(Collection<UUID> ids);
        Optional<KnowledgeUnit> unit(UUID id);
        ResolvedScope resolveScope(List<ScopeRef> refs);
        Optional<String> currentVersion(ResourceType type, UUID resourceId);
        void markIndexState(ResourceVersionKey version, IndexStatus status, String errorCode, int unitCount);
        IndexStatus indexStatus(ResourceVersionKey version);

        void saveEmbeddingProfile(EmbeddingProfile profile);
        void markEmbeddingIndexed(UUID unitId, String profileId, String contentHash, String externalPointId);

        QaConversation saveConversation(QaConversation conversation);
        Optional<QaConversation> conversation(UUID id);
        QaAnswer saveAnswer(QaAnswer answer, List<UUID> scopeUnitIds, String scopeSnapshotJson);
        Optional<QaAnswer> answer(UUID id);
        List<UUID> answerUnitIds(UUID answerId);
        String answerScopeSnapshot(UUID answerId);
        QaClaim saveClaim(QaClaim claim);
        Optional<QaClaim> claim(UUID id);
        QaCitation saveCitation(QaCitation citation);
        List<QaClaim> claims(UUID answerId);
        List<QaCitation> citationsForClaim(UUID claimId);
        Optional<QaCitation> citation(UUID id);
    }

    public interface LexicalUnitIndex extends AutoCloseable {
        void upsert(List<KnowledgeUnit> units);
        void delete(ResourceVersionKey version);
        List<UnitHit> search(String query, Set<UUID> allowedUnitIds, int limit);
        void commit();
        @Override default void close() {}
    }

    public interface SemanticUnitIndex extends AutoCloseable {
        void upsert(EmbeddingProfile profile, List<KnowledgeUnit> units, List<float[]> vectors);
        void delete(EmbeddingProfile profile, ResourceVersionKey version);
        List<UnitHit> search(EmbeddingProfile profile, float[] queryVector, Set<UUID> allowedUnitIds, int limit);
        String backend();
        void commit();
        @Override default void close() {}
    }

    public interface EmbeddingProvider {
        EmbeddingProfile profile();
        List<float[]> embed(List<String> texts);
    }
}
