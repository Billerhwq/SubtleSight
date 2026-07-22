# SubtleSight 统一财经日历设计方案（零付费 API）

## 1. 目标与约束

本方案建设一个可追溯、可去重、可订阅的统一财经日历。数据只来自公开 HTML、RSS/Atom、ICS、公开 JSON 文件和允许访问的网页内嵌状态，不购买或依赖付费 API。

完整交付范围包括未来日程、事件发布状态、实际值/预测值/前值、修正值、来源证据、时区换算、冲突合并、筛选、详情页、刷新、定时采集和 ICS 导出，不以只有一张日历表的 MVP 为终点。

硬约束：

- 不伪造预测值或实际值；源没有该字段时保持 `null` 并显示“暂无”。
- 官方来源优先决定发布时间和实际值；聚合页只补充覆盖面、重要性和市场预测。
- 不绕过登录、验证码、Cloudflare、403 或 robots/站点条款；被限制的适配器自动停用，不影响官方来源链路。
- 所有展示字段都能追溯到原始页面、抓取时间、内容哈希和解析器版本。
- 页面只展示真实入库事件或明确的空状态，不回退到原型静态事件。

## 2. 已验证的免费来源

| 优先级 | 来源 | 免费载体 | 主要字段 | 用途 |
| --- | --- | --- | --- | --- |
| P0 | 中国国家统计局发布日程 | HTML 表格 | 名称、日期、时间、说明 | 中国 GDP、PMI、CPI/PPI、工业、投资、社零等官方日程 |
| P0 | BLS | ICS + HTML | 名称、日期、时间、详情链接 | 美国 CPI、就业、PPI 等官方日程 |
| P0 | BEA | HTML + 官方 ICS/JSON | 名称、日期、时间、发布类型、详情链接 | 美国 GDP、PCE、贸易等官方日程 |
| P0 | Federal Reserve | HTML | FOMC、讲话、会议、新闻事件 | 美联储事件与货币政策日程 |
| P0 | ECB | HTML | 会议、决定、发布会日期和说明 | 欧洲央行货币政策日程 |
| P0 | UK ONS | HTML + RSS + Add-to-calendar | 名称、发布时间、状态、详情链接 | 英国宏观数据官方日程 |
| P1 | FRED Release Calendar | HTML | 发布名称、日期、时间、详情链接 | 美国多机构发布日程补齐与交叉校验 |
| P1 | Investing.com Economic Calendar | 公开 HTML/网页内嵌状态/官方可嵌入组件 | 国家、事件、重要性、实际、预测、前值 | 全球覆盖、预测值、重要性补充 |

2026-07-18 曾出现过本机浏览器/命令行探测返回 HTTP 200 的情况，但最终服务链路验收必须以当前运行环境、站点条款和可持续访问结果为准；不能把一次可访问快照包装成稳定真实来源。当前实现默认禁用 Investing.com 入库适配器，来源健康页明示限制原因；只有在当时条款和访问方式允许、且解析契约测试通过后，才可重新启用。事件区已不是旧版 `eventRowId` 表格结构，因此不能写死旧选择器。实现预留“普通 DOM → 内嵌状态 → 合规浏览器渲染”三种解析策略，并用页面快照契约测试检测结构漂移。

Investing.com 同时提供需接受其条款后生成的 Economic Calendar Widget。能否解析或嵌入必须以当时条款和 robots 为准；如果不允许数据再利用，只保留跳转/嵌入，不将其作为数据库来源。

第二批官方适配器按同一接口扩展：Eurostat、Bank of England、Bank of Japan、日本统计局、加拿大统计局、澳大利亚统计局、中国人民银行/海关总署。没有未来发布日程的站点只采“已发布结果”，不推测未来时间。

## 3. 总体架构

```text
HTML / RSS / ICS / 公开 JSON
            │
            ▼
礼貌抓取器（限速、缓存、ETag、退避、robots/条款开关）
            │
            ▼
原始快照库（URL、响应头、抓取时间、SHA-256、正文 Blob）
            │
            ▼
来源适配器（HTML / JSON-in-HTML / RSS / ICS / Browser）
            │
            ▼
规范化（指标字典、国家、期间、单位、时区、状态）
            │
            ▼
事件归并（字段级来源优先级、冲突与修订历史）
            │
            ├── REST 查询 / 手动刷新 / 来源健康
            ├── 今日、周、月日历与事件详情
            └── ICS 订阅 / SSE 更新
```

