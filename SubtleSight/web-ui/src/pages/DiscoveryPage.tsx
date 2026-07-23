import React, { useState } from 'react';
import type { ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Input, Modal, TabPane, Tabs, Tag, Toast } from '@douyinfe/semi-ui';
import { IconPlus, IconSearch } from '@douyinfe/semi-icons';
import { get, post } from '../api/client';
import type { FeedItem, SavedView, ViewType } from '../types';
import { PageHeader } from '../components/PageHeader';
import { StatePanel } from '../components/StatePanel';
import { StoryCard } from '../components/StoryCard';

const tabs: [ViewType, string, string][] = [
  ['FOR_YOU', '为你发现', '根据关注与反馈'],
  ['EMERGING', '正在发生', '跨来源快速扩散'],
  ['IMPORTANT', '重要情报', '影响与证据优先'],
  ['LATEST', '最新收录', '不等待模型增强'],
];

export function DiscoveryPage(): ReactNode {
  const client = useQueryClient();
  const [view, setView] = useState<ViewType>('FOR_YOU');
  const [query, setQuery] = useState('');
  const [discovering, setDiscovering] = useState(false);
  const [modal, setModal] = useState(false);
  const [viewName, setViewName] = useState('');
  const [expression, setExpression] = useState('topic:"AI Agent" AND NOT tier:SOCIAL');

  const feed = useQuery({ queryKey: ['feed', view], queryFn: () => get<FeedItem[]>(`/feed/${view}?limit=30`) });
  const views = useQuery<SavedView[]>({ queryKey: ['views'], queryFn: () => get<SavedView[]>('/views') });
  const createView = useMutation({
    mutationFn: () => post('/views', { name: viewName, expression }),
    onSuccess: () => { client.invalidateQueries({ queryKey: ['views'] }); setModal(false); Toast.success('自定义视图已创建'); },
  });

  const search = async () => {
    if (!query.trim()) return;
    setDiscovering(true);
    try {
      const local: unknown[] = await get<unknown[]>(`/search?q=${encodeURIComponent(query)}&limit=30`);
      if (local.length === 0) await post('/discovery', { seed: query, limit: 50 });
      Toast.success(local.length ? `本地找到 ${local.length} 项` : '已启动全网发现');
    } catch (e) {
      Toast.error((e as Error).message);
    } finally {
      setDiscovering(false);
    }
  };

  const savedViews = (views.data ?? []) as SavedView[];
  const savedViewTags = savedViews.map((v: SavedView) =>
    React.createElement(Tag, { key: v.id, size: 'large' as const, color: 'blue' as const }, v.name),
  );

  const feedItems = (feed.data ?? []) as FeedItem[];
  const storyCards: React.ReactNode[] = [];
  for (const item of feedItems) {
    storyCards.push(React.createElement(StoryCard as any, { key: item.story.id, item }));
  }

  return (
    <>
      <PageHeader
        eyebrow="DISCOVERY STREAM"
        title="发现"
        description="同一内容池的多种观察视角；重要性不由社区声量单独决定。"
        actions={<Button icon={<IconPlus />} onClick={() => setModal(true)}>新建自定义视图</Button>}
      />
      <div className="search-command">
        <Input
          prefix={<IconSearch />}
          value={query}
          onChange={setQuery}
          onEnterPress={search}
          placeholder="搜索本地情报；若无结果，继续 Search the Web"
          size="large"
          suffix={<Button theme="solid" loading={discovering} onClick={search}>搜索</Button>}
        />
      </div>
      <Tabs activeKey={view} onChange={key => setView(key as ViewType)} className="view-tabs">
        {tabs.map(([key, label, tip]) => (
          <TabPane
            key={key}
            tab={React.createElement('span', null, label, React.createElement('small', null, tip))}
            itemKey={key}
          />
        ))}
      </Tabs>
      {savedViews.length > 0 && (
        <div className="saved-view-row">
          <span>自定义视图</span>
          {savedViewTags}
        </div>
      )}
      <StatePanel loading={feed.isLoading} error={feed.error} empty={feedItems.length === 0} onRetry={() => feed.refetch()}>
        <div className="story-grid">
          {storyCards}
        </div>
      </StatePanel>
      <Modal
        title="创建自定义视图"
        visible={modal}
        onCancel={() => setModal(false)}
        onOk={() => createView.mutate()}
        confirmLoading={createView.isPending}
      >
        <label className="field-label">视图名称</label>
        <Input value={viewName} onChange={setViewName} placeholder="例如：AI Agent 一级来源" />
        <label className="field-label">安全规则表达式</label>
        <Input value={expression} onChange={setExpression} />
        <p className="form-hint">支持 topic、entity、source、tier、language、score、age 和 status；表达式会解析为 AST，不执行 SQL。</p>
      </Modal>
    </>
  );
}
