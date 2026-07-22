package com.subtlesight.agent;

import com.subtlesight.domain.Models.AgentRequest;
import com.subtlesight.domain.Models.AgentResponse;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Controlled business-tool agent. No shell, arbitrary fetch or filesystem tool exists in this registry. */
public final class WebAgentService {
    private static final Set<String> TOOLS=Set.of("search_local","discover_web","get_story","create_saved_view","add_watch_target","start_research","get_research_status","create_report","request_publish","submit_feedback");
    private static final Set<String> HIGH_RISK=Set.of("request_publish","delete","modify_provider","override_settings");
    private final ToolExecutor executor;
    public WebAgentService(ToolExecutor executor){this.executor=executor;}
    public AgentResponse handle(AgentRequest request){if(isInjection(request.message()))return new AgentResponse("检测到网页指令污染；未执行任何工具。",List.of(),Map.of("blocked","PROMPT_INJECTION"),false);String tool=plan(request.message());if(tool==null)return new AgentResponse("我可以搜索本地情报、发现全网资料、创建视图、启动研究、建立跟踪或生成报告。",List.of(),Map.of(),false);if(!TOOLS.contains(tool))throw new SecurityException("unregistered tool");if(HIGH_RISK.contains(tool)&&!request.confirmed())return new AgentResponse("此操作需要你的明确确认。",List.of(tool),Map.of(),true);Map<String,Object> result=executor.execute(tool,request.message(),request.context());return new AgentResponse("操作已完成。",List.of(tool),result,false);}
    String plan(String message){String m=message.toLowerCase(Locale.ROOT);if(m.contains("发布")||m.contains("publish"))return "request_publish";if(m.contains("报告")||m.contains("report"))return "create_report";if(m.contains("跟踪")||m.contains("watch"))return "add_watch_target";if(m.contains("研究")||m.contains("research")||m.contains("核实"))return "start_research";if(m.contains("视图")||m.contains("feed"))return "create_saved_view";if(m.contains("全网")||m.contains("web"))return "discover_web";if(m.contains("隐藏")||m.contains("反馈")||m.contains("hide")||m.contains("feedback"))return "submit_feedback";if(m.contains("搜索")||m.contains("查找")||m.contains("search")||m.contains("find"))return "search_local";return null;}
    boolean isInjection(String m){String x=m.toLowerCase(Locale.ROOT);return x.contains("ignore previous instructions")||x.contains("忽略之前")||x.contains("system prompt")||x.contains("调用未注册工具")||x.contains("bypass tool policy");}
    @FunctionalInterface public interface ToolExecutor{Map<String,Object> execute(String tool,String message,Map<String,Object> context);}
}
