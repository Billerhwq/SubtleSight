export type DrawNodeKind = 'rect' | 'pill' | 'accent' | 'purple' | 'note' | 'diamond';

export interface DrawNode {
  id: string;
  kind: DrawNodeKind;
  x: number;
  y: number;
  width: number;
  height: number;
  label: string;
  fill?: string;
}

export interface DrawEdge {
  id: string;
  from: string;
  to: string;
}

export interface DrawingModel {
  nodes: DrawNode[];
  edges: DrawEdge[];
}

export const DEFAULT_DRAWING: DrawingModel = {
  nodes: [
    { id: 'start', kind: 'pill', x: 70, y: 92, width: 150, height: 72, label: '项目启动' },
    { id: 'requirement', kind: 'rect', x: 310, y: 92, width: 150, height: 72, label: '需求分析' },
    { id: 'design', kind: 'accent', x: 570, y: 92, width: 150, height: 72, label: '系统设计' },
    { id: 'develop', kind: 'purple', x: 570, y: 270, width: 150, height: 72, label: '开发实现' },
    { id: 'test', kind: 'rect', x: 310, y: 270, width: 150, height: 72, label: '测试验收' },
    { id: 'finish', kind: 'pill', x: 70, y: 270, width: 150, height: 72, label: '项目交付' },
    { id: 'note', kind: 'note', x: 690, y: 390, width: 185, height: 92, label: '可从知识库引用资料，作为图中的活卡片。' },
  ],
  edges: [
    { id: 'e1', from: 'start', to: 'requirement' },
    { id: 'e2', from: 'requirement', to: 'design' },
    { id: 'e3', from: 'design', to: 'develop' },
    { id: 'e4', from: 'develop', to: 'test' },
    { id: 'e5', from: 'test', to: 'finish' },
  ],
};

export function parseDrawing(raw: string | null | undefined): DrawingModel {
  if (!raw) return structuredClone(DEFAULT_DRAWING);
  try {
    const value = JSON.parse(raw) as Partial<DrawingModel>;
    if (!Array.isArray(value.nodes) || !Array.isArray(value.edges)) return structuredClone(DEFAULT_DRAWING);
    return {
      nodes: value.nodes.filter(isDrawNode).map(node => ({ ...node })),
      edges: value.edges.filter(isDrawEdge).map(edge => ({ ...edge })),
    };
  } catch {
    return structuredClone(DEFAULT_DRAWING);
  }
}

export function stringifyDrawing(model: DrawingModel): string {
  return JSON.stringify({
    nodes: model.nodes.map(node => ({ ...node })),
    edges: model.edges.map(edge => ({ ...edge })),
  });
}

export function addNode(model: DrawingModel, kind: DrawNodeKind): DrawingModel {
  const serial = model.nodes.length + 1;
  const isNote = kind === 'note';
  const isDiamond = kind === 'diamond';
  const node: DrawNode = {
    id: `node-${Date.now()}-${serial}`,
    kind,
    x: 225 + (serial % 5) * 24,
    y: 150 + (serial % 4) * 22,
    width: isNote ? 185 : isDiamond ? 112 : 150,
    height: isNote ? 92 : isDiamond ? 112 : 72,
    label: isNote ? '双击或在右侧填写备注' : isDiamond ? '是否通过？' : '新建步骤',
  };
  return { ...model, nodes: [...model.nodes, node] };
}

export function updateNode(model: DrawingModel, id: string, patch: Partial<DrawNode>): DrawingModel {
  return { ...model, nodes: model.nodes.map(node => node.id === id ? { ...node, ...patch, id: node.id } : node) };
}

export function removeNode(model: DrawingModel, id: string): DrawingModel {
  return {
    nodes: model.nodes.filter(node => node.id !== id),
    edges: model.edges.filter(edge => edge.from !== id && edge.to !== id),
  };
}

function isDrawNode(value: unknown): value is DrawNode {
  if (!value || typeof value !== 'object') return false;
  const node = value as Partial<DrawNode>;
  return typeof node.id === 'string'
    && typeof node.kind === 'string'
    && typeof node.x === 'number'
    && typeof node.y === 'number'
    && typeof node.width === 'number'
    && typeof node.height === 'number'
    && typeof node.label === 'string';
}

function isDrawEdge(value: unknown): value is DrawEdge {
  if (!value || typeof value !== 'object') return false;
  const edge = value as Partial<DrawEdge>;
  return typeof edge.id === 'string' && typeof edge.from === 'string' && typeof edge.to === 'string';
}
