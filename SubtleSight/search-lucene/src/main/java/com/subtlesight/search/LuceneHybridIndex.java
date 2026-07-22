package com.subtlesight.search;

import com.subtlesight.application.Ports.SearchIndex;
import com.subtlesight.domain.Models.DocumentVersion;
import com.subtlesight.domain.Models.Story;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.index.VectorSimilarityFunction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** SQLite remains authoritative; this BM25 + HNSW projection is intentionally rebuildable. */
public final class LuceneHybridIndex implements SearchIndex {
    static final int VECTOR_DIMENSIONS = 384;
    private final Analyzer analyzer = new SmartChineseAnalyzer();
    private final IndexWriter writer;
    private final SearcherManager manager;

    public LuceneHybridIndex(Path path) {
        try {
            Files.createDirectories(path);
            IndexWriterConfig config = new IndexWriterConfig(analyzer).setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            config.setSimilarity(new BM25Similarity());
            this.writer = new IndexWriter(FSDirectory.open(path), config);
            this.manager = new SearcherManager(writer, null);
        } catch (IOException ex) { throw new IllegalStateException("cannot initialize Lucene", ex); }
    }

    @Override public synchronized void index(DocumentVersion d, Set<String> entities, Set<String> topics) {
        Document doc = base("document", d.id(), d.title(), d.text());
        doc.add(new StringField("rawId", d.rawDocumentId().toString(), Field.Store.YES));
        doc.add(new StringField("language", d.language(), Field.Store.YES));
        entities.forEach(value -> doc.add(new StringField("entity", value, Field.Store.YES)));
        topics.forEach(value -> doc.add(new StringField("topic", value, Field.Store.YES)));
        update("document", d.id(), doc);
    }

    @Override public synchronized void index(Story s) {
        String body = s.summary() == null ? s.title() : s.title() + "\n" + s.summary();
        Document doc = base("story", s.id(), s.title(), body);
        s.entities().forEach(value -> doc.add(new StringField("entity", value, Field.Store.YES)));
        s.topics().forEach(value -> doc.add(new StringField("topic", value, Field.Store.YES)));
        update("story", s.id(), doc);
    }

    private Document base(String type, UUID id, String title, String body) {
        Document doc = new Document();
        doc.add(new StringField("key", type + ":" + id, Field.Store.YES));
        doc.add(new StringField("type", type, Field.Store.YES));
        doc.add(new StringField("id", id.toString(), Field.Store.YES));
        doc.add(new TextField("title", title == null ? "" : title, Field.Store.YES));
        doc.add(new TextField("body", body == null ? "" : body, Field.Store.YES));
        doc.add(new KnnFloatVectorField("vector", featureVector((title == null ? "" : title) + " " + (body == null ? "" : body)), VectorSimilarityFunction.COSINE));
        return doc;
    }
    private void update(String type, UUID id, Document doc) {
        try { writer.updateDocument(new Term("key", type + ":" + id), doc); }
        catch (IOException ex) { throw new IllegalStateException("Lucene update failed", ex); }
    }
    @Override public synchronized void delete(String type, UUID id) {
        try { writer.deleteDocuments(new Term("key", type + ":" + id)); }
        catch (IOException ex) { throw new IllegalStateException("Lucene delete failed", ex); }
    }

    @Override public List<SearchResult> search(String queryText, Set<String> types, int limit) {
        if (queryText == null || queryText.isBlank() || limit <= 0) return List.of();
        try {
            manager.maybeRefresh();
            IndexSearcher searcher = manager.acquire();
            try {
                Query typeFilter = typeFilter(types);
                MultiFieldQueryParser parser = new MultiFieldQueryParser(new String[]{"title","body"}, analyzer, Map.of("title", 2.0f, "body", 1.0f));
                Query text = parser.parse(QueryParser.escape(queryText));
                BooleanQuery bm25 = new BooleanQuery.Builder().add(text, BooleanClause.Occur.MUST).add(typeFilter, BooleanClause.Occur.FILTER).build();
                int k = Math.max(limit * 3, 20);
                TopDocs lexical = searcher.search(bm25, k);
                TopDocs semantic = searcher.search(new KnnFloatVectorQuery("vector", featureVector(queryText), k, typeFilter), k);
                return reciprocalRankFusion(searcher, lexical.scoreDocs, semantic.scoreDocs, limit);
            } finally { manager.release(searcher); }
        } catch (Exception ex) { throw new IllegalStateException("Lucene search failed", ex); }
    }

    private List<SearchResult> reciprocalRankFusion(IndexSearcher searcher, ScoreDoc[] lexical, ScoreDoc[] semantic, int limit) throws IOException {
        Map<String, Double> scores = new HashMap<>();
        Map<String, Integer> docIds = new HashMap<>();
        addRanks(searcher, lexical, scores, docIds);
        addRanks(searcher, semantic, scores, docIds);
        List<Map.Entry<String, Double>> ranked = scores.entrySet().stream().sorted(Map.Entry.<String,Double>comparingByValue().reversed()).limit(limit).toList();
        List<SearchResult> results = new ArrayList<>();
        for (Map.Entry<String, Double> item : ranked) {
            Document doc = searcher.storedFields().document(docIds.get(item.getKey()));
            String body = doc.get("body");
            String snippet = body == null ? "" : body.substring(0, Math.min(body.length(), 240));
            results.add(new SearchResult(doc.get("type"), UUID.fromString(doc.get("id")), doc.get("title"), snippet, item.getValue().floatValue()));
        }
        return results;
    }
    private void addRanks(IndexSearcher searcher, ScoreDoc[] hits, Map<String, Double> scores, Map<String,Integer> ids) throws IOException {
        for (int rank = 0; rank < hits.length; rank++) {
            String key = searcher.storedFields().document(hits[rank].doc).get("key");
            scores.merge(key, 1d / (60 + rank + 1), Double::sum);
            ids.putIfAbsent(key, hits[rank].doc);
        }
    }
    private Query typeFilter(Set<String> types) {
        if (types == null || types.isEmpty()) return new MatchAllDocsQuery();
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        types.stream().map(v -> v.toLowerCase(Locale.ROOT)).forEach(type -> builder.add(new TermQuery(new Term("type", type)), BooleanClause.Occur.SHOULD));
        builder.setMinimumNumberShouldMatch(1);
        return builder.build();
    }

    static float[] featureVector(String text) {
        float[] vector = new float[VECTOR_DIMENSIONS];
        String normalized = text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (normalized.isEmpty()) { vector[0] = 1f; return vector; }
        for (int i = 0; i < normalized.length(); i++) {
            String token = normalized.substring(i, Math.min(i + 3, normalized.length()));
            int hash = token.hashCode();
            int index = Math.floorMod(hash, vector.length);
            vector[index] += (hash & 1) == 0 ? 1f : -1f;
        }
        double norm = 0;
        for (float value : vector) norm += value * value;
        norm = Math.sqrt(norm);
        if (norm == 0) vector[0] = 1f;
        else for (int i = 0; i < vector.length; i++) vector[i] /= (float) norm;
        return vector;
    }

    @Override public synchronized void commit() {
        try { writer.commit(); manager.maybeRefreshBlocking(); }
        catch (IOException ex) { throw new IllegalStateException("Lucene commit failed", ex); }
    }
    @Override public synchronized void close() {
        try { manager.close(); writer.close(); analyzer.close(); }
        catch (IOException ex) { throw new IllegalStateException("Lucene close failed", ex); }
    }
}

