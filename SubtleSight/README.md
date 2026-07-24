# SubtleSight

> **See the subtle. Know the significant.**

SubtleSight 是一个本地优先、Web-first 的单机全能情报工作台。它把来源采集、发现、文档解析、Story 聚合、信号分析、Deep Research、证据管理、Watchlist、报告导出、Agent 任务和灾备恢复连成一个可审计闭环。

前端采用组合式入口：发现、知识库、财经日历等工作台页面使用原生 HTML/CSS/JavaScript，编辑与 Draw 模块使用 React 19。两部分共享同一套浅色导航和后端 API，用户从知识库进入编辑器时不会经过旧版深色 React 首页。

当前主要入口：

- **发现**：`http://localhost:5173/discover.html`
- **知识库**：`http://localhost:5173/knowledge.html`
- **编辑与 Draw**：`http://localhost:5173/knowledge-editor.html#/knowledge/editor`
- **生产部署**：构建后由 Spring Boot 在 `http://127.0.0.1:8080` 同端口提供界面和 API

---

## 技术栈

| 层 | 技术 |
|---|------|
| **后端** | Java 21、Spring Boot 3.5、Spring Security、虚拟线程、Flyway |
| **数据库** | SQLite WAL + 内容寻址 Blob Store |
| **搜索** | Lucene 10（BM25、HNSW 向量、RRF 混合检索） |
| **文档解析** | Apache Tika 3.2、Apache POI 5.4 |
| **前端 (React SPA)** | React 19、TypeScript、Semi Design、TanStack Query、Zustand、Vite、TipTap |
| **前端 (Prototype)** | 原生 HTML + CSS + JavaScript（Lucide 图标） |
| **测试** | JUnit 5、AssertJ、jqwik、ArchUnit、Vitest、Testing Library |

---

## 模块架构

| 模块 | 类型 | 职责 |
|------|------|------|
| `core-domain` | 核心 | 领域模型、状态机、业务规则 |
| `core-application` | 核心 | 应用层接口、业务门面 |
| `discovery-engine` | 引擎 | 数据发现与探索 |
| `research-engine` | 引擎 | 深度研究服务（状态机、预算约束） |
| `story-engine` | 引擎 | Story 聚类与实体/主题提取 |
| `watchlist-engine` | 引擎 | 观察列表管理 |
| `report-engine` | 引擎 | 报告生成与导出（Markdown/HTML/JSON/PDF） |
| `signal-engine` | 引擎 | 信号视图处理 |
| `evidence-engine` | 引擎 | 证据管理 |
| `document-engine` | 引擎 | 文档解析处理 |
| `agent-engine` | 引擎 | Web Agent 服务 |
| `calendar-engine` | 引擎 | 金融日历处理 |
| `source-connectors` | 连接器 | RSS、Hacker News、GitHub Trending、Arxiv、Product Hunt、HuggingFace |
| `calendar-connectors` | 连接器 | 日历数据源接入（ICS/HTML） |
| `storage-sqlite` | 存储 | SQLite 持久化实现 |
| `storage-blob` | 存储 | 内容寻址 Blob 存储 |
| `calendar-storage-sqlite` | 存储 | 日历数据 SQLite 存储 |
| `provider-ai` | 提供者 | AI 服务集成（OpenAI-compatible） |
| `provider-search` | 提供者 | 搜索服务集成 |
| `search-lucene` | 基础设施 | Lucene 全文搜索引擎 |
| `runtime-jobs` | 基础设施 | 分布式任务队列与重试机制 |
| `observability` | 基础设施 | 监控、密钥脱敏、备份恢复 |
| `server-app` | 应用 | Spring Boot 主应用（REST API） |
| `web-ui` | 前端 | React SPA + 原型 HTML 页面 |
| `acceptance-tests` | 测试 | 端到端验收测试 |
| `coverage-report` | 测试 | JaCoCo 聚合覆盖率报告 |

---

## 快速开始

### 前置环境

- **JDK 21+**
- **Maven 3.9+**
- **Node.js 20+**

### 开发模式（前后端分离）

#### 1. 启动后端

```bash
cd SubtleSight
mvn -q -pl server-app -am package -DskipTests -Dskip.web
java -jar server-app/target/server-app-1.0.0-SNAPSHOT.jar
```

后端启动于 `http://127.0.0.1:8080`。

#### 2. 启动前端

```bash
pnpm --dir web-ui install --frozen-lockfile
pnpm --dir web-ui dev
```

前端启动于 `http://localhost:5173`（自动代理 `/api` 到后端）。

#### 3. 访问

- **发现**：`http://localhost:5173/discover.html`
- **知识库**：`http://localhost:5173/knowledge.html`
- **编辑与 Draw**：`http://localhost:5173/knowledge-editor.html#/knowledge/editor`
- **后端直连**：`http://127.0.0.1:8080`（需先完成生产构建）

### 生产模式

```bash
mvn -q -pl server-app -am package -DskipTests
java -jar server-app/target/server-app-1.0.0-SNAPSHOT.jar
```

