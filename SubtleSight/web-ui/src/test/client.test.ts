import { afterEach, describe, expect, it, vi } from 'vitest';
import { api, del, get, post, uploadKnowledgeFile } from '../api/client';

describe('api client',()=>{afterEach(()=>vi.restoreAllMocks());it('returns json and sends csrf on writes',async()=>{Object.defineProperty(document,'cookie',{writable:true,value:'XSRF-TOKEN=abc'});const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(new Response(JSON.stringify({ok:true}),{status:200,headers:{'content-type':'application/json'}}));await expect(post('/x',{value:1})).resolves.toEqual({ok:true});expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get('X-XSRF-TOKEN')).toBe('abc');expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get('Content-Type')).toBe('application/json');});it('supports get delete text and no-content responses',async()=>{const fetchMock=vi.spyOn(globalThis,'fetch');fetchMock.mockResolvedValueOnce(new Response('hello',{status:200,headers:{'content-type':'text/plain'}}));await expect(get('/text')).resolves.toBe('hello');fetchMock.mockResolvedValueOnce(new Response(null,{status:204}));await expect(del('/item')).resolves.toBeUndefined();});it('surfaces problem detail',async()=>{vi.spyOn(globalThis,'fetch').mockResolvedValue(new Response(JSON.stringify({detail:'bad input'}),{status:400,headers:{'content-type':'application/problem+json'}}));await expect(api('/x')).rejects.toEqual(expect.objectContaining({status:400,detail:'bad input'}));});it('falls back to status text for non-json errors',async()=>{vi.spyOn(globalThis,'fetch').mockResolvedValue(new Response('down',{status:503,statusText:'Unavailable'}));await expect(api('/x')).rejects.toEqual(expect.objectContaining({status:503,detail:'Unavailable'}));});

describe('knowledge upload',()=>{afterEach(()=>vi.unstubAllGlobals());
  it('uploads with csrf header and progress events',async()=>{
    Object.defineProperty(document,'cookie',{writable:true,value:'XSRF-TOKEN=abc'});
    const sent:string[]=[];
    class FakeXHR{
      upload:{onprogress:((e:{lengthComputable:boolean;loaded:number;total:number})=>void)|null}={onprogress:null};
      onload:(()=>void)|null=null;onerror:(()=>void)|null=null;status=201;statusText='Created';responseText='[]';withCredentials=false;
      open=vi.fn();setRequestHeader=(k:string,v:string)=>{sent.push(k+'='+v);};
      send=(_body:FormData)=>{this.upload.onprogress?.({lengthComputable:true,loaded:50,total:100});this.onload?.();};
    }
    vi.stubGlobal('XMLHttpRequest',FakeXHR);
    const progresses:number[]=[];
    await expect(uploadKnowledgeFile('folder-1',new File(['x'],'a.pdf'),p=>progresses.push(p))).resolves.toBeUndefined();
    expect(progresses).toEqual([50]);
    expect(sent).toContain('X-XSRF-TOKEN=abc');
  });
  it('rejects with server detail on failure',async()=>{
    class FailXHR{
      upload:{onprogress:null}={onprogress:null};
      onload:(()=>void)|null=null;onerror:(()=>void)|null=null;status=413;statusText='Payload Too Large';responseText=JSON.stringify({detail:'file too large'});withCredentials=false;
      open=vi.fn();setRequestHeader=vi.fn();send=(_body:FormData)=>{this.onload?.();};
    }
    vi.stubGlobal('XMLHttpRequest',FailXHR);
    await expect(uploadKnowledgeFile(null,new File(['x'],'b.pdf'),()=>{})).rejects.toEqual(expect.objectContaining({status:413,detail:'file too large'}));
  });
  it('rejects on network error',async()=>{
    class ErrorXHR{
      upload:{onprogress:null}={onprogress:null};
      onload:(()=>void)|null=null;onerror:(()=>void)|null=null;status=0;statusText='';responseText='';withCredentials=false;
      open=vi.fn();setRequestHeader=vi.fn();send=(_body:FormData)=>{this.onerror?.();};
    }
    vi.stubGlobal('XMLHttpRequest',ErrorXHR);
    await expect(uploadKnowledgeFile(null,new File(['x'],'c.pdf'),()=>{})).rejects.toEqual(expect.objectContaining({status:0}));
  });
});});
