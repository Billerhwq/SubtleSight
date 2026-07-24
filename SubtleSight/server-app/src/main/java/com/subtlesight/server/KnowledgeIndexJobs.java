package com.subtlesight.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.domain.TraceableQa.IndexStatus;
import com.subtlesight.domain.TraceableQa.ResourceType;
import com.subtlesight.domain.TraceableQa.ResourceVersionKey;
import com.subtlesight.jobs.DurableJobQueue;
import com.subtlesight.application.TraceableQaPorts.Repository;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeDocument;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeFile;

import java.util.Map;

public final class KnowledgeIndexJobs {
    public static final String TYPE = "KNOWLEDGE_INDEX";
    private final DurableJobQueue jobs;
    private final Repository repository;
    private final ObjectMapper json;

    public KnowledgeIndexJobs(DurableJobQueue jobs, Repository repository, ObjectMapper json) {
        this.jobs = jobs; this.repository = repository; this.json = json;
    }

    public void submit(KnowledgeFile file) {
        ResourceVersionKey key = new ResourceVersionKey(ResourceType.FILE, file.id(), file.sha256());
        repository.markIndexState(key, IndexStatus.PENDING, null, 0);
        submit(Map.of("resourceType", "FILE", "resourceId", file.id().toString(), "version", file.sha256()), key.externalKey());
    }

    public void submit(KnowledgeDocument document) {
        ResourceVersionKey documentKey = new ResourceVersionKey(ResourceType.DOCUMENT, document.id(), String.valueOf(document.version()));
        ResourceVersionKey drawKey = new ResourceVersionKey(ResourceType.DRAW_NODE, document.id(), String.valueOf(document.version()));
        repository.markIndexState(documentKey, IndexStatus.PENDING, null, 0);
        repository.markIndexState(drawKey, IndexStatus.PENDING, null, 0);
        submit(Map.of("resourceType", "DOCUMENT", "resourceId", document.id().toString(), "version", document.version()), documentKey.externalKey());
    }

    private void submit(Map<String, ?> payload, String dedup) {
        try { jobs.submit(TYPE, 60, json.writeValueAsString(payload), "index:" + dedup, 3, null); }
        catch (Exception e) { throw new IllegalStateException("cannot enqueue knowledge index", e); }
    }
}
