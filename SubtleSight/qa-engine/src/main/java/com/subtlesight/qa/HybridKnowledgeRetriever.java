package com.subtlesight.qa;

import com.subtlesight.application.TraceableQaPorts.EmbeddingProvider;
import com.subtlesight.application.TraceableQaPorts.LexicalUnitIndex;
import com.subtlesight.application.TraceableQaPorts.Repository;
import com.subtlesight.application.TraceableQaPorts.SemanticUnitIndex;
import com.subtlesight.domain.TraceableQa.EmbeddingProfile;
import com.subtlesight.domain.TraceableQa.KnowledgeUnit;
import com.subtlesight.domain.TraceableQa.UnitHit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HybridKnowledgeRetriever {
    private final Repository repository;
    private final LexicalUnitIndex lexical;
    private final SemanticUnitIndex semantic;
    private final EmbeddingProvider embeddings;

    public HybridKnowledgeRetriever(Repository repository, LexicalUnitIndex lexical,
                                    SemanticUnitIndex semantic, EmbeddingProvider embeddings) {
        this.repository = repository;
        this.lexical = lexical;
        this.semantic = semantic;
        this.embeddings = embeddings;
    }

    public Retrieval retrieve(String query, Collection<UUID> scopeUnitIds, int limit) {
        Set<UUID> allowed = Set.copyOf(scopeUnitIds);
        if (allowed.isEmpty() || limit < 1) return new Retrieval(List.of(), false, semantic.backend());
        List<UnitHit> lexicalHits = lexical.search(query, allowed, Math.max(limit * 3, 20));
        List<UnitHit> semanticHits = List.of();
        boolean semanticAvailable = true;
        try {
            float[] vector = embeddings.embed(List.of(query)).getFirst();
            semanticHits = semantic.search(embeddings.profile(), vector, allowed, Math.max(limit * 3, 20));
        } catch (RuntimeException failure) {
            semanticAvailable = false;
        }

        // A stable insertion order lets lexical evidence win exact RRF ties. This is
        // important for the extractive fallback, where the first unit becomes the answer.
        Map<UUID, Double> scores = new LinkedHashMap<>();
        addRanks(lexicalHits, scores);
        addRanks(semanticHits, scores);
        List<UUID> rankedIds = scores.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
        Map<UUID, KnowledgeUnit> units = new LinkedHashMap<>();
        repository.unitsByIds(rankedIds).forEach(unit -> units.put(unit.id(), unit));
        List<KnowledgeUnit> ranked = rankedIds.stream().map(units::get).filter(java.util.Objects::nonNull).toList();
        return new Retrieval(ranked, semanticAvailable, semantic.backend());
    }

    public EmbeddingProfile profile() { return embeddings.profile(); }

    private static void addRanks(List<UnitHit> hits, Map<UUID, Double> scores) {
        for (int rank = 0; rank < hits.size(); rank++)
            scores.merge(hits.get(rank).unitId(), 1d / (60 + rank + 1), Double::sum);
    }

    public record Retrieval(List<KnowledgeUnit> units, boolean semanticAvailable, String semanticBackend) {}
}
