package com.subtlesight.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.domain.TraceableQa.QaAnswer;
import com.subtlesight.domain.TraceableQa.ScopeRef;
import com.subtlesight.jobs.DurableJobQueue;
import com.subtlesight.qa.TraceableQaService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TraceableQaJobs {
    public static final String TYPE = "TRACEABLE_QA";
    private final TraceableQaService qa;
    private final DurableJobQueue jobs;
    private final ObjectMapper json;

    public TraceableQaJobs(TraceableQaService qa, DurableJobQueue jobs, ObjectMapper json) {
        this.qa = qa; this.jobs = jobs; this.json = json;
    }

    public Submission submit(UUID conversationId, UUID parentAnswerId,
                             String question, List<ScopeRef> scopes) {
        TraceableQaService.StartResult result = qa.start(conversationId, parentAnswerId, question, scopes);
        return new Submission(result, enqueue(result.answer()));
    }

    public Submission followUp(UUID parentAnswerId, String question) {
        TraceableQaService.StartResult result = qa.followUp(parentAnswerId, question);
        return new Submission(result, enqueue(result.answer()));
    }

    private UUID enqueue(QaAnswer answer) {
        try {
            return jobs.submit(TYPE, 80, json.writeValueAsString(Map.of("answerId", answer.id().toString())),
                    "traceable-qa:" + answer.id(), 2, null).id();
        } catch (Exception e) { throw new IllegalStateException("cannot enqueue traceable answer", e); }
    }

    public record Submission(TraceableQaService.StartResult result, UUID jobId) {}
}
