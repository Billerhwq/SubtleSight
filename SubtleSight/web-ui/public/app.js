const page = document.documentElement.dataset.page || 'discover';

const navGroups = [
  { label: '工作区', items: [
    ['discover', 'discover.html', 'radar', '发现'],
    ['knowledge', 'knowledge.html', 'folder-open', '知识库'],
    ['calendar', 'calendar.html', 'calendar-days', '财经日历'],
    ['watchlist', 'watchlist.html', 'eye', 'Watchlist', '4']
  ]},
  { label: '输出', items: [
    ['reports', 'reports.html', 'file-text', '报告', '3'],
    ['views', 'views.html', 'list-filter', '自定义视图']
  ]},
  { label: '系统', items: [
    ['sources', 'sources.html', 'rss', '信源', '2'],
    ['tasks', 'tasks.html', 'activity', '任务中心', '5'],
    ['settings', 'settings.html', 'settings-2', '设置']
  ]}
];

const signals = [
  {
    source: 'GitHub Release', domain: 'github.com', title: 'OpenAI Agents SDK 发布动态',
    summary: '该事件聚合官方仓库、文档更新与开发者讨论，当前已有多个独立来源可供核验。',
    tags: [['Agent', 'blue'], ['多源 6', 'green'], ['官方来源', 'orange']], score: '92', reason: '命中 Watchlist', trend: '+18%', depth: '4 个来源族'
  },
  {
    source: 'arXiv', domain: 'arxiv.org', title: 'Agent 长期记忆评测研究更新',
    summary: '论文、代码仓库与后续讨论已归并为同一 Story，可查看实验设置、结论与反证。',
    tags: [['论文', 'blue'], ['代码可用', 'green'], ['新实体', 'amber']], score: '88', reason: '研究相关', trend: '+11%', depth: '论文 + 代码'
  },
  {
    source: 'Anthropic Docs', domain: 'anthropic.com', title: 'Model Context Protocol 文档变更',
    summary: '系统检测到官方文档内容变化，并与上一次 Watchlist 基线进行了字段和语义比较。',
    tags: [['MCP', 'blue'], ['文档变化', 'amber'], ['一级来源', 'orange']], score: '85', reason: '基线变化', trend: '+7%', depth: '官方文档'
  },
  {
    source: 'Hacker News', domain: 'news.ycombinator.com', title: 'Agent 工具调用可靠性讨论升温',
    summary: '社区讨论提供了新的失败案例和待核验问题，尚未作为确定事实写入知识库。',
    tags: [['社区线索', 'amber'], ['正在发生', 'orange'], ['待核验', 'red']], score: '79', reason: '跨源加速', trend: '+23%', depth: '待核验线索'
  }
];

const favicon = domain => `https://www.google.com/s2/favicons?domain=${domain}&sz=32`;
const icon = name => `<i data-lucide="${name}" aria-hidden="true"></i>`;
const tagsHtml = tags => tags.map(([label, color]) => `<span class="tag ${color || ''}">${escapeHtml(label)}</span>`).join('');

const discoveryScopes = {
  recommended: {
    label: '今日推荐', count: 248, icon: 'sparkles',
    platforms: [{ label: '全平台', icon: 'globe-2' }, { label: 'GitHub', domain: 'github.com' }, { label: '微博', domain: 'weibo.com' }, { label: 'B站', domain: 'bilibili.com' }, { label: '雪球', domain: 'xueqiu.com' }],
    topics: ['我的关注', '快速上升', '高质量来源'],
    available: ['我的关注', '快速上升', '高质量来源', '首次出现', '多平台共振', '适合深度研究', '值得持续跟踪', '与工作相关']
  },
  tech: {
    label: '技术与开源', count: 126, icon: 'code-2',
    platforms: [{ label: '全平台', icon: 'globe-2' }, { label: 'GitHub', domain: 'github.com' }, { label: 'Hacker News', domain: 'news.ycombinator.com' }, { label: 'Product Hunt', domain: 'producthunt.com' }, { label: 'arXiv', domain: 'arxiv.org' }, { label: 'Hugging Face', domain: 'huggingface.co' }],
    topics: ['Agent / AI 开发', 'Java / 后端', 'AI Infra', '开源新项目'],
    available: ['Agent / AI 开发', '大模型 / LLM', 'Java / 后端', '前端 / Web', 'AI Infra', '数据工程 / RAG', '数据库', '云原生', '安全工程', '开源新项目', '开发者工具', '机器人']
  },
  society: {
    label: '社会热点', count: 84, icon: 'newspaper',
    platforms: [{ label: '全平台', icon: 'globe-2' }, { label: '微博', domain: 'weibo.com' }, { label: '知乎', domain: 'zhihu.com' }, { label: 'Reddit', domain: 'reddit.com' }, { label: '主流媒体', icon: 'newspaper' }, { label: '新闻聚合', domain: 'news.google.com' }],
    topics: ['科技事件', '公共政策', '消费趋势', '突发事件'],
    available: ['科技事件', '公共政策', '消费趋势', '教育', '医疗', '就业', '城市生活', '突发事件', '国际热点', '舆情变化', '事实核查', '多平台共振']
  },
  news: {
    label: '新闻事件', count: 108, icon: 'radio',
    platforms: [{ label: '全平台', icon: 'globe-2' }, { label: '主流媒体', icon: 'newspaper' }, { label: 'Reuters', domain: 'reuters.com' }, { label: '科技媒体', domain: 'techcrunch.com' }, { label: '财经媒体', domain: 'eastmoney.com' }, { label: '官方发布', icon: 'landmark' }],
    topics: ['科技新闻', '国际事件', '公共政策', '财经事件'],
    available: ['科技新闻', '国际事件', '公共政策', '财经事件', '公司动态', '产品发布', '突发新闻', '事实核查', '各方回应', '持续更新', '深度报道', '官方通报']
  },
  video: {
    label: '视频热榜', count: 63, icon: 'clapperboard',
    platforms: [{ label: '全平台', icon: 'globe-2' }, { label: 'Internet Archive', domain: 'archive.org' }, { label: 'B站', domain: 'bilibili.com' }, { label: '抖音', domain: 'douyin.com' }, { label: 'YouTube', domain: 'youtube.com' }, { label: '小红书', domain: 'xiaohongshu.com' }, { label: 'TikTok', domain: 'tiktok.com' }],
    topics: ['AI 工具', '科技数码', '商业财经', '内容创作'],
    available: ['AI 工具', '科技数码', '商业财经', '内容创作', '知识科普', '生活方式', '产品测评', '教程', '访谈', '短视频爆款', '长视频佳作', '跨平台上升']
  },
  finance: {
    label: '财经市场', count: 91, icon: 'chart-candlestick',
    platforms: [{ label: '全平台', icon: 'globe-2' }, { label: '雪球', domain: 'xueqiu.com' }, { label: '东方财富', domain: 'eastmoney.com' }, { label: 'TradingView', domain: 'tradingview.com' }, { label: 'Reuters', domain: 'reuters.com' }, { label: '公司公告', icon: 'landmark' }],
    topics: ['A 股', '美股', '科技股', 'AI / 算力'],
    available: ['A 股', '港股', '美股', '科技股', 'AI / 算力', '新能源', '消费', '医药', '指数异动', '公司公告', '财报', '宏观政策']
  },
  research: {
    label: '学术研究', count: 57, icon: 'flask-conical',
    platforms: [{ label: '全平台', icon: 'globe-2' }, { label: 'arXiv', domain: 'arxiv.org' }, { label: 'Google Scholar', domain: 'scholar.google.com' }, { label: 'Semantic Scholar', domain: 'semanticscholar.org' }, { label: 'Papers with Code', domain: 'paperswithcode.com' }],
    topics: ['Agent 研究', '评测基准', '多模态', 'RAG'],
    available: ['Agent 研究', '基础模型', '评测基准', '多模态', 'RAG', 'AI 安全', '具身智能', '推理优化', '新数据集', '代码已公开', '高引用', '可复现']
  }
};

const rankingProfiles = {
  recommended: { title: '今日综合热榜', description: '综合多个领域的增长速度、平台覆盖与内容质量。', modes: ['综合热度', '快速上升', '新上榜'], metrics: [['96', '综合热度'], ['91', '综合热度'], ['88', '综合热度'], ['84', '综合热度'], ['81', '综合热度']] },
  tech: { title: '技术与开源热榜', description: '综合项目增长、开发者讨论、维护活跃度与主题匹配度。', modes: ['综合', 'Star 增长', '新上榜'], metrics: [['+1.8k', '今日 Star'], ['+1.2k', '今日 Star'], ['+860', '今日 Star'], ['+640', '今日 Star'], ['+420', '今日 Star']] },
  society: { title: '社会热点榜', description: '综合传播速度、跨平台覆盖、权威来源与事实核验状态。', modes: ['综合', '热度', '新上榜'], metrics: [['982万', '讨论热度'], ['746万', '讨论热度'], ['615万', '讨论热度'], ['538万', '讨论热度'], ['426万', '讨论热度']] },
  news: { title: '新闻事件榜', description: '综合新闻时效、来源覆盖、事件重要度与持续更新程度。', modes: ['综合', '最新', '持续更新'], metrics: [['96', '事件热度'], ['91', '事件热度'], ['87', '事件热度'], ['83', '事件热度'], ['79', '事件热度']] },
  video: { title: '视频爆款榜', description: '综合播放增速、互动率、跨平台传播与内容完成度。', modes: ['综合', '播放增速', '互动率'], metrics: [['326万', '播放'], ['218万', '播放'], ['176万', '播放'], ['139万', '播放'], ['108万', '播放']] },
  finance: { title: '财经市场热榜', description: '综合涨跌幅、成交活跃度、公告事件与市场关注变化。', modes: ['市场异动', '成交额', '关注度'], metrics: [['+4.8%', '涨跌幅'], ['+3.6%', '涨跌幅'], ['+2.9%', '涨跌幅'], ['+2.3%', '涨跌幅'], ['+1.8%', '涨跌幅']] },
  research: { title: '学术研究热榜', description: '综合论文关注、引用增长、代码开放与可复现程度。', modes: ['综合', '引用增长', '代码公开'], metrics: [['+42', '新增引用'], ['+31', '新增引用'], ['+24', '新增引用'], ['+18', '新增引用'], ['+15', '新增引用']] }
};

let discoveryChannelSignals = {};
let liveTrendItems = [];
let liveFeedItems = [];

function platformOptionsHtml(scope) {
  return scope.platforms.map((platform, index) => `<button class="platform-option ${index === 0 ? 'active' : ''}" data-platform="${platform.label}">${platform.domain ? `<img src="${favicon(platform.domain)}" width="16" height="16" alt="">` : icon(platform.icon)}<span>${platform.label}</span></button>`).join('');
}

function selectedTopicsHtml(topics) {
  return topics.map(topic => `<span class="selected-topic"><span>${topic}</span><button class="topic-remove" aria-label="移除 ${topic}" title="移除">${icon('x')}</button></span>`).join('');
}

const platformDomains = {
  GitHub: ['github.com'],
  'Hacker News': ['news.ycombinator.com'],
  'Product Hunt': ['producthunt.com', 'www.producthunt.com'],
  arXiv: ['arxiv.org'],
  'Hugging Face': ['huggingface.co'],
  'Internet Archive': ['archive.org'],
  TechCrunch: ['techcrunch.com'],
  Lobsters: ['lobste.rs']
};

function matchesPlatform(item, platform = '全平台') {
  if (platform === '全平台') return true;
  const domains = platformDomains[platform] || [];
  const domain = String(item.domain || '').toLowerCase();
  const source = String(item.source || item.sourceName || '').toLowerCase();
  const itemPlatform = String(item.platform || '').toLowerCase();
  const label = platform.toLowerCase();
  return itemPlatform === label || source.includes(label) || domains.some(value => domain === value || domain.endsWith(`.${value}`) || source.includes(value));
}

function emptyLiveHtml(platform = '全平台') {
  return `<article class="intel-row selected"><div class="intel-main"><div class="intel-kicker"><strong>真实采集结果</strong><span>· 刚刚检查</span><span class="verification verify">暂无新增</span></div><h2>${escapeHtml(platform)} 暂无真实入库条目</h2><p>没有回退到原型静态数据。可以点击“刷新”触发真实信源采集，或稍后等待后台定时采集。</p></div></article>`;
}

function topicOptionsHtml(scope) {
  return scope.available.map((topic, index) => `<label class="check-row"><input type="checkbox" ${scope.topics.includes(topic) || index < 2 ? 'checked' : ''}>${topic}</label>`).join('');
}

function detailTypeForItem(item) {
  if (item.detailType) return item.detailType;
  if (['B站', '抖音', 'YouTube', '小红书', 'TikTok'].includes(item.platform)) return 'video';
  if (['雪球', '东方财富', 'TradingView', 'Reuters', '公司公告'].includes(item.platform)) return 'finance';
  if (['arXiv', 'Hugging Face', 'Google Scholar', 'Semantic Scholar', 'Papers with Code'].includes(item.platform)) return 'paper';
  if (['微博', '知乎', 'Reddit', '新闻聚合'].includes(item.platform)) return 'social';
  if (item.domain === 'github.com' && item.title.includes('/')) return 'project';
  return 'news';
}

const detailUrl = item => item.mediaUrl || '#';

function sidebar() {
  return `
    <aside class="sidebar" id="sidebar">
      <a class="brand" href="discover.html">
        <span class="brand-mark">S</span><span class="brand-name">SubtleSight</span><span class="prototype-badge">LIVE</span>
      </a>
      <nav class="nav" aria-label="主导航">
        ${navGroups.map(group => `
          <div class="nav-label">${group.label}</div>
          ${group.items.map(([key, href, ico, label, count]) => `
            <a class="nav-link ${page === key || (page === 'story' && key === 'discover') ? 'active' : ''}" href="${href}">
              ${icon(ico)}<span>${label}</span>${count ? `<span class="nav-count">${count}</span>` : ''}
            </a>`).join('')}
        `).join('')}
      </nav>
      <div class="sidebar-footer">
        <div class="status-row"><span class="status-dot"></span><span>服务运行正常</span></div>
        <div class="status-row">${icon('database')}<span>索引已同步</span></div>
      </div>
    </aside>`;
}

function topbar() {
  return `
    <header class="topbar">
      <button class="icon-button mobile-menu" id="menuButton" aria-label="打开导航" title="打开导航">${icon('menu')}</button>
      <span class="sample-label">Live · 本机数据</span>
      <div class="topbar-spacer"></div>
      <button class="icon-button" data-toast="暂无新提醒" aria-label="提醒" title="提醒">${icon('bell')}</button>
      <span class="avatar" aria-label="当前用户">SS</span>
    </header>`;
}

function agentDrawer() {
  return '';
}

function modal() {
  return `
    <div class="modal-layer" id="sourceModal" role="dialog" aria-modal="true" aria-labelledby="sourceModalTitle">
      <div class="modal">
        <div class="modal-header"><span class="modal-title" id="sourceModalTitle">添加信源</span><button class="icon-button" data-close-modal aria-label="关闭" title="关闭">${icon('x')}</button></div>
        <div class="modal-body">
          <div class="form-row"><label class="form-label" for="sourceType">类型</label><select id="sourceType" class="form-control"><option>RSS / Atom</option><option>网站</option><option>GitHub 仓库</option><option>arXiv 查询</option><option>公开 API</option></select></div>
          <div class="form-row"><label class="form-label" for="sourceUrl">地址</label><div><input id="sourceUrl" class="form-control" type="url" placeholder="https://example.com/feed.xml…"><div class="form-help">保存前会检查格式、可访问性与重复来源。</div></div></div>
          <div class="form-row"><label class="form-label" for="sourceSchedule">采集计划</label><select id="sourceSchedule" class="form-control"><option>每 30 分钟</option><option>每 2 小时</option><option>每天</option><option>手动</option></select></div>
        </div>
        <div class="modal-footer"><button class="btn" data-close-modal>取消</button><button class="btn primary" data-save-source>检查并添加</button></div>
      </div>
    </div>
    <div class="modal-layer" id="topicModal" role="dialog" aria-modal="true" aria-labelledby="topicModalTitle">
      <div class="modal topic-modal">
        <div class="modal-header"><span class="modal-title" id="topicModalTitle">筛选技术与开源主题</span><button class="icon-button" data-close-modal aria-label="关闭" title="关闭">${icon('x')}</button></div>
        <div class="modal-body">
          <div class="topic-modal-summary"><span class="tag orange" id="topicModalCount">已选 4 项</span><span class="meta">当前领域：<strong id="topicModalChannel">技术与开源</strong>。主题用于决定推荐内容，不限制具体平台。</span></div>
          <section class="topic-picker"><h3>选择你关心的主题</h3><div class="topic-option-grid" id="topicModalOptions">${topicOptionsHtml(discoveryScopes.tech)}</div></section>
          <section class="topic-preferences"><h3>推荐偏好</h3><div class="preference-options">${['优先看快速上升', '优先看多来源确认', '排除营销与搬运', '允许探索相邻主题'].map((x,i)=>`<label class="check-row"><input type="checkbox" ${i<3?'checked':''}>${x}</label>`).join('')}</div></section>
        </div>
        <div class="modal-footer"><button class="btn" data-toast="主题选择已重置">重置</button><button class="btn" data-close-modal>取消</button><button class="btn primary" data-apply-topics>应用主题</button></div>
      </div>
    </div>`;
}

function shell(content) {
  document.getElementById('app').innerHTML = `<div class="app-shell">${sidebar()}${topbar()}<main class="main">${content}</main>${agentDrawer()}${modal()}<div class="toast" id="toast" role="status" aria-live="polite">${icon('check-circle-2')}<span id="toastText"></span></div></div>`;
}

function header(eyebrow, title, subtitle, actions = '') {
  return `<div class="page-header"><div class="page-title-wrap"><div class="page-eyebrow">${eyebrow}</div><h1 class="page-title">${title}</h1><p class="page-subtitle">${subtitle}</p></div><div class="page-actions">${actions}</div></div>`;
}

function signalRow(item, index) {
  return `
    <article class="signal-row">
      <div class="signal-rank">${String(index + 1).padStart(2, '0')}</div>
      <div class="signal-body">
        <div class="signal-kicker"><img class="source-logo" src="${favicon(item.domain)}" width="17" height="17" alt=""><span class="source-name">${item.source}</span><span class="meta">· 示例事件</span></div>
        <h2 class="signal-title"><a href="#">${item.title}</a></h2>
        <p class="signal-summary">${item.summary}</p>
        <div class="signal-tags">${tagsHtml(item.tags)}</div>
      </div>
      <div class="signal-score">
        <div class="score-card"><span class="score-value">${item.score}</span><div class="score-label">综合信号</div></div>
        <div class="signal-meta-stack"><span>${item.trend || '+0%'}</span><span>${item.depth || item.reason}</span></div>
        <div class="row-actions"><button class="icon-button" aria-label="更多" title="更多">${icon('more-horizontal')}</button></div>
      </div>
    </article>`;
}

function signalInboxRow(item, index) {
  const businessRows = [
    { priority: '高优先', tone: 'high', age: '38 分钟前', coverage: '4 个独立来源', status: '多源确认', owner: '产品 / 研发', impact: '将影响 Agent 产品路线与 SDK 选型', action: '本周安排技术评估', sources: ['github.com', 'openai.com', 'news.ycombinator.com'] },
    { priority: '高优先', tone: 'high', age: '1 小时前', coverage: '3 个独立来源', status: '研究可复核', owner: 'AI 研究 / 平台', impact: '可能改变长期记忆能力的评估口径', action: '发起深度研究，跟踪复现代码', sources: ['arxiv.org', 'github.com', 'huggingface.co'] },
    { priority: '值得看', tone: 'medium', age: '2 小时前', coverage: '2 个独立来源', status: '官方已确认', owner: '平台架构 / 生态', impact: 'MCP 的能力边界和接入方式发生变化', action: '更新持续跟踪的判断基线', sources: ['anthropic.com', 'github.com'] },
    { priority: '待核实', tone: 'verify', age: '2 小时前', coverage: '18 条讨论', status: '等待佐证', owner: '工程效能', impact: '工具调用稳定性可能仍有实际风险', action: '收集失败样本，等待交叉验证', sources: ['news.ycombinator.com', 'reddit.com'] },
    { priority: '值得看', tone: 'medium', age: '3 小时前', coverage: '2 个关联页面', status: '官方已确认', owner: 'API 平台 / 方案', impact: '官方示例变化可能影响客户方案文档', action: '同步知识库并标记文档变化', sources: ['openai.com', 'github.com'] },
    { priority: '趋势', tone: 'trend', age: '4 小时前', coverage: '12 个相关仓库', status: '趋势明确', owner: '开源生态 / 技术雷达', impact: 'MCP 工具供给快速增长，选型成本上升', action: '归并同类项目，筛选可用仓库', sources: ['github.com', 'mcp.so'] },
    { priority: '值得看', tone: 'medium', age: '5 小时前', coverage: '论文 + 数据集', status: '可复核', owner: '模型平台 / 评测', impact: '可补充工具调用能力的横向对比依据', action: '加入评测资料库', sources: ['huggingface.co', 'arxiv.org'] },
    { priority: '观察', tone: 'low', age: '6 小时前', coverage: '6 家媒体报道', status: '事实已确认', owner: '战略 / 投资', impact: 'AI 编程助手赛道出现新融资与产品动作', action: '归入产业观察，暂不发起研究', sources: ['techcrunch.com', 'reuters.com'] },
    { priority: '待核实', tone: 'verify', age: '7 小时前', coverage: '32 条讨论', status: '社区线索', owner: '产品体验 / 开发者关系', impact: '真实使用反馈暴露了调试流程痛点', action: '保留线索，等待官方回应', sources: ['reddit.com', 'github.com'] },
    { priority: '观察', tone: 'low', age: '昨天', coverage: '论文 + 代码', status: '研究可复核', owner: '研究团队', impact: '协作指标可能影响后续报告框架', action: '保存论文并观察后续引用', sources: ['arxiv.org', 'github.com'] }
  ];
  const intel = item.intel || businessRows[index % businessRows.length];
  const sourceStack = (intel.sources || [item.domain || 'local']).map(domain => `<img src="${favicon(domain)}" width="18" height="18" alt="">`).join('');
  const heat = (item.trend || '+0%').replace('+', '');
  const mediaPreview = item.mediaUrl ? `<div class="inline-video-shell"><video class="inline-video" controls preload="metadata" src="${escapeHtml(item.mediaUrl)}" ${item.mediaType ? `type="${escapeHtml(item.mediaType)}"` : ''}></video><span>已下载到本机 blob，可直接播放</span></div>` : '';
  return `
    <article class="intel-row ${index === 0 ? 'selected' : ''}">
      <label class="intel-check"><input type="checkbox" aria-label="选择 ${escapeHtml(item.title)}"></label>
      <div class="intel-priority ${intel.tone}">
        <span class="priority-label" title="根据热度、时效与来源质量综合判断">${escapeHtml(intel.priority)}</span>
        <span class="priority-trend">${escapeHtml(intel.age)}</span>
      </div>
      <div class="intel-main">
        <div class="intel-kicker">
          <img class="source-logo" src="${favicon(item.domain)}" width="17" height="17" alt="">
          <strong>${escapeHtml(item.source)}</strong><span>· ${escapeHtml(intel.age)}</span>
          <span class="meta-divider"></span>
          <span class="source-stack">${sourceStack}</span>
          <span>${escapeHtml(intel.coverage)}</span>
          <span class="verification ${intel.tone}">${escapeHtml(intel.status)}</span>
        </div>
        <h2><a href="${detailUrl(item)}">${escapeHtml(item.title)}</a></h2>
        <p>${escapeHtml(item.summary)}</p>
        ${mediaPreview}
        <div class="intel-context">${tagsHtml(item.tags.filter(([label]) => !label.startsWith('多源')).slice(0, 2))}</div>
      </div>
      <div class="intel-meta">
        <span class="intel-meta-label">热度与状态</span>
        <strong>↑ ${escapeHtml(heat)}</strong>
        <span>${escapeHtml(intel.coverage)}</span>
        <span class="verification ${intel.tone}">${escapeHtml(intel.status)}</span>
      </div>
      <div class="intel-actions">
        <button class="icon-button" aria-label="更多" title="更多">${icon('more-horizontal')}</button>
      </div>
    </article>`;
}

function rankingItems(channelKey, platform = '全平台') {
  const allItems = discoveryChannelSignals[channelKey] || [];
  let items = platform === '全平台' ? allItems : allItems.filter(item => item.platform === platform);
  if (!items.length) items = allItems;
  if (channelKey === 'tech' || platform === 'GitHub') {
    items = [...items].sort((a, b) => Number(b.intel?.judgmentLabel === '推荐理由') - Number(a.intel?.judgmentLabel === '推荐理由'));
  } else {
    items = [...items].sort((a, b) => Number.parseFloat(b.trend || 0) - Number.parseFloat(a.trend || 0));
  }
  return items.slice(0, 5);
}