### 3.1 模块建议

- `calendar-engine`：领域模型、指标字典、归并规则、状态机、时区与 surprise 计算。
- `calendar-connectors`：每个站点一个适配器，复用现有 `SafeHttpClient`、代理、限速和原始 Blob。
- `calendar-storage-sqlite`：事件、来源证据、字段值和修订历史持久化；Flyway 管理迁移。
- `server-app`：REST、CSRF 保护的刷新入口、定时任务和 SSE。
- `web-ui`：真实日历视图、来源抽屉、刷新状态和空状态。

## 4. 统一数据模型

### 4.1 `calendar_event`

| 字段 | 说明 |
| --- | --- |
| `id` | UUID |
| `event_key` | `country + indicator_code + period + scheduled_local_date` 的稳定哈希 |
| `indicator_code` / `name_zh` / `name_original` | 规范指标和原始名称 |
| `country_code` / `region` / `currency` | 国家、区域、货币 |
| `category` | inflation、employment、growth、trade、central_bank、speech、holiday 等 |
| `importance` | LOW/MEDIUM/HIGH；保留判定来源 |
| `scheduled_at_utc` / `source_timezone` / `scheduled_local_text` | UTC、IANA 时区和原始时间文本 |
| `period` | 例如 2026-06、2026-Q2 |
| `actual` / `forecast` / `previous` / `revised_previous` / `unit` | 数值字段，全部可空 |
| `status` | SCHEDULED/CONFIRMED/RELEASED/REVISED/DELAYED/CANCELLED |
| `official_url` / `primary_source_id` | 官方详情和主来源 |
| `first_seen_at` / `last_seen_at` / `released_at` | 生命周期时间 |
| `version` | 乐观锁版本 |

### 4.2 `calendar_event_evidence`

每个来源一行，记录 `event_id`、`source_id`、`raw_document_id`、`source_event_id`、`source_url`、`fetched_at`、`parser_version`、原始字段 JSON 和解析告警。事件详情页按字段显示“值来自哪个页面”。

### 4.3 `calendar_event_revision`

保存时间、实际值、前值、状态和来源的每次变化，支持回答“发布时间是否调整”“前值何时修正”“哪个来源先报出”。

## 5. 解析、规范化与合并

### 5.1 解析器顺序

1. ICS/RSS/官方 JSON：结构最稳定，优先使用。
2. 静态 HTML：Jsoup 按表头语义和邻接关系解析，不只依赖 CSS 类名。
3. JSON-in-HTML：解析 `__NEXT_DATA__`、JSON-LD 或页面 hydration 状态。
4. 合规浏览器渲染：仅在站点允许且前三种取不到数据时启用；禁止模拟登录或绕过挑战。

每个来源保留 HTML/ICS 脱敏快照夹具。选择器失效时产生 `PARSER_DRIFT` 健康告警，旧数据仍可查询，但不把空解析当作“当天没有事件”。

### 5.2 指标字典

使用可版本化别名表统一中文、英文和缩写，例如：

- `US_CPI_YOY`：CPI (YoY)、Consumer Price Index YoY、美国消费者物价指数年率。
- `CN_NBS_PMI_MFG`：采购经理指数月度报告、制造业 PMI。
- `ECB_RATE_DECISION`：ECB Monetary Policy Decision、欧洲央行利率决议。

未识别名称进入人工可审计的 `UNMAPPED` 队列，不用模糊匹配强行合并。

### 5.3 去重与字段级优先级

主键候选为 `country_code + indicator_code + period + scheduled_local_date`。当期间未知时，用规范名称、当地日期、时间容差和机构共同判定；匹配置信度不足则保留两条并标记待归并。

字段优先级：

1. 官方机构：时间、状态、实际值、修正值、单位。
2. 官方交叉日历（如 FRED）：日程补齐和确认。
3. 聚合 HTML：预测值、重要性、全球覆盖。

冲突不会静默覆盖。主表保存胜出值，证据表保存全部候选，详情页显示冲突和来源。

## 6. 抓取频率与礼貌策略

