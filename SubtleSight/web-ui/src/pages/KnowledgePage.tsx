import { useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Dropdown, Input, Modal, Spin, Toast, TreeSelect } from '@douyinfe/semi-ui';
import {
  IconArrowDown,
  IconArrowUp,
  IconArticle,
  IconChevronDown,
  IconChevronLeft,
  IconDelete,
  IconEdit,
  IconFile,
  IconFolder,
  IconFolderStroked,
  IconGridRectangle,
  IconList,
  IconPlus,
  IconRefresh,
  IconSearch,
} from '@douyinfe/semi-icons';
import { del, get, post, put, uploadKnowledgeFiles } from '../api/client';
import type { KnowledgeDocument, KnowledgeFile, KnowledgeFolder, KnowledgePreview } from '../types';
import { KnowledgeEditorPage } from './KnowledgeEditorPage';

type ViewMode = 'browse' | 'edit';
type TreeNode = KnowledgeFolder & { children: TreeNode[] };
type Item = { kind: 'folder'; folder: KnowledgeFolder } | { kind: 'file'; file: KnowledgeFile } | { kind: 'document'; document: KnowledgeDocument };

function buildTree(folders: KnowledgeFolder[]): TreeNode[] {
  const map = new Map<string, TreeNode>();
  folders.forEach(f => map.set(f.id, { ...f, children: [] }));
  const roots: TreeNode[] = [];
  map.forEach(node => {
    if (node.parentId && map.has(node.parentId)) map.get(node.parentId)!.children.push(node);
    else roots.push(node);
  });
  return roots;
}
function toTreeData(nodes: TreeNode[]): { label: string; value: string; key: string; children: ReturnType<typeof toTreeData> }[] {
  return nodes.map(n => ({ label: n.name, value: n.id, key: n.id, children: toTreeData(n.children) }));
}
function fmtSize(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  const units = ['KB', 'MB', 'GB'];
  let v = bytes / 1024, i = 0;
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
  return `${v >= 100 ? v.toFixed(0) : v.toFixed(1)} ${units[i]}`;
}
function relTime(iso: string) {
  const diff = Date.now() - new Date(iso).getTime();
  const m = Math.floor(diff / 60000);
  if (m < 1) return '刚刚';
  if (m < 60) return `${m} 分钟前`;
  const h = Math.floor(m / 60);
  if (h < 24) return '今天';
  if (h < 48) return '昨天';
  const d = Math.floor(h / 24);
  if (d < 30) return `${d} 天前`;
  return new Date(iso).toLocaleDateString();
}
function docKind(ext: string) {
  switch (ext) {
    case 'pdf': return { cls: 'pdf', label: 'PDF' };
    case 'xlsx': case 'xls': case 'csv': return { cls: 'xlsx', label: 'X' };
    case 'docx': case 'doc': return { cls: 'docx', label: 'W' };
    case 'md': return { cls: 'md', label: 'M↓' };
    case 'png': case 'jpg': case 'jpeg': case 'gif': case 'webp': case 'svg': case 'bmp': return { cls: 'image', label: 'IMG' };
    case 'txt': case 'log': return { cls: 'txt', label: 'TXT' };
    default: return { cls: 'txt', label: ext ? ext.slice(0, 3).toUpperCase() : 'FILE' };
  }
}
const itemName = (item: Item) => {
  if (item.kind === 'folder') return item.folder.name;
  if (item.kind === 'document') return item.document.title;
  return item.file.name;
};

interface ContextMenuState {
  x: number;
  y: number;
  items: Array<{ label: string; icon?: ReactNode; danger?: boolean; onClick: () => void }>;
}