function rankingRowsHtml(channelKey, platform = '全平台') {
  const profile = rankingProfiles[channelKey];
  const rows = rankingItems(channelKey, platform).map((item, index) => {
    const metric = profile.metrics[index] || [item.trend || '上升', '热度变化'];
    return `<article class="ranking-row">
      <span class="ranking-number rank-${index + 1}">${String(index + 1).padStart(2, '0')}</span>
      <div class="ranking-main">
        <a href="${detailUrl(item)}">${item.title}</a>
        <span><img src="${favicon(item.domain)}" width="15" height="15" alt="">${item.source}<i>·</i>${item.tags?.[0]?.[0] || item.depth || '热门内容'}</span>
      </div>
      <span class="ranking-metric"><strong>${metric[0]}</strong><small>${metric[1]}</small></span>
    </article>`;
  }).join('');
  return `${rows}<button class="ranking-more-cell" data-toast="已打开${profile.title}"><span>查看完整热榜</span>${icon('arrow-right')}</button>`;
}

function renderDomainRanking(channelKey, platform = '全平台') {
  if (page === 'discover') {
    renderLiveRanking(platform, channelKey);
    return;
  }
  const profile = rankingProfiles[channelKey];
  if (!profile || !document.getElementById('rankingList')) return;
  document.getElementById('rankingTitle').textContent = platform === '全平台' ? profile.title : `${platform} 热榜`;
  document.getElementById('rankingDescription').textContent = profile.description;
  document.getElementById('rankingModes').innerHTML = profile.modes.map((mode, index) => `<button class="ranking-mode ${index === 0 ? 'active' : ''}">${mode}</button>`).join('');
  document.getElementById('rankingList').innerHTML = rankingRowsHtml(channelKey, platform);
  refreshIcons();
}

function matchesChannel(item, channelKey = 'tech') {
  const platform = String(item.platform || '').toLowerCase();
  const sourceType = String(item.sourceType || '').toUpperCase();
  if (channelKey === 'recommended') return Number(item.score || item.signal?.score || 0) >= 65;
  if (channelKey === 'research') return ['arxiv', 'hugging face'].includes(platform) || ['ARXIV', 'HUGGING_FACE'].includes(sourceType);
  if (channelKey === 'news') return ['hacker news', 'techcrunch', 'lobsters'].includes(platform);
  if (channelKey === 'video') return ['internet archive', 'b站', '抖音', 'youtube', '小红书', 'tiktok'].includes(platform) || sourceType === 'VIDEO' || Boolean(item.mediaUrl);
  if (channelKey === 'tech') return ['github', 'hacker news', 'product hunt', 'arxiv', 'hugging face', 'techcrunch', 'lobsters'].includes(platform);
  return false;
}

function updateLiveChannelCounts() {
  document.querySelectorAll('[data-channel-count]').forEach(element => {
    element.textContent = String(liveFeedItems.filter(item => matchesChannel(item, element.dataset.channelCount)).length);
  });
}

function renderLiveRanking(platform = '全平台', channelKey = 'tech') {
  const list = document.getElementById('rankingList');
  const title = document.getElementById('rankingTitle');
  const description = document.getElementById('rankingDescription');
  const modes = document.getElementById('rankingModes');
  if (!list || !title || !description || !modes) return;
  let rows = liveTrendItems.filter(item => matchesChannel(item, channelKey) && matchesPlatform(item, platform));
  if (!rows.length && platform !== '全平台') {
    rows = liveFeedItems.filter(item => matchesChannel(item, channelKey) && matchesPlatform(item, platform)).map(item => ({
      title: item.title,
      summary: item.summary,
      domain: item.domain,
      source: item.source,
      sourceName: item.sourceName,
      platform: item.platform,
      mediaUrl: item.mediaUrl,
      mediaType: item.mediaType,
      blobHash: item.blobHash,
      metric: `score ${item.score}`,
      metricLabel: '真实 Feed',
      score: item.score,
      reason: item.reason,
      url: '#'
    }));
  }
  title.textContent = platform === '全平台' ? '真实信源热榜' : `${platform} 真实热榜`;
  description.textContent = '来自后端真实采集入库的 Story 信号，按综合信号分排序。';
  modes.innerHTML = ['综合信号', '新收录', '来源覆盖'].map((mode, index) => `<button class="ranking-mode ${index === 0 ? 'active' : ''}">${mode}</button>`).join('');
  if (!rows.length) {
    list.innerHTML = `<article class="ranking-row"><span class="ranking-number">--</span><div class="ranking-main"><a>${escapeHtml(platform)} 暂无真实入库</a><span>没有回退到原型榜单</span></div><span class="ranking-metric"><strong>Live</strong><small>等待采集</small></span></article>`;
    refreshIcons();
    return;
  }
  list.innerHTML = rows.slice(0, 8).map((item, index) => {
    const domain = item.domain || 'local';
    return `<article class="ranking-row">
      <span class="ranking-number rank-${index + 1}">${String(index + 1).padStart(2, '0')}</span>
      <div class="ranking-main">
        <a href="${escapeHtml(item.url || '#')}">${escapeHtml(item.title || 'Untitled')}</a>
        <span><img src="${favicon(domain)}" width="15" height="15" alt="">${escapeHtml(item.sourceName || item.source || domain)}<i>·</i>${escapeHtml(item.platform || item.reason || '真实采集')}</span>
      </div>
      <span class="ranking-metric"><strong>${escapeHtml(item.metric || item.score || 'Live')}</strong><small>${escapeHtml(item.metricLabel || '综合信号')}</small></span>
    </article>`;
  }).join('') + `<button class="ranking-more-cell" data-toast="真实热榜来自后端采集"><span>查看完整热榜</span>${icon('arrow-right')}</button>`;
  refreshIcons();
}

function renderDiscoveryList(channelKey, platform = '全平台') {
  const list = document.querySelector('.intel-list');
  if (!list || !discoveryChannelSignals[channelKey]) return;
  if (page === 'discover') {
    const rows = liveFeedItems.filter(item => matchesChannel(item, channelKey) && matchesPlatform(item, platform));
    list.innerHTML = rows.length ? rows.map(signalInboxRow).join('') : emptyLiveHtml(platform);
    const count = document.getElementById('scopeResultCount');
    if (count) count.textContent = String(rows.length);
    refreshIcons();
    return;
  }
  const allItems = discoveryChannelSignals[channelKey];
  let items = platform === '全平台' ? allItems : allItems.filter(item => item.platform === platform);
  if (platform === 'GitHub') items = [...items].sort((a, b) => Number(b.intel?.judgmentLabel === '推荐理由') - Number(a.intel?.judgmentLabel === '推荐理由'));
  list.innerHTML = (items.length ? items : allItems).map(signalInboxRow).join('');
  document.getElementById('scopeResultCount').textContent = platform === '全平台' || !items.length ? discoveryScopes[channelKey].count : items.length;
  refreshIcons();
}

function renderDiscover() {
  const actions = `<button class="btn" data-refresh-live>${icon('refresh-cw')}刷新</button>`;
  const inboxSignals = [
    ...signals,
    { source: 'OpenAI Blog', domain: 'openai.com', title: 'Responses API 工具调用案例更新', summary: '官方示例新增工具编排和状态保存说明，已与既有 Agent SDK Story 合并待核验。', tags: [['API', 'blue'], ['官方来源', 'orange'], ['文档变化', 'amber']], score: '82', reason: '文档变化', trend: '+6%', depth: '官方文档' },
    { source: 'GitHub Trending', domain: 'github.com', title: 'MCP 相关工具仓库增长加速', summary: '过去 24 小时新增多个 MCP 客户端和服务端实现，系统已按语言和用途拆分实体。', tags: [['MCP', 'blue'], ['开源项目', 'green'], ['正在发生', 'orange']], score: '81', reason: '跨源加速', trend: '+14%', depth: '12 个仓库' },
    { source: 'Hugging Face', domain: 'huggingface.co', title: '小模型工具调用评测集合更新', summary: '新数据集补充了多轮工具调用失败案例，适合进入后续标准研究任务。', tags: [['模型', 'blue'], ['评测', 'amber']], score: '78', reason: '研究相关', trend: '+4%', depth: '数据集' },
    { source: 'TechCrunch', domain: 'techcrunch.com', title: 'AI 编程助手融资与产品动态', summary: '多家创业公司动态被聚为同一产业 Story，已排除招聘和营销稿。', tags: [['产业', 'amber'], ['AI Coding', 'blue']], score: '73', reason: '行业信号', trend: '+5%', depth: '新闻源' },
    { source: 'Reddit', domain: 'reddit.com', title: '开发者反馈 Agent 调试体验问题', summary: '讨论提供失败样本和使用场景，不作为确定事实，等待官方 Issue 或复现仓库交叉验证。', tags: [['社区线索', 'amber'], ['待核验', 'red']], score: '69', reason: '待核验', trend: '+9%', depth: '社区线索' },
    { source: 'arXiv', domain: 'arxiv.org', title: '多 Agent 协作基准新论文收录', summary: '论文提出任务分解和通信效率指标，已加入 Agent Research 主题队列。', tags: [['论文', 'blue'], ['新实体', 'amber']], score: '76', reason: '新实体', trend: '+3%', depth: '论文' },
    { source: 'Google AI Blog', domain: 'blog.google', title: '长上下文检索实践文章更新', summary: '文章和示例代码与现有 RAG Watchlist 存在交集，等待实体归并。', tags: [['RAG', 'blue'], ['官方来源', 'orange']], score: '74', reason: '实体归并', trend: '+2%', depth: '博客 + 代码' },
    { source: 'Product Hunt', domain: 'producthunt.com', title: 'AI 研究助理类产品集中发布', summary: '多个产品声明支持自动调研和报告输出，系统仅保留可验证功能描述。', tags: [['产品', 'green'], ['Deep Research', 'blue']], score: '71', reason: '产品线索', trend: '+8%', depth: '产品页' },
    { source: 'GitHub Trending', domain: 'github.com', title: 'browser-use / browser-use', summary: '让 AI Agent 通过浏览器完成网页任务的开源项目，提供 Python 接口、示例与常见模型集成。', tags: [['Python', 'blue'], ['Browser Agent', 'green']], trend: '+26%', intel: { priority: '今日热门', tone: 'high', age: '1 小时前', coverage: '今日增长较快', status: '维护活跃', judgmentLabel: '推荐理由', impact: '浏览器自动化与 Agent 场景结合紧密，示例完整', owner: 'Python / Agent 开发者', ownerIcon: 'github', nextLabel: '适合谁', action: '适合评估网页操作与自动化任务', sources: ['github.com', 'browser-use.com'] } },
    { source: 'GitHub Trending', domain: 'github.com', title: 'langchain-ai / langgraph', summary: '面向有状态、可恢复 Agent 工作流的编排框架，支持持久化、人工介入与多步骤执行。', tags: [['Python', 'blue'], ['Agent 编排', 'green']], trend: '+19%', intel: { priority: '值得收藏', tone: 'medium', age: '2 小时前', coverage: '社区活跃', status: '持续更新', judgmentLabel: '推荐理由', impact: '适合需要状态管理与复杂流程控制的 Agent 项目', owner: 'AI 平台 / 后端开发者', ownerIcon: 'github', nextLabel: '适合谁', action: '适合做框架选型和工作流验证', sources: ['github.com', 'langchain.com'] } },
    { source: 'GitHub Trending', domain: 'github.com', title: 'modelcontextprotocol / servers', summary: 'Model Context Protocol 官方参考服务集合，覆盖文件、数据库、搜索与常见工具接入示例。', tags: [['TypeScript', 'blue'], ['MCP', 'orange']], trend: '+17%', intel: { priority: '值得收藏', tone: 'trend', age: '3 小时前', coverage: '官方参考实现', status: '持续更新', judgmentLabel: '推荐理由', impact: '可快速理解 MCP 服务结构和标准接入方式', owner: '平台架构 / 工具开发者', ownerIcon: 'github', nextLabel: '适合谁', action: '适合作为 MCP 接入与权限设计参考', sources: ['github.com', 'modelcontextprotocol.io'] } }
  ];
  const platformBySource = { 'GitHub Release': 'GitHub', 'GitHub Trending': 'GitHub', 'Hacker News': 'Hacker News', 'Product Hunt': 'Product Hunt', 'arXiv': 'arXiv', 'Hugging Face': 'Hugging Face' };
  inboxSignals.forEach(item => { item.platform = platformBySource[item.source] || '全平台'; });

  const socialSignals = [
    { source: '微博热搜', domain: 'weibo.com', platform: '微博', title: '生成式 AI 进入学习场景的讨论升温', summary: '多个讨论从工具效率延伸到内容真实性、能力评价与使用边界，观点分化正在扩大。', tags: [['科技事件', 'blue'], ['多平台共振', 'orange']], trend: '+32%', intel: { priority: '热议', tone: 'high', age: '26 分钟前', coverage: '3 个平台', status: '持续上升', judgmentLabel: '热点判断', impact: '讨论焦点已从“能不能用”转向“如何规范使用”', owner: '学生 / 教师 / 家长', ownerIcon: 'users', nextLabel: '值得关注', action: '查看观点分布与事实核查', sources: ['weibo.com', 'zhihu.com', 'news.qq.com'] } },
    { source: '知乎', domain: 'zhihu.com', platform: '知乎', title: '公共数据开放与个人隐私边界引发集中讨论', summary: '高赞回答主要围绕数据最小化、用途约束和公开透明度展开，已有媒体跟进解读。', tags: [['公共政策', 'amber'], ['事实核查', 'green']], trend: '+18%', intel: { priority: '值得看', tone: 'medium', age: '1 小时前', coverage: '42 个回答', status: '观点分化', judgmentLabel: '热点判断', impact: '政策解释与公众感受之间仍有明显信息差', owner: '普通公众 / 数据从业者', nextLabel: '建议阅读', action: '先看政策原文，再看代表性观点', sources: ['zhihu.com', 'gov.cn'] } },
    { source: 'Reddit', domain: 'reddit.com', platform: 'Reddit', title: '智能驾驶功能命名与用户认知成为跨社区话题', summary: '讨论集中在功能边界、营销表述和驾驶责任，不同社区对风险的关注点明显不同。', tags: [['消费趋势', 'amber'], ['社区讨论', 'blue']], trend: '+14%', intel: { priority: '观察', tone: 'low', age: '3 小时前', coverage: '5 个社区', status: '跨社区扩散', judgmentLabel: '热点判断', impact: '产品命名正在影响消费者对能力边界的理解', owner: '消费者 / 汽车行业', nextLabel: '继续观察', action: '等待企业回应与权威说明', sources: ['reddit.com', 'weibo.com'] } },
    { source: '主流媒体', domain: 'news.cn', platform: '主流媒体', title: '极端天气相关信息传播速度快速上升', summary: '权威预警、现场信息与用户转发同时增加，系统已将未经证实的视频单独标记。', tags: [['突发事件', 'red'], ['权威信息', 'green']], trend: '+47%', intel: { priority: '高优先', tone: 'high', age: '12 分钟前', coverage: '8 家媒体', status: '快速上升', judgmentLabel: '热点判断', impact: '公共安全信息需要优先核验来源与发布时间', owner: '相关地区公众', nextLabel: '立即查看', action: '优先查看权威预警和时间线', sources: ['news.cn', 'cma.gov.cn'] } },
    { source: '新闻聚合', domain: 'news.google.com', platform: '新闻聚合', title: '消费补贴与以旧换新相关讨论进入上升榜', summary: '政策解读、消费体验与商家执行情况被聚合为同一热点，重复转载已折叠。', tags: [['消费趋势', 'amber'], ['政策解读', 'blue']], trend: '+27%', intel: { priority: '上升', tone: 'trend', age: '2 小时前', coverage: '12 家来源', status: '新上榜', sources: ['news.google.com', 'gov.cn'] } }
  ];

  const videoSignals = [
    { source: 'B站', domain: 'bilibili.com', platform: 'B站', title: '实测多款 AI 编程工具：完整工作流对比', summary: '内容用同一任务对比不同工具，评论区集中补充了失败案例与使用成本。', tags: [['AI 工具', 'blue'], ['实测对比', 'green']], trend: '+68%', intel: { priority: '爆款', tone: 'high', age: '48 分钟前', coverage: '播放快速上升', status: '站内热门', judgmentLabel: '爆款原因', impact: '同题实测、结果对照和失败片段提升了可信度', owner: '开发者 / 科技创作者', ownerIcon: 'play-circle', nextLabel: '可借鉴', action: '拆解开场、对照结构与评论反馈', sources: ['bilibili.com', 'github.com'] } },
    { source: 'YouTube', domain: 'youtube.com', platform: 'YouTube', title: '从零搭建个人知识库的长视频教程', summary: '教程覆盖采集、整理、检索和复盘，章节清晰，观众停留主要集中在实际演示部分。', tags: [['知识科普', 'blue'], ['长视频', 'amber']], trend: '+29%', intel: { priority: '值得看', tone: 'medium', age: '2 小时前', coverage: '互动率较高', status: '稳定上升', judgmentLabel: '内容亮点', impact: '完整过程演示比单纯功能介绍更能建立信任', owner: '知识工作者 / 创作者', ownerIcon: 'play-circle', nextLabel: '可借鉴', action: '保留章节结构与操作演示节奏', sources: ['youtube.com'] } },
    { source: '抖音', domain: 'douyin.com', platform: '抖音', title: 'AI 短视频批量生产工作流拆解', summary: '短视频以结果前置和步骤压缩获得快速传播，但部分效率数据仍缺少可验证依据。', tags: [['短视频爆款', 'orange'], ['待核实', 'red']], trend: '+83%', intel: { priority: '爆款', tone: 'verify', age: '35 分钟前', coverage: '多账号跟拍', status: '快速扩散', judgmentLabel: '爆款原因', impact: '结果前置、强对比和低门槛承诺推动转发', owner: '短视频创作者', ownerIcon: 'play-circle', nextLabel: '注意风险', action: '借鉴表达结构，避免复用未核实数据', sources: ['douyin.com', 'xiaohongshu.com'] } },
    { source: '小红书', domain: 'xiaohongshu.com', platform: '小红书', title: '科技产品桌面工作流内容集中增长', summary: '多篇内容采用场景照片、工具清单和成本说明，收藏率高于评论率。', tags: [['科技数码', 'blue'], ['内容创作', 'green']], trend: '+21%', intel: { priority: '趋势', tone: 'trend', age: '4 小时前', coverage: '24 篇相关内容', status: '持续增长', judgmentLabel: '内容亮点', impact: '可直接复用的清单与真实场景更容易获得收藏', owner: '科技 / 效率创作者', ownerIcon: 'play-circle', nextLabel: '可借鉴', action: '关注清单结构、成本透明与场景图', sources: ['xiaohongshu.com'] } },
    { source: 'B站', domain: 'bilibili.com', platform: 'B站', title: '一周科技新品速览进入站内上升榜', summary: '视频用时间线串联新品发布、价格变化和用户反馈，信息密度较高。', tags: [['科技数码', 'blue'], ['资讯盘点', 'orange']], trend: '+34%', intel: { priority: '上升', tone: 'trend', age: '3 小时前', coverage: '互动增长较快', status: '新上榜', sources: ['bilibili.com'] } }
  ];

  const financeSignals = [
    { source: '雪球', domain: 'xueqiu.com', platform: '雪球', title: 'AI 算力相关板块讨论与成交关注度上升', summary: '市场讨论主要集中在订单可见度、资本开支与估值消化，观点分歧较大。', tags: [['AI / 算力', 'blue'], ['A 股', 'red']], trend: '+24%', intel: { priority: '异动', tone: 'high', age: '18 分钟前', coverage: '6 个相关标的', status: '关注度上升', judgmentLabel: '市场解读', impact: '情绪与基本面线索同时升温，但尚未形成一致预期', owner: 'A 股 / 科技板块', ownerIcon: 'chart-candlestick', nextLabel: '观察要点', action: '核对公告、订单与成交量变化', sources: ['xueqiu.com', 'eastmoney.com'] } },
    { source: 'Reuters', domain: 'reuters.com', platform: 'Reuters', title: '大型科技公司 AI 资本开支成为财报关注重点', summary: '投资者关注算力投入速度、收入转化周期以及自由现金流承压程度。', tags: [['美股', 'blue'], ['财报', 'amber']], trend: '+16%', intel: { priority: '值得看', tone: 'medium', age: '1 小时前', coverage: '多家公司', status: '财报窗口', judgmentLabel: '市场解读', impact: 'AI 投入正从增长叙事转向回报周期比较', owner: '美股 / 大型科技', ownerIcon: 'chart-candlestick', nextLabel: '观察要点', action: '对比资本开支指引与收入增速', sources: ['reuters.com', 'sec.gov'] } },
    { source: 'TradingView', domain: 'tradingview.com', platform: 'TradingView', title: '美股半导体板块盘前波动扩大', summary: '多个核心标的同步波动，需结合公司消息、期权成交和宏观数据判断驱动来源。', tags: [['美股', 'blue'], ['指数异动', 'red']], trend: '+31%', intel: { priority: '异动', tone: 'verify', age: '32 分钟前', coverage: '8 个核心标的', status: '波动扩大', judgmentLabel: '市场解读', impact: '板块联动明显，但驱动因素仍需进一步确认', owner: '美股 / 半导体', ownerIcon: 'chart-candlestick', nextLabel: '风险提示', action: '区分消息驱动与短期交易拥挤', sources: ['tradingview.com', 'nasdaq.com'] } },
    { source: '东方财富', domain: 'eastmoney.com', platform: '东方财富', title: '新能源产业链公司公告集中更新', summary: '公告涉及订单、产能、股东变化等不同类型，系统已按事件性质拆分。', tags: [['新能源', 'green'], ['公司公告', 'orange']], trend: '+12%', intel: { priority: '观察', tone: 'low', age: '2 小时前', coverage: '11 份公告', status: '信息集中', judgmentLabel: '市场解读', impact: '同板块公告密集，但对基本面的影响方向并不一致', owner: 'A 股 / 新能源', ownerIcon: 'chart-candlestick', nextLabel: '观察要点', action: '逐项核对原始公告与生效时间', sources: ['eastmoney.com', 'cninfo.com.cn'] } },
    { source: '公司公告', domain: 'cninfo.com.cn', platform: '公司公告', title: '宽基指数 ETF 资金关注度连续上升', summary: '资金流向与成交活跃度同步增加，系统已区分申购变化和二级市场成交。', tags: [['A 股', 'red'], ['指数异动', 'amber']], trend: '+20%', intel: { priority: '上升', tone: 'trend', age: '3 小时前', coverage: '5 只相关 ETF', status: '连续上升', sources: ['cninfo.com.cn', 'eastmoney.com'] } }
  ];

  const researchSignals = [
    { ...inboxSignals[1], platform: 'arXiv' },
    { ...inboxSignals[9], platform: 'arXiv' },
    { ...inboxSignals[6], platform: 'Hugging Face' },
    { source: 'Papers with Code', domain: 'paperswithcode.com', platform: 'Papers with Code', title: 'Agent 工具调用评测补充公开实现', summary: '新增实现补充了复现实验所需的任务配置、数据处理与结果脚本。', tags: [['代码已公开', 'green'], ['评测基准', 'blue']], trend: '+9%', intel: { priority: '值得看', tone: 'medium', age: '5 小时前', coverage: '论文 + 代码', status: '可复现', judgmentLabel: '研究价值', impact: '公开实现降低了结果复核与横向比较成本', owner: 'AI 研究 / 评测团队', ownerIcon: 'flask-conical', nextLabel: '建议下一步', action: '加入复现队列并记录实验差异', sources: ['paperswithcode.com', 'github.com', 'arxiv.org'] } },
    { source: 'Semantic Scholar', domain: 'semanticscholar.org', platform: 'Semantic Scholar', title: '多模态推理评测研究关注度上升', summary: '相关论文围绕视觉理解、长链推理与评测污染提出新的实验设计。', tags: [['多模态', 'blue'], ['评测基准', 'amber']], trend: '+13%', intel: { priority: '上升', tone: 'trend', age: '昨天', coverage: '8 篇相关论文', status: '引用增长', sources: ['semanticscholar.org', 'arxiv.org'] } }
  ];

  const newsSignals = [
    { ...inboxSignals[0], platform: '官方发布', detailType: 'news' },
    { ...socialSignals[3], platform: '主流媒体', detailType: 'news' },
    { ...financeSignals[1], platform: 'Reuters', detailType: 'news' },
    { ...inboxSignals[7], platform: '科技媒体', detailType: 'news' },
    { ...socialSignals[4], platform: '主流媒体', detailType: 'news' }
  ];

  discoveryChannelSignals = {
    tech: inboxSignals,
    society: socialSignals,
    news: newsSignals,
    video: videoSignals,
    finance: financeSignals,
    research: researchSignals,
    recommended: [inboxSignals[0], socialSignals[0], videoSignals[0], financeSignals[0], researchSignals[0]]
  };
  const content = `<section class="page discover-page dense-discover">${header('全网情报', '今日情报', '在同一个信息流里查看技术项目、社会热点、视频趋势与财经市场。', actions)}
    <nav class="channel-nav" aria-label="领域频道">
      <span class="channel-nav-label">领域</span>
      ${Object.entries(discoveryScopes).map(([key, scope]) => `<button class="channel-option ${key === 'tech' ? 'active' : ''}" data-channel="${key}">${icon(scope.icon)}<span>${scope.label}</span><small data-channel-count="${key}">0</small></button>`).join('')}
      <button class="channel-manage" data-toast="已打开频道管理" aria-label="管理频道" title="管理频道">${icon('sliders-horizontal')}</button>
    </nav>
    <section class="scope-panel" aria-label="当前情报范围">
      <div class="scope-row">
        <span class="scope-label">平台</span>
        <div class="scope-options" id="platformOptions">${platformOptionsHtml(discoveryScopes.tech)}</div>
      </div>
      <div class="scope-row">
        <span class="scope-label">主题</span>
        <div class="selected-topics" id="selectedTopics">${selectedTopicsHtml(discoveryScopes.tech.topics)}</div>
        <button class="topic-trigger" data-open-modal="topicModal">${icon('list-filter')}筛选主题</button>
        <span class="scope-spacer"></span>
        <label class="scope-status"><span>状态</span><select class="select" aria-label="信息状态"><option>全部</option><option>需要我看</option><option>正在变化</option><option>需要行动</option><option>刚收录</option></select></label>
      </div>
    </section>
    <div class="discover-content-grid">
      <section class="discovery-feed">
        <div class="toolbar discovery-toolbar">
      <div class="result-summary"><strong>更多发现</strong><span><i id="scopeResultContext">技术与开源</i> · <b id="scopeResultCount">0</b> 条 · 真实入库</span></div>
      <label class="global-search inline-search">${icon('search')}<input type="search" placeholder="搜索项目、事件、公司或创作者…" aria-label="过滤发现流"></label>
      <span class="toolbar-spacer"></span>
      <select class="select" aria-label="排序"><option>按推荐程度</option><option>按热度变化</option><option>按收录时间</option></select>
      <select class="select" aria-label="时间范围"><option>今天</option><option>最近 7 天</option><option>最近 30 天</option></select>
      <button class="icon-button" data-toast="当前频道视图已保存" aria-label="保存当前视图" title="保存当前视图">${icon('save')}</button>
        </div>
        <div class="discover-workbench">
          <main class="intel-inbox">
            <div class="intel-header">
              <label class="intel-check"><input type="checkbox" aria-label="选择全部"></label>
              <span>状态</span><span>内容与来源</span><span>热度信息</span><span></span>
            </div>
            <div class="intel-list">${emptyLiveHtml('全平台')}</div>
          </main>
        </div>
      </section>
      <aside class="domain-ranking" aria-labelledby="rankingTitle">
        <div class="ranking-head">
          <div class="ranking-title-wrap"><span class="ranking-kicker">${icon('flame')}领域热榜</span><h2 id="rankingTitle">${rankingProfiles.tech.title}</h2><p id="rankingDescription">${rankingProfiles.tech.description}</p></div>
          <div class="ranking-head-actions"><div class="ranking-modes" id="rankingModes">${rankingProfiles.tech.modes.map((mode, index) => `<button class="ranking-mode ${index === 0 ? 'active' : ''}">${mode}</button>`).join('')}</div></div>
        </div>
        <div class="ranking-list" id="rankingList"><article class="ranking-row"><span class="ranking-number">--</span><div class="ranking-main"><a>正在读取真实入库</a><span>不会展示原型静态榜单</span></div><span class="ranking-metric"><strong>Live</strong><small>等待数据</small></span></article></div>
      </aside>
    </div>
  </section>`;
  shell(content);
}

