import type { CitationMeta, Reference } from './citations';

export type RunStatus = 'planning' | 'running' | 'paused' | 'completed' | 'failed' | 'cancelled';
export type RunStepStatus = 'waiting' | 'active' | 'completed' | 'failed' | 'paused';

export type RunLink = { label: string; documentId?: string; fileId?: string; url?: string };

/** A structured search-result record shown in the execution-details panel (highlightable). */
export type RunStepRecord = {
  resourceId: string;
  label: string;
  detail?: string;
  url?: string;
};

export type RunStep = {
  ordinal: number;
  toolName: string;
  toolId?: string;
  toolVersion?: string;
  callId?: string;
  label?: string;
  description: string;
  status: RunStepStatus;
  summary: string;
  links: RunLink[];
  records?: RunStepRecord[];
  citationMap?: Record<number, CitationMeta>;
  effects?: Array<{ type: string; verified: boolean; resource?: { type?: string; id?: string } }>;
};

export type RunActivity = {
  id: number;
  ordinal: number;
  text: string;
};

export type AssistantRun = {
  turnId: string;
  title: string;
  rationale: string;
  status: RunStatus;
  steps: RunStep[];
  activities: RunActivity[];
  nextActivityId: number;
  allSuccess?: boolean;
  references?: Reference[];
};

export type AssistantRunEvent =
  | { type: 'planning_started' }
  | { type: 'plan_created'; rationale: string; steps: Array<{ ordinal: number; toolName: string; toolId?: string; toolVersion?: string; label?: string; description: string }> }
  | { type: 'step_started'; ordinal: number; toolName: string; toolId?: string; toolVersion?: string; callId?: string; label?: string; description: string }
  | { type: 'step_chunk'; ordinal: number; chunk: string }
  | { type: 'tool_progress'; ordinal: number; toolName: string; toolId?: string; toolVersion?: string; callId?: string; phase: string; summary: string; detail?: string }
  | { type: 'step_completed'; ordinal: number; toolName: string; toolId?: string; toolVersion?: string; callId?: string; success: boolean; summary: string; links?: RunLink[]; records?: RunStepRecord[]; citationMap?: Record<number, CitationMeta>; effects?: RunStep['effects'] }
  | { type: 'confirmation_required'; toolName: string; description: string }
  | { type: 'streaming_chunk'; chunk: string }
  | { type: 'turn_completed'; allSuccess: boolean; answer?: string; references?: Reference[] }
  | { type: 'turn_cancelled'; message: string }
  | { type: 'error'; message: string };

const MAX_ACTIVITIES = 200;

export function createAssistantRun(turnId: string, request: string): AssistantRun {
  const normalized = request.replace(/\s+/g, ' ').trim();
  const title = normalized.length > 34 ? `${normalized.slice(0, 34)}...` : normalized;
  return {
    turnId,
    title: title || '执行任务',
    rationale: '正在理解请求并生成执行计划',
    status: 'planning',
    steps: [],
    activities: [],
    nextActivityId: 1,
  };
}

function normalizeActivityLines(chunk: string): string[] {
  return chunk
    .split(/\r?\n/)
    .map(line => line.replace(/\s+/g, ' ').trim())
    .filter(Boolean);
}

function appendActivities(run: AssistantRun, ordinal: number, chunk: string): AssistantRun {
  const existing = new Set(run.activities.map(activity => `${activity.ordinal}\u0000${activity.text}`));
  const lines = normalizeActivityLines(chunk).filter(text => !existing.has(`${ordinal}\u0000${text}`));
  if (lines.length === 0) return run;

  let nextId = run.nextActivityId;
  const additions = lines.map(text => ({ id: nextId++, ordinal, text }));
  return {
    ...run,
    activities: [...run.activities, ...additions].slice(-MAX_ACTIVITIES),
    nextActivityId: nextId,
  };
}

function upsertStep(run: AssistantRun, nextStep: RunStep): AssistantRun {
  const existing = run.steps.find(step => step.ordinal === nextStep.ordinal);
  if (existing?.status === 'completed' || existing?.status === 'failed') {
    if (nextStep.status === 'active' || nextStep.status === 'waiting') return run;
  }
  const steps = existing
    ? run.steps.map(step => step.ordinal === nextStep.ordinal ? { ...step, ...nextStep } : step)
    : [...run.steps, nextStep];
  return { ...run, steps: steps.sort((a, b) => a.ordinal - b.ordinal) };
}

