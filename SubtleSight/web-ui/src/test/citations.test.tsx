import { describe, expect, it } from 'vitest';
import { fireEvent, render, cleanup } from '@testing-library/react';
import { buildCitationMap, buildCitationUrl, formatCitationTime, CitationMarker } from '../components/citations';

describe('citations helpers', () => {
  it('buildCitationMap maps references to the marker record', () => {
    const map = buildCitationMap([
      { index: 1, resourceId: 'story-1', resourceType: 'STORY', resourceName: '故事一', url: 'https://example.com/1', publishedAt: '2026-01-01T00:00:00Z' },
      { index: 3, resourceId: 'doc-1', resourceType: 'DOCUMENT', resourceName: '文档一', locator: '{"metadata":{"blockId":"block-42"}}' },
    ]);
    expect(map?.[1].resourceName).toBe('故事一');
    expect(map?.[1].url).toBe('https://example.com/1');
    expect(map?.[3].locatorJson).toContain('block-42');
    expect(buildCitationMap(undefined)).toBeUndefined();
  });

  it('buildCitationUrl maps STORY/DOCUMENT/FILE and returns null for DOCUMENTVERSION', () => {
    expect(buildCitationUrl({ resourceId: 's1', resourceType: 'STORY', locatorJson: '{}', resourceName: 'n' })).toBe('/stories/s1');
    expect(buildCitationUrl({ resourceId: 'd1', resourceType: 'DOCUMENT', locatorJson: '{"metadata":{"blockId":"b1"}}', resourceName: 'n' }))
      .toBe('/knowledge?documentId=d1&blockId=b1');
    expect(buildCitationUrl({ resourceId: 'f1', resourceType: 'FILE', locatorJson: '{}', resourceName: 'n' })).toBe('/knowledge?fileId=f1');
    expect(buildCitationUrl({ resourceId: 'dv1', resourceType: 'DOCUMENTVERSION', locatorJson: '{}', resourceName: 'n', url: 'https://x' })).toBeNull();
  });

  it('formatCitationTime formats ISO date', () => {
    expect(formatCitationTime('2026-01-01T00:00:00Z')).toContain('2026');
    expect(formatCitationTime(undefined)).toBe('');
  });

  it('CitationMarker renders a hover card and navigates on click', () => {
    const meta = {
      resourceId: 'story-1', resourceType: 'STORY', locatorJson: '{}', resourceName: '故事一',
      summary: '摘要', publishedAt: '2026-01-01T00:00:00Z', url: 'https://example.com/1',
    };
    let clicked: string | null = null;
    const { container } = render(
      <CitationMarker num="1" meta={meta} onNavigate={(url) => { clicked = url; }} />,
    );
    const sup = container.querySelector('sup.citation-marker');
    expect(sup).not.toBeNull();
    expect(sup!.textContent).toBe('[1]');

    fireEvent.click(sup!);
    expect(clicked).toBe('/stories/story-1');

    fireEvent.mouseEnter(sup!.parentElement!);
    const pop = container.querySelector('.citation-popover');
    expect(pop).not.toBeNull();
    expect(pop!.textContent).toContain('故事一');
    expect(pop!.textContent).toContain('摘要');
    expect(pop!.textContent).toContain('查看原文');
    cleanup();
  });
});
