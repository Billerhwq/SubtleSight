# SubtleSight

> See the subtle. Know the significant.

SubtleSight 是一个本地优先、Web-first 的单机情报研究工作台。项目把信息源采集、发现、知识库、富文档编辑、Draw 可视化、Story 聚合、Deep Research、Watchlist、财经日历、报告输出、Agent 任务和备份恢复整合在同一套可审计工作流中。

项目主代码位于 [`SubtleSight/`](SubtleSight/)，详细架构、API 和配置说明见 [`SubtleSight/README.md`](SubtleSight/README.md)。

## 核心能力

- 多来源采集：RSS、Hacker News、GitHub Trending、Arxiv、Product Hunt、HuggingFace
- 情报发现：Story 聚合、信号分析、全文搜索和混合检索
- 知识库：文件夹与文件管理、上传下载、全文搜索、多格式在线预览
- 编辑与 Draw：TipTap 富文本、自动保存、乐观版本检查、版本历史、恢复、AI 辅助和可编辑流程图
- 研究与输出：Deep Research、证据管理、Watchlist、财经日历、报告生成与导出
- 本地数据闭环：SQLite WAL、内容寻址 Blob Store、Lucene 索引、备份与恢复

## 界面入口

开发环境默认使用 Vite 的 `5173` 端口：

| 功能 | 地址 |
|---|---|
| 发现 | `http://localhost:5173/discover.html` |
| 知识库 | `http://localhost:5173/knowledge.html` |
| 编辑与 Draw | `http://localhost:5173/knowledge-editor.html#/knowledge/editor` |
| 财经日历 | `http://localhost:5173/calendar.html` |
| Watchlist | `http://localhost:5173/watchlist.html` |
| 报告 | `http://localhost:5173/reports.html` |
| 设置 | `http://localhost:5173/settings.html` |

工作台页面采用轻量 HTML/CSS/JavaScript，编辑与 Draw 模块采用 React。两部分共享同一套浅色导航、页面入口和后端 API，不需要经过旧版深色 React 首页。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 21、Spring Boot 3.5.16、Spring Security、Flyway |
| 数据与检索 | SQLite WAL、内容寻址 Blob Store、Lucene 10.3.2 |
| 文档处理 | Apache Tika 3.2.3、Apache POI |
| 前端 | React 19、TypeScript、Vite、Semi UI、TipTap、TanStack Query、Zustand |
| 测试 | JUnit 5、AssertJ、jqwik、ArchUnit、Vitest、Testing Library |

后端是包含 26 个模块的 Maven 工程，覆盖领域模型、应用服务、采集连接器、研究与报告引擎、SQLite/Blob/Lucene 基础设施、Spring Boot 服务和验收测试。

## 快速开始

前置环境：JDK 21、Maven 3.9、Node.js 20 和 pnpm。

### 开发模式

启动后端：

```powershell
cd SubtleSight
mvn -q -pl server-app -am package -DskipTests -Dskip.web
java --add-modules jdk.incubator.vector -jar server-app/target/server-app-1.0.0-SNAPSHOT.jar
```

后端地址为 `http://127.0.0.1:8080`。

另开终端启动前端：

```powershell
cd SubtleSight
pnpm --dir web-ui install --frozen-lockfile
pnpm --dir web-ui dev
```

Vite 会把 `/api` 请求代理到本机后端。

### 一键构建并运行

```powershell
cd SubtleSight
.\scripts\run.ps1
```

可用 `-SkipBuild` 跳过已有产物的重新构建。

## 编辑与 Draw

编辑模块将正文和绘图数据统一保存在知识库 SQLite 中：

- 1.2 秒无操作后自动保存，并支持手动保存
- 使用 `expectedVersion` 检测并发编辑冲突
- 每次保存形成版本记录，可查看和恢复历史版本
- 文档可分配到知识库文件夹
- AI 辅助结果可编辑后插入正文
- Draw 支持节点、连线、拖动与属性编辑
- 图形可插入文档，并随正文继续编辑和版本化

主要 API：

```text
/api/v1/knowledge/documents
/api/v1/knowledge/documents/{id}
/api/v1/knowledge/documents/{id}/versions
/api/v1/knowledge/documents/{id}/versions/{version}/restore
/api/v1/knowledge/documents/{id}/ai-assist
```

## 验证

```powershell
cd SubtleSight
.\scripts\verify.ps1
```

前端也可单独执行：

```powershell
pnpm --dir web-ui lint
pnpm --dir web-ui test
pnpm --dir web-ui build
```

## 目录结构

```text
SubtleSight/
├── SubtleSight/              # Maven 多模块工程与前端
│   ├── server-app/           # Spring Boot REST API
│   ├── web-ui/               # 工作台页面、React 编辑器与 Draw
│   ├── core-domain/          # 领域模型
│   ├── core-application/     # 应用服务
│   ├── *-engine/             # 发现、研究、报告等业务引擎
│   ├── storage-*/            # SQLite 与 Blob 存储
│   ├── search-lucene/        # 全文与混合检索
│   ├── docs/                 # 架构和验收文档
│   └── scripts/              # 启动与验证脚本
├── SubtleSight-知识库交互原型.html
└── LICENSE
```

## 许可证

[Apache License 2.0](LICENSE)
