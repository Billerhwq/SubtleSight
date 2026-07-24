package com.subtlesight.search;

import com.subtlesight.application.TraceableQaPorts.LexicalUnitIndex;
import com.subtlesight.domain.TraceableQa.KnowledgeUnit;
import com.subtlesight.domain.TraceableQa.ResourceVersionKey;
import com.subtlesight.domain.TraceableQa.UnitHit;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class LuceneLexicalUnitIndex implements LexicalUnitIndex {
    private final Analyzer analyzer = new SmartChineseAnalyzer();
    private final IndexWriter writer;
    private final SearcherManager manager;

    public LuceneLexicalUnitIndex(Path path) {
        try {
            Files.createDirectories(path);
            IndexWriterConfig config = new IndexWriterConfig(analyzer).setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            config.setSimilarity(new BM25Similarity());
            writer = new IndexWriter(FSDirectory.open(path), config);
            manager = new SearcherManager(writer, null);
        } catch (IOException e) { throw new IllegalStateException("cannot initialize knowledge lexical index", e); }
    }

    @Override public synchronized void upsert(List<KnowledgeUnit> units) {
        try {
            for (KnowledgeUnit unit : units) {
                Document document = new Document();
                document.add(new StringField("key", unit.id().toString(), Field.Store.YES));
                document.add(new StringField("unitId", unit.id().toString(), Field.Store.YES));
                document.add(new StringField("resourceKey", unit.versionKey().externalKey(), Field.Store.NO));
                document.add(new StringField("resourceType", unit.resourceType().name(), Field.Store.YES));
                document.add(new StringField("unitType", unit.unitType().name(), Field.Store.YES));
                document.add(new TextField("locator", unit.stableLocator(), Field.Store.YES));
                document.add(new TextField("body", unit.text(), Field.Store.YES));
                document.add(new TextField("context", unit.contextText(), Field.Store.NO));
                writer.updateDocument(new Term("key", unit.id().toString()), document);
            }
        } catch (IOException e) { throw new IllegalStateException("knowledge lexical upsert failed", e); }
    }

    @Override public synchronized void delete(ResourceVersionKey version) {
        try { writer.deleteDocuments(new Term("resourceKey", version.externalKey())); }
        catch (IOException e) { throw new IllegalStateException("knowledge lexical delete failed", e); }
    }

    @Override public List<UnitHit> search(String queryText, Set<UUID> allowedUnitIds, int limit) {
        if (queryText == null || queryText.isBlank() || allowedUnitIds == null || allowedUnitIds.isEmpty() || limit < 1)
            return List.of();
        try {
            manager.maybeRefresh();
            IndexSearcher searcher = manager.acquire();
            try {
                MultiFieldQueryParser parser = new MultiFieldQueryParser(
                        new String[]{"body", "context", "locator"}, analyzer,
                        Map.of("body", 2.0f, "context", 0.6f, "locator", 0.4f));
                Query text = parser.parse(QueryParser.escape(queryText));
                Query scope = new TermInSetQuery("unitId", allowedUnitIds.stream().map(UUID::toString).map(BytesRef::new).toList());
                Query query = new BooleanQuery.Builder()
                        .add(text, BooleanClause.Occur.MUST)
                        .add(scope, BooleanClause.Occur.FILTER)
                        .build();
                TopDocs top = searcher.search(query, limit);
                List<UnitHit> hits = new ArrayList<>();
                for (ScoreDoc score : top.scoreDocs) {
                    Document document = searcher.storedFields().document(score.doc);
                    hits.add(new UnitHit(UUID.fromString(document.get("unitId")), score.score, "LEXICAL"));
                }
                return List.copyOf(hits);
            } finally { manager.release(searcher); }
        } catch (Exception e) { throw new IllegalStateException("knowledge lexical search failed", e); }
    }

    @Override public synchronized void commit() {
        try { writer.commit(); manager.maybeRefreshBlocking(); }
        catch (IOException e) { throw new IllegalStateException("knowledge lexical commit failed", e); }
    }

    @Override public synchronized void close() {
        try { manager.close(); writer.close(); analyzer.close(); }
        catch (IOException e) { throw new IllegalStateException("knowledge lexical close failed", e); }
    }
}
