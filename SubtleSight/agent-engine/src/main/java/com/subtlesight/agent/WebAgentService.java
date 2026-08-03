package com.subtlesight.agent;

import com.subtlesight.agent.orchestrator.AssistantOrchestrator;
import com.subtlesight.agent.orchestrator.OrchestratorModels.OrchestrationResult;
import com.subtlesight.application.AssistantPorts;
import com.subtlesight.domain.AssistantModels.Role;
import com.subtlesight.domain.AssistantModels.TurnStatus;
import com.subtlesight.domain.Models.AgentRequest;
import com.subtlesight.domain.Models.AgentResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/** Controlled business-tool agent. Delegates to {@link AssistantOrchestrator} when wired; falls back to keyword routing. */
public final class WebAgentService {
    private static final Set<String> TOOLS=Set.of("search_local","discover_web","get_story","create_saved_view","add_watch_target","start_research","get_research_status","create_report","request_publish","submit_feedback");
    private static final Set<String> HIGH_RISK=Set.of("request_publish","delete","modify_provider","override_settings");
    private final ToolExecutor executor;
    private final AssistantPorts.Repository assistantRepo;
    private final AssistantOrchestrator orchestrator;
    private final ExecutorService asyncExecutor;
    private final Map<UUID, Future<?>> runningTurns = new ConcurrentHashMap<>();

    /** Constructor for tests and simple usage (no persistence, no orchestrator). */
    public WebAgentService(ToolExecutor executor){this(executor,null,null, null);}

    /** Constructor with persistence but no orchestrator (M0.3 compat). */
    public WebAgentService(ToolExecutor executor, AssistantPorts.Repository assistantRepo){this(executor,assistantRepo,null, null);}

    /** Full constructor with orchestrator. */
    public WebAgentService(ToolExecutor executor, AssistantPorts.Repository assistantRepo, AssistantOrchestrator orchestrator){
        this(executor, assistantRepo, orchestrator, null);
    }

    /** Full constructor with orchestrator and async executor. */
    public WebAgentService(ToolExecutor executor, AssistantPorts.Repository assistantRepo,
                           AssistantOrchestrator orchestrator, ExecutorService asyncExecutor){
        this.executor=executor;this.assistantRepo=assistantRepo;this.orchestrator=orchestrator;
        this.asyncExecutor=asyncExecutor;
    }

    public AgentResponse handle(AgentRequest request){
        UUID sessionId=request.sessionId();
        String message = sanitizeHiddenChars(request.message());
        String contextJson=request.context()==null||request.context().isEmpty()?"{}":write(request.context());

        // Create turn record if repo is wired (unless turnId already provided — e.g. confirm flow)
        UUID turnId;
        if(request.turnId()!=null){
            turnId=request.turnId();
        }else if(assistantRepo!=null&&sessionId!=null){
            turnId=assistantRepo.appendTurn(sessionId,Role.USER,message,contextJson);
        }else{
            turnId=UUID.randomUUID();
        }

        // Check injection
        if(isInjection(message)){
            AgentResponse resp=new AgentResponse("检测到网页指令污染；未执行任何工具。",List.of(),Map.of("blocked","PROMPT_INJECTION"),false,turnId,sessionId);
            if(assistantRepo!=null&&sessionId!=null){
                assistantRepo.updateTurnStatus(turnId,TurnStatus.FAILED,null,write(List.of()));
                String reason = InjectionDetector.detectReason(request.message());
                assistantRepo.appendAudit(turnId,"inject_blocked","{\"reason\":\""+reason+"\"}");
            }
            return resp;
        }

        // ── Delegate to orchestrator if wired ──
        if(orchestrator!=null&&sessionId!=null){
            if(request.confirmed()){
                // Re-execute synchronously for confirmation flow (already paused)
                OrchestrationResult or = orchestrator.orchestrate(
                        turnId, sessionId, message, request.context(), true);
                List<String> tools = or.executions().stream().map(e->e.tool()).distinct().toList();
                return new AgentResponse(or.summary(),tools,resultMap(or),false,turnId,sessionId);
            }
            // Run orchestration asynchronously so the HTTP request returns immediately;
            // results are streamed via SSE events.
            final UUID fTurnId = turnId;
            final UUID fSessionId = sessionId;
            if(asyncExecutor!=null){
                final String fMessage = message;
                Future<?> future = asyncExecutor.submit(()->{
                    try {
                        orchestrator.orchestrate(fTurnId, fSessionId, fMessage, request.context(), false);
                    } finally {
                        runningTurns.remove(fTurnId);
                    }
                });
                runningTurns.put(fTurnId, future);
                if (future.isDone()) runningTurns.remove(fTurnId, future);
            }else{
                // No executor — run synchronously (fallback for tests)
                OrchestrationResult or = orchestrator.orchestrate(
                        turnId, sessionId, message, request.context(), false);
                List<String> tools = or.executions().stream().map(e->e.tool()).distinct().toList();
                return new AgentResponse(or.summary(),tools,resultMap(or),false,turnId,sessionId);
            }
            return new AgentResponse("正在分析您的请求…",List.of(),Map.of(),false,turnId,sessionId);
        }

        // ── Fallback: keyword routing (when orchestrator not wired) ──
        if(assistantRepo!=null&&sessionId!=null){
            assistantRepo.updateTurnStatus(turnId,TurnStatus.PLANNING,null,null);
        }

        String tool=plan(message);
        if(tool==null){
            AgentResponse resp=new AgentResponse("我可以搜索本地情报、发现全网资料、创建视图、启动研究、建立跟踪或生成报告。",List.of(),Map.of(),false,turnId,sessionId);
            if(assistantRepo!=null&&sessionId!=null){
                assistantRepo.updateTurnStatus(turnId,TurnStatus.COMPLETED,null,null);
            }
            return resp;
        }

        if(!TOOLS.contains(tool))throw new SecurityException("unregistered tool");
        if(HIGH_RISK.contains(tool)&&!request.confirmed()){
            AgentResponse resp=new AgentResponse("此操作需要你的明确确认。",List.of(tool),Map.of(),true,turnId,sessionId);
            if(assistantRepo!=null&&sessionId!=null){
                assistantRepo.updateTurnStatus(turnId,TurnStatus.AWAITING_CONFIRM,null,write(List.of(tool)));
                assistantRepo.appendAudit(turnId,"awaiting_confirm","{\"tool\":\""+tool+"\"}");
            }
            return resp;
        }

        if(assistantRepo!=null&&sessionId!=null){
            assistantRepo.updateTurnStatus(turnId,TurnStatus.EXECUTING,null,write(List.of(tool)));
        }

        Map<String,Object> result=executor.execute(tool,message,request.context());
        AgentResponse resp=new AgentResponse("操作已完成。",List.of(tool),result,false,turnId,sessionId);

        if(assistantRepo!=null&&sessionId!=null){
            assistantRepo.updateTurnStatus(turnId,TurnStatus.COMPLETED,null,write(List.of(tool)));
            assistantRepo.appendAudit(turnId,"tool_call","{\"tool\":\""+tool+"\",\"result\":\""+result.keySet().size()+" keys\"}");
        }
        return resp;
    }