function detailCommand(domain, primaryLabel = '返回发现') {
  return `<div class="domain-detail-command"><a class="detail-back" href="discover.html">${icon('arrow-left')}返回发现</a><span class="detail-breadcrumb">${domain}</span><span class="toolbar-spacer"></span><button class="btn" data-toast="已加入持续关注">${icon('eye')}持续关注</button></div>`;
}

function financeChartSvg() {
  const candles = [
    [78, 62, 54, 72], [70, 76, 64, 67], [68, 58, 50, 61], [60, 53, 46, 55],
    [56, 64, 52, 62], [63, 48, 43, 51], [50, 42, 36, 45], [46, 55, 40, 52],
    [51, 38, 33, 41], [40, 31, 26, 34], [35, 43, 29, 40], [39, 28, 23, 31],
    [30, 22, 17, 25], [24, 34, 20, 31], [32, 25, 19, 27], [26, 18, 13, 21]
  ];
  const body = candles.map(([open, close, high, low], index) => {
    const x = 48 + index * 38;
    const up = close < open;
    const color = up ? '#d4473d' : '#16835b';
    const top = Math.min(open, close) * 3.2;
    const height = Math.max(8, Math.abs(open - close) * 3.2);
    return `<line x1="${x}" y1="${high * 3.2}" x2="${x}" y2="${low * 3.2}" stroke="${color}"/><rect x="${x - 7}" y="${top}" width="14" height="${height}" fill="${color}"/><rect x="${x - 10}" y="330" width="20" height="${28 + (index % 5) * 8}" fill="${color}" opacity=".34"/>`;
  }).join('');
  return `<svg class="market-chart-svg" viewBox="0 0 680 400" role="img" aria-label="英伟达近一个月日K线示意图"><g class="chart-grid"><line x1="32" y1="64" x2="664" y2="64"/><line x1="32" y1="128" x2="664" y2="128"/><line x1="32" y1="192" x2="664" y2="192"/><line x1="32" y1="256" x2="664" y2="256"/><line x1="32" y1="320" x2="664" y2="320"/></g>${body}<path d="M48 254 C120 242,156 230,198 218 S280 190,332 176 S430 142,492 126 S580 84,618 62" fill="none" stroke="#2b69b1" stroke-width="2"/><g class="chart-axis"><text x="32" y="392">06/18</text><text x="190" y="392">06/26</text><text x="348" y="392">07/04</text><text x="506" y="392">07/12</text><text x="628" y="392">今天</text></g></svg>`;
}

function renderProjectDetailPage() {
  return `<section class="page repo-detail-page">
    ${detailCommand('技术与开源 / GitHub 项目', '研究该项目')}
    <header class="repo-detail-header">
      <div class="repo-title-line"><img src="${favicon('github.com')}" width="24" height="24" alt="GitHub"><h1><span>browser-use</span> / browser-use</h1><span class="repo-visibility">Public</span></div>
      <p>让 AI Agent 能够理解网页并完成点击、输入、导航和多步骤浏览器任务的 Python 开源框架。</p>
      <div class="repo-header-actions"><a class="btn" href="https://github.com/browser-use/browser-use" target="_blank" rel="noreferrer">${icon('external-link')}访问仓库</a><button class="btn">${icon('git-fork')}Fork <strong>11.6k</strong></button><button class="btn primary">${icon('star')}Star <strong>105k</strong></button></div>
    </header>
    <nav class="repo-tabs"><a class="active">${icon('code-2')}项目介绍</a><a>${icon('circle-dot')}Issues <span>81</span></a><a>${icon('git-pull-request')}Pull requests <span>238</span></a><a>${icon('messages-square')}讨论</a><a>${icon('tag')}版本 <span>133</span></a></nav>
    <div class="repo-detail-layout">
      <main class="repo-readme">
        <div class="repo-readme-bar"><strong>${icon('file-text')}README</strong><span>最近更新于今天</span><button class="icon-button" title="复制链接" aria-label="复制链接">${icon('link-2')}</button></div>
        <article class="readme-body">
          <div class="readme-brand"><span class="readme-logo">BU</span><div><h2>Browser Use</h2><p>Make websites accessible for AI agents.</p></div></div>
          <div class="readme-badges"><span>Python 3.11+</span><span>MIT License</span><span>PyPI v0.13.6</span><span>Docs</span></div>
          <h2>项目能做什么</h2>
          <p>Browser Use 把浏览器页面、元素和操作转换为 Agent 可以理解的工具。开发者可以用自然语言定义任务，并接入 OpenAI、Anthropic、Gemini 等模型完成网页搜索、表单填写、数据采集和流程自动化。</p>
          <div class="repo-feature-list"><div>${icon('mouse-pointer-click')}<span><strong>网页操作</strong>识别页面元素，完成点击、输入、滚动与文件上传。</span></div><div>${icon('route')}<span><strong>多步骤任务</strong>保留会话状态，支持跨页面导航和失败恢复。</span></div><div>${icon('blocks')}<span><strong>模型与工具集成</strong>提供常见模型适配、结构化输出和自定义动作。</span></div></div>
          <h2>快速开始</h2>
          <div class="repo-code"><div><span>Terminal</span><button class="icon-button" aria-label="复制安装命令" title="复制">${icon('copy')}</button></div><code>pip install browser-use<br>playwright install chromium</code></div>
          <h2>适合谁使用</h2>
          <ul><li>需要把网页能力接入 Agent 产品的开发团队</li><li>需要自动执行公开网页研究、录入和重复流程的个人开发者</li><li>正在验证 Browser Agent 可靠性与成本的研究者</li></ul>
        </article>
      </main>
      <aside class="repo-sidebar">
        <section><h2>关于项目</h2><p>让 AI Agent 轻松完成真实网站任务。</p><a href="https://browser-use.com" target="_blank" rel="noreferrer">${icon('link')}browser-use.com</a><div class="repo-topic-list"><span>python</span><span>browser-automation</span><span>ai-agents</span><span>playwright</span></div></section>
        <section class="repo-stat-list"><div>${icon('star')}<span><strong>105k</strong> Stars</span></div><div>${icon('eye')}<span><strong>445</strong> Watching</span></div><div>${icon('git-fork')}<span><strong>11.6k</strong> Forks</span></div><div>${icon('scale')}<span>MIT license</span></div></section>
        <section><div class="repo-section-title"><h2>最新版本</h2><span>133</span></div><div class="release-item">${icon('tag')}<div><strong>0.13.6 <span>Latest</span></strong><small>今天发布 · 修复会话恢复问题</small></div></div><a class="repo-more">查看全部版本 ${icon('chevron-right')}</a></section>
        <section><h2>主要语言</h2><div class="language-bar"><span style="width:82%"></span><span style="width:12%"></span><span style="width:6%"></span></div><div class="language-legend"><span><i class="python"></i>Python 82%</span><span><i class="ts"></i>TypeScript 12%</span><span><i></i>其他 6%</span></div></section>
        <section><h2>相似项目</h2><div class="repo-related"><a>langchain-ai / langgraph</a><a>microsoft / autogen</a><a>modelcontextprotocol / servers</a></div></section>
      </aside>
    </div>
  </section>`;
}

function renderFinanceDetailPage() {
  return `<section class="page finance-detail-page">
    ${detailCommand('财经市场 / 美股行情', '研究公司与行业')}
    <header class="quote-header">
      <div class="quote-identity"><span class="quote-logo">NV</span><div><div class="quote-symbol"><strong>NVDA</strong><span>NASDAQ · 盘中</span></div><h1>英伟达</h1><p>NVIDIA Corporation · 半导体与人工智能计算</p></div></div>
      <div class="quote-price"><div><strong>173.88</strong><span>USD</span></div><p class="market-up">+4.51&nbsp;&nbsp;+2.66%</p><small>北京时间 17:16 · 延迟行情</small></div>
      <div class="quote-actions"><button class="btn primary">${icon('plus')}加入自选</button><button class="btn">${icon('bell')}价格提醒</button></div>
    </header>
    <nav class="market-tabs"><a class="active">行情</a><a>财务</a><a>公告</a><a>新闻</a><a>资金</a><a>机构观点</a><a>相关基金</a></nav>
    <div class="finance-layout">
      <main class="finance-main">
        <section class="chart-panel">
          <div class="chart-toolbar"><div class="segmented"><button>分时</button><button class="active">日K</button><button>周K</button><button>月K</button></div><div class="chart-indicators"><button>MA</button><button>EMA</button><button>成交量</button><button class="icon-button" title="全屏图表" aria-label="全屏图表">${icon('maximize-2')}</button></div></div>
          <div class="chart-ohlc"><span>开 <strong>169.62</strong></span><span>高 <strong class="market-up">174.28</strong></span><span>低 <strong class="market-down">168.91</strong></span><span>量 <strong>201.6M</strong></span></div>
          ${financeChartSvg()}
          <div class="chart-legend"><span><i class="candle-up"></i>上涨</span><span><i class="candle-down"></i>下跌</span><span><i class="ma-line"></i>MA20</span></div>
        </section>
        <section class="market-section">
          <div class="market-section-head"><h2>关键指标</h2><span>截至最近报告期</span></div>
          <div class="fundamental-grid"><div><span>总市值</span><strong>4.24T USD</strong></div><div><span>市盈率 TTM</span><strong>56.8</strong></div><div><span>营收 TTM</span><strong>165.2B USD</strong></div><div><span>净利润率</span><strong>55.8%</strong></div><div><span>52周区间</span><strong>86.62 - 174.28</strong></div><div><span>平均成交量</span><strong>182.4M</strong></div></div>
        </section>
        <section class="market-section">
          <div class="market-section-head"><h2>公司动态</h2><a>查看全部 ${icon('chevron-right')}</a></div>
          <div class="finance-news-list"><a><time>16:42</time><span><strong>AI 芯片需求预期继续上调，半导体板块成交活跃</strong><small>Reuters · 12 个相关报道</small></span></a><a><time>14:10</time><span><strong>英伟达公布下一代加速平台合作伙伴计划</strong><small>公司公告 · 官方来源</small></span></a><a><time>昨天</time><span><strong>多只科技主题基金更新前十大持仓</strong><small>基金公告 · 6 只相关基金</small></span></a></div>
        </section>
        <p class="finance-disclaimer">行情数据为原型示例，不构成投资建议。实际产品应展示数据源、交易所时区、延迟状态和复权方式。</p>
      </main>
      <aside class="quote-sidebar">
        <section><div class="market-section-head"><h2>盘口概览</h2><span class="market-status">交易中</span></div><div class="quote-facts"><div><span>今开</span><strong>169.62</strong></div><div><span>昨收</span><strong>169.37</strong></div><div><span>最高</span><strong class="market-up">174.28</strong></div><div><span>最低</span><strong class="market-down">168.91</strong></div><div><span>换手率</span><strong>0.83%</strong></div><div><span>振幅</span><strong>3.17%</strong></div></div></section>
        <section><div class="market-section-head"><h2>相关股票</h2><a>更多</a></div><div class="ticker-list"><a><span><strong>AMD</strong><small>超威半导体</small></span><b>159.21</b><em class="market-up">+1.84%</em></a><a><span><strong>AVGO</strong><small>博通</small></span><b>281.44</b><em class="market-up">+1.26%</em></a><a><span><strong>TSM</strong><small>台积电</small></span><b>238.91</b><em class="market-down">-0.42%</em></a></div></section>
        <section><div class="market-section-head"><h2>相关基金 / ETF</h2><a>更多</a></div><div class="fund-list"><a><span class="fund-code">QQQ</span><span><strong>纳斯达克 100 ETF</strong><small>NVDA 持仓占比 8.7%</small></span><em class="market-up">+1.12%</em></a><a><span class="fund-code">SMH</span><span><strong>半导体 ETF</strong><small>NVDA 持仓占比 19.4%</small></span><em class="market-up">+1.63%</em></a><a><span class="fund-code">SOXX</span><span><strong>美国半导体 ETF</strong><small>NVDA 持仓占比 9.1%</small></span><em class="market-up">+1.48%</em></a></div></section>
      </aside>
    </div>
  </section>`;
}

function renderVideoDetailPage() {
  return `<section class="page video-detail-page">
    ${detailCommand('视频热榜 / B站', '分析该视频')}
    <div class="video-detail-layout">
      <main class="video-main">
        <div class="video-player" role="img" aria-label="AI 编程工具实测视频封面">
          <img src="https://images.unsplash.com/photo-1516321318423-f06f85e504b3?auto=format&fit=crop&w=1400&q=82" alt="电脑屏幕上的开发工具">
          <div class="video-player-shade"></div>
          <button class="video-play" aria-label="播放视频">${icon('play')}</button>
          <div class="video-progress"><span></span></div><div class="video-controls">${icon('play')}<span>00:00 / 18:42</span><span class="toolbar-spacer"></span>${icon('volume-2')}${icon('settings')}${icon('maximize')}</div>
        </div>
        <article class="video-info">
          <div class="video-platform-line"><img src="${favicon('bilibili.com')}" width="18" height="18" alt=""><span>B站 · 科技区</span><span class="tag orange">热门第 3</span><time>今天 10:30</time></div>
          <h1>实测多款 AI 编程工具：完整工作流与失败过程对比</h1>
          <div class="video-stat-actions"><div class="video-stats"><span>326.4万 播放</span><span>18.6万 互动</span><span>2.9万 评论</span></div><div class="video-actions"><button class="btn">${icon('thumbs-up')}18.6万</button><button class="btn">${icon('bookmark')}收藏</button><button class="btn">${icon('share-2')}分享</button></div></div>
          <div class="creator-row"><span class="creator-avatar">LK</span><div><strong>老K实验室</strong><span>科技测评创作者 · 86.4万粉丝</span></div><button class="btn primary">${icon('plus')}关注</button></div>
          <section class="video-description"><p>同一个真实开发任务，完整对比需求理解、代码生成、调试和修改四个阶段。视频保留了失败过程，并说明不同工具在速度、上下文理解、成本和错误恢复上的差异。</p><div><span>#AI编程</span><span>#开发者工具</span><span>#效率测评</span></div></section>
          <section class="video-chapters"><div class="video-section-head"><h2>视频章节</h2><span>4 个章节</span></div><div class="chapter-grid"><button><time>00:00</time><span><strong>测试规则与最终结果</strong><small>先看工具版本和评分标准</small></span></button><button><time>03:20</time><span><strong>需求理解与首轮生成</strong><small>同一提示下的实现差异</small></span></button><button><time>09:45</time><span><strong>调试与错误恢复</strong><small>失败次数和人工介入</small></span></button><button><time>15:30</time><span><strong>成本与适用人群</strong><small>订阅成本和最终结论</small></span></button></div></section>
          <section class="comment-insights"><div class="video-section-head"><h2>观众都在讨论什么</h2><span>基于 2.9 万条公开评论</span></div><div class="comment-topic"><strong>复杂项目稳定性</strong><span>高赞评论认为小型 Demo 差异不大，长任务中的上下文保持更关键。</span><em>38%</em></div><div class="comment-topic"><strong>订阅成本</strong><span>用户集中比较套餐限制、模型额度与团队使用成本。</span><em>27%</em></div><div class="comment-topic"><strong>实际工作流</strong><span>观众希望补充 Java、大型前端项目和单元测试场景。</span><em>21%</em></div></section>
        </article>
      </main>
      <aside class="video-related"><div class="video-section-head"><h2>同领域热门</h2><a>查看榜单</a></div><a class="related-video"><div class="related-thumb"><img src="https://images.unsplash.com/photo-1555066931-4365d14bab8c?auto=format&fit=crop&w=420&q=78" alt="代码编辑器"><time>12:08</time></div><span><strong>程序员真实使用一周后的效率变化</strong><small>极客观察 · 188万播放</small></span></a><a class="related-video"><div class="related-thumb"><img src="https://images.unsplash.com/photo-1555949963-ff9fe0c870eb?auto=format&fit=crop&w=420&q=78" alt="软件开发屏幕"><time>09:36</time></div><span><strong>五款 AI IDE 的代码库理解对比</strong><small>CodeLab · 142万播放</small></span></a><a class="related-video"><div class="related-thumb"><img src="https://images.unsplash.com/photo-1461749280684-dccba630e2f6?auto=format&fit=crop&w=420&q=78" alt="编程代码"><time>22:17</time></div><span><strong>从零构建一个可用的 Agent 工作流</strong><small>开发者频道 · 96万播放</small></span></a><section class="video-rank-box"><span>当前排名</span><strong>科技区第 3</strong><p>过去 6 小时播放增速 +68%</p><a href="discover.html?channel=video">进入视频热榜 ${icon('chevron-right')}</a></section></aside>
    </div>
  </section>`;
}

function renderPaperDetailPage() {
  return `<section class="page paper-detail-page">
    ${detailCommand('学术研究 / arXiv 论文', '深入研究论文')}
    <header class="paper-header">
      <div class="paper-category">Computer Science · Artificial Intelligence <span>arXiv:2607.10421</span></div>
      <h1>MemoryArena: Evaluating Long-Term Memory for LLM Agents Across Continual Tasks</h1>
      <p class="paper-authors"><a>Lin Chen</a><a>Yutong Wang</a><a>Maria Gomez</a><a>David Park</a><span>等 6 位作者</span></p>
      <div class="paper-meta"><span>${icon('calendar')}提交于 2026-07-15</span><span>${icon('rotate-ccw')}v2 · 今天更新</span><span>${icon('quote')}42 次引用</span><span class="tag green">代码已公开</span></div>
    </header>
    <nav class="paper-tabs"><a class="active">论文概要</a><a>方法与实验</a><a>图表</a><a>引用</a><a>相关论文</a></nav>
    <div class="paper-layout">
      <main class="paper-content">
        <section class="paper-abstract"><h2>摘要</h2><p>长期记忆是通用 Agent 在持续任务中保持一致性和积累经验的关键能力，但现有评测通常只覆盖短对话或单次检索。本研究提出 MemoryArena，通过连续任务、干扰信息和跨任务迁移，测量 Agent 的信息保留、检索准确率、记忆更新和遗忘控制。</p><p>实验比较了六种记忆架构。结果表明，更大的存储容量并不必然带来更好的任务表现；检索策略、记忆写入时机和冲突处理对最终结果影响更大。</p><div class="paper-keywords"><strong>关键词</strong><span>LLM Agent</span><span>Long-term Memory</span><span>Benchmark</span><span>Retrieval</span></div></section>
        <section class="paper-findings"><h2>研究要点</h2><div><span>01</span><article><h3>提出持续任务评测框架</h3><p>不只测试“能否记住”，还测试 Agent 在后续任务中是否能正确调用、更新或忽略历史信息。</p></article></div><div><span>02</span><article><h3>检索质量比容量更关键</h3><p>当记忆库持续增长时，错误召回和过期信息会明显拖累决策质量。</p></article></div><div><span>03</span><article><h3>结论仍有模型依赖</h3><p>结果主要基于当前六种模型与既定提示模板，跨模型泛化仍需要独立复现。</p></article></div></section>
        <section class="paper-result-table"><div class="paper-section-head"><h2>主要实验结果</h2><span>总体任务成功率 ↑</span></div><div class="table-wrap"><table><thead><tr><th>记忆方法</th><th>信息保留</th><th>检索准确</th><th>跨任务迁移</th><th>总体</th></tr></thead><tbody><tr><td>Context Only</td><td>61.2</td><td>58.9</td><td>42.6</td><td>54.2</td></tr><tr><td>Vector Memory</td><td>78.4</td><td>71.5</td><td>60.1</td><td>70.0</td></tr><tr class="best"><td>Adaptive Memory</td><td>84.7</td><td>82.3</td><td>74.8</td><td>80.6</td></tr></tbody></table></div></section>
        <section class="paper-citation"><h2>引用格式</h2><div><code>@article{chen2026memoryarena, title={MemoryArena: Evaluating Long-Term Memory for LLM Agents}, year={2026}}</code><button class="icon-button" title="复制引用" aria-label="复制引用">${icon('copy')}</button></div></section>
      </main>
      <aside class="paper-sidebar">
        <section class="paper-actions"><a class="btn primary">${icon('file-down')}下载 PDF</a><a class="btn">${icon('github')}查看代码</a><a class="btn">${icon('bookmark')}保存论文</a></section>
        <section><h2>论文信息</h2><div class="paper-fact"><span>领域</span><strong>cs.AI / cs.CL</strong></div><div class="paper-fact"><span>页数</span><strong>24 页</strong></div><div class="paper-fact"><span>数据集</span><strong>已公开</strong></div><div class="paper-fact"><span>代码</span><strong>MIT</strong></div><div class="paper-fact"><span>复现状态</span><strong>3 个独立结果</strong></div></section>
        <section><div class="paper-section-head"><h2>作者机构</h2></div><div class="author-list"><div><span>LC</span><p><strong>Lin Chen</strong><small>清华大学</small></p></div><div><span>YW</span><p><strong>Yutong Wang</strong><small>斯坦福大学</small></p></div><div><span>MG</span><p><strong>Maria Gomez</strong><small>Google DeepMind</small></p></div></div></section>
        <section><div class="paper-section-head"><h2>相关论文</h2><a>更多</a></div><div class="related-paper-list"><a><strong>LongMemEval: Benchmarking Chat Assistants</strong><span>2025 · 126 次引用</span></a><a><strong>Generative Agents: Interactive Simulacra</strong><span>2023 · 2,846 次引用</span></a><a><strong>MemGPT: Towards LLMs as Operating Systems</strong><span>2024 · 734 次引用</span></a></div></section>
      </aside>
    </div>
  </section>`;
}

