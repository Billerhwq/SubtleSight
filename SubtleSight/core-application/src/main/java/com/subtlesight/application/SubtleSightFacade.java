package com.subtlesight.application;

import com.subtlesight.application.Ports.BlobStore;
import com.subtlesight.application.Ports.IntelligenceRepository;
import com.subtlesight.application.Ports.SearchIndex;
import com.subtlesight.domain.CanonicalUrl;
import com.subtlesight.domain.Hashing;
import com.subtlesight.domain.Models.*;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Transaction-bound application facade used by REST, Agent tools and background jobs alike. */
public final class SubtleSightFacade {
    private final IntelligenceRepository repository;
    private final BlobStore blobs;
    private final SearchIndex search;
    private final Clock clock;

    public SubtleSightFacade(IntelligenceRepository repository, BlobStore blobs, SearchIndex search, Clock clock) {
        this.repository = repository;
        this.blobs = blobs;
        this.search = search;
        this.clock = clock;
    }

    public Source registerSource(String name, SourceType type, SourceKind kind, String endpoint,
                                 String schedule, SourceTier tier, Set<String> topics) {
        Instant now = clock.instant();
        String canonicalEndpoint = endpoint.startsWith("http") ? CanonicalUrl.normalize(endpoint) : endpoint.trim();
        Optional<Source> existing = repository.listSources().stream()
                .filter(source -> source.type() == type && source.endpoint().equals(canonicalEndpoint)).findFirst();
        if (existing.isPresent()) return existing.get();
        Source source = new Source(UUID.randomUUID(), name, type, kind, canonicalEndpoint, schedule, null, tier,
                SourceHealth.HEALTHY, topics, true, 1, now, now);
        repository.saveSource(source);
        repository.appendOutbox("source", source.id(), "source.registered", "{}", now);
        repository.appendAudit("user", "source.register", "source", source.id().toString(), "{}", now);
        return source;
    }

    public RawDocument storeRaw(UUID sourceId, UUID fetchRunId, String externalId, String url,
                                String mediaType, byte[] content, String license, Instant observedAt) {
        String canonical = CanonicalUrl.normalize(url);
        String hash = Hashing.sha256(content);
        Optional<RawDocument> duplicate = repository.findRawByCanonicalUrl(canonical)
                .or(() -> repository.findRawByHash(hash));
        if (duplicate.isPresent()) return duplicate.get();
        BlobStore.BlobRef blob = blobs.put(content, mediaType);
        Instant now = clock.instant();
        RawDocument raw = new RawDocument(UUID.randomUUID(), sourceId, fetchRunId, externalId, url, canonical,
                mediaType, "UTF-8", hash, blob.hash(), content.length, 200, license,
                DocumentStatus.STORED, observedAt == null ? now : observedAt, now);
        repository.saveRawDocument(raw);
        repository.appendOutbox("raw_document", raw.id(), "raw_document.stored", "{}", now);
        return raw;
    }

    public DocumentVersion storeDocument(DocumentVersion document, Set<String> entities, Set<String> topics) {
        DocumentVersion saved = repository.saveDocumentVersion(document);
        search.index(saved, entities, topics);
        search.commit();
        repository.appendOutbox("document", saved.id(), "document.normalized", "{}", clock.instant());
        return saved;
    }

    public Story saveStory(Story story, List<StoryMember> members) {
        Story saved = repository.saveStory(story);
        members.forEach(repository::addStoryMember);
        search.index(saved);
        search.commit();
        repository.appendOutbox("story", saved.id(), "story.updated", "{}", clock.instant());
        return saved;
    }

    public List<FeedItem> feed(ViewType viewType, UUID savedViewId, int limit, String cursor) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("limit must be between 1 and 100");
        return repository.feed(viewType, savedViewId, limit, cursor);
    }

    public Interaction feedback(UUID storyId, UUID targetId, InteractionType type, String reason, UUID undoOf) {
        Interaction interaction = new Interaction(UUID.randomUUID(), storyId, targetId, type, reason, undoOf, clock.instant());
        repository.saveInteraction(interaction);
        repository.appendOutbox("interaction", interaction.id(), "feedback.recorded", "{}", clock.instant());
        repository.appendAudit("user", "feedback." + type.name().toLowerCase(), "story", String.valueOf(storyId), "{}", clock.instant());
        return interaction;
    }

    public List<SearchIndex.SearchResult> searchLocal(String query, Set<String> types, int limit) {
        if (query == null || query.isBlank()) return List.of();
        return search.search(query, types == null ? Set.of() : types, Math.min(Math.max(limit, 1), 100));
    }

    public IntelligenceRepository repository() { return repository; }
}

