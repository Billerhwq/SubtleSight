# SubtleSight

> See the subtle. Know the significant.

SubtleSight 是一个本地优先、Web-first 的单机全能情报工作台。它把来源采集、发现、文档解析、Story 聚合、五视图信号、Deep Research、Evidence、Watchlist、报告导出/发布、Agent、任务恢复和灾备恢复连成一个可审计闭环。项目按《全能情报 Agent-Web 版完整闭环设计与测试验收方案》实现，不以演示数据或内存仓储代替核心链路。

## 技术栈

- Java 21、Spring Boot 3.5、Spring Security、虚拟线程
- SQLite WAL + Flyway，内容寻址 Blob Store
- Lucene 10：BM25、HNSW 向量、RRF 混合检索
- React 19、TypeScript、Semi Design、TanStack Query、Zustand、Vite
- JUnit 5、AssertJ、jqwik、ArchUnit、Vitest、Testing Library

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

## 数据与恢复

运行时目录由进程独占锁保护。管理页可生成 `.insightpack`，包内包含一致性 SQLite 快照、Blob 与 manifest；不包含 API Key/密码。恢复会拒绝路径穿越、格式不兼容和 SHA-256 不匹配，恢复后 Lucene 可由 SQLite/Blob 重建。
