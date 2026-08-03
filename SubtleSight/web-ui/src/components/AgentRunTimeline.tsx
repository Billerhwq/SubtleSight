import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { IconBolt, IconChevronDown, IconClose, IconTickCircle } from '@douyinfe/semi-icons';
import { completedStepCount, visibleActivities } from './agentRunModel';
import type { AssistantRun, RunLink, RunStep } from './agentRunModel';

const TOOL_LABELS: Record<string, string> = {
  search_local: '搜索本地情报库',
  search_documents: '搜索知识库文档',
  discover_web: '搜索全网资料',
  ask_question: '检索并组织回答',
  create_document: '创建知识文档',
  create_report: '生成报告草稿',
  update_document: '更新文档内容',
  start_research: '启动深度研究',
  add_watch_target: '添加监控目标',
  draw_add_node: '绘制关系图节点',
  draw_add_edge: '连接关系图节点',
  draw_auto_layout: '整理关系图布局',
};

function stepLabel(step: RunStep): string {
  return step.description || step.label || TOOL_LABELS[step.toolName] || step.toolName || `步骤 ${step.ordinal}`;
}

function statusCopy(run: AssistantRun): string {
  if (run.status === 'planning') return '正在生成计划';
  if (run.status === 'paused') return '等待确认';
  if (run.status === 'completed') return '任务已完成';
  if (run.status === 'failed') return '任务未完全完成';
  if (run.status === 'cancelled') return '任务已停止';
  return '任务执行中';
}

export function AgentRunTimeline({ run, onOpenLink }: { run: AssistantRun; onOpenLink: (link: RunLink) => void }): ReactNode {
  const [expanded, setExpanded] = useState(false);
  const [highlightResourceId, setHighlightResourceId] = useState<string | null>(null);
  const completed = completedStepCount(run);
  const total = run.steps.length;
  const progress = total > 0 ? Math.min(100, (completed / total) * 100) : 0;
  const activities = visibleActivities(run, expanded);
  const isLive = run.status === 'planning' || run.status === 'running';
  const activityStep = run.steps.find(step => step.status === 'active' || step.status === 'paused')
    ?? [...run.steps].reverse().find(step => step.status === 'completed' || step.status === 'failed');

  // Listen for citation clicks in the answer — highlight the matching source record.
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent<{ resourceId?: string }>).detail;
      if (!detail?.resourceId) return;
      setHighlightResourceId(detail.resourceId);
      const el = document.querySelector(`[data-resource-id="${detail.resourceId}"]`);
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    };
    window.addEventListener('subtlesight:citation-highlight', handler);
    return () => window.removeEventListener('subtlesight:citation-highlight', handler);
  }, []);

  // Clear the flash highlight shortly after it fires.
  useEffect(() => {
    if (!highlightResourceId) return;
    const t = setTimeout(() => setHighlightResourceId(null), 2600);
    return () => clearTimeout(t);
  }, [highlightResourceId]);

  return (
    <section className={`agent-run agent-run-${run.status}`} aria-label={`任务进度：${statusCopy(run)}`}>
      <header className="agent-run-header">
        <span className="agent-run-logo"><IconBolt size="small" /></span>
        <div className="agent-run-heading">
          <strong>{isLive ? '正在处理：' : ''}{run.title}</strong>
          <span>{run.rationale}</span>
        </div>
        <span className="agent-run-count">{total > 0 ? `${completed} / ${total}` : statusCopy(run)}</span>
      </header>

      <div className="agent-run-progress" role="progressbar" aria-valuemin={0} aria-valuemax={total || 1} aria-valuenow={completed}>
        <span style={{ width: `${progress}%` }} />
      </div>

      <div className="agent-run-timeline">
        {run.steps.length === 0 && (
          <div className="agent-run-planning">
            <span className="agent-run-marker active" />
            <div><strong>理解请求并生成计划</strong><span>正在拆分任务步骤...</span></div>
          </div>
        )}

        {run.steps.map(step => {
          const active = step.status === 'active' || step.status === 'paused';
          const verified = step.effects?.some(effect => effect.verified) === true;
          const showsActivity = run.activities.length > 0 && step.ordinal === activityStep?.ordinal;
          return (
            <article className={`agent-run-step ${step.status}`} key={step.ordinal}>
              <span className={`agent-run-marker ${step.status}`}>
                {step.status === 'completed' && <IconTickCircle size="small" />}
                {step.status === 'failed' && <IconClose size="small" />}
              </span>
              <div className="agent-run-step-main">
                <div className="agent-run-step-head">
                  <strong>{stepLabel(step)}</strong>
                  <span>{step.status === 'completed' ? verified ? '完成 · 已验证' : '完成' : step.status === 'failed' ? '失败' : step.status === 'paused' ? '待确认' : step.status === 'active' ? '进行中' : '等待'}</span>
                </div>
                {step.records && step.records.length > 0 && !active ? (
                  <div className="agent-run-records">
                    {step.records.map((rec, ri) => (
                      <div
                        key={ri}
                        className={`agent-run-record${highlightResourceId === rec.resourceId ? ' flash' : ''}`}
                        data-resource-id={rec.resourceId}
                        title={rec.detail}
                      >
                        <span className="agent-run-record-name">{rec.label}</span>
                        {rec.detail && <span className="agent-run-record-detail">{rec.detail}</span>}
                        {rec.url && (
                          <a href={rec.url} target="_blank" rel="noreferrer" onClick={e => e.stopPropagation()}>原文</a>
                        )}
                      </div>
                    ))}
                  </div>
                ) : step.summary && !active ? (
                  <p title={step.summary}>{step.summary}</p>
                ) : null}
                {step.links.length > 0 && !active && (
                  <div className="agent-run-links">
                    {step.links.map((link, index) => (
                      <button type="button" key={`${link.label}-${index}`} onClick={() => onOpenLink(link)}>{link.label}</button>
                    ))}
                  </div>
                )}

                {showsActivity && (
                  <div className={`agent-activity-slot${expanded ? ' expanded' : ''}`}>
                    <div className="agent-activity-head">
                      <span className="agent-live-dot" />
                      <strong>{step.status === 'paused' ? '执行已暂停' : active ? '实时动态' : '执行详情'}</strong>
                      <span className="agent-activity-updated">刚刚更新</span>
                      {run.activities.length > 2 && (
                        <button type="button" aria-expanded={expanded} onClick={() => setExpanded(value => !value)}>
                          {expanded ? '收起详情' : `展开 ${run.activities.length} 条`}
                          <IconChevronDown size="small" />
                        </button>
                      )}
                    </div>
                    <div className="agent-activity-lines" aria-live="polite">
                      {activities.map(activity => (
                        <div className="agent-activity-line" key={activity.id}>
                          <span>{String(activity.id).padStart(2, '0')}</span>
                          <p>{activity.text}</p>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            </article>
          );
        })}
      </div>

      <footer className="agent-run-footer">
        <span>{completed} 个步骤已处理 · {run.activities.length} 条执行记录</span>
        <strong>{statusCopy(run)}</strong>
      </footer>
    </section>
  );
}
