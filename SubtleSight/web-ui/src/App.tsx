import { lazy, Suspense, useEffect } from 'react';
import { HashRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Layout, Nav, Spin } from '@douyinfe/semi-ui';
import { IconArticle, IconBolt, IconHome, IconList, IconMenu, IconSearch, IconSetting } from '@douyinfe/semi-icons';
import { get } from './api/client';
import { useUi } from './store/ui';

const DiscoveryPage=lazy(()=>import('./pages/DiscoveryPage').then(module=>({default:module.DiscoveryPage})));
const StoryPage=lazy(()=>import('./pages/StoryPage').then(module=>({default:module.StoryPage})));
const ResearchPage=lazy(()=>import('./pages/ResearchPage').then(module=>({default:module.ResearchPage})));
const WatchlistPage=lazy(()=>import('./pages/WatchlistPage').then(module=>({default:module.WatchlistPage})));
const ReportsPage=lazy(()=>import('./pages/ReportsPage').then(module=>({default:module.ReportsPage})));
const AdminPage=lazy(()=>import('./pages/AdminPage').then(module=>({default:module.AdminPage})));
const AgentDrawer=lazy(()=>import('./components/AgentDrawer').then(module=>({default:module.AgentDrawer})));

type Auth={authenticated:boolean;user:string};
export default function App(){return <HashRouter><AuthenticatedApp/></HashRouter>}
function AuthenticatedApp(){const query=useQuery({queryKey:['auth'],queryFn:()=>get<Auth>('/auth/status')});if(query.isLoading)return <div className="center-screen"><Spin size="large" tip="正在唤醒 SubtleSight…"/></div>;return <Workspace user={query.data?.user??'local'}/>}
function Workspace({user}:{user:string}){const location=useLocation(),navigate=useNavigate(),client=useQueryClient();const {collapsed,setCollapsed,setAgentOpen}=useUi();
  useEffect(()=>{const events=new EventSource('/api/v1/events');for(const name of ['story.updated','research.requested','research.completed','discovery.completed'])events.addEventListener(name,()=>client.invalidateQueries());events.onerror=()=>{};return()=>events.close();},[client]);
  const items=[{itemKey:'/discover',text:'发现',icon:<IconHome/>},{itemKey:'/stories',text:'事件',icon:<IconList/>},{itemKey:'/research',text:'研究',icon:<IconSearch/>},{itemKey:'/watchlists',text:'跟踪',icon:<IconArticle/>},{itemKey:'/reports',text:'报告',icon:<IconArticle/>},{itemKey:'/admin',text:'系统管理',icon:<IconSetting/>}];
  const selected='/' + location.pathname.split('/')[1];
  return <Layout className="app-shell"><Layout.Sider className="sidebar"><div className="brand"><div className="brand-mark">S</div>{!collapsed&&<div><strong>SubtleSight</strong><span>INTEL</span></div>}</div><Nav className="main-nav" selectedKeys={[selected]} items={items} isCollapsed={collapsed} onSelect={d=>navigate(String(d.itemKey))} footer={{collapseButton:true,onClick:()=>setCollapsed(!collapsed)}}/></Layout.Sider><Layout><Layout.Header className="topbar"><Button theme="borderless" icon={<IconMenu/>} onClick={()=>setCollapsed(!collapsed)}/><div className="topbar-title">全网情报研究工作台</div><div className="top-actions"><span className="status-dot"/>系统在线 <Button icon={<IconBolt/>} theme="solid" onClick={()=>setAgentOpen(true)}>问 SubtleSight</Button><span>{user} · 本机模式</span></div></Layout.Header><Layout.Content className="workspace"><Suspense fallback={<div className="center-screen"><Spin size="large" tip="正在加载工作区…"/></div>}><Routes><Route path="/discover" element={<DiscoveryPage/>}/><Route path="/stories" element={<StoryPage/>}/><Route path="/stories/:id" element={<StoryPage/>}/><Route path="/research" element={<ResearchPage/>}/><Route path="/research/:id" element={<ResearchPage/>}/><Route path="/watchlists" element={<WatchlistPage/>}/><Route path="/reports" element={<ReportsPage/>}/><Route path="/admin" element={<AdminPage/>}/><Route path="*" element={<Navigate to="/discover" replace/>}/></Routes><AgentDrawer/></Suspense></Layout.Content></Layout></Layout>;
}