| 场景 | 建议频率 |
| --- | --- |
| 未来 7–365 天官方日程 | 每 6 小时一次，支持 ETag/Last-Modified |
| 未来 24 小时 | 每 30 分钟一次 |
| 高重要事件发布前后 30 分钟 | 官方结果页每 2–5 分钟，拿到实际值后停止高频 |
| Investing 等聚合页 | 常态 30–60 分钟；发布窗口最低 10 分钟，不并发刷页 |
| 403/429 | 立即停止；指数退避 30 分钟、2 小时、6 小时，并记录健康告警 |

通用限制：单 host 并发 1；请求间隔至少 3 秒并加 0–2 秒抖动；每日请求预算；超时、最大响应体、重定向和 SSRF 防护沿用现有安全客户端。手动“刷新”仍受相同 host 预算约束，不能绕过限速。

## 7. 后端接口

- `GET /api/v1/calendar/events?from=&to=&countries=&categories=&importance=&status=`
- `GET /api/v1/calendar/events/{id}`：事件、修订历史、字段级来源。
- `GET /api/v1/calendar/sources`：来源状态、最后成功时间、下次计划、解析告警。
- `POST /api/v1/calendar/refresh?scope=today`：真实采集，返回逐源结果；受 CSRF 和限速保护。
- `GET /api/v1/calendar.ics?...`：按筛选条件订阅。
- `GET /api/v1/calendar/stream`：实际值、延迟、取消和修订的 SSE 更新。

刷新报告必须区分：请求失败、解析失败、0 条新增、字段更新、事件新增，避免再次把“0 条”误报为“未运行”。

## 8. 前端方案

- 今日 / 本周 / 月视图，默认按用户时区展示，同时可切换来源时区。
- 筛选国家、货币、类别、重要性和状态。
- 行内显示时间、国家、事件、重要性、实际/预测/前值；未发布保持 `—`。
- 实际值发布后计算 `actual - forecast`，但只有同单位且可比较时才显示高于/低于预期。
- 点击事件打开来源抽屉：官方链接、聚合链接、抓取时间、修订记录、冲突和原始证据摘要。
- 顶部明确显示“最后成功采集”和“下一次采集”，刷新按钮展示逐源真实结果。
- 数据为空、来源失败和解析器漂移分别显示，不用静态样例填充。

## 9. 测试与真实验收

### 9.1 单元测试

- 每个来源的 HTML/ICS/RSS 快照解析。
- 夏令时、跨日、全天假日、`TBA`、延迟和取消。
- 指标别名、期间解析、单位规范化、事件键和模糊匹配阈值。
- 官方/聚合冲突、实际值修订、空预测值和 surprise 计算。
- 频控、ETag、429/403 退避和请求预算。

### 9.2 功能与链路测试

- 本地 HTTP fixture：抓取 → 原始快照 → 解析 → 合并 → SQLite → REST → ICS。
- 同一事件由国家统计局/官方源与聚合源同时进入，断言只形成一个事件且字段来源正确。
- 页面结构变化导致解析 0 条时，断言来源为 `PARSER_DRIFT`，不能删除既有未来事件。
- 前端断言空数据不显示任何原型事件，筛选数量来自 REST 结果。

### 9.3 联网验收证据

最终交付时在独立真实数据目录执行：

1. 至少抓取国家统计局、BLS、BEA、Fed/ECB/ONS 中三类官方源和一个合规聚合源。
2. 保存逐源 HTTP 状态、响应头、抓取时间、SHA-256、解析数、入库数和告警。
3. 抽取当天/未来七天事件，与原始页面人工抽样核对时间、名称和数值。
4. 验证手动刷新和定时刷新都会推进 `last_attempt_at/last_success_at`。
5. 从 REST 查询、前端日历和导出的 ICS 各核对同一事件。
6. 保存机器可读 `calendar-evidence.json`、日志和截图；任何断言失败时验收退出码非 0。

## 10. 实施顺序与完成定义

实施可按依赖顺序推进，但完整交付必须同时满足以下条件：

1. 完成领域模型、SQLite 迁移、合并与修订历史。
2. 完成 P0 官方适配器和允许使用的 Investing 适配器；被条款限制时以其他公开 HTML 官方/聚合页替代，并在来源页明示覆盖变化。
3. 完成 REST、定时刷新、手动刷新、限速和来源健康。
4. 完成日历 UI、来源证据抽屉和 ICS 导出。
5. 单元、功能、链路和真实联网验收全部通过并留下证据。

“页面能看到若干事件”不是完成；只有事件真实、更新持续、字段可追溯、冲突可解释、测试可复现，才算闭环。
