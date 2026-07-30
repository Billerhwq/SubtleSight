type UiState = {
    collapsed: boolean;
    agentOpen: boolean;
    agentContext: Record<string, unknown>;
    setCollapsed: (v: boolean) => void;
    setAgentOpen: (v: boolean) => void;
    openAgent: (context?: Record<string, unknown>) => void;
};
export declare const useUi: import("zustand").UseBoundStore<import("zustand").StoreApi<UiState>>;
export {};
