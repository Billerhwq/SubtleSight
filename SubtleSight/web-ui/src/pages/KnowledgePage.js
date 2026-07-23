import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Dropdown, Input, Modal, Toast, TreeSelect, Upload } from '@douyinfe/semi-ui';
import { IconArrowDown, IconArrowUp, IconChevronDown, IconChevronLeft, IconDelete, IconEdit, IconFolder, IconFolderStroked, IconGridRectangle, IconList, IconPlus, IconRefresh, IconSearch } from '@douyinfe/semi-icons';
import { del, get, post, uploadKnowledgeFile } from '../api/client';
function buildTree(folders) {
    const map = new Map();
    folders.forEach(f => map.set(f.id, { ...f, children: [] }));
    const roots = [];
    map.forEach(node => { if (node.parentId && map.has(node.parentId))
        map.get(node.parentId).children.push(node);
    else
        roots.push(node); });
    return roots;
}
function toTreeData(nodes) {
    return nodes.map(n => ({ label: n.name, value: n.id, key: n.id, children: toTreeData(n.children) }));
}
function fmtSize(bytes) { if (bytes < 1024)
    return `${bytes} B`; const units = ['KB', 'MB', 'GB']; let v = bytes / 1024, i = 0; while (v >= 1024 && i < units.length - 1) {
    v /= 1024;
    i++;
} return `${v >= 100 ? v.toFixed(0) : v.toFixed(1)} ${units[i]}`; }
function relTime(iso) { const diff = Date.now() - new Date(iso).getTime(); const m = Math.floor(diff / 60000); if (m < 1)
    return '刚刚'; if (m < 60)
    return `${m} 分钟前`; const h = Math.floor(m / 60); if (h < 24)
    return '今天'; if (h < 48)
    return '昨天'; const d = Math.floor(h / 24); if (d < 30)
    return `${d} 天前`; return new Date(iso).toLocaleDateString(); }
