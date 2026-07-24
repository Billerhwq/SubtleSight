package com.subtlesight.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.subtlesight.application.TraceableQaPorts.Repository;
import com.subtlesight.domain.TraceableQa.*;
import com.subtlesight.qa.TraceableQaService;
import com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeDocument;

import java.util.List;
import java.util.UUID;

public final class TraceableQaPersistenceService {
    private final TraceableQaService qa;
    private final KnowledgeService knowledge;
    private final ObjectMapper json;
    private final Repository repository;

    public TraceableQaPersistenceService(TraceableQaService qa, KnowledgeService knowledge,
                                         ObjectMapper json, Repository repository) {
        this.qa = qa; this.knowledge = knowledge; this.json = json; this.repository = repository;
    }

    public KnowledgeDocument saveAsDocument(UUID answerId, UUID folderId, String title) {
        TraceableQaService.AnswerView view = qa.answerView(answerId);
        requireComplete(view.answer());
        StringBuilder html = new StringBuilder("<h2 data-block-id=\"qa-summary\">可追溯回答</h2>");
        html.append("<p data-block-id=\"qa-question\"><strong>问题：</strong>").append(escape(view.answer().question())).append("</p>");
        int ordinal = 1;
        for (TraceableQaService.ClaimView claim : view.claims()) {
            html.append("<p data-block-id=\"qa-claim-").append(ordinal).append("\">")
                    .append(escape(claim.claim().statement()));
            for (QaCitation citation : claim.citations()) html.append(" <sup>[").append(citation.rank() + 1).append("]</sup>");
            html.append("</p>");
            ordinal++;
        }
        html.append("<p data-block-id=\"qa-provenance\"><small>回答 ID：").append(answerId).append("</small></p>");
        return knowledge.createDocument(folderId,
                title == null || title.isBlank() ? "可追溯问答｜" + truncate(view.answer().question(), 60) : title,
                html.toString(), "{\"nodes\":[],\"edges\":[]}");
    }

    public KnowledgeDocument addClaimToDraw(UUID claimId, UUID documentId, int expectedVersion) {
        QaClaim claim = repository.claim(claimId).orElseThrow(() -> new IllegalArgumentException("claim not found"));
        QaAnswer answer = qa.requireAnswer(claim.answerId());
        requireComplete(answer);
        KnowledgeDocument document = knowledge.requireDocument(documentId);
        if (document.version() != expectedVersion) throw new IllegalArgumentException("document version changed");
        try {
            ObjectNode drawing = (ObjectNode) json.readTree(document.drawingJson());
            ArrayNode nodes = drawing.withArray("nodes");
            String nodeId = "qa-" + claim.id();
            for (JsonNode node : nodes) if (nodeId.equals(node.path("id").asText())) return document;
            List<QaCitation> citations = qa.answerView(answer.id()).claims().stream()
                    .filter(value -> value.claim().id().equals(claimId)).findFirst().orElseThrow().citations();
            ObjectNode node = json.createObjectNode();
            node.put("id", nodeId); node.put("kind", "note");
            node.put("x", 180 + (nodes.size() % 4) * 170); node.put("y", 160 + (nodes.size() % 3) * 120);
            node.put("width", 220); node.put("height", 110); node.put("label", truncate(claim.statement(), 120));
            ObjectNode sourceRef = node.putObject("sourceRef");
            sourceRef.put("answerId", answer.id().toString());
            sourceRef.putArray("claimIds").add(claim.id().toString());
            ArrayNode citationIds = sourceRef.putArray("citationIds");
            citations.forEach(citation -> citationIds.add(citation.id().toString()));
            nodes.add(node);
            return knowledge.updateDocument(document.id(), document.folderId(), document.title(), document.contentHtml(),
                    json.writeValueAsString(drawing), expectedVersion, "加入可追溯问答结论");
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalStateException("cannot add answer claim to Draw", e); }
    }

    private static void requireComplete(QaAnswer answer) {
        if (answer.status() != AnswerStatus.COMPLETED) throw new IllegalArgumentException("only completed answers can be saved");
    }
    private static String truncate(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
    private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}
