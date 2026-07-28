package com.subtlesight.research;

import com.subtlesight.application.Ports.IntelligenceRepository;
import com.subtlesight.application.Ports.ReaderProvider;
import com.subtlesight.domain.CanonicalUrl;
import com.subtlesight.domain.Hashing;
import com.subtlesight.domain.Models.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * Fetches real web page content via {@link ReaderProvider} and falls back to
 * the local repository when fetch fails or budgets are tight.
 * <p>
 * This replaces the previous pass-through lambda that only read from the local
 * repository without ever touching the web.
 */
public final class WebMaterialProvider implements DeepResearchService.MaterialProvider {

    private final IntelligenceRepository repository;
    private final ReaderProvider reader;
    private final Clock clock;

    public WebMaterialProvider(IntelligenceRepository repository, ReaderProvider reader, Clock clock) {
        this.repository = repository;
        this.reader = reader;
        this.clock = clock;
    }

    @Override
    public List<DocumentVersion> materialize(String question, List<SearchHit> hits, int maxPages) {
        if (hits == null || hits.isEmpty()) {
            return repository.listDocumentVersions(Math.min(Math.max(maxPages, 0), 100));
        }

        List<DocumentVersion> documents = new ArrayList<>();
        Set<String> seenUrls = new LinkedHashSet<>();
        int budget = Math.max(1, maxPages);

        for (SearchHit hit : hits) {
            if (documents.size() >= budget) break;
            String url = CanonicalUrl.normalize(hit.url());
            if (seenUrls.contains(url)) continue;
            seenUrls.add(url);

            // Try fetching real web content
            DocumentVersion doc = fetchOne(hit, url);
            if (doc != null) {
                documents.add(doc);
            }
        }

        // Fallback: supplement with local repository documents
        if (documents.size() < budget) {
            int remaining = budget - documents.size();
            Set<UUID> alreadyHave = new LinkedHashSet<>();
            for (DocumentVersion d : documents) alreadyHave.add(d.id());
            List<DocumentVersion> local = repository.listDocumentVersions(remaining);
            for (DocumentVersion d : local) {
                if (!alreadyHave.contains(d.id()) && documents.size() < budget) {
                    documents.add(d);
                }
            }
        }

        return documents;
    }

    private DocumentVersion fetchOne(SearchHit hit, String url) {
        try {
            URI uri = URI.create(url);
            ReaderProvider.ReadResult result = reader.read(uri);

            if (result == null || result.content() == null || result.content().length == 0) {
                return createPlaceholder(hit, url, "empty response");
            }

            String text = new String(result.content(), StandardCharsets.UTF_8);
            if (text.isBlank()) return createPlaceholder(hit, url, "blank page");

            // Truncate to reasonable size
            if (text.length() > 256_000) text = text.substring(0, 256_000);

            Instant now = clock.instant();
            String hash = Hashing.sha256(text.getBytes(StandardCharsets.UTF_8));
            String title = hit.title() != null && !hit.title().isBlank() ? hit.title() : url;

            return new DocumentVersion(
                    UUID.randomUUID(),       // id
                    UUID.randomUUID(),       // rawDocumentId (synthetic)
                    title,                   // title
                    hit.provider(),          // author (search provider name)
                    hit.publishedAt() != null ? hit.publishedAt() : now, // publishedAt
                    "und",                   // language
                    url,                     // canonicalUrl
                    stripHtml(text),         // text (plain text)
                    hash,                    // textHash
                    firstSentence(text),     // summary
                    "web-fetch-v1",          // algorithmVersion
                    "safe-reader",           // modelVersion
                    false,                   // promptInjection
                    now                      // createdAt
            );
        } catch (Exception e) {
            // Individual fetch failures are silently skipped
            return createPlaceholder(hit, url, "fetch failed: " + e.getClass().getSimpleName());
        }
    }

    private DocumentVersion createPlaceholder(SearchHit hit, String url, String reason) {
        Instant now = clock.instant();
        String snippet = hit.snippet() != null ? hit.snippet() : reason;
        String hash = Hashing.sha256(snippet.getBytes(StandardCharsets.UTF_8));
        return new DocumentVersion(
                UUID.randomUUID(), UUID.randomUUID(),
                hit.title() != null ? hit.title() : url,
                hit.provider(), now, "und", url, snippet, hash,
                firstSentence(snippet), "web-placeholder-v1", "none", false, now);
    }

    // ── Helpers ──

    private static String firstSentence(String text) {
        if (text == null || text.isBlank()) return "";
        int end = text.length();
        for (char c : new char[]{'。', '.', '!', '！', '?', '？', '\n'}) {
            int p = text.indexOf(c);
            if (p >= 0) end = Math.min(end, p + 1);
        }
        return text.substring(0, Math.min(end, 500)).trim();
    }

    private static String stripHtml(String text) {
        if (text == null) return "";
        return text
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replaceAll("&[a-z]+;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
