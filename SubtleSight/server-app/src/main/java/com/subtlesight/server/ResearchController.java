package com.subtlesight.server;

import com.subtlesight.domain.Models.Claim;
import com.subtlesight.domain.Models.ResearchBudget;
import com.subtlesight.domain.Models.ResearchMode;
import com.subtlesight.domain.Models.ResearchRun;
import com.subtlesight.jobs.DurableJobQueue;
import com.subtlesight.research.DeepResearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/research")
public class ResearchController {

    private final DeepResearchService research;
    private final DurableJobQueue jobs;
    private final com.subtlesight.application.Ports.IntelligenceRepository repo;

    public ResearchController(DeepResearchService research, DurableJobQueue jobs,
                               com.subtlesight.application.Ports.IntelligenceRepository repo) {
        this.research = research; this.jobs = jobs; this.repo = repo;
    }

    @GetMapping
    public List<ResearchRun> list() {
        return repo.listResearch(200);
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable UUID id) {
        ResearchRun run = repo.findResearch(id).orElseThrow();
        List<Claim> claims = repo.researchClaims(id);
        return Map.of("run", run, "claims", claims);
    }

    @PostMapping
    public ResponseEntity<ResearchRun> start(@RequestBody StartRequest req) {
        ResearchMode mode = req.resolvedMode();
        ResearchBudget budget = switch (mode) {
            case VERIFY -> new ResearchBudget(3, 8, 4096, new BigDecimal("2"), 300);
            case STANDARD -> new ResearchBudget(6, 20, 8192, new BigDecimal("5"), 600);
            case DEEP -> new ResearchBudget(12, 40, 16384, new BigDecimal("10"), 900);
            case CONTINUOUS -> new ResearchBudget(20, 80, 24576, new BigDecimal("15"), 1800);
        };
        ResearchRun run = research.create(req.storyUuid(), req.question(), mode, budget);
        // Submit to background job queue
        try {
            String payload = "{\"researchId\":\"" + run.id() + "\"}";
            jobs.submit("RESEARCH", 2, payload, run.id().toString(), 3, null);
        } catch (Exception ignored) {
            // Job will be picked up by the runner on next poll
        }
        return ResponseEntity.accepted().body(run);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable UUID id) {
        // Cancel all jobs related to this research
        var jobList = jobs.list(500);
        for (var job : jobList) {
            if ("RESEARCH".equals(job.type()) && job.payloadJson().contains(id.toString())) {
                jobs.cancel(job.id());
            }
        }
        return ResponseEntity.noContent().build();
    }

    public record StartRequest(String storyId, String question, String mode) {
        public StartRequest {
            question = question == null || question.isBlank() ? "未命名研究" : question;
        }
        public UUID storyUuid() {
            if (storyId == null || storyId.isBlank()) return null;
            try { return UUID.fromString(storyId); }
            catch (IllegalArgumentException e) { return null; }
        }
        public ResearchMode resolvedMode() {
            if (mode == null || mode.isBlank()) return ResearchMode.STANDARD;
            try { return ResearchMode.valueOf(mode); }
            catch (IllegalArgumentException e) { return ResearchMode.STANDARD; }
        }
    }
}
