import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import { lazy, Suspense, useEffect } from 'react';
import { HashRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Layout, Nav, Spin } from '@douyinfe/semi-ui';
import { IconArticle, IconBolt, IconFolderStroked, IconHome, IconList, IconMenu, IconSearch, IconSetting } from '@douyinfe/semi-icons';
import { get } from './api/client';
import { useUi } from './store/ui';
const DiscoveryPage = lazy(() => import('./pages/DiscoveryPage'));
const StoryPage = lazy(() => import('./pages/StoryPage'));
const ResearchPage = lazy(() => import('./pages/ResearchPage'));
const WatchlistPage = lazy(() => import('./pages/WatchlistPage'));
const KnowledgePage = lazy(() => import('./pages/KnowledgePage'));
const ReportsPage = lazy(() => import('./pages/ReportsPage'));
const AdminPage = lazy(() => import('./pages/AdminPage'));
const AgentDrawer = lazy(() => import('./components/AgentDrawer'));
export default function App() { return _jsx(HashRouter, { children: _jsx(AuthenticatedApp, {}) }); }
function AuthenticatedApp() { const query = useQuery({ queryKey: ['auth'], queryFn: () => get('/auth/status') }); if (query.isLoading)
    return _jsx("div", { className: "center-screen", children: _jsx(Spin, { size: "large", tip: "\u6B63\u5728\u5524\u9192 SubtleSight\u2026" }) }); return _jsx(Workspace, { user: query.data?.user ?? 'local' }); }
function Workspace({ user }) {
    const location = useLocation(), navigate = useNavigate(), client = useQueryClient();
    const { collapsed, setCollapsed, setAgentOpen } = useUi();
    useEffect(() => { const events = new EventSource('/api/v1/events'); for (const name of ['story.updated', 'research.requested', 'research.completed', 'discovery.completed'])
        events.addEventListener(name, () => client.invalidateQueries()); events.onerror = () => { }; return () => events.close(); }, [client]);
    const items = [{ itemKey: '/discover', text: '发现', icon: _jsx(IconHome, {}) }, { itemKey: '/stories', text: '事件', icon: _jsx(IconList, {}) }, { itemKey: '/research', text: '研究', icon: _jsx(IconSearch, {}) }, { itemKey: '/watchlists', text: '跟踪', icon: _jsx(IconArticle, {}) }, { itemKey: '/knowledge', text: '知识库', icon: _jsx(IconFolderStroked, {}) }, { itemKey: '/reports', text: '报告', icon: _jsx(IconArticle, {}) }, { itemKey: '/admin', text: '系统管理', icon: _jsx(IconSetting, {}) }];
    const selected = '/' + location.pathname.split('/')[1];
    return _jsxs(Layout, { className: "app-shell", children: [_jsxs(Layout.Sider, { className: "sidebar", children: [_jsxs("div", { className: "brand", children: [_jsx("div", { className: "brand-mark", children: "S" }), !collapsed && _jsxs("div", { children: [_jsx("strong", { children: "SubtleSight" }), _jsx("span", { children: "INTEL" })] })] }), _jsx(Nav, { className: "main-nav", selectedKeys: [selected], items: items, isCollapsed: collapsed, onSelect: d => navigate(String(d.itemKey)), footer: { collapseButton: true, onClick: () => setCollapsed(!collapsed) } })] }), _jsxs(Layout, { children: [_jsxs(Layout.Header, { className: "topbar", children: [_jsx(Button, { theme: "borderless", icon: _jsx(IconMenu, {}), onClick: () => setCollapsed(!collapsed) }), _jsx("div", { className: "topbar-title", children: "\u5168\u7F51\u60C5\u62A5\u7814\u7A76\u5DE5\u4F5C\u53F0" }), _jsxs("div", { className: "top-actions", children: [_jsx("span", { className: "status-dot" }), "\u7CFB\u7EDF\u5728\u7EBF ", _jsx(Button, { icon: _jsx(IconBolt, {}), theme: "solid", onClick: () => setAgentOpen(true), children: "\u95EE SubtleSight" }), _jsxs("span", { children: [user, " \u00B7 \u672C\u673A\u6A21\u5F0F"] })] })] }), _jsx(Layout.Content, { className: "workspace", children: _jsxs(Suspense, { fallback: _jsx("div", { className: "center-screen", children: _jsx(Spin, { size: "large", tip: "\u6B63\u5728\u52A0\u8F7D\u5DE5\u4F5C\u533A\u2026" }) }), children: [_jsxs(Routes, { children: [_jsx(Route, { path: "/discover", element: _jsx(DiscoveryPage, {}) }), _jsx(Route, { path: "/stories", element: _jsx(StoryPage, {}) }), _jsx(Route, { path: "/stories/:id", element: _jsx(StoryPage, {}) }), _jsx(Route, { path: "/research", element: _jsx(ResearchPage, {}) }), _jsx(Route, { path: "/research/:id", element: _jsx(ResearchPage, {}) }), _jsx(Route, { path: "/watchlists", element: _jsx(WatchlistPage, {}) }), _jsx(Route, { path: "/knowledge", element: _jsx(KnowledgePage, {}) }), _jsx(Route, { path: "/reports", element: _jsx(ReportsPage, {}) }), _jsx(Route, { path: "/admin", element: _jsx(AdminPage, {}) }), _jsx(Route, { path: "*", element: _jsx(Navigate, { to: "/discover", replace: true }) })] }), _jsx(AgentDrawer, {})] }) })] })] });
}
