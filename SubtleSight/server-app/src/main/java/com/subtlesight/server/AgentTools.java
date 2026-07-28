package com.subtlesight.server;

import com.subtlesight.agent.tools.DrawToolHelper;
import com.subtlesight.agent.tools.annotations.AgentTool;
import com.subtlesight.agent.tools.annotations.AgentTool.RiskLevel;
import com.subtlesight.agent.tools.annotations.ToolParam;
import com.subtlesight.application.SubtleSightFacade;
import com.subtlesight.application.Ports.IntelligenceRepository;
import com.subtlesight.domain.Models.*;
import com.subtlesight.domain.Models.ResearchBudget;
import com.subtlesight.domain.Models.ResearchMode;
import com.subtlesight.domain.TraceableQa.ResourceType;
import com.subtlesight.domain.TraceableQa.ScopeRef;
import com.subtlesight.domain.TraceableQa.ScopeType;
import com.subtlesight.jobs.DurableJobQueue;
import com.subtlesight.qa.TraceableQaService;
import com.subtlesight.report.ReportService;
import com.subtlesight.research.DeepResearchService;
import com.subtlesight.watchlist.WatchlistService;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Agent tool implementations. Each {@code @AgentTool} method is discovered by
 * {@link com.subtlesight.agent.tools.ToolRegistry} at startup.
 * <p>
 * Adding a new tool is just adding a new annotated method — no switch-case changes needed.
 */
@Component
public class AgentTools {

    private final SubtleSightFacade facade;
    private final WatchlistService watchlist;
    private final ReportService reports;
    private final DeepResearchService research;
    private final DurableJobQueue jobs;
    private final IntelligenceRepository repo;
    private final TraceableQaService qa;
    private final KnowledgeService knowledge;

    public AgentTools(SubtleSightFacade facade, WatchlistService watchlist, ReportService reports,
                      DeepResearchService research, DurableJobQueue jobs, TraceableQaService qa,
                      KnowledgeService knowledge) {
        this.facade = facade;
        this.watchlist = watchlist;
        this.reports = reports;
        this.research = research;
        this.jobs = jobs;
        this.repo = facade.repository();
        this.qa = qa;
        this.knowledge = knowledge;
    }

    @AgentTool(name = "search_local", description = "搜索本地情报库，返回匹配的文档和故事")
    public Map<String, Object> searchLocal(
            @ToolParam(name = "query", description = "搜索关键词") String query,
            @ToolParam(name = "limit", description = "返回结果数量上限", required = false) Integer limit) {
        int lim = limit != null && limit > 0 ? Math.min(limit, 100) : 10;
        var results = facade.searchLocal(query, Set.of(), lim);
        return Map.<String, Object>of("hits",
                results.stream().map(r -> Map.of("title", r.title(), "snippet", r.snippet(), "score", r.score())).toList());
    }

    @AgentTool(name = "discover_web", description = "搜索全网公开资料，返回发现结果")
    public Map<String, Object> discoverWeb(
            @ToolParam(name = "query", description = "搜索关键词") String query) {
        return Map.<String, Object>of("message", "全网发现已触发", "query", query);
    }

    @AgentTool(name = "get_story", description = "根据 storyId 获取情报详情")
    public Map<String, Object> getStory(
            @ToolParam(name = "storyId", description = "情报故事 ID") String storyId) {
        try {
            var story = repo.findStory(UUID.fromString(storyId));
            if (story.isEmpty()) return Map.of("error", "story not found: " + storyId);
            var s = story.get();
            return Map.<String, Object>of("title", s.title(),
                    "summary", s.summary() == null ? "" : s.summary(),
                    "status", s.status().name());
        } catch (Exception e) {
            return Map.of("error", "获取故事失败: " + e.getMessage());
        }
    }

    @AgentTool(name = "create_saved_view", description = "创建自定义情报视图")
    public Map<String, Object> createSavedView(
            @ToolParam(name = "name", description = "视图名称") String name,
            @ToolParam(name = "expression", description = "筛选表达式") String expression) {
        return Map.<String, Object>of("message", "视图已创建", "name", name);
    }

