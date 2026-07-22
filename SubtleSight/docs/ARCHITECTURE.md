# SubtleSight 架构说明

## 边界

```mermaid
flowchart LR
  UI["React Web 工作台"] --> API["Spring REST / SSE"]
  API --> APP["Application / Workflow"]
  APP --> SRC["Connectors + Discovery"]
  APP --> DOC["Document + Story + Signal"]
  APP --> RES["Research + Evidence"]
  APP --> OUT["Watchlist + Report + Agent"]
  APP --> JOB["Durable Job Runtime"]
  SRC --> PORTS["Core Ports"]
  DOC --> PORTS
  RES --> PORTS
  OUT --> PORTS
  JOB --> SQLITE["SQLite WAL"]
  PORTS --> SQLITE
  PORTS --> BLOB["Content-addressed Blob"]
  PORTS --> LUCENE["Lucene BM25 / HNSW / RRF"]
```

`core-domain` 只放稳定模型、哈希/URL 规范化、预算与状态机；`core-application` 只定义端口和用例编排。存储、搜索、Provider、Web 和 Spring 都在外层模块。`server-app` 是唯一装配根。

## 主链路

```mermaid
sequenceDiagram
  participant U as 用户/Web
  participant W as Workflow
  participant B as Blob/SQLite
  participant S as Story/Signal
  participant J as Job Runtime
  participant R as Research/Evidence
  participant P as Report/Publisher
  U->>W: 注册来源/上传/采集
  W->>B: 原文 SHA-256 + 文档版本
  W->>S: 聚类、实体主题、五视图投影
  U->>J: 发起研究（预算与 dedup key）
  J->>R: lease + heartbeat + 状态机
  R->>B: Claim、Evidence、Locator、checkpoint
  U->>P: 生成/导出报告
  P->>B: 引用门禁、版本、发布幂等记录
  P-->>U: Markdown/HTML/JSON/PDF 或发布回执
```

## 可靠性约束

- Job 状态转换由 Java 状态机控制；同一 dedup key 不重复入队，过期 lease 可回收，重试采用有界指数退避。
- Research 的模型输出不能控制状态迁移和预算；每阶段持久化 checkpoint，终态可重复读取。
- 原文先写临时文件、fsync、原子移动，再提交元数据。原文和证据均带 SHA-256。
- Story 聚类区分 exact duplicate、same story 和 new story；人工覆盖不被自动流程回退。
- 发布必须明确确认，且关键 Claim 必须有可定位证据；远端不确定时进入 reconcile 而非盲重发。
- `.insightpack` 使用一致性 SQLite 快照、逐项校验和；恢复时验证格式、密钥标记、zip-slip 和内容哈希。

## 安全边界

- 默认仅监听 `127.0.0.1`，采用本机直达模式，不再要求登录。
- 所有写请求同时校验 CSRF token 与 Origin；Web 工作台启动时先取得 CSRF Cookie。
- 外部 URL 逐跳做 SSRF 检查，拒绝 loopback、私网、链路本地和非 HTTP(S) 地址，并限制重定向、超时和响应体。
- Agent 只能调用注册工具；发布属于高风险动作，不能由对话绕过确认；检测到提示注入时阻断工具执行。
- 日志脱敏常见 Key、Bearer、密码字段；备份 manifest 明确 `secretsIncluded=false`。
