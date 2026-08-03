import { describe, expect, it } from 'vitest';
import { applyAssistantRunEvent, completedStepCount, createAssistantRun, visibleActivities } from '../components/agentRunModel';

describe('assistant run model', () => {
  it('keeps only the latest two activities in the compact view', () => {
    let run = createAssistantRun('turn-1', '生成报告并绘制关系图');
    run = applyAssistantRunEvent(run, { type: 'step_started', ordinal: 1, toolName: 'create_report', description: '生成报告' });
    run = applyAssistantRunEvent(run, { type: 'step_chunk', ordinal: 1, chunk: '第一条\n第二条\n第三条' });

    expect(visibleActivities(run, false).map(item => item.text)).toEqual(['第二条', '第三条']);
    expect(visibleActivities(run, true)).toHaveLength(4);
  });

  it('does not regress a completed step when an SSE event is replayed', () => {
    let run = createAssistantRun('turn-2', '搜索资料');
    run = applyAssistantRunEvent(run, {
      type: 'plan_created', rationale: '检索并汇总',
      steps: [{ ordinal: 1, toolName: 'search_local', description: '搜索本地情报库' }],
    });
    run = applyAssistantRunEvent(run, { type: 'step_completed', ordinal: 1, toolName: 'search_local', success: true, summary: '找到 8 条结果' });
    run = applyAssistantRunEvent(run, { type: 'step_started', ordinal: 1, toolName: 'search_local', description: '搜索本地情报库' });

    expect(run.steps[0].status).toBe('completed');
    expect(completedStepCount(run)).toBe(1);
  });

  it('caps the full activity buffer for long-running turns', () => {
    let run = createAssistantRun('turn-3', '长期任务');
    for (let index = 0; index < 230; index += 1) {
      run = applyAssistantRunEvent(run, { type: 'step_chunk', ordinal: 1, chunk: `记录 ${index}` });
    }
    expect(run.activities).toHaveLength(200);
    expect(run.activities[0].text).toBe('记录 30');
  });

  it('tracks protocol progress and verified resource effects', () => {
    let run = createAssistantRun('turn-4', '更新当前文档');
    run = applyAssistantRunEvent(run, {
      type: 'plan_created', rationale: '写入并校验',
      steps: [{
        ordinal: 1,
        toolName: 'update_document',
        toolId: 'knowledge.document.update',
        toolVersion: '1.0.0',
        label: '更新文档',
        description: '写入当前文档',
      }],
    });
    run = applyAssistantRunEvent(run, {
      type: 'tool_progress', ordinal: 1, toolName: 'update_document',
      toolId: 'knowledge.document.update', toolVersion: '1.0.0', callId: 'call-1',
      phase: 'verifying', summary: '正在校验保存结果', detail: '核对资源版本',
    });
    run = applyAssistantRunEvent(run, {
      type: 'step_completed', ordinal: 1, toolName: 'update_document', success: true,
      summary: '文档版本已更新为 6',
      effects: [{ type: 'resource.updated', verified: true, resource: { type: 'knowledge_document', id: 'doc-1' } }],
    });

    expect(run.steps[0]).toMatchObject({
      toolId: 'knowledge.document.update',
      toolVersion: '1.0.0',
      callId: 'call-1',
      status: 'completed',
    });
    expect(run.steps[0].effects?.[0].verified).toBe(true);
    expect(visibleActivities(run, false).at(-1)?.text).toBe('文档版本已更新为 6');
  });

  it('threads citationMap and records onto the step and preserves them through tool_progress', () => {
    let run = createAssistantRun('turn-5', '搜索资料');
    run = applyAssistantRunEvent(run, { type: 'step_started', ordinal: 1, toolName: 'search_local', description: '搜索本地情报库' });
    run = applyAssistantRunEvent(run, {
      type: 'step_completed', ordinal: 1, toolName: 'search_local', success: true,
      summary: '搜索到 2 条结果',
      records: [
        { resourceId: 'story-1', label: '故事一', detail: '片段', url: 'https://example.com/1' },
        { resourceId: 'doc-1', label: '文档一', detail: '片段' },
      ],
      citationMap: {
        1: { resourceId: 'story-1', resourceType: 'STORY', locatorJson: '{}', resourceName: '故事一' },
        2: { resourceId: 'doc-1', resourceType: 'DOCUMENT', locatorJson: '{}', resourceName: '文档一' },
      },
    });
    // A later tool_progress "succeeded" must not wipe records/citationMap
    run = applyAssistantRunEvent(run, { type: 'tool_progress', ordinal: 1, toolName: 'search_local', phase: 'succeeded', summary: 'search_local 已完成' });

    expect(run.steps[0].status).toBe('completed');
    expect(run.steps[0].records).toHaveLength(2);
    expect(run.steps[0].records?.[0].url).toBe('https://example.com/1');
    expect(run.steps[0].citationMap?.[1].resourceId).toBe('story-1');
  });

  it('stores turn-level references from turn_completed', () => {
    let run = createAssistantRun('turn-6', '搜索');
    run = applyAssistantRunEvent(run, {
      type: 'turn_completed', allSuccess: true,
      references: [{ index: 1, resourceId: 'story-1', resourceType: 'STORY', resourceName: '故事一' }],
    });
    expect(run.status).toBe('completed');
    expect(run.references?.[0].index).toBe(1);
    expect(run.references?.[0].resourceId).toBe('story-1');
  });
});
