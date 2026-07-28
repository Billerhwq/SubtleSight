package com.subtlesight.server;

import com.subtlesight.application.TraceableQaPorts.Repository;
import com.subtlesight.domain.TraceableQa.*;
import com.subtlesight.qa.TraceableQaService;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeDocument;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeFile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/qa")
public class TraceableQaController {
    private final Repository repository;
    private final TraceableQaService qa;
    private final TraceableQaJobs jobs;
    private final TraceableQaPersistenceService persistence;
    private final KnowledgeService knowledge;

    public TraceableQaController(Repository repository, TraceableQaService qa,
                                 TraceableQaJobs jobs, TraceableQaPersistenceService persistence,
                                 KnowledgeService knowledge) {
        this.repository = repository; this.qa = qa; this.jobs = jobs; this.persistence = persistence;
        this.knowledge = knowledge;
    }

    @PostMapping("/scopes/resolve")
    ResolvedScope resolveScope(@RequestBody ScopeRequest request) { return repository.resolveScope(request.scopes()); }

    @PostMapping("/answers")
    ResponseEntity<StartResponse> answer(@RequestBody AnswerRequest request) {
        TraceableQaJobs.Submission submission = jobs.submit(
                request.conversationId(), request.parentAnswerId(), request.question(), request.scopes());
        return ResponseEntity.accepted().body(new StartResponse(
                submission.result().answer(), submission.result().scope(), submission.jobId()));
    }

    @PostMapping("/answers/{answerId}/follow-ups")
    ResponseEntity<StartResponse> followUp(@PathVariable UUID answerId, @RequestBody FollowUpRequest request) {
        TraceableQaJobs.Submission submission = jobs.followUp(answerId, request.question());
        return ResponseEntity.accepted().body(new StartResponse(
                submission.result().answer(), submission.result().scope(), submission.jobId()));
    }

    @GetMapping("/answers/{answerId}")
    Map<String, Object> answer(@PathVariable UUID answerId) {
        var view = qa.answerView(answerId);
        // Build a resourceId → resourceName lookup so the frontend can show
        // human-readable names instead of "FILE/85f3c7ee".
        Map<String, String> resourceNames = new LinkedHashMap<>();
        for (var claim : view.claims()) {
            for (var citation : claim.citations()) {
                String key = citation.resourceId().toString();
                if (!resourceNames.containsKey(key)) {
                    resourceNames.put(key, resolveResourceName(citation.resourceType(), citation.resourceId()));
                }
            }
        }
        return Map.of(
                "answer", view.answer(),
                "claims", view.claims(),
                "scopeSnapshotJson", view.scopeSnapshotJson(),
                "resourceNames", resourceNames
        );
    }

    @GetMapping("/citations/{citationId}/resolve")
    CitationResolution citation(@PathVariable UUID citationId) { return qa.resolveCitation(citationId); }

    @PostMapping("/answers/{answerId}/save-document")
    KnowledgeDocument saveDocument(@PathVariable UUID answerId, @RequestBody SaveDocumentRequest request) {
        return persistence.saveAsDocument(answerId, request.folderId(), request.title());
    }

    @PostMapping("/claims/{claimId}/add-to-draw")
    KnowledgeDocument addToDraw(@PathVariable UUID claimId, @RequestBody AddToDrawRequest request) {
        return persistence.addClaimToDraw(claimId, request.documentId(), request.expectedVersion());
    }

    private String resolveResourceName(ResourceType type, UUID resourceId) {
        try {
            return switch (type) {
                case DOCUMENT, DRAW_NODE -> {
                    var doc = knowledge.findDocument(resourceId);
                    yield doc.isPresent() ? doc.get().title() : type.name();
                }
                case FILE -> {
                    var file = knowledge.findFile(resourceId);
                    yield file.isPresent() ? file.get().name() : type.name();
                }
            };
        } catch (Exception e) {
            return type.name();
        }
    }

    public record ScopeRequest(List<ScopeRef> scopes) {}
    public record AnswerRequest(UUID conversationId, UUID parentAnswerId, String question, List<ScopeRef> scopes) {}
    public record FollowUpRequest(String question) {}
    public record StartResponse(QaAnswer answer, ResolvedScope scope, UUID jobId) {}
    public record SaveDocumentRequest(UUID folderId, String title) {}
    public record AddToDrawRequest(UUID documentId, int expectedVersion) {}
}
