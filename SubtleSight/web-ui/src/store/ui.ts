import { create } from 'zustand';
type AgentContext = Record<string, unknown>;
type UiState={collapsed:boolean;agentOpen:boolean;agentContext:AgentContext;setCollapsed:(v:boolean)=>void;setAgentOpen:(v:boolean)=>void;openAgent:(context?:AgentContext)=>void};
export const useUi=create<UiState>(set=>({
  collapsed:false,
  agentOpen:false,
  agentContext:{},
  setCollapsed:collapsed=>set({collapsed}),
  setAgentOpen:agentOpen=>set({agentOpen,...(agentOpen?{agentContext:{}}:{})}),
  openAgent:agentContext=>set({agentOpen:true,agentContext:agentContext??{}}),
}));

