export class ApiError extends Error { constructor(public status:number,public detail:string){super(detail);} }
function csrf(){return document.cookie.split('; ').find(v=>v.startsWith('XSRF-TOKEN='))?.split('=')[1];}
export async function api<T>(path:string,init:RequestInit={}):Promise<T>{
  const method=(init.method??'GET').toUpperCase();const headers=new Headers(init.headers);
  if(init.body&&!(init.body instanceof FormData))headers.set('Content-Type','application/json');
  if(!['GET','HEAD','OPTIONS'].includes(method)){const token=csrf();if(token)headers.set('X-XSRF-TOKEN',decodeURIComponent(token));}
  const response=await fetch(`/api/v1${path}`,{...init,headers,credentials:'same-origin'});
  if(!response.ok){let detail=response.statusText;try{const p=await response.json();detail=p.detail??detail;}catch{/* non-JSON upstream error */}throw new ApiError(response.status,detail);}
  if(response.status===204)return undefined as T;const type=response.headers.get('content-type')??'';return (type.includes('json')?response.json():response.text()) as Promise<T>;
}
export const get=<T>(path:string)=>api<T>(path);
export const post=<T>(path:string,body?:unknown)=>api<T>(path,{method:'POST',body:body===undefined?undefined:JSON.stringify(body)});
export const del=<T>(path:string)=>api<T>(path,{method:'DELETE'});
/** 知识库文件上传：使用 XHR 以获取实时进度，配合多文件并行提升大文件上传体验 */
export function uploadKnowledgeFile(folderId:string|null,file:File,onProgress:(percent:number)=>void):Promise<void>{
  return new Promise((resolve,reject)=>{
    const xhr=new XMLHttpRequest();
    xhr.open('POST',`/api/v1/knowledge/upload${folderId?`?folderId=${encodeURIComponent(folderId)}`:''}`);
    xhr.withCredentials=true;
    const token=csrf();if(token)xhr.setRequestHeader('X-XSRF-TOKEN',decodeURIComponent(token));
    xhr.upload.onprogress=e=>{if(e.lengthComputable)onProgress(Math.round(e.loaded/e.total*100));};
    xhr.onload=()=>{if(xhr.status>=200&&xhr.status<300){resolve();}else{let detail=xhr.statusText;try{detail=JSON.parse(xhr.responseText).detail??detail;}catch{/* non-JSON upstream error */}reject(new ApiError(xhr.status,detail));}};
    xhr.onerror=()=>reject(new ApiError(0,'网络错误，上传失败'));
    const body=new FormData();body.append('files',file);xhr.send(body);
  });
}