function renderSocialDetailPage() {
  return `<section class="page social-detail-page">
    ${detailCommand('社会热点 / 全网话题', '研究舆论与背景')}
    <header class="social-topic-header">
      <div class="social-topic-label"><span>全网热议</span><time>最近更新 17:12</time></div>
      <h1>多地推进公共数据开放，个人信息使用边界引发讨论</h1>
      <p>讨论从政策发布扩展到公共服务便利性、数据授权和隐私保护。微博与知乎贡献主要讨论量，主流媒体和政府账号提供政策原文与解释。</p>
      <div class="social-topic-stats"><div><strong>982万</strong><span>全网热度</span></div><div><strong>+36%</strong><span>6小时增长</span></div><div><strong>8</strong><span>覆盖平台</span></div><div><strong>12.4万</strong><span>公开讨论</span></div></div>
    </header>
    <nav class="social-tabs"><a class="active">讨论概览</a><a>代表观点</a><a>平台分布</a><a>相关话题</a></nav>
    <div class="social-layout">
      <main class="social-main">
        <section class="social-trend-section"><div class="social-section-head"><div><h2>24 小时讨论趋势</h2><p>发文量与互动量综合趋势</p></div><div class="segmented"><button class="active">全部</button><button>发文</button><button>互动</button></div></div><div class="social-trend-chart"><svg viewBox="0 0 760 240" role="img" aria-label="话题24小时讨论趋势"><g class="social-chart-grid"><line x1="34" y1="38" x2="744" y2="38"/><line x1="34" y1="90" x2="744" y2="90"/><line x1="34" y1="142" x2="744" y2="142"/><line x1="34" y1="194" x2="744" y2="194"/></g><path class="social-area" d="M34 190 L92 182 L150 175 L208 160 L266 148 L324 126 L382 140 L440 112 L498 86 L556 72 L614 48 L672 62 L730 34 L730 210 L34 210 Z"/><path class="social-line" d="M34 190 L92 182 L150 175 L208 160 L266 148 L324 126 L382 140 L440 112 L498 86 L556 72 L614 48 L672 62 L730 34"/><g class="social-chart-axis"><text x="34" y="230">00:00</text><text x="208" y="230">06:00</text><text x="382" y="230">12:00</text><text x="556" y="230">18:00</text><text x="702" y="230">现在</text></g></svg><span class="trend-peak">16:40 热度峰值</span></div></section>
        <section class="platform-distribution"><div class="social-section-head"><div><h2>平台讨论分布</h2><p>不同平台的内容量与讨论侧重点</p></div></div><div class="platform-share-row"><img src="${favicon('weibo.com')}" alt=""><strong>微博</strong><div><span style="width:38%"></span></div><b>38%</b><em>传播最快</em></div><div class="platform-share-row"><img src="${favicon('zhihu.com')}" alt=""><strong>知乎</strong><div><span style="width:26%"></span></div><b>26%</b><em>政策解读</em></div><div class="platform-share-row"><img src="${favicon('douyin.com')}" alt=""><strong>抖音</strong><div><span style="width:18%"></span></div><b>18%</b><em>案例讨论</em></div><div class="platform-share-row"><img src="${favicon('reddit.com')}" alt=""><strong>Reddit</strong><div><span style="width:10%"></span></div><b>10%</b><em>国际比较</em></div><div class="platform-share-row"><span class="platform-more-icon">+4</span><strong>其他</strong><div><span style="width:8%"></span></div><b>8%</b><em>新闻与论坛</em></div></section>
        <section class="representative-posts"><div class="social-section-head"><div><h2>代表性讨论</h2><p>按互动质量和观点差异筛选，不等同于事实结论</p></div><select class="select"><option>综合代表性</option><option>互动最多</option><option>最新发布</option></select></div><article class="social-post"><img src="${favicon('weibo.com')}" alt=""><div><div class="post-author"><strong>城市观察</strong><span>微博 · 认证媒体</span><time>2小时前</time></div><p>公共数据开放最直接的价值是减少重复证明，但“可用”和“可识别个人”之间需要明确边界。</p><div class="post-meta"><span>${icon('thumbs-up')}2.8万</span><span>${icon('message-circle')}4,126</span><span class="tag green">政策原文引用</span></div></div></article><article class="social-post"><img src="${favicon('zhihu.com')}" alt=""><div><div class="post-author"><strong>数据合规律师</strong><span>知乎 · 法律话题优秀答主</span><time>3小时前</time></div><p>争议的核心不是能否开放，而是目的限制、最小必要和退出机制是否能在实际产品中被用户看见。</p><div class="post-meta"><span>${icon('thumbs-up')}8,462</span><span>${icon('message-circle')}816</span><span class="tag blue">专业解读</span></div></div></article><article class="social-post"><img src="${favicon('reddit.com')}" alt=""><div><div class="post-author"><strong>r/privacy</strong><span>Reddit · 讨论串</span><time>5小时前</time></div><p>讨论对比了欧盟开放数据政策，关注去标识化数据在多源关联后重新识别的风险。</p><div class="post-meta"><span>${icon('arrow-big-up')}3,204</span><span>${icon('message-circle')}548</span><span class="tag">国际比较</span></div></div></article></section>
      </main>
      <aside class="social-sidebar">
        <section><div class="social-section-head"><div><h2>讨论焦点</h2></div></div><div class="focus-list"><div><span>公共服务便利性</span><strong>31%</strong></div><div><span>个人授权机制</span><strong>27%</strong></div><div><span>数据安全责任</span><strong>23%</strong></div><div><span>商业使用边界</span><strong>19%</strong></div></div></section>
        <section><h2>高频关键词</h2><div class="keyword-cloud"><span>公共数据</span><span>个人信息</span><span>授权</span><span>隐私保护</span><span>政务服务</span><span>数据要素</span><span>匿名化</span><span>知情同意</span></div></section>
        <section><h2>内容构成</h2><div class="content-mix"><div><span>媒体报道</span><strong>34%</strong></div><div><span>个人讨论</span><strong>29%</strong></div><div><span>专业解读</span><strong>21%</strong></div><div><span>官方发布</span><strong>16%</strong></div></div></section>
        <section><div class="social-section-head"><div><h2>相关话题</h2></div><a>更多</a></div><div class="related-topic-list"><a><span>01</span><strong>个人信息保护合规指南更新</strong><em>428万</em></a><a><span>02</span><strong>城市公共数据授权运营试点</strong><em>316万</em></a><a><span>03</span><strong>AI 训练数据版权边界讨论</strong><em>289万</em></a></div></section>
      </aside>
    </div>
  </section>`;
}

function renderNewsDetailPage() {
  return `<section class="page news-detail-page">
    ${detailCommand('新闻事件 / 重大科技新闻', '深度调查此事件')}
    <article class="news-article-header">
      <div class="news-kicker"><span>持续更新</span><strong>科技 · 人工智能</strong><time>2026-07-17 16:10</time></div>
      <h1>OpenAI 发布新一代 Agents SDK，开发者生态开始跟进</h1>
      <p>官方仓库、产品文档与首批开发者反馈显示，新版本重点增强了工具调用、状态管理和长任务恢复。生产环境稳定性仍需更多独立案例验证。</p>
      <div class="news-byline"><img src="${favicon('reuters.com')}" alt=""><span><strong>综合报道</strong><small>汇总 8 家媒体、2 个官方来源与开发者社区</small></span><div><button class="btn">${icon('share-2')}分享</button><button class="btn save-action">${icon('bookmark')}收藏</button></div></div>
    </article>
    <div class="news-layout">
      <main class="news-article-body">
        <section class="confirmed-facts"><h2>目前可以确认</h2><div>${icon('badge-check')}<p><strong>SDK 与官方文档已经发布</strong><span>官方产品页、GitHub 仓库和版本记录可相互核对。</span></p></div><div>${icon('badge-check')}<p><strong>新增工具调用追踪与会话状态管理</strong><span>功能范围可由官方示例和 API 参考文档确认。</span></p></div><div class="fact-watch">${icon('circle-help')}<p><strong>生产环境表现仍待观察</strong><span>公开案例正在增加，但复杂任务中的稳定性和成本尚无充分独立数据。</span></p></div></section>
        <section class="news-prose"><h2>事件概况</h2><p>OpenAI 于今天上午发布新一代 Agents SDK，并同步更新官方仓库、快速开始文档和工具调用示例。与此前版本相比，新版本将任务状态、工具执行记录和失败恢复放在更明确的位置，希望降低开发者构建长流程 Agent 的复杂度。</p><p>发布后数小时内，多家技术媒体和开发者社区开始跟进。首批讨论主要集中在迁移成本、可观测性、并发任务管理和第三方模型兼容性。部分开发者已经完成简单示例接入，但大规模生产案例尚未形成。</p><blockquote>“这次更新的重点不是增加更多抽象，而是让开发者能看见 Agent 在每一步做了什么，并在失败后继续运行。”<cite>官方发布说明</cite></blockquote><h2>为什么值得关注</h2><p>Agent 开发正从演示型应用进入需要长期运行、多人协作和明确审计的业务场景。SDK 是否能稳定管理状态、权限和工具错误，将直接影响企业采用速度。与此同时，框架绑定和跨模型迁移成本也是开发者关注的主要风险。</p></section>
        <section class="news-perspectives"><div class="news-section-head"><h2>各方观点</h2><span>同一事件的不同观察角度</span></div><article><span>官方</span><div><h3>强调可观测性与长任务恢复</h3><p>发布说明把工具调用轨迹、状态保存和人工介入作为主要更新。</p></div></article><article><span>开发者</span><div><h3>关注迁移成本与框架锁定</h3><p>社区反馈认为快速开始更简单，但仍需评估现有 Agent 工作流的迁移成本。</p></div></article><article><span>行业媒体</span><div><h3>竞争焦点转向生产可靠性</h3><p>报道普遍认为 Agent 框架竞争正在从功能数量转向稳定性、权限与生态。</p></div></article></section>
        <section class="news-source-coverage"><div class="news-section-head"><h2>来源报道</h2><span>按来源角色整理</span></div><a><img src="${favicon('openai.com')}" alt=""><span><strong>OpenAI 官方发布</strong><small>产品能力、版本范围与迁移说明</small></span><em>官方来源</em></a><a><img src="${favicon('github.com')}" alt=""><span><strong>GitHub 仓库与 Release</strong><small>代码、示例、Issue 与版本记录</small></span><em>原始资料</em></a><a><img src="${favicon('reuters.com')}" alt=""><span><strong>Reuters：Agent 工具竞争升温</strong><small>行业背景、公司回应与市场关系</small></span><em>媒体报道</em></a><a><img src="${favicon('news.ycombinator.com')}" alt=""><span><strong>Hacker News 开发者讨论</strong><small>首批接入体验、问题与待验证线索</small></span><em>社区讨论</em></a></section>
      </main>
      <aside class="news-sidebar">
        <section class="news-progress"><div class="news-section-head"><h2>事件进展</h2><span class="live-dot">持续更新</span></div><div class="news-progress-list"><article class="current"><time>16:10</time><div><strong>首批开发者反馈出现</strong><p>讨论转向迁移成本、并发任务和第三方模型兼容。</p></div></article><article><time>13:40</time><div><strong>技术媒体集中报道</strong><p>重点比较 Agent 框架的可观测性和生态差异。</p></div></article><article><time>10:20</time><div><strong>示例与迁移文档补充</strong><p>官方增加工具调用、状态保存和恢复说明。</p></div></article><article><time>09:00</time><div><strong>SDK 正式发布</strong><p>产品页、仓库和基础文档同步上线。</p></div></article></div></section>
        <section><h2>事件信息</h2><div class="news-fact"><span>类型</span><strong>产品发布</strong></div><div class="news-fact"><span>首次出现</span><strong>今天 09:00</strong></div><div class="news-fact"><span>相关来源</span><strong>10 个</strong></div><div class="news-fact"><span>最近更新</span><strong>38 分钟前</strong></div></section>
        <section><div class="news-section-head"><h2>相关事件</h2><a>更多</a></div><div class="related-news-list"><a><span>07-16</span><strong>主流 Agent 框架近期版本变化</strong></a><a><span>07-14</span><strong>MCP 工具权限与审计实践更新</strong></a><a><span>07-11</span><strong>企业 Agent 可观测性产品集中发布</strong></a></div></section>
      </aside>
    </div>
  </section>`;
}

function renderStory() {
  const requestedType = new URLSearchParams(window.location.search).get('type') || 'project';
  const dedicatedDetails = {
    project: renderProjectDetailPage,
    finance: renderFinanceDetailPage,
    video: renderVideoDetailPage,
    paper: renderPaperDetailPage,
    social: renderSocialDetailPage,
    news: renderNewsDetailPage
  };
  if (dedicatedDetails[requestedType]) {
    shell(dedicatedDetails[requestedType]());
    return;
  }
  const configs = {
    project: {
      domain: '技术与开源', kind: '项目详情', source: 'GitHub', host: 'github.com', title: 'browser-use / browser-use',
      summary: '让 AI Agent 通过浏览器完成网页任务的开源项目，提供 Python 接口、常见模型集成、示例和完整文档。',
      tags: [['Python', 'blue'], ['Browser Agent', 'green'], ['MIT', '']],
      metrics: [['105k', 'Stars'], ['11.6k', 'Forks'], ['133', 'Releases'], ['+1.8k', '今日增长']],
      overviewTitle: '这个项目做什么',
      overview: 'browser-use 把浏览器操作封装为 Agent 可以理解和执行的能力，适合网页检索、表单操作、信息采集和重复性工作流。项目重点不在单次演示，而在可组合的浏览器控制、模型接入与任务运行能力。',
      points: [['核心能力', '浏览器任务自动化', '支持页面理解、点击、输入、导航与多步骤任务。'], ['适用场景', '网页操作与研究任务', '适合原型验证、数据采集、流程自动化和 Agent 工具链。'], ['使用门槛', 'Python 生态', '仓库提供快速开始、示例、Docker 与常见模型配置。']],
      facts: [['主要语言', 'Python'], ['许可证', 'MIT'], ['最新版本', '0.13.6'], ['维护状态', '持续更新']],
      updates: [['今天', '发布 0.13.6，修复浏览器会话与任务恢复问题。'], ['本周', '新增多个模型接入示例与运行配置。'], ['近期', '社区讨论集中在长任务稳定性与资源占用。']],
      sources: [['官方仓库', '代码、Issue、版本与项目说明。', 'github.com'], ['项目文档', '安装、配置和使用示例。', 'browser-use.com'], ['开发者讨论', '实际使用问题和解决方案。', 'news.ycombinator.com']],
      related: ['langchain-ai / langgraph', 'modelcontextprotocol / servers', 'OpenAI Agents SDK']
    },
    news: {
      domain: '新闻事件', kind: '事件详情', source: '综合新闻', host: 'reuters.com', title: 'OpenAI Agents SDK 发布与开发者采用进展',
      summary: '官方发布、文档更新和开发者讨论共同构成这一新闻事件。当前可以确认产品与文档已经发布；关于生产稳定性的评价仍在持续形成。',
      tags: [['科技新闻', 'blue'], ['持续更新', 'orange'], ['多来源', 'green']],
      metrics: [['6', '相关来源'], ['4', '事件更新'], ['较高', '关注度'], ['38 分钟', '最近更新']],
      overviewTitle: '发生了什么',
      overview: 'OpenAI 发布 Agents SDK 并同步更新官方仓库和文档。随后技术媒体、开发者社区和项目实践开始跟进，讨论重点从功能介绍逐渐转向接入成本、状态管理和长期任务稳定性。',
      points: [['已经确认', '官方仓库与文档已发布', '发布信息可由官方页面和仓库相互核对。'], ['仍需观察', '生产环境表现', '公开案例正在增加，但复杂场景下的独立验证仍有限。'], ['主要分歧', '易用性与控制能力', '部分开发者重视快速接入，另一部分更关注可观测性与状态一致性。']],
      facts: [['事件类型', '产品发布'], ['首次出现', '今天 09:00'], ['覆盖范围', '科技媒体与开发者社区'], ['当前状态', '持续更新']],
      updates: [['09:00', '官方发布 SDK、仓库与基础文档。'], ['10:20', '官方示例补充工具调用与状态保存说明。'], ['13:40', '技术媒体开始梳理产品能力与竞争关系。'], ['16:10', '开发者社区出现首批接入体验与问题反馈。']],
      progressTitle: '事件进展',
      sources: [['官方发布', '产品能力、版本与使用范围。', 'openai.com'], ['GitHub 仓库', '代码、示例、Issue 和版本记录。', 'github.com'], ['媒体报道', '行业背景和产品对比。', 'techcrunch.com'], ['开发者讨论', '实际接入体验与待确认问题。', 'news.ycombinator.com']],
      related: ['Agent 工具调用可靠性讨论', 'Responses API 案例更新', 'MCP 工具生态增长']
    },
    video: {
      domain: '视频热榜', kind: '视频详情', source: 'B站', host: 'bilibili.com', title: '实测多款 AI 编程工具：完整工作流对比',
      summary: '视频使用同一开发任务对比多款 AI 编程工具，完整保留成功步骤、失败过程和最终结果，评论区补充了大量实际使用差异。',
      tags: [['AI 工具', 'blue'], ['实测对比', 'green'], ['站内热门', 'orange']],
      metrics: [['326万', '播放'], ['18.6万', '互动'], ['+68%', '今日增速'], ['18:42', '时长']],
      overviewTitle: '视频讲了什么',
      overview: '内容从需求理解、代码生成、调试和修改四个阶段进行同题对比，并展示不同工具在速度、上下文理解和错误恢复上的差异。',
      points: [['内容结构', '结果前置 + 同题对比', '开场先展示最终差异，再回到完整测试过程。'], ['传播原因', '失败过程被完整保留', '相比纯功能介绍，真实失败片段提升了讨论和可信度。'], ['观众反馈', '关注成本与稳定性', '高赞评论主要补充订阅成本、复杂项目表现和模型差异。']],
      facts: [['发布平台', 'B站'], ['内容类型', '工具实测'], ['发布时间', '今天 10:30'], ['当前状态', '站内热门']],
      updates: [['00:00', '测试目标、工具版本与评价标准。'], ['03:20', '第一轮代码生成和需求理解对比。'], ['09:45', '调试、错误恢复与人工介入。'], ['15:30', '成本、适用人群与最终结论。']],
      sources: [['原视频', '完整内容、章节与评论。', 'bilibili.com'], ['相关项目', '视频中使用的开源工具和示例。', 'github.com'], ['补充讨论', '其他开发者的对比体验。', 'reddit.com']],
      related: ['AI 编程助手成本对比', 'Agent 调试体验讨论', '开发者工具本周热榜']
    },
    finance: {
      domain: '财经市场', kind: '行情详情', source: '综合行情', host: 'tradingview.com', title: 'AI 算力相关板块讨论与成交关注度上升',
      summary: '相关标的成交活跃度和市场讨论同步上升，关注点集中在订单可见度、资本开支、业绩兑现与估值消化。',
      tags: [['AI / 算力', 'blue'], ['A 股', 'red'], ['市场异动', 'orange']],
      metrics: [['+4.8%', '板块涨幅'], ['42.6亿', '成交额'], ['6', '相关标的'], ['18 分钟', '数据更新']],
      overviewTitle: '行情概览',
      overview: '板块内多个标的同步活跃，市场交易与讨论存在共振。当前上涨既包含订单和资本开支预期，也包含短期情绪推动，需要结合公告和成交结构判断持续性。',
      points: [['主要驱动', '订单与资本开支预期', '市场关注云厂商投入、设备订单和交付节奏。'], ['需要核对', '公司公告与业绩兑现', '讨论热度不能替代正式公告、财报和订单信息。'], ['主要风险', '短期交易拥挤', '板块联动增强时，价格波动可能明显放大。']],
      facts: [['市场', 'A 股'], ['所属主题', 'AI / 算力'], ['数据状态', '盘中'], ['风险等级', '波动较高']],
      updates: [['09:35', '板块成交活跃度开始高于近期均值。'], ['10:10', '多个核心标的同步进入关注榜。'], ['11:05', '公司公告与订单讨论成为主要驱动。']],
      sources: [['行情数据', '价格、成交量和板块联动。', 'tradingview.com'], ['公司公告', '订单、产能和财务信息。', 'cninfo.com.cn'], ['市场讨论', '投资者观点与关注变化。', 'xueqiu.com']],
      related: ['大型科技公司 AI 资本开支', '半导体板块盘前异动', '宽基指数 ETF 资金变化'],
      notice: '行情数据为原型示例，不构成投资建议。'
    },
    paper: {
      domain: '学术研究', kind: '论文详情', source: 'arXiv', host: 'arxiv.org', title: 'Agent 长期记忆评测研究更新',
      summary: '研究提出长期记忆任务的评测框架，并公开部分实验代码，用于比较 Agent 在信息保留、检索和跨任务迁移上的表现。',
      tags: [['Agent 研究', 'blue'], ['评测基准', 'amber'], ['代码公开', 'green']],
      metrics: [['42', '新增引用'], ['5', '评测任务'], ['公开', '实验代码'], ['今天', '最近更新']],
      overviewTitle: '研究解决什么问题',
      overview: '论文关注 Agent 在长时间、多任务环境中如何保存和使用历史信息，区分短期上下文、外部记忆和可更新知识，并提出统一的实验任务。',
      points: [['研究方法', '多任务长期评测', '通过连续任务测量信息保留、检索准确率和迁移能力。'], ['主要发现', '记忆容量不等于有效使用', '更大的存储并不必然带来更好的检索和决策表现。'], ['结论边界', '仍依赖任务与模型设置', '结果需要结合具体模型、提示和外部存储实现理解。']],
      facts: [['研究方向', 'Agent Memory'], ['论文状态', '预印本'], ['代码', '已公开'], ['复现状态', '社区进行中']],
      updates: [['版本 v2', '补充更多模型和长期任务结果。'], ['代码更新', '公开数据处理与评测脚本。'], ['社区复现', '出现首批独立实验记录。']],
      sources: [['论文原文', '方法、实验设置与完整结论。', 'arxiv.org'], ['代码仓库', '数据处理、评测与复现脚本。', 'github.com'], ['论文索引', '引用、相关研究与后续工作。', 'semanticscholar.org']],
      related: ['多 Agent 协作基准', '工具调用评测集合', '长上下文检索实践']
    }
  };
  const type = new URLSearchParams(window.location.search).get('type') || 'project';
  const detail = configs[type] || configs.project;
  const progress = detail.updates?.length ? `<section class="detail-section" id="updates"><div class="detail-section-head"><h2>${detail.progressTitle || (type === 'video' ? '内容章节' : '最近动态')}</h2><span>${detail.updates.length} 项</span></div><div class="detail-update-list">${detail.updates.map(([time, text], index) => `<div class="detail-update"><time>${time}</time><span class="detail-update-dot ${index === 0 ? 'active' : ''}"></span><p>${text}</p></div>`).join('')}</div></section>` : '';
  const content = `<section class="page detail-page">
    <div class="detail-command"><a class="detail-back" href="discover.html">${icon('arrow-left')}返回今日情报</a><span class="detail-breadcrumb">${detail.domain} / ${detail.kind}</span><span class="toolbar-spacer"></span><button class="btn" data-toast="已加入持续跟踪">${icon('eye')}持续跟踪</button></div>
    <header class="detail-hero">
      <div class="detail-source-line"><img src="${favicon(detail.host)}" width="20" height="20" alt=""><strong>${detail.source}</strong><span>· 最近更新：今天</span><span class="tag green">已收录</span></div>
      <h1>${detail.title}</h1><p>${detail.summary}</p><div class="detail-tags">${tagsHtml(detail.tags)}</div>
      <div class="detail-metrics">${detail.metrics.map(([value, label]) => `<div><strong>${value}</strong><span>${label}</span></div>`).join('')}</div>
    </header>
    <nav class="detail-anchor-nav"><a href="#overview">概览</a><a href="#sources">相关来源</a>${detail.updates?.length ? '<a href="#updates">最新动态</a>' : ''}</nav>
    <div class="detail-layout">
      <main class="detail-main">
        <section class="detail-section" id="overview"><h2>${detail.overviewTitle}</h2><p class="detail-lead">${detail.overview}</p>${detail.notice ? `<p class="detail-notice">${detail.notice}</p>` : ''}<div class="detail-points">${detail.points.map(([label, title, text]) => `<article class="detail-point"><span>${label}</span><div><h3>${title}</h3><p>${text}</p></div></article>`).join('')}</div></section>
        ${progress}
        <section class="detail-section" id="sources"><div class="detail-section-head"><h2>相关来源</h2><span>${detail.sources.length} 个</span></div><div class="detail-source-list">${detail.sources.map(([title, text, host]) => `<a href="https://${host}" target="_blank" rel="noreferrer"><img src="${favicon(host)}" width="18" height="18" alt=""><span><strong>${title}</strong><small>${text}</small></span>${icon('arrow-up-right')}</a>`).join('')}</div></section>
      </main>
      <aside class="detail-aside">
        <section><h2>基本信息</h2>${detail.facts.map(([label, value]) => `<div class="detail-fact"><span>${label}</span><strong>${value}</strong></div>`).join('')}</section>
        <section><h2>相关内容</h2><div class="detail-related">${detail.related.map((title, index) => `<a href="#"><span>${String(index + 1).padStart(2, '0')}</span>${title}${icon('chevron-right')}</a>`).join('')}</div></section>
      </aside>
    </div>
  </section>`;
  shell(content);
}