    /** Best-effort cancellation for an asynchronously executing assistant turn. */
    public boolean cancel(UUID turnId) {
        Future<?> future = runningTurns.remove(turnId);
        boolean cancelled = future != null && future.cancel(true);
        if (assistantRepo != null) {
            assistantRepo.updateTurnStatus(turnId, TurnStatus.CANCELLED, null, null);
            assistantRepo.appendAudit(turnId, "cancelled", "{\"requested\":true}");
        }
        return cancelled;
    }

    String plan(String message){String m=message.toLowerCase(Locale.ROOT);if(m.contains("发布")||m.contains("publish"))return "request_publish";if(m.contains("报告")||m.contains("report"))return "create_report";if(m.contains("跟踪")||m.contains("watch"))return "add_watch_target";if(m.contains("研究")||m.contains("research")||m.contains("核实"))return "start_research";if(m.contains("视图")||m.contains("feed"))return "create_saved_view";if(m.contains("全网")||m.contains("web"))return "discover_web";if(m.contains("隐藏")||m.contains("反馈")||m.contains("hide")||m.contains("feedback"))return "submit_feedback";if(m.contains("搜索")||m.contains("查找")||m.contains("search")||m.contains("find"))return "search_local";return null;}

    boolean isInjection(String m){return InjectionDetector.isInjection(m);}

    /** Strip invisible/hidden Unicode characters that are often introduced by copy-paste. */
    static String sanitizeHiddenChars(String s) {
        if (s == null || s.isBlank()) return s;
        return s.replaceAll("[\\u200B-\\u200F\\u2028-\\u202F\\u2060-\\u206F\\uFEFF\\u200E\\u200F\\u061C]", "");
    }

    @FunctionalInterface public interface ToolExecutor{Map<String,Object> execute(String tool,String message,Map<String,Object> context);}

    /** Build the sync-path result map including structured references. */
    private static Map<String, Object> resultMap(OrchestrationResult or) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executions", or.executions().size());
        result.put("references", or.references().stream().map(r -> Map.<String, Object>of(
                "index", r.index(), "resourceId", r.resourceId(), "resourceType", r.resourceType(),
                "resourceName", r.resourceName(), "url", r.url(), "publishedAt", r.publishedAt(),
                "summary", r.summary(), "locator", r.locator(), "exactQuote", r.exactQuote())).toList());
        return result;
    }

    private static String write(Object value){
        if(value==null)return null;
        try{return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(value);}
        catch(Exception e){return "\"serialization_error\"";}
    }
}