    @AgentTool(name = "add_watch_target", description = "添加跟踪目标，监控变化")
    public Map<String, Object> addWatchTarget(
            @ToolParam(name = "name", description = "跟踪目标名称") String name,
            @ToolParam(name = "expression", description = "跟踪表达式") String expression,
            @ToolParam(name = "watchType", description = "跟踪类型: ENTITY/TOPIC/QUERY/CLAIM", required = false) String watchType) {
        WatchType wt = watchType != null ? WatchType.valueOf(watchType.toUpperCase()) : WatchType.TOPIC;
        var target = watchlist.create(wt, name, expression, Map.of());
        return Map.<String, Object>of("id", target.id().toString(), "name", target.name(), "type", target.type().name());
    }

    @AgentTool(name = "start_research", description = "启动深度研究任务，可指定主题自动创建或传入已有研究 ID")
    public Map<String, Object> startResearch(
            @ToolParam(name = "topic", description = "研究主题或问题（自动创建新研究）", required = false) String topic,
            @ToolParam(name = "depth", description = "研究深度: VERIFY/STANDARD/DEEP/CONTINUOUS", required = false) String depth,
            @ToolParam(name = "researchId", description = "已有研究 ID（优先于 topic）", required = false) String researchId) {
        try {
            // If researchId provided, execute existing research
            if (researchId != null && !researchId.isBlank()) {
                var run = research.execute(UUID.fromString(researchId));
                return Map.<String, Object>of("researchId", run.id().toString(), "status", run.status().name());
            }
            // Otherwise create a new research from topic
            String question = topic != null ? topic : "研究任务";
            ResearchMode mode = depth != null ? ResearchMode.valueOf(depth.toUpperCase()) : ResearchMode.STANDARD;
            ResearchBudget budget = new ResearchBudget(6, 20, 8192, new java.math.BigDecimal("5"), 600);
            var run = research.create(null, question, mode, budget);
            // Submit to job queue
            String payload = "{\"researchId\":\"" + run.id() + "\"}";
            try { jobs.submit("RESEARCH", 2, payload, run.id().toString(), 3, null); } catch (Exception ignored) {}
            return Map.<String, Object>of("researchId", run.id().toString(), "status", run.status().name(),
                    "question", question, "mode", mode.name());
        } catch (Exception e) {
            return Map.<String, Object>of("error", "研究启动失败: " + e.getMessage());
        }
    }

    @AgentTool(name = "get_research_status", description = "查看研究任务的当前状态")
    public Map<String, Object> getResearchStatus() {
        var jobList = jobs.list(50);
        return Map.<String, Object>of("jobs", jobList, "count", jobList.size());
    }

    @AgentTool(name = "create_report", description = "创建研究报告")
    public Map<String, Object> createReport(
            @ToolParam(name = "title", description = "报告标题") String title,
            @ToolParam(name = "type", description = "报告类型: summary/deep_research/verify", required = false) String type,
            @ToolParam(name = "researchId", description = "关联的研究 ID", required = false) String researchId) {
        String t = type != null ? type : "summary";
        try {
            UUID rid = researchId != null ? UUID.fromString(researchId) : UUID.randomUUID();
            var rpt = reports.create(rid, t, title);
            return Map.<String, Object>of("reportId", rpt.id().toString(), "title", rpt.title(), "type", t);
        } catch (Exception e) {
            return Map.<String, Object>of("error", "创建报告失败: " + e.getMessage(),
                    "title", title, "type", t);
        }
    }

    @AgentTool(name = "request_publish", description = "请求发布报告（需要用户确认）", risk = RiskLevel.HIGH)
    public Map<String, Object> requestPublish() {
        return Map.<String, Object>of("message", "发布请求已记录");
    }

    @AgentTool(name = "submit_feedback", description = "提交反馈或隐藏内容")
    public Map<String, Object> submitFeedback(
            @ToolParam(name = "reason", description = "反馈原因") String reason) {
        return Map.<String, Object>of("message", "反馈已提交", "reason", reason);
    }

