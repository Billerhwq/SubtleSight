export declare class ApiError extends Error {
    status: number;
    detail: string;
    constructor(status: number, detail: string);
}
export declare function api<T>(path: string, init?: RequestInit): Promise<T>;
export declare const get: <T>(path: string) => Promise<T>;
export declare const post: <T>(path: string, body?: unknown) => Promise<T>;
export declare const del: <T>(path: string) => Promise<T>;
/** 知识库文件上传：使用 XHR 以获取实时进度，配合多文件并行提升大文件上传体验 */
export declare function uploadKnowledgeFile(folderId: string | null, file: File, onProgress: (percent: number) => void): Promise<void>;
