# 验收说明

本项目的通过标准是“断言实际业务结果并取得进程退出码 0”，不是接口存在、测试启动或人工口头判断。

## 自动验收映射

| 设计验收项 | 自动化入口 | 关键断言 |
| --- | --- | --- |
| E2E-01 来源到 Feed | `FullClosedLoopAcceptanceTest`、`smoke.ps1` | 原文/版本/Story 持久化，五视图信号可解释，检索可召回 |
| E2E-02 发现到 Story | `DiscoveryPlannerTest`、闭环测试 | 六类查询、URL 规范化、跨源去重、Story 成员 |
| E2E-03 Deep Research | 闭环测试、真实 HTTP Job | 状态机进入终态，Claim 和 Evidence 持久化，引用定位率 100% |
| E2E-04 Watchlist | 闭环测试、真实 HTTP | baseline diff、严重度、重复检测不重复落库、确认升级版本 |
| E2E-05 Report/Publish | 闭环测试、真实 HTTP | 四种导出非空，PDF 真渲染，引用门禁，发布确认和幂等 |
| E2E-06 UI/Security | Vitest、`SecurityFunctionalTest`、真实 HTTP | lint/type/build、SubtleSight 原型壳发布、本机直达、CSRF Cookie、CSRF/Origin 拦截 |
| E2E-07 Agent | 闭环测试、真实 HTTP | 工具白名单、提示注入阻断、高风险发布不能直达 |
| E2E-08 Crash/Recovery | `CrashRecoveryAcceptanceTest`、备份恢复段 | lease 回收、checkpoint、恢复库/Blob 校验、Lucene 重建召回 |

## 测试层次

- 单元/属性测试：URL、状态机、预算、重试、SSRF、解析、聚类、信号、证据、Watch、报告、Agent、Provider、脱敏。
- 架构测试：核心域与应用边界不得依赖 Web、Spring、SQLite、Lucene 具体实现。
- 功能测试：真实 SQLite/Flyway、Blob、Lucene、Spring Context、MockMvc Security、PDF renderer。
- 链路测试：真实进程监听随机本机端口，通过本机直达模式和 CSRF 发起 HTTP 请求，异步轮询持久化 Job 终态。
- 恢复测试：SQLite online backup 打包，解包后校验 manifest/hash，打开恢复库，检查 Blob，重建并查询 Lucene。

## 复验命令

```powershell
cd G:\SubtleSight
.\scripts\verify.ps1
```

脚本任何阶段失败都会停止并返回非零退出码。最终的机器可读证据位于 `build/acceptance-*/evidence.json`，服务 stdout/stderr 同目录保留。

## 2026-07-16 实测记录

- 改名后 clean 一键验收：`.\scripts\verify.ps1 -SkipInstall`，退出码 0；前端 lint/test/build 通过，Maven reactor `clean verify` 为 `BUILD SUCCESS`，真实 JAR smoke 通过，覆盖率聚合刷新通过。
- 真实 HTTP 证据：`G:\SubtleSight\build\acceptance-20260716-232148\evidence.json`，`accepted=true`，启动后 health=UP，本机直达/CSRF/Origin、采集、Feed、搜索、异步研究、Watchlist、报告/PDF、Agent 注入防护、备份均完成。
- 后端 Surefire 汇总：55 tests，0 failures，0 errors，0 skipped。
- 前端 Vitest：7 tests，0 failures；业务逻辑行/函数/语句覆盖率 100%，分支覆盖率 90.32%。
- JaCoCo 聚合报告：`G:\SubtleSight\coverage-report\target\site\jacoco-aggregate\index.html`；聚合行覆盖率 91.65%，聚合分支覆盖率 64.02%。真实 HTTP smoke 覆盖率通过 `server-app\target\jacoco-smoke.exec` dump 后纳入聚合。

## 2026-07-17 原型替换后实测记录

- 前端已替换为 `G:\SubtleSight\jianwei-web-prototype` 原型界面，并改为英文品牌名 SubtleSight；根页面和 Discover/Story/Research/Watchlist/Knowledge/Reports/Sources/Views/Tasks/Settings 页面均由静态原型壳发布。
- 本轮补齐后端聚合接口 `GET /api/v1/workspace/overview`，供原型界面读取 summary、sources、stories、documents、research、watchlists、reports、views、jobs、feed 等本机真实数据；登录步骤保持移除，`/api/v1/auth/status` 返回本机直达状态。
- 最终一键验收：`.\scripts\verify.ps1 -SkipInstall`，退出码 0；前端 lint/test/build 通过且无 Vite 静态资源警告，Maven reactor `clean verify` 为 `BUILD SUCCESS`，真实 JAR smoke 通过，覆盖率聚合刷新通过。
- 真实 HTTP 证据：`G:\SubtleSight\build\acceptance-20260717-204654\evidence.json`，`accepted=true`，`packagedUi=true`，`noLoginMode=true`，`badOriginRejected=true`，启动后 health=UP；采集、Feed、搜索、异步研究、Watchlist、报告/PDF、Agent 注入防护、备份均完成。
- 后端 Surefire 汇总：54 tests，0 failures，0 errors，0 skipped。
- 前端 Vitest：9 tests，0 failures；业务逻辑行/函数/语句覆盖率 100%，分支覆盖率 90.32%。
- JaCoCo 聚合报告：`G:\SubtleSight\coverage-report\target\site\jacoco-aggregate\index.html`；聚合行覆盖率 92.10%，聚合分支覆盖率 63.93%。真实 HTTP smoke 覆盖率通过 `server-app\target\jacoco-smoke.exec` dump 后纳入聚合。