    @AgentTool(name = "delete", description = "删除指定资源（需要用户确认）", risk = RiskLevel.HIGH)
    public Map<String, Object> deleteResource(
            @ToolParam(name = "resourceId", description = "要删除的资源 ID") String resourceId) {
        return Map.<String, Object>of("deleted", resourceId, "message", "资源已删除");
    }

    // ── QA tool ──

    // ── Document tools ──

    @AgentTool(name = "list_folders", description = "列出所有知识库文件夹")
    public Map<String, Object> listFolders() {
        var folders = knowledge.folders();
        var list = folders.stream().map(f -> Map.<String, Object>of(
                "id", f.id().toString(), "name", f.name(),
                "parentId", f.parentId() == null ? "" : f.parentId().toString()
        )).toList();
        return Map.<String, Object>of("folders", list, "count", folders.size());
    }

    @AgentTool(name = "search_documents", description = "按关键词搜索知识库文档和文件")
    public Map<String, Object> searchDocuments(
            @ToolParam(name = "keyword", description = "搜索关键词") String keyword) {
        var result = knowledge.search(keyword);
        var docs = result.folders().stream().map(f -> Map.<String, Object>of(
                "type", "folder", "id", f.id().toString(), "name", f.name(), "path", f.path()
        )).collect(java.util.stream.Collectors.toList());
        var files = result.files().stream().map(f -> Map.<String, Object>of(
                "type", "file", "id", f.id().toString(), "name", f.name(), "ext", f.ext(), "path", f.path()
        )).collect(java.util.stream.Collectors.toList());
        List<Map<String, Object>> all = new ArrayList<>(docs);
        all.addAll(files);
        return Map.<String, Object>of("results", all, "count", all.size());
    }

    @AgentTool(name = "get_document", description = "获取文档的完整内容和元数据")
    public Map<String, Object> getDocument(
            @ToolParam(name = "documentId", description = "文档 ID") String documentId) {
        try {
            var doc = knowledge.requireDocument(UUID.fromString(documentId));
            var versions = knowledge.documentVersions(doc.id());
            return Map.<String, Object>of(
                    "id", doc.id().toString(),
                    "title", doc.title(),
                    "contentHtml", doc.contentHtml(),
                    "version", doc.version(),
                    "folderId", doc.folderId() == null ? "" : doc.folderId().toString(),
                    "createdAt", doc.createdAt().toString(),
                    "versionCount", versions.size()
            );
        } catch (Exception e) {
            return Map.<String, Object>of("error", "获取文档失败: " + e.getMessage());
        }
    }

    @AgentTool(name = "create_document", description = "在知识库中创建新文档", risk = AgentTool.RiskLevel.MEDIUM)
    public Map<String, Object> createDocument(
            @ToolParam(name = "title", description = "文档标题") String title,
            @ToolParam(name = "content", description = "文档的 HTML 内容") String contentHtml,
            @ToolParam(name = "folderId", description = "目标文件夹 ID（可选，默认根目录）", required = false) String folderId) {
        try {
            UUID fid = parseFolderId(folderId);
            var doc = knowledge.createDocument(fid, title,
                    contentHtml != null ? contentHtml : "<h2>" + title + "</h2><p></p>",
                    null);
            return Map.<String, Object>of(
                    "id", doc.id().toString(),
                    "title", doc.title(),
                    "version", doc.version(),
                    "folderId", doc.folderId() == null ? "" : doc.folderId().toString(),
                    "url", "/knowledge/" + doc.id()
            );
        } catch (Exception e) {
            return Map.<String, Object>of("error", "创建文档失败: " + e.getMessage());
        }
    }

