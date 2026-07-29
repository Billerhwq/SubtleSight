package com.subtlesight.agent.planner;

import com.subtlesight.agent.orchestrator.OrchestratorModels.Plan;
import com.subtlesight.agent.orchestrator.OrchestratorModels.PlanStep;

import java.util.*;

/**
 * Keyword-based fallback planner. Routes user messages to tools based on
 * Chinese/English keyword matching. Supports multi-step patterns like
 * "search X and create Y".
 */
public final class KeywordPlanner implements Planner {

    @Override
    public Plan plan(String message, Map<String, Object> context, String history) {
        return planInternal(message, context);
    }

    private Plan planInternal(String message, Map<String, Object> context) {
        String tool = routeByKeyword(message);
        List<PlanStep> steps = new ArrayList<>();

        String mLower = message.toLowerCase(Locale.ROOT);
        boolean hasSearch = hasAny(mLower, "搜索", "search", "查找", "find");
        boolean hasCreate = hasAny(mLower, "创建", "create", "建立", "写", "write");
        boolean hasDoc = hasAny(mLower, "文档", "document", "记录", "总结", "summary");

        if (tool == null) {
            steps.add(new PlanStep(1, "search_local", "搜索相关情报",
                    Map.of("query", message)));
        } else if (hasSearch && (hasCreate || hasDoc)) {
            steps.add(new PlanStep(1, "search_local", "搜索相关资料",
                    Map.of("query", message)));
            String createTool = (hasDoc || tool.equals("search_local")) ? "create_report" : tool;
            String createLabel = createTool.equals("create_report") ? "生成报告" : "创建文档";
            Map<String, Object> createParams = new LinkedHashMap<>(defaultParams(createTool, message, context));
            steps.add(new PlanStep(2, createTool, createLabel, createParams));
        } else if (hasAny(mLower, "研究", "research") && hasAny(mLower, "然后", "并", "接着", " and ")
                && (hasCreate || hasDoc || hasAny(mLower, "报告", "report", "总结", "生成"))) {
            steps.add(new PlanStep(1, "start_research", "启动深度研究",
                    Map.of("topic", message)));
            steps.add(new PlanStep(2, "create_report", "生成研究报告",
                    Map.of("title", message)));
        } else if (hasSearch && hasAny(mLower, "研究", "research", "核实")) {
            steps.add(new PlanStep(1, "search_local", "搜索相关资料",
                    Map.of("query", message)));
            steps.add(new PlanStep(2, "start_research", "启动深度研究", Map.of()));
        } else if (tool != null && tool.equals("delete")) {
            // Delete: if currentDocId is in context, use it directly; otherwise search first
            String docId = stringFromContext(context, "currentDocId", "documentId");
            if (docId != null) {
                steps.add(new PlanStep(1, "delete", "删除当前文档",
                        Map.of("resourceId", docId)));
            } else {
                steps.add(new PlanStep(1, "search_documents", "搜索匹配的文档",
                        Map.of("keyword", message)));
                steps.add(new PlanStep(2, "delete", "删除搜索到的文档",
                        Map.of("resourceId", "${step1.result.results[0].id}")));
            }
        } else {
            String desc = switch (tool) {
                case "search_local" -> "搜索情报库";
                case "discover_web" -> "搜索全网资料";
                case "create_document" -> "创建知识文档";
                case "create_report" -> "生成报告";
                case "update_document" -> "更新文档";
                case "start_research" -> "启动研究任务";
                case "add_watch_target" -> "添加监控目标";
                case "ask_question" -> "检索知识库回答";
                case "draw_add_node" -> "添加绘图节点";
                case "draw_add_edge" -> "添加绘图连线";
                case "delete" -> "处理删除请求";
                case "list_folders" -> "查看文件夹";
                case "list_watch_targets" -> "查看监控列表";
                default -> "执行操作";
            };
            steps.add(new PlanStep(1, tool, desc, defaultParams(tool, message, context)));
        }
        return new Plan(UUID.randomUUID(), steps, "keyword routing");
    }