## 2026-07-17 真实信源接入后实测记录

- 新增真实采集源：Hacker News Top Stories、GitHub AI Trending Search、arXiv AI Recent Papers、Hugging Face Trending Models、Product Hunt Daily Feed、Lobsters Technology RSS、TechCrunch AI RSS；主 Feed 与右侧“真实信源热榜”改为读取后端真实入库 Story/Signal。
- 采集频控：后端 `SafeHttpClient` 支持按 host 睡眠间隔（本次 live 验证使用 1200ms），arXiv connector 保留 API 友好延迟；每源单次采集量受 `SUBTLESIGHT_COLLECT_MAX_ITEMS_PER_SOURCE` 限制。本机网络下 Java 对 Hugging Face 直连超时，已补充 `HTTP_PROXY/HTTPS_PROXY/ALL_PROXY` 自动读取与失败直连回退，不让单一网络路径影响全部信源。
- live 真实数据目录：`G:\SubtleSight\build\live-real-data-2`；最终全信源采集报告 7 个源全部 `ok=true`，累计 `sources=7`、`stories=19`、`documents=19`。已验证样例包括 arXiv `RoboTTT: Context Scaling for Robot Policies`、HN/Kaggle `Blatant AI slop...`、Hugging Face `thinkingmachines/Inkling`、Product Hunt `Scribble Party`、TechCrunch AI RSS 与 Lobsters RSS 条目。
- UI 截图证据：`G:\SubtleSight\build\screenshots\subtlesight-real-sources-live.png`，页面同源加载 `GET /api/v1/workspace/overview`，主 Feed 和右侧榜单均显示真实后端趋势。
- 最终一键验收：`.\scripts\verify.ps1 -SkipInstall`，退出码 0；前端 lint/test/build 通过，Maven reactor `clean verify` 为 `BUILD SUCCESS`，真实 JAR smoke 通过，覆盖率聚合刷新通过。
- 真实 HTTP 闭环证据：`G:\SubtleSight\build\acceptance-20260717-215606\evidence.json`，`accepted=true`，`packagedUi=true`，`noLoginMode=true`，`badOriginRejected=true`，启动后 health=UP；采集、Feed、搜索、异步研究、Watchlist、报告/PDF、Agent 注入防护、备份均完成。
- 后端 Surefire 汇总：55 tests，0 failures，0 errors，0 skipped。
- 前端 Vitest：4 test files，9 tests，0 failures；Vite production build 通过。

## 2026-07-18 真实刷新纠偏与复验记录

- 用户反馈属实：原实现虽已入库真实数据，但平台筛选会重新渲染 `discoveryChannelSignals` 原型卡片；右侧榜单的平台匹配也会失败后回退到全平台；“刷新”按钮没有触发采集；真实来源没有独立定时调度。以上问题均已修复。
- 发现页现在只展示后端真实入库 Feed/Trend；即使后端返回 0 条，也只显示“暂无真实入库”，不再回退到原型卡片或榜单。频道计数和频道/平台筛选均由真实 Feed 计算，并新增 Vitest 防回归断言。
- 后端新增真实来源定时采集器，默认启动 60 秒后执行、之后每 30 分钟执行；逐 host 睡眠、单源数量上限、串行采集和失败隔离继续生效。页面“刷新”现调用 `POST /api/v1/sources/collect-real` 并显示真实新增数。
- GitHub 来源改为滚动 7 天、按 `updated` 排序，并以 `pushed_at → updated_at → created_at` 作为事件时间，避免最近更新仓库被显示成创建于几个月前；连接器功能测试断言 `pushed_at` 优先。
- 2026-07-18 12:46（Asia/Shanghai）现场刷新：刷新前 Feed 41 条，刷新后 44 条；7 个来源全部 `ok=true`，报告 `totalIngested=6`，其中 Hacker News 3、GitHub 3，其余来源本轮游标前无新增。所有来源 `updatedAt` 从 12:34 推进到 12:46，证明请求实际执行；后续定时器运行后 Feed 达到 47 条。
- 真实数据目录：`G:\SubtleSight\build\live-real-data-2`。当前来源为 Hacker News、GitHub、arXiv、Hugging Face、Product Hunt、Lobsters RSS、TechCrunch AI RSS；页面接口返回 7 个来源、47 条 Feed、20 条趋势。
- 最终代码全量自动验收：`G:\SubtleSight\build\verify-final-real-refresh-20260718-125703.log`，前端 4 个测试文件/10 个测试通过，24 模块 Maven reactor `BUILD SUCCESS`；HTTP 闭环证据 `G:\SubtleSight\build\acceptance-20260718-130127\evidence.json`，`accepted=true`、`health=UP`、`packagedUi=true`、`noLoginMode=true`。
- 修复后 Hugging Face 平台筛选截图：`G:\SubtleSight\build\screenshots\subtlesight-hf-real-filter-20260718-fixed.png`，主 Feed 与右榜均只显示真实入库的 `thinkingmachines/Inkling`、`prism-ml/Ternary-Bonsai-27B-gguf`。
- 最终打包实例截图：`G:\SubtleSight\build\screenshots\subtlesight-real-refresh-final-20260718.png`；实例健康状态 `UP`，真实数据目录返回 7 个来源、50 条 Feed、20 条趋势，发现页频道计数来自真实 Feed。正式实例启动后的定时任务于 13:04:11–13:04:32 再次推进全部 7 个来源的 `updatedAt`；本轮无新内容所以 Feed 保持 50 条，证明“0 新增”和“未执行”已被正确区分。