function docKind(ext) { switch (ext) {
    case 'pdf': return { cls: 'pdf', label: 'PDF' };
    case 'xlsx':
    case 'xls':
    case 'csv': return { cls: 'xlsx', label: 'X' };
    case 'docx':
    case 'doc': return { cls: 'docx', label: 'W' };
    case 'md': return { cls: 'md', label: 'M↓' };
    case 'png':
    case 'jpg':
    case 'jpeg':
    case 'gif':
    case 'webp':
    case 'svg':
    case 'bmp': return { cls: 'image', label: 'IMG' };
    case 'txt':
    case 'log': return { cls: 'txt', label: 'TXT' };
    default: return { cls: 'txt', label: ext ? ext.slice(0, 3).toUpperCase() : 'FILE' };
} }
const itemName = (item) => item.kind === 'folder' ? item.folder.name : item.file.name;
export function KnowledgePage() {
    const client = useQueryClient();
    const [current, setCurrent] = useState(null);
    const [collapsed, setCollapsed] = useState(new Set());
    const [view, setView] = useState('grid');
    const [ascending, setAscending] = useState(true);
    const [keyword, setKeyword] = useState('');
    const [selectedCard, setSelectedCard] = useState(null);
    const [spinning, setSpinning] = useState(false);
    const [newFolderOpen, setNewFolderOpen] = useState(false);
    const [newFolderName, setNewFolderName] = useState('');
    const [uploadOpen, setUploadOpen] = useState(false);
    const [renameTarget, setRenameTarget] = useState(null);
    const [renameValue, setRenameValue] = useState('');
    const [moveTarget, setMoveTarget] = useState(null);
    const [moveFolderId, setMoveFolderId] = useState('root');
    const foldersQuery = useQuery({ queryKey: ['knowledge-folders'], queryFn: () => get('/knowledge/folders') });
    const filesQuery = useQuery({ queryKey: ['knowledge-files', current], queryFn: () => get(`/knowledge/files${current ? `?folderId=${current}` : ''}`) });
    const folders = useMemo(() => foldersQuery.data ?? [], [foldersQuery.data]);
    const files = useMemo(() => filesQuery.data ?? [], [filesQuery.data]);
    const tree = useMemo(() => buildTree(folders), [folders]);
    const folderMap = useMemo(() => new Map(folders.map(f => [f.id, f])), [folders]);
    const invalidate = () => { client.invalidateQueries({ queryKey: ['knowledge-folders'] }); client.invalidateQueries({ queryKey: ['knowledge-files'] }); };
    const createFolder = useMutation({ mutationFn: () => post('/knowledge/folders', { parentId: current, name: newFolderName }), onSuccess: () => { invalidate(); setNewFolderOpen(false); Toast.success('文件夹已创建'); }, onError: (e) => Toast.error(e.message) });
    const renameFile = useMutation({ mutationFn: (input) => post(`/knowledge/files/${input.id}/rename`, { name: input.name }), onSuccess: () => { invalidate(); setRenameTarget(null); Toast.success('已重命名'); }, onError: (e) => Toast.error(e.message) });
    const moveFile = useMutation({ mutationFn: (input) => post(`/knowledge/files/${input.id}/move`, { folderId: input.folderId }), onSuccess: () => { invalidate(); setMoveTarget(null); Toast.success('已移动'); }, onError: (e) => Toast.error(e.message) });
    const deleteFile = useMutation({ mutationFn: (id) => del(`/knowledge/files/${id}`), onSuccess: () => { invalidate(); Toast.success('已删除'); }, onError: (e) => Toast.error(e.message) });
    const breadcrumb = useMemo(() => { const parts = []; let id = current; while (id) {
        const f = folderMap.get(id);
        if (!f)
            break;
        parts.unshift(f.name);
        id = f.parentId ?? null;
    } return parts; }, [current, folderMap]);
    const items = useMemo(() => {
        const subfolders = folders.filter(f => (f.parentId ?? null) === current);
        const list = [...subfolders.map(folder => ({ kind: 'folder', folder })), ...files.map(file => ({ kind: 'file', file }))];
        const key = keyword.trim().toLowerCase();
        const filtered = key ? list.filter(item => itemName(item).toLowerCase().includes(key)) : list;
        return [...filtered].sort((a, b) => ascending ? itemName(a).localeCompare(itemName(b), 'zh-CN') : itemName(b).localeCompare(itemName(a), 'zh-CN'));
    }, [folders, files, current, keyword, ascending]);
    const toggle = (id) => setCollapsed(prev => { const next = new Set(prev); if (next.has(id))
        next.delete(id);
    else
        next.add(id); return next; });
    const openFolder = (id) => { setCurrent(id); setSelectedCard(null); };
    const collapseAll = () => { setCollapsed(new Set(folders.map(f => f.id))); Toast.success('知识树已折叠'); };
    const refresh = () => { setSpinning(true); invalidate(); setTimeout(() => setSpinning(false), 450); Toast.success('知识树已刷新'); };
    const confirmDelete = (file) => Modal.confirm({ title: '删除文件', content: `确定删除「${file.name}」吗？文件将从本地磁盘中移除，此操作不可恢复。`, okText: '删除', cancelText: '取消', okButtonProps: { type: 'danger', theme: 'solid' }, onOk: () => deleteFile.mutate(file.id) });
    const renderBranch = (nodes) => nodes.map(node => (_jsxs("div", { className: `kb-tree-branch${collapsed.has(node.id) ? ' collapsed' : ''}`, children: [_jsxs("button", { className: `kb-tree-row${current === node.id ? ' selected' : ''}`, onClick: () => openFolder(node.id), children: [_jsx("span", { className: `kb-chev${node.children.length ? '' : ' hidden'}`, onClick: e => { e.stopPropagation(); toggle(node.id); }, children: _jsx(IconChevronDown, { size: "small" }) }), _jsx(IconFolder, { size: "small" }), _jsx("span", { className: "kb-tree-name", title: node.name, children: node.name })] }), node.children.length > 0 && _jsx("div", { className: "kb-tree-children", children: renderBranch(node.children) })] }, node.id)));
    const fileMenu = (file) => (_jsxs(Dropdown.Menu, { children: [_jsx(Dropdown.Item, { icon: _jsx(IconFolderStroked, {}), onClick: () => { setMoveTarget(file); setMoveFolderId(file.folderId ?? 'root'); }, children: "\u79FB\u52A8\u5230\u2026" }), _jsx(Dropdown.Item, { icon: _jsx(IconEdit, {}), onClick: () => { setRenameTarget(file); setRenameValue(file.name); }, children: "\u91CD\u547D\u540D" }), _jsx(Dropdown.Item, { icon: _jsx(IconDelete, {}), type: "danger", onClick: () => confirmDelete(file), children: "\u5220\u9664" })] }));
    const renderCard = (item) => {
        if (item.kind === 'folder') {
            const f = item.folder;
            return _jsxs("article", { className: `kb-file-card${selectedCard === `f-${f.id}` ? ' selected' : ''}`, tabIndex: 0, onClick: () => setSelectedCard(`f-${f.id}`), onDoubleClick: () => openFolder(f.id), children: [_jsx("div", { className: "kb-visual", children: _jsx("div", { className: "kb-folder-icon" }) }), _jsx("div", { className: "kb-name", title: f.name, children: f.name }), _jsxs("div", { className: "kb-meta", children: ["\u6587\u4EF6\u5939 \u00B7 ", relTime(f.updatedAt)] })] }, `f-${f.id}`);
        }
        const file = item.file;
        const kind = docKind(file.ext);
        return _jsx(Dropdown, { trigger: "contextMenu", position: "rightTop", render: fileMenu(file), children: _jsxs("article", { className: `kb-file-card${selectedCard === file.id ? ' selected' : ''}`, tabIndex: 0, onClick: () => setSelectedCard(file.id), children: [_jsx("div", { className: "kb-visual", children: _jsx("div", { className: `kb-doc-icon ${kind.cls}`, children: kind.label }) }), _jsx("div", { className: "kb-name", title: file.name, children: file.name }), _jsxs("div", { className: "kb-meta", children: [fmtSize(file.sizeBytes), " \u00B7 ", relTime(file.updatedAt)] })] }) }, file.id);
    };
    const latest = useMemo(() => { const times = [...folders.map(f => f.updatedAt), ...files.map(f => f.updatedAt)]; return times.length ? times.sort().at(-1) : null; }, [folders, files]);
    return _jsxs("div", { className: "kb-page", children: [_jsxs("div", { className: "kb-page-head", children: [_jsx("h1", { children: "\u77E5\u8BC6\u5E93" }), _jsx("p", { className: "kb-subtitle", children: "\u96C6\u4E2D\u7BA1\u7406\u7814\u7A76\u8D44\u6599\u3001\u9879\u76EE\u6587\u6863\u4E0E\u56E2\u961F\u77E5\u8BC6\u3002" })] }), _jsxs("section", { className: "kb-workspace", children: [_jsxs("aside", { className: "kb-tree-pane", children: [_jsxs("div", { className: "kb-pane-header", children: [_jsx("span", { className: "kb-pane-title", children: "\u77E5\u8BC6\u6811" }), _jsxs("div", { className: "kb-pane-actions", children: [_jsx("button", { className: "kb-icon-btn", "aria-label": "\u65B0\u5EFA\u6587\u4EF6\u5939", title: "\u65B0\u5EFA\u6587\u4EF6\u5939", onClick: () => { setNewFolderName(''); setNewFolderOpen(true); }, children: _jsx(IconPlus, { size: "small" }) }), _jsx("button", { className: `kb-icon-btn${spinning ? ' kb-spin' : ''}`, "aria-label": "\u5237\u65B0\u77E5\u8BC6\u6811", title: "\u5237\u65B0\u77E5\u8BC6\u6811", onClick: refresh, children: _jsx(IconRefresh, { size: "small" }) }), _jsx("button", { className: "kb-icon-btn", "aria-label": "\u5168\u90E8\u6298\u53E0", title: "\u5168\u90E8\u6298\u53E0", onClick: collapseAll, children: _jsx(IconChevronLeft, { size: "small" }) })] })] }), _jsx("div", { className: "kb-tree-scroll", children: _jsxs("div", { className: "kb-tree-branch", children: [_jsxs("button", { className: `kb-tree-row${current === null ? ' selected' : ''}`, onClick: () => openFolder(null), children: [_jsx("span", { className: "kb-chev", children: _jsx(IconChevronDown, { size: "small" }) }), _jsx(IconFolderStroked, { size: "small" }), _jsx("span", { className: "kb-tree-name", children: "\u6211\u7684\u77E5\u8BC6\u5E93" })] }), _jsx("div", { className: "kb-tree-children", children: renderBranch(tree) })] }) })] }), _jsxs("section", { className: "kb-content-pane", children: [_jsxs("div", { className: "kb-content-toolbar", children: [_jsxs("div", { className: "kb-breadcrumb", children: [_jsx(IconFolderStroked, { size: "small" }), _jsx("span", { children: "\u77E5\u8BC6\u5E93" }), breadcrumb.map((part, i) => _jsxs("span", { style: { display: 'inline-flex', alignItems: 'center', gap: 10 }, children: [_jsx("span", { children: "/" }), i === breadcrumb.length - 1 ? _jsx("strong", { children: part }) : _jsx("span", { children: part })] }, i))] }), _jsxs("div", { className: "kb-toolbar-right", children: [_jsx("label", { className: "kb-search", children: _jsx(Input, { value: keyword, onChange: setKeyword, placeholder: "\u641C\u7D22\u6587\u4EF6\u6216\u6587\u4EF6\u5939", suffix: _jsx(IconSearch, {}) }) }), _jsxs("div", { className: "kb-segmented", "aria-label": "\u89C6\u56FE\u5207\u6362", children: [_jsx("button", { className: `kb-icon-btn${view === 'grid' ? ' active' : ''}`, "aria-label": "\u7F51\u683C\u89C6\u56FE", title: "\u7F51\u683C\u89C6\u56FE", onClick: () => setView('grid'), children: _jsx(IconGridRectangle, { size: "small" }) }), _jsx("button", { className: `kb-icon-btn${view === 'list' ? ' active' : ''}`, "aria-label": "\u5217\u8868\u89C6\u56FE", title: "\u5217\u8868\u89C6\u56FE", onClick: () => setView('list'), children: _jsx(IconList, { size: "small" }) })] }), _jsx("button", { className: "kb-icon-btn", "aria-label": "\u6392\u5E8F", title: ascending ? '按名称升序' : '按名称降序', onClick: () => { setAscending(a => !a); Toast.success(ascending ? '已按名称降序排列' : '已按名称升序排列'); }, children: ascending ? _jsx(IconArrowUp, { size: "small" }) : _jsx(IconArrowDown, { size: "small" }) }), _jsx("div", { className: "kb-new-wrap", children: _jsx(Dropdown, { trigger: "click", position: "bottomRight", render: _jsxs(Dropdown.Menu, { children: [_jsx(Dropdown.Item, { onClick: () => { setNewFolderName(''); setNewFolderOpen(true); }, children: "\u65B0\u5EFA\u6587\u4EF6\u5939" }), _jsx(Dropdown.Item, { onClick: () => setUploadOpen(true), children: "\u4E0A\u4F20\u6587\u4EF6" })] }), children: _jsxs("button", { className: "kb-new-btn semi-button semi-button-solid", children: [_jsx(IconPlus, { size: "small" }), _jsx("span", { children: "\u65B0\u5EFA" }), _jsx(IconChevronDown, { size: "small" })] }) }) })] })] }), _jsx("div", { className: "kb-content-meta", children: _jsxs("span", { children: ["\u5171 ", items.length, " \u9879", latest ? ` · 更新于 ${relTime(latest)}` : ''] }) }), _jsx("div", { className: "kb-file-area", children: items.length > 0
                                    ? _jsx("div", { className: `kb-file-grid${view === 'list' ? ' list-view' : ''}`, children: items.map(renderCard) })
                                    : _jsx("div", { className: "kb-empty", children: "\u6CA1\u6709\u5339\u914D\u7684\u6587\u4EF6\u6216\u6587\u4EF6\u5939" }) })] })] }), _jsxs(Modal, { title: "\u65B0\u5EFA\u6587\u4EF6\u5939", visible: newFolderOpen, onCancel: () => setNewFolderOpen(false), onOk: () => { if (newFolderName.trim())
                    createFolder.mutate();
                else
                    Toast.warning('请输入文件夹名称'); }, okText: "\u521B\u5EFA", cancelText: "\u53D6\u6D88", confirmLoading: createFolder.isPending, children: [_jsx("label", { className: "field-label", children: "\u6587\u4EF6\u5939\u540D\u79F0" }), _jsx(Input, { value: newFolderName, onChange: setNewFolderName, placeholder: "\u8BF7\u8F93\u5165\u540D\u79F0", showClear: true, onEnterPress: () => { if (newFolderName.trim())
                            createFolder.mutate(); } })] }), _jsxs(Modal, { title: "\u91CD\u547D\u540D\u6587\u4EF6", visible: !!renameTarget, onCancel: () => setRenameTarget(null), onOk: () => { if (renameValue.trim())
                    renameFile.mutate({ id: renameTarget.id, name: renameValue });
                else
                    Toast.warning('请输入文件名称'); }, okText: "\u4FDD\u5B58", cancelText: "\u53D6\u6D88", confirmLoading: renameFile.isPending, children: [_jsx("label", { className: "field-label", children: "\u6587\u4EF6\u540D\u79F0" }), _jsx(Input, { value: renameValue, onChange: setRenameValue, placeholder: "\u8BF7\u8F93\u5165\u65B0\u540D\u79F0", showClear: true })] }), _jsxs(Modal, { title: "\u79FB\u52A8\u6587\u4EF6", visible: !!moveTarget, onCancel: () => setMoveTarget(null), onOk: () => moveFile.mutate({ id: moveTarget.id, folderId: moveFolderId === 'root' ? null : moveFolderId }), okText: "\u79FB\u52A8", cancelText: "\u53D6\u6D88", confirmLoading: moveFile.isPending, children: [_jsx("label", { className: "field-label", children: "\u76EE\u6807\u4F4D\u7F6E" }), _jsx(TreeSelect, { style: { width: '100%' }, value: moveFolderId, onChange: v => setMoveFolderId(String(v)), treeData: [{ label: '我的知识库', value: 'root', key: 'root', children: toTreeData(tree) }], defaultExpandAll: true })] }), _jsx(Modal, { title: "\u4E0A\u4F20\u6587\u4EF6", visible: uploadOpen, onCancel: () => setUploadOpen(false), footer: null, width: 560, children: _jsx(Upload, { draggable: true, multiple: true, action: "", dragMainText: "\u70B9\u51FB\u6216\u62D6\u62FD\u6587\u4EF6\u5230\u6B64\u5904\u4E0A\u4F20", dragSubText: "\u652F\u6301 PDF\u3001Office\u3001Markdown\u3001\u56FE\u7247\u7B49\u5404\u7C7B\u6587\u4EF6\uFF0C\u591A\u6587\u4EF6\u5E76\u884C\u4E0A\u4F20", customRequest: ({ fileInstance, onProgress, onSuccess, onError }) => {
                        uploadKnowledgeFile(current, fileInstance, percent => onProgress({ total: 100, loaded: percent }))
                            .then(() => { onSuccess({}); client.invalidateQueries({ queryKey: ['knowledge-files'] }); })
                            .catch((e) => { onError({ status: 0 }); Toast.error(`上传失败：${e.message}`); });
                    } }) })] });
}
export default KnowledgePage;