function renderResearch() {
  const steps = [['确定范围','已完成','done'],['制定计划','已完成','done'],['搜索与阅读','进行中','running'],['抽取证据','已完成 8/12','done'],['补充缺口','等待',''],['撰写报告','等待',''],['引用核验','等待','']];
  const evidence = [['官方发布与仓库','支持“项目已正式发布”','github.com'],['官方文档','限定当前能力范围','openai.com'],['论文资料','提供评测方法参照','arxiv.org'],['开发者案例','提出可靠性待核验问题','news.ycombinator.com']];
  const content = `<section class="three-pane"><aside class="pane"><div class="pane-header"><span class="pane-title">研究计划</span><span class="tag orange">进行中</span></div><div class="step-list">${steps.map(([name,state,cls],i)=>`<div class="step-item ${cls} ${i===2?'active':''}"><span class="step-index">${i+1}</span><div><div class="step-name">${name}</div><div class="step-state">${state}</div></div></div>`).join('')}</div><div class="aside-section"><h2 class="section-title">预算</h2><div class="metric-line"><span>查询</span><span class="metric-value">12 / 25</span></div><div class="metric-line"><span>已读页面</span><span class="metric-value">18 / 50</span></div><div class="metric-line"><span>时间</span><span class="metric-value">06:42</span></div></div></aside>
    <main class="pane"><div class="research-head"><div class="research-query"><h1>Agent SDK 的能力边界与生产可靠性如何？</h1><p>Standard Research · 示例任务 · 自动保存</p></div><div><div class="progress-track"><div class="progress-bar"></div></div><div class="meta" style="margin-top:6px;text-align:right">68%</div></div><button class="icon-button" data-toast="研究已暂停" aria-label="暂停" title="暂停">${icon('pause')}</button></div>
      <article class="report-body"><span class="tag orange">草稿 · 尚未完成核验</span><h2>当前结论</h2><p>现有一级来源支持该 SDK 已形成正式发布、文档和仓库基础。关于生产可靠性的公开证据仍有限，当前结论需要区分“框架能力存在”与“复杂生产环境已经充分验证”。<button class="citation" data-toast="已定位证据 01">01</button><button class="citation" data-toast="已定位证据 02">02</button></p><div class="claim-block"><div class="claim-label">关键 Claim · 支持度较高</div><div class="claim-text">官方仓库和文档提供了可追溯的发布与能力说明。</div></div><h2>研究维度</h2><h3>1. 能力范围</h3><p>研究计划将工具调用、状态管理、可观测性和多 Agent 协作拆为独立子问题，并优先查找官方文档、代码和可复现资料。</p><h3>2. 证据缺口</h3><p>当前缺口集中在长期运行、失败恢复和复杂工具链下的独立评测。下一轮查询将限制在原始案例、Issue 和复现仓库。</p><h3>3. 待核验冲突</h3><p>部分社区讨论认为上手成本较低，但也有关于状态一致性的反馈。两类陈述尚不能直接互相否定，需要按场景和版本拆分。</p></article></main>
    <aside class="pane"><div class="pane-header"><span class="pane-title">证据与来源</span><button class="icon-button" aria-label="筛选证据" title="筛选证据">${icon('list-filter')}</button></div><div class="evidence-list">${evidence.map(([t,p,d])=>`<div class="evidence-item"><strong>${t}</strong><p>${p}</p><div class="evidence-source"><img class="source-logo" src="${favicon(d)}" width="17" height="17" alt="">${d}</div></div>`).join('')}</div><div class="aside-section"><button class="btn" data-toast="证据矩阵已导出">${icon('download')}导出证据矩阵</button></div></aside></section>`;
  shell(content);
}

function renderWatchlist() {
  const rows = [
    ['OpenAI Agents SDK','产品','高','检测到文档变化','68%','orange'],
    ['Model Context Protocol','协议','中','新增官方来源','44%','blue'],
    ['LangGraph','开源项目','低','版本更新','21%','green'],
    ['Agent Memory Research','主题','高','出现结论冲突','82%','red']
  ];
  const actions = `<button class="btn">${icon('bell-ring')}提醒规则</button><button class="btn primary" data-toast="已新建 Watch Target">${icon('plus')}添加跟踪</button>`;
  const content = `<section class="page">${header('持续跟踪', 'Watchlist', '实体、主题、Repo、论文和假设的基线与变化。', actions)}<div class="stat-strip"><div class="stat"><div class="stat-label">跟踪目标</div><div class="stat-value">18</div></div><div class="stat"><div class="stat-label">本周期变化</div><div class="stat-value">4<span class="stat-delta">2 项重要</span></div></div><div class="stat"><div class="stat-label">自动研究</div><div class="stat-value">1</div></div><div class="stat"><div class="stat-label">待确认提醒</div><div class="stat-value">3</div></div></div><div class="toolbar"><input class="field" type="search" placeholder="搜索跟踪目标…" aria-label="搜索跟踪目标"><button class="filter-chip active">全部</button><button class="filter-chip">重要变化</button><button class="filter-chip">研究中</button><span class="toolbar-spacer"></span><select class="select"><option>按变化排序</option><option>按名称排序</option></select></div><div class="table-wrap"><table class="data-table"><thead><tr><th>目标</th><th>类型</th><th>优先级</th><th>最新变化</th><th>变化幅度</th><th>上次检查</th><th></th></tr></thead><tbody>${rows.map(([name,type,priority,change,pct,color])=>`<tr><td><div class="cell-title">${name}</div><div class="cell-sub">示例 Watch Target</div></td><td>${type}</td><td><span class="tag ${priority==='高'?'red':priority==='中'?'amber':''}">${priority}</span></td><td>${change}</td><td><div class="flex items-center gap-6"><div class="change-bar"><span style="width:${pct}"></span></div><span class="num">${pct}</span></div></td><td class="muted nowrap">示例时间</td><td><button class="icon-button" aria-label="更多" title="更多">${icon('more-horizontal')}</button></td></tr>`).join('')}</tbody></table></div></section>`;
  shell(content);
}

/* (renderKnowledge moved below) */

function renderReports() {
  const actions = `<button class="btn">${icon('layout-template')}模板</button><button class="btn primary" data-toast="已创建报告草稿">${icon('plus')}新建报告</button>`;
  const content = `<section class="page">${header('输出与发布', '报告', '日报、周报和 Watchlist 变化报告。', actions)}<div class="toolbar"><button class="filter-chip active">全部</button><button class="filter-chip">草稿</button><button class="filter-chip">待核验</button><button class="filter-chip">已发布</button><span class="toolbar-spacer"></span></div><div class="report-layout"><aside class="report-list"><div class="report-list-item active"><h3>技术信源周度回顾</h3><p>专题周报 · 草稿 · 示例时间</p></div><div class="report-list-item"><h3>AI 生态每日发现</h3><p>日报 · 已发布 · 示例时间</p></div><div class="report-list-item"><h3>MCP Watchlist 变化</h3><p>变化报告 · 待核验</p></div><div class="report-list-item"><h3>财经日历下周预览</h3><p>日历报告 · 已完成</p></div></aside><article class="report-preview"><div class="story-meta"><span class="tag amber">草稿</span><span class="meta">版本 3 · 引用检查通过</span></div><h2 class="report-document-title">技术信源周度回顾</h2><p>本报告汇总本周收录的高影响情报、Watchlist 变化和财经日历重点事件。所有事实引用均锁定到本地入库版本。</p><div class="story-actions"><button class="btn small" data-toast="报告已保存">${icon('save')}保存</button><button class="btn small" data-toast="已打开预览">${icon('eye')}预览</button><button class="btn primary small" data-toast="发布前检查已开始">${icon('send')}检查并发布</button></div><h3>本周重要变化</h3><p>技术开源、公开视频和财经日历仍是主要更新方向。多个官方来源出现文档或日程变化，其中部分信号已经进入持续跟踪。</p><h3>值得继续观察</h3><ul><li>公开项目的版本变化与社区反馈。</li><li>重要财经事件的预测、前值与发布时间。</li><li>视频热榜中的可下载公开内容。</li></ul><h3>引用状态</h3><div class="metric-line"><span>关键事实</span><span class="metric-value">8</span></div><div class="metric-line"><span>已验证 Citation</span><span class="metric-value">12 / 12</span></div><div class="metric-line"><span>未解决冲突</span><span class="metric-value">1</span></div></article></div></section>`;
  shell(content);
}

let calendarEvents = [];
let calendarSources = [];

function renderCalendar() {
  const now = new Date();
  const from = new Date(now.getTime() - 3 * 24 * 60 * 60 * 1000).toISOString();
  const to = new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000).toISOString();
  const actions = `<button class="btn" data-refresh-calendar>${icon('refresh-cw')}刷新日历</button><a class="btn" href="/api/v1/calendar.ics">${icon('calendar-plus')}订阅 ICS</a>`;
  const content = `<section class="page calendar-page">${header('统一宏观事件', '财经日历', '官方信源优先，聚合页只补充预测、前值和重要性；所有字段都能追溯来源。', actions)}
    <div class="stat-strip calendar-stats">
      <div class="stat"><div class="stat-label">日历事件</div><div class="stat-value" id="calendarEventCount">0</div></div>
      <div class="stat"><div class="stat-label">高重要</div><div class="stat-value" id="calendarHighCount">0</div></div>
      <div class="stat"><div class="stat-label">启用来源</div><div class="stat-value" id="calendarSourceCount">0</div></div>
      <div class="stat"><div class="stat-label">最后成功</div><div class="stat-value calendar-small-stat" id="calendarLastSuccess">暂无</div></div>
    </div>
    <div class="toolbar calendar-toolbar">
      <input type="hidden" id="calendarFrom" value="${from}">
      <input type="hidden" id="calendarTo" value="${to}">
      <select class="select" id="calendarCountry" data-calendar-filter aria-label="国家"><option value="">全部国家</option><option value="US">美国</option><option value="CN">中国</option><option value="GB">英国</option><option value="EU">欧元区</option></select>
      <select class="select" id="calendarCategory" data-calendar-filter aria-label="类别"><option value="">全部类别</option><option value="INFLATION">通胀</option><option value="EMPLOYMENT">就业</option><option value="GROWTH">增长</option><option value="TRADE">贸易</option><option value="CENTRAL_BANK">央行</option><option value="CONSUMER">消费</option><option value="INDUSTRY">工业</option></select>
      <select class="select" id="calendarImportance" data-calendar-filter aria-label="重要性"><option value="">全部重要性</option><option value="HIGH">高</option><option value="MEDIUM">中</option><option value="LOW">低</option></select>
      <select class="select" id="calendarStatus" data-calendar-filter aria-label="状态"><option value="">全部状态</option><option value="SCHEDULED">未发布</option><option value="RELEASED">已发布</option><option value="REVISED">已修订</option><option value="DELAYED">延迟</option><option value="CANCELLED">取消</option></select>
      <span class="toolbar-spacer"></span>
      <span class="tag green" id="calendarRealityBadge">只显示真实入库</span>
    </div>
    <div class="calendar-layout">
      <main class="calendar-main">
        <div class="calendar-table-head">
          <strong>事件流</strong><span id="calendarWindowLabel">过去 3 天到未来 30 天</span>
        </div>
        <div class="table-wrap"><table class="data-table calendar-table"><thead><tr><th>时间</th><th>国家</th><th>事件</th><th>重要性</th><th>实际</th><th>预测</th><th>前值</th><th>来源</th></tr></thead><tbody id="calendarEventRows"><tr><td colspan="8" class="calendar-empty">正在读取真实财经日历…</td></tr></tbody></table></div>
      </main>
      <aside class="calendar-side">
        <section class="aside-section">
          <h2 class="section-title">${icon('satellite-dish')}来源健康</h2>
          <div id="calendarSourceRows"><div class="calendar-empty">正在读取来源状态…</div></div>
        </section>
        <section class="aside-section">
          <h2 class="section-title">${icon('shield-check')}验真规则</h2>
          <div class="calendar-rule"><strong>官方优先</strong><span>时间、实际值、修正值以官方机构为主。</span></div>
          <div class="calendar-rule"><strong>聚合补充</strong><span>Forecast/importance 可来自公开聚合页，缺失就显示暂无。</span></div>
          <div class="calendar-rule"><strong>不绕限制</strong><span>403/风控/条款限制会显示为来源告警。</span></div>
        </section>
      </aside>
    </div>
    <div class="drawer-overlay" id="calendarEvidenceOverlay">
      <aside class="agent-drawer calendar-evidence-drawer" aria-label="财经日历来源证据">
        <div class="drawer-header">${icon('file-search')}<span class="drawer-title" id="calendarEvidenceTitle">事件证据</span><span class="topbar-spacer"></span><button class="icon-button" id="closeCalendarEvidence" aria-label="关闭" title="关闭">${icon('x')}</button></div>
        <div class="drawer-body" id="calendarEvidenceBody"><div class="calendar-empty">选择一个事件查看来源证据。</div></div>
      </aside>
    </div>
  </section>`;
  shell(content);
  hydrateCalendarData();
}

function calendarQuery() {
  const params = new URLSearchParams();
  const from = document.getElementById('calendarFrom')?.value;
  const to = document.getElementById('calendarTo')?.value;
  const country = document.getElementById('calendarCountry')?.value;
  const category = document.getElementById('calendarCategory')?.value;
  const importance = document.getElementById('calendarImportance')?.value;
  const status = document.getElementById('calendarStatus')?.value;
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  if (country) params.set('countries', country);
  if (category) params.set('categories', category);
  if (importance) params.set('importance', importance);
  if (status) params.set('status', status);
  params.set('limit', '300');
  return params;
}

async function hydrateCalendarData() {
  if (page !== 'calendar') return;
  try {
    const [eventsResponse, sourcesResponse] = await Promise.all([
      fetch(`/api/v1/calendar/events?${calendarQuery()}`, { credentials: 'same-origin' }),
      fetch('/api/v1/calendar/sources', { credentials: 'same-origin' })
    ]);
    if (!eventsResponse.ok) throw new Error(`calendar events ${eventsResponse.status}`);
    if (!sourcesResponse.ok) throw new Error(`calendar sources ${sourcesResponse.status}`);
    calendarEvents = await eventsResponse.json();
    calendarSources = await sourcesResponse.json();
    renderCalendarRows();
    renderCalendarSources();
    setLiveBadge('Live · 财经日历', true);
  } catch (error) {
    console.warn('SubtleSight calendar hydrate failed', error);
    const rows = document.getElementById('calendarEventRows');
    if (rows) rows.innerHTML = `<tr><td colspan="8" class="calendar-empty">财经日历读取失败，请检查后端日志。不会展示静态样例。</td></tr>`;
    setLiveBadge('财经日历未联通', false);
  }
}

function renderCalendarRows() {
  const body = document.getElementById('calendarEventRows');
  if (!body) return;
  document.getElementById('calendarEventCount').textContent = String(calendarEvents.length);
  document.getElementById('calendarHighCount').textContent = String(calendarEvents.filter(e => e.importance === 'HIGH').length);
  document.getElementById('calendarSourceCount').textContent = String(calendarSources.filter(s => s.enabled).length);
  const lastSuccess = calendarSources.map(s => s.lastSuccessAt).filter(Boolean).sort().at(-1);
  document.getElementById('calendarLastSuccess').textContent = lastSuccess ? relativeTime(lastSuccess) : '暂无';
  body.innerHTML = calendarEvents.length ? calendarEvents.map(calendarEventRow).join('') : `<tr><td colspan="8" class="calendar-empty">当前筛选范围没有真实入库事件。可以点击“刷新日历”触发真实采集。</td></tr>`;
  refreshIcons();
}

function calendarEventRow(event) {
  const source = calendarSources.find(s => s.id === event.primarySourceId);
  return `<tr class="calendar-event-row" data-calendar-event="${escapeHtml(event.id)}">
    <td><div class="cell-title">${escapeHtml(formatDateTime(event.scheduledAtUtc))}</div><div class="cell-sub">${escapeHtml(event.scheduledLocalText || event.sourceTimezone || '')}</div></td>
    <td><span class="calendar-country">${escapeHtml(event.countryCode || 'UN')}</span><div class="cell-sub">${escapeHtml(event.currency || '')}</div></td>
    <td><div class="cell-title">${escapeHtml(event.nameOriginal)}</div><div class="cell-sub">${escapeHtml(categoryLabel(event.category))} · ${escapeHtml(event.period || '期间待确认')}</div></td>
    <td>${importanceTag(event.importance)}</td>
    <td class="num">${valueOrDash(event.actual)}</td>
    <td class="num">${valueOrDash(event.forecast)}</td>
    <td class="num">${valueOrDash(event.previous)}</td>
    <td><div class="cell-title">${escapeHtml(source?.name || '来源待解析')}</div><div class="cell-sub">${escapeHtml(statusLabel(event.status))}</div></td>
  </tr>`;
}

function renderCalendarSources() {
  const list = document.getElementById('calendarSourceRows');
  if (!list) return;
  list.innerHTML = calendarSources.map(source => `<article class="calendar-source-row">
    <div><strong>${escapeHtml(source.name)}</strong><span>${escapeHtml(source.key)} · ${escapeHtml(source.tier)}</span></div>
    <span class="tag ${sourceHealthTag(source.health)}">${escapeHtml(sourceHealthLabel(source.health))}</span>
    <small>成功：${escapeHtml(source.lastSuccessAt ? relativeTime(source.lastSuccessAt) : '暂无')} · 解析 ${escapeHtml(source.lastParsedCount ?? 0)} · 新增 ${escapeHtml(source.lastInsertedCount ?? 0)}</small>
    ${source.warning ? `<p>${escapeHtml(source.warning)}</p>` : ''}
  </article>`).join('');
}

async function refreshFinancialCalendar(button) {
  if (!getCookie('XSRF-TOKEN')) await fetch('/api/v1/auth/status', { credentials: 'same-origin' });
  const token = decodeURIComponent(getCookie('XSRF-TOKEN'));
  button.disabled = true;
  showToast('正在真实采集财经日历…');
  try {
    const response = await fetch('/api/v1/calendar/refresh?scope=today', { method: 'POST', credentials: 'same-origin', headers: token ? { 'X-XSRF-TOKEN': token } : {} });
    if (!response.ok) throw new Error(`calendar refresh ${response.status}`);
    const report = await response.json();
    await hydrateCalendarData();
    showToast(`财经日历采集完成：新增 ${report.totalInserted || 0}，更新 ${report.totalUpdated || 0}`);
  } catch (error) {
    console.warn('calendar refresh failed', error);
    showToast('财经日历采集失败，请看来源状态');
  } finally {
    button.disabled = false;
  }
}

async function openCalendarEvidence(id) {
  const body = document.getElementById('calendarEvidenceBody');
  const overlay = document.getElementById('calendarEvidenceOverlay');
  if (!body || !overlay) return;
  overlay.classList.add('open');
  body.innerHTML = `<div class="calendar-empty">正在读取来源证据…</div>`;
  try {
    const response = await fetch(`/api/v1/calendar/events/${id}`, { credentials: 'same-origin' });
    if (!response.ok) throw new Error(`calendar detail ${response.status}`);
    const detail = await response.json();
    document.getElementById('calendarEvidenceTitle').textContent = detail.event?.nameOriginal || '事件证据';
    body.innerHTML = calendarEvidenceHtml(detail);
    refreshIcons();
  } catch (error) {
    body.innerHTML = `<div class="calendar-empty">证据读取失败。</div>`;
  }
}

function calendarEvidenceHtml(detail) {
  const event = detail.event || {};
  const evidence = detail.evidence || [];
  const revisions = detail.revisions || [];
  return `<section class="calendar-evidence-summary">
    <span class="tag ${event.status === 'RELEASED' ? 'green' : 'amber'}">${escapeHtml(statusLabel(event.status))}</span>
    <h2>${escapeHtml(event.nameOriginal || '')}</h2>
    <div class="metric-line"><span>实际 / 预测 / 前值</span><span class="metric-value">${valueOrDash(event.actual)} / ${valueOrDash(event.forecast)} / ${valueOrDash(event.previous)}</span></div>
    <div class="metric-line"><span>事件键</span><span class="metric-value">${escapeHtml(event.eventKey || '')}</span></div>
  </section>
  <section class="calendar-evidence-section"><h3>来源证据</h3>${evidence.length ? evidence.map(item => `<article class="calendar-evidence-item">
    <strong>${escapeHtml(item.sourceTier)} · ${escapeHtml(relativeTime(item.fetchedAt))}</strong>
    <a href="${escapeHtml(item.sourceUrl)}" target="_blank" rel="noreferrer">${escapeHtml(item.sourceUrl || '来源链接')}</a>
    <pre>${escapeHtml(item.rawFieldsJson || '{}')}</pre>
    ${item.warningsJson && item.warningsJson !== '[]' ? `<p>${escapeHtml(item.warningsJson)}</p>` : ''}
  </article>`).join('') : '<div class="calendar-empty">暂无 evidence，不会补静态证据。</div>'}</section>
  <section class="calendar-evidence-section"><h3>修订历史</h3>${revisions.length ? revisions.map(item => `<article class="calendar-revision-item"><strong>${escapeHtml(relativeTime(item.changedAt))}</strong><pre>${escapeHtml(item.changedFieldsJson || '{}')}</pre></article>`).join('') : '<div class="calendar-empty">暂无字段修订。</div>'}</section>`;
}

function formatDateTime(value) {
  if (!value) return '时间待确认';
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}

function relativeTime(value) {
  const minutes = minutesSince(value);
  if (minutes == null) return '暂无';
  if (minutes < 2) return '刚刚';
  if (minutes < 60) return `${minutes} 分钟前`;
  if (minutes < 1440) return `${Math.round(minutes / 60)} 小时前`;
  return `${Math.round(minutes / 1440)} 天前`;
}

function valueOrDash(value) { return value == null || value === '' ? '暂无' : escapeHtml(value); }
function categoryLabel(value) { return ({ INFLATION: '通胀', EMPLOYMENT: '就业', GROWTH: '增长', TRADE: '贸易', CENTRAL_BANK: '央行', SPEECH: '讲话', HOUSING: '住房', CONSUMER: '消费', INDUSTRY: '工业', HOLIDAY: '假日', OTHER: '其他' })[value] || value || '其他'; }
function statusLabel(value) { return ({ SCHEDULED: '未发布', CONFIRMED: '已确认', RELEASED: '已发布', REVISED: '已修订', DELAYED: '延迟', CANCELLED: '取消' })[value] || value || '未知'; }
function importanceTag(value) { const cls = value === 'HIGH' ? 'red' : value === 'MEDIUM' ? 'amber' : ''; const label = ({ HIGH: '高', MEDIUM: '中', LOW: '低' })[value] || value || '中'; return `<span class="tag ${cls}">${label}</span>`; }
function sourceHealthTag(value) { return value === 'HEALTHY' ? 'green' : value === 'DEGRADED' ? 'amber' : value === 'DISABLED' ? '' : 'red'; }
function sourceHealthLabel(value) { return ({ HEALTHY: '健康', DEGRADED: '告警', DOWN: '失败', DISABLED: '停用' })[value] || value || '未知'; }