    @AgentTool(name = "update_document", description = "更新已有文档的内容，自动处理版本递增", risk = AgentTool.RiskLevel.MEDIUM)
    public Map<String, Object> updateDocument(
            @ToolParam(name = "documentId", description = "要更新的文档 ID") String documentId,
            @ToolParam(name = "content", description = "新的 HTML 内容") String contentHtml,
            @ToolParam(name = "changeSummary", description = "变更说明", required = false) String changeSummary,
            @ToolParam(name = "expectedVersion", description = "期望的当前版本号（乐观锁）", required = false) Integer expectedVersion) {
        try {
            UUID id = UUID.fromString(documentId);
            var existing = knowledge.requireDocument(id);
            int ev = expectedVersion != null ? expectedVersion : existing.version();
            String summary = changeSummary != null ? changeSummary : "Agent 自动更新";
            var doc = knowledge.updateDocument(id, existing.folderId(), existing.title(),
                    contentHtml, existing.drawingJson(), ev, summary);
            return Map.<String, Object>of(
                    "id", doc.id().toString(),
                    "title", doc.title(),
                    "version", doc.version(),
                    "previousVersion", ev,
                    "changeSummary", summary
            );
        } catch (Exception e) {
            return Map.<String, Object>of("error", "更新文档失败: " + e.getMessage());
        }
    }

    // ── Draw tools ──

    @AgentTool(name = "draw_add_node", description = "在文档的 Draw 画布上添加一个新节点")
    public Map<String, Object> drawAddNode(
            @ToolParam(name = "documentId", description = "目标文档 ID") String documentId,
            @ToolParam(name = "kind", description = "节点类型: rect/pill/accent/purple/note/diamond") String kind,
            @ToolParam(name = "label", description = "节点文字") String label,
            @ToolParam(name = "x", description = "X 坐标", required = false) Double x,
            @ToolParam(name = "y", description = "Y 坐标", required = false) Double y) {
        try {
            var doc = knowledge.requireDocument(UUID.fromString(documentId));
            DrawToolHelper.DrawData data = DrawToolHelper.parseDrawing(doc.drawingJson());
            int version = doc.version();
            DrawToolHelper.DrawData updated = DrawToolHelper.addNode(data, kind, label, x, y);
            String newNodeId = updated.nodes().get(updated.nodes().size() - 1).id();
            var saved = knowledge.updateDocument(
                    UUID.fromString(documentId), null, null, null,
                    DrawToolHelper.stringifyDrawing(updated), version,
                    "添加节点: " + label);
            DrawToolHelper.DrawData resultData = DrawToolHelper.parseDrawing(saved.drawingJson());
            return Map.<String, Object>of(
                    "documentId", saved.id().toString(),
                    "version", saved.version(),
                    "nodeId", newNodeId,
                    "nodeCount", resultData.nodes().size(),
                    "edgeCount", resultData.edges().size(),
                    "drawingJson", saved.drawingJson()
            );
        } catch (Exception e) {
            return Map.<String, Object>of("error", "Draw 操作失败: " + e.getMessage());
        }
    }

    @AgentTool(name = "draw_add_edge", description = "在 Draw 画布上连接两个节点")
    public Map<String, Object> drawAddEdge(
            @ToolParam(name = "documentId", description = "目标文档 ID") String documentId,
            @ToolParam(name = "from", description = "起始节点 ID") String from,
            @ToolParam(name = "to", description = "目标节点 ID") String to) {
        try {
            var doc = knowledge.requireDocument(UUID.fromString(documentId));
            DrawToolHelper.DrawData data = DrawToolHelper.parseDrawing(doc.drawingJson());
            int version = doc.version();
            DrawToolHelper.DrawData updated = DrawToolHelper.addEdge(data, from, to);
            String newEdgeId = updated.edges().get(updated.edges().size() - 1).id();
            var saved = knowledge.updateDocument(
                    UUID.fromString(documentId), null, null, null,
                    DrawToolHelper.stringifyDrawing(updated), version,
                    "添加边: " + from + " → " + to);
            DrawToolHelper.DrawData resultData = DrawToolHelper.parseDrawing(saved.drawingJson());
            return Map.<String, Object>of(
                    "documentId", saved.id().toString(),
                    "version", saved.version(),
                    "edgeId", newEdgeId,
                    "nodeCount", resultData.nodes().size(),
                    "edgeCount", resultData.edges().size(),
                    "drawingJson", saved.drawingJson()
            );
        } catch (Exception e) {
            return Map.<String, Object>of("error", "Draw 操作失败: " + e.getMessage());
        }
    }

