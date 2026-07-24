package com.subtlesight.search;

import com.subtlesight.application.TraceableQaPorts.SemanticUnitIndex;
import com.subtlesight.domain.TraceableQa.EmbeddingProfile;
import com.subtlesight.domain.TraceableQa.ResourceVersionKey;
import com.subtlesight.domain.TraceableQa.UnitHit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class LuceneSemanticUnitIndex implements SemanticUnitIndex {
    private final IndexWriter writer;
    private final SearcherManager manager;

    public LuceneSemanticUnitIndex(Path path) {
        try {
            Files.createDirectories(path);
            writer = new IndexWriter(FSDirectory.open(path), new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND));
            manager = new SearcherManager(writer, null);
        } catch (IOException e) { throw new IllegalStateException("cannot initialize knowledge semantic index", e); }
    }

    @Override public synchronized void upsert(EmbeddingProfile profile, List<com.subtlesight.domain.TraceableQa.KnowledgeUnit> units, List<float[]> vectors) {
        if (units.size() != vectors.size()) throw new IllegalArgumentException("embedding result size mismatch");
        try {
            for (int i = 0; i < units.size(); i++) {
                var unit = units.get(i);
                float[] vector = vectors.get(i);
                if (vector.length != profile.dimensions()) throw new IllegalArgumentException("embedding dimension mismatch");
                String key = profile.id() + ":" + unit.id();
                Document document = new Document();
                document.add(new StringField("key", key, Field.Store.YES));
                document.add(new StringField("profile", profile.id(), Field.Store.YES));
                document.add(new StringField("unitId", unit.id().toString(), Field.Store.YES));
                document.add(new StringField("resourceKey", unit.versionKey().externalKey(), Field.Store.NO));
                document.add(new KnnFloatVectorField("vector", vector, VectorSimilarityFunction.COSINE));
                writer.updateDocument(new Term("key", key), document);
            }
        } catch (IOException e) { throw new IllegalStateException("knowledge semantic upsert failed", e); }
    }

    @Override public synchronized void delete(EmbeddingProfile profile, ResourceVersionKey version) {
        try {
            writer.deleteDocuments(new BooleanQuery.Builder()
                    .add(new TermQuery(new Term("profile", profile.id())), BooleanClause.Occur.MUST)
                    .add(new TermQuery(new Term("resourceKey", version.externalKey())), BooleanClause.Occur.MUST)
                    .build());
        } catch (IOException e) { throw new IllegalStateException("knowledge semantic delete failed", e); }
    }

    @Override public List<UnitHit> search(EmbeddingProfile profile, float[] queryVector, Set<UUID> allowedUnitIds, int limit) {
        if (allowedUnitIds == null || allowedUnitIds.isEmpty() || limit < 1) return List.of();
        if (queryVector.length != profile.dimensions()) throw new IllegalArgumentException("embedding dimension mismatch");
        try {
            manager.maybeRefresh();
            IndexSearcher searcher = manager.acquire();
            try {
                var scope = new BooleanQuery.Builder()
                        .add(new TermInSetQuery("unitId", allowedUnitIds.stream().map(UUID::toString).map(BytesRef::new).toList()), BooleanClause.Occur.MUST)
                        .add(new TermQuery(new Term("profile", profile.id())), BooleanClause.Occur.MUST)
                        .build();
                TopDocs top = searcher.search(new KnnFloatVectorQuery("vector", queryVector, limit, scope), limit);
                List<UnitHit> hits = new ArrayList<>();
                for (ScoreDoc score : top.scoreDocs) {
                    Document document = searcher.storedFields().document(score.doc);
                    hits.add(new UnitHit(UUID.fromString(document.get("unitId")), score.score, "SEMANTIC"));
                }
                return List.copyOf(hits);
            } finally { manager.release(searcher); }
        } catch (IOException e) { throw new IllegalStateException("knowledge semantic search failed", e); }
    }

    @Override public String backend() { return "lucene-local-feature"; }

    @Override public synchronized void commit() {
        try { writer.commit(); manager.maybeRefreshBlocking(); }
        catch (IOException e) { throw new IllegalStateException("knowledge semantic commit failed", e); }
    }

    @Override public synchronized void close() {
        try { manager.close(); writer.close(); }
        catch (IOException e) { throw new IllegalStateException("knowledge semantic close failed", e); }
    }
}
