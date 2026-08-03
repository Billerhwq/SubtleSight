import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Button, SideSheet, Spin, TextArea, Toast, ButtonGroup } from '@douyinfe/semi-ui';
import { IconSend, IconStop, IconAlertTriangle, IconSearch, IconGlobe, IconArticle, IconEdit, IconPlus, IconHelpCircle, IconTreeTriangleDown, IconDelete, IconFolderStroked, IconList, IconSave, IconRefresh } from '@douyinfe/semi-icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useUi } from '../store/ui';
import { postTurn, subscribeTurnEvents, confirmTurn, cancelTurn, listSessions, updateSession, deleteSession, forkSession, listTurns } from '../api/agent';
import { QaPanel } from './QaPanel';
import { AgentRunTimeline } from './AgentRunTimeline';
import { applyAssistantRunEvent, createAssistantRun } from './agentRunModel';
import type { AssistantRun, AssistantRunEvent, RunLink, RunStepRecord } from './agentRunModel';
import { buildCitationMap, CitationMarker, CitationTemplateHint, handleCitationNav } from './citations';
import type { CitationMeta, Reference } from './citations';
import type { AssistantSession } from '../types';

// ── State machine ──
type DrawerState =
  | { kind: 'idle' }
  | { kind: 'thinking'; turnId?: string }
  | { kind: 'awaiting_confirm'; turnId: string; tools: string[]; message: string }
  | { kind: 'streaming'; turnId: string; chunk?: string }
  | { kind: 'done' }
  | { kind: 'error'; message: string };

type ResLink = RunLink;
type Message = { role: 'user' | 'agent'; text: string; tools?: string[]; confirmationRequired?: boolean; turnId?: string; kind?: 'thinking' | 'result'; links?: ResLink[]; citationMap?: Record<number, CitationMeta>; references?: Reference[]; streaming?: boolean };

// ── Derive page context from current route ──
function usePageContext(): Record<string, unknown> {
  const { pathname, search } = useLocation();
  const ctx: Record<string, unknown> = { page: pathname };
  const seg = pathname.split('/').filter(Boolean);
  if (seg[0] === 'stories' && seg[1]) ctx.currentStoryId = seg[1];
  if (seg[0] === 'research' && seg[1]) ctx.currentResearchId = seg[1];
  if (seg[0] === 'knowledge') {
    ctx.currentPage = 'knowledge';
    // Read currentDocId from URL params (set by KnowledgeEditorPage)
    const sp = new URLSearchParams(search);
    const docId = sp.get('documentId');
    if (docId) ctx.currentDocId = docId;
  }
  if (seg[0] === 'discover') ctx.currentView = 'discover';
  return ctx;
}

const WELCOME: Message = { role: 'agent', text: '告诉我你想追踪什么、核实什么，或要建立怎样的发现视图。', kind: 'result' };