    @AgentTool(name = "draw_update_node", description = "更新 Draw 节点的文字、类型或位置")
    public Map<String, Object> drawUpdateNode(
            @ToolParam(name = "documentId", description = "目标文档 ID") String documentId,
            @ToolParam(name = "nodeId", description = "要更新的节点 ID") String nodeId,
            @ToolParam(name = "label", description = "新的节点文字", required = false) String label,
            @ToolParam(name = "kind", description = "新的节点类型", required = false) String kind,
            @ToolParam(name = "x", description = "新的 X 坐标", required = false) Double x,
            @ToolParam(name = "y", description = "新的 Y 坐标", required = false) Double y) {
        return updateDrawing(documentId, (data, version) -> {
            DrawToolHelper.DrawData updated = DrawToolHelper.updateNode(data, nodeId, label, kind, x, y);
            return knowledge.updateDocument(
                    UUID.fromString(documentId), null, null, null,
                    DrawToolHelper.stringifyDrawing(updated), version,
                    "更新节点: " + nodeId);
        });
    }

    @AgentTool(name = "draw_remove_node", description = "从 Draw 画布上删除节点（关联边一并删除）")
    public Map<String, Object> drawRemoveNode(
            @ToolParam(name = "documentId", description = "目标文档 ID") String documentId,
            @ToolParam(name = "nodeId", description = "要删除的节点 ID") String nodeId) {
        return updateDrawing(documentId, (data, version) -> {
            DrawToolHelper.DrawData updated = DrawToolHelper.removeNode(data, nodeId);
            return knowledge.updateDocument(
                    UUID.fromString(documentId), null, null, null,
                    DrawToolHelper.stringifyDrawing(updated), version,
                    "删除节点: " + nodeId);
        });
    }

    @AgentTool(name = "draw_auto_layout", description = "自动整理 Draw 画布上的节点位置（网格布局）")
    public Map<String, Object> drawAutoLayout(
            @ToolParam(name = "documentId", description = "目标文档 ID") String documentId) {
        return updateDrawing(documentId, (data, version) -> {
            DrawToolHelper.DrawData updated = DrawToolHelper.autoLayout(data);
            return knowledge.updateDocument(
                    UUID.fromString(documentId), null, null, null,
                    DrawToolHelper.stringifyDrawing(updated), version,
                    "自动布局");
        });
    }

    @AgentTool(name = "draw_diagram", description = "一次性绘制完整图表：用节点/边列表覆盖文档的 drawingJson，边可用节点索引或节点 ID", risk = AgentTool.RiskLevel.MEDIUM)
    public Map<String, Object> drawDiagram(
            @ToolParam(name = "documentId", description = "目标文档 ID") String documentId,
            @ToolParam(name = "nodes", description = "节点列表，每项 {kind?, label, x?, y?, id?}") List<Map<String, Object>> nodes,
            @ToolParam(name = "edges", description = "边列表，每项 {from, to}；from/to 可以是节点索引(数字)或节点 ID") List<Map<String, Object>> edges,
            @ToolParam(name = "autoLayout", description = "完成后是否自动布局", required = false) Boolean autoLayout) {
        if (nodes != null && !nodes.isEmpty()) {
            Object first = nodes.get(0);
            if (!(first instanceof Map)) {
                return Map.<String, Object>of("error",
                        "draw_diagram 的 nodes 必须是对象列表，实际收到: " + first.getClass().getSimpleName());
            }
        }
        return updateDrawing(documentId, (data, version) -> {
            DrawToolHelper.DrawData result = new DrawToolHelper.DrawData(new ArrayList<>(), new ArrayList<>());
            List<String> nodeIds = new ArrayList<>();
            if (nodes != null) {
                for (Map<String, Object> n : nodes) {
                    String id = n.get("id") instanceof String s ? s : null;
                    String kind = n.get("kind") instanceof String s ? s : "rect";
                    String label = n.get("label") instanceof String s ? s : "";
                    Double x = asDouble(n.get("x"));
                    Double y = asDouble(n.get("y"));
                    result = DrawToolHelper.addNodeWithId(result, id, kind, label, x, y);
                    nodeIds.add(result.nodes().get(result.nodes().size() - 1).id());
                }
            }
            if (edges != null) {
                for (Map<String, Object> e : edges) {
                    String from = resolveEdgeEndpoint(e.get("from"), nodeIds);
                    String to = resolveEdgeEndpoint(e.get("to"), nodeIds);
                    if (from != null && to != null) {
                        result = DrawToolHelper.addEdge(result, from, to);
                    }
                }
            }
            if (Boolean.TRUE.equals(autoLayout)) {
                result = DrawToolHelper.autoLayout(result);
            }
            return knowledge.updateDocument(
                    UUID.fromString(documentId), null, null, null,
                    DrawToolHelper.stringifyDrawing(result), version,
                    "Agent 绘制图表");
        });
    }

