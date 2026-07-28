import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Button, SideSheet, Spin, TextArea, Toast, ButtonGroup } from '@douyinfe/semi-ui';
import { IconSend, IconAlertTriangle, IconSearch } from '@douyinfe/semi-icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useUi } from '../store/ui';
import { postTurn, subscribeTurnEvents, confirmTurn } from '../api/agent';
import { QaPanel } from './QaPanel';
import type { TurnResponse, TurnEvent } from '../types';

// ── State machine ──
type DrawerState =
  | { kind: 'idle' }
  | { kind: 'thinking'; turnId?: string }
  | { kind: 'awaiting_confirm'; turnId: string; tools: string[]; message: string }
  | { kind: 'streaming'; turnId: string; chunk?: string }
  | { kind: 'done' }
  | { kind: 'error'; message: string };

type ResLink = { label: string; documentId?: string; fileId?: string; url?: string };
type Message = { role: 'user' | 'agent'; text: string; tools?: string[]; confirmationRequired?: boolean; turnId?: string; kind?: 'thinking' | 'result'; links?: ResLink[] };

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

const WELCOME: Message = { role: 'agent', text: '告诉我你想追踪什么、核实什么，或要建立怎样的发现视图。' };

export function AgentDrawer(): ReactNode {
  const { agentOpen, setAgentOpen } = useUi();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [mode, setMode] = useState<'chat' | 'qa'>('chat');
  const [text, setText] = useState('');
  const [state, setState] = useState<DrawerState>({ kind: 'idle' });
  const [messages, setMessages] = useState<Message[]>([WELCOME]);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const sseRef = useRef<EventSource | null>(null);
  const context = usePageContext();
  const busy = state.kind === 'thinking';

  // Clean up SSE on unmount
  useEffect(() => () => sseRef.current?.close(), []);

  const closeSse = useCallback(() => {
    const es = sseRef.current;
    if (es) { es.close(); sseRef.current = null; }
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
    onEvent('planning_started', () => setState({ kind: 'thinking', turnId }));

    // plan_created → show plan summary in chat
    onEvent('plan_created', (data) => {
      const steps = data.steps as number ?? 0;
      const rationale = data.rationale as string ?? '';
      setMessages(prev => [...prev, {
        role: 'agent',
        text: `计划已生成：${steps} 步${rationale ? ' — ' + rationale : ''}`,
        turnId,
      }]);
    });

    // step_started → skip showing internal descriptions to user
    // (step_completed provides the actual user-facing result)

    // step_completed → show result with content
    onEvent('step_completed', (data) => {
      const success = data.success as boolean;
      const toolName = data.toolName as string ?? '';
      const result = (data.result ?? {}) as Record<string, unknown>;
      const { text: resultText, links: resultLinks } = formatToolResult(toolName, success, result);
      setMessages(prev => [...prev, {
        role: 'agent',
        text: resultText,
        tools: [toolName],
        turnId,
        kind: 'thinking',
        links: resultLinks,
      }]);
      // Draw / document mutations → refresh knowledge queries so the UI picks up changes
      if (success && (toolName.startsWith('draw_') || toolName.startsWith('create_') || toolName.startsWith('update_'))) {
        queryClient.invalidateQueries({ queryKey: ['knowledge-documents'] });
        queryClient.invalidateQueries({ queryKey: ['knowledge-files'] });
        queryClient.invalidateQueries({ queryKey: ['knowledge-files-all'] });
      }
    });

    // confirmation_required → show confirm UI
    onEvent('confirmation_required', (data) => {
      const toolName = data.toolName as string ?? '';
      const desc = data.description as string ?? '';
      setState({ kind: 'awaiting_confirm', turnId, tools: [toolName], message: desc });
    });

    // streaming_chunk → synthesis text — replace the last agent message
    onEvent('streaming_chunk', (data) => {
      const chunk = data.chunk as string ?? '';
      if (!chunk) return;
      setMessages(prev => {
        const copy = [...prev];
        // Replace the last agent message's text with the synthesis
        for (let i = copy.length - 1; i >= 0; i--) {
          if (copy[i].role === 'agent' && copy[i].turnId === turnId) {
            copy[i] = { ...copy[i], text: chunk, kind: 'result' };
            return copy;
          }
        }
        // Fallback: append new message
        return [...copy, { role: 'agent', text: chunk, turnId, kind: 'result' }];
      });
    });

    // turn_completed → done
    onEvent('turn_completed', () => setState({ kind: 'done' }));

    // turn_result (backward compat) — only show if it adds real content
    onEvent('turn_result', (data) => {
      setMessages(prev => {
        const msg = data.message as string ?? '';
        const tools = data.tools as string[] ?? [];
        // Skip redundant "N 个步骤已执行" — step_completed already shows details
        if (msg.matches(/^\d+\s*个步骤已执行$/)) return prev;
        const exists = prev.some(m => m.turnId === data.turnId && m.text === msg);
        if (exists) return prev;
        return [...prev, { role: 'agent', text: msg, tools, turnId: data.turnId as string }];
      });
    });

    // error
    onEvent('error', (data) => {
      setState({ kind: 'error', message: data.message as string ?? '未知错误' });
    });

    es.onerror = () => { es.close(); sseRef.current = null; };
  }, [closeSse]);

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
        if (!hits || hits.length === 0) return { text: '搜索完成，未找到匹配结果。', links: noLinks };
        const items = hits.slice(0, 5).map((h: any) =>
          `- **${h.title ?? '无标题'}**：${(h.snippet ?? '').slice(0, 120)}`
        ).join('\n');
        return { text: `搜索到 ${hits.length} 条结果：\n${items}`, links: noLinks };
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
  /** Format a short, human-friendly status from tool names. */
  const summarizeTools = (tools: string[]): string => {
    if (!tools || tools.length === 0) return '已处理请求。';
    const labels: Record<string, string> = {
      search_local: '正在搜索情报库…',
      discover_web: '正在搜索全网资料…',
      create_document: '正在创建文档…',
      create_report: '正在生成报告…',
      update_document: '正在更新文档…',
      start_research: '正在启动研究…',
      add_watch_target: '正在添加监控…',
      ask_question: '正在检索知识库…',
      draw_add_node: '正在绘制节点…',
      draw_add_edge: '正在连接节点…',
      delete: '正在处理删除请求…',
    };
    return tools.map(t => labels[t] ?? t).join('；');
  };

  const send = async () => {
    if (!text.trim() || busy) return;
    const current = text.trim();
    setMessages(v => [...v, { role: 'user', text: current }]);
    setText('');
    setState({ kind: 'thinking' });

    try {
      const result = await postTurn({ message: current, context, sessionId: sessionId ?? undefined });
      if (result.sessionId && !sessionId) setSessionId(result.sessionId);
      // Show human-friendly status instead of "N 个步骤已执行"
      const displayMsg = result.confirmationRequired
        ? result.message
        : summarizeTools(result.tools ?? []);
      const msg: Message = {
        role: 'agent', text: displayMsg, tools: result.tools,
        confirmationRequired: result.confirmationRequired, turnId: result.turnId,
        kind: 'thinking',
      };
      setMessages(v => [...v, msg]);

      if (result.confirmationRequired) {
        setState({ kind: 'awaiting_confirm', turnId: result.turnId, tools: result.tools, message: result.message });
      } else {
        setState({ kind: 'done' });
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
  const retry = () => setState({ kind: 'idle' });

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
  const renderLinks = (m: Message) => {
    if (!m.links || m.links.length === 0) return null;
    return (
      <div className="agent-links">
        {m.links.map((l, i) => (
          <button
            key={i}
            className="agent-resource-link"
            onClick={() => {
              if (l.documentId) {
                navigate(`/knowledge?documentId=${l.documentId}`);
              } else if (l.fileId) {
                navigate(`/knowledge?fileId=${l.fileId}`);
              } else if (l.url) {
                window.open(l.url, '_blank');
              }
            }}
          >
            {l.label}
          </button>
        ))}
      </div>
    );
  };

  // Track which turns have their thinking chain collapsed
  const [collapsedTurns, setCollapsedTurns] = useState<Set<string>>(new Set());
  const toggleCollapse = (turnId: string) => setCollapsedTurns(prev => {
    const next = new Set(prev);
    if (next.has(turnId)) next.delete(turnId); else next.add(turnId);
    return next;
  });

  const renderMessages = () => {
    // Group messages by turn: each turn has thinking steps + optional result
    const groups: { turnId?: string; userMsg?: Message; thinking: Message[]; result: Message | null }[] = [];
    for (const m of messages) {
      if (m.role === 'user') {
        groups.push({ userMsg: m, thinking: [], result: null });
      } else if (m.kind === 'result') {
        const last = groups[groups.length - 1];
        if (last && last.turnId === m.turnId) {
          last.result = m;
          last.turnId = m.turnId;
        } else {
          // Standalone result (no thinking prefix)
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
        {groups.map((g, gi) => (
          <div key={gi}>
            {/* User message */}
            {g.userMsg && (
              <div className="chat user">
                <p>{g.userMsg.text}</p>
              </div>
            )}

            {/* Thinking chain — collapsible */}
            {g.thinking.length > 0 && (
              <div className="agent-thinking-chain">
                <button
                  className="agent-thinking-toggle"
                  onClick={() => g.turnId && toggleCollapse(g.turnId)}
                >
                  <span className={`agent-chevron ${g.turnId && !collapsedTurns.has(g.turnId) ? 'expanded' : ''}`}>▸</span>
                  <span>
                    {g.result ? '推理过程' : '执行详情'}
                    <span className="agent-thinking-count">（{g.thinking.length} 步）</span>
                  </span>
                </button>
                {g.turnId && !collapsedTurns.has(g.turnId) && (
                  <div className="agent-thinking-body">
                    {g.thinking.map((m, mi) => (
                      <div key={mi} className="chat agent thinking-step">
                        <p>{m.text}</p>
                        {renderLinks(m)}
                        {m.tools?.map(t => <code key={t}>{t}</code>)}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Result — always visible */}
            {g.result && (
              <div className="chat agent result-bubble">
                <p>{g.result.text}</p>
                {renderLinks(g.result)}
                {g.result.tools?.map(t => <code key={t}>{t}</code>)}
                {g.result.confirmationRequired && (
                  <span className="agent-tag">⚠ 需确认</span>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
    );
  };

  return (
    <SideSheet
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <span>问 SubtleSight</span>
          <ButtonGroup size="small" style={{ marginLeft: 8 }}>
            <Button
              type={mode === 'chat' ? 'primary' : 'tertiary'}
              size="small"
              onClick={() => setMode('chat')}
            >对话</Button>
            <Button
              type={mode === 'qa' ? 'primary' : 'tertiary'}
              size="small"
              icon={<IconSearch />}
              onClick={() => setMode('qa')}
            >问答</Button>
          </ButtonGroup>
        </div>
      }
      visible={agentOpen}
      onCancel={() => { closeSse(); setAgentOpen(false); }}
      width={520}
      footer={mode === 'qa' ? undefined : (
        <div className="agent-compose">
          <TextArea
            autosize rows={2} value={text} onChange={setText}
            onEnterPress={e => { if (!e.shiftKey) { e.preventDefault(); send(); } }}
            placeholder={busy ? '正在处理…' : '搜索、研究、创建视图或跟踪目标…'}
            disabled={busy}
          />
          {state.kind === 'error' ? (
            <Button theme="solid" type="danger" onClick={retry}>重试</Button>
          ) : (
            <Button theme="solid" icon={<IconSend />} loading={busy} onClick={send} disabled={busy} />
          )}
        </div>
      )}
    >
      {mode === 'qa' ? (
        <QaPanel />
      ) : (
        <>
          <div className="agent-intro">
            <span className="brand-mark">S</span>
            <div>
              <strong>受控情报 Agent</strong>
              <p>所有操作通过同一业务服务；发布等高风险动作必须确认。</p>
            </div>
          </div>
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
    </SideSheet>
  );
}