function renderSources() {
  const rows = [['OpenAI Blog','RSS / 网站','健康','最近成功','一级来源','openai.com'],['GitHub Trending','GitHub','健康','最近成功','发现源','github.com'],['arXiv Agent Query','arXiv','健康','最近成功','论文','arxiv.org'],['Hacker News','社区','限流','等待重试','社区线索','news.ycombinator.com'],['示例 Reader Provider','Reader API','错误','凭据失效','服务','jina.ai']];
  const actions = `<button class="btn">${icon('upload')}导入 OPML</button><button class="btn primary" data-open-modal="sourceModal">${icon('plus')}添加信源</button>`;
  const content = `<section class="page">${header('采集与健康', '信源', '持续来源、主动发现 Provider 和内容读取服务。', actions)}<div class="stat-strip"><div class="stat"><div class="stat-label">持续信源</div><div class="stat-value">126</div></div><div class="stat"><div class="stat-label">24h 成功率</div><div class="stat-value">98.7%</div></div><div class="stat"><div class="stat-label">待处理错误</div><div class="stat-value">2</div></div><div class="stat"><div class="stat-label">发现候选</div><div class="stat-value">43</div></div></div><div class="toolbar"><input class="field" type="search" placeholder="搜索信源…" aria-label="搜索信源"><button class="filter-chip active">全部</button><button class="filter-chip">持续源</button><button class="filter-chip">搜索 Provider</button><button class="filter-chip">错误</button><span class="toolbar-spacer"></span><button class="icon-button" data-toast="信源健康检查已开始" aria-label="检查全部" title="检查全部">${icon('stethoscope')}</button></div><div class="table-wrap"><table class="data-table"><thead><tr><th>名称</th><th>类型</th><th>健康</th><th>最近运行</th><th>角色</th><th>计划</th><th></th></tr></thead><tbody>${rows.map(([name,type,health,last,role,domain])=>`<tr><td><div class="flex items-center gap-6"><img class="source-logo" src="${favicon(domain)}" width="17" height="17" alt=""><div><div class="cell-title">${name}</div><div class="cell-sub">${domain}</div></div></div></td><td>${type}</td><td><div class="health"><span class="health-dot ${health==='限流'?'warning':health==='错误'?'error':''}"></span>${health}</div></td><td>${last}</td><td><span class="tag">${role}</span></td><td class="muted">每 2 小时</td><td><button class="icon-button" aria-label="配置" title="配置">${icon('settings')}</button></td></tr>`).join('')}</tbody></table></div></section>`;
  shell(content);
}

function renderViews() {
  const content = `<section class="page">${header('组合发现规则', '自定义视图', '用实体、主题、来源、关键词和排除条件定义专属信息流。', `<button class="btn">${icon('copy')}复制视图</button><button class="btn primary" data-toast="视图规则已保存">${icon('save')}保存视图</button>`)}<div class="toolbar"><input class="field" value="Agent Infrastructure" aria-label="视图名称"><span class="tag green">18 条匹配</span><span class="toolbar-spacer"></span><div class="toggle on" role="switch" aria-checked="true" tabindex="0" title="启用提醒"></div><span class="meta">提醒</span></div><div class="view-builder"><aside class="rules-panel"><div class="rule-block"><h3>包含主题</h3><div class="token-input"><span class="tag blue">AI Agent</span><span class="tag blue">Agent Infrastructure</span><input placeholder="添加主题…" aria-label="添加主题"></div></div><div class="rule-block"><h3>跟踪实体</h3><div class="token-input"><span class="tag">LangGraph</span><span class="tag">MCP</span><span class="tag">OpenAI Agents</span><input placeholder="添加实体…" aria-label="添加实体"></div></div><div class="rule-block"><h3>来源类型</h3>${['官方来源','GitHub','论文','新闻','社区线索'].map((x,i)=>`<label class="check-row"><input type="checkbox" ${i<4?'checked':''}>${x}</label>`).join('')}</div><div class="rule-block"><h3>排除条件</h3><div class="token-input"><span class="tag red">招聘</span><span class="tag red">课程推广</span><input placeholder="添加排除词…" aria-label="添加排除词"></div></div><div class="rule-block"><h3>默认排序</h3><select class="form-control"><option>正在发生</option><option>重要情报</option><option>最新收录</option></select></div></aside><main class="view-preview"><div class="pane-header"><span class="pane-title">实时预览</span><span class="meta">规则变更后自动刷新</span></div>${signals.slice(0,3).map(signalRow).join('')}</main></div></section>`;
  shell(content);
}

function renderTasks() {
  const rows = [['真实信源批量采集','Collect','运行中','68%','06:42'],['OpenAI Blog 增量采集','Fetch','已完成','100%','00:18'],['arXiv Agent Query','Discovery','等待重试','42%','02:11'],['Weekly Report 发布检查','Publish','等待确认','75%','01:04'],['内容入库索引','Index','已完成','100%','00:33']];
  const content = `<section class="page">${header('运行状态', '任务中心', '采集、解析、索引和发布任务。', `<button class="btn" data-toast="失败任务已重新入队">${icon('rotate-ccw')}重试失败任务</button>`)}<div class="stat-strip"><div class="stat"><div class="stat-label">运行中</div><div class="stat-value">2</div></div><div class="stat"><div class="stat-label">等待</div><div class="stat-value">5</div></div><div class="stat"><div class="stat-label">需要处理</div><div class="stat-value">2</div></div><div class="stat"><div class="stat-label">今日完成</div><div class="stat-value">146</div></div></div><div class="toolbar"><button class="filter-chip active">全部</button><button class="filter-chip">运行中</button><button class="filter-chip">等待</button><button class="filter-chip">错误</button><span class="toolbar-spacer"></span><select class="select"><option>全部类型</option><option>采集</option><option>解析</option><option>发布</option></select></div><div class="table-wrap"><table class="data-table"><thead><tr><th>任务</th><th>类型</th><th>状态</th><th>进度</th><th>耗时</th><th>优先级</th><th></th></tr></thead><tbody>${rows.map(([name,type,state,pct,time],i)=>`<tr><td><div class="cell-title">${name}</div><div class="cell-sub">任务 ID · 示例</div></td><td>${type}</td><td><span class="tag ${state==='运行中'?'orange':state==='已完成'?'green':state==='等待重试'?'red':'amber'}">${state}</span></td><td><div class="flex items-center gap-6"><div class="change-bar"><span style="width:${pct}"></span></div><span class="num">${pct}</span></div></td><td class="num">${time}</td><td>${i===0?'高':'普通'}</td><td><button class="icon-button task-toggle" aria-label="${state==='运行中'?'暂停':'更多'}" title="${state==='运行中'?'暂停':'更多'}">${icon(state==='运行中'?'pause':'more-horizontal')}</button></td></tr>`).join('')}</tbody></table></div></section>`;
  shell(content);
}

function renderSettings() {
  const content = `<section class="page">${header('系统管理', '设置', '采集策略、安全、存储和备份。', `<button class="btn primary" data-toast="设置已保存">${icon('save')}保存设置</button>`)}<div class="settings-layout"><aside class="settings-nav"><button class="settings-link active">采集策略</button><button class="settings-link">存储与保留</button><button class="settings-link">安全</button><button class="settings-link">备份与诊断</button></aside><main><section class="settings-section"><h2>采集策略</h2><p>控制真实信源采集频率、并发和失败重试。</p><div class="form-row"><label class="form-label" for="collectInterval">默认间隔</label><select id="collectInterval" class="form-control"><option>每 30 分钟</option><option>每 2 小时</option><option>每天</option><option>手动</option></select></div><div class="form-row"><label class="form-label" for="sourceLimit">单轮来源上限</label><input id="sourceLimit" class="form-control" type="number" value="8"></div><div class="form-row"><span class="form-label">视频下载</span><div><div class="toggle" role="switch" aria-checked="false" tabindex="0"></div><div class="form-help">仅启用公开视频或显式配置的 OpenCLI 桥接源。</div></div></div></section><section class="settings-section"><h2>存储与保留</h2><p>管理 SQLite、原文 blob、报告和日志保留周期。</p><div class="form-row"><label class="form-label" for="retention">保留周期</label><select id="retention" class="form-control"><option>永久保留</option><option>180 天</option><option>90 天</option></select></div><div class="form-row"><label class="form-label" for="blobLimit">单文件上限</label><input id="blobLimit" class="form-control" value="50MB" readonly></div></section><section class="settings-section"><h2>备份</h2><p>备份 SQLite、原文、报告和配置；Secret 不进入普通备份。</p><div class="form-row"><span class="form-label">上次备份</span><div><span>示例时间</span><div style="margin-top:10px"><button class="btn" data-toast="备份任务已开始">${icon('archive')}立即备份</button></div></div></div></section></main></div></section>`;
  shell(content);
}

/* --- 知识库 mock 数据 --- */
/* --- 知识库 API 工具函数 --- */
function kbApiCsrfHeader(){
  const token = decodeURIComponent(getCookie('XSRF-TOKEN'));
  return token ? { 'X-XSRF-TOKEN': token } : {};
}
function kbApiHeaders(extra){
  return Object.assign({ 'Content-Type':'application/json' }, kbApiCsrfHeader(), extra || {});
}
async function kbEnsureCsrf(){
  if (!getCookie('XSRF-TOKEN')) await fetch('/api/v1/auth/status', { credentials: 'same-origin' });
}
async function kbFetchFolders(){
  const r=await fetch('/api/v1/knowledge/folders',{credentials:'same-origin'});
  if(!r.ok)throw new Error('获取文件夹列表失败: '+r.status);
  return r.json();
}
async function kbFetchFiles(folderId){
  const q=folderId?'?folderId='+encodeURIComponent(folderId):'';
  const r=await fetch('/api/v1/knowledge/files'+q,{credentials:'same-origin'});
  if(!r.ok)throw new Error('获取文件列表失败: '+r.status);
  return r.json();
}
async function kbCreateFolder(parentId,name){
  await kbEnsureCsrf();
  const r=await fetch('/api/v1/knowledge/folders',{method:'POST',credentials:'same-origin',headers:kbApiHeaders(),body:JSON.stringify({parentId:parentId||null,name:name})});
  if(!r.ok){let d;try{d=(await r.json()).detail}catch{}throw new Error(d||'创建文件夹失败: '+r.status);}
  return r.json();
}
async function kbRenameFile(id,name){
  await kbEnsureCsrf();
  const r=await fetch('/api/v1/knowledge/files/'+encodeURIComponent(id)+'/rename',{method:'POST',credentials:'same-origin',headers:kbApiHeaders(),body:JSON.stringify({name:name})});
  if(!r.ok){let d;try{d=(await r.json()).detail}catch{}throw new Error(d||'重命名失败: '+r.status);}
  return r.json();
}
async function kbMoveFile(id,folderId){
  await kbEnsureCsrf();
  const r=await fetch('/api/v1/knowledge/files/'+encodeURIComponent(id)+'/move',{method:'POST',credentials:'same-origin',headers:kbApiHeaders(),body:JSON.stringify({folderId:folderId||null})});
  if(!r.ok){let d;try{d=(await r.json()).detail}catch{}throw new Error(d||'移动文件失败: '+r.status);}
  return r.json();
}
async function kbDeleteFile(id){
  await kbEnsureCsrf();
  const r=await fetch('/api/v1/knowledge/files/'+encodeURIComponent(id),{method:'DELETE',credentials:'same-origin',headers:kbApiCsrfHeader()});
  if(!r.ok){let d;try{d=(await r.json()).detail}catch{}throw new Error(d||'删除失败: '+r.status);}
}
function kbUploadXhr(folderId,file,onProgress){
  return new Promise(function(resolve,reject){
    var xhr=new XMLHttpRequest();
    var q=folderId?'?folderId='+encodeURIComponent(folderId):'';
    xhr.open('POST','/api/v1/knowledge/upload'+q);
    xhr.withCredentials=true;
    var token=decodeURIComponent(getCookie('XSRF-TOKEN')||'');
    if(token)xhr.setRequestHeader('X-XSRF-TOKEN',token);
    xhr.upload.onprogress=function(e){if(e.lengthComputable&&onProgress)onProgress(Math.round(e.loaded/e.total*100));};
    xhr.onload=function(){if(xhr.status>=200&&xhr.status<300){resolve();}else{var d=xhr.statusText;try{d=JSON.parse(xhr.responseText).detail||d;}catch{}reject(new Error(d));}};
    xhr.onerror=function(){reject(new Error('网络错误，上传失败'));};
    var body=new FormData();body.append('files',file);xhr.send(body);
  });
}
async function kbDeleteFolder(id){
  const r=await fetch('/api/v1/knowledge/folders/'+encodeURIComponent(id),{method:'DELETE',credentials:'same-origin'});
  if(!r.ok){let d;try{d=(await r.json()).detail}catch{}throw new Error(d||'删除文件夹失败: '+r.status);}
}
async function kbSearch(q){
  const r=await fetch('/api/v1/knowledge/search?q='+encodeURIComponent(q),{credentials:'same-origin'});
  if(!r.ok)throw new Error('搜索失败: '+r.status);
  return r.json();
}
async function kbPreview(id){
  const r=await fetch('/api/v1/knowledge/files/'+encodeURIComponent(id)+'/preview',{credentials:'same-origin'});
  if(!r.ok)throw new Error('预览失败: '+r.status);
  return r.json();
}

/* --- 自定义确认弹窗（直接操作 DOM，不依赖 KB 闭包，替代 confirm()） --- */
function kbShowConfirm(message,danger){
  return new Promise(function(resolve){
    var layer=document.getElementById('kbConfirmModal');
    if(!layer){resolve(false);return;}
    var msgEl=layer.querySelector('.modal-body p');
    var titleEl=layer.querySelector('.modal-title');
    var okBtn=layer.querySelector('#kbConfirmOkBtn');
    window._kbConfirmResolve=resolve;
    /* 设置内容 */
    if(msgEl)msgEl.textContent=message;
    if(titleEl)titleEl.innerHTML=(danger?'<i data-lucide="alert-triangle" aria-hidden="true" style="width:18px;height:18px;color:var(--red);vertical-align:middle;margin-right:6px"></i>':'')+'确认操作';
    if(okBtn)okBtn.className='btn'+(danger?' danger':' primary');
    if(okBtn)okBtn.innerHTML=(danger?'<i data-lucide="trash-2" aria-hidden="true" style="width:15px;height:15px;vertical-align:middle;margin-right:4px"></i>':'')+'确定删除';
    layer.classList.add('open');
    /* 刷新图标 */
    if(window.lucide)setTimeout(function(){window.lucide.createIcons();},0);
    /* 按钮事件 —— 一次性 */
    function cleanup(result){
      layer.classList.remove('open');
      window._kbConfirmResolve=null;
      var c1=document.getElementById('kbConfirmCancelBtn');
      var c2=document.getElementById('kbConfirmCancelBtn2');
      if(okBtn)okBtn.onclick=null;if(c1)c1.onclick=null;if(c2)c2.onclick=null;
      resolve(result);
    }
    if(okBtn)okBtn.onclick=function(){cleanup(true);};
    var cancel1=document.getElementById('kbConfirmCancelBtn');
    var cancel2=document.getElementById('kbConfirmCancelBtn2');
    if(cancel1)cancel1.onclick=function(){cleanup(false);};
    if(cancel2)cancel2.onclick=function(){cleanup(false);};
  });
}

