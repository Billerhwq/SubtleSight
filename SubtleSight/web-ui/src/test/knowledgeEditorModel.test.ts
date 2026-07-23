import { describe, expect, it, vi } from 'vitest';
import {
  DEFAULT_DRAWING,
  addNode,
  parseDrawing,
  removeNode,
  stringifyDrawing,
  updateNode,
} from '../knowledgeEditorModel';

describe('knowledge editor drawing model', () => {
  it('round trips editable nodes and edges', () => {
    const serialized = stringifyDrawing(DEFAULT_DRAWING);
    const restored = parseDrawing(serialized);

    expect(restored).toEqual(DEFAULT_DRAWING);
    expect(restored).not.toBe(DEFAULT_DRAWING);
    expect(restored.nodes).not.toBe(DEFAULT_DRAWING.nodes);
  });

  it('falls back to a safe starter drawing for invalid JSON', () => {
    expect(parseDrawing('{broken')).toEqual(DEFAULT_DRAWING);
    expect(parseDrawing('{"nodes":null,"edges":[]}')).toEqual(DEFAULT_DRAWING);
  });

  it('adds, edits and removes a node without leaving dangling edges', () => {
    vi.spyOn(Date, 'now').mockReturnValue(1234);
    const added = addNode(DEFAULT_DRAWING, 'rect');
    const node = added.nodes.at(-1)!;
    const linked = {
      ...added,
      edges: [...added.edges, { id: 'new-edge', from: 'start', to: node.id }],
    };
    const edited = updateNode(linked, node.id, { label: '发布复盘', x: 410 });
    const removed = removeNode(edited, node.id);

    expect(edited.nodes.at(-1)).toMatchObject({ label: '发布复盘', x: 410 });
    expect(removed.nodes.some(item => item.id === node.id)).toBe(false);
    expect(removed.edges.some(edge => edge.to === node.id)).toBe(false);
    vi.restoreAllMocks();
  });
});