## 2026-07-18 财经日历闭环实现与真实联网验收记录

- 已按 `docs/FINANCIAL_CALENDAR_DESIGN.md` 完成统一财经日历闭环：新增 `calendar-engine`、`calendar-connectors`、`calendar-storage-sqlite`，并在 `server-app` 接入 REST、CSRF 手动刷新、来源健康、定时刷新、SSE、字段证据、修订历史和 ICS 导出；前端 `calendar.html` 只展示真实入库事件，空状态不回退原型静态样例。
- API 与设计对齐：`GET /api/v1/calendar/events`、`GET /api/v1/calendar/events/{id}`、`GET /api/v1/calendar/sources`、`POST /api/v1/calendar/refresh?scope=today`、`GET /api/v1/calendar.ics`、`GET /api/v1/calendar/stream` 均已实现；旧兼容地址 `/api/v1/calendar/calendar.ics` 继续可用。
- 真实联网财经日历证据：`G:\SubtleSight\build\calendar-acceptance-20260718-135531\calendar-evidence.json`。本次逐源采集解析 543 条、插入 460 条、更新 76 条；窗口查询（过去 3 天到未来 30 天）返回事件，页面最终实例同窗口返回 273 条。
- 本轮真实成功来源：TradingEconomics Public Calendar（HTTP 200，解析 314）、New York Fed Economic Indicators Calendar（HTTP 200，解析 42）、BEA Release Calendar（HTTP 200，解析 119）、ECB Monetary Policy Calendar（HTTP 200，解析 1）、Federal Reserve FOMC Calendar（HTTP 200，解析 57）、UK ONS Release Calendar（HTTP 200，解析 10）。
- 本轮没有伪装成功的来源：Investing.com 默认禁用，原因是当前条款/访问链路不能保证合规稳定采集；BLS 在 Java 采集链路里连接超时；中国国家统计局英文发布日程在 Java 链路里出现证书链校验失败。三者都在来源健康里显示为 `DISABLED` 或 `DOWN`，不会用旧数据或静态样例冒充成功。
- 频控验收：同一数据目录立即二次刷新返回 `RATE_LIMITED_UNTIL_*`，解析/插入/更新均为 0，证明手动刷新也受 `nextAllowedAt` 约束；本轮 live 采集环境设置单 host 请求间隔 3000ms。
- 前端截图证据：`G:\SubtleSight\build\screenshots\financial-calendar-20260718-135531.png`，显示财经日历页面、来源健康、失败源告警和“只显示真实入库”标识。
- 最终服务实例：`G:\SubtleSight\build\calendar-final-run.txt`，数据目录 `G:\SubtleSight\build\calendar-live-20260718-135443`；最终 smoke 证据 `G:\SubtleSight\build\calendar-final-smoke-20260718-141548.json`，`/actuator/health=UP`，`/calendar.html=200`。
- 单元/功能/链路测试：后端 targeted 日历测试日志 `G:\SubtleSight\build\calendar-targeted-maven-test.log`；前端测试日志 `G:\SubtleSight\build\calendar-web-test.log`、构建日志 `G:\SubtleSight\build\calendar-web-build.log`。最终全量验收日志 `G:\SubtleSight\build\verify-financial-calendar-code-20260718-140816.out.log`，退出码文件 `G:\SubtleSight\build\verify-financial-calendar-code-20260718-140816.exit.txt=0`；Surefire 汇总 29 个报告、63 tests、0 failures、0 errors、0 skipped；HTTP 闭环证据 `G:\SubtleSight\build\acceptance-20260718-141306\evidence.json`，`accepted=true`、`health=UP`、`packagedUi=true`、`noLoginMode=true`。
