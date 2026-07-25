import React, { lazy, Suspense, useEffect } from 'react';
import type { ReactNode } from 'react';
import { HashRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Spin } from '@douyinfe/semi-ui';
import {
  IconBellStroked,
  IconBolt,
  IconCalendarStroked,
  IconEditStroked,
  IconEyeOpenedStroked,
  IconFile,
  IconFilterStroked,
  IconFolderStroked,
  IconHomeStroked,
  IconMenu,
  IconPulse,
  IconServerStroked,
  IconSettingStroked,
  IconWifi,
} from '@douyinfe/semi-icons';
import { get } from './api/client';
import { useUi } from './store/ui';

const DiscoveryPage = lazy<React.ComponentType<object>>(() => import('./pages/DiscoveryPage').then(m => ({ default: m.DiscoveryPage }) as unknown as { default: React.ComponentType }));
const StoryPage = lazy<React.ComponentType<object>>(() => import('./pages/StoryPage').then(m => ({ default: m.StoryPage }) as unknown as { default: React.ComponentType }));
const ResearchPage = lazy<React.ComponentType<object>>(() => import('./pages/ResearchPage').then(m => ({ default: m.ResearchPage }) as unknown as { default: React.ComponentType }));
const WatchlistPage = lazy<React.ComponentType<object>>(() => import('./pages/WatchlistPage').then(m => ({ default: m.WatchlistPage }) as unknown as { default: React.ComponentType }));
const KnowledgePage = lazy<React.ComponentType<object>>(() => import('./pages/KnowledgePage').then(m => ({ default: m.KnowledgePage }) as unknown as { default: React.ComponentType }));
const ReportsPage = lazy<React.ComponentType<object>>(() => import('./pages/ReportsPage').then(m => ({ default: m.ReportsPage }) as unknown as { default: React.ComponentType }));
const AdminPage = lazy<React.ComponentType<object>>(() => import('./pages/AdminPage').then(m => ({ default: m.AdminPage }) as unknown as { default: React.ComponentType }));
const AgentDrawer = lazy<React.ComponentType<object>>(() => import('./components/AgentDrawer').then(m => ({ default: m.AgentDrawer }) as unknown as { default: React.ComponentType }));

interface Auth { authenticated: boolean; user: string }

interface ShellNavItem {
  key: string;
  href: string;
  label: string;
  icon: ReactNode;
  count?: string;
}

const shellNavGroups: Array<{ label: string; items: ShellNavItem[] }> = [
  {
    label: '工作区',
    items: [
      { key: '/discover', href: 'discover.html', label: '发现', icon: <IconHomeStroked /> },
      { key: '/knowledge', href: 'knowledge-editor.html#/knowledge', label: '知识库', icon: <IconFolderStroked /> },
      { key: '/calendar', href: 'calendar.html', label: '财经日历', icon: <IconCalendarStroked /> },
      { key: '/watchlists', href: 'watchlist.html', label: 'Watchlist', icon: <IconEyeOpenedStroked />, count: '4' },
    ],
  },
  {
    label: '输出',
    items: [
      { key: '/reports', href: 'reports.html', label: '报告', icon: <IconFile />, count: '3' },
      { key: '/views', href: 'views.html', label: '自定义视图', icon: <IconFilterStroked /> },
    ],
  },
  {
    label: '系统',
    items: [
      { key: '/sources', href: 'sources.html', label: '信源', icon: <IconWifi />, count: '2' },
      { key: '/tasks', href: 'tasks.html', label: '任务中心', icon: <IconPulse />, count: '5' },
      { key: '/admin', href: 'settings.html', label: '设置', icon: <IconSettingStroked /> },
    ],
  },
];

export default function App(): ReactNode {
  return <HashRouter><AuthenticatedApp /></HashRouter>;
}

function AuthenticatedApp(): ReactNode {
  const query = useQuery({ queryKey: ['auth'], queryFn: () => get<Auth>('/auth/status') });
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const q = query as any;
  if (q.isLoading) return <div className="center-screen"><Spin size="large" tip="正在唤醒 SubtleSight…" /></div>;
  const authUser = (q.data as Auth | undefined)?.user ?? 'local';
  return <Workspace user={authUser} apiConnected={!q.isError} />;
}

