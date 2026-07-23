import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Dropdown, Input, Modal, Toast, TreeSelect, Upload } from '@douyinfe/semi-ui';
import { IconArrowDown, IconArrowUp, IconChevronDown, IconChevronLeft, IconDelete, IconEdit, IconFolder, IconFolderStroked, IconGridRectangle, IconList, IconPlus, IconRefresh, IconSearch } from '@douyinfe/semi-icons';
import { del, get, post, uploadKnowledgeFile } from '../api/client';
import type { KnowledgeFile, KnowledgeFolder } from '../types';

type TreeNode=KnowledgeFolder&{children:TreeNode[]};
type Item={kind:'folder';folder:KnowledgeFolder}|{kind:'file';file:KnowledgeFile};

function buildTree(folders:KnowledgeFolder[]):TreeNode[]{
  const map=new Map<string,TreeNode>();folders.forEach(f=>map.set(f.id,{...f,children:[]}));
  const roots:TreeNode[]=[];
  map.forEach(node=>{if(node.parentId&&map.has(node.parentId))map.get(node.parentId)!.children.push(node);else roots.push(node);});
  return roots;
}
function toTreeData(nodes:TreeNode[]):{label:string;value:string;key:string;children:ReturnType<typeof toTreeData>}[]{
  return nodes.map(n=>({label:n.name,value:n.id,key:n.id,children:toTreeData(n.children)}));
}
function fmtSize(bytes:number){if(bytes<1024)return `${bytes} B`;const units=['KB','MB','GB'];let v=bytes/1024,i=0;while(v>=1024&&i<units.length-1){v/=1024;i++;}return `${v>=100?v.toFixed(0):v.toFixed(1)} ${units[i]}`;}
function relTime(iso:string){const diff=Date.now()-new Date(iso).getTime();const m=Math.floor(diff/60000);if(m<1)return '刚刚';if(m<60)return `${m} 分钟前`;const h=Math.floor(m/60);if(h<24)return '今天';if(h<48)return '昨天';const d=Math.floor(h/24);if(d<30)return `${d} 天前`;return new Date(iso).toLocaleDateString();}
function docKind(ext:string){switch(ext){case'pdf':return{cls:'pdf',label:'PDF'};case'xlsx':case'xls':case'csv':return{cls:'xlsx',label:'X'};case'docx':case'doc':return{cls:'docx',label:'W'};case'md':return{cls:'md',label:'M↓'};case'png':case'jpg':case'jpeg':case'gif':case'webp':case'svg':case'bmp':return{cls:'image',label:'IMG'};case'txt':case'log':return{cls:'txt',label:'TXT'};default:return{cls:'txt',label:ext?ext.slice(0,3).toUpperCase():'FILE'};}}
const itemName=(item:Item)=>item.kind==='folder'?item.folder.name:item.file.name;

