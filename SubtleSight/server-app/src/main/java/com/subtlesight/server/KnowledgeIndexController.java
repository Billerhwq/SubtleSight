package com.subtlesight.server;

import com.subtlesight.application.TraceableQaPorts.Repository;
import com.subtlesight.domain.TraceableQa.*;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/knowledge/index")
public class KnowledgeIndexController {
    private final Repository repository;
    private final SqliteKnowledgeRepository knowledge;
    private final KnowledgeIndexJobs jobs;

    public KnowledgeIndexController(Repository repository, SqliteKnowledgeRepository knowledge, KnowledgeIndexJobs jobs) {
        this.repository = repository; this.knowledge = knowledge; this.jobs = jobs;
    }

    @GetMapping("/status")
    Map<String, Object> status(@RequestParam ResourceType type, @RequestParam UUID id,
                               @RequestParam(required = false) String version) {
        String resolved = version == null ? repository.currentVersion(type, id)
                .orElseThrow(() -> new IllegalArgumentException("resource not found")) : version;
        ResourceVersionKey key = new ResourceVersionKey(type, id, resolved);
        return Map.of("resource", key, "status", repository.indexStatus(key), "units", repository.units(key).size());
    }

    @PostMapping("/rebuild")
    Map<String, Object> rebuild(@RequestBody RebuildRequest request) {
        if (request.type() == ResourceType.FILE) jobs.submit(knowledge.findFile(request.id())
                .orElseThrow(() -> new IllegalArgumentException("file not found")));
        else jobs.submit(knowledge.findDocument(request.id())
                .orElseThrow(() -> new IllegalArgumentException("document not found")));
        return Map.of("accepted", true, "type", request.type(), "id", request.id());
    }

    public record RebuildRequest(ResourceType type, UUID id) {}
}