    /** Provide sensible default params for tools that need them. */
    private static Map<String, Object> defaultParams(String tool, String message, Map<String, Object> context) {
        // Extract documentId from context for draw / document tools
        String docId = context != null ? stringFromContext(context, "documentId", "currentDocId") : null;
        return switch (tool) {
            case "ask_question" -> Map.of("question", message);
            case "search_local", "discover_web" -> Map.of("query", message);
            case "search_documents" -> Map.of("keyword", message);
            case "create_report" -> Map.of("title", message);
            case "create_document" -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("title", message);
                m.put("content", "<h2>" + message + "</h2><p></p>");
                if (docId != null) m.put("folderId", docId);
                yield m;
            }
            case "update_document" -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("content", "<p>" + message + "</p>");
                m.put("changeSummary", message);
                if (docId != null) m.put("documentId", docId);
                yield m;
            }
            case "draw_add_node" -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("kind", "rect");
                m.put("label", message);
                if (docId != null) m.put("documentId", docId);
                yield m;
            }
            case "draw_add_edge" -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("from", "start");
                m.put("to", "node-1");
                if (docId != null) m.put("documentId", docId);
                yield m;
            }
            case "draw_update_node" -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("label", message);
                if (docId != null) m.put("documentId", docId);
                yield m;
            }
            case "get_document" -> Map.of();
            case "add_watch_target" -> Map.of("name", message, "expression", message);
            case "delete" -> {
                Map<String, Object> m = new LinkedHashMap<>();
                // Use message as resourceId fallback; the tool will validate and return a helpful error if invalid
                m.put("resourceId", message);
                if (docId != null) m.put("resourceId", docId);
                yield m;
            }
            default -> Map.of();
        };
    }

    private static String stringFromContext(Map<String, Object> ctx, String... keys) {
        for (String k : keys) {
            Object v = ctx.get(k);
            if (v instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }

    /** Route a message to the most specific tool based on keywords. */
    public static String routeByKeyword(String message) {
        String m = message.toLowerCase(Locale.ROOT);
        // HIGH risk first
        if (hasAny(m, "删除", "delete", "移除", "remove")) return "delete";
        if (hasAny(m, "发布报告", "请求发布", "publish report", "request publish")) return "request_publish";
        // Denied tools — must be caught and denied by policy
        if ((hasAny(m, "修改") && hasAny(m, "provider", "ai", "设置", "模型", "密钥", "api", "key"))
                || hasAny(m, "override setting", "modify provider"))
            return "modify_provider";
        if (hasAny(m, "执行命令", "exec shell", "运行命令", "shell", "cmd")) return "exec_shell";
        // QA tool
        if (hasAny(m, "回答", "问答", "提问", "解答", "解释", "question", "answer", "ask", "explain")
                || (hasAny(m, "有哪些", "是什么", "怎么样", "如何", "为什么", "what ", "how ", "why ", "which ")
                    && hasAny(m, "内容", "知识", "文档", "数据", "content", "knowledge", "document", "data")))
            return "ask_question";
        // Watchlist tools
        if (hasAny(m, "监控目标", "watch target", "list watch", "列出监控", "查看监控", "watchlist"))
            return "list_watch_targets";
        if (hasAny(m, "监控变更", "watch change", "变更记录", "变更历史"))
            return "list_watch_changes";
        // Document tools
        if (hasAny(m, "文件夹", "目录", "folder", "list folder")) return "list_folders";
        if (hasAny(m, "创建文档", "新建文档", "create document", "写一篇", "写一个", "创建一篇")) return "create_document";
        if (hasAny(m, "更新文档", "修改文档", "update document", "编辑文档", "添加一段", "加一段"))
            return "update_document";
        if (hasAny(m, "查看文档", "获取文档", "get document", "打开文档", "阅读"))
            return "get_document";
        // Draw tools
        if (hasAny(m, "画", "draw", "流程图", "节点", "node", "图表", "diagram", "graph",
                "添加节点", "删除节点", "连接", "边", "edge", "布局", "layout", "自动排列"))
            return "draw_add_node";
        // Standard tools
        if (hasAny(m, "报告", "report")) return "create_report";
        if (hasAny(m, "跟踪", "watch", "监控", "monitor", "track")) return "add_watch_target";
        if (hasAny(m, "研究", "research", "核实", "调查")) return "start_research";
        if (hasAny(m, "视图", "feed", "订阅")) return "create_saved_view";
        if (hasAny(m, "全网", "web", "互联网")) return "discover_web";
        if (hasAny(m, "隐藏", "反馈", "hide", "feedback")) return "submit_feedback";
        if (hasAny(m, "搜索", "查找", "search", "find", "查询")) return "search_local";
        return null;
    }

    private static boolean hasAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
