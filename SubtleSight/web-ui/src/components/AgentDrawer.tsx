import { useState } from 'react';
import type { ReactNode } from 'react';
import { Button, SideSheet, TextArea, Toast } from '@douyinfe/semi-ui';
import { IconSend } from '@douyinfe/semi-icons';
import { post } from '../api/client';
import { useUi } from '../store/ui';

type Message={role:'user'|'agent';text:string;tools?:string[]};
export function AgentDrawer(): ReactNode {const {agentOpen,setAgentOpen}=useUi();const [text,setText]=useState(''),[busy,setBusy]=useState(false),[messages,setMessages]=useState<Message[]>([{role:'agent',text:'告诉我你想追踪什么、核实什么，或要建立怎样的发现视图。'}]);const send=async()=>{if(!text.trim())return;const current=text;setMessages(v=>[...v,{role:'user',text:current}]);setText('');setBusy(true);try{const result=await post<{message:string;tools:string[];confirmationRequired:boolean}>('/agent',{message:current,confirmed:false,context:{}});setMessages(v=>[...v,{role:'agent',text:result.message,tools:result.tools}]);}catch(e){Toast.error((e as Error).message)}finally{setBusy(false)}};return <SideSheet title="问 SubtleSight" visible={agentOpen} onCancel={()=>setAgentOpen(false)} width={480} footer={<div className="agent-compose"><TextArea autosize rows={2} value={text} onChange={setText} onEnterPress={e=>{if(!e.shiftKey){e.preventDefault();send()}}} placeholder="搜索、研究、创建视图或跟踪目标…"/><Button theme="solid" icon={<IconSend/>} loading={busy} onClick={send}/></div>}><div className="agent-intro"><span className="brand-mark">S</span><div><strong>受控情报 Agent</strong><p>所有操作通过同一业务服务；发布等高风险动作必须确认。</p></div></div><div className="chat-list">{messages.map((m,i)=><div key={i} className={`chat ${m.role}`}><p>{m.text}</p>{m.tools?.map(t=><code key={t}>{t}</code>)}</div>)}</div></SideSheet>}
