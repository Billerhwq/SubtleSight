# SubtleSight

> See the subtle. Know the significant.

SubtleSight 是一个本地优先、Web-first 的单机全能情报工作台。它把来源采集、发现、文档解析、Story 聚合、五视图信号、Deep Research、Evidence、Watchlist、报告导出/发布、Agent、任务恢复和灾备恢复连成一个可审计闭环。项目按《全能情报 Agent-Web 版完整闭环设计与测试验收方案》实现，不以演示数据或内存仓储代替核心链路。

## 技术栈

- **后端**: Java 21、Spring Boot 3.5、Spring Security、虚拟线程
- **数据库**: SQLite WAL + Flyway，内容寻址 Blob Store
- **搜索**: Lucene 10（BM25、HNSW 向量、RRF 混合检索）
- **前端**: React 19、TypeScript、Semi Design、TanStack Query、Zustand、Vite、TipTap
- **测试**: JUnit 5、AssertJ、jqwik、ArchUnit、Vitest、Testing Library

## 闭环能力

1. 来源与发现：RSS、网站及通用 Web 来源注册、拉取、SSRF/重定向/响应体上限防护，六类发现查询与跨 Provider 合并。
2. 内容与知识：原始内容按 SHA-256 保存，HTML/Office/PDF 文本抽取、规范化、注入标记、版本化、Story 聚类、实体与主题抽取。
3. 信号与反馈：Latest、Emerging、Important、For You、Saved View，可解释分数、保存/隐藏/不相关/撤销反馈。
4. 研究与证据：Java 持久化状态机、预算硬约束、断点、Claim/Evidence/Locator、原文偏移和快照哈希校验。
5. 监测与交付：Watch baseline/diff/严重度、Markdown/HTML/JSON/PDF 导出、发布确认、幂等键和未知结果对账。
6. 可靠性与安全：SQLite Job lease/heartbeat/retry/cancel/recovery、本机直达模式、CSRF、Origin 校验、密钥脱敏、目录独占锁、备份校验与恢复重建。

## 快速开始（Windows PowerShell）

前置环境：JDK 21、Maven 3.9+、Node.js 20+、pnpm 9+。

```powershell
cd G:\SubtleSight
Copy-Item .env.example .env
# API Key 可留空，此时研究仍走确定性本地证据链。
pnpm --dir web-ui install --frozen-lockfile
pnpm --dir web-ui build
mvn -q -pl server-app -am package -DskipTests
java --add-modules jdk.incubator.vector -jar server-app\target\server-app-1.0.0-SNAPSHOT.jar
```

浏览器访问 `http://127.0.0.1:8080`。默认仅监听本机地址，打开后直接进入工作台；写操作仍校验 CSRF token 与 Origin。

也可以运行：

```powershell
.\scripts\run.ps1
```

## 完整验收

```powershell
.\scripts\verify.ps1
```

该脚本顺序执行前端依赖锁定安装、lint、带覆盖率测试、生产构建、后端整仓 `mvn clean verify`，最后启动打包 JAR，通过真实 HTTP/本机直达/CSRF/Origin 完成来源→入库→Story→检索→反馈→研究任务→证据→Watch→报告/PDF→Agent→备份链路。证据写入 `build/acceptance-*`，失败即非零退出。

单独执行 HTTP 验收：

```powershell
.\scripts\smoke.ps1
```

## 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SUBTLESIGHT_DATA_DIR` | `./data` | SQLite、Blob、Lucene、报告、备份和日志目录 |
| `SUBTLESIGHT_BIND_ADDRESS` | `127.0.0.1` | 监听地址 |
| `SUBTLESIGHT_PORT` | `8080` | HTTP 端口 |
| `SUBTLESIGHT_ALLOWED_ORIGINS` | 本机 8080 | 写操作允许的 Origin |
| `SUBTLESIGHT_OPENAI_BASE_URL` | 空 | OpenAI-compatible API 根地址 |
| `SUBTLESIGHT_OPENAI_API_KEY` | 空 | AI Provider 密钥，不写入备份 |
| `SUBTLESIGHT_SEARCH_BASE_URL` | 空 | JSON Web Search Provider 地址 |
| `SUBTLESIGHT_SEARCH_API_KEY` | 空 | Search Provider 密钥，不写入备份 |

