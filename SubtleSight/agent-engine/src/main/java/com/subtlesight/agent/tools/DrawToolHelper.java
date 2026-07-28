package com.subtlesight.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;

/**
 * Java mirror of the frontend {@code knowledgeEditorModel.ts} pure functions.
 * Operates on Draw JSON ({@code {"nodes":[...], "edges":[...]}}) without side effects.
 */
public final class DrawToolHelper {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final double NODE_WIDTH = 150, NODE_HEIGHT = 72;
    private static final double NOTE_WIDTH = 185, NOTE_HEIGHT = 92;
    private static final double DIAMOND_WIDTH = 112, DIAMOND_HEIGHT = 112;

    private DrawToolHelper() {}

    // ── Parse / stringify ──

    public record DrawNode(String id, String kind, double x, double y,
                            double width, double height, String label) {}

    public record DrawEdge(String id, String from, String to) {}

    public record DrawData(List<DrawNode> nodes, List<DrawEdge> edges) {}

    public static DrawData parseDrawing(String raw) {
        if (raw == null || raw.isBlank()) return empty();
        try {
            JsonNode root = JSON.readTree(raw);
            if (!root.has("nodes") || !root.has("edges")) return empty();
            List<DrawNode> nodes = new ArrayList<>();
            for (JsonNode n : root.get("nodes")) {
                nodes.add(new DrawNode(
                        n.path("id").asText(""),
                        n.path("kind").asText("rect"),
                        n.path("x").asDouble(0), n.path("y").asDouble(0),
                        n.path("width").asDouble(NODE_WIDTH),
                        n.path("height").asDouble(NODE_HEIGHT),
                        n.path("label").asText("")
                ));
            }
            List<DrawEdge> edges = new ArrayList<>();
            for (JsonNode e : root.get("edges")) {
                edges.add(new DrawEdge(
                        e.path("id").asText(""),
                        e.path("from").asText(""),
                        e.path("to").asText("")
                ));
            }
            return new DrawData(nodes, edges);
        } catch (Exception e) {
            return empty();
        }
    }

    public static String stringifyDrawing(DrawData data) {
        ObjectNode root = JSON.createObjectNode();
        ArrayNode nodes = root.putArray("nodes");
        for (DrawNode n : data.nodes()) {
            ObjectNode node = nodes.addObject();
            node.put("id", n.id());
            node.put("kind", n.kind());
            node.put("x", n.x()); node.put("y", n.y());
            node.put("width", n.width()); node.put("height", n.height());
            node.put("label", n.label());
        }
        ArrayNode edges = root.putArray("edges");
        for (DrawEdge e : data.edges()) {
            ObjectNode edge = edges.addObject();
            edge.put("id", e.id()); edge.put("from", e.from()); edge.put("to", e.to());
        }
        return root.toString();
    }

    private static DrawData empty() {
        return new DrawData(new ArrayList<>(), new ArrayList<>());
    }

    // ── Operations ──

    public static DrawData addNode(DrawData data, String kind, String label, Double x, Double y) {
        return addNodeWithId(data, null, kind, label, x, y);
    }

    public static DrawData addNodeWithId(DrawData data, String explicitId, String kind, String label, Double x, Double y) {
        String nodeId = explicitId != null && !explicitId.isBlank()
                ? explicitId
                : "node-" + UUID.randomUUID().toString().substring(0, 8);
        String k = kind != null ? kind : "rect";
        boolean isNote = "note".equals(k);
        boolean isDiamond = "diamond".equals(k);
        DrawNode node = new DrawNode(
                nodeId, k,
                x != null ? x : 225 + (data.nodes().size() % 5) * 24,
                y != null ? y : 150 + (data.nodes().size() % 4) * 22,
                isNote ? NOTE_WIDTH : isDiamond ? DIAMOND_WIDTH : NODE_WIDTH,
                isNote ? NOTE_HEIGHT : isDiamond ? DIAMOND_HEIGHT : NODE_HEIGHT,
                label != null ? label : "新建步骤"
        );
        List<DrawNode> nodes = new ArrayList<>(data.nodes());
        nodes.add(node);
        return new DrawData(nodes, data.edges());
    }

    public static DrawData addEdge(DrawData data, String from, String to) {
        if (from == null || to == null) return data;
        String edgeId = "e-" + UUID.randomUUID().toString().substring(0, 8);
        List<DrawEdge> edges = new ArrayList<>(data.edges());
        edges.add(new DrawEdge(edgeId, from, to));
        return new DrawData(data.nodes(), edges);
    }

    public static DrawData updateNode(DrawData data, String nodeId, String label,
                                       String kind, Double x, Double y) {
        List<DrawNode> nodes = new ArrayList<>();
        for (DrawNode n : data.nodes()) {
            if (n.id().equals(nodeId)) {
                nodes.add(new DrawNode(
                        n.id(),
                        kind != null ? kind : n.kind(),
                        x != null ? x : n.x(),
                        y != null ? y : n.y(),
                        n.width(), n.height(),
                        label != null ? label : n.label()
                ));
            } else {
                nodes.add(n);
            }
        }
        return new DrawData(nodes, data.edges());
    }

    public static DrawData removeNode(DrawData data, String nodeId) {
        List<DrawNode> nodes = data.nodes().stream()
                .filter(n -> !n.id().equals(nodeId)).toList();
        List<DrawEdge> edges = data.edges().stream()
                .filter(e -> !e.from().equals(nodeId) && !e.to().equals(nodeId))
                .toList();
        return new DrawData(nodes, edges);
    }

    /** Simple grid-based auto-layout: arrange nodes in rows of 3. */
    public static DrawData autoLayout(DrawData data) {
        List<DrawNode> nodes = new ArrayList<>();
        int idx = 0;
        for (DrawNode n : data.nodes()) {
            int col = idx % 3;
            int row = idx / 3;
            nodes.add(new DrawNode(n.id(), n.kind(),
                    80 + col * 280, 80 + row * 160,
                    n.width(), n.height(), n.label()));
            idx++;
        }
        return new DrawData(nodes, data.edges());
    }
}