function Workspace(props: { user: string; apiConnected: boolean }): ReactNode {
  const { user, apiConnected } = props;
  const location = useLocation();
  const client = useQueryClient();
  const { collapsed, setCollapsed, setAgentOpen } = useUi();

  useEffect(() => {
    const events = new EventSource('/api/v1/events');
    const refreshAll = () => (client as any).invalidateQueries();
    for (const name of ['story.updated', 'research.requested', 'research.completed', 'discovery.completed']) {
      events.addEventListener(name, refreshAll);
    }
    events.onerror = () => {};
    return () => events.close();
  }, [client]);

  const selected = '/' + location.pathname.split('/')[1];
  const avatarText = user === 'local' ? 'SS' : user.slice(0, 2).toUpperCase();

  return (
    <div className={`editor-shell${collapsed ? ' shell-collapsed' : ''}`}>
      <aside className="shell-sidebar">
        <a className="shell-brand" href="discover.html" aria-label="返回发现页">
          <span className="shell-brand-mark">S</span>
          <span className="shell-brand-name">SubtleSight</span>
          <span className="shell-live-badge">LIVE</span>
        </a>
        <nav className="shell-nav" aria-label="主导航">
          {shellNavGroups.map(group => (
            <div className="shell-nav-group" key={group.label}>
              <div className="shell-nav-label">{group.label}</div>
              {group.items.map(item => (
                <a
                  key={item.key}
                  className={`shell-nav-link${selected === item.key ? ' active' : ''}`}
                  href={item.href}
                  title={collapsed ? item.label : undefined}
                >
                  <span className="shell-nav-icon">{item.icon}</span>
                  <span className="shell-nav-text">{item.label}</span>
                  {item.count && <span className="shell-nav-count">{item.count}</span>}
                </a>
              ))}
            </div>
          ))}
        </nav>
        <div className="shell-sidebar-footer">
          <div className="shell-status-row">
            <span className={`shell-status-dot${apiConnected ? '' : ' offline'}`} />
            <span>{apiConnected ? 'API 已联通' : 'API 未连接'}</span>
          </div>
          <div className="shell-status-row"><IconServerStroked /><span>{apiConnected ? '索引已同步' : '等待后端服务'}</span></div>
        </div>
      </aside>

      <header className="shell-topbar">
        <button className="shell-icon-button" title={collapsed ? '展开导航' : '收起导航'} onClick={() => setCollapsed(!collapsed)}>
          <IconMenu />
        </button>
        <span className="shell-mode-label">Live · 本机数据</span>
        <div className="shell-topbar-spacer" />
        <button className="shell-icon-button" title="问 SubtleSight" onClick={() => setAgentOpen(true)}><IconBolt /></button>
        <button className="shell-icon-button" title="提醒"><IconBellStroked /></button>
        <span className="shell-avatar" aria-label={`当前用户 ${user}`}>{avatarText}</span>
      </header>

      <main className="shell-workspace">
        <Suspense fallback={<div className="center-screen"><Spin size="large" tip="正在加载工作区…" /></div>}>
          <Routes>
            <Route path="/discover" element={<DiscoveryPage />} />
            <Route path="/stories" element={<StoryPage />} />
            <Route path="/stories/:id" element={<StoryPage />} />
            <Route path="/research" element={<ResearchPage />} />
            <Route path="/research/:id" element={<ResearchPage />} />
            <Route path="/watchlists" element={<WatchlistPage />} />
            <Route path="/knowledge" element={<KnowledgePage />} />
            <Route path="/reports" element={<ReportsPage />} />
            <Route path="/admin" element={<AdminPage />} />
            <Route path="*" element={<Navigate to="/discover" replace />} />
          </Routes>
          <AgentDrawer />
        </Suspense>
      </main>
    </div>
  );
}