## 项目结构

模块边界和数据流见 [架构说明](docs/ARCHITECTURE.md)，验收项和真实证据见 [验收说明](docs/ACCEPTANCE.md)。核心模块不反向依赖 Spring/Web/SQLite/Lucene，边界由 ArchUnit 测试约束。

### Maven 模块架构

| 模块 | 类型 | 职责 |
|------|------|------|
| `core-domain` | 核心 | 领域模型、状态机、业务规则 |
| `core-application` | 核心 | 应用层接口、业务门面 |
| `discovery-engine` | 引擎 | 数据发现与探索 |
| `research-engine` | 引擎 | 深度研究服务 |
| `story-engine` | 引擎 | Story 聚类与实体提取 |
| `watchlist-engine` | 引擎 | 观察列表管理 |
| `report-engine` | 引擎 | 报告生成与导出 |
| `signal-engine` | 引擎 | 信号视图处理 |
| `evidence-engine` | 引擎 | 证据管理 |
| `document-engine` | 引擎 | 文档解析处理 |
| `agent-engine` | 引擎 | Web Agent 服务 |
| `calendar-engine` | 引擎 | 金融日历处理 |
| `source-connectors` | 连接器 | 多源数据采集（RSS、Arxiv、GitHub Trending、Hacker News、Product Hunt、HuggingFace） |
| `calendar-connectors` | 连接器 | 日历数据源接入 |
| `storage-sqlite` | 存储 | SQLite 持久化实现 |
| `storage-blob` | 存储 | 内容寻址 Blob 存储 |
| `calendar-storage-sqlite` | 存储 | 日历数据 SQLite 存储 |
| `provider-ai` | 提供者 | AI 服务集成（OpenAI-compatible） |
| `provider-search` | 提供者 | 搜索服务集成 |
| `search-lucene` | 基础设施 | Lucene 全文搜索 |
| `runtime-jobs` | 基础设施 | 定时任务与重试机制 |
| `observability` | 基础设施 | 监控、脱敏、备份 |
| `server-app` | 应用 | Spring Boot 主应用 |
| `web-ui` | 前端 | React 前端应用 |
| `acceptance-tests` | 测试 | 端到端验收测试 |

## 数据与恢复

运行时目录由进程独占锁保护。管理页可生成 `.insightpack`，包内包含一致性 SQLite 快照、Blob 与 manifest；不包含 API Key/密码。恢复会拒绝路径穿越、格式不兼容和 SHA-256 不匹配，恢复后 Lucene 可由 SQLite/Blob 重建。

## API 端点

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/research` | POST | 创建研究任务 |
| `/api/research/{id}` | GET/PUT/DELETE | 研究任务 CRUD |
| `/api/stories` | GET | 获取 Story 列表 |
| `/api/stories/{id}` | GET | 获取单个 Story |
| `/api/watchlist` | GET/POST | 观察列表管理 |
| `/api/watchlist/{id}` | PUT/DELETE | 观察项操作 |
| `/api/reports` | POST | 生成报告 |
| `/api/reports/{id}` | GET | 获取报告 |
| `/api/calendar` | GET | 获取日历事件 |
| `/api/calendar/sources` | GET/POST | 日历源管理 |
| `/api/discovery` | POST | 执行发现任务 |
| `/api/sources` | GET/POST | 数据源管理 |
| `/api/sources/{id}` | PUT/DELETE | 数据源操作 |

## 前端页面

| 页面 | 路径 | 功能 |
|------|------|------|
| Discovery | `/discover` | 数据发现与探索 |
| Research | `/research` | 深度研究工作台 |
| Story | `/stories` | Story 聚合视图 |
| Watchlist | `/watchlist` | 观察列表 |
| Reports | `/reports` | 报告管理 |
| Admin | `/admin` | 系统管理 |

## 安全特性

- CSRF token 校验
- Origin 白名单控制
- API Key 脱敏存储（不写入备份）
- SSRF 防护（来源采集）
- 重定向限制
- 响应体大小上限
- 目录独占锁

## 许可证

MIT License