/* --- 知识库工具函数（纯函数，不依赖 mock 数据） --- */
function kbFileKind(ext){
  switch(ext){
    case'pdf':return{cls:'kb-pdf',label:'PDF'};
    case'xlsx':case'xls':case'csv':return{cls:'kb-xlsx',label:'X'};
    case'docx':case'doc':return{cls:'kb-docx',label:'W'};
    case'md':return{cls:'kb-md',label:'M↓'};
    case'png':case'jpg':case'jpeg':case'gif':case'webp':case'svg':case'bmp':return{cls:'kb-img',label:'IMG'};
    case'txt':case'log':return{cls:'kb-txt',label:'TXT'};
    default:return{cls:'kb-txt',label:ext?ext.slice(0,3).toUpperCase():'FILE'};
  }
}
function kbFmtSize(bytes){if(bytes<1024)return bytes+' B';var u=['KB','MB','GB'];var v=bytes/1024,i=0;while(v>=1024&&i<u.length-1){v/=1024;i++}return(v>=100?v.toFixed(0):v.toFixed(1))+' '+u[i];}
function kbRelTime(iso){var diff=Date.now()-new Date(iso).getTime();var m=Math.floor(diff/60000);if(m<1)return'刚刚';if(m<60)return m+' 分钟前';var h=Math.floor(m/60);if(h<24)return'今天';if(h<48)return'昨天';var d=Math.floor(h/24);if(d<30)return d+' 天前';return new Date(iso).toLocaleDateString();}
function kbEscape(str){return String(str).replace(/[&<>"']/g,function(m){return{'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]||m;})}

/* --- 知识树渲染（纯函数） --- */
function kbRenderTree(tree,currentId,collapsedSet){
  var html='';
  for(var i=0;i<tree.length;i++){
    var node=tree[i];var isCollapsed=collapsedSet&&collapsedSet.has(node.id);
    var hasChildren=node.children&&node.children.length>0;
    html+='<div class="kb-tree-branch'+(isCollapsed?' collapsed':'')+'" data-folder-id="'+node.id+'">';
    html+='<button class="kb-tree-row'+(currentId===node.id?' selected':'')+'" data-folder-click="'+node.id+'">';
    html+='<span class="kb-chev'+(hasChildren?'':' hidden')+'" data-chev="'+node.id+'">'+icon('chevron-down')+'</span>';
    html+=icon('folder')+'<span class="kb-tree-name" title="'+kbEscape(node.name)+'">'+kbEscape(node.name)+'</span>';
    html+='</button>';
    if(hasChildren){html+='<div class="kb-tree-children">'+kbRenderTree(node.children,currentId,collapsedSet)+'</div>';}
    html+='</div>';
  }
  return html;
}

/* --- 文件卡片/行渲染（纯函数，path 可选，仅搜索结果时传入） --- */
function kbRenderCard(item,view,selectedCard,path){
  if(item.kind==='folder'){
    var f=item.folder;var sel=selectedCard==='f-'+f.id;
    var h='<article class="kb-file-card'+(sel?' selected':'')+'" tabindex="0" data-card="f-'+f.id+'" data-dbl-folder="'+f.id+'">';
    h+='<div class="kb-visual"><div class="kb-folder-icon"></div></div>';
    h+='<div class="kb-name" title="'+kbEscape(f.name)+'">'+kbEscape(f.name)+'</div>';
    h+='<div class="kb-meta">'+(path?'<span class="kb-path">'+kbEscape(path)+'</span> · ':'')+'文件夹 · '+kbRelTime(f.updatedAt)+'</div></article>';
    return h;
  }
  var file=item.file;var kind=kbFileKind(file.ext);var sel=selectedCard===file.id;
  var h='<article class="kb-file-card'+(sel?' selected':'')+'" tabindex="0" data-card="'+file.id+'" data-file="'+file.id+'">';
  h+='<div class="kb-visual"><div class="kb-doc-icon '+kind.cls+'">'+kind.label+'</div></div>';
  h+='<div class="kb-name" title="'+kbEscape(file.name)+'">'+kbEscape(file.name)+'</div>';
  h+='<div class="kb-meta">'+(path?'<span class="kb-path">'+kbEscape(path)+'</span> · ':'')+kbFmtSize(file.sizeBytes)+' · '+kbRelTime(file.updatedAt)+'</div></article>';
  return h;
}

/* --- 构建文件夹树（从平面列表构建嵌套结构） --- */
function kbBuildTree(folders,parentId){
  var result=[];
  for(var i=0;i<folders.length;i++){
    var f=folders[i];
    if((f.parentId||null)===(parentId||null)){
      result.push({id:f.id,parentId:f.parentId,name:f.name,createdAt:f.createdAt,updatedAt:f.updatedAt,children:kbBuildTree(folders,f.id)});
    }
  }
  return result;
}

/* --- 获取面包屑路径 --- */
function kbBreadcrumb(folders,folderId){
  var crumbs=[];var id=folderId;
  while(id){
    var f=null;for(var i=0;i<folders.length;i++){if(folders[i].id===id){f=folders[i];break;}}
    if(!f)break;crumbs.unshift({name:f.name,id:f.id});id=f.parentId||null;
  }
  return crumbs;
}
/* --- 展开树路径：将目标文件夹及其所有祖先从 collapsed 中移除 --- */
function kbExpandPath(folders,folderId,collapsedSet){
  var id=folderId;
  while(id){
    collapsedSet.delete(id);
    var f=null;for(var i=0;i<folders.length;i++){if(folders[i].id===id){f=folders[i];break;}}
    if(!f)break;id=f.parentId||null;
  }
}

/* --- 知识库主渲染函数（API 驱动） --- */
function renderKnowledge(){
  var KB={folders:[],files:[],current:null,collapsed:new Set(),view:'grid',ascending:true,keyword:'',selectedCard:null,
    newFolderOpen:false,newFolderName:'',renameTarget:null,renameValue:'',moveTarget:null,moveFolderId:'root',uploadOpen:false,
    contextFile:null,contextFolder:null,contextX:0,contextY:0,contextOpen:false,loading:true,error:null,
    searchResults:null,searching:false,
    previewFile:null,previewContent:null,previewBase64:null,previewKind:null,previewLoading:false};

  function render(){
    var tree=kbBuildTree(KB.folders,null);
    /* 当前文件夹下的子文件夹 */
    var subfolders=[];for(var i=0;i<KB.folders.length;i++){var f=KB.folders[i];if((f.parentId||null)===(KB.current||null))subfolders.push(f);}
    /* 当前文件夹下的文件 */
    var curFiles=[];for(var i=0;i<KB.files.length;i++){var f=KB.files[i];if((f.folderId||null)===(KB.current||null))curFiles.push(f);}
    var allItems=[];
    if(KB.searchResults){
      for(var i=0;i<KB.searchResults.folders.length;i++)allItems.push({kind:'folder',folder:KB.searchResults.folders[i]});
      for(var i=0;i<KB.searchResults.files.length;i++)allItems.push({kind:'file',file:KB.searchResults.files[i]});
    }else{
      for(var i=0;i<subfolders.length;i++)allItems.push({kind:'folder',folder:subfolders[i]});
      for(var i=0;i<curFiles.length;i++)allItems.push({kind:'file',file:curFiles[i]});
    }
    allItems.sort(function(a,b){var na=a.folder?a.folder.name:a.file.name;var nb=b.folder?b.folder.name:b.file.name;return KB.ascending?na.localeCompare(nb,'zh-CN'):nb.localeCompare(na,'zh-CN');});

    /* 所有更新时间 */
    var latest=null;
    if(!KB.searchResults){
      for(var i=0;i<KB.folders.length;i++){if(!latest||KB.folders[i].updatedAt>latest)latest=KB.folders[i].updatedAt;}
      for(var i=0;i<KB.files.length;i++){if(!latest||KB.files[i].updatedAt>latest)latest=KB.files[i].updatedAt;}
    }

    var breadcrumb=kbBreadcrumb(KB.folders,KB.current);
    var emptyMsg=KB.searchResults?'没有搜索到匹配的内容':(KB.error||'这个文件夹还是空的');
    var searchHint=KB.keyword.trim()?' 搜索「'+KB.keyword.trim()+'」':'';
    var fileHtml=allItems.length?
      (KB.searchResults?'<div class="kb-search-hint">找到 '+allItems.length+' 条'+searchHint+'</div>':'')+
      '<div class="kb-file-grid'+(KB.view==='list'?' list-view':'')+'">'+allItems.map(function(it){return kbRenderCard(it,KB.view,KB.selectedCard,it.folder?it.folder.path:it.file.path);}).join('')+'</div>':
      (KB.searching?
        '<div class="kb-empty">正在搜索'+searchHint+'…</div>':
        '<div class="kb-empty">'+emptyMsg+'</div>');
    var countInfo=KB.searchResults?'搜索'+searchHint+' 共 '+allItems.length+' 条结果':'共 '+allItems.length+' 项'+(latest?' · 更新于 '+kbRelTime(latest):'');

    if(KB.loading){
      var loadingHtml='<section class="page kb-page" id="kb-page">'+''+'<div class="empty" style="min-height:400px">正在加载知识库…</div></section>';
      shell(loadingHtml);return;
    }

    var content='<section class="page kb-page" id="kb-page">'+
      ''+
      '<section class="kb-workspace'+(KB.previewFile?' has-preview':'')+'">'+
        '<aside class="kb-tree-pane">'+
          '<div class="kb-pane-header"><span class="kb-pane-title">知识树</span><div class="kb-pane-actions">'+
            '<button class="kb-icon-btn" aria-label="新建文件夹" title="新建文件夹" id="kbNewFolderBtn">'+icon('folder-plus')+'</button>'+
            '<button class="kb-icon-btn" aria-label="刷新知识树" title="刷新知识树" id="kbRefreshBtn">'+icon('refresh-cw')+'</button>'+
            '<button class="kb-icon-btn" aria-label="全部折叠" title="全部折叠" id="kbCollapseAllBtn">'+icon('chevrons-up-down')+'</button>'+
          '</div></div>'+
          '<div class="kb-tree-scroll"><div class="kb-tree-branch">'+
            '<button class="kb-tree-row kb-root-btn'+(KB.current===null?' selected':'')+'">'+icon('folder-open')+'<span class="kb-tree-name">我的知识库</span></button>'+
            '<div class="kb-tree-children">'+kbRenderTree(tree,KB.current,KB.collapsed)+'</div>'+
          '</div></div>'+
        '</aside>'+
        '<section class="kb-content-pane">'+
          '<div class="kb-content-toolbar">'+
            '<div class="kb-breadcrumb">'+icon('folder-open')+'<span><a class="kb-crumb" data-crumb="root" href="#">知识库</a></span>'+
              breadcrumb.map(function(part,i){return '<span style="display:inline-flex;align-items:center;gap:10px"><span>/</span>'+(i===breadcrumb.length-1?'<strong>'+kbEscape(part.name)+'</strong>':'<a class="kb-crumb" data-crumb="'+part.id+'" href="#">'+kbEscape(part.name)+'</a>')+'</span>';}).join('')+
            '</div>'+
            '<div class="kb-toolbar-right">'+
              '<label class="kb-search"><input id="kbSearchInput" placeholder="搜索文件或文件夹" class="kb-input" value="'+kbEscape(KB.keyword)+'">'+icon('search')+'</label>'+
              '<div class="kb-segmented" aria-label="视图切换">'+
                '<button class="kb-icon-btn'+(KB.view==='grid'?' active':'')+'" id="kbGridBtn" aria-label="网格视图" title="网格视图">'+icon('grid-3x3')+'</button>'+
                '<button class="kb-icon-btn'+(KB.view==='list'?' active':'')+'" id="kbListBtn" aria-label="列表视图" title="列表视图">'+icon('list')+'</button>'+
              '</div>'+
              '<button class="kb-icon-btn" id="kbSortBtn" aria-label="排序" title="'+(KB.ascending?'按名称升序':'按名称降序')+'">'+(KB.ascending?icon('arrow-up'):icon('arrow-down'))+'</button>'+
              '<div class="kb-new-wrap" id="kbNewDropdownWrap">'+
                '<button class="kb-new-btn" id="kbNewDropdownBtn">'+icon('plus')+'<span>新建</span>'+icon('chevron-down')+'</button>'+
                '<div class="kb-new-dropdown" id="kbNewDropdown">'+
                  '<button id="kbNewFolderBtn2">'+icon('folder')+' 新建文件夹</button>'+
                  '<button id="kbNewUploadBtn2">'+icon('upload')+' 上传文件</button>'+
                '</div>'+
              '</div>'+
            '</div>'+
          '</div>'+
          '<div class="kb-content-meta"><span>'+countInfo+'</span></div>'+
          '<div class="kb-file-area" id="kbFileArea">'+fileHtml+'</div>'+
        '</section>'+
        (KB.previewFile?'<aside class="kb-preview-pane" id="kbPreviewPane">'+
          '<div class="kb-preview-resize-handle" id="kbPreviewResize"></div>'+
          '<div class="kb-preview-header">'+
            '<div class="kb-preview-file-info">'+
              '<strong>'+kbEscape(KB.previewFile.name)+'</strong>'+
              '<span class="kb-preview-meta">'+kbFmtSize(KB.previewFile.sizeBytes)+'</span>'+
            '</div>'+
            '<button class="kb-icon-btn" id="kbPreviewCloseBtn" aria-label="关闭预览" title="关闭预览">'+icon('x')+'</button>'+
          '</div>'+
          '<div class="kb-preview-body">'+
            (KB.previewLoading?'<div class="kb-empty" style="height:200px">正在加载预览…</div>':
            KB.previewKind==='text'?'<pre class="kb-preview-text"><code>'+kbEscape(KB.previewContent||'')+'</code></pre>':
            KB.previewKind==='image'?'<div class="kb-preview-image"><img src="data:'+kbEscape(KB.previewFile.mimeType||'image/png')+';base64,'+KB.previewBase64+'" alt="'+kbEscape(KB.previewFile.name)+'"></div>':
            KB.previewKind==='pdf'?'<iframe class="kb-preview-iframe" src="/api/v1/knowledge/files/'+KB.previewFile.id+'/view" title="'+kbEscape(KB.previewFile.name)+'"></iframe>':
            KB.previewKind==='officeHtml'?'<div class="kb-office-html">'+KB.previewContent+'</div>':
            '<div class="kb-empty"><p>此文件类型暂不支持预览</p><a class="btn primary" href="/api/v1/knowledge/files/'+KB.previewFile.id+'/view" target="_blank" style="margin-top:12px">'+icon('download')+'下载查看</a></div>')+
          '</div>'+
        '</aside>':'')+
      '</section>'+
      /* 新建文件夹弹窗 */
      '<div class="modal-layer'+(KB.newFolderOpen?' open':'')+'" id="kbFolderModal">'+
        '<div class="modal"><div class="modal-header"><span class="modal-title">新建文件夹</span><button class="icon-button" data-close-kb-modal>'+icon('x')+'</button></div>'+
        '<div class="modal-body"><label class="field-label">文件夹名称</label><input class="field" id="kbNewFolderInput" placeholder="请输入名称" style="width:100%"></div>'+
        '<div class="modal-footer"><button class="btn" data-close-kb-modal>取消</button><button class="btn primary" id="kbCreateFolderConfirm">创建</button></div></div>'+
      '</div>'+
      /* 重命名弹窗 */
      '<div class="modal-layer'+(KB.renameTarget?' open':'')+'" id="kbRenameModal">'+
        '<div class="modal"><div class="modal-header"><span class="modal-title">重命名文件</span><button class="icon-button" data-close-kb-modal>'+icon('x')+'</button></div>'+
        '<div class="modal-body"><label class="field-label">文件名称</label><input class="field" id="kbRenameInput" placeholder="请输入新名称" style="width:100%" value="'+(KB.renameTarget?kbEscape(KB.renameTarget.name):'')+'"></div>'+
        '<div class="modal-footer"><button class="btn" data-close-kb-modal>取消</button><button class="btn primary" id="kbRenameConfirm">保存</button></div></div>'+
      '</div>'+
      /* 移动弹窗 */
      '<div class="modal-layer'+(KB.moveTarget?' open':'')+'" id="kbMoveModal">'+
        '<div class="modal"><div class="modal-header"><span class="modal-title">移动文件</span><button class="icon-button" data-close-kb-modal>'+icon('x')+'</button></div>'+
        '<div class="modal-body"><label class="field-label">目标位置</label>'+
        '<select class="field" id="kbMoveSelect" style="width:100%"><option value="root">我的知识库</option>'+
          KB.folders.map(function(f){return '<option value="'+f.id+'"'+(KB.moveFolderId===f.id?' selected':'')+'>'+(f.parentId?'— ':'')+kbEscape(f.name)+'</option>';}).join('')+
        '</select></div>'+
        '<div class="modal-footer"><button class="btn" data-close-kb-modal>取消</button><button class="btn primary" id="kbMoveConfirm">移动</button></div></div>'+
      '</div>'+
      /* 上传弹窗 */
      '<div class="modal-layer'+(KB.uploadOpen?' open':'')+'" id="kbUploadModal">'+
        '<div class="modal"><div class="modal-header"><span class="modal-title">上传文件</span><button class="icon-button" data-close-kb-modal>'+icon('x')+'</button></div>'+
        '<div class="modal-body"><div class="kb-upload-area">'+
          '<div class="kb-upload-icon">'+icon('upload-cloud')+'</div>'+
          '<div class="kb-upload-text"><strong>点击或拖拽文件到此处上传</strong><span>支持 PDF、Office、Markdown、图片等各类文件</span></div>'+
          '<input type="file" multiple id="kbFileInput" style="display:none">'+
          '<button class="btn primary" id="kbUploadBtn">选择文件</button>'+
        '</div></div>'+
        '<div class="modal-footer"><button class="btn" data-close-kb-modal>关闭</button></div></div>'+
      '</div>'+
      /* 确认弹窗（由 kbShowConfirm 动态填充内容并显示） */
      '<div class="modal-layer" id="kbConfirmModal">'+
        '<div class="modal" style="max-width:420px"><div class="modal-header"><span class="modal-title">确认操作</span><button class="icon-button" id="kbConfirmCancelBtn">'+icon('x')+'</button></div>'+
        '<div class="modal-body"><p style="margin:8px 0;line-height:1.6;color:var(--ink)"></p></div>'+
        '<div class="modal-footer"><button class="btn" id="kbConfirmCancelBtn2">取消</button><button class="btn primary" id="kbConfirmOkBtn">确定删除</button></div></div>'+
      '</div>'+
      /* 右键菜单 */
      '<div class="kb-context-menu'+(KB.contextOpen?' open':'')+'" id="kbContextMenu" style="left:'+KB.contextX+'px;top:'+KB.contextY+'px">'+
        (KB.contextFolder?'':(
          '<button id="kbCtxMove">'+icon('folder-open')+' 移动到…</button>'+
          '<button id="kbCtxRename">'+icon('edit-3')+' 重命名</button>'+
          '<div class="kb-ctx-divider"></div>'
        ))+
        '<button id="kbCtxDelete" class="kb-ctx-danger">'+icon('trash-2')+' '+(KB.contextFolder?'删除文件夹':'删除')+'</button>'+
      '</div>'+
    '</section>';

    shell(content);
    attachEvents();
    refreshIcons();
  }

  /* --- 异步加载知识库数据 --- */
  function loadData(callback){
    KB.loading=true;KB.error=null;
    var loadingShell='<section class="page kb-page" id="kb-page">'+''+'<div class="empty" style="min-height:400px">正在加载知识库…</div></section>';
    shell(loadingShell);
    kbFetchFolders().then(function(folders){
      KB.folders=folders;
      return kbFetchFiles(KB.current);
    }).then(function(files){
      KB.files=files;KB.loading=false;
      render();
      if(callback)callback();
    }).catch(function(err){
      KB.loading=false;KB.error='加载失败: '+err.message;
      render();
      showToast(KB.error);
    });
  }

  /* --- 静默更新文件列表区域（不重建整个页面，保持输入框焦点和事件） --- */
  function kbUpdateFileArea(){
    var subfolders=[];for(var i=0;i<KB.folders.length;i++){var f=KB.folders[i];if((f.parentId||null)===(KB.current||null))subfolders.push(f);}
    var curFiles=[];for(var i=0;i<KB.files.length;i++){var f=KB.files[i];if((f.folderId||null)===(KB.current||null))curFiles.push(f);}
    var allItems=[];
    if(KB.searchResults){
      for(var i=0;i<KB.searchResults.folders.length;i++)allItems.push({kind:'folder',folder:KB.searchResults.folders[i]});
      for(var i=0;i<KB.searchResults.files.length;i++)allItems.push({kind:'file',file:KB.searchResults.files[i]});
    }else{
      for(var i=0;i<subfolders.length;i++)allItems.push({kind:'folder',folder:subfolders[i]});
      for(var i=0;i<curFiles.length;i++)allItems.push({kind:'file',file:curFiles[i]});
    }
    allItems.sort(function(a,b){var na=a.folder?a.folder.name:a.file.name;var nb=b.folder?b.folder.name:b.file.name;return KB.ascending?na.localeCompare(nb,'zh-CN'):nb.localeCompare(na,'zh-CN');});
    var latest=null;
    if(!KB.searchResults){
      for(var i=0;i<KB.folders.length;i++){if(!latest||KB.folders[i].updatedAt>latest)latest=KB.folders[i].updatedAt;}
      for(var i=0;i<KB.files.length;i++){if(!latest||KB.files[i].updatedAt>latest)latest=KB.files[i].updatedAt;}
    }
    var emptyMsg=KB.searchResults?'没有搜索到匹配的内容':(KB.error||'这个文件夹还是空的');
    var searchHint=KB.keyword.trim()?' 搜索「'+KB.keyword.trim()+'」':'';
    var fileHtml=allItems.length?
      (KB.searchResults?'<div class="kb-search-hint">找到 '+allItems.length+' 条'+searchHint+'</div>':'')+
      '<div class="kb-file-grid'+(KB.view==='list'?' list-view':'')+'">'+allItems.map(function(it){return kbRenderCard(it,KB.view,KB.selectedCard,it.folder?it.folder.path:it.file.path);}).join('')+'</div>':
      (KB.searching?
        '<div class="kb-empty">正在搜索'+searchHint+'…</div>':
        '<div class="kb-empty">'+emptyMsg+'</div>');
    var countInfo=KB.searchResults?'搜索'+searchHint+' 共 '+allItems.length+' 条结果':'共 '+allItems.length+' 项'+(latest?' · 更新于 '+kbRelTime(latest):'');
    var metaEl=document.querySelector('.kb-content-meta');
    var areaEl=document.getElementById('kbFileArea');
    if(metaEl)metaEl.innerHTML=countInfo;
    if(areaEl)areaEl.innerHTML=fileHtml;
    refreshIcons();
  }

  /* --- 刷新文件夹和文件 --- */
  function refreshData(callback){
    KB.searchResults=null;KB.searching=false;
    kbFetchFolders().then(function(folders){
      KB.folders=folders;
      return kbFetchFiles(KB.current);
    }).then(function(files){
      KB.files=files;
      render();
      if(callback)callback();
    }).catch(function(err){
      showToast('刷新失败: '+err.message);
    });
  }

  /* --- 绑定事件 --- */
  function attachEvents(){
    /* 树节点点击委托 */
    var pageEl=document.getElementById('kb-page');
    if(!pageEl)return;

    /* 使用全局点击委托处理知识树交互 */
    /* 知识树点击处理在 document click 中完成 */

    /* 视图切换 */
    var gridBtn=document.getElementById('kbGridBtn');
    var listBtn=document.getElementById('kbListBtn');
    if(gridBtn)gridBtn.onclick=function(){KB.view='grid';if(listBtn)listBtn.classList.remove('active');this.classList.add('active');render();};
    if(listBtn)listBtn.onclick=function(){KB.view='list';if(gridBtn)gridBtn.classList.remove('active');this.classList.add('active');render();};
    /* 排序 */
    var sortBtn=document.getElementById('kbSortBtn');
    if(sortBtn)sortBtn.onclick=function(){KB.ascending=!KB.ascending;render();};
    /* 搜索（带 200ms 防抖，仅替换文件列表区域，不触发全量 render） */
    var searchInput=document.getElementById('kbSearchInput');
    if(searchInput)searchInput.oninput=function(){
      KB.keyword=this.value;KB.selectedCard=null;
      if(window._kbSearchTimer)clearTimeout(window._kbSearchTimer);
      if(KB.keyword.trim()){
        window._kbSearchTimer=setTimeout(function(){
          kbSearch(KB.keyword.trim()).then(function(result){
            KB.searchResults=result;KB.searching=false;
            kbUpdateFileArea();
          }).catch(function(err){
            KB.searching=false;showToast('搜索失败: '+err.message);
          });
        },200);
      }else{
        KB.searchResults=null;KB.searching=false;
        kbUpdateFileArea();
      }
    };
    /* 新建文件夹按钮 */
    var nfb=document.getElementById('kbNewFolderBtn');
    if(nfb)nfb.onclick=function(){KB.newFolderName='';KB.newFolderOpen=true;render();};
    var nfb2=document.getElementById('kbNewFolderBtn2');
    if(nfb2)nfb2.onclick=function(){var dd=document.getElementById('kbNewDropdown');if(dd)dd.classList.remove('open');KB.newFolderName='';KB.newFolderOpen=true;render();};
    /* 上传按钮 */
    var upb=document.getElementById('kbNewUploadBtn');
    if(upb)upb.onclick=function(){KB.uploadOpen=true;render();};
    var upb2=document.getElementById('kbNewUploadBtn2');
    if(upb2)upb2.onclick=function(){var dd=document.getElementById('kbNewDropdown');if(dd)dd.classList.remove('open');KB.uploadOpen=true;render();};
    /* 新建下拉切换 */
    var nddBtn=document.getElementById('kbNewDropdownBtn');
    if(nddBtn)nddBtn.onclick=function(){var dd=document.getElementById('kbNewDropdown');if(dd)dd.classList.toggle('open');};
    /* 全部折叠 */
    var colBtn=document.getElementById('kbCollapseAllBtn');
    if(colBtn)colBtn.onclick=function(){var s=new Set();for(var i=0;i<KB.folders.length;i++){var c=false;for(var j=0;j<KB.folders.length;j++){if(KB.folders[j].parentId===KB.folders[i].id){c=true;break;}}if(c)s.add(KB.folders[i].id);}KB.collapsed=s;render();};
    /* 刷新按钮 */
    var refBtn=document.getElementById('kbRefreshBtn');
    if(refBtn)refBtn.onclick=function(){this.classList.add('kb-spin');var self=this;refreshData(function(){setTimeout(function(){self.classList.remove('kb-spin');},200);});showToast('正在刷新…');};
    /* 新建文件夹确认 */
    var cf=document.getElementById('kbCreateFolderConfirm');
    if(cf)cf.onclick=function(){
      var inp=document.getElementById('kbNewFolderInput');
      if(inp&&inp.value.trim()){
        var name=inp.value.trim();KB.newFolderOpen=false;render();
        kbCreateFolder(KB.current,name).then(function(){showToast('文件夹「'+name+'」已创建');refreshData();}).catch(function(e){showToast(e.message);});
      }else{showToast('请输入文件夹名称');}
    };
    /* 重命名确认 */
    var rf=document.getElementById('kbRenameConfirm');
    if(rf)rf.onclick=function(){
      var inp=document.getElementById('kbRenameInput');
      if(inp&&inp.value.trim()&&KB.renameTarget){
        var name=inp.value.trim();var id=KB.renameTarget.id;KB.renameTarget=null;render();
        kbRenameFile(id,name).then(function(){showToast('已重命名为「'+name+'」');refreshData();}).catch(function(e){showToast(e.message);});
      }else{showToast('请输入文件名称');}
    };
    /* 移动确认 */
    var mf=document.getElementById('kbMoveConfirm');
    if(mf)mf.onclick=function(){
      if(KB.moveTarget){
        var id=KB.moveTarget.id;var fid=KB.moveFolderId==='root'?null:KB.moveFolderId;KB.moveTarget=null;render();
        kbMoveFile(id,fid).then(function(){showToast('文件已移动');refreshData();}).catch(function(e){showToast(e.message);});
      }
    };
    /* 右键菜单：移动到 */
    var cm=document.getElementById('kbCtxMove');
    if(cm)cm.onclick=function(){if(KB.contextFile){KB.moveTarget=KB.contextFile;KB.moveFolderId=KB.contextFile.folderId||'root';KB.contextOpen=false;render();}};
    /* 右键菜单：重命名 */
    var cr=document.getElementById('kbCtxRename');
    if(cr)cr.onclick=function(){if(KB.contextFile){KB.renameTarget=KB.contextFile;KB.renameValue=KB.contextFile.name;KB.contextOpen=false;render();}};
    /* 右键菜单：删除 */
    var cd=document.getElementById('kbCtxDelete');
    if(cd)cd.onclick=async function(){
      /* 先关闭右键菜单，再弹出确认 */
      KB.contextOpen=false;render();
      if(KB.contextFile){
        var msg='确定删除「'+KB.contextFile.name+'」吗？文件将从本地磁盘中移除，此操作不可恢复。';
        var confirmed=await kbShowConfirm(msg,true);
        if(confirmed){
          var id=KB.contextFile.id;var name=KB.contextFile.name;KB.contextFile=null;KB.contextFolder=null;render();
          kbDeleteFile(id).then(function(){showToast('「'+name+'」已删除');refreshData();}).catch(function(e){showToast(e.message);});
        }
      }else if(KB.contextFolder){
        var msg='确定删除文件夹「'+KB.contextFolder.name+'」及其所有内容吗？文件夹内的文件和子文件夹都将被删除，此操作不可恢复。';
        var confirmed=await kbShowConfirm(msg,true);
        if(confirmed){
          var id=KB.contextFolder.id;var name=KB.contextFolder.name;KB.contextFile=null;KB.contextFolder=null;render();
          kbDeleteFolder(id).then(function(){showToast('文件夹「'+name+'」已删除');refreshData();}).catch(function(e){showToast(e.message);});
        }
      }
    };
    /* 上传 */
    var upBtn=document.getElementById('kbUploadBtn');
    var upInput=document.getElementById('kbFileInput');
    if(upBtn&&upInput)upBtn.onclick=function(){upInput.click();};
    if(upInput)upInput.onchange=function(){
      if(upInput.files&&upInput.files.length>0){
        var files=upInput.files;var total=files.length;var done=0;
        KB.uploadOpen=false;render();
        showToast('正在上传 '+total+' 个文件…');
        for(var i=0;i<total;i++){
          (function(file){
            kbUploadXhr(KB.current,file,function(pct){}).then(function(){done++;if(done===total){showToast('已上传 '+total+' 个文件');refreshData();}}).catch(function(e){done++;showToast(file.name+' 上传失败: '+e.message);if(done===total)refreshData();});
          })(files[i]);
        }
      }
    };
    /* 移动选择框 */
    var ms=document.getElementById('kbMoveSelect');
    if(ms)ms.onchange=function(){KB.moveFolderId=this.value;};
    /* 弹窗回车 */
    var ni=document.getElementById('kbNewFolderInput');
    if(ni)ni.onkeydown=function(e){if(e.key==='Enter'){var btn=document.getElementById('kbCreateFolderConfirm');if(btn)btn.click();}};
    var ri=document.getElementById('kbRenameInput');
    if(ri)ri.onkeydown=function(e){if(e.key==='Enter'){var btn=document.getElementById('kbRenameConfirm');if(btn)btn.click();}};
    /* 点击文件区域空白取消选中 */
    var fa=document.getElementById('kbFileArea');
    if(fa)fa.onclick=function(e){if(!e.target.closest('.kb-file-card')&&!e.target.closest('.kb-context-menu')){KB.selectedCard=null;render();}};
    /* 关闭预览 */
    var pc=document.getElementById('kbPreviewCloseBtn');
    if(pc)pc.onclick=function(){KB.previewFile=null;KB.previewContent=null;KB.previewBase64=null;KB.previewKind=null;KB.previewLoading=false;render();};
    window._kbPreviewClose=function(){KB.previewFile=null;KB.previewContent=null;KB.previewBase64=null;KB.previewKind=null;KB.previewLoading=false;render();};
    /* 预览面板拖拽调整宽度 */
    var rh=document.getElementById('kbPreviewResize');
    if(rh)rh.onmousedown=function(e){
      e.preventDefault();
      var pane=document.getElementById('kbPreviewPane');
      var startX=e.clientX;
      var startW=pane.offsetWidth;
      function onMove(ev){
        var w=startW-(ev.clientX-startX);
        if(w<280)w=280;if(w>800)w=800;
        pane.style.width=w+'px';
        pane.style.flex='none';
      }
      function onUp(){document.removeEventListener('mousemove',onMove);document.removeEventListener('mouseup',onUp);document.body.style.cursor='';document.body.style.userSelect='';}
      document.addEventListener('mousemove',onMove);
      document.addEventListener('mouseup',onUp);
      document.body.style.cursor='col-resize';
      document.body.style.userSelect='none';
    };
  }

  /* --- 全局事件委托（一次性注册） --- */
  if(!window._kbEventsRegistered){
    window._kbEventsRegistered=true;
    document.addEventListener('click',function(e){
      if(!document.getElementById('kb-page'))return;
      /* 知识树折叠/展开 */
      var chev=e.target.closest('[data-chev]');
      if(chev&&document.getElementById('kb-page')){e.stopPropagation();var id=chev.dataset.chev;KB.collapsed.has(id)?KB.collapsed.delete(id):KB.collapsed.add(id);render();return;}
      /* 知识树选择文件夹 + 折叠/展开 */
      var fc=e.target.closest('[data-folder-click]');
      if(fc&&document.getElementById('kb-page')){e.stopPropagation();var id=fc.dataset.folderClick;KB.current=id;KB.selectedCard=null;KB.searchResults=null;KB.searching=false;KB.keyword='';var hasChild=false;for(var i=0;i<KB.folders.length;i++){if((KB.folders[i].parentId||null)===(id||null)){hasChild=true;break;}}if(hasChild){KB.collapsed.has(id)?KB.collapsed.delete(id):KB.collapsed.add(id);}render();var si=document.getElementById('kbSearchInput');if(si)si.value='';kbFetchFiles(id).then(function(files){KB.files=files;render();}).catch(function(err){showToast('加载文件失败: '+err.message);});return;}
      /* 我的知识库根节点 */
      var rb=e.target.closest('.kb-root-btn');
      if(rb&&document.getElementById('kb-page')){e.stopPropagation();KB.current=null;KB.selectedCard=null;KB.searchResults=null;KB.searching=false;KB.keyword='';render();var si=document.getElementById('kbSearchInput');if(si)si.value='';kbFetchFiles(null).then(function(files){KB.files=files;render();}).catch(function(err){showToast('加载失败: '+err.message);});return;}
      /* 卡片选中（仅用于高亮，不阻止后续操作） */
      var card=e.target.closest('[data-card]');
      if(card&&document.getElementById('kb-page')&&!e.target.closest('.kb-context-menu')){KB.selectedCard=card.dataset.card;render();}
      /* 文件夹卡片单击 → 进入文件夹 + 展开树路径 */
      var dbl=e.target.closest('[data-dbl-folder]');
      if(dbl&&document.getElementById('kb-page')){e.preventDefault();var fid=dbl.dataset.dblFolder;KB.current=fid;KB.selectedCard=null;KB.searchResults=null;KB.searching=false;KB.keyword='';kbExpandPath(KB.folders,fid,KB.collapsed);render();var si=document.getElementById('kbSearchInput');if(si)si.value='';kbFetchFiles(fid).then(function(files){KB.files=files;render();}).catch(function(err){showToast('加载失败: '+err.message);});return;}
      /* 文件卡片单击 → 预览文件 */
      var fileEl=e.target.closest('[data-file]');
      if(fileEl&&document.getElementById('kb-page')&&!e.target.closest('.kb-context-menu')){e.preventDefault();var fid=fileEl.dataset.file;var found=null;for(var i=0;i<KB.files.length;i++){if(KB.files[i].id===fid){found=KB.files[i];break;}}if(!found&&KB.searchResults){for(var i=0;i<KB.searchResults.files.length;i++){if(KB.searchResults.files[i].id===fid){found=KB.searchResults.files[i];break;}}}if(found){KB.previewFile=found;KB.previewContent=null;KB.previewBase64=null;KB.previewKind=null;KB.previewLoading=true;render();kbPreview(fid).then(function(r){KB.previewContent=r.content;KB.previewBase64=r.contentBase64;KB.previewKind=r.kind;KB.previewLoading=false;render();}).catch(function(e){KB.previewLoading=false;showToast('预览失败');});}return;}
      /* 关闭右键菜单 */
      if(!e.target.closest('.kb-context-menu')&&KB.contextOpen){KB.contextOpen=false;render();}
      /* 关闭弹窗 */
      if(e.target.closest('[data-close-kb-modal]')){KB.newFolderOpen=false;KB.renameTarget=null;KB.moveTarget=null;KB.uploadOpen=false;KB.contextOpen=false;KB.contextFolder=null;KB.contextFile=null;render();}
      /* 确认弹窗点击遮罩关闭 */
      var confirmLayer=document.getElementById('kbConfirmModal');
      if(confirmLayer&&confirmLayer.classList.contains('open')&&e.target===confirmLayer){
        confirmLayer.classList.remove('open');
        if(window._kbConfirmResolve){window._kbConfirmResolve(false);window._kbConfirmResolve=null;}
      }
      /* 关闭新建下拉 */
      if(!e.target.closest('#kbNewDropdownWrap')){var dd=document.getElementById('kbNewDropdown');if(dd)dd.classList.remove('open');}
      /* 面包屑导航点击回退 */
      var crumb=e.target.closest('[data-crumb]');
      if(crumb&&document.getElementById('kb-page')){e.preventDefault();var cid=crumb.dataset.crumb==='root'?null:crumb.dataset.crumb;KB.current=cid;KB.selectedCard=null;KB.searchResults=null;KB.searching=false;KB.keyword='';if(cid)kbExpandPath(KB.folders,cid,KB.collapsed);render();var si=document.getElementById('kbSearchInput');if(si)si.value='';kbFetchFiles(cid).then(function(files){KB.files=files;render();}).catch(function(err){showToast('加载失败: '+err.message);});return;}
    });
    document.addEventListener('dblclick',function(e){
      if(!document.getElementById('kb-page'))return;
      var dbl=e.target.closest('[data-dbl-folder]');
      if(dbl&&document.getElementById('kb-page')){var fid=dbl.dataset.dblFolder;KB.current=fid;KB.selectedCard=null;KB.searchResults=null;KB.searching=false;KB.keyword='';kbExpandPath(KB.folders,fid,KB.collapsed);render();var si=document.getElementById('kbSearchInput');if(si)si.value='';kbFetchFiles(fid).then(function(files){KB.files=files;render();}).catch(function(err){showToast('加载失败: '+err.message);});}
    });
    document.addEventListener('contextmenu',function(e){
      if(!document.getElementById('kb-page'))return;
      var fileEl=e.target.closest('[data-file]');
      if(fileEl&&document.getElementById('kb-page')){
        e.preventDefault();
        var fileId=fileEl.dataset.file;
        var found=null;for(var i=0;i<KB.files.length;i++){if(KB.files[i].id===fileId){found=KB.files[i];break;}}
        if(found){KB.contextFile=found;KB.contextFolder=null;KB.contextX=e.clientX;KB.contextY=e.clientY;KB.contextOpen=true;render();}return;
      }
      var folderEl=e.target.closest('[data-dbl-folder]');
      if(folderEl&&document.getElementById('kb-page')){
        e.preventDefault();
        var folderId=folderEl.dataset.dblFolder;
        var found=null;for(var i=0;i<KB.folders.length;i++){if(KB.folders[i].id===folderId){found=KB.folders[i];break;}}
        if(found){KB.contextFolder=found;KB.contextFile=null;KB.contextX=e.clientX;KB.contextY=e.clientY;KB.contextOpen=true;render();}
      }
    });
  }

  /* --- 启动 --- */
  KB.loading=true;
  var loadingShell='<section class="page kb-page" id="kb-page">'+''+'<div class="empty" style="min-height:400px">正在加载知识库…</div></section>';
  shell(loadingShell);
  loadData();
}

const renderers = { discover: renderDiscover, calendar: renderCalendar, watchlist: renderWatchlist, reports: renderReports, sources: renderSources, views: renderViews, tasks: renderTasks, settings: renderSettings, knowledge: renderKnowledge };
(renderers[page] || renderDiscover)();

function refreshIcons() { if (window.lucide) window.lucide.createIcons(); }
refreshIcons();

let toastTimer;
function showToast(message) {
  const toast = document.getElementById('toast');
  document.getElementById('toastText').textContent = message;
  toast.classList.add('show');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('show'), 2400);
}

document.addEventListener('click', event => {
  const toastTarget = event.target.closest('[data-toast]');
  if (toastTarget) showToast(toastTarget.dataset.toast);

  const refreshLive = event.target.closest('[data-refresh-live]');
  if (refreshLive) {
    event.preventDefault();
    refreshLive.disabled = true;
    showToast('正在真实采集信源…');
    refreshLiveSources().catch(error => {
      console.warn('SubtleSight live collect failed', error);
      showToast('真实采集失败，请看后端日志');
    }).finally(() => { refreshLive.disabled = false; });
  }

  const refreshCalendar = event.target.closest('[data-refresh-calendar]');
  if (refreshCalendar) {
    event.preventDefault();
    refreshFinancialCalendar(refreshCalendar);
  }

  const calendarEvent = event.target.closest('[data-calendar-event]');
  if (calendarEvent) {
    event.preventDefault();
    openCalendarEvidence(calendarEvent.dataset.calendarEvent);
  }

  if (event.target.closest('#closeCalendarEvidence') || event.target.id === 'calendarEvidenceOverlay') {
    document.getElementById('calendarEvidenceOverlay')?.classList.remove('open');
  }

  const tab = event.target.closest('.tab');
  if (tab) { tab.parentElement.querySelectorAll('.tab').forEach(x => x.classList.remove('active')); tab.classList.add('active'); showToast(`已切换到${tab.childNodes[0].textContent.trim()}`); }

  const channel = event.target.closest('.channel-option');
  if (channel) {
    channel.parentElement.querySelectorAll('.channel-option').forEach(x => x.classList.remove('active'));
    channel.classList.add('active');
    const scope = discoveryScopes[channel.dataset.channel];
    document.getElementById('platformOptions').innerHTML = platformOptionsHtml(scope);
    document.getElementById('selectedTopics').innerHTML = selectedTopicsHtml(scope.topics);
    document.getElementById('scopeResultContext').textContent = scope.label;
    document.getElementById('scopeResultCount').textContent = '0';
    document.getElementById('topicModalTitle').textContent = `筛选${scope.label}主题`;
    document.getElementById('topicModalChannel').textContent = scope.label;
    document.getElementById('topicModalOptions').innerHTML = topicOptionsHtml(scope);
    document.getElementById('topicModalCount').textContent = `已选 ${scope.topics.length} 项`;
    renderDomainRanking(channel.dataset.channel);
    renderDiscoveryList(channel.dataset.channel);
    refreshIcons();
    showToast(`已切换到${scope.label}`);
  }

  const platform = event.target.closest('.platform-option');
  if (platform) {
    platform.parentElement.querySelectorAll('.platform-option').forEach(x => x.classList.remove('active'));
    platform.classList.add('active');
    const channelKey = document.querySelector('.channel-option.active').dataset.channel;
    renderDomainRanking(channelKey, platform.dataset.platform);
    renderDiscoveryList(channelKey, platform.dataset.platform);
    showToast(`平台范围：${platform.dataset.platform}`);
  }

  const rankingMode = event.target.closest('.ranking-mode');
  if (rankingMode) {
    rankingMode.parentElement.querySelectorAll('.ranking-mode').forEach(x => x.classList.remove('active'));
    rankingMode.classList.add('active');
    showToast(`热榜排序：${rankingMode.textContent}`);
  }

  const topicRemove = event.target.closest('.topic-remove');
  if (topicRemove) {
    const topic = topicRemove.closest('.selected-topic');
    showToast(`已移除主题：${topic.querySelector('span').textContent}`);
    topic.remove();
  }

  const chip = event.target.closest('.filter-chip');
  if (chip) { chip.parentElement.querySelectorAll('.filter-chip').forEach(x => x.classList.remove('active')); chip.classList.add('active'); }

  const segment = event.target.closest('.segmented button');
  if (segment) { segment.parentElement.querySelectorAll('button').forEach(x => x.classList.remove('active')); segment.classList.add('active'); }

  const toggle = event.target.closest('.toggle');
  if (toggle) { toggle.classList.toggle('on'); toggle.setAttribute('aria-checked', toggle.classList.contains('on')); }

  const openModal = event.target.closest('[data-open-modal]');
  if (openModal) document.getElementById(openModal.dataset.openModal).classList.add('open');
  if (event.target.closest('[data-close-modal]')) document.querySelectorAll('.modal-layer').forEach(x => x.classList.remove('open'));
  if (event.target.closest('[data-save-source]')) { document.getElementById('sourceModal').classList.remove('open'); showToast('信源检查通过并已添加'); }
  if (event.target.matches('#topicModalOptions input')) {
    const selectedCount = document.querySelectorAll('#topicModalOptions input:checked').length;
    document.getElementById('topicModalCount').textContent = `已选 ${selectedCount} 项`;
  }
  if (event.target.closest('[data-apply-topics]')) {
    const topics = [...document.querySelectorAll('#topicModalOptions .check-row')]
      .filter(row => row.querySelector('input').checked)
      .map(row => row.textContent.trim())
      .slice(0, 6);
    document.getElementById('selectedTopics').innerHTML = selectedTopicsHtml(topics);
    document.getElementById('topicModal').classList.remove('open');
    refreshIcons();
    showToast(`已应用 ${topics.length} 个关注主题`);
  }

  const task = event.target.closest('.task-toggle');
  if (task) showToast(task.title === '暂停' ? '任务已暂停并保存检查点' : '已打开任务操作');

  if (event.target.closest('.settings-link')) { document.querySelectorAll('.settings-link').forEach(x => x.classList.remove('active')); event.target.closest('.settings-link').classList.add('active'); }
});

document.getElementById('menuButton')?.addEventListener('click', () => document.getElementById('sidebar').classList.toggle('open'));
document.querySelectorAll('.toggle').forEach(toggle => toggle.addEventListener('keydown', event => {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    toggle.click();
  }
}));
document.addEventListener('keydown', event => { if (event.key === 'Escape') { document.getElementById('calendarEvidenceOverlay')?.classList.remove('open'); document.querySelectorAll('.modal-layer').forEach(x => x.classList.remove('open')); document.getElementById('sidebar')?.classList.remove('open'); if(window._kbConfirmResolve){window._kbConfirmResolve(false);window._kbConfirmResolve=null;} if(window._kbPreviewClose)window._kbPreviewClose(); } });
document.addEventListener('change', event => { if (event.target.matches('[data-calendar-filter]')) hydrateCalendarData(); });

const previewState = new URLSearchParams(window.location.search);
if (previewState.get('modal') === 'source') document.getElementById('sourceModal')?.classList.add('open');
if (previewState.get('modal') === 'topic' || previewState.get('modal') === 'filter') document.getElementById('topicModal')?.classList.add('open');
if (previewState.get('channel')) document.querySelector(`[data-channel="${previewState.get('channel')}"]`)?.click();
if (previewState.get('platform')) document.querySelector(`[data-platform="${previewState.get('platform')}"]`)?.click();

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]));
}

