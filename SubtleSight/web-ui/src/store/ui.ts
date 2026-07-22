import { create } from 'zustand';
type UiState={collapsed:boolean;agentOpen:boolean;setCollapsed:(v:boolean)=>void;setAgentOpen:(v:boolean)=>void};
export const useUi=create<UiState>(set=>({collapsed:false,agentOpen:false,setCollapsed:collapsed=>set({collapsed}),setAgentOpen:agentOpen=>set({agentOpen})}));

