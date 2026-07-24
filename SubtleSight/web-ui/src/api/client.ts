export class ApiError extends Error {
  constructor(public status: number, public detail: string) {
    super(detail);
  }
}

function csrf(): string | undefined {
  const cookie = document.cookie.split('; ').find(v => v.startsWith('XSRF-TOKEN='));
  return cookie?.split('=')[1];
}

function getCsrfToken(): string {
  return csrf() ?? '';
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase();
  const headers = new Headers(init.headers as HeadersInit | undefined);
  if (init.body && !(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = getCsrfToken();
    if (token) headers.set('X-XSRF-TOKEN', decodeURIComponent(token));
  }
  const fetchInit: RequestInit = { ...init, headers, credentials: 'same-origin' };
  const response = await fetch(`/api/v1${path}`, fetchInit);
  if (!response.ok) {
    let detail = response.statusText;
    try { const p: unknown = await response.json(); detail = (p as Record<string, unknown>).detail as string ?? detail; } catch { /* non-JSON */ }
    throw new ApiError(response.status, detail);
  }
  if (response.status === 204) return undefined as unknown as T;
  const type = response.headers.get('content-type') ?? '';
  return (type.includes('json') ? response.json() : response.text()) as unknown as Promise<T>;
}

export const get = <T>(path: string): Promise<T> => api<T>(path);

export const post = <T>(path: string, body?: unknown): Promise<T> =>
  api<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) } as RequestInit);

export const put = <T>(path: string, body?: unknown): Promise<T> =>
  api<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) } as RequestInit);

export const del = <T>(path: string): Promise<T> => api<T>(path, { method: 'DELETE' } as RequestInit);

/** 知识库文件上传：使用 XHR 以获取实时进度，配合多文件并行提升大文件上传体验 */
export function uploadKnowledgeFile(
  folderId: string | null,
  file: File,
  onProgress: (percent: number) => void,
): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `/api/v1/knowledge/upload${folderId ? `?folderId=${encodeURIComponent(folderId)}` : ''}`);
    xhr.withCredentials = true;
    const token = getCsrfToken();
    if (token) xhr.setRequestHeader('X-XSRF-TOKEN', decodeURIComponent(token));
    xhr.upload.onprogress = e => {
      if (e.lengthComputable) onProgress(Math.round((e.loaded / e.total) * 100));
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve();
      } else {
        let detail = xhr.statusText;
        try { detail = JSON.parse(xhr.responseText).detail ?? detail; } catch { /* non-JSON */ }
        reject(new ApiError(xhr.status, detail));
      }
    };
    xhr.onerror = () => reject(new ApiError(0, '网络错误，上传失败'));
    const body = new FormData();
    body.append('files', file);
    xhr.send(body as XMLHttpRequestBodyInit);
  });
}
