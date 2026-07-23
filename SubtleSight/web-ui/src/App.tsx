import React, { lazy, Suspense, useEffect } from 'react';
import type { ReactNode } from 'react';
import { HashRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Layout, Nav, Spin } from '@douyinfe/semi-ui';
import { IconArticle, IconBolt, IconEdit, IconFolderStroked, IconHome, IconList, IconMenu, IconSearch, IconSetting } from '@douyinfe/semi-icons';
import { get } from './api/client';
import { useUi } from './store/ui';

const DiscoveryPage = lazy<React.ComponentType<object>>(() => import('./pages/DiscoveryPage').then(m => ({ default: m.DiscoveryPage }) as unknown as { default: React.ComponentType }));
const StoryPage = lazy<React.ComponentType<object>>(() => import('./pages/StoryPage').then(m => ({ default: m.StoryPage }) as unknown as { default: React.ComponentType }));
const ResearchPage = lazy<React.ComponentType<object>>(() => import('./pages/ResearchPage').then(m => ({ default: m.ResearchPage }) as unknown as { default: React.ComponentType }));
const WatchlistPage = lazy<React.ComponentType<object>>(() => import('./pages/WatchlistPage').then(m => ({ default: m.WatchlistPage }) as unknown as { default: React.ComponentType }));
const KnowledgePage = lazy<React.ComponentType<object>>(() => import('./pages/KnowledgePage').then(m => ({ default: m.KnowledgePage }) as unknown as { default: React.ComponentType }));
const KnowledgeEditorPage = lazy<React.ComponentType<object>>(() => import('./pages/KnowledgeEditorPage').then(m => ({ default: m.KnowledgeEditorPage }) as unknown as { default: React.ComponentType }));
const ReportsPage = lazy<React.ComponentType<object>>(() => import('./pages/ReportsPage').then(m => ({ default: m.ReportsPage }) as unknown as { default: React.ComponentType }));
const AdminPage = lazy<React.ComponentType<object>>(() => import('./pages/AdminPage').then(m => ({ default: m.AdminPage }) as unknown as { default: React.ComponentType }));
const AgentDrawer = lazy<React.ComponentType<object>>(() => import('./components/AgentDrawer').then(m => ({ default: m.AgentDrawer }) as unknown as { default: React.ComponentType }));

interface Auth { authenticated: boolean; user: string }

export default function App(): ReactNode {
  return <HashRouter><AuthenticatedApp /></HashRouter>;
}

function AuthenticatedApp(): ReactNode {
  const query = useQuery({ queryKey: ['auth'], queryFn: () => get<Auth>('/auth/status') });
  if (query.isLoading) return <div className="center-screen"><Spin size="large" tip="正在唤醒 SubtleSight…" /></div>;
  const authUser = (query.data as Auth | undefined)?.user ?? 'local';
  return <Workspace user={authUser} />;
}

function Workspace(props: { user: string }): ReactNode {
  const { user } = props;
  const location = useLocation();
  const navigate = useNavigate();
  const client = useQueryClient();
  const { collapsed, setCollapsed, setAgentOpen } = useUi();

  useEffect(() => {
    const events = new EventSource('/api/v1/events');
    for (const name of ['story.updated', 'research.requested', 'research.completed', 'discovery.completed']) {
      events.addEventListener(name, () => client.invalidateQueries());
    }
    events.onerror = () => {};
    return () => events.close();
  }, [client]);

  const navItems = [
    { itemKey: '/discover', text: '发现', icon: React.createElement(IconHome) },
    { itemKey: '/stories', text: '事件', icon: React.createElement(IconList) },
    { itemKey: '/research', text: '研究', icon: React.createElement(IconSearch) },
    { itemKey: '/watchlists', text: '跟踪', icon: React.createElement(IconArticle) },
    { itemKey: '/knowledge', text: '知识库', icon: React.createElement(IconFolderStroked) },
    { itemKey: '/knowledge/editor', text: '编辑', icon: React.createElement(IconEdit) },
    { itemKey: '/reports', text: '报告', icon: React.createElement(IconArticle) },
    { itemKey: '/admin', text: '系统管理', icon: React.createElement(IconSetting) },
  ];

  const selected = location.pathname.startsWith('/knowledge/editor')
    ? '/knowledge/editor'
    : '/' + location.pathname.split('/')[1];

  return (
    <Layout className="app-shell">
      <Layout.Sider className="sidebar">
        <div className="brand">
          <div className="brand-mark">S</div>
          {!collapsed && <div><strong>SubtleSight</strong><span>INTEL</span></div>}
        </div>
        <Nav
          className="main-nav"
          selectedKeys={[selected]}
          items={navItems}
          isCollapsed={collapsed}
          onSelect={d => navigate(String(d.itemKey))}
          footer={{ collapseButton: true, onClick: () => setCollapsed(!collapsed) }}
        />
      </Layout.Sider>
      <Layout>
        <Layout.Header className="topbar">
          <Button theme="borderless" icon={<IconMenu />} onClick={() => setCollapsed(!collapsed)} />
          <div className="topbar-title">全网情报研究工作台</div>
          <div className="top-actions">
            <span className="status-dot" />
            系统在线
            <Button icon={<IconBolt />} theme="solid" onClick={() => setAgentOpen(true)}>问 SubtleSight</Button>
            <span>{user} · 本机模式</span>
          </div>
        </Layout.Header>
        <Layout.Content className="workspace">
          <Suspense fallback={<div className="center-screen"><Spin size="large" tip="正在加载工作区…" /></div>}>
            <Routes>
              <Route path="/discover" element={<DiscoveryPage />} />
              <Route path="/stories" element={<StoryPage />} />
              <Route path="/stories/:id" element={<StoryPage />} />
              <Route path="/research" element={<ResearchPage />} />
              <Route path="/research/:id" element={<ResearchPage />} />
              <Route path="/watchlists" element={<WatchlistPage />} />
              <Route path="/knowledge/editor" element={<KnowledgeEditorPage />} />
              <Route path="/knowledge" element={<KnowledgePage />} />
              <Route path="/reports" element={<ReportsPage />} />
              <Route path="/admin" element={<AdminPage />} />
              <Route path="*" element={<Navigate to="/discover" replace />} />
            </Routes>
            <AgentDrawer />
          </Suspense>
        </Layout.Content>
      </Layout>
    </Layout>
  );
}
