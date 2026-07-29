package com.subtlesight.agent.planner;

import com.subtlesight.agent.tools.ToolRegistry;
import com.subtlesight.agent.tools.ToolModels.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Builds LLM prompts for plan generation, embedding live tool schemas
 * from the {@link ToolRegistry} so the model always sees the current tool set.
 */
public final class PlanPromptBuilder {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            你是 SubtleSight AI 助手的计划生成器。
            根据用户消息生成一个工具执行计划。

            核心原则：
            - 你会收到复合任务（如"搜索X，然后创建文档、画图"），需要将它们分解为有序步骤
            - 每一步使用下面列出的一个工具，参数必须符合 JSON Schema
            - 步骤数没有硬性上限，但通常 1-5 步即可，复合任务可达 10 步
            - 步骤的 description 用自然语言描述这一步要做什么（面向用户），不要用技术术语
            - 后一步可以引用前一步的输出，语法：${stepN.result.field}
              例：create_document 返回 id，后续 draw 步骤用 "documentId": "${step1.result.id}"
              draw_add_node 返回 nodeId，后续 draw_add_edge 可用 "from": "${step2.result.nodeId}"

            工具选择指南：
            - search_local：搜索已采集的情报、新闻、故事（外部互联网内容），不搜索用户创建的文档
            - search_documents：搜索知识库中用户创建的文档和文件（本地内容），返回包含 id 字段的结果
            - 两者的搜索范围完全不同，不要混用

            任务模式映射：
            - "搜索X" → search_local（搜索情报库）
            - "搜索X并创建文档" → 先 search_local 再 create_document（两步）
            - "删除/移除文档X" → 第一步必须用 search_documents(keyword=从用户消息中提取的文档关键词) 找到匹配文档，第二步用 delete(resourceId=${step1.result.results[0].id})
            - "删除文件X" → 同上，先 search_documents 再 delete
            - 更新或打开知识库中的文档 → 先用 search_documents 找到文档 ID
            - 任何涉及用户文档/文件的操作都必须先用 search_documents 定位
            - "研究X并生成报告" → 先 start_research 再 create_report
            - "画完整流程图/路线图/技术架构图" → 优先用 draw_diagram：一次性传入 nodes + edges，边用节点索引
              例：{"tool": "draw_diagram", "params": {"documentId": "${step1.result.id}", "nodes": [...], "edges": [{"from": 0, "to": 1}], "autoLayout": true}}
            - "只添加一个节点" → draw_add_node
            - "只连接两个已有节点" → draw_add_edge（必须知道真实 nodeId）
            - "监控X" → add_watch_target
            - 知识问答 → ask_question
            - 意图不明确 → search_local(query=用户原文)

            高风险工具（标记 [高风险]）只在用户明确要求时才使用。
            只输出 JSON，不要 markdown 代码块，不要额外解释。
            """;

    private final ToolRegistry registry;

    public PlanPromptBuilder(ToolRegistry registry) {
        this.registry = registry;
    }

    /** Build the system prompt with live tool schemas. */
    public String buildSystemPrompt() {
        StringBuilder sb = new StringBuilder(SYSTEM_PROMPT);
        sb.append("\n\n可用工具（JSON Schema）:\n```json\n");
        List<Map<String, Object>> schemas = registry.functionSchemas();
        try {
            sb.append(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(schemas));
        } catch (Exception e) {
            // fallback: compact list
            for (ToolDefinition def : registry.all()) {
                sb.append("- ").append(def.name()).append(": ").append(def.description());
                if (def.risk().name().equals("HIGH")) sb.append(" [高风险]");
                sb.append("\n");
            }
        }
        sb.append("\n```");
        return sb.toString();
    }

    /** Build the user prompt with message, context, and history. */
    public String buildUserPrompt(String message, Map<String, Object> context, String historyJson) {
        StringBuilder sb = new StringBuilder();
        sb.append("用户消息: ").append(message).append("\n");
        if (context != null && !context.isEmpty()) {
            sb.append("当前上下文: ");
            try { sb.append(JSON.writeValueAsString(context)); }
            catch (Exception e) { sb.append(context); }
            sb.append("\n");
        }
        if (historyJson != null && !historyJson.isEmpty() && !historyJson.equals("[]")) {
            sb.append("对话历史: ").append(historyJson).append("\n");
        }
        sb.append("\n请输出 JSON 执行计划。");
        return sb.toString();
    }

    /** Build a tool-friendly JSON output format specification. */
    public static String outputFormat() {
        return """
                {
                  "rationale": "规划理由（一句话）",
                  "steps": [
                    {
                      "tool": "工具名称",
                      "description": "这一步做什么（中文）",
                      "params": { "参数名": "参数值" }
                    }
                  ]
                }""";
    }
}
