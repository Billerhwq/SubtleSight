type UiState = {
    collapsed: boolean;
    agentOpen: boolean;
    setCollapsed: (v: boolean) => void;
    setAgentOpen: (v: boolean) => void;
};
export declare const useUi: import("zustand").UseBoundStore<import("zustand").StoreApi<UiState>>;
export {};
