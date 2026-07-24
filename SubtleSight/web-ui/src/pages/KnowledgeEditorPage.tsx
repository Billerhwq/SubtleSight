import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { PointerEvent as ReactPointerEvent, ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { EditorContent, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { Button, Input, Modal, Select, Spin, Toast } from '@douyinfe/semi-ui';
import {
  IconArticle,
  IconDelete,
  IconEdit,
  IconFolderStroked,
  IconPlus,
  IconRefresh,
  IconSave,
} from '@douyinfe/semi-icons';
import { ApiError, del, get, post, put } from '../api/client';
import {
  DEFAULT_DRAWING,
  addNode,
  parseDrawing,
  removeNode,
  stringifyDrawing,
  updateNode,
} from '../knowledgeEditorModel';
import type { DrawingModel, DrawNode, DrawNodeKind } from '../knowledgeEditorModel';
import type {
  KnowledgeAiSuggestion,
  KnowledgeDocument,
  KnowledgeDocumentVersion,
  KnowledgeFolder,
} from '../types';

type EditorMode = 'document' | 'draw';
type SaveState = 'saved' | 'saving' | 'dirty' | 'conflict' | 'error';

const DEFAULT_CONTENT = `
  <p>本项目以“校园知识协作平台”为实践主题，引导团队完成从需求分析、系统设计到测试交付的完整流程。</p>
  <h2>一、项目目标</h2>
  <ul>
    <li>理解需求、设计、开发与测试之间的依赖关系。</li>
    <li>使用结构化文档沉淀过程，并形成可追溯的知识资产。</li>
    <li>通过 Draw 梳理流程，再把图形作为可编辑内容块放回文档。</li>
  </ul>
  <h2>二、项目实施流程</h2>
  <p>点击下方图形的“编辑图形”，即可切换到 Draw 模式继续调整。</p>
`;

function draftSignature(document: KnowledgeDocument): string {
  return JSON.stringify({
    folderId: document.folderId ?? null,
    title: document.title,
    contentHtml: document.contentHtml,
    drawingJson: document.drawingJson,
  });
}

function relativeTime(iso: string): string {
  const diff = Math.max(0, Date.now() - new Date(iso).getTime());
  const minutes = Math.floor(diff / 60_000);
  if (minutes < 1) return '刚刚';
  if (minutes < 60) return `${minutes} 分钟前`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours} 小时前`;
  return new Date(iso).toLocaleDateString('zh-CN');
}

function escapeHtml(value: string): string {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll('\n', '<br>');
}

function edgePath(model: DrawingModel, fromId: string, toId: string): string | null {
  const from = model.nodes.find(node => node.id === fromId);
  const to = model.nodes.find(node => node.id === toId);
  if (!from || !to) return null;
  const fromCenter = { x: from.x + from.width / 2, y: from.y + from.height / 2 };
  const toCenter = { x: to.x + to.width / 2, y: to.y + to.height / 2 };
  const horizontal = Math.abs(toCenter.x - fromCenter.x) >= Math.abs(toCenter.y - fromCenter.y);
  if (horizontal) {
    const direction = Math.sign(toCenter.x - fromCenter.x) || 1;
    const x1 = fromCenter.x + direction * from.width / 2;
    const x2 = toCenter.x - direction * (to.width / 2 + 7);
    const mid = (x1 + x2) / 2;
    return `M ${x1} ${fromCenter.y} C ${mid} ${fromCenter.y}, ${mid} ${toCenter.y}, ${x2} ${toCenter.y}`;
  }
  const direction = Math.sign(toCenter.y - fromCenter.y) || 1;
  const y1 = fromCenter.y + direction * from.height / 2;
  const y2 = toCenter.y - direction * (to.height / 2 + 7);
  const mid = (y1 + y2) / 2;
  return `M ${fromCenter.x} ${y1} C ${fromCenter.x} ${mid}, ${toCenter.x} ${mid}, ${toCenter.x} ${y2}`;
}

function nodeColors(node: DrawNode): { fill: string; stroke: string; text: string } {
  if (node.fill) return { fill: node.fill, stroke: '#c8d0dc', text: '#344155' };
  switch (node.kind) {
    case 'pill': return { fill: '#fff4e9', stroke: '#f2a26a', text: '#9a571d' };
    case 'accent': return { fill: '#ecfbf5', stroke: '#78d0ae', text: '#216c51' };
    case 'purple': return { fill: '#f6f1ff', stroke: '#b9a2ec', text: '#6743a6' };
    case 'note': return { fill: '#fff8d6', stroke: '#e6d77f', text: '#725d1f' };
    case 'diamond': return { fill: '#eef5ff', stroke: '#8db5f2', text: '#2f5c9c' };
    default: return { fill: '#ffffff', stroke: '#cbd4e0', text: '#344155' };
  }
}

function DiagramPreview({ model }: { model: DrawingModel }): ReactNode {
  return (
    <svg className="ke-diagram-svg" viewBox="0 0 920 520" role="img" aria-label="Draw 图形预览">
      <defs>
        <marker id="ke-preview-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
          <path d="M0 0 10 5 0 10Z" fill="#9aa6b7" />
        </marker>
      </defs>
      {model.edges.map(edge => {
        const path = edgePath(model, edge.from, edge.to);
        return path ? <path key={edge.id} d={path} fill="none" stroke="#9aa6b7" strokeWidth="2" markerEnd="url(#ke-preview-arrow)" /> : null;
      })}
      {model.nodes.map(node => {
        const colors = nodeColors(node);
        if (node.kind === 'diamond') {
          const cx = node.x + node.width / 2;
          const cy = node.y + node.height / 2;
          const points = `${cx},${node.y} ${node.x + node.width},${cy} ${cx},${node.y + node.height} ${node.x},${cy}`;
          return (
            <g key={node.id}>
              <polygon points={points} fill={colors.fill} stroke={colors.stroke} strokeWidth="1.7" />
              <text x={cx} y={cy + 4} fill={colors.text} fontSize="14" textAnchor="middle">{node.label}</text>
            </g>
          );
        }
        return (
          <g key={node.id}>
            <rect
              x={node.x}
              y={node.y}
              width={node.width}
              height={node.height}
              rx={node.kind === 'pill' ? node.height / 2 : node.kind === 'note' ? 4 : 9}
              fill={colors.fill}
              stroke={colors.stroke}
              strokeWidth="1.7"
            />
            <text
              x={node.x + node.width / 2}
              y={node.y + node.height / 2 + 5}
              fill={colors.text}
              fontSize={node.kind === 'note' ? 12 : 14}
              textAnchor="middle"
            >
              {node.label.length > 17 ? `${node.label.slice(0, 17)}…` : node.label}
            </text>
          </g>
        );
      })}
    </svg>
  );
}

interface DrawBoardProps {
  model: DrawingModel;
  onChange: (model: DrawingModel) => void;
  onInsert: () => void;
}

function DrawBoard({ model, onChange, onInsert }: DrawBoardProps): ReactNode {
  const [selectedId, setSelectedId] = useState<string | null>(model.nodes[0]?.id ?? null);
  const [zoom, setZoom] = useState(100);
  const canvasRef = useRef<HTMLDivElement | null>(null);
  const selected = model.nodes.find(node => node.id === selectedId) ?? null;

  useEffect(() => {
    if (selectedId && !model.nodes.some(node => node.id === selectedId)) setSelectedId(null);
  }, [model.nodes, selectedId]);

  const add = (kind: DrawNodeKind) => {
    const next = addNode(model, kind);
    onChange(next);
    setSelectedId(next.nodes.at(-1)?.id ?? null);
  };

  const startDrag = (event: ReactPointerEvent<HTMLDivElement>, node: DrawNode) => {
    event.preventDefault();
    event.stopPropagation();
    setSelectedId(node.id);
    const canvas = canvasRef.current;
    if (!canvas) return;
    const bounds = canvas.getBoundingClientRect();
    const startX = event.clientX;
    const startY = event.clientY;
    const originX = node.x;
    const originY = node.y;

    const move = (pointer: PointerEvent) => {
      const scale = zoom / 100;
      const x = Math.max(0, Math.min(920 - node.width, originX + (pointer.clientX - startX) / scale));
      const y = Math.max(50, Math.min(520 - node.height, originY + (pointer.clientY - startY) / scale));
      if (pointer.clientX >= bounds.left - 20 && pointer.clientX <= bounds.right + 20)
        onChange(updateNode(model, node.id, { x: Math.round(x), y: Math.round(y) }));
    };
    const up = () => {
      window.removeEventListener('pointermove', move);
      window.removeEventListener('pointerup', up);
    };
    window.addEventListener('pointermove', move);
    window.addEventListener('pointerup', up);
  };

  return (
    <div className="ke-draw-layout">
      <aside className="ke-shape-rail" aria-label="Draw 工具栏">
        <button className="ke-shape-tool active" title="选择">↖</button>
        <button className="ke-shape-tool" title="矩形" onClick={() => add('rect')}>▭</button>
        <button className="ke-shape-tool" title="判断" onClick={() => add('diamond')}>◇</button>
        <button className="ke-shape-tool" title="便签" onClick={() => add('note')}>▱</button>
        <button className="ke-shape-tool" title="强调节点" onClick={() => add('accent')}>＋</button>
      </aside>

      <section className="ke-canvas-shell">
        <div className="ke-canvas-floating-toolbar">
          <button title="撤销">↶</button>
          <button title="重做">↷</button>
          <span />
          <button><strong>B</strong></button>
          <button><em>I</em></button>
          <span />
          <button>14 px</button>
          <button>自动布局</button>
        </div>
        <div className="ke-canvas-viewport" onClick={() => setSelectedId(null)}>
          <div
            className="ke-canvas"
            ref={canvasRef}
            style={{ transform: `scale(${zoom / 100})` }}
          >
            <div className="ke-canvas-name">项目实施流程.draw</div>
            <svg className="ke-connector-layer" viewBox="0 0 920 520" aria-hidden="true">
              <defs>
                <marker id="ke-canvas-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
                  <path d="M0 0 10 5 0 10Z" fill="#97a4b5" />
                </marker>
              </defs>
              {model.edges.map(edge => {
                const path = edgePath(model, edge.from, edge.to);
                return path ? <path key={edge.id} d={path} fill="none" stroke="#97a4b5" strokeWidth="2" markerEnd="url(#ke-canvas-arrow)" /> : null;
              })}
            </svg>
            {model.nodes.map(node => {
              const colors = nodeColors(node);
              return (
                <div
                  key={node.id}
                  className={`ke-draw-node ${node.kind}${selectedId === node.id ? ' selected' : ''}`}
                  style={{
                    left: node.x,
                    top: node.y,
                    width: node.width,
                    height: node.height,
                    background: colors.fill,
                    borderColor: colors.stroke,
                    color: colors.text,
                  }}
                  onPointerDown={event => startDrag(event, node)}
                >
                  <span>{node.label}</span>
                </div>
              );
            })}
          </div>
        </div>
        <div className="ke-zoom">
          <button onClick={() => setZoom(value => Math.max(60, value - 10))}>−</button>
          <span>{zoom}%</span>
          <button onClick={() => setZoom(value => Math.min(140, value + 10))}>＋</button>
          <button onClick={() => setZoom(100)}>⌗</button>
        </div>
      </section>

      <aside className="ke-properties">
        <div className="ke-property-head">图形属性</div>
        {selected ? (
          <>
            <section className="ke-property-section">
              <label>节点文字</label>
              <Input value={selected.label} onChange={label => onChange(updateNode(model, selected.id, { label }))} />
            </section>
            <section className="ke-property-section">
              <label>填充颜色</label>
              <div className="ke-swatches">
                {['#ffffff', '#fff4e9', '#ecfbf5', '#f6f1ff', '#eef5ff', '#fff8d6'].map(color => (
                  <button
                    key={color}
                    className={selected.fill === color ? 'active' : ''}
                    style={{ background: color }}
                    aria-label={`填充色 ${color}`}
                    onClick={() => onChange(updateNode(model, selected.id, { fill: color }))}
                  />
                ))}
              </div>
            </section>
            <section className="ke-property-section">
              <label>尺寸与位置</label>
              <div className="ke-metrics">
                <span>X <strong>{Math.round(selected.x)}</strong></span>
                <span>Y <strong>{Math.round(selected.y)}</strong></span>
                <span>W <strong>{Math.round(selected.width)}</strong></span>
                <span>H <strong>{Math.round(selected.height)}</strong></span>
              </div>
            </section>
            <section className="ke-property-section">
              <Button
                block
                type="danger"
                theme="borderless"
                icon={<IconDelete />}
                onClick={() => {
                  onChange(removeNode(model, selected.id));
                  setSelectedId(null);
                }}
              >
                删除节点
              </Button>
            </section>
          </>
        ) : (
          <div className="ke-property-empty">选择一个图形后，可在这里编辑文字、颜色与尺寸。</div>
        )}
        <div className="ke-insert-panel">
          <Button block theme="solid" type="primary" icon={<IconPlus />} onClick={onInsert}>插入到文档</Button>
          <p>保存节点、连线和位置，并作为可继续编辑的图块同步到正文。</p>
        </div>
      </aside>
    </div>
  );
}

export function KnowledgeEditorPage(): ReactNode {
  const queryClient = useQueryClient();
  const documentsQuery = useQuery({
    queryKey: ['knowledge-documents'],
    queryFn: () => get<KnowledgeDocument[]>('/knowledge/documents?all=true'),
  });
  const foldersQuery = useQuery({
    queryKey: ['knowledge-folders'],
    queryFn: () => get<KnowledgeFolder[]>('/knowledge/folders'),
  });
  const [currentId, setCurrentId] = useState<string | null>(null);
  const [draft, setDraft] = useState<KnowledgeDocument | null>(null);
  const [loadingDocument, setLoadingDocument] = useState(false);
  const [mode, setMode] = useState<EditorMode>('document');
  const [saveState, setSaveState] = useState<SaveState>('saved');
  const [versionsOpen, setVersionsOpen] = useState(false);
  const [versions, setVersions] = useState<KnowledgeDocumentVersion[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(false);
  const [aiOpen, setAiOpen] = useState(false);
  const [aiInstruction, setAiInstruction] = useState('完善结构并提炼要点');
  const [aiSuggestion, setAiSuggestion] = useState<KnowledgeAiSuggestion | null>(null);
  const [aiLoading, setAiLoading] = useState(false);
  const draftRef = useRef<KnowledgeDocument | null>(null);
  const lastPersistedRef = useRef('');
  const savePromiseRef = useRef<Promise<boolean> | null>(null);
  const loadSequenceRef = useRef(0);

  const editor = useEditor({
    extensions: [StarterKit],
    content: '',
    editorProps: { attributes: { class: 'ke-prose' } },
    onUpdate: ({ editor: activeEditor }) => {
      const html = activeEditor.getHTML();
      setDraft(current => {
        if (!current || current.contentHtml === html) return current;
        const next = { ...current, contentHtml: html };
        draftRef.current = next;
        setSaveState('dirty');
        return next;
      });
    },
  });

  const documents = documentsQuery.data ?? [];
  const folders = foldersQuery.data ?? [];
  const drawing = useMemo(() => parseDrawing(draft?.drawingJson), [draft?.drawingJson]);
  const currentSignature = draft ? draftSignature(draft) : '';

  const updateDraft = useCallback((patch: Partial<KnowledgeDocument>) => {
    setDraft(current => {
      if (!current) return current;
      const next = { ...current, ...patch };
      draftRef.current = next;
      setSaveState('dirty');
      return next;
    });
  }, []);

  const loadDocument = useCallback(async (id: string) => {
    const sequence = ++loadSequenceRef.current;
    setLoadingDocument(true);
    try {
      const loaded = await get<KnowledgeDocument>(`/knowledge/documents/${id}`);
      if (sequence !== loadSequenceRef.current) return;
      draftRef.current = loaded;
      setDraft(loaded);
      lastPersistedRef.current = draftSignature(loaded);
      setSaveState('saved');
      editor?.commands.setContent(loaded.contentHtml, { emitUpdate: false });
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '文档加载失败');
    } finally {
      if (sequence === loadSequenceRef.current) setLoadingDocument(false);
    }
  }, [editor]);

  useEffect(() => {
    if (!currentId && documents.length > 0) setCurrentId(documents[0].id);
  }, [currentId, documents]);

  useEffect(() => {
    if (currentId) void loadDocument(currentId);
  }, [currentId, loadDocument]);

  const saveCurrent = useCallback(async (changeSummary = '自动保存', notify = false): Promise<boolean> => {
    if (savePromiseRef.current) await savePromiseRef.current;
    const current = draftRef.current;
    if (!current) return false;
    const signature = draftSignature(current);
    if (signature === lastPersistedRef.current) {
      setSaveState('saved');
      if (notify) Toast.success('当前内容已保存');
      return true;
    }

    const task = (async () => {
      setSaveState('saving');
      try {
        const saved = await put<KnowledgeDocument>(`/knowledge/documents/${current.id}`, {
          folderId: current.folderId ?? null,
          title: current.title,
          contentHtml: current.contentHtml,
          drawingJson: current.drawingJson,
          expectedVersion: current.version,
          changeSummary,
        });
        const live = draftRef.current;
        if (live?.id === saved.id) {
          const next = { ...live, version: saved.version, updatedAt: saved.updatedAt };
          draftRef.current = next;
          setDraft(next);
        }
        lastPersistedRef.current = signature;
        setSaveState(draftRef.current && draftSignature(draftRef.current) !== signature ? 'dirty' : 'saved');
        await queryClient.invalidateQueries({ queryKey: ['knowledge-documents'] });
        if (notify) Toast.success('文档已保存');
        return true;
      } catch (error) {
        if (error instanceof ApiError && error.status === 409) {
          setSaveState('conflict');
          Modal.error({
            title: '检测到版本冲突',
            content: '这份文档已在其他窗口更新。请刷新后再继续编辑，当前页面内容不会被自动覆盖。',
          });
        } else {
          setSaveState('error');
          Toast.error(error instanceof Error ? error.message : '保存失败');
        }
        return false;
      }
    })();
    savePromiseRef.current = task;
    const result = await task;
    savePromiseRef.current = null;
    return result;
  }, [queryClient]);

  useEffect(() => {
    if (!draft || currentSignature === lastPersistedRef.current) return;
    const timer = window.setTimeout(() => void saveCurrent('自动保存'), 1_200);
    return () => window.clearTimeout(timer);
  }, [currentSignature, draft, saveCurrent]);

  const selectDocument = async (id: string) => {
    if (id === currentId) return;
    const saved = await saveCurrent('切换文档前自动保存');
    if (draftRef.current && !saved) return;
    setCurrentId(id);
    setMode('document');
  };

  const createDocument = async () => {
    try {
      const created = await post<KnowledgeDocument>('/knowledge/documents', {
        folderId: null,
        title: '无标题文档',
        contentHtml: DEFAULT_CONTENT,
        drawingJson: stringifyDrawing(DEFAULT_DRAWING),
      });
      await queryClient.invalidateQueries({ queryKey: ['knowledge-documents'] });
      setCurrentId(created.id);
      setMode('document');
      Toast.success('已创建新文档');
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '创建失败');
    }
  };

  const deleteCurrent = () => {
    const current = draftRef.current;
    if (!current) return;
    Modal.confirm({
      title: '删除文档',
      content: `确定删除「${current.title}」及其版本历史吗？`,
      okText: '删除',
      okButtonProps: { type: 'danger', theme: 'solid' },
      onOk: async () => {
        await del(`/knowledge/documents/${current.id}`);
        const remaining = documents.filter(item => item.id !== current.id);
        draftRef.current = null;
        setDraft(null);
        setCurrentId(remaining[0]?.id ?? null);
        await queryClient.invalidateQueries({ queryKey: ['knowledge-documents'] });
        Toast.success('文档已删除');
      },
    });
  };

  const openVersions = async () => {
    const current = draftRef.current;
    if (!current) return;
    setVersionsOpen(true);
    setVersionsLoading(true);
    try {
      setVersions(await get<KnowledgeDocumentVersion[]>(`/knowledge/documents/${current.id}/versions`));
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '版本历史加载失败');
    } finally {
      setVersionsLoading(false);
    }
  };

  const restoreVersion = async (version: number) => {
    const current = draftRef.current;
    if (!current) return;
    try {
      const restored = await post<KnowledgeDocument>(
        `/knowledge/documents/${current.id}/versions/${version}/restore`,
        { expectedVersion: current.version },
      );
      draftRef.current = restored;
      setDraft(restored);
      editor?.commands.setContent(restored.contentHtml, { emitUpdate: false });
      lastPersistedRef.current = draftSignature(restored);
      setSaveState('saved');
      setVersionsOpen(false);
      await queryClient.invalidateQueries({ queryKey: ['knowledge-documents'] });
      Toast.success(`已恢复到 V${version}，并生成新版本 V${restored.version}`);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : '恢复失败');
    }
  };

  const requestAi = async () => {
    const current = draftRef.current;
    if (!current) return;
    setAiLoading(true);
    setAiSuggestion(null);
    try {
      const { from, to } = editor?.state.selection ?? { from: 0, to: 0 };
      const selectedText = editor && from !== to ? editor.state.doc.textBetween(from, to, ' ') : '';
      const result = await post<KnowledgeAiSuggestion>(`/knowledge/documents/${current.id}/ai-assist`, {
        instruction: aiInstruction,
        selectedText,
      });
      setAiSuggestion(result);
    } catch (error) {
      Toast.error(error instanceof Error ? error.message : 'AI 辅助失败');
    } finally {
      setAiLoading(false);
    }
  };

  const insertAiSuggestion = () => {
    if (!editor || !aiSuggestion) return;
    editor.chain().focus().insertContent(`<p>${escapeHtml(aiSuggestion.suggestion)}</p>`).run();
    setAiOpen(false);
    Toast.success('AI 建议已插入正文，等待自动保存');
  };

  const updateDrawing = (next: DrawingModel) => updateDraft({ drawingJson: stringifyDrawing(next) });

  const insertDrawing = async () => {
    const saved = await saveCurrent('同步 Draw 图形', true);
    if (saved) {
      setMode('document');
      window.setTimeout(() => document.getElementById('ke-diagram-block')?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 80);
    }
  };

  const saveCopy = saveState === 'saving'
    ? '正在保存…'
    : saveState === 'dirty'
      ? '等待自动保存'
      : saveState === 'conflict'
        ? '版本冲突'
        : saveState === 'error'
          ? '保存失败'
          : '已自动保存';

  const folderOptions = [
    { label: '我的知识库（根目录）', value: 'root' },
    ...folders.map(folder => ({ label: folder.name, value: folder.id })),
  ];

  return (
    <div className="ke-page">
      <aside className="ke-documents-pane">
        <div className="ke-pane-title">
          <div>
            <strong>文档编辑</strong>
            <span>{documents.length} 篇文档</span>
          </div>
          <div className="ke-pane-title-actions">
            <button title="刷新" onClick={() => void documentsQuery.refetch()}><IconRefresh size="small" /></button>
            <button title="新建文档" onClick={() => void createDocument()}><IconPlus size="small" /></button>
          </div>
        </div>
        <button className="ke-create-card" onClick={() => void createDocument()}>
          <span><IconPlus /></span>
          新建文档
        </button>
        <div className="ke-document-list">
          {documentsQuery.isLoading ? (
            <div className="ke-list-loading"><Spin /></div>
          ) : documents.length === 0 ? (
            <div className="ke-list-empty">还没有文档<br />点击上方开始写作</div>
          ) : documents.map(document => (
            <button
              key={document.id}
              className={`ke-document-item${document.id === currentId ? ' active' : ''}`}
              onClick={() => void selectDocument(document.id)}
            >
              <span className="ke-document-icon"><IconArticle size="small" /></span>
              <span className="ke-document-copy">
                <strong>{document.title}</strong>
                <small>V{document.version} · {relativeTime(document.updatedAt)}</small>
              </span>
            </button>
          ))}
        </div>
        <div className="ke-pane-hint">
          <IconFolderStroked />
          文档与 Draw 图形统一保存到知识库 SQLite，并保留完整版本。
        </div>
      </aside>

      <section className="ke-editor-area">
        <header className="ke-editor-topbar">
          <div className="ke-breadcrumb">
            <IconFolderStroked />
            <span>知识库</span>
            <span>/</span>
            <strong>{draft?.title ?? '编辑'}</strong>
          </div>
          <div className={`ke-save-status ${saveState}`}>
            <span />
            {saveCopy}
          </div>
          <Button theme="borderless" icon={<IconEdit />} onClick={() => setAiOpen(true)} disabled={!draft}>AI 辅助</Button>
          <Button theme="borderless" icon={<IconArticle />} onClick={() => void openVersions()} disabled={!draft}>历史</Button>
          <Button icon={<IconSave />} onClick={() => void saveCurrent('手动保存', true)} disabled={!draft}>保存</Button>
          <Button theme="solid" type="primary" icon={<IconPlus />} onClick={() => void createDocument()}>新建</Button>
        </header>

        <div className="ke-modebar">
          <div className="ke-mode-tabs">
            <button className={mode === 'document' ? 'active' : ''} onClick={() => setMode('document')}>
              <IconArticle size="small" />文档
            </button>
            <button className={mode === 'draw' ? 'active' : ''} onClick={() => setMode('draw')}>
              <span className="ke-draw-symbol">⌘</span>Draw <em>BETA</em>
            </button>
          </div>
          {draft && (
            <Select
              size="small"
              className="ke-folder-select"
              value={draft.folderId ?? 'root'}
              optionList={folderOptions}
              onChange={value => updateDraft({ folderId: String(value) === 'root' ? undefined : String(value) })}
            />
          )}
        </div>

        {!draft || loadingDocument ? (
          <div className="ke-editor-loading">
            {loadingDocument ? <Spin size="large" tip="正在加载文档…" /> : <Button theme="solid" type="primary" onClick={() => void createDocument()}>创建第一篇文档</Button>}
          </div>
        ) : mode === 'draw' ? (
          <DrawBoard model={drawing} onChange={updateDrawing} onInsert={() => void insertDrawing()} />
        ) : (
          <div className="ke-document-layout">
            <div className="ke-paper-scroll">
              <article className="ke-paper">
                <div className="ke-document-kicker">知识文档 · 可视化协作</div>
                <Input
                  className="ke-title-input"
                  value={draft.title}
                  onChange={title => updateDraft({ title })}
                  placeholder="无标题文档"
                />
                <div className="ke-document-meta">
                  <span>本机用户</span><i /> <span>V{draft.version}</span><i /> <span>{relativeTime(draft.updatedAt)}更新</span>
                </div>
                <div className="ke-rich-toolbar">
                  <button className={editor?.isActive('bold') ? 'active' : ''} onClick={() => editor?.chain().focus().toggleBold().run()}><strong>B</strong></button>
                  <button className={editor?.isActive('italic') ? 'active' : ''} onClick={() => editor?.chain().focus().toggleItalic().run()}><em>I</em></button>
                  <span />
                  <button className={editor?.isActive('heading', { level: 2 }) ? 'active' : ''} onClick={() => editor?.chain().focus().toggleHeading({ level: 2 }).run()}>H2</button>
                  <button className={editor?.isActive('bulletList') ? 'active' : ''} onClick={() => editor?.chain().focus().toggleBulletList().run()}>• 列表</button>
                  <span />
                  <button onClick={() => editor?.chain().focus().undo().run()}>↶</button>
                  <button onClick={() => editor?.chain().focus().redo().run()}>↷</button>
                </div>
                <EditorContent editor={editor} />

                <section className="ke-diagram-block" id="ke-diagram-block">
                  <header>
                    <div><span className="ke-diagram-icon">⌘</span><strong>项目实施流程.draw</strong></div>
                    <Button size="small" theme="light" type="primary" icon={<IconEdit />} onClick={() => setMode('draw')}>编辑图形</Button>
                  </header>
                  <div className="ke-diagram-preview"><DiagramPreview model={drawing} /></div>
                  <footer>
                    <span>图形数据与正文一起保存，可随时返回 Draw 继续编辑</span>
                    <span>当前 {drawing.nodes.length} 个节点 · {drawing.edges.length} 条连线</span>
                  </footer>
                </section>
              </article>
            </div>
            <aside className="ke-document-side">
              <div className="ke-side-tabs"><strong>大纲</strong><span>引用</span></div>
              <nav className="ke-outline">
                <button className="active">{draft.title}</button>
                <button>一、项目目标</button>
                <button>二、项目实施流程</button>
                <button>Draw 图形</button>
              </nav>
              <div className="ke-feature-card">
                <strong>编辑 + Draw 闭环</strong>
                <p>图形不是静态截图：节点、连线和位置会与正文一起自动保存，并进入版本历史。</p>
                <button onClick={() => setMode('draw')}>进入 Draw 编辑 →</button>
              </div>
              <div className="ke-side-actions">
                <Button block icon={<IconArticle />} onClick={() => void openVersions()}>查看版本历史</Button>
                <Button block type="danger" theme="borderless" icon={<IconDelete />} onClick={deleteCurrent}>删除文档</Button>
              </div>
            </aside>
          </div>
        )}
      </section>

      <Modal
        title="版本历史"
        visible={versionsOpen}
        width={620}
        footer={null}
        onCancel={() => setVersionsOpen(false)}
      >
        {versionsLoading ? <div className="ke-modal-loading"><Spin /></div> : (
          <div className="ke-version-list">
            {versions.map(version => (
              <div className="ke-version-item" key={version.version}>
                <span className="ke-version-dot" />
                <div>
                  <strong>V{version.version} · {version.changeSummary || '自动保存'}</strong>
                  <small>{new Date(version.createdAt).toLocaleString('zh-CN')} · {version.title}</small>
                </div>
                {version.version !== draft?.version && (
                  <Button size="small" onClick={() => void restoreVersion(version.version)}>恢复此版本</Button>
                )}
                {version.version === draft?.version && <em>当前版本</em>}
              </div>
            ))}
          </div>
        )}
      </Modal>

      <Modal
        title="AI 文档助手"
        visible={aiOpen}
        width={640}
        onCancel={() => setAiOpen(false)}
        footer={(
          <div className="ke-ai-footer">
            <Button onClick={() => setAiOpen(false)}>取消</Button>
            <Button loading={aiLoading} onClick={() => void requestAi()}>生成建议</Button>
            <Button theme="solid" type="primary" disabled={!aiSuggestion} onClick={insertAiSuggestion}>插入正文</Button>
          </div>
        )}
      >
        <label className="ke-modal-label">希望 AI 做什么？</label>
        <Input value={aiInstruction} onChange={setAiInstruction} placeholder="例如：提炼要点、完善结构、生成验收标准" />
        <div className="ke-ai-presets">
          {['完善结构并提炼要点', '生成可执行的下一步', '补充风险与验收标准'].map(value => (
            <button key={value} onClick={() => setAiInstruction(value)}>{value}</button>
          ))}
        </div>
        <label className="ke-modal-label">生成结果</label>
        <textarea
          className="ke-ai-result"
          value={aiSuggestion?.suggestion ?? ''}
          onChange={event => setAiSuggestion(current => current ? { ...current, suggestion: event.target.value } : null)}
          placeholder={aiLoading ? '正在分析当前文档…' : '点击“生成建议”，结果会显示在这里。'}
        />
        {aiSuggestion && (
          <div className="ke-ai-provider">
            {aiSuggestion.fallback ? '当前未配置在线模型，使用本地规则建议' : `由 ${aiSuggestion.provider} · ${aiSuggestion.model} 生成`}
          </div>
        )}
      </Modal>
    </div>
  );
}
