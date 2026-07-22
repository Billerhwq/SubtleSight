const page = document.documentElement.dataset.page || 'discover';

const navGroups = [
  { label: '工作区', items: [
    ['discover', 'discover.html', 'radar', '发现'],
    ['research', 'research.html', 'search-check', '深度研究', '1'],
    ['watchlist', 'watchlist.html', 'eye', 'Watchlist', '4'],
    ['knowledge', 'knowledge.html', 'library', '知识库']
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
const tagsHtml = tags => tags.map(([label, color]) => `<span class="tag ${color || ''}">${label}</span>`).join('');

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
    platforms: [{ label: '全平台', icon: 'globe-2' }, { label: 'B站', domain: 'bilibili.com' }, { label: '抖音', domain: 'douyin.com' }, { label: 'YouTube', domain: 'youtube.com' }, { label: '小红书', domain: 'xiaohongshu.com' }, { label: 'TikTok', domain: 'tiktok.com' }],
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

function platformOptionsHtml(scope) {
  return scope.platforms.map((platform, index) => `<button class="platform-option ${index === 0 ? 'active' : ''}" data-platform="${platform.label}">${platform.domain ? `<img src="${favicon(platform.domain)}" width="16" height="16" alt="">` : icon(platform.icon)}<span>${platform.label}</span></button>`).join('');
}

function selectedTopicsHtml(topics) {
  return topics.map(topic => `<span class="selected-topic"><span>${topic}</span><button class="topic-remove" aria-label="移除 ${topic}" title="移除">${icon('x')}</button></span>`).join('');
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

const detailUrl = item => `story.html?type=${detailTypeForItem(item)}`;

function sidebar() {
  return `
    <aside class="sidebar" id="sidebar">
      <a class="brand" href="discover.html">
        <span class="brand-mark">见</span><span class="brand-name">见微</span><span class="prototype-badge">原型</span>
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
      <label class="global-search">
        ${icon('search')}<input id="globalSearch" type="search" placeholder="搜索 Story、来源、实体或研究…" aria-label="全局搜索">
      </label>
      <span class="sample-label">原型 · 示例数据</span>
      <div class="topbar-spacer"></div>
      <button class="icon-button" data-toast="暂无新提醒" aria-label="提醒" title="提醒">${icon('bell')}</button>
      <button class="icon-button agent-trigger" id="agentButton" aria-label="打开 Agent" title="打开 Agent">${icon('sparkles')}</button>
      <span class="avatar" aria-label="当前用户">JW</span>
    </header>`;
}

function agentDrawer() {
  return `
    <div class="drawer-overlay" id="agentOverlay">
      <aside class="agent-drawer" aria-label="见微 Agent">
        <div class="drawer-header">
          ${icon('sparkles')}<span class="drawer-title">见微 Agent</span><span class="tag green">可用</span>
          <span class="topbar-spacer"></span>
          <button class="icon-button" id="closeAgent" aria-label="关闭" title="关闭">${icon('x')}</button>
        </div>
        <div class="drawer-body">
          <div class="agent-message user">比较最近收录的 Agent 记忆研究，并创建一个 Watchlist。</div>
          <div class="agent-message">
            已定位本地知识、论文来源和相关 Story。创建 Watchlist 前，我会先核对研究对象与监控条件。
          </div>
          <div class="tool-run">
            <div class="tool-step">${icon('check-circle-2')}检索本地知识库</div>
            <div class="tool-step">${icon('check-circle-2')}合并相关 Story</div>
            <div class="tool-step">${icon('loader-circle')}整理监控实体与关键词</div>
          </div>
          <div class="agent-message">建议跟踪：Agent Memory、Long-term Memory Benchmark、相关论文仓库。提醒条件设为“新论文、代码发布或结论冲突”。</div>
          <button class="btn primary" data-toast="Watchlist 已创建">${icon('eye')}创建 Watchlist</button>
        </div>
        <div class="drawer-input">
          <div class="composer">
            <textarea placeholder="向见微提问…" aria-label="向见微提问"></textarea>
            <div class="composer-actions"><button class="icon-button" aria-label="添加附件" title="添加附件">${icon('paperclip')}</button><button class="btn primary small" data-toast="消息已发送">发送</button></div>
          </div>
        </div>
      </aside>
    </div>`;
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
        <h2 class="signal-title"><a href="story.html">${item.title}</a></h2>
        <p class="signal-summary">${item.summary}</p>
        <div class="signal-tags">${tagsHtml(item.tags)}</div>
      </div>
      <div class="signal-score">
        <div class="score-card"><span class="score-value">${item.score}</span><div class="score-label">综合信号</div></div>
        <div class="signal-meta-stack"><span>${item.trend || '+0%'}</span><span>${item.depth || item.reason}</span></div>
        <div class="row-actions"><button class="icon-button save-action" aria-label="保存" title="保存">${icon('bookmark')}</button><button class="icon-button" data-toast="该信号将在后续发现中隐藏" aria-label="隐藏" title="隐藏">${icon('eye-off')}</button><button class="icon-button" aria-label="更多" title="更多">${icon('more-horizontal')}</button></div>
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
  const sourceStack = intel.sources.map(domain => `<img src="${favicon(domain)}" width="18" height="18" alt="">`).join('');
  const heat = (item.trend || '+0%').replace('+', '');
  return `
    <article class="intel-row ${index === 0 ? 'selected' : ''}">
      <label class="intel-check"><input type="checkbox" aria-label="选择 ${item.title}"></label>
      <div class="intel-priority ${intel.tone}">
        <span class="priority-label" title="根据热度、时效与来源质量综合判断">${intel.priority}</span>
        <span class="priority-trend">${intel.age}</span>
      </div>
      <div class="intel-main">
        <div class="intel-kicker">
          <img class="source-logo" src="${favicon(item.domain)}" width="17" height="17" alt="">
          <strong>${item.source}</strong><span>· ${intel.age}</span>
          <span class="meta-divider"></span>
          <span class="source-stack">${sourceStack}</span>
          <span>${intel.coverage}</span>
          <span class="verification ${intel.tone}">${intel.status}</span>
        </div>
        <h2><a href="${detailUrl(item)}">${item.title}</a></h2>
        <p>${item.summary}</p>
        <div class="intel-context">${tagsHtml(item.tags.filter(([label]) => !label.startsWith('多源')).slice(0, 2))}</div>
      </div>
      <div class="intel-meta">
        <span class="intel-meta-label">热度与状态</span>
        <strong>↑ ${heat}</strong>
        <span>${intel.coverage}</span>
        <span class="verification ${intel.tone}">${intel.status}</span>
      </div>
      <div class="intel-actions">
        <button class="icon-button save-action" aria-label="保存" title="保存">${icon('bookmark')}</button>
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
  const profile = rankingProfiles[channelKey];
  if (!profile || !document.getElementById('rankingList')) return;
  document.getElementById('rankingTitle').textContent = platform === '全平台' ? profile.title : `${platform} 热榜`;
  document.getElementById('rankingDescription').textContent = profile.description;
  document.getElementById('rankingModes').innerHTML = profile.modes.map((mode, index) => `<button class="ranking-mode ${index === 0 ? 'active' : ''}">${mode}</button>`).join('');
  document.getElementById('rankingList').innerHTML = rankingRowsHtml(channelKey, platform);
  refreshIcons();
}

function renderDiscoveryList(channelKey, platform = '全平台') {
  const list = document.querySelector('.intel-list');
  if (!list || !discoveryChannelSignals[channelKey]) return;
  const allItems = discoveryChannelSignals[channelKey];
  let items = platform === '全平台' ? allItems : allItems.filter(item => item.platform === platform);
  if (platform === 'GitHub') items = [...items].sort((a, b) => Number(b.intel?.judgmentLabel === '推荐理由') - Number(a.intel?.judgmentLabel === '推荐理由'));
  list.innerHTML = (items.length ? items : allItems).map(signalInboxRow).join('');
  document.getElementById('scopeResultCount').textContent = platform === '全平台' || !items.length ? discoveryScopes[channelKey].count : items.length;
  refreshIcons();
}

function renderDiscover() {
  const actions = `<button class="btn" data-toast="发现任务已刷新">${icon('refresh-cw')}刷新</button><a class="btn primary" href="research.html">${icon('search-check')}开始研究</a>`;
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
      ${Object.entries(discoveryScopes).map(([key, scope]) => `<button class="channel-option ${key === 'tech' ? 'active' : ''}" data-channel="${key}">${icon(scope.icon)}<span>${scope.label}</span><small>${scope.count}</small></button>`).join('')}
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
      <div class="result-summary"><strong>更多发现</strong><span><i id="scopeResultContext">技术与开源</i> · <b id="scopeResultCount">126</b> 条 · 过去 24 小时</span></div>
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
            <div class="intel-list">${inboxSignals.map(signalInboxRow).join('')}</div>
          </main>
        </div>
      </section>
      <aside class="domain-ranking" aria-labelledby="rankingTitle">
        <div class="ranking-head">
          <div class="ranking-title-wrap"><span class="ranking-kicker">${icon('flame')}领域热榜</span><h2 id="rankingTitle">${rankingProfiles.tech.title}</h2><p id="rankingDescription">${rankingProfiles.tech.description}</p></div>
          <div class="ranking-head-actions"><div class="ranking-modes" id="rankingModes">${rankingProfiles.tech.modes.map((mode, index) => `<button class="ranking-mode ${index === 0 ? 'active' : ''}">${mode}</button>`).join('')}</div></div>
        </div>
        <div class="ranking-list" id="rankingList">${rankingRowsHtml('tech')}</div>
      </aside>
    </div>
  </section>`;
  shell(content);
}

function detailCommand(domain, primaryLabel = '深入调研') {
  return `<div class="domain-detail-command"><a class="detail-back" href="discover.html">${icon('arrow-left')}返回发现</a><span class="detail-breadcrumb">${domain}</span><span class="toolbar-spacer"></span><button class="btn save-action">${icon('bookmark')}收藏</button><button class="btn" data-toast="已加入持续关注">${icon('eye')}持续关注</button><a class="btn primary" href="research.html">${icon('search-check')}${primaryLabel}</a></div>`;
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
    <div class="detail-command"><a class="detail-back" href="discover.html">${icon('arrow-left')}返回今日情报</a><span class="detail-breadcrumb">${detail.domain} / ${detail.kind}</span><span class="toolbar-spacer"></span><button class="btn save-action">${icon('bookmark')}保存</button><button class="btn" data-toast="已加入持续跟踪">${icon('eye')}持续跟踪</button><a class="btn primary" href="research.html">${icon('search-check')}深入调研</a></div>
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
        <section><h2>相关内容</h2><div class="detail-related">${detail.related.map((title, index) => `<a href="story.html?type=${type}"><span>${String(index + 1).padStart(2, '0')}</span>${title}${icon('chevron-right')}</a>`).join('')}</div></section>
        <section class="detail-agent"><h2>需要更深入？</h2><p>让 Agent 汇总更多来源、比较观点并生成带引用的调研结果。</p><a class="btn primary" href="research.html">${icon('search-check')}开始调研</a></section>
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

function renderKnowledge() {
  const actions = `<button class="btn">${icon('upload')}上传资料</button><button class="btn primary" data-toast="已创建知识集合">${icon('folder-plus')}新建集合</button>`;
  const items = [['Claim','Agent SDK 已形成官方仓库和文档基础','来自研究：Agent SDK 能力边界'],['文档','Model Context Protocol 官方文档','来源：Anthropic Docs'],['Story','Agent 长期记忆评测研究更新','论文、代码与讨论已聚类'],['报告','Agent 基础设施周度回顾','报告版本：示例']];
  const content = `<section class="page">${header('本地知识', '知识库', '检索文档、Story、Claim、实体关系和历史研究。', actions)}<div class="toolbar"><label class="global-search" style="width:min(620px,100%)">${icon('search')}<input type="search" placeholder="搜索知识、Claim 或来源…" aria-label="搜索知识库"></label><div class="segmented"><button class="active">混合</button><button>关键词</button><button>语义</button></div></div><div class="knowledge-layout"><aside class="knowledge-filter"><div class="filter-group"><div class="filter-label">内容类型</div>${['文档','Story','Claim','研究报告','实体'].map((x,i)=>`<label class="check-row"><input type="checkbox" ${i<3?'checked':''}>${x}</label>`).join('')}</div><div class="filter-group"><div class="filter-label">来源等级</div>${['一级来源','独立分析','专业来源','社区线索'].map((x,i)=>`<label class="check-row"><input type="checkbox" ${i<2?'checked':''}>${x}</label>`).join('')}</div><div class="filter-group"><div class="filter-label">时间范围</div><select class="form-control"><option>不限</option><option>最近 30 天</option><option>最近一年</option></select></div></aside><div class="knowledge-results">${items.map(([type,title,sub],i)=>`<article class="knowledge-item ${i===0?'active':''}"><span class="tag ${type==='Claim'?'orange':type==='Story'?'blue':''}">${type}</span><h3>${title}</h3><p>${sub}。该条目为原型示例，可通过右侧查看证据与关联实体。</p></article>`).join('')}</div><aside class="knowledge-detail"><span class="tag orange">Claim</span><h2 class="detail-title">Agent SDK 已形成官方仓库和文档基础</h2><p class="detail-prose">该 Claim 来自一次 Standard Research，并关联两个一级来源。报告中的引用已锁定到本地文档版本。</p><div class="metric-line"><span>状态</span><span class="tag green">Supported</span></div><div class="metric-line"><span>置信度</span><span class="metric-value">高</span></div><div class="metric-line"><span>独立来源族</span><span class="metric-value">2</span></div><h3 class="section-title" style="margin-top:18px">关联实体</h3><div class="signal-tags">${tagsHtml([['OpenAI',''],['Agents SDK',''],['Agent','']])}</div><h3 class="section-title" style="margin-top:18px">证据</h3><div class="evidence-item"><strong>官方仓库</strong><p>支持发布与能力说明。</p></div></aside></div></section>`;
  shell(content);
}

function renderReports() {
  const actions = `<button class="btn">${icon('layout-template')}模板</button><button class="btn primary" data-toast="已创建报告草稿">${icon('plus')}新建报告</button>`;
  const content = `<section class="page">${header('输出与发布', '报告', '日报、周报、专题研究和 Watchlist 变化报告。', actions)}<div class="toolbar"><button class="filter-chip active">全部</button><button class="filter-chip">草稿</button><button class="filter-chip">待核验</button><button class="filter-chip">已发布</button><span class="toolbar-spacer"></span><button class="icon-button" aria-label="搜索报告" title="搜索报告">${icon('search')}</button></div><div class="report-layout"><aside class="report-list"><div class="report-list-item active"><h3>Agent 基础设施周度回顾</h3><p>专题周报 · 草稿 · 示例时间</p></div><div class="report-list-item"><h3>AI 生态每日发现</h3><p>日报 · 已发布 · 示例时间</p></div><div class="report-list-item"><h3>MCP Watchlist 变化</h3><p>变化报告 · 待核验</p></div><div class="report-list-item"><h3>Agent Memory 研究</h3><p>深度研究 · 已完成</p></div></aside><article class="report-preview"><div class="story-meta"><span class="tag amber">草稿</span><span class="meta">版本 3 · 引用检查通过</span></div><h2 class="report-document-title">Agent 基础设施周度回顾</h2><p>本报告汇总本周收录的高影响 Story、Watchlist 变化和已完成研究。所有事实引用均锁定到本地文档版本。</p><div class="story-actions"><button class="btn small" data-toast="报告已保存">${icon('save')}保存</button><button class="btn small" data-toast="已打开预览">${icon('eye')}预览</button><button class="btn primary small" data-toast="发布前检查已开始">${icon('send')}检查并发布</button></div><h3>本周重要变化</h3><p>Agent 工具调用与状态管理仍是主要更新方向。多个官方来源出现文档或仓库变化，其中部分信号已经进入持续跟踪。</p><h3>值得继续观察</h3><ul><li>Agent Memory 的独立评测与复现代码。</li><li>MCP 工具生态的权限与审计实践。</li><li>长任务的检查点和失败恢复机制。</li></ul><h3>引用状态</h3><div class="metric-line"><span>关键 Claim</span><span class="metric-value">8</span></div><div class="metric-line"><span>已验证 Citation</span><span class="metric-value">12 / 12</span></div><div class="metric-line"><span>未解决冲突</span><span class="metric-value">1</span></div></article></div></section>`;
  shell(content);
}

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
  const rows = [['Agent SDK 能力边界研究','Deep Research','运行中','68%','06:42'],['OpenAI Blog 增量采集','Fetch','已完成','100%','00:18'],['arXiv Agent Query','Discovery','等待重试','42%','02:11'],['Weekly Report 发布检查','Publish','等待确认','75%','01:04'],['Lucene 增量索引','Index','已完成','100%','00:33']];
  const content = `<section class="page">${header('运行状态', '任务中心', '采集、解析、索引、研究和发布任务。', `<button class="btn" data-toast="失败任务已重新入队">${icon('rotate-ccw')}重试失败任务</button>`)}<div class="stat-strip"><div class="stat"><div class="stat-label">运行中</div><div class="stat-value">2</div></div><div class="stat"><div class="stat-label">等待</div><div class="stat-value">5</div></div><div class="stat"><div class="stat-label">需要处理</div><div class="stat-value">2</div></div><div class="stat"><div class="stat-label">今日完成</div><div class="stat-value">146</div></div></div><div class="toolbar"><button class="filter-chip active">全部</button><button class="filter-chip">运行中</button><button class="filter-chip">等待</button><button class="filter-chip">错误</button><span class="toolbar-spacer"></span><select class="select"><option>全部类型</option><option>研究</option><option>采集</option><option>发布</option></select></div><div class="table-wrap"><table class="data-table"><thead><tr><th>任务</th><th>类型</th><th>状态</th><th>进度</th><th>耗时</th><th>优先级</th><th></th></tr></thead><tbody>${rows.map(([name,type,state,pct,time],i)=>`<tr><td><div class="cell-title">${name}</div><div class="cell-sub">任务 ID · 示例</div></td><td>${type}</td><td><span class="tag ${state==='运行中'?'orange':state==='已完成'?'green':state==='等待重试'?'red':'amber'}">${state}</span></td><td><div class="flex items-center gap-6"><div class="change-bar"><span style="width:${pct}"></span></div><span class="num">${pct}</span></div></td><td class="num">${time}</td><td>${i===0?'高':'普通'}</td><td><button class="icon-button task-toggle" aria-label="${state==='运行中'?'暂停':'更多'}" title="${state==='运行中'?'暂停':'更多'}">${icon(state==='运行中'?'pause':'more-horizontal')}</button></td></tr>`).join('')}</tbody></table></div></section>`;
  shell(content);
}

function renderSettings() {
  const content = `<section class="page">${header('系统管理', '设置', 'Provider、发现策略、预算、安全、存储和备份。', `<button class="btn primary" data-toast="设置已保存">${icon('save')}保存设置</button>`)}<div class="settings-layout"><aside class="settings-nav"><button class="settings-link active">模型与研究</button><button class="settings-link">搜索与读取</button><button class="settings-link">发现策略</button><button class="settings-link">存储与保留</button><button class="settings-link">安全</button><button class="settings-link">备份与诊断</button></aside><main><section class="settings-section"><h2>模型与研究</h2><p>用于摘要、影响判断、研究和报告的远程模型。</p><div class="form-row"><label class="form-label" for="modelProvider">Provider</label><div><select id="modelProvider" class="form-control"><option>OpenAI-compatible</option><option>Anthropic</option><option>Gemini</option><option>通义</option></select><div class="form-help">凭据已安全保存，不会在页面返回明文。</div></div></div><div class="form-row"><label class="form-label" for="researchModel">研究模型</label><input id="researchModel" class="form-control" value="已配置模型" readonly></div><div class="form-row"><span class="form-label">自动研究</span><div><div class="toggle on" role="switch" aria-checked="true" tabindex="0"></div><div class="form-help">仅对高影响、低置信或来源冲突的信号触发。</div></div></div></section><section class="settings-section"><h2>研究预算</h2><p>限制单次研究可使用的查询、页面、时间和模型费用。</p><div class="form-row"><label class="form-label" for="queryBudget">查询上限</label><input id="queryBudget" class="form-control" type="number" value="25"></div><div class="form-row"><label class="form-label" for="pageBudget">读取页面上限</label><input id="pageBudget" class="form-control" type="number" value="50"></div><div class="form-row"><label class="form-label" for="timeBudget">时间上限</label><select id="timeBudget" class="form-control"><option>15 分钟</option><option>30 分钟</option><option>60 分钟</option></select></div></section><section class="settings-section"><h2>搜索与读取</h2><p>全网发现和 DeepSearch 使用的搜索与 Reader 服务。</p><div class="form-row"><label class="form-label">Search Provider</label><div><div class="health"><span class="health-dot"></span><strong>Brave Search</strong><span class="tag green">已连接</span></div><div class="form-help">用于通用 Web Discovery。</div></div></div><div class="form-row"><label class="form-label">Reader Provider</label><div><div class="health"><span class="health-dot warning"></span><strong>Jina Reader</strong><span class="tag amber">需要检查</span></div><div class="form-help">用于普通 HTTP 无法稳定提取的公开页面。</div></div></div></section><section class="settings-section"><h2>备份</h2><p>备份 SQLite、原文、报告和配置；Secret 不进入普通备份。</p><div class="form-row"><span class="form-label">上次备份</span><div><span>示例时间</span><div style="margin-top:10px"><button class="btn" data-toast="备份任务已开始">${icon('archive')}立即备份</button></div></div></div></section></main></div></section>`;
  shell(content);
}

const renderers = { discover: renderDiscover, story: renderStory, research: renderResearch, watchlist: renderWatchlist, knowledge: renderKnowledge, reports: renderReports, sources: renderSources, views: renderViews, tasks: renderTasks, settings: renderSettings };
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
    document.getElementById('scopeResultCount').textContent = scope.count;
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

  const save = event.target.closest('.save-action');
  if (save) { save.classList.toggle('active'); showToast(save.classList.contains('active') ? '已保存到知识库' : '已取消保存'); }

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

document.getElementById('agentButton')?.addEventListener('click', () => document.getElementById('agentOverlay').classList.add('open'));
document.getElementById('closeAgent')?.addEventListener('click', () => document.getElementById('agentOverlay').classList.remove('open'));
document.getElementById('agentOverlay')?.addEventListener('click', event => { if (event.target.id === 'agentOverlay') event.currentTarget.classList.remove('open'); });
document.getElementById('menuButton')?.addEventListener('click', () => document.getElementById('sidebar').classList.toggle('open'));
document.getElementById('globalSearch')?.addEventListener('keydown', event => { if (event.key === 'Enter' && event.currentTarget.value.trim()) { event.preventDefault(); showToast(`正在搜索：${event.currentTarget.value.trim()}`); } });
document.querySelectorAll('.toggle').forEach(toggle => toggle.addEventListener('keydown', event => {
  if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault();
    toggle.click();
  }
}));
document.addEventListener('keydown', event => { if (event.key === 'Escape') { document.getElementById('agentOverlay')?.classList.remove('open'); document.querySelectorAll('.modal-layer').forEach(x => x.classList.remove('open')); document.getElementById('sidebar')?.classList.remove('open'); } });

const previewState = new URLSearchParams(window.location.search);
if (previewState.get('agent') === 'open') document.getElementById('agentOverlay')?.classList.add('open');
if (previewState.get('modal') === 'source') document.getElementById('sourceModal')?.classList.add('open');
if (previewState.get('modal') === 'topic' || previewState.get('modal') === 'filter') document.getElementById('topicModal')?.classList.add('open');
if (previewState.get('channel')) document.querySelector(`[data-channel="${previewState.get('channel')}"]`)?.click();
if (previewState.get('platform')) document.querySelector(`[data-platform="${previewState.get('platform')}"]`)?.click();
