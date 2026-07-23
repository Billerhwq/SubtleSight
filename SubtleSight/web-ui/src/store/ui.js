import { create } from 'zustand';
export const useUi = create(set => ({ collapsed: false, agentOpen: false, setCollapsed: collapsed => set({ collapsed }), setAgentOpen: agentOpen => set({ agentOpen }) }));