/** Extract a short title from a step's result text for collapsed display. */
function extractStepTitle(text: string): string {
  // Take the first meaningful line, strip markdown, limit to 60 chars
  const firstLine = text.split('\n')[0].replace(/[*_`#]/g, '').trim();
  return firstLine.length > 60 ? firstLine.slice(0, 60) + '…' : firstLine || '执行完成';
}

/** Render text with clickable [N] citation markers (hover card + click-to-jump). */
function renderTextWithCitations(text: string, citationMap?: Record<number, CitationMeta>, navigate?: (url: string | null, meta: CitationMeta) => void): ReactNode {
  if (!citationMap || Object.keys(citationMap).length === 0) {
    return text;
  }
  const parts: ReactNode[] = [];
  let lastIndex = 0;
  const regex = /\[(\d+)\]/g;
  let match: RegExpExecArray | null;
  let citeIdx = 0;
  while ((match = regex.exec(text)) !== null) {
    const num = parseInt(match[1], 10);
    const meta = citationMap[num];
    if (match.index > lastIndex) {
      parts.push(text.slice(lastIndex, match.index));
    }
    if (meta) {
      citeIdx++;
      parts.push(
        <CitationMarker
          key={`cite-${num}-${citeIdx}`}
          num={match[1]}
          meta={meta}
          onNavigate={navigate}
        />
      );
    } else {
      parts.push(`[${match[1]}]`);
    }
    lastIndex = match.index + match[0].length;
  }
  if (lastIndex < text.length) {
    parts.push(text.slice(lastIndex));
  }
  return parts.length > 0 ? parts : text;
}

/** Build structured, highlightable records from a search/qa tool result. */
function buildRecords(toolName: string, result: Record<string, unknown>): RunStepRecord[] {
  const r = result as any;
  if (toolName === 'search_local') {
    const hits = r.hits as any[] | undefined;
    if (!hits) return [];
    const citations = r.citationMap as Record<number, CitationMeta> | undefined;
    return hits.map((h: any, i: number) => ({
      resourceId: h.id,
      label: h.title ?? '无标题',
      detail: h.snippet,
      url: citations?.[i + 1]?.url,
    }));
  }
  if (toolName === 'search_documents') {
    const results = r.results as any[] | undefined;
    if (!results) return [];
    return results.map((item: any) => ({
      resourceId: item.id,
      label: item.title ?? item.name ?? '无名称',
      detail: item.type === 'folder' ? undefined : item.path,
    }));
  }
  if (toolName === 'ask_question') {
    const citations = r.citations as any[] | undefined;
    if (!citations) return [];
    return citations.map((c: any) => ({
      resourceId: c.resourceId,
      label: c.resourceName ?? `${c.resourceType}/${String(c.resourceId ?? '').substring(0, 8)}`,
      detail: c.exactQuote,
    }));
  }
  return [];
}

/** Fetch and display context snapshot for a turn. */
function ContextSnapshot({ sessionId, turnId }: { sessionId: string; turnId: string }): ReactNode {
  const [data, setData] = useState<any>(null);
  useEffect(() => {
    import('../api/client').then(({ get }) =>
      get<any[]>(`/agent/sessions/${sessionId}/turns?limit=500`)
    ).then(turns => {
      const turn = turns.find((t: any) => t.id === turnId);
      setData(turn || null);
    }).catch(() => setData(null));
  }, [sessionId, turnId]);
  if (!data) return <Spin size="small" />;
  return (
    <div className="context-snapshot-inner">
      <div className="context-snapshot-hint">此回答使用的上下文快照</div>
      {data.planJson && (
        <details>
          <summary>执行计划</summary>
          <pre className="context-json">{(() => { try { return JSON.stringify(JSON.parse(data.planJson), null, 2); } catch { return data.planJson; } })()}</pre>
        </details>
      )}
      {data.contextJson && (
        <details>
          <summary>页面上下文</summary>
          <pre className="context-json">{(() => { try { return JSON.stringify(JSON.parse(data.contextJson), null, 2); } catch { return data.contextJson; } })()}</pre>
        </details>
      )}
      {!data.planJson && !data.contextJson && (
        <span className="context-snapshot-empty">暂无上下文快照数据</span>
      )}
    </div>
  );
}

export function AgentDrawer(): ReactNode {
  const { agentOpen, setAgentOpen, agentContext } = useUi();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<'chat' | 'qa'>('chat');
  const [text, setText] = useState('');
  const [state, setState] = useState<DrawerState>({ kind: 'idle' });
  const [messages, setMessages] = useState<Message[]>([WELCOME]);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [sessions, setSessions] = useState<AssistantSession[]>([]);
  const [sessionsLoading, setSessionsLoading] = useState(false);
  const [editingSessionId, setEditingSessionId] = useState<string | null>(null);
  const [editTitle, setEditTitle] = useState('');
  const [showSessionList, setShowSessionList] = useState(false);
  const [expandedContextTurn, setExpandedContextTurn] = useState<string | null>(null);
  const [runs, setRuns] = useState<Record<string, AssistantRun>>({});
  const sseRef = useRef<EventSource | null>(null);
  const pageContext = usePageContext();
  const context = { ...pageContext, ...agentContext };
  const busy = state.kind === 'thinking' || state.kind === 'streaming';
  // Show a citation-style preview when the last agent reply asks the user for a source link.
  const lastAgentMessage = [...messages].reverse().find(m => m.role === 'agent');
  const showCitationTemplateHint = !!lastAgentMessage
    && !(lastAgentMessage.references && lastAgentMessage.references.length > 0)
    && /提供.{0,8}(链接|url)|资料链接|source url|请提供.{0,8}(链接|url)/i.test(lastAgentMessage.text);

  // Load sessions on mount
  const loadSessions = useCallback(async () => {
    setSessionsLoading(true);
    try { setSessions(await listSessions(30)); } catch { /* ignore */ }
    setSessionsLoading(false);
  }, []);
  useEffect(() => { if (agentOpen) loadSessions(); }, [agentOpen, loadSessions]);
  // Refresh sessions when sessionId changes (new session created)
  useEffect(() => { if (sessionId && agentOpen) { const t = setTimeout(loadSessions, 500); return () => clearTimeout(t); } }, [sessionId]);

  // Clean up SSE on unmount
  useEffect(() => () => sseRef.current?.close(), []);

  const closeSse = useCallback(() => {
    const es = sseRef.current;
    if (es) { es.close(); sseRef.current = null; }
  }, []);

  const updateRun = useCallback((turnId: string, event: AssistantRunEvent) => {
    setRuns(previous => {
      const current = previous[turnId];
      if (!current) return previous;
      return { ...previous, [turnId]: applyAssistantRunEvent(current, event) };
    });
  }, []);

  /** Subscribe to SSE and handle all standardized event types. */
  const subscribeTurn = useCallback((turnId: string) => {
    closeSse();
    const es = subscribeTurnEvents(turnId);
    sseRef.current = es;

    const onEvent = (type: string, handler: (data: Record<string, unknown>) => void) => {
      es.addEventListener(type, (e: MessageEvent) => {
        try { handler(JSON.parse(e.data)); } catch { /* ignore malformed */ }
      });
    };

    // planning_started → show thinking
    onEvent('planning_started', () => {
      updateRun(turnId, { type: 'planning_started' });
      setState({ kind: 'thinking', turnId });
    });

    // plan_created → show plan summary in chat
    onEvent('plan_created', (data) => {
      const rationale = data.rationale as string ?? '';
      const stepList = Array.isArray(data.stepList) ? data.stepList : [];
      updateRun(turnId, {
        type: 'plan_created',
        rationale,
        steps: stepList.map((item, index) => {
          const step = item as Record<string, unknown>;
          return {
            ordinal: Number(step.ordinal ?? index + 1),
            toolName: String(step.toolName ?? step.tool ?? ''),
            toolId: step.toolId ? String(step.toolId) : undefined,
            toolVersion: step.toolVersion ? String(step.toolVersion) : undefined,
            label: step.label ? String(step.label) : undefined,
            description: String(step.description ?? ''),
          };
        }),
      });
    });

    // step_started → create a placeholder thinking step for streaming
    onEvent('step_started', (data) => {
      const ordinal = data.ordinal as number ?? 0;
      const toolName = data.toolName as string ?? '';
      const description = data.description as string ?? '';
      updateRun(turnId, {
        type: 'step_started', ordinal, toolName, description,
        toolId: data.toolId ? String(data.toolId) : undefined,
        toolVersion: data.toolVersion ? String(data.toolVersion) : undefined,
        callId: data.callId ? String(data.callId) : undefined,
        label: data.label ? String(data.label) : undefined,
      });
      setState({ kind: 'thinking', turnId });
    });

    // tool_progress → compact live line; full history remains expandable.
    onEvent('tool_progress', (data) => {
      const summary = String(data.summary ?? '正在执行工具');
      updateRun(turnId, {
        type: 'tool_progress',
        ordinal: Number(data.ordinal ?? 0),
        toolName: String(data.toolName ?? ''),
        toolId: data.toolId ? String(data.toolId) : undefined,
        toolVersion: data.toolVersion ? String(data.toolVersion) : undefined,
        callId: data.callId ? String(data.callId) : undefined,
        phase: String(data.phase ?? 'running'),
        summary,
        detail: data.detail ? String(data.detail) : undefined,
      });
    });

    // step_chunk → append incremental content to the last thinking step
    onEvent('step_chunk', (data) => {
      const chunk = data.chunk as string ?? '';
      if (!chunk) return;
      const ordinal = Number(data.ordinal ?? 0);
      updateRun(turnId, { type: 'step_chunk', ordinal, chunk });
    });

    // step_completed → replace the placeholder with final formatted result
    onEvent('step_completed', (data) => {
      const toolResult = data.toolResult && typeof data.toolResult === 'object'
        ? data.toolResult as Record<string, unknown> : undefined;
      const status = toolResult ? String(toolResult.status ?? '') : '';
      const success = status ? status === 'succeeded' : data.success !== false;
      const toolName = data.toolName as string ?? '';
      const protocolData = toolResult?.data;
      const result = protocolData && typeof protocolData === 'object'
        ? protocolData as Record<string, unknown>
        : (data.result ?? {}) as Record<string, unknown>;
      const effects = Array.isArray(toolResult?.effects)
        ? toolResult.effects.map(item => {
          const effect = item as Record<string, unknown>;
          return {
            type: String(effect.type ?? ''),
            verified: effect.verified === true,
            resource: effect.resource && typeof effect.resource === 'object'
              ? effect.resource as { type?: string; id?: string } : undefined,
          };
        }) : undefined;
      const { text: resultText, links: resultLinks } = formatToolResult(toolName, success, result);
      const citationMap = (result.citationMap as Record<number, CitationMeta>) ?? undefined;
      const records = buildRecords(toolName, result);
      const ordinal = Number(data.ordinal ?? 0);
      updateRun(turnId, {
        type: 'step_completed', ordinal, toolName, success,
        toolId: toolResult?.toolId ? String(toolResult.toolId) : data.toolId ? String(data.toolId) : undefined,
        toolVersion: toolResult?.toolVersion ? String(toolResult.toolVersion) : data.toolVersion ? String(data.toolVersion) : undefined,
        callId: toolResult?.callId ? String(toolResult.callId) : data.callId ? String(data.callId) : undefined,
        summary: resultText || String(toolResult?.content ?? ''),
        links: resultLinks,
        records,
        citationMap,
        effects,
      });
      const hasVerifiedResourceEffect = effects?.some(effect => effect.verified && effect.type.startsWith('resource.'));
      if (success && (hasVerifiedResourceEffect || toolName.startsWith('draw_') || toolName.startsWith('create_') || toolName.startsWith('update_'))) {
        queryClient.invalidateQueries({ queryKey: ['knowledge-documents'] });
        queryClient.invalidateQueries({ queryKey: ['knowledge-files'] });
        queryClient.invalidateQueries({ queryKey: ['knowledge-files-all'] });
      }
      if (success && (toolName === 'update_document' || toolName.startsWith('draw_'))) {
        const documentId = result.documentId ?? result.id;
        if (typeof documentId === 'string' && documentId) {
          window.dispatchEvent(new CustomEvent('subtlesight:document-refresh', {
            detail: { documentId },
          }));
        }
      }
    });

    // confirmation_required → show confirm UI
    onEvent('confirmation_required', (data) => {
      const toolName = data.toolName as string ?? '';
      const desc = data.description as string ?? '';
      updateRun(turnId, { type: 'confirmation_required', toolName, description: desc });
      setState({ kind: 'awaiting_confirm', turnId, tools: [toolName], message: desc });
    });

    // streaming_chunk → synthesis text — replace the last agent message.
    // Note: deliberately NOT fed into the run model — the answer must not leak into
    // the execution-details activity panel (would duplicate the result bubble).
    onEvent('streaming_chunk', (data) => {
      const chunk = data.chunk as string ?? '';
      if (!chunk) return;
      setState({ kind: 'streaming', turnId, chunk });
      setMessages(prev => {
        const copy = [...prev];
        for (let i = copy.length - 1; i >= 0; i--) {
          if (copy[i].role === 'agent' && copy[i].turnId === turnId) {
            copy[i] = { ...copy[i], text: chunk, kind: 'result', streaming: true };
            return copy;
          }
        }
        // No existing result — insert right after this turn's user message so replaying
        // an older turn's events never pushes its answer after newer turns.
        const userIdx = copy.findIndex(m => m.role === 'user' && m.turnId === turnId);
        const newResult: Message = { role: 'agent', text: chunk, turnId, kind: 'result', streaming: true };
        if (userIdx >= 0) {
          copy.splice(userIdx + 1, 0, newResult);
          return copy;
        }
        return [...copy, newResult];
      });
    });

    // turn_completed → done, replace the streamed text with the anchored answer + references
    onEvent('turn_completed', (data) => {
      const answer = data.answer ? String(data.answer) : undefined;
      const references = Array.isArray(data.references)
        ? data.references as Reference[]
        : undefined;
      setMessages(prev => prev.map(m => {
        // Only rewrite the agent result message — never the user's own prompt.
        if (m.turnId !== turnId || m.role !== 'agent') return m;
        const next: Message = { ...m, streaming: false };
        if (answer) next.text = answer;
        if (references) next.references = references;
        return next;
      }));
      updateRun(turnId, {
        type: 'turn_completed',
        allSuccess: data.allSuccess !== false,
        answer,
        references,
      });
      setState({ kind: 'done' });
      closeSse();
    });

    onEvent('turn_cancelled', (data) => {
      updateRun(turnId, { type: 'turn_cancelled', message: String(data.message ?? '任务已停止') });
      setState({ kind: 'done' });
      closeSse();
    });

    // turn_result (backward compat) — only show if it adds real content
    onEvent('turn_result', (data) => {
      setMessages(prev => {
        const msg = data.message as string ?? '';
        const tools = data.tools as string[] ?? [];
        // Skip redundant "N 个步骤已执行" — step_completed already shows details
        if (!msg || msg === '正在分析您的请求…' || (typeof msg === 'string' && /^\d+\s*个步骤已执行$/.test(msg))) return prev;
        const exists = prev.some(m => m.turnId === data.turnId && m.text === msg);
        if (exists) return prev;
        return [...prev, { role: 'agent', text: msg, tools, turnId: data.turnId as string }];
      });
    });

    // confirmation_expired — reset to idle gracefully
    onEvent('confirmation_expired', () => {
      setState({ kind: 'idle' });
    });

    // error
    onEvent('error', (data) => {
      const message = data.message as string ?? '未知错误';
      updateRun(turnId, { type: 'error', message });
      setState({ kind: 'error', message });
    });

    es.onerror = () => { es.close(); sseRef.current = null; };
  }, [closeSse, queryClient, updateRun]);

  /** Format tool execution results — returns { text, links }. */
  const formatToolResult = (toolName: string, success: boolean, result: Record<string, unknown>): { text: string; links: ResLink[] } => {
    const noLinks: ResLink[] = [];
    if (!success) {
      const err = (result?.error ?? '') as string;
      return { text: err ? `${toolName} 执行失败：${err}` : `${toolName} 执行失败`, links: noLinks };
    }
    const r = result as any;

    switch (toolName) {
      case 'search_local': {
        const hits = r.hits as any[] | undefined;
        const citations = r.citationMap as Record<number, CitationMeta> | undefined;
        if (!hits || hits.length === 0) return { text: '搜索完成，未找到匹配结果。', links: noLinks };
        const items = hits.slice(0, 5).map((h: any) =>
          `- **${h.title ?? '无标题'}**：${(h.snippet ?? '').slice(0, 120)}`
        ).join('\n');
        let text = `搜索到 ${hits.length} 条结果：\n${items}`;
        if (citations && Object.keys(citations).length > 0) {
          const refs = Object.entries(citations).slice(0, 5).map(([n, c]) =>
            `[${n}] ${c.resourceName}`
          ).join('\n');
          text += `\n\n📎 引用来源 (${Object.keys(citations).length})：\n${refs}`;
        }
        return { text, links: noLinks };
      }
      case 'search_documents': {
        const results = r.results as any[] | undefined;
        const citations = r.citationMap as Record<number, CitationMeta> | undefined;
        if (!results || results.length === 0) return { text: '搜索完成，未找到匹配的文档或文件。', links: noLinks };
        const items = results.slice(0, 5).map((item: any) => {
          const name = item.title ?? item.name ?? '无名称';
          const typeLabel = item.type === 'folder' ? '📁' : item.type === 'file' ? '📄' : '📝';
          return `- ${typeLabel} **${name}**`;
        }).join('\n');
        let text = `搜索到 ${results.length} 条结果：\n${items}`;
        if (citations && Object.keys(citations).length > 0) {
          const refs = Object.entries(citations).slice(0, 5).map(([n, c]) =>
            `[${n}] ${c.resourceName}`
          ).join('\n');
          text += `\n\n📎 引用来源 (${Object.keys(citations).length})：\n${refs}`;
        }
        return { text, links: noLinks };
      }
      case 'ask_question': {
        const answer = r.answer as string | undefined;
        const citations = r.citations as any[] | undefined;
        if (answer) {
          let text = answer;
          if (citations && citations.length > 0) {
            const refs = citations.slice(0, 5).map((c: any, i: number) => {
              const name = c.resourceName ?? `${c.resourceType}/${String(c.resourceId ?? '').substring(0, 8)}`;
              return `[${i + 1}] ${name}`;
            }).join('\n');
            text += `\n\n📎 引用来源 (${citations.length})：\n${refs}`;
          } else {
            const count = r.citationCount as number | undefined;
            if (count) text += `\n\n📎 ${count} 条引用来源`;
          }
          return { text, links: noLinks };
        }
        return { text: '问答完成。', links: noLinks };
      }
      case 'create_document': {
        const title = r.title as string ?? '';
        const id = r.id as string ?? '';
        const links: ResLink[] = id ? [{ label: `📄 ${title || '新文档'}`, documentId: id }] : [];
        return { text: title ? `已创建文档` : '文档创建完成。', links };
      }
      case 'create_report': {
        const title = r.title as string ?? '';
        const id = r.reportId as string ?? r.id as string ?? '';
        const links: ResLink[] = id ? [{ label: `📊 ${title || '新报告'}`, documentId: id }] : [];
        return { text: title ? `已创建报告` : '报告创建完成。', links };
      }
      case 'update_document': {
        const ver = r.version as number | undefined;
        const id = r.documentId as string ?? r.id as string ?? '';
        const links: ResLink[] = id ? [{ label: '📝 打开文档', documentId: id }] : [];
        return { text: ver !== undefined ? `文档已更新至 V${ver}` : '文档已更新。', links };
      }
      case 'start_research': {
        const topic = r.question as string ?? r.topic as string ?? '';
        return { text: topic ? `已启动研究：**${topic}**` : '研究任务已启动。', links: noLinks };
      }
      case 'add_watch_target': {
        const name = r.name as string ?? '';
        return { text: name ? `已添加监控目标：**${name}**` : '监控目标已添加。', links: noLinks };
      }
      case 'draw_add_node': {
        const nodes = r.nodeCount as number | undefined;
        const docId = r.documentId as string ?? '';
        const links: ResLink[] = docId ? [{ label: '🎨 查看画布', documentId: docId }] : [];
        return { text: nodes !== undefined ? `已添加节点（共 ${nodes} 个节点）` : '节点已添加。', links };
      }
      case 'draw_add_edge': {
        const edges = r.edgeCount as number | undefined;
        const docId = r.documentId as string ?? '';
        const links: ResLink[] = docId ? [{ label: '🎨 查看画布', documentId: docId }] : [];
        return { text: edges !== undefined ? `已添加边（共 ${edges} 条边）` : '边已添加。', links };
      }
      case 'list_folders':
      case 'list_watch_targets': {
        const count = r.count as number | undefined;
        return { text: count !== undefined ? `查询到 ${count} 条记录。` : '查询完成。', links: noLinks };
      }
      default:
        return { text: `${toolName} 完成。`, links: noLinks };
    }
  };
  const send = async () => {
    if (!text.trim() || busy) return;
    const current = text.trim();
    lastMessageRef.current = current;
    setMessages(v => [...v, { role: 'user', text: current }]);
    setText('');
    setState({ kind: 'thinking' });

    try {
      const result = await postTurn({ message: current, context, sessionId: sessionId ?? undefined });
      if (result.sessionId && !sessionId) setSessionId(result.sessionId);
      setRuns(previous => ({ ...previous, [result.turnId]: createAssistantRun(result.turnId, current) }));
      setMessages(previous => {
        const copy = [...previous];
        for (let index = copy.length - 1; index >= 0; index -= 1) {
          if (copy[index].role === 'user' && !copy[index].turnId) {
            copy[index] = { ...copy[index], turnId: result.turnId };
            break;
          }
        }
        return copy;
      });

      if (result.confirmationRequired) {
        updateRun(result.turnId, {
          type: 'confirmation_required',
          toolName: result.tools?.[0] ?? '',
          description: result.message,
        });
        setState({ kind: 'awaiting_confirm', turnId: result.turnId, tools: result.tools, message: result.message });
      } else {
        setState({ kind: 'thinking', turnId: result.turnId });
        subscribeTurn(result.turnId);
      }
    } catch (e) {
      setState({ kind: 'error', message: (e as Error).message });
    }
  };

  /** Handle confirm/cancel for high-risk actions. */
  const handleConfirm = async (approved: boolean) => {
    if (state.kind !== 'awaiting_confirm') return;
    const { turnId } = state;
    if (!approved) {
      setMessages(v => [...v, { role: 'agent', text: '操作已取消。', turnId }]);
      setState({ kind: 'idle' });
      return;
    }
    setState({ kind: 'thinking', turnId });
    // Subscribe BEFORE confirming so we catch the SSE event
    subscribeTurn(turnId);
    try {
      await confirmTurn(turnId, true);
      // SSE will deliver the result; fallback timeout to done
      setTimeout(() => { if (sseRef.current) { setState({ kind: 'done' }); } }, 5000);
    } catch (e) {
      setState({ kind: 'error', message: (e as Error).message });
    }
  };

  /** Retry after error — back to idle. */
  const lastMessageRef = useRef('');
  const retry = () => {
    const msg = lastMessageRef.current;
    if (msg) {
      setState({ kind: 'idle' });
      setText(msg);
      setTimeout(() => send(), 50);
    } else {
      setState({ kind: 'idle' });
    }
  };

  // ── Session management ──
  const newSession = () => { closeSse(); setSessionId(null); setMessages([WELCOME]); setRuns({}); setState({ kind: 'idle' }); setShowSessionList(false); };

  const switchSession = async (sid: string) => {
    closeSse(); setSessionId(sid); setShowSessionList(false); setRuns({}); setState({ kind: 'idle' });
    try {
      const turns = await listTurns(sid);
      const msgs: Message[] = [WELCOME];
      for (const t of turns) {
        if (t.role === 'USER') {
          msgs.push({ role: 'user', text: t.content, turnId: t.id });
        } else {
          msgs.push({ role: 'agent', text: t.content, turnId: t.id, kind: 'result' });
        }
      }
      setMessages(msgs);
    } catch { setMessages([WELCOME]); }
  };

  const startRename = (sid: string, currentTitle: string) => { setEditingSessionId(sid); setEditTitle(currentTitle); };
  const commitRename = async (sid: string) => {
    if (!editTitle.trim()) { setEditingSessionId(null); return; }
    try { await updateSession(sid, editTitle.trim()); setSessions(v => v.map(s => s.id === sid ? { ...s, title: editTitle.trim() } : s)); } catch { /* ignore */ }
    setEditingSessionId(null);
  };

  const removeSession = async (sid: string) => {
    try { await deleteSession(sid); setSessions(v => v.filter(s => s.id !== sid)); if (sid === sessionId) newSession(); } catch { /* ignore */ }
  };

  const branchFromTurn = async (turnId: string) => {
    if (!sessionId) return;
    try {
      const { sessionId: newId, history } = await forkSession(sessionId, turnId);
      const msgs: Message[] = [WELCOME];
      for (const h of history) {
        if (h.role === 'USER') msgs.push({ role: 'user', text: h.content });
        else msgs.push({ role: 'agent', text: h.content, kind: 'result' });
      }
      setSessionId(newId); setMessages(msgs); setState({ kind: 'idle' });
      Toast.success('已从该消息创建分支');
      loadSessions();
    } catch (e) { Toast.error('分支失败'); }
  };

  // ── Render helpers ──
  const renderConfirmationCard = () => {
    if (state.kind !== 'awaiting_confirm') return null;
    const toolLabels: Record<string, string> = {
      request_publish: '发布报告', delete: '删除', modify_provider: '修改供应商配置', override_settings: '覆盖系统设置',
    };
    const label = state.tools.map(t => toolLabels[t] ?? t).join('、');
    return (
      <div className="agent-confirm-card">
        <div className="agent-confirm-header">
          <IconAlertTriangle size="large" style={{ color: 'var(--semi-color-warning, #f5a623)' }} />
          <span>高风险操作需要确认</span>
        </div>
        <p className="agent-confirm-detail">即将执行：<strong>{label}</strong></p>
        <p className="agent-confirm-hint">{state.message}</p>
        <div className="agent-confirm-actions">
          <Button type="primary" theme="solid" onClick={() => handleConfirm(true)}>确认执行</Button>
          <Button type="tertiary" onClick={() => handleConfirm(false)}>取消</Button>
        </div>
      </div>
    );
  };

  /** Render clickable resource links below a message. */
  const openResourceLink = (link: ResLink) => {
    if (link.documentId) {
      window.dispatchEvent(new CustomEvent('subtlesight:document-refresh', {
        detail: { documentId: link.documentId },
      }));
      navigate(`/knowledge?documentId=${link.documentId}`);
    } else if (link.fileId) {
      navigate(`/knowledge?fileId=${link.fileId}`);
    } else if (link.url) {
      window.open(link.url, '_blank');
    }
  };

  /** Navigate a citation: highlight the matching record in the execution panel, then jump. */
  const navigateCitation = (url: string | null, meta: CitationMeta) => {
    if (url) {
      window.dispatchEvent(new CustomEvent('subtlesight:citation-highlight', {
        detail: { resourceId: meta.resourceId },
      }));
      handleCitationNav(url);
    } else if (meta.url) {
      window.open(meta.url, '_blank', 'noopener');
    }
  };

  const stopCurrentTurn = async () => {
    const turnId = state.kind === 'thinking' || state.kind === 'streaming' ? state.turnId : undefined;
    if (!turnId) return;
    closeSse();
    updateRun(turnId, { type: 'turn_cancelled', message: '任务已停止' });
    setState({ kind: 'done' });
    try {
      await cancelTurn(turnId);
    } catch (error) {
      Toast.error(`停止任务失败：${(error as Error).message}`);
    }
  };

  const renderLinks = (m: Message) => {
    if (!m.links || m.links.length === 0) return null;
    return (
      <div className="agent-links">
        {m.links.map((l, i) => (
          <button
            key={i}
            className="agent-resource-link"
            onClick={() => openResourceLink(l)}
          >
            {l.label}
          </button>
        ))}
      </div>
    );
  };

  // Thinking chain body visibility per turn (default: visible)
  const [collapsedThinkingChains, setCollapsedThinkingChains] = useState<Set<string>>(new Set());
  const toggleThinkingChain = (turnId: string) => setCollapsedThinkingChains(prev => {
    const next = new Set(prev);
    if (next.has(turnId)) next.delete(turnId); else next.add(turnId);
    return next;
  });

  // Tool icon mapping using Semi UI SVG icons
  const toolIcon = (tool: string): ReactNode => {
    switch (tool) {
      case 'search_local': return <IconSearch size="small" />;
      case 'discover_web': return <IconGlobe size="small" />;
      case 'search_documents': return <IconArticle size="small" />;
      case 'create_document': return <IconPlus size="small" />;
      case 'create_report': return <IconArticle size="small" />;
      case 'update_document': return <IconEdit size="small" />;
      case 'ask_question': return <IconHelpCircle size="small" />;
      case 'start_research': return <IconTreeTriangleDown size="small" />;
      case 'draw_diagram': case 'draw_add_node': return <IconPlus size="small" />;
      case 'draw_add_edge': return <IconTreeTriangleDown size="small" />;
      case 'add_watch_target': return <IconGlobe size="small" />;
      case 'delete': return <IconDelete size="small" />;
      case 'list_folders': return <IconFolderStroked size="small" />;
      case 'list_watch_targets': case 'list_watch_changes': return <IconList size="small" />;
      default: return <IconArticle size="small" />;
    }
  };

  const renderMessages = () => {
    // Group messages by turn: each turn has thinking steps + optional result
    const groups: { turnId?: string; userMsg?: Message; thinking: Message[]; result: Message | null }[] = [];
    for (const m of messages) {
      if (m.role === 'user') {
        groups.push({ turnId: m.turnId, userMsg: m, thinking: [], result: null });
      } else if (m.kind === 'result') {
        // Merge into the turn group that owns this result's user message. Results may
        // arrive out of order (SSE replay on subscribe), so search all groups instead of
        // only the last one — otherwise a stale result becomes a standalone bubble that
        // duplicates the answer.
        let target: (typeof groups)[number] | undefined;
        for (let i = groups.length - 1; i >= 0; i -= 1) {
          if (groups[i].turnId === m.turnId) {
            target = groups[i];
            if (groups[i].userMsg) break;
          }
        }
        if (target) {
          target.result = m;
        } else {
          // Genuinely orphaned result (no user group) — keep it standalone.
          groups.push({ turnId: m.turnId, thinking: [], result: m });
        }
      } else {
        // thinking message
        const last = groups[groups.length - 1];
        if (last && last.turnId === m.turnId) {
          last.thinking.push(m);
        } else if (last && !last.turnId && !last.result) {
          // First thinking message after user
          last.turnId = m.turnId;
          last.thinking.push(m);
        } else {
          groups.push({ turnId: m.turnId, thinking: [m], result: null });
        }
      }
    }

    return (
      <div className="chat-list">
        {groups.map((g, gi) => {
          const run = g.turnId ? runs[g.turnId] : undefined;
          return (
          <div key={g.turnId ?? gi} className="agent-turn-group">
            {/* User message */}
            {g.userMsg && (
              <div className="chat user">
                <p>{g.userMsg.text}</p>
              </div>
            )}

            {run && <AgentRunTimeline run={run} onOpenLink={openResourceLink} />}

            {/* Thinking chain — per-step collapsible cards */}
            {!run && g.thinking.length > 0 && (
              <div className="agent-thinking-chain">
                <button
                  className="agent-thinking-toggle"
                  onClick={() => g.turnId && toggleThinkingChain(g.turnId)}
                >
                  <span className={`agent-chevron ${g.turnId && !collapsedThinkingChains.has(g.turnId) ? 'expanded' : ''}`}>▸</span>
                  <span>
                    {g.result ? '推理过程' : '执行详情'}
                    <span className="agent-thinking-count">{g.thinking.length} 步</span>
                  </span>
                </button>
                {g.turnId && !collapsedThinkingChains.has(g.turnId) && (
                  <div className="agent-thinking-body">
                    {g.thinking.map((m, mi) => {
                      const toolName = m.tools?.[0] ?? '';
                      return (
                        <div key={mi} className="thinking-step-card">
                          <div className="step-card-header">
                            <span className="step-card-icon">{toolIcon(toolName)}</span>
                            <span className="step-card-title">{extractStepTitle(m.text)}</span>
                          </div>
                          <div className="step-card-body">
                            <p>{renderTextWithCitations(m.text, m.citationMap, navigateCitation)}</p>
                            {renderLinks(m)}
                            {m.tools?.map(t => <code key={t}>{t}</code>)}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            )}

            {/* Result — always visible */}
            {g.result && (
              <div className="chat agent result-bubble">
                <p>
                  {renderTextWithCitations(g.result.text, buildCitationMap(g.result.references) ?? g.result.citationMap, navigateCitation)}
                  {g.result.streaming && <span className="typing-cursor">|</span>}
                </p>
                {renderLinks(g.result)}
                {g.result.tools?.map(t => <code key={t}>{t}</code>)}
                {g.result.confirmationRequired && (
                  <span className="agent-tag">⚠ 需确认</span>
                )}
                {g.result.turnId && (
                  <div className="result-meta-actions">
                    {sessionId && <button className="result-meta-btn" title="从此消息分支继续" onClick={() => branchFromTurn(g.result!.turnId!)}>↳ 分支</button>}
                    <button className="result-meta-btn" title="查看上下文快照" onClick={() => setExpandedContextTurn(expandedContextTurn === g.result!.turnId ? null : g.result!.turnId!)}>📋 上下文</button>
                  </div>
                )}
                {g.result.turnId && expandedContextTurn === g.result.turnId && sessionId && (
                  <div className="context-snapshot">
                    <ContextSnapshot sessionId={sessionId} turnId={g.result.turnId} />
                  </div>
                )}
              </div>
            )}
          </div>
          );
        })}
      </div>
    );
  };

  return (
    <SideSheet
      className="subtlesight-agent-drawer"
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Button size="small" type={showSessionList ? 'primary' : 'tertiary'} onClick={() => setShowSessionList(v => !v)}>☰</Button>
          <span>问 SubtleSight</span>
          <ButtonGroup size="small" style={{ marginLeft: 4 }}>
            <Button type={mode === 'chat' ? 'primary' : 'tertiary'} size="small" onClick={() => setMode('chat')}>对话</Button>
            <Button type={mode === 'qa' ? 'primary' : 'tertiary'} size="small" icon={<IconSearch />} onClick={() => setMode('qa')}>问答</Button>
          </ButtonGroup>
        </div>
      }
      visible={agentOpen}
      onCancel={() => { closeSse(); setTimeout(() => setAgentOpen(false), 0); }}
      width={showSessionList ? 870 : 590}
      footer={mode === 'qa' ? undefined : (
        <div className="agent-compose">
          {showCitationTemplateHint && <CitationTemplateHint />}
          <TextArea
            autosize rows={2} value={text} onChange={setText}
            onEnterPress={e => { if (!e.shiftKey) { e.preventDefault(); send(); } }}
            placeholder={busy ? '正在处理…' : '搜索、研究、创建视图或跟踪目标…'}
            disabled={busy}
          />
          {state.kind === 'error' ? (
            <Button theme="solid" type="danger" onClick={retry}>重试</Button>
          ) : busy ? (
            <Button className="agent-stop-button" aria-label="停止生成" title="停止生成" icon={<IconStop />} type="danger" onClick={stopCurrentTurn} />
          ) : (
            <Button theme="solid" icon={<IconSend />} onClick={send} />
          )}
        </div>
      )}
    >
      <div style={{ display: 'flex', height: '100%', overflow: 'hidden' }}>
        {showSessionList && (
          <div className="session-list-panel">
            <div className="session-list-header">
              <Button size="small" type="primary" onClick={newSession}>+ 新对话</Button>
            </div>
            <div className="session-list-scroll">
              {sessionsLoading ? <div style={{ padding: 20, textAlign: 'center' }}><Spin /></div> :
               sessions.length === 0 ? <div className="session-list-empty">暂无历史对话</div> :
               sessions.map(s => (
                <div key={s.id} className={`session-list-item${s.id === sessionId ? ' active' : ''}`} onClick={() => switchSession(s.id)}>
                  <div className="session-item-main">
                    {editingSessionId === s.id ? (
                      <input className="session-edit-input" value={editTitle} onChange={e => setEditTitle(e.target.value)}
                        onBlur={() => commitRename(s.id)} onKeyDown={e => { if (e.key === 'Enter') commitRename(s.id); if (e.key === 'Escape') setEditingSessionId(null); }}
                        onClick={e => e.stopPropagation()} autoFocus />
                    ) : (
                      <span className="session-item-title">{s.title}</span>
                    )}
                    <span className="session-item-time">{new Date(s.updatedAt).toLocaleDateString('zh-CN')}</span>
                  </div>
                  <div className="session-item-actions" onClick={e => e.stopPropagation()}>
                    <button title="重命名" onClick={() => startRename(s.id, s.title)}>✎</button>
                    <button title="删除" onClick={() => removeSession(s.id)}>✕</button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflowY: 'auto' }}>
      {mode === 'qa' ? (
        <QaPanel />
      ) : (
        <>
          {state.kind === 'thinking' && !messages.some(m => m.turnId !== undefined) && (
            <div style={{ textAlign: 'center', padding: '2rem' }}><Spin size="large" tip="正在分析…" /></div>
          )}
          {renderConfirmationCard()}
          {renderMessages()}
          {state.kind === 'error' && (
            <div className="agent-error">
              <p>❌ {state.message}</p>
            </div>
          )}
        </>
      )}
        </div>
      </div>
    </SideSheet>
  );
}