访问 `http://127.0.0.1:8080`（React SPA + API 同一端口）。

---

## 知识库功能

知识库是 SubtleSight 的文档管理中心，支持文件夹/文件的增删改查、上传下载、全文搜索和在线预览。

### API 端点

| 方法 | 端点 | 描述 |
|------|------|------|
| `GET` | `/api/v1/knowledge/folders` | 获取所有文件夹 |
| `POST` | `/api/v1/knowledge/folders` | 创建文件夹 |
| `DELETE` | `/api/v1/knowledge/folders/{id}` | 递归删除文件夹（含子文件夹和文件） |
| `GET` | `/api/v1/knowledge/files` | 获取文件列表（可选 `?folderId=`） |
| `POST` | `/api/v1/knowledge/upload` | 上传文件（multipart） |
| `POST` | `/api/v1/knowledge/files/{id}/rename` | 重命名文件 |
| `POST` | `/api/v1/knowledge/files/{id}/move` | 移动文件 |
| `DELETE` | `/api/v1/knowledge/files/{id}` | 删除文件 |
| `GET` | `/api/v1/knowledge/files/{id}/download` | 下载文件（attachment） |
| `GET` | `/api/v1/knowledge/files/{id}/view` | 内联查看文件（iframe 嵌入） |
| `GET` | `/api/v1/knowledge/files/{id}/preview` | 预览文件（含文本提取/图片 base64/Office HTML） |
| `GET` | `/api/v1/knowledge/search?q=` | 全库搜索（模糊匹配文件夹和文件名） |

### 预览支持

| 文件类型 | 预览方式 | 说明 |
|---------|---------|------|
| 文本（.txt/.md/.js/.py/.json 等 50+ 种） | Syntax-highlighted 代码块 | 自动检测编码（UTF-8/GBK/GB18030） |
| 图片（.png/.jpg/.gif/.webp/.svg 等 12 种） | Base64 内嵌渲染 | SVG 作为文本渲染 |
| PDF | 浏览器 iframe 内联查看 | `Content-Disposition: inline` |
| Word（.docx） | POI 解析为富文本 HTML | 保留段落结构 |
| Excel（.xlsx） | POI 解析为 HTML 表格 | 首行加粗，Sheet 名称 |
| PowerPoint（.pptx） | POI 解析为幻灯片卡片 | 缩略列表 |
| 旧版 Office（.doc/.xls/.ppt） / ODF / RTF | Tika 纯文本提取 | 等宽字体显示 |
| 其他二进制 | 下载提示 | — |

### 前端交互

- **文件夹管理**: 新建（树面板 + 按钮）、重命名、移动、递归删除
- **文件操作**: 上传（拖拽/选择，多文件并行）、下载、右键菜单
- **视图模式**: 网格视图 / 列表视图，名称升序/降序排序
- **搜索**: 带 200ms 防抖的全库搜索，显示匹配文件路径
- **预览面板**: 右侧可拖拽宽度面板，鼠标悬停时独立滚动

### 编辑与 Draw 工作流

知识库的“编辑”入口加载 React 编辑器，但继续使用工作台现有的浅色侧栏、分组导航和 `Live · 本机数据` 顶栏。编辑器保留完整的文档与绘图闭环：

- TipTap 富文本编辑、标题编辑和文件夹归属
- 1.2 秒无操作后自动保存，也可手动保存
- 使用 `expectedVersion` 做乐观并发检查，冲突时不会覆盖当前页面内容
- 保存形成版本历史，可恢复任意旧版本并生成新版本
- AI 辅助支持基于全文或选区生成建议，并将结果插回正文
- Draw BETA 支持节点、连线、拖动、文本和颜色等属性编辑
- Draw 数据与正文统一保存到 SQLite，可插入文档后继续编辑并进入版本历史

编辑器相关 API：

| 方法 | 端点 | 描述 |
|---|---|---|
| `GET/POST` | `/api/v1/knowledge/documents` | 查询或创建编辑文档 |
| `GET/PUT/DELETE` | `/api/v1/knowledge/documents/{id}` | 获取、保存或删除文档 |
| `GET` | `/api/v1/knowledge/documents/{id}/versions` | 获取版本历史 |
| `POST` | `/api/v1/knowledge/documents/{id}/versions/{version}/restore` | 恢复指定版本 |
| `POST` | `/api/v1/knowledge/documents/{id}/ai-assist` | 生成 AI 编辑建议 |

---

