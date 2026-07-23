import { jsx as _jsx, jsxs as _jsxs, Fragment as _Fragment } from "react/jsx-runtime";
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Input, Modal, TabPane, Tabs, Tag, Toast } from '@douyinfe/semi-ui';
import { IconPlus, IconSearch } from '@douyinfe/semi-icons';
import { get, post } from '../api/client';
import { PageHeader } from '../components/PageHeader';
import { StatePanel } from '../components/StatePanel';
import { StoryCard } from '../components/StoryCard';
const tabs = [['FOR_YOU', '为你发现', '根据关注与反馈'], ['EMERGING', '正在发生', '跨来源快速扩散'], ['IMPORTANT', '重要情报', '影响与证据优先'], ['LATEST', '最新收录', '不等待模型增强']];
export function DiscoveryPage() { const client = useQueryClient(); const [view, setView] = useState('FOR_YOU'), [query, setQuery] = useState(''), [discovering, setDiscovering] = useState(false), [modal, setModal] = useState(false), [viewName, setViewName] = useState(''), [expression, setExpression] = useState('topic:"AI Agent" AND NOT tier:SOCIAL'); const feed = useQuery({ queryKey: ['feed', view], queryFn: () => get(`/feed/${view}?limit=30`) }); const views = useQuery({ queryKey: ['views'], queryFn: () => get('/views') }); const createView = useMutation({ mutationFn: () => post('/views', { name: viewName, expression }), onSuccess: () => { client.invalidateQueries({ queryKey: ['views'] }); setModal(false); Toast.success('自定义视图已创建'); } }); const search = async () => { if (!query.trim())
    return; setDiscovering(true); try {
    const local = await get(`/search?q=${encodeURIComponent(query)}&limit=30`);
    if (local.length === 0)
        await post('/discovery', { seed: query, limit: 50 });
    Toast.success(local.length ? `本地找到 ${local.length} 项` : '已启动全网发现');
}
catch (e) {
    Toast.error(e.message);
}
finally {
    setDiscovering(false);
} }; return _jsxs(_Fragment, { children: [_jsx(PageHeader, { eyebrow: "DISCOVERY STREAM", title: "\u53D1\u73B0", description: "\u540C\u4E00\u5185\u5BB9\u6C60\u7684\u591A\u79CD\u89C2\u5BDF\u89C6\u89D2\uFF1B\u91CD\u8981\u6027\u4E0D\u7531\u793E\u533A\u58F0\u91CF\u5355\u72EC\u51B3\u5B9A\u3002", actions: _jsx(Button, { icon: _jsx(IconPlus, {}), onClick: () => setModal(true), children: "\u65B0\u5EFA\u81EA\u5B9A\u4E49\u89C6\u56FE" }) }), _jsx("div", { className: "search-command", children: _jsx(Input, { prefix: _jsx(IconSearch, {}), value: query, onChange: setQuery, onEnterPress: search, placeholder: "\u641C\u7D22\u672C\u5730\u60C5\u62A5\uFF1B\u82E5\u65E0\u7ED3\u679C\uFF0C\u7EE7\u7EED Search the Web", size: "large", suffix: _jsx(Button, { theme: "solid", loading: discovering, onClick: search, children: "\u641C\u7D22" }) }) }), _jsx(Tabs, { activeKey: view, onChange: key => setView(key), className: "view-tabs", children: tabs.map(([key, label, tip]) => _jsx(TabPane, { tab: _jsxs("span", { children: [label, _jsx("small", { children: tip })] }), itemKey: key }, key)) }), views.data && views.data.length > 0 && _jsxs("div", { className: "saved-view-row", children: [_jsx("span", { children: "\u81EA\u5B9A\u4E49\u89C6\u56FE" }), views.data.map(v => _jsx(Tag, { size: "large", color: "blue", children: v.name }, v.id))] }), _jsx(StatePanel, { loading: feed.isLoading, error: feed.error, empty: !feed.data?.length, onRetry: () => feed.refetch(), children: _jsx("div", { className: "story-grid", children: feed.data?.map(item => _jsx(StoryCard, { item: item }, item.story.id)) }) }), _jsxs(Modal, { title: "\u521B\u5EFA\u81EA\u5B9A\u4E49\u89C6\u56FE", visible: modal, onCancel: () => setModal(false), onOk: () => createView.mutate(), confirmLoading: createView.isPending, children: [_jsx("label", { className: "field-label", children: "\u89C6\u56FE\u540D\u79F0" }), _jsx(Input, { value: viewName, onChange: setViewName, placeholder: "\u4F8B\u5982\uFF1AAI Agent \u4E00\u7EA7\u6765\u6E90" }), _jsx("label", { className: "field-label", children: "\u5B89\u5168\u89C4\u5219\u8868\u8FBE\u5F0F" }), _jsx(Input, { value: expression, onChange: setExpression }), _jsx("p", { className: "form-hint", children: "\u652F\u6301 topic\u3001entity\u3001source\u3001tier\u3001language\u3001score\u3001age \u548C status\uFF1B\u8868\u8FBE\u5F0F\u4F1A\u89E3\u6790\u4E3A AST\uFF0C\u4E0D\u6267\u884C SQL\u3002" })] })] }); }
