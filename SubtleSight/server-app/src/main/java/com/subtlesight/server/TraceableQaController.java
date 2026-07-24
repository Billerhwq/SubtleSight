package com.subtlesight.server;

import com.subtlesight.application.TraceableQaPorts.Repository;
import com.subtlesight.domain.TraceableQa.*;
import com.subtlesight.qa.TraceableQaService;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeDocument;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/qa")
public class TraceableQaController {
    private final Repository repository;
    private final TraceableQaService qa;
    private final TraceableQaJobs jobs;
    private final TraceableQaPersistenceService persistence;

    public TraceableQaController(Repository repository, TraceableQaService qa,
                                 TraceableQaJobs jobs, TraceableQaPersistenceService persistence) {
        this.repository = repository; this.qa = qa; this.jobs = jobs; this.persistence = persistence;
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
    TraceableQaService.AnswerView answer(@PathVariable UUID answerId) { return qa.answerView(answerId); }

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

    public record ScopeRequest(List<ScopeRef> scopes) {}
    public record AnswerRequest(UUID conversationId, UUID parentAnswerId, String question, List<ScopeRef> scopes) {}
    public record FollowUpRequest(String question) {}
    public record StartResponse(QaAnswer answer, ResolvedScope scope, UUID jobId) {}
    public record SaveDocumentRequest(UUID folderId, String title) {}
    public record AddToDrawRequest(UUID documentId, int expectedVersion) {}
}