export function KnowledgePage(){
  const client=useQueryClient();
  const [current,setCurrent]=useState<string|null>(null);
  const [collapsed,setCollapsed]=useState<Set<string>>(new Set());
  const [view,setView]=useState<'grid'|'list'>('grid');
  const [ascending,setAscending]=useState(true);
  const [keyword,setKeyword]=useState('');
  const [selectedCard,setSelectedCard]=useState<string|null>(null);
  const [spinning,setSpinning]=useState(false);
  const [newFolderOpen,setNewFolderOpen]=useState(false);
  const [newFolderName,setNewFolderName]=useState('');
  const [uploadOpen,setUploadOpen]=useState(false);
  const [renameTarget,setRenameTarget]=useState<KnowledgeFile|null>(null);
  const [renameValue,setRenameValue]=useState('');
  const [moveTarget,setMoveTarget]=useState<KnowledgeFile|null>(null);
  const [moveFolderId,setMoveFolderId]=useState<string>('root');

  const foldersQuery=useQuery({queryKey:['knowledge-folders'],queryFn:()=>get<KnowledgeFolder[]>('/knowledge/folders')});
  const filesQuery=useQuery({queryKey:['knowledge-files',current],queryFn:()=>get<KnowledgeFile[]>(`/knowledge/files${current?`?folderId=${current}`:''}`)});
  const folders=useMemo(()=>foldersQuery.data??[],[foldersQuery.data]);
  const files=useMemo(()=>filesQuery.data??[],[filesQuery.data]);
  const tree=useMemo(()=>buildTree(folders),[folders]);
  const folderMap=useMemo(()=>new Map(folders.map(f=>[f.id,f])),[folders]);
  const invalidate=()=>{client.invalidateQueries({queryKey:['knowledge-folders']});client.invalidateQueries({queryKey:['knowledge-files']});};

  const createFolder=useMutation({mutationFn:()=>post('/knowledge/folders',{parentId:current,name:newFolderName}),onSuccess:()=>{invalidate();setNewFolderOpen(false);Toast.success('文件夹已创建');},onError:(e:Error)=>Toast.error(e.message)});
  const renameFile=useMutation({mutationFn:(input:{id:string;name:string})=>post(`/knowledge/files/${input.id}/rename`,{name:input.name}),onSuccess:()=>{invalidate();setRenameTarget(null);Toast.success('已重命名');},onError:(e:Error)=>Toast.error(e.message)});
  const moveFile=useMutation({mutationFn:(input:{id:string;folderId:string|null})=>post(`/knowledge/files/${input.id}/move`,{folderId:input.folderId}),onSuccess:()=>{invalidate();setMoveTarget(null);Toast.success('已移动');},onError:(e:Error)=>Toast.error(e.message)});
  const deleteFile=useMutation({mutationFn:(id:string)=>del(`/knowledge/files/${id}`),onSuccess:()=>{invalidate();Toast.success('已删除');},onError:(e:Error)=>Toast.error(e.message)});

  const breadcrumb=useMemo(()=>{const parts:string[]=[];let id=current;while(id){const f=folderMap.get(id);if(!f)break;parts.unshift(f.name);id=f.parentId??null;}return parts;},[current,folderMap]);

  const items=useMemo<Item[]>(()=>{
    const subfolders=folders.filter(f=>(f.parentId??null)===current);
    const list:Item[]=[...subfolders.map(folder=>({kind:'folder' as const,folder})),...files.map(file=>({kind:'file' as const,file}))];
    const key=keyword.trim().toLowerCase();
    const filtered=key?list.filter(item=>itemName(item).toLowerCase().includes(key)):list;
    return [...filtered].sort((a,b)=>ascending?itemName(a).localeCompare(itemName(b),'zh-CN'):itemName(b).localeCompare(itemName(a),'zh-CN'));
  },[folders,files,current,keyword,ascending]);

  const toggle=(id:string)=>setCollapsed(prev=>{const next=new Set(prev);if(next.has(id))next.delete(id);else next.add(id);return next;});
  const openFolder=(id:string|null)=>{setCurrent(id);setSelectedCard(null);};
  const collapseAll=()=>{setCollapsed(new Set(folders.map(f=>f.id)));Toast.success('知识树已折叠');};
  const refresh=()=>{setSpinning(true);invalidate();setTimeout(()=>setSpinning(false),450);Toast.success('知识树已刷新');};

  const confirmDelete=(file:KnowledgeFile)=>Modal.confirm({title:'删除文件',content:`确定删除「${file.name}」吗？文件将从本地磁盘中移除，此操作不可恢复。`,okText:'删除',cancelText:'取消',okButtonProps:{type:'danger',theme:'solid'},onOk:()=>deleteFile.mutate(file.id)});

  const renderBranch=(nodes:TreeNode[])=>nodes.map(node=>(
    <div className={`kb-tree-branch${collapsed.has(node.id)?' collapsed':''}`} key={node.id}>
      <button className={`kb-tree-row${current===node.id?' selected':''}`} onClick={()=>openFolder(node.id)}>
        <span className={`kb-chev${node.children.length?'':' hidden'}`} onClick={e=>{e.stopPropagation();toggle(node.id);}}><IconChevronDown size="small"/></span>
        <IconFolder size="small"/><span className="kb-tree-name" title={node.name}>{node.name}</span>
      </button>
      {node.children.length>0&&<div className="kb-tree-children">{renderBranch(node.children)}</div>}
    </div>
  ));

  const fileMenu=(file:KnowledgeFile)=>(
    <Dropdown.Menu>
      <Dropdown.Item icon={<IconFolderStroked/>} onClick={()=>{setMoveTarget(file);setMoveFolderId(file.folderId??'root');}}>移动到…</Dropdown.Item>
      <Dropdown.Item icon={<IconEdit/>} onClick={()=>{setRenameTarget(file);setRenameValue(file.name);}}>重命名</Dropdown.Item>
      <Dropdown.Item icon={<IconDelete/>} type="danger" onClick={()=>confirmDelete(file)}>删除</Dropdown.Item>
    </Dropdown.Menu>
  );

  const renderCard=(item:Item)=>{
    if(item.kind==='folder'){
      const f=item.folder;
      return <article key={`f-${f.id}`} className={`kb-file-card${selectedCard===`f-${f.id}`?' selected':''}`} tabIndex={0} onClick={()=>setSelectedCard(`f-${f.id}`)} onDoubleClick={()=>openFolder(f.id)}>
        <div className="kb-visual"><div className="kb-folder-icon"/></div>
        <div className="kb-name" title={f.name}>{f.name}</div>
        <div className="kb-meta">文件夹 · {relTime(f.updatedAt)}</div>
      </article>;
    }
    const file=item.file;const kind=docKind(file.ext);
    return <Dropdown key={file.id} trigger="contextMenu" position="rightTop" render={fileMenu(file)}>
      <article className={`kb-file-card${selectedCard===file.id?' selected':''}`} tabIndex={0} onClick={()=>setSelectedCard(file.id)}>
        <div className="kb-visual"><div className={`kb-doc-icon ${kind.cls}`}>{kind.label}</div></div>
        <div className="kb-name" title={file.name}>{file.name}</div>
        <div className="kb-meta">{fmtSize(file.sizeBytes)} · {relTime(file.updatedAt)}</div>
      </article>
    </Dropdown>;
  };

  const latest=useMemo(()=>{const times=[...folders.map(f=>f.updatedAt),...files.map(f=>f.updatedAt)];return times.length?times.sort().at(-1)!:null;},[folders,files]);

  return <div className="kb-page">
    <div className="kb-page-head"><h1>知识库</h1><p className="kb-subtitle">集中管理研究资料、项目文档与团队知识。</p></div>
    <section className="kb-workspace">
      <aside className="kb-tree-pane">
        <div className="kb-pane-header"><span className="kb-pane-title">知识树</span><div className="kb-pane-actions">
          <button className="kb-icon-btn" aria-label="新建文件夹" title="新建文件夹" onClick={()=>{setNewFolderName('');setNewFolderOpen(true);}}><IconPlus size="small"/></button>
          <button className={`kb-icon-btn${spinning?' kb-spin':''}`} aria-label="刷新知识树" title="刷新知识树" onClick={refresh}><IconRefresh size="small"/></button>
          <button className="kb-icon-btn" aria-label="全部折叠" title="全部折叠" onClick={collapseAll}><IconChevronLeft size="small"/></button>
        </div></div>
        <div className="kb-tree-scroll">
          <div className="kb-tree-branch">
            <button className={`kb-tree-row${current===null?' selected':''}`} onClick={()=>openFolder(null)}>
              <span className="kb-chev"><IconChevronDown size="small"/></span><IconFolderStroked size="small"/><span className="kb-tree-name">我的知识库</span>
            </button>
            <div className="kb-tree-children">{renderBranch(tree)}</div>
          </div>
        </div>
      </aside>

      <section className="kb-content-pane">
        <div className="kb-content-toolbar">
          <div className="kb-breadcrumb"><IconFolderStroked size="small"/><span>知识库</span>
            {breadcrumb.map((part,i)=><span key={i} style={{display:'inline-flex',alignItems:'center',gap:10}}><span>/</span>{i===breadcrumb.length-1?<strong>{part}</strong>:<span>{part}</span>}</span>)}
          </div>
          <div className="kb-toolbar-right">
            <label className="kb-search"><Input value={keyword} onChange={setKeyword} placeholder="搜索文件或文件夹" suffix={<IconSearch/>}/></label>
            <div className="kb-segmented" aria-label="视图切换">
              <button className={`kb-icon-btn${view==='grid'?' active':''}`} aria-label="网格视图" title="网格视图" onClick={()=>setView('grid')}><IconGridRectangle size="small"/></button>
              <button className={`kb-icon-btn${view==='list'?' active':''}`} aria-label="列表视图" title="列表视图" onClick={()=>setView('list')}><IconList size="small"/></button>
            </div>
            <button className="kb-icon-btn" aria-label="排序" title={ascending?'按名称升序':'按名称降序'} onClick={()=>{setAscending(a=>!a);Toast.success(ascending?'已按名称降序排列':'已按名称升序排列');}}>{ascending?<IconArrowUp size="small"/>:<IconArrowDown size="small"/>}</button>
            <div className="kb-new-wrap"><Dropdown trigger="click" position="bottomRight" render={<Dropdown.Menu>
              <Dropdown.Item onClick={()=>{setNewFolderName('');setNewFolderOpen(true);}}>新建文件夹</Dropdown.Item>
              <Dropdown.Item onClick={()=>setUploadOpen(true)}>上传文件</Dropdown.Item>
            </Dropdown.Menu>}>
              <button className="kb-new-btn semi-button semi-button-solid"><IconPlus size="small"/><span>新建</span><IconChevronDown size="small"/></button>
            </Dropdown></div>
          </div>
        </div>
        <div className="kb-content-meta"><span>共 {items.length} 项{latest?` · 更新于 ${relTime(latest)}`:''}</span></div>
        <div className="kb-file-area">
          {items.length>0
            ?<div className={`kb-file-grid${view==='list'?' list-view':''}`}>{items.map(renderCard)}</div>
            :<div className="kb-empty">没有匹配的文件或文件夹</div>}
        </div>
      </section>
    </section>

    <Modal title="新建文件夹" visible={newFolderOpen} onCancel={()=>setNewFolderOpen(false)} onOk={()=>{if(newFolderName.trim())createFolder.mutate();else Toast.warning('请输入文件夹名称');}} okText="创建" cancelText="取消" confirmLoading={createFolder.isPending}>
      <label className="field-label">文件夹名称</label>
      <Input value={newFolderName} onChange={setNewFolderName} placeholder="请输入名称" showClear onEnterPress={()=>{if(newFolderName.trim())createFolder.mutate();}}/>
    </Modal>

    <Modal title="重命名文件" visible={!!renameTarget} onCancel={()=>setRenameTarget(null)} onOk={()=>{if(renameValue.trim())renameFile.mutate({id:renameTarget!.id,name:renameValue});else Toast.warning('请输入文件名称');}} okText="保存" cancelText="取消" confirmLoading={renameFile.isPending}>
      <label className="field-label">文件名称</label>
      <Input value={renameValue} onChange={setRenameValue} placeholder="请输入新名称" showClear/>
    </Modal>

    <Modal title="移动文件" visible={!!moveTarget} onCancel={()=>setMoveTarget(null)} onOk={()=>moveFile.mutate({id:moveTarget!.id,folderId:moveFolderId==='root'?null:moveFolderId})} okText="移动" cancelText="取消" confirmLoading={moveFile.isPending}>
      <label className="field-label">目标位置</label>
      <TreeSelect style={{width:'100%'}} value={moveFolderId} onChange={v=>setMoveFolderId(String(v))} treeData={[{label:'我的知识库',value:'root',key:'root',children:toTreeData(tree)}]} defaultExpandAll/>
    </Modal>

    <Modal title="上传文件" visible={uploadOpen} onCancel={()=>setUploadOpen(false)} footer={null} width={560}>
      <Upload draggable multiple action="" dragMainText="点击或拖拽文件到此处上传" dragSubText="支持 PDF、Office、Markdown、图片等各类文件，多文件并行上传" customRequest={({fileInstance,onProgress,onSuccess,onError})=>{
        uploadKnowledgeFile(current,fileInstance,percent=>onProgress({total:100,loaded:percent}))
          .then(()=>{onSuccess({});client.invalidateQueries({queryKey:['knowledge-files']});})
          .catch((e:Error)=>{onError({status:0});Toast.error(`上传失败：${e.message}`);});
      }}/>
    </Modal>
  </div>;
}
