export class ApiError extends Error {
    status;
    detail;
    constructor(status, detail) {
        super(detail);
        this.status = status;
        this.detail = detail;
    }
}
function csrf() { const m = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/); return m ? m[1] : undefined; }
export async function api(path, init) {
    const method = (init?.method ?? 'GET').toUpperCase();
    const headers = new Headers(init?.headers);
    if (init?.body && !(init.body instanceof FormData))
        headers.set('Content-Type', 'application/json');
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
        const token = csrf();
        if (token)
            headers.set('X-XSRF-TOKEN', decodeURIComponent(token));
    }
    const response = await fetch(`/api/v1${path}`, Object.assign({}, init, { headers, credentials: 'same-origin' }));
    if (!response.ok) {
        let detail = response.statusText;
        try {
            const p = await response.json();
            detail = p.detail ?? detail;
        }
        catch { /* ignore parse error */ }
        throw new ApiError(response.status, detail);
    }
    if (response.status === 204)
        return undefined;
    const type = response.headers.get('content-type') ?? '';
    return (type.includes('json') ? response.json() : response.text());
}
export const get = (path) => api(path);
export const post = (path, body) => api(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) });
export const del = (path) => api(path, { method: 'DELETE' });
/** 知识库文件上传：使用 XHR 以获取实时进度，配合多文件并行提升大文件上传体验 */
export function uploadKnowledgeFile(folderId, file, onProgress) {
    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        xhr.open('POST', `/api/v1/knowledge/upload${folderId ? `?folderId=${encodeURIComponent(folderId)}` : ''}`);
        xhr.withCredentials = true;
        const token = csrf();
        if (token)
            xhr.setRequestHeader('X-XSRF-TOKEN', decodeURIComponent(token));
        xhr.upload.onprogress = e => { if (e.lengthComputable)
            onProgress(Math.round(e.loaded / e.total * 100)); };
        xhr.onload = () => { if (xhr.status >= 200 && xhr.status < 300) {
            resolve();
        }
        else {
            let detail = xhr.statusText;
            try {
                detail = JSON.parse(xhr.responseText).detail ?? detail;
            }
            catch { /* ignore parse error */ }
            reject(new ApiError(xhr.status, detail));
        } };
        xhr.onerror = () => reject(new ApiError(0, '网络错误，上传失败'));
        const body = new FormData();
        body.append('files', file);
        xhr.send(body);
    });
}