export function KnowledgePage(): ReactNode {
  const client = useQueryClient() as any;
  const [viewMode, setViewMode] = useState<ViewMode>('browse');
  const [current, setCurrent] = useState<string | null>(null);
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());
  const [view, setView] = useState<'grid' | 'list'>('grid');
  const [ascending, setAscending] = useState(true);
  const [keyword, setKeyword] = useState('');
  const [selectedCard, setSelectedCard] = useState<string | null>(null);
  const [spinning, setSpinning] = useState(false);
  // Modals
  const [newFolderOpen, setNewFolderOpen] = useState(false);
  const [newFolderName, setNewFolderName] = useState('');
  const [newFolderParentId, setNewFolderParentId] = useState<string | null>(null);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploadFiles, setUploadFiles] = useState<File[]>([]);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadResults, setUploadResults] = useState<Array<{ name: string; success: boolean; error?: string }> | null>(null);
  const uploadInputRef = useRef<HTMLInputElement | null>(null);
  const dropZoneRef = useRef<HTMLDivElement | null>(null);
  const [dragOver, setDragOver] = useState(false);
  const [renameTarget, setRenameTarget] = useState<KnowledgeFile | KnowledgeDocument | null>(null);
  const [renameValue, setRenameValue] = useState('');
  const [moveTarget, setMoveTarget] = useState<KnowledgeFile | KnowledgeDocument | null>(null);
  const [moveFolderId, setMoveFolderId] = useState<string>('root');
  const [deleteTarget, setDeleteTarget] = useState<KnowledgeFolder | null>(null);
  // Edit mode
  const [editingDocumentId, setEditingDocumentId] = useState<string | null>(null);
  // Preview
  const [previewItem, setPreviewItem] = useState<Item | null>(null);
  const [previewData, setPreviewData] = useState<KnowledgePreview | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewWidth, setPreviewWidth] = useState(420);
  const previewResizeRef = useRef<{ startX: number; startWidth: number } | null>(null);
  // Context menu
  const [ctxMenu, setCtxMenu] = useState<ContextMenuState | null>(null);
  const ctxBackdropRef = useRef<HTMLDivElement | null>(null);

  const foldersQuery = useQuery<KnowledgeFolder[]>({ queryKey: ['knowledge-folders'], queryFn: () => get<KnowledgeFolder[]>('/knowledge/folders') });
  const filesQuery = useQuery<KnowledgeFile[]>({ queryKey: ['knowledge-files', current], queryFn: () => get<KnowledgeFile[]>(`/knowledge/files${current ? `?folderId=${current}` : ''}`) });
  const allFilesQuery = useQuery<KnowledgeFile[]>({ queryKey: ['knowledge-files-all'], queryFn: () => get<KnowledgeFile[]>('/knowledge/files?all=true') });
  const documentsQuery = useQuery<KnowledgeDocument[]>({ queryKey: ['knowledge-documents'], queryFn: () => get<KnowledgeDocument[]>('/knowledge/documents?all=true') });

  const folders = useMemo(() => (foldersQuery as any).data ?? [], [(foldersQuery as any).data]) as KnowledgeFolder[];
  const files = useMemo(() => (filesQuery as any).data ?? [], [(filesQuery as any).data]) as KnowledgeFile[];
  const allFiles = useMemo(() => (allFilesQuery as any).data ?? [], [(allFilesQuery as any).data]) as KnowledgeFile[];
  const documents = useMemo(() => (documentsQuery as any).data ?? [], [(documentsQuery as any).data]) as KnowledgeDocument[];
  const tree = useMemo(() => buildTree(folders), [folders]);
  const folderMap = useMemo(() => new Map(folders.map(f => [f.id, f])), [folders]);

  const invalidate = () => {
    client.invalidateQueries({ queryKey: ['knowledge-folders'] });
    client.invalidateQueries({ queryKey: ['knowledge-files'] });
    client.invalidateQueries({ queryKey: ['knowledge-files-all'] });
    client.invalidateQueries({ queryKey: ['knowledge-documents'] });
  };

  const createFolder = useMutation({
    mutationFn: (parentId: string | null) => post('/knowledge/folders', { parentId, name: newFolderName }),
    onSuccess: () => { invalidate(); setNewFolderOpen(false); setNewFolderName(''); Toast.success('文件夹已创建'); },
    onError: (e: Error) => Toast.error(e.message),
  } as any);

  const createDocument = useMutation({
    mutationFn: (folderId?: string | null) => post<KnowledgeDocument>('/knowledge/documents', {
      folderId: folderId !== undefined ? folderId : current,
      title: '无标题文档',
      contentHtml: '<p></p>',
      drawingJson: '{"nodes":[],"edges":[]}',
    }),
    onSuccess: (doc: any) => {
      invalidate();
      setEditingDocumentId(doc.id);
      setViewMode('edit');
      Toast.success('已创建新文档');
    },
    onError: (e: Error) => Toast.error(e.message),
  } as any);

  const renameFile = useMutation({
    mutationFn: (input: { id: string; name: string }) => post(`/knowledge/files/${input.id}/rename`, { name: input.name }),
    onSuccess: () => { invalidate(); setRenameTarget(null); Toast.success('已重命名'); },
    onError: (e: Error) => Toast.error(e.message),
  } as any);

  const moveFile = useMutation({
    mutationFn: (input: { id: string; folderId: string | null }) => post(`/knowledge/files/${input.id}/move`, { folderId: input.folderId }),
    onSuccess: () => { invalidate(); setMoveTarget(null); Toast.success('已移动'); },
    onError: (e: Error) => Toast.error(e.message),
  } as any);

  const deleteFile = useMutation({
    mutationFn: (id: string) => del(`/knowledge/files/${id}`),
    onSuccess: () => { invalidate(); Toast.success('已删除'); },
    onError: (e: Error) => Toast.error(e.message),
  } as any);

  const deleteDocument = useMutation({
    mutationFn: (id: string) => del(`/knowledge/documents/${id}`),
    onSuccess: () => { invalidate(); Toast.success('文档已删除'); },
    onError: (e: Error) => Toast.error(e.message),
  } as any);

  const deleteFolder = useMutation({
    mutationFn: (id: string) => del(`/knowledge/folders/${id}`),
    onSuccess: () => { invalidate(); setDeleteTarget(null); Toast.success('文件夹已删除'); },
    onError: (e: Error) => Toast.error(e.message),
  } as any);

  const breadcrumb = useMemo(() => {
    const parts: { name: string; id: string }[] = [];
    let id = current;
    while (id) {
      const f = folderMap.get(id);
      if (!f) break;
      parts.unshift({ name: f.name, id: f.id });
      id = f.parentId ?? null;
    }
    return parts;
  }, [current, folderMap]);

  // Documents in current folder
  const folderDocuments = useMemo(
    () => documents.filter(d => (d.folderId ?? null) === current),
    [documents, current],
  );

  const items = useMemo<Item[]>(() => {
    const subfolders = folders.filter(f => (f.parentId ?? null) === current);
    const list: Item[] = [
      ...subfolders.map(folder => ({ kind: 'folder' as const, folder })),
      ...folderDocuments.map(document => ({ kind: 'document' as const, document })),
      ...files.map(file => ({ kind: 'file' as const, file })),
    ];
    const key = keyword.trim().toLowerCase();
    const filtered = key ? list.filter(item => itemName(item).toLowerCase().includes(key)) : list;
    return [...filtered].sort((a, b) =>
      ascending ? itemName(a).localeCompare(itemName(b), 'zh-CN') : itemName(b).localeCompare(itemName(a), 'zh-CN'),
    );
  }, [folders, files, folderDocuments, current, keyword, ascending]);

  const toggle = (id: string) => setCollapsed(prev => {
    const next = new Set(prev);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    return next;
  });

  const openFolder = (id: string | null) => {
    setCurrent(id);
    setSelectedCard(null);
    setViewMode('browse');
    // Expand tree path to this folder
    if (id) {
      const pathIds: string[] = [];
      let parentId: string | null | undefined = folderMap.get(id)?.parentId ?? null;
      while (parentId) {
        pathIds.push(parentId);
        parentId = folderMap.get(parentId)?.parentId ?? null;
      }
      setCollapsed(prev => {
        const next = new Set(prev);
        for (const pid of pathIds) next.delete(pid);
        next.delete('root');
        return next;
      });
    }
  };

  const collapseAll = () => {
    setCollapsed(new Set(folders.map(f => f.id)));
  };

  const refresh = () => {
    setSpinning(true);
    invalidate();
    setTimeout(() => setSpinning(false), 450);
  };

  const openEditDocument = (docId: string) => {
    setEditingDocumentId(docId);
    setViewMode('edit');
  };

  const closeEdit = () => {
    // First switch back to browse mode
    setViewMode('browse');
    setEditingDocumentId(null);
    // Invalidate to refresh the document list
    invalidate();
  };

  // --- Upload ---
  const handleUploadFiles = (newFiles: FileList | File[]) => {
    const arr = Array.from(newFiles);
    setUploadFiles(prev => [...prev, ...arr]);
    setUploadResults(null);
  };

  const removeUploadFile = (index: number) => {
    setUploadFiles(prev => prev.filter((_, i) => i !== index));
  };

  const startUpload = async () => {
    if (uploadFiles.length === 0) return;
    setUploading(true);
    setUploadProgress(0);
    setUploadResults(null);
    try {
      await uploadKnowledgeFiles(
        current,
        uploadFiles,
        (percent) => setUploadProgress(percent),
      );
      setUploadResults(uploadFiles.map(f => ({ name: f.name, success: true })));
      setUploadFiles([]);
      invalidate();
      Toast.success(`成功上传 ${uploadFiles.length} 个文件`);
    } catch (e) {
      setUploadResults([{ name: '批量上传', success: false, error: e instanceof Error ? e.message : '上传失败' }]);
      Toast.error(e instanceof Error ? e.message : '上传失败');
    } finally {
      setUploading(false);
    }
  };

  const resetUpload = () => {
    setUploadFiles([]);
    setUploadProgress(0);
    setUploadResults(null);
    setUploading(false);
  };

  const closeUpload = () => {
    if (uploading) return;
    setUploadOpen(false);
    resetUpload();
  };

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
    return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
  };

  const fileTypeIcon = (name: string) => {
    const ext = name.split('.').pop()?.toLowerCase() ?? '';
    if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'bmp', 'ico'].includes(ext)) return { cls: 'image', label: 'IMG' };
    if (['pdf'].includes(ext)) return { cls: 'pdf', label: 'PDF' };
    if (['xlsx', 'xls', 'csv'].includes(ext)) return { cls: 'xlsx', label: 'X' };
    if (['docx', 'doc'].includes(ext)) return { cls: 'docx', label: 'W' };
    if (['md'].includes(ext)) return { cls: 'md', label: 'M' };
    if (['txt', 'log', 'json', 'xml', 'yml', 'yaml'].includes(ext)) return { cls: 'txt', label: 'TXT' };
    return { cls: 'txt', label: ext.slice(0, 3).toUpperCase() || 'FILE' };
  };

  // --- Context Menu ---
  const showCtx = (e: React.MouseEvent, items: ContextMenuState['items']) => {
    e.preventDefault();
    e.stopPropagation();
    setCtxMenu({ x: e.clientX, y: e.clientY, items });
  };

  const hideCtx = () => setCtxMenu(null);

  const confirmDeleteFolder = (folder: KnowledgeFolder) => {
    setDeleteTarget(folder);
    (Modal as any).confirm({
      title: '删除文件夹',
      content: `确定删除文件夹「${folder.name}」吗？其中的文件和子文件夹不会被删除，将移至根目录。`,
      okText: '删除',
      okButtonProps: { type: 'danger', theme: 'solid' },
      onOk: () => deleteFolder.mutate(folder.id),
    });
  };

  const confirmDeleteFile = (file: KnowledgeFile) => (Modal as any).confirm({
    title: '删除文件',
    content: `确定删除「${file.name}」吗？此操作不可恢复。`,
    okText: '删除',
    okButtonProps: { type: 'danger', theme: 'solid' },
    onOk: () => deleteFile.mutate(file.id),
  });

  const confirmDeleteDoc = (doc: KnowledgeDocument) => (Modal as any).confirm({
    title: '删除文档',
    content: `确定删除「${doc.title}」及其版本历史吗？`,
    okText: '删除',
    okButtonProps: { type: 'danger', theme: 'solid' },
    onOk: () => deleteDocument.mutate(doc.id),
  });

  // --- Preview ---
  const openPreview = (item: Item) => {
    setSelectedCard(item.kind === 'folder' ? `f-${item.folder.id}` : item.kind === 'document' ? item.document.id : item.file.id);
    setPreviewItem(item);
    if (item.kind === 'file') {
      setPreviewLoading(true);
      setPreviewData(null);
      get<KnowledgePreview>(`/knowledge/files/${item.file.id}/preview`)
        .then((data: KnowledgePreview) => { setPreviewData(data); setPreviewLoading(false); })
        .catch((e: Error) => { Toast.error(e.message); setPreviewLoading(false); });
    } else if (item.kind === 'document') {
      setPreviewData(null);
      setPreviewLoading(false);
    } else {
      setPreviewData(null);
      setPreviewLoading(false);
    }
  };

  const closePreview = () => {
    setPreviewItem(null);
    setPreviewData(null);
  };

  // Preview pane resize
  const startResize = (e: React.MouseEvent) => {
    e.preventDefault();
    previewResizeRef.current = { startX: e.clientX, startWidth: previewWidth };
    const onMove = (ev: MouseEvent) => {
      if (!previewResizeRef.current) return;
      const delta = previewResizeRef.current.startX - ev.clientX;
      setPreviewWidth(Math.max(280, Math.min(800, previewResizeRef.current.startWidth + delta)));
    };
    const onUp = () => { document.removeEventListener('mousemove', onMove); document.removeEventListener('mouseup', onUp); };
    document.addEventListener('mousemove', onMove);
    document.addEventListener('mouseup', onUp);
  };

  // --- Tree Rendering ---
  type TreeItem =
    | { kind: 'folder'; folder: TreeNode }
    | { kind: 'doc'; doc: KnowledgeDocument }
    | { kind: 'file'; file: KnowledgeFile };

  const compareTreeItems = (a: TreeItem, b: TreeItem): number => {
    const nameA = a.kind === 'folder' ? a.folder.name : a.kind === 'doc' ? a.doc.title : a.file.name;
    const nameB = b.kind === 'folder' ? b.folder.name : b.kind === 'doc' ? b.doc.title : b.file.name;
    return nameA.localeCompare(nameB, 'zh-CN');
  };

  const renderTreeLevel = (folderId: string | null, childFolders: TreeNode[]): ReactNode => {
    const folderDocs = documents.filter(d => (d.folderId ?? null) === folderId);
    const folderFiles = allFiles.filter(f => (f.folderId ?? null) === folderId);
    const items: TreeItem[] = [
      ...childFolders.map(f => ({ kind: 'folder' as const, folder: f })),
      ...folderDocs.map(d => ({ kind: 'doc' as const, doc: d })),
      ...folderFiles.map(f => ({ kind: 'file' as const, file: f })),
    ];
    if (items.length === 0) return null;
    items.sort(compareTreeItems);

    return items.map(item => {
      if (item.kind === 'folder') {
        const node = item.folder;
        const subChildren = renderTreeLevel(node.id, node.children);
        const hasChildren = subChildren !== null;
        return (
          <div
            className={`kb-tree-branch${collapsed.has(node.id) ? ' collapsed' : ''}`}
            key={node.id}
            onContextMenu={e => showCtx(e, [
              { label: '新建文件夹', icon: <IconPlus size="small" />, onClick: () => { setNewFolderName(''); setNewFolderParentId(node.id); setNewFolderOpen(true); } },
              { label: '新建文档', icon: <IconArticle size="small" />, onClick: () => { setNewFolderParentId(node.id); createDocument.mutate(node.id); } },
              { label: '重命名', icon: <IconEdit size="small" />, onClick: () => { /* TODO */ Toast.info('重命名功能开发中'); } },
              { label: '删除', icon: <IconDelete size="small" />, danger: true, onClick: () => confirmDeleteFolder(node) },
            ])}
          >
            <button
              className={`kb-tree-row${current === node.id ? ' selected' : ''}`}
              onClick={() => openFolder(node.id)}
            >
              <span className={`kb-chev${hasChildren ? '' : ' hidden'}`} onClick={e => { e.stopPropagation(); toggle(node.id); }}>
                <IconChevronDown size="small" />
              </span>
              <IconFolder size="small" />
              <span className="kb-tree-name" title={node.name}>{node.name}</span>
            </button>
            {hasChildren && <div className="kb-tree-children">{subChildren}</div>}
          </div>
        );
      }
      if (item.kind === 'doc') {
        const doc = item.doc;
        return (
          <button
            key={`doc-${doc.id}`}
            className={`kb-tree-row leaf${selectedCard === doc.id ? ' selected' : ''}`}
            onClick={() => setSelectedCard(doc.id)}
            onDoubleClick={() => openEditDocument(doc.id)}
            onContextMenu={e => showCtx(e, [
              { label: '编辑', icon: <IconEdit size="small" />, onClick: () => openEditDocument(doc.id) },
              { label: '删除', icon: <IconDelete size="small" />, danger: true, onClick: () => confirmDeleteDoc(doc) },
            ])}
            title={doc.title}
          >
            <span className="kb-chev hidden"><IconChevronDown size="small" /></span>
            <IconArticle size="small" />
            <span className="kb-tree-name">{doc.title}</span>
          </button>
        );
      }
      // file
      const file = item.file;
      return (
        <button
          key={`file-${file.id}`}
          className={`kb-tree-row leaf${selectedCard === file.id ? ' selected' : ''}`}
          onClick={() => setSelectedCard(file.id)}
          onDoubleClick={() => openPreview({ kind: 'file', file })}
          onContextMenu={e => showCtx(e, [
            { label: '预览', icon: <IconFile size="small" />, onClick: () => openPreview({ kind: 'file', file }) },
            { label: '重命名', icon: <IconEdit size="small" />, onClick: () => { setRenameTarget(file); setRenameValue(file.name); } },
            { label: '删除', icon: <IconDelete size="small" />, danger: true, onClick: () => confirmDeleteFile(file) },
          ])}
          title={file.name}
        >
          <span className="kb-chev hidden"><IconChevronDown size="small" /></span>
          <IconFile size="small" />
          <span className="kb-tree-name">{file.name}</span>
        </button>
      );
    });
  };

  // --- File/Document Cards ---
  const renderCard = (item: Item): ReactNode => {
    if (item.kind === 'folder') {
      const f = item.folder;
      return (
        <article
          key={`f-${f.id}`}
          className={`kb-file-card${selectedCard === `f-${f.id}` ? ' selected' : ''}`}
          tabIndex={0}
          onClick={() => setSelectedCard(`f-${f.id}`)}
          onDoubleClick={() => openFolder(f.id)}
          onContextMenu={e => showCtx(e, [
            { label: '打开', icon: <IconFolderStroked size="small" />, onClick: () => openFolder(f.id) },
            { label: '重命名', icon: <IconEdit size="small" />, onClick: () => { /* TODO */ } },
            { label: '删除', icon: <IconDelete size="small" />, danger: true, onClick: () => confirmDeleteFolder(f) },
          ])}
        >
          <div className="kb-visual"><div className="kb-folder-icon" /></div>
          <div className="kb-name" title={f.name}>{f.name}</div>
          <div className="kb-meta">文件夹 · {relTime(f.updatedAt)}</div>
        </article>
      );
    }
    if (item.kind === 'document') {
      const doc = item.document;
      return (
        <article
          className={`kb-file-card${selectedCard === doc.id ? ' selected' : ''}`}
          tabIndex={0}
          onClick={() => setSelectedCard(doc.id)}
          onDoubleClick={() => openEditDocument(doc.id)}
          onContextMenu={e => {
            e.preventDefault();
            showCtx(e, [
              { label: '预览', icon: <IconFile size="small" />, onClick: () => openPreview({ kind: 'document', document: doc }) },
              { label: '编辑', icon: <IconEdit size="small" />, onClick: () => openEditDocument(doc.id) },
              { label: '重命名', icon: <IconEdit size="small" />, onClick: () => { setRenameTarget(doc); setRenameValue(doc.title); } },
              { label: '移动到…', icon: <IconFolderStroked size="small" />, onClick: () => { setMoveTarget(doc); setMoveFolderId(doc.folderId ?? 'root'); } },
              { label: '删除', icon: <IconDelete size="small" />, danger: true, onClick: () => confirmDeleteDoc(doc) },
            ]);
          }}
        >
          <div className="kb-visual"><div className="kb-doc-icon md">M↓</div></div>
          <div className="kb-name" title={doc.title}>{doc.title}</div>
          <div className="kb-meta">文档 · V{doc.version} · {relTime(doc.updatedAt)}</div>
        </article>
      );
    }
    const file = item.file;
    const kind = docKind(file.ext);
    return (
      <article
        className={`kb-file-card${selectedCard === file.id ? ' selected' : ''}`}
        tabIndex={0}
        onClick={() => setSelectedCard(file.id)}
        onDoubleClick={() => openPreview(item)}
        onContextMenu={e => {
          e.preventDefault();
          showCtx(e, [
            { label: '预览', icon: <IconFile size="small" />, onClick: () => openPreview({ kind: 'file', file }) },
            { label: '重命名', icon: <IconEdit size="small" />, onClick: () => { setRenameTarget(file); setRenameValue(file.name); } },
            { label: '移动到…', icon: <IconFolderStroked size="small" />, onClick: () => { setMoveTarget(file); setMoveFolderId(file.folderId ?? 'root'); } },
            { label: '删除', icon: <IconDelete size="small" />, danger: true, onClick: () => confirmDeleteFile(file) },
          ]);
        }}
      >
        <div className="kb-visual"><div className={`kb-doc-icon ${kind.cls}`}>{kind.label}</div></div>
        <div className="kb-name" title={file.name}>{file.name}</div>
        <div className="kb-meta">{fmtSize(file.sizeBytes)} · {relTime(file.updatedAt)}</div>
      </article>
    );
  };

  const latest = useMemo(() => {
    const times = [
      ...folders.map(f => f.updatedAt),
      ...files.map(f => f.updatedAt),
      ...folderDocuments.map(d => d.updatedAt),
    ];
    return times.length ? times.sort().at(-1)! : null;
  }, [folders, files, folderDocuments]);

  // Loading state
  if (foldersQuery.isLoading && documentsQuery.isLoading) {
    return (
      <div className="kb-page">
        <div className="kb-page-head"><h1>知识库</h1><p className="kb-subtitle">集中管理研究资料、项目文档与团队知识。</p></div>
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: 300 }}>
          <Spin size="large" tip="正在加载知识库…" />
        </div>
      </div>
    );
  }

  return (
    <div className="kb-page">
      <div className="kb-page-head">
        <h1>知识库</h1>
        <p className="kb-subtitle">集中管理研究资料、项目文档与团队知识。</p>
      </div>

      {/* Edit Mode: full editor */}
      {viewMode === 'edit' && editingDocumentId ? (
        <div className="kb-workspace kb-workspace-editing">
          <KnowledgeEditorPage
            embedded
            documentId={editingDocumentId}
            onBack={closeEdit}
          />
        </div>
      ) : (
        /* Browse Mode: tree + file grid */
        <section className={`kb-workspace${previewItem ? ' has-preview' : ''}`}>
          <aside className="kb-tree-pane">
            <div className="kb-pane-header">
              <span className="kb-pane-title">知识树</span>
              <div className="kb-pane-actions">
                <button className="kb-icon-btn" aria-label="新建文件夹" title="新建文件夹" onClick={() => { setNewFolderName(''); setNewFolderParentId(current); setNewFolderOpen(true); }}>
                  <IconPlus size="small" />
                </button>
                <button className={`kb-icon-btn${spinning ? ' kb-spin' : ''}`} aria-label="刷新知识树" title="刷新知识树" onClick={refresh}>
                  <IconRefresh size="small" />
                </button>
                <button className="kb-icon-btn" aria-label="全部折叠" title="全部折叠" onClick={collapseAll}>
                  <IconChevronLeft size="small" />
                </button>
              </div>
            </div>
            <div className="kb-tree-scroll">
              <div className={`kb-tree-branch${collapsed.has('root') ? ' collapsed' : ''}`}>
                <button
                  className={`kb-tree-row${current === null ? ' selected' : ''}`}
                  onClick={() => openFolder(null)}
                  onContextMenu={e => showCtx(e, [
                    { label: '新建文件夹', icon: <IconFolderStroked size="small" />, onClick: () => { setNewFolderName(''); setNewFolderParentId(null); setNewFolderOpen(true); } },
                    { label: '新建文档', icon: <IconArticle size="small" />, onClick: () => createDocument.mutate(null) },
                  ])}
                >
                  <span
                    className={`kb-chev${tree.length > 0 || allFiles.some(f => !f.folderId) || documents.some(d => !d.folderId) ? '' : ' hidden'}`}
                    onClick={e => { e.stopPropagation(); toggle('root'); }}
                  >
                    <IconChevronDown size="small" />
                  </span>
                  <IconFolderStroked size="small" />
                  <span className="kb-tree-name">我的知识库</span>
                </button>
                <div className="kb-tree-children">
                  {renderTreeLevel(null, tree)}
                </div>
              </div>
            </div>
          </aside>

          <section className="kb-content-pane">
            <div className="kb-content-toolbar">
              <div className="kb-breadcrumb">
                <IconFolderStroked size="small" />
                <button
                  className="kb-breadcrumb-link"
                  onClick={() => openFolder(null)}
                  style={{ fontWeight: current === null ? 650 : 400, color: current === null ? 'var(--text-1)' : undefined }}
                >
                  知识库
                </button>
                {breadcrumb.map((part, i) => {
                  const isLast = i === breadcrumb.length - 1;
                  return (
                    <span key={part.id} style={{ display: 'inline-flex', alignItems: 'center', gap: 10 }}>
                      <span className="kb-breadcrumb-sep">/</span>
                      <button
                        className="kb-breadcrumb-link"
                        onClick={() => openFolder(part.id)}
                        style={{
                          fontWeight: isLast ? 650 : 400,
                          color: isLast ? 'var(--text-1)' : undefined,
                          maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis',
                        }}
                        title={isLast ? part.name : `跳转到「${part.name}」`}
                      >
                        {part.name}
                      </button>
                    </span>
                  );
                })}
              </div>
              <div className="kb-toolbar-right">
                <label className="kb-search">
                  <Input value={keyword} onChange={setKeyword} placeholder="搜索文件或文件夹" suffix={<IconSearch />} />
                </label>
                <div className="kb-segmented" aria-label="视图切换">
                  <button className={`kb-icon-btn${view === 'grid' ? ' active' : ''}`} aria-label="网格视图" title="网格视图" onClick={() => setView('grid')}>
                    <IconGridRectangle size="small" />
                  </button>
                  <button className={`kb-icon-btn${view === 'list' ? ' active' : ''}`} aria-label="列表视图" title="列表视图" onClick={() => setView('list')}>
                    <IconList size="small" />
                  </button>
                </div>
                <button className="kb-icon-btn" aria-label="排序" title={ascending ? '按名称升序' : '按名称降序'} onClick={() => { setAscending(a => !a); }}>
                  {ascending ? <IconArrowUp size="small" /> : <IconArrowDown size="small" />}
                </button>
                <div className="kb-new-wrap">
                  <Dropdown
                    trigger="click"
                    position="bottomRight"
                    render={
                      <Dropdown.Menu>
                        <Dropdown.Item icon={<IconFolderStroked />} onClick={() => { setNewFolderName(''); setNewFolderParentId(current); setNewFolderOpen(true); }}>新建文件夹</Dropdown.Item>
                        <Dropdown.Item icon={<IconFile />} onClick={() => setUploadOpen(true)}>上传文件</Dropdown.Item>
                        <Dropdown.Item icon={<IconArticle />} onClick={() => createDocument.mutate(current)}>新建文档</Dropdown.Item>
                      </Dropdown.Menu>
                    }
                  >
                    <button className="kb-new-btn semi-button semi-button-solid">
                      <IconPlus size="small" /><span>新建</span><IconChevronDown size="small" />
                    </button>
                  </Dropdown>
                </div>
              </div>
            </div>
            <div className="kb-content-meta">
              <span>共 {items.length} 项{latest ? ` · 更新于 ${relTime(latest)}` : ''}</span>
            </div>
            <div className="kb-file-area">
              {items.length > 0
                ? <div className={`kb-file-grid${view === 'list' ? ' list-view' : ''}`}>{items.map(renderCard)}</div>
                : <div className="kb-empty">没有匹配的文件或文件夹</div>}
            </div>
          </section>

          {/* Preview Panel */}
          {previewItem && (
            <aside className="kb-preview-pane" style={{ width: previewWidth, minWidth: previewWidth, maxWidth: previewWidth }}>
              <div className="kb-preview-resize-handle" onMouseDown={startResize} />
              <div className="kb-preview-header">
                <div className="kb-preview-file-info">
                  <strong>{previewItem.kind === 'folder' ? previewItem.folder.name : previewItem.kind === 'document' ? previewItem.document.title : previewItem.file.name}</strong>
                  <div className="kb-preview-meta">
                    {previewItem.kind === 'folder' && `文件夹 · ${relTime(previewItem.folder.updatedAt)}`}
                    {previewItem.kind === 'document' && `文档 · V${previewItem.document.version} · ${relTime(previewItem.document.updatedAt)}`}
                    {previewItem.kind === 'file' && `${previewItem.file.ext.toUpperCase()} · ${fmtSize(previewItem.file.sizeBytes)} · ${relTime(previewItem.file.updatedAt)}`}
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 6, flexShrink: 0 }}>
                  {previewItem.kind === 'document' && (
                    <button className="kb-preview-doc-edit-btn" onClick={() => openEditDocument(previewItem.document.id)}>
                      <IconEdit size="small" />编辑
                    </button>
                  )}
                  {previewItem.kind === 'file' && previewData && (
                    <a className="kb-preview-download" href={`/api/v1/knowledge/files/${previewItem.file.id}/download`} download>
                      下载
                    </a>
                  )}
                  <button className="kb-icon-btn" aria-label="关闭预览" title="关闭预览" onClick={closePreview} style={{ border: '1px solid var(--border)' }}>
                    <IconChevronLeft size="small" style={{ transform: 'rotate(180deg)' }} />
                  </button>
                </div>
              </div>
              <div className="kb-preview-body">
                {previewItem.kind === 'folder' && (
                  <div className="kb-preview-text" style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-3)' }}>
                    <div style={{ textAlign: 'center' }}>
                      <IconFolderStroked size="large" style={{ fontSize: 48, marginBottom: 12, opacity: 0.3 }} />
                      <p>双击打开文件夹查看内容</p>
                      <p style={{ fontSize: 12, marginTop: 4 }}>共 {folders.filter(f => (f.parentId ?? null) === previewItem.folder.id).length + folderDocuments.filter(d => (d.folderId ?? null) === previewItem.folder.id).length + files.filter(f => (f.folderId ?? null) === previewItem.folder.id).length} 项</p>
                    </div>
                  </div>
                )}
                {previewItem.kind === 'document' && (
                  <div className="kb-preview-doc">
                    <div className="kb-preview-doc-head">
                      <div className="kb-preview-doc-icon">M↓</div>
                      <div>
                        <strong style={{ display: 'block', fontSize: 13 }}>{previewItem.document.title}</strong>
                        <div className="kb-preview-doc-meta">
                          <span>V{previewItem.document.version}</span><i />
                          <span>{relTime(previewItem.document.updatedAt)}更新</span>
                        </div>
                      </div>
                      <div className="kb-preview-doc-actions">
                        <button className="kb-preview-doc-edit-btn" onClick={() => openEditDocument(previewItem.document.id)}>
                          <IconEdit size="small" />编辑
                        </button>
                      </div>
                    </div>
                    <div className="kb-preview-doc-scroll">
                      <div className="kb-preview-doc-paper">
                        <div
                          className="kb-preview-doc-body"
                          dangerouslySetInnerHTML={{ __html: previewItem.document.contentHtml || '<p style="color:var(--text-3)">空白文档</p>' }}
                        />
                      </div>
                    </div>
                  </div>
                )}
                {previewItem.kind === 'file' && previewLoading && (
                  <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
                    <Spin tip="正在加载预览…" />
                  </div>
                )}
                {previewItem.kind === 'file' && !previewLoading && previewData && previewData.kind === 'text' && (
                  <pre className="kb-preview-text"><code>{previewData.content}</code></pre>
                )}
                {previewItem.kind === 'file' && !previewLoading && previewData && previewData.kind === 'image' && (
                  <div className="kb-preview-image">
                    <img src={`data:${previewData.mimeType};base64,${previewData.contentBase64}`} alt={previewData.name} />
                  </div>
                )}
                {previewItem.kind === 'file' && !previewLoading && previewData && previewData.kind === 'pdf' && (
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: 14, color: 'var(--text-2)' }}>
                    <IconFile size="large" style={{ fontSize: 48, opacity: 0.3 }} />
                    <span>PDF 文件暂不支持在线预览</span>
                    <a className="kb-preview-download" href={`/api/v1/knowledge/files/${previewItem.file.id}/download`} download>
                      <IconChevronLeft size="small" style={{ transform: 'rotate(-90deg)' }} />下载查看
                    </a>
                  </div>
                )}
                {previewItem.kind === 'file' && !previewLoading && previewData && previewData.kind === 'binary' && (
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', gap: 14, color: 'var(--text-2)' }}>
                    <IconFile size="large" style={{ fontSize: 48, opacity: 0.3 }} />
                    <span>二进制文件不支持预览</span>
                    <a className="kb-preview-download" href={`/api/v1/knowledge/files/${previewItem.file.id}/download`} download>
                      <IconChevronLeft size="small" style={{ transform: 'rotate(-90deg)' }} />下载文件
                    </a>
                  </div>
                )}
              </div>
            </aside>
          )}
        </section>
      )}

      {/* Context Menu Overlay */}
      {ctxMenu && (
        <>
          <div className="kb-context-backdrop" onClick={hideCtx} ref={ctxBackdropRef} />
          <div className="kb-context-menu" style={{ left: ctxMenu.x, top: ctxMenu.y }}>
            <div className="kb-context-menu-inner">
              {ctxMenu.items.map((item, i) => (
                <button
                  key={i}
                  className={`kb-context-item${item.danger ? ' danger' : ''}`}
                  onClick={() => { item.onClick(); hideCtx(); }}
                >
                  {item.icon}
                  {item.label}
                </button>
              ))}
            </div>
          </div>
        </>
      )}

      {/* Modals */}
      <Modal
        title="新建文件夹"
        visible={newFolderOpen}
        onCancel={() => setNewFolderOpen(false)}
        onOk={() => { if (newFolderName.trim()) createFolder.mutate(newFolderParentId); else Toast.warning('请输入文件夹名称'); }}
        okText="创建"
        cancelText="取消"
        confirmLoading={createFolder.isPending}
      >
        <label className="field-label">文件夹名称</label>
        <Input value={newFolderName} onChange={setNewFolderName} placeholder="请输入名称" showClear onEnterPress={() => { if (newFolderName.trim()) createFolder.mutate(newFolderParentId); }} />
      </Modal>

      <Modal
        title={renameTarget && 'ext' in renameTarget ? '重命名文件' : '重命名文档'}
        visible={!!renameTarget}
        onCancel={() => setRenameTarget(null)}
        onOk={async () => {
          if (!renameValue.trim()) { Toast.warning('请输入名称'); return; }
          if (renameTarget && 'ext' in renameTarget) {
            renameFile.mutate({ id: renameTarget.id, name: renameValue });
          } else if (renameTarget) {
            try {
              const doc = await get<KnowledgeDocument>(`/knowledge/documents/${renameTarget.id}`);
              await put(`/knowledge/documents/${renameTarget.id}`, {
                folderId: doc.folderId ?? null,
                title: renameValue,
                contentHtml: doc.contentHtml,
                drawingJson: doc.drawingJson,
                expectedVersion: doc.version,
                changeSummary: '重命名文档',
              });
              invalidate();
              setRenameTarget(null);
              Toast.success('已重命名');
            } catch (e) { Toast.error(e instanceof Error ? e.message : '重命名失败'); }
          }
        }}
        okText="保存"
        cancelText="取消"
        confirmLoading={renameFile.isPending}
      >
        <label className="field-label">{renameTarget && 'ext' in renameTarget ? '文件名称' : '文档标题'}</label>
        <Input value={renameValue} onChange={setRenameValue} placeholder="请输入新名称" showClear />
      </Modal>

      <Modal
        title="移动到…"
        visible={!!moveTarget}
        onCancel={() => setMoveTarget(null)}
        onOk={() => {
          if (!moveTarget) return;
          const targetFolderId = moveFolderId === 'root' ? null : moveFolderId;
          if ('ext' in moveTarget) {
            moveFile.mutate({ id: moveTarget.id, folderId: targetFolderId });
          } else {
            get<KnowledgeDocument>(`/knowledge/documents/${moveTarget.id}`).then(doc =>
              put(`/knowledge/documents/${moveTarget.id}`, {
                folderId: targetFolderId,
                title: doc.title,
                contentHtml: doc.contentHtml,
                drawingJson: doc.drawingJson,
                expectedVersion: doc.version,
                changeSummary: '移动文档',
              }),
            ).then(() => { invalidate(); setMoveTarget(null); Toast.success('已移动'); }).catch((e: Error) => Toast.error(e.message));
          }
        }}
        okText="移动"
        cancelText="取消"
        confirmLoading={moveFile.isPending}
      >
        <label className="field-label">目标位置</label>
        <TreeSelect
          style={{ width: '100%' }}
          value={moveFolderId}
          onChange={v => setMoveFolderId(String(v))}
          treeData={[{ label: '我的知识库', value: 'root', key: 'root', children: toTreeData(tree) }]}
          defaultExpandAll
        />
      </Modal>

      <Modal
        title="上传文件"
        visible={uploadOpen}
        onCancel={closeUpload}
        footer={uploadResults ? (
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <Button onClick={() => { resetUpload(); }}>继续上传</Button>
              <Button theme="solid" type="primary" onClick={closeUpload}>完成</Button>
            </div>
          ) : (
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <Button onClick={closeUpload} disabled={uploading}>取消</Button>
              {uploadFiles.length > 0 ? (
                <Button theme="solid" type="primary" onClick={startUpload} loading={uploading}>
                  上传 {uploadFiles.length} 个文件
                </Button>
              ) : null}
            </div>
          ) as ReactNode}
        width={620}
      >
        {/* Upload Results */}
        {uploadResults && (
          <div style={{ marginBottom: 16 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 12, color: 'var(--text-2)', fontSize: 13 }}>
              <span style={{ color: 'var(--success)', fontWeight: 600 }}>
                {uploadResults.filter(r => r.success).length} 成功
              </span>
              {uploadResults.some(r => !r.success) && (
                <span style={{ color: '#e5484d', fontWeight: 600 }}>
                  {uploadResults.filter(r => !r.success).length} 失败
                </span>
              )}
            </div>
            <div style={{ maxHeight: 220, overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 4 }}>
              {uploadResults.map((result, i) => (
                <div key={i} style={{
                  display: 'flex', alignItems: 'center', gap: 10, padding: '8px 12px',
                  borderRadius: 'var(--radius)', fontSize: 12,
                  background: result.success ? '#f0fdf6' : '#fef2f2',
                  border: `1px solid ${result.success ? '#bbf7d0' : '#fecaca'}`,
                }}>
                  <span style={{
                    width: 20, height: 20, borderRadius: '50%', display: 'inline-grid', placeItems: 'center',
                    background: result.success ? '#22c55e' : '#ef4444', color: '#fff', fontSize: 11, fontWeight: 700, flexShrink: 0,
                  }}>
                    {result.success ? '✓' : '✗'}
                  </span>
                  <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'var(--text-1)' }}>
                    {result.name}
                  </span>
                  {result.error && <span style={{ color: '#dc2626', whiteSpace: 'nowrap' }}>{result.error}</span>}
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Upload progress bar */}
        {uploading && (
          <div style={{ marginBottom: 16 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 6, fontSize: 12, color: 'var(--text-2)' }}>
              <span>正在上传…</span>
              <span>{uploadProgress}%</span>
            </div>
            <div style={{ height: 6, borderRadius: 3, background: '#e5e6eb', overflow: 'hidden' }}>
              <div style={{ height: '100%', width: `${uploadProgress}%`, background: 'var(--accent)', borderRadius: 3, transition: 'width .2s ease' }} />
            </div>
          </div>
        )}

        {!uploadResults && (
          <>
            {/* Drop zone */}
            <div
              ref={dropZoneRef}
              className={`kb-upload-drag${dragOver ? ' kb-upload-drag-over' : ''}`}
              style={{
                border: `2px dashed ${dragOver ? 'var(--accent)' : 'var(--border-strong)'}`,
                borderRadius: 'var(--radius)',
                padding: '32px 20px',
                textAlign: 'center',
                cursor: 'pointer',
                background: dragOver ? 'var(--accent-soft)' : '#fafbfc',
                transition: 'border-color .15s, background .15s',
                marginBottom: uploadFiles.length > 0 ? 16 : 0,
                minHeight: uploadFiles.length > 0 ? 'auto' : 120,
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                gap: 8,
              }}
              onClick={() => uploadInputRef.current?.click()}
              onDragOver={(e) => { e.preventDefault(); setDragOver(true); }}
              onDragLeave={() => setDragOver(false)}
              onDrop={(e) => { e.preventDefault(); setDragOver(false); handleUploadFiles(e.dataTransfer.files); }}
            >
              <IconPlus size="large" style={{ fontSize: 28, color: dragOver ? 'var(--accent)' : 'var(--text-3)', opacity: 0.6 }} />
              <div>
                <span style={{ color: 'var(--accent)', fontWeight: 600, fontSize: 13 }}>点击选择</span>
                <span style={{ color: 'var(--text-3)', fontSize: 13 }}> 或拖拽文件到此处</span>
              </div>
              <span style={{ color: 'var(--text-3)', fontSize: 11 }}>
                支持 PDF、Office、Markdown、图片等各类文件
              </span>
              <input
                ref={uploadInputRef}
                type="file"
                multiple
                style={{ display: 'none' }}
                onChange={(e) => { if (e.target.files) handleUploadFiles(e.target.files); e.target.value = ''; }}
              />
            </div>

            {/* File list */}
            {uploadFiles.length > 0 && (
              <div style={{ maxHeight: 260, overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 6 }}>
                {uploadFiles.map((file, i) => {
                  const icon = fileTypeIcon(file.name);
                  return (
                    <div key={i} style={{
                      display: 'flex', alignItems: 'center', gap: 10, padding: '10px 12px',
                      borderRadius: 'var(--radius)', border: '1px solid var(--border)',
                      background: '#fff',
                    }}>
                      <div className={`kb-doc-icon ${icon.cls}`} style={{ width: 32, height: 38, fontSize: 10, flexShrink: 0 }}>
                        {icon.label}
                      </div>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontSize: 13, fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'var(--text-1)' }}>
                          {file.name}
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--text-3)', marginTop: 2 }}>
                          {formatFileSize(file.size)}
                        </div>
                      </div>
                      {!uploading && (
                        <button
                          style={{
                            width: 28, height: 28, border: '1px solid var(--border)', borderRadius: 'var(--radius)',
                            background: '#fff', cursor: 'pointer', display: 'grid', placeItems: 'center',
                            color: 'var(--text-3)', flexShrink: 0,
                          }}
                          onClick={(e) => { e.stopPropagation(); removeUploadFile(i); }}
                          title="移除文件"
                        >
                          <IconDelete size="small" />
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </>
        )}
      </Modal>
    </div>
  );
}