export function applyAssistantRunEvent(run: AssistantRun, event: AssistantRunEvent): AssistantRun {
  switch (event.type) {
    case 'planning_started':
      return run.status === 'completed' ? run : { ...run, status: 'planning' };
    case 'plan_created': {
      const existing = new Map(run.steps.map(step => [step.ordinal, step]));
      const steps = event.steps.map(step => existing.get(step.ordinal) ?? {
        ...step,
        status: 'waiting' as const,
        summary: '',
        links: [],
      });
      return {
        ...run,
        rationale: event.rationale || `${steps.length} 个步骤已就绪`,
        status: run.status === 'completed' ? run.status : 'running',
        steps,
      };
    }
    case 'step_started': {
      const next = upsertStep(run, {
        ordinal: event.ordinal,
        toolName: event.toolName,
        description: event.description,
        toolId: event.toolId,
        toolVersion: event.toolVersion,
        callId: event.callId,
        label: event.label,
        status: 'active',
        summary: '',
        links: [],
      });
      return appendActivities({ ...next, status: 'running' }, event.ordinal, event.description || `开始执行 ${event.toolName}`);
    }
    case 'tool_progress': {
      const current = run.steps.find(step => step.ordinal === event.ordinal);
      const terminal = event.phase === 'succeeded' || event.phase === 'failed';
      const paused = event.phase === 'waiting_confirmation';
      const status: RunStepStatus = event.phase === 'failed' ? 'failed'
        : event.phase === 'succeeded' ? 'completed'
          : paused ? 'paused' : 'active';
      const next = upsertStep(run, {
        ordinal: event.ordinal,
        toolName: event.toolName || current?.toolName || '',
        toolId: event.toolId ?? current?.toolId,
        toolVersion: event.toolVersion ?? current?.toolVersion,
        callId: event.callId ?? current?.callId,
        label: current?.label,
        description: current?.description || event.summary,
        status,
        summary: terminal ? current?.summary || event.summary : current?.summary ?? '',
        links: current?.links ?? [],
        records: current?.records,
        citationMap: current?.citationMap,
        effects: current?.effects,
      });
      const withStatus = { ...next, status: paused ? 'paused' as const : terminal ? next.status : 'running' as const };
      return appendActivities(withStatus, event.ordinal, event.detail || event.summary);
    }
    case 'step_chunk':
      return appendActivities(run, event.ordinal, event.chunk);
    case 'step_completed': {
      const current = run.steps.find(step => step.ordinal === event.ordinal);
      const next = upsertStep(run, {
        ordinal: event.ordinal,
        toolName: event.toolName || current?.toolName || '',
        description: current?.description || event.toolName,
        toolId: event.toolId ?? current?.toolId,
        toolVersion: event.toolVersion ?? current?.toolVersion,
        callId: event.callId ?? current?.callId,
        label: current?.label,
        status: event.success ? 'completed' : 'failed',
        summary: event.summary,
        links: event.links ?? current?.links ?? [],
        records: event.records ?? current?.records,
        citationMap: event.citationMap ?? current?.citationMap,
        effects: event.effects ?? current?.effects,
      });
      return appendActivities(next, event.ordinal, event.summary);
    }
    case 'confirmation_required': {
      const active = run.steps.find(step => step.status === 'active' || step.toolName === event.toolName);
      if (!active) return { ...run, status: 'paused' };
      return upsertStep({ ...run, status: 'paused' }, { ...active, status: 'paused', description: event.description || active.description });
    }
    case 'streaming_chunk': {
      const active = run.steps.find(step => step.status === 'active');
      return appendActivities({ ...run, status: 'running' }, active?.ordinal ?? 0, event.chunk);
    }
    case 'turn_completed':
      return {
        ...run,
        status: event.allSuccess ? 'completed' : 'failed',
        allSuccess: event.allSuccess,
        references: event.references ?? run.references,
      };
    case 'turn_cancelled':
      return appendActivities({ ...run, status: 'cancelled', allSuccess: false }, 0, event.message || '任务已停止');
    case 'error':
      return appendActivities({ ...run, status: 'failed', allSuccess: false }, 0, event.message);
  }
}

export function completedStepCount(run: AssistantRun): number {
  return run.steps.filter(step => step.status === 'completed' || step.status === 'failed').length;
}

export function visibleActivities(run: AssistantRun, expanded: boolean): RunActivity[] {
  return expanded ? run.activities : run.activities.slice(-2);
}