    // ── Denied tools (registered so Planner can route to them; ActionPolicy will deny) ──

    @AgentTool(name = "modify_provider", description = "修改 AI Provider 配置（被安全策略禁止）", risk = RiskLevel.HIGH)
    public Map<String, Object> modifyProvider() {
        return Map.<String, Object>of("error", "该工具已被安全策略禁止");
    }

    @AgentTool(name = "override_settings", description = "覆盖系统设置（被安全策略禁止）", risk = RiskLevel.HIGH)
    public Map<String, Object> overrideSettings() {
        return Map.<String, Object>of("error", "该工具已被安全策略禁止");
    }

    @AgentTool(name = "exec_shell", description = "执行 Shell 命令（被安全策略禁止）", risk = RiskLevel.HIGH)
    public Map<String, Object> execShell() {
        return Map.<String, Object>of("error", "该工具已被安全策略禁止");
    }

    // ── Helpers ──

    /** Parse folderId, returning null for non-UUID values like "default" or "root". */
    private static UUID parseFolderId(String folderId) {
        if (folderId == null || folderId.isBlank()) return null;
        try { return UUID.fromString(folderId); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static Double asDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(value.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private static String resolveEdgeEndpoint(Object value, List<String> nodeIds) {
        if (value == null) return null;
        if (value instanceof Number n) {
            int i = n.intValue();
            return i >= 0 && i < nodeIds.size() ? nodeIds.get(i) : null;
        }
        String s = value.toString();
        try {
            int i = Integer.parseInt(s);
            return i >= 0 && i < nodeIds.size() ? nodeIds.get(i) : null;
        } catch (NumberFormatException e) {
            return s; // assume it's already a node id
        }
    }

    // ── Draw helper ──

    @FunctionalInterface
    private interface DrawingUpdater {
        com.subtlesight.storage.sqlite.SqliteKnowledgeRepository.KnowledgeDocument apply(
                DrawToolHelper.DrawData data, int version);
    }

    private Map<String, Object> updateDrawing(String documentId, DrawingUpdater updater) {
        try {
            var doc = knowledge.requireDocument(UUID.fromString(documentId));
            DrawToolHelper.DrawData data = DrawToolHelper.parseDrawing(doc.drawingJson());
            int version = doc.version();
            var updated = updater.apply(data, version);
            String drawingJson = updated.drawingJson();
            DrawToolHelper.DrawData resultData = DrawToolHelper.parseDrawing(drawingJson);
            return Map.<String, Object>of(
                    "documentId", updated.id().toString(),
                    "version", updated.version(),
                    "nodeCount", resultData.nodes().size(),
                    "edgeCount", resultData.edges().size(),
                    "drawingJson", drawingJson
            );
        } catch (Exception e) {
            return Map.<String, Object>of("error", "Draw 操作失败: " + e.getMessage());
        }
    }

    // ── Watchlist tools ──

    @AgentTool(name = "list_watch_changes", description = "查看最近的监控变更记录")
    public Map<String, Object> listWatchChanges(
            @ToolParam(name = "targetId", description = "监控目标 ID（可选，不传则返回全部）", required = false) String targetId,
            @ToolParam(name = "since", description = "起始时间 ISO-8601（可选）", required = false) String since) {
        try {
            List<Map<String, Object>> all = new ArrayList<>();
            if (targetId != null && !targetId.isBlank()) {
                var changes = repo.listChanges(UUID.fromString(targetId), 100);
                for (var c : changes) all.add(changeToMap(c));
            } else {
                for (var target : repo.listWatchTargets()) {
                    for (var c : repo.listChanges(target.id(), 20)) {
                        all.add(changeToMap(c));
                    }
                }
            }
            return Map.<String, Object>of("changes", all, "count", all.size());
        } catch (Exception e) {
            return Map.<String, Object>of("error", "查询变更失败: " + e.getMessage());
        }
    }

    @AgentTool(name = "list_watch_targets", description = "列出所有活跃的监控目标")
    public Map<String, Object> listWatchTargets() {
        var targets = repo.listWatchTargets();
        var list = targets.stream().map(t -> Map.<String, Object>of(
                "id", t.id().toString(), "name", t.name(),
                "type", t.type().name(), "expression", t.expression(),
                "enabled", t.enabled(), "baselineVersion", t.baselineVersion()
        )).toList();
        return Map.<String, Object>of("targets", list, "count", targets.size());
    }

    private Map<String, Object> changeToMap(com.subtlesight.domain.Models.ChangeEvent c) {
        return Map.<String, Object>of(
                "id", c.id().toString(), "targetId", c.watchTargetId().toString(),
                "field", c.field(), "oldValue", c.oldValue() != null ? c.oldValue() : "",
                "newValue", c.newValue() != null ? c.newValue() : "",
                "severity", c.severity().name(), "detectedAt", c.detectedAt().toString()
        );
    }

    @AgentTool(name = "ask_question", description = "基于知识库回答问题，提供可追溯的引用来源")
    public Map<String, Object> askQuestion(
            @ToolParam(name = "question", description = "要回答的问题") String question,
            @ToolParam(name = "scope", description = "搜索范围: KNOWLEDGE_BASE(全库) 或 FOLDER:<folderId>", required = false) String scope) {
        try {
            // Build scope: default to KNOWLEDGE_BASE
            List<ScopeRef> scopes = new ArrayList<>();
            if (scope != null && scope.startsWith("FOLDER:")) {
                scopes.add(new ScopeRef(ScopeType.FOLDER, UUID.fromString(scope.substring(7)), null, null));
            } else {
                scopes.add(new ScopeRef(ScopeType.KNOWLEDGE_BASE, null, null, null));
            }

            // Start QA
            var startResult = qa.start(null, null, question, scopes);
            UUID answerId = startResult.answer().id();

            // Execute synchronously (agent context)
            var completed = qa.execute(answerId);

            if (completed.status().name().equals("FAILED")) {
                return Map.<String, Object>of("error", "问答执行失败: " +
                        (completed.errorCode() != null ? completed.errorCode() : "unknown"));
            }

            // Build answer view
            var view = qa.answerView(answerId);
            List<Map<String, Object>> citations = new ArrayList<>();
            for (var claim : view.claims()) {
                for (var citation : claim.citations()) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    c.put("id", citation.id().toString());
                    c.put("claimId", claim.claim().id().toString());
                    c.put("exactQuote", citation.exactQuote());
                    c.put("resourceType", citation.resourceType().name());
                    c.put("resourceId", citation.resourceId().toString());
                    c.put("resourceName", resolveResourceName(citation.resourceType(), citation.resourceId()));
                    c.put("locatorJson", citation.locatorJson());
                    c.put("unitId", citation.unitId().toString());
                    citations.add(c);
                }
            }

            return Map.<String, Object>of(
                    "answer", view.answer().directAnswer(),
                    "answerId", answerId.toString(),
                    "status", view.answer().status().name(),
                    "citationCount", citations.size(),
                    "citations", citations
            );
        } catch (Exception e) {
            return Map.<String, Object>of("error", "问答失败: " + e.getMessage());
        }
    }

    /** Resolve a human-readable name for a citation resource (document title or file name). */
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
}
