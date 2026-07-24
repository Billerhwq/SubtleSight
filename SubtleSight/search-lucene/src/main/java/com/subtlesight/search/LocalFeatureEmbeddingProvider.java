package com.subtlesight.search;

import com.subtlesight.application.TraceableQaPorts.EmbeddingProvider;
import com.subtlesight.domain.TraceableQa.EmbeddingProfile;

import java.time.Instant;
import java.util.List;

public final class LocalFeatureEmbeddingProvider implements EmbeddingProvider {
    public static final int DIMENSIONS = 384;
    private final EmbeddingProfile profile = new EmbeddingProfile(
            "local-feature-v1", "local", "character-trigram", "1", DIMENSIONS,
            "COSINE", 1, true, Instant.EPOCH);

    @Override public EmbeddingProfile profile() { return profile; }

    @Override public List<float[]> embed(List<String> texts) {
        return texts.stream().map(LocalFeatureEmbeddingProvider::vector).toList();
    }

    public static float[] vector(String text) {
        float[] result = new float[DIMENSIONS];
        String normalized = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ").strip();
        if (normalized.isEmpty()) { result[0] = 1f; return result; }
        for (int i = 0; i < normalized.length(); i++) {
            String token = normalized.substring(i, Math.min(i + 3, normalized.length()));
            int hash = token.hashCode();
            result[Math.floorMod(hash, result.length)] += (hash & 1) == 0 ? 1f : -1f;
        }
        double norm = 0;
        for (float value : result) norm += value * value;
        norm = Math.sqrt(norm);
        if (norm == 0) result[0] = 1f;
        else for (int i = 0; i < result.length; i++) result[i] /= (float) norm;
        return result;
    }
}
