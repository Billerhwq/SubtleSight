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