## 配置

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SUBTLESIGHT_DATA_DIR` | `./data` | SQLite、Blob、Lucene、报告、备份和日志目录 |
| `SUBTLESIGHT_BIND_ADDRESS` | `127.0.0.1` | 监听地址 |
| `SUBTLESIGHT_PORT` | `8080` | HTTP 端口 |
| `SUBTLESIGHT_SECURE_COOKIE` | `false` | Cookie Secure 标志 |
| `SUBTLESIGHT_ALLOWED_ORIGINS` | 本机地址 | 写操作允许的 Origin |
| `SUBTLESIGHT_OPENAI_BASE_URL` | 空 | OpenAI-compatible API 根地址 |
| `SUBTLESIGHT_OPENAI_API_KEY` | 空 | AI Provider 密钥，不写入备份 |
| `SUBTLESIGHT_OPENAI_MODEL` | `gpt-4.1-mini` | AI 模型名称 |
| `SUBTLESIGHT_SEARCH_BASE_URL` | 空 | JSON Web Search Provider 地址 |
| `SUBTLESIGHT_SEARCH_API_KEY` | 空 | Search Provider 密钥 |
| `SUBTLESIGHT_UPLOAD_MAX_FILE_SIZE` | `2GB` | 单文件上传上限 |
| `SUBTLESIGHT_COLLECT_SCHEDULER_ENABLED` | `true` | 自动采集调度器 |
| `SUBTLESIGHT_CALENDAR_SCHEDULER_ENABLED` | `true` | 日历采集调度器 |

---

## 工作台页面

工作台主页面使用原生 HTML 构建，编辑入口由 React 模块承载：

| 页面 | 路径 | 功能 |
|------|------|------|
| 发现 | `discover.html` | 信息发现与信号流 |
| **知识库** | `knowledge.html` | 文档管理与在线预览 |
| **编辑** | `knowledge-editor.html#/knowledge/editor` | 富文档编辑、版本历史、AI 辅助与 Draw |
| 财经日历 | `calendar.html` | 宏观日历事件 |
| Watchlist | `watchlist.html` | 持续跟踪管理 |
| 报告 | `reports.html` | 报告列表与预览 |
| 自定义视图 | `views.html` | 组合发现规则 |
| 信源 | `sources.html` | 数据源健康管理 |
| 任务中心 | `tasks.html` | 后台任务状态 |
| 设置 | `settings.html` | 系统配置 |

---

## 完整 API 端点

| 端点 | 方法 | 描述 |
|------|------|------|
| `/api/v1/research` | POST | 创建研究任务 |
| `/api/v1/research/{id}` | GET/PUT/DELETE | 研究任务 CRUD |
| `/api/v1/stories` | GET | Story 列表 |
| `/api/v1/stories/{id}` | GET | 单个 Story 详情 |
| `/api/v1/watchlist` | GET/POST | 观察列表 |
| `/api/v1/reports` | POST | 生成报告 |
| `/api/v1/reports/{id}` | GET | 获取报告 |
| `/api/v1/calendar/events` | GET | 日历事件 |
| `/api/v1/calendar/sources` | GET | 日历来源 |
| `/api/v1/discovery` | POST | 执行发现 |
| `/api/v1/sources` | GET/POST | 数据源管理 |
| `/api/v1/auth/status` | GET | 认证状态 |
| `/api/v1/knowledge/*` | 多种 | 知识库 CRUD（见上表） |
| `/actuator/health` | GET | 健康检查 |
| `/actuator/prometheus` | GET | Prometheus 指标 |

---

## 安全特性

- CSRF 保护（可配置关闭）
- Origin 白名单控制
- API Key 脱敏存储（不写入备份）
- SSRF 防护（来源采集）
- 重定向限制
- 响应体大小上限
- X-Frame-Options: SAMEORIGIN（支持 iframe 内联预览）

---

## 数据与恢复

运行时目录由进程独占锁保护。管理页可生成 `.insightpack` 备份包，包含一致性 SQLite 快照、Blob 与 manifest；不包含 API Key/密码。恢复会拒绝路径穿越、格式不兼容和 SHA-256 不匹配，恢复后 Lucene 可由 SQLite/Blob 重建。

---

## 项目结构

```
SubtleSight/
├── pom.xml                    # Maven 父 POM
├── .env.example               # 环境变量模板
├── scripts/
│   ├── run.ps1                # 一键启动
│   ├── verify.ps1             # 完整验收
│   └── smoke.ps1              # HTTP 验收
├── docs/
│   ├── ARCHITECTURE.md        # 架构说明
│   └── ACCEPTANCE.md          # 验收说明
├── server-app/                # Spring Boot 主应用
│   └── src/main/java/com/subtlesight/server/
│       ├── SubtleSightApplication.java
│       ├── SecurityConfig.java
│       ├── ApplicationConfiguration.java
│       ├── KnowledgeController.java
│       ├── KnowledgeService.java
│       ├── ApiController.java
│       └── ...
├── web-ui/                    # 前端应用
│   ├── public/                # 原型 HTML 页面
│   │   ├── knowledge.html
│   │   ├── discover.html
│   │   ├── app.js             # 原型版 JS 逻辑
│   │   └── styles.css         # 原型版样式
│   └── src/                   # React SPA
│       ├── App.tsx
│       ├── api/client.ts
│       ├── pages/KnowledgePage.tsx
│       └── ...
├── core-domain/               # 领域模型
├── core-application/          # 应用层
├── storage-sqlite/            # SQLite 存储
├── search-lucene/             # Lucene 搜索
├── runtime-jobs/              # 任务队列
└── ...
```

---

## 许可证

MIT License