function setLiveBadge(text, ok = true) {
  const badge = document.querySelector('.sample-label');
  if (badge) badge.textContent = text;
  const status = document.querySelector('.sidebar-footer .status-row:first-child span:last-child');
  if (status) status.textContent = ok ? 'API 已联通' : '使用本地原型数据';
}

function applySummary(summary = {}) {
  const map = {
    '持续信源': summary.sources,
    '跟踪目标': summary.watchlists,
    '待确认提醒': summary.jobs,
    '运行中': summary.jobs,
    '等待': summary.jobs,
    '今日完成': summary.jobs,
    '本周期变化': summary.watchlists
  };
  document.querySelectorAll('.stat').forEach(stat => {
    const label = stat.querySelector('.stat-label')?.textContent?.trim();
    const value = stat.querySelector('.stat-value');
    if (label && value && map[label] !== undefined) value.childNodes[0].textContent = String(map[label]);
  });
}

function sourceHealthClass(health) {
  if (health === 'DOWN' || health === 'DISABLED') return 'error';
  if (health === 'DEGRADED') return 'warning';
  return '';
}

function hydrateSources(sources = []) {
  if (page !== 'sources' || !sources.length) return;
  const tbody = document.querySelector('.data-table tbody');
  if (!tbody) return;
  tbody.innerHTML = sources.map(source => {
    const endpoint = escapeHtml(source.endpoint || '');
    let domain = 'example.com';
    try { domain = new URL(endpoint).hostname; } catch {}
    const healthLabel = ({ HEALTHY: '健康', DEGRADED: '限流', DOWN: '错误', DISABLED: '停用' })[source.health] || source.health;
    return `<tr><td><div class="flex items-center gap-6"><img class="source-logo" src="${favicon(domain)}" width="17" height="17" alt=""><div><div class="cell-title">${escapeHtml(source.name)}</div><div class="cell-sub">${escapeHtml(domain)}</div></div></div></td><td>${escapeHtml(source.type)}</td><td><div class="health"><span class="health-dot ${sourceHealthClass(source.health)}"></span>${escapeHtml(healthLabel)}</div></td><td>${source.enabled ? '已启用' : '已停用'}</td><td><span class="tag">${escapeHtml(source.tier)}</span></td><td class="muted">${escapeHtml(source.schedule || '手动')}</td><td><button class="icon-button" aria-label="配置" title="配置">${icon('settings')}</button></td></tr>`;
  }).join('');
  refreshIcons();
}

function jobProgress(job) {
  if (job.status === 'COMPLETED') return '100%';
  if (job.status === 'RUNNING') return '68%';
  if (job.status === 'FAILED' || job.status === 'CANCELLED') return '100%';
  return '0%';
}

function hydrateTasks(jobs = []) {
  if (page !== 'tasks' || !jobs.length) return;
  const tbody = document.querySelector('.data-table tbody');
  if (!tbody) return;
  tbody.innerHTML = jobs.slice(0, 30).map(job => {
    const statusMap = { RUNNING: '运行中', COMPLETED: '已完成', RETRY_WAIT: '等待重试', FAILED: '错误', CANCELLED: '已取消', QUEUED: '等待', PAUSED: '暂停' };
    const state = statusMap[job.status] || job.status;
    const pct = jobProgress(job);
    const tag = state === '运行中' ? 'orange' : state === '已完成' ? 'green' : state === '错误' || state === '等待重试' ? 'red' : 'amber';
    return `<tr><td><div class="cell-title">${escapeHtml(job.type)} · ${escapeHtml(job.dedupKey || job.id)}</div><div class="cell-sub">${escapeHtml(job.id)}</div></td><td>${escapeHtml(job.type)}</td><td><span class="tag ${tag}">${escapeHtml(state)}</span></td><td><div class="flex items-center gap-6"><div class="change-bar"><span style="width:${pct}"></span></div><span class="num">${pct}</span></div></td><td class="num">${escapeHtml(job.attempt ?? 0)} 次</td><td>${job.priority > 5 ? '高' : '普通'}</td><td><button class="icon-button task-toggle" aria-label="更多" title="更多">${icon('more-horizontal')}</button></td></tr>`;
  }).join('');
  refreshIcons();
}

function hydrateReports(reports = []) {
  if (page !== 'reports' || !reports.length) return;
  const list = document.querySelector('.report-list');
  const preview = document.querySelector('.report-preview');
  if (!list || !preview) return;
  list.innerHTML = reports.slice(0, 20).map((report, index) => `<div class="report-list-item ${index === 0 ? 'active' : ''}"><h3>${escapeHtml(report.title)}</h3><p>${escapeHtml(report.reportType)} · v${escapeHtml(report.version)} · ${report.citationsVerified ? '引用通过' : '待核验'}</p></div>`).join('');
  const first = reports[0];
  preview.innerHTML = `<div class="story-meta"><span class="tag ${first.citationsVerified ? 'green' : 'amber'}">${first.citationsVerified ? '引用已通过' : '待核验'}</span><span class="meta">版本 ${escapeHtml(first.version)} · Live</span></div><h2 class="report-document-title">${escapeHtml(first.title)}</h2><div>${first.html || `<p>${escapeHtml(first.markdown || '暂无报告正文')}</p>`}</div>`;
}

function hydrateKnowledge(data) {
  if (page !== 'knowledge') return;
  const stories = data.stories || [];
  const documents = data.documents || [];
  const reports = data.reports || [];
  const items = [
    ...stories.map(story => ({ type: 'Story', title: story.title, sub: story.summary })),
    ...documents.map(document => ({ type: '文档', title: document.title, sub: document.summary || document.canonicalUrl })),
    ...reports.map(report => ({ type: '报告', title: report.title, sub: `${report.reportType} · v${report.version}` }))
  ].slice(0, 20);
  if (!items.length) return;
  const results = document.querySelector('.knowledge-results');
  const detail = document.querySelector('.knowledge-detail');
  if (!results || !detail) return;
  results.innerHTML = items.map((item, index) => `<article class="knowledge-item ${index === 0 ? 'active' : ''}"><span class="tag ${item.type === 'Story' ? 'blue' : item.type === '报告' ? 'orange' : ''}">${escapeHtml(item.type)}</span><h3>${escapeHtml(item.title)}</h3><p>${escapeHtml(item.sub || '来自本地知识库')}</p></article>`).join('');
  detail.innerHTML = `<span class="tag orange">Live</span><h2 class="detail-title">${escapeHtml(items[0].title)}</h2><p class="detail-prose">${escapeHtml(items[0].sub || '已从 SubtleSight 后端聚合接口读取。')}</p><div class="metric-line"><span>来源</span><span class="metric-value">本地库</span></div><div class="metric-line"><span>内容类型</span><span class="metric-value">${escapeHtml(items[0].type)}</span></div>`;
}

function hydrateDiscover(feed = []) {
  if (page !== 'discover') return;
  liveFeedItems = feed.slice(0, 100).map(normalizeLiveFeedItem);
  updateLiveChannelCounts();
  const activePlatform = document.querySelector('.platform-option.active')?.dataset.platform || '全平台';
  const activeChannel = document.querySelector('.channel-option.active')?.dataset.channel || 'tech';
  renderDiscoveryList(activeChannel, activePlatform);
}

function scorePercent(score) {
  const value = Number(score || 0);
  return value <= 1 ? Math.round(value * 100) : Math.round(value);
}

function hydrateTrends(trends = []) {
  if (page !== 'discover') return;
  liveTrendItems = trends;
  const activePlatform = document.querySelector('.platform-option.active')?.dataset.platform || '全平台';
  const activeChannel = document.querySelector('.channel-option.active')?.dataset.channel || 'tech';
  renderLiveRanking(activePlatform, activeChannel);
}

function normalizeLiveFeedItem(item) {
  const score = scorePercent(item.signal?.score);
  const platform = item.platform || sourceTypePlatform(item.sourceType);
  const domain = item.domain || item.timeline?.[0]?.sourceFamily || 'local';
  const source = item.sourceName || item.source || platform || domain;
  const mediaTags = item.mediaUrl ? [['可播放', 'green'], ['已下载', 'orange']] : [];
  return {
    source,
    sourceName: source,
    sourceType: item.sourceType || 'UNKNOWN',
    platform,
    domain,
    mediaUrl: item.mediaUrl || '',
    mediaType: item.mediaType || '',
    blobHash: item.blobHash || '',
    downloaded: Boolean(item.downloaded || item.mediaUrl),
    title: item.story?.title || '本地 Story',
    summary: item.story?.summary || '已从真实信源采集入库。',
    tags: [['Live', 'green'], ...mediaTags, [`score ${score}`, 'orange'], [platform || '真实信源', 'blue']],
    score,
    reason: (item.signal?.reasonCodes || ['本地信号']).join(' / '),
    trend: freshnessTrend(item.updatedAt || item.observedAt || item.story?.firstObservedAt),
    depth: `${item.timeline?.length || 0} 条时间线`,
    intel: liveIntel(item, source, platform, domain)
  };
}

function sourceTypePlatform(sourceType = '') {
  return ({ GITHUB: 'GitHub', HN: 'Hacker News', ARXIV: 'arXiv', HUGGING_FACE: 'Hugging Face', PRODUCT_HUNT: 'Product Hunt', VIDEO: 'Internet Archive' })[sourceType] || 'RSS';
}

function liveIntel(item, source, platform, domain) {
  const collected = item.updatedAt || item.observedAt || item.story?.firstObservedAt;
  const minutes = minutesSince(collected);
  const age = minutes == null ? '真实入库' : minutes < 2 ? '刚刚入库' : minutes < 60 ? `${minutes} 分钟前入库` : minutes < 1440 ? `${Math.round(minutes / 60)} 小时前入库` : `${Math.round(minutes / 1440)} 天前入库`;
  const timeline = item.timeline?.length || 1;
  return {
    priority: Number(item.signal?.score || 0) >= 0.65 ? '高优先' : '值得看',
    tone: Number(item.signal?.score || 0) >= 0.65 ? 'high' : 'medium',
    age,
    coverage: `${timeline} 个来源链路`,
    status: platform ? `${platform} 真实入库` : '真实入库',
    sources: [domain],
    impact: item.reason || '',
    action: `来自 ${source}`
  };
}

function minutesSince(value) {
  if (!value) return null;
  const time = Date.parse(value);
  if (Number.isNaN(time)) return null;
  return Math.max(0, Math.round((Date.now() - time) / 60000));
}

function freshnessTrend(value) {
  const minutes = minutesSince(value);
  if (minutes == null) return '+0%';
  if (minutes < 60) return '+12%';
  if (minutes < 360) return '+8%';
  if (minutes < 1440) return '+4%';
  return '+0%';
}

function getCookie(name) {
  return document.cookie.split(';').map(part => part.trim()).find(part => part.startsWith(`${name}=`))?.slice(name.length + 1) || '';
}

async function refreshLiveSources() {
  if (!getCookie('XSRF-TOKEN')) await fetch('/api/v1/auth/status', { credentials: 'same-origin' });
  const token = decodeURIComponent(getCookie('XSRF-TOKEN'));
  const response = await fetch('/api/v1/sources/collect-real?maxSources=8&maxItems=3', {
    method: 'POST',
    credentials: 'same-origin',
    headers: token ? { 'X-XSRF-TOKEN': token } : {}
  });
  if (!response.ok) throw new Error(`collect ${response.status}`);
  const report = await response.json();
  await hydrateLiveData();
  showToast(`真实采集完成：新增 ${report.totalIngested || 0} 条`);
}

async function hydrateLiveData() {
  try {
    const response = await fetch('/api/v1/workspace/overview', { credentials: 'same-origin' });
    if (!response.ok) throw new Error(`overview ${response.status}`);
    const data = await response.json();
    setLiveBadge('Live · 本机数据', true);
    applySummary(data.summary || {});
    hydrateSources(data.sources || []);
    hydrateTasks(data.jobs || []);
    hydrateReports(data.reports || []);
    hydrateDiscover(data.feed || []);
    hydrateTrends(data.trends || []);
  } catch (error) {
    console.warn('SubtleSight live hydrate failed', error);
    setLiveBadge('原型 · 离线数据', false);
  }
}

hydrateLiveData();
if (page === 'calendar') hydrateCalendarData();
