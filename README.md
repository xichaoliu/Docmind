# DocMind · 基于 RAG 的文档知识库问答

上传文档，针对文档内容提问，回答**流式输出**并附带**引用来源**，支持多轮对话、按文档过滤检索。


![DocMind 演示](docs/sample.gif)

## 功能

- 📄 文档上传与管理，支持 PDF / Word / TXT / Markdown，异步解析入库，实时状态轮询
- 💬 流式问答，打字机效果 + 引用来源展示（精确到文档与分块）
- 🔄 多轮对话，支持指代消解（"它有什么优点？"会被自动改写为完整问题再检索）
- 🎯 支持限定检索范围（只针对选中的某几个文档提问）
- 🔐 用户注册登录，JWT 鉴权，业务数据按用户隔离
- 🐳 Docker Compose 一键部署

## 架构

```
┌─────────────┐      SSE/REST      ┌──────────────────┐      REST/SSE      ┌──────────────────┐
│   React     │ ──────────────────▶│   Java Gateway    │ ──────────────────▶│  Python RAG       │
│  (Vite+TS)  │                     │  (Spring Boot)     │                     │   (FastAPI)       │
└─────────────┘                     └──────────────────┘                     └──────────────────┘
                                             │                                         │
                                             ▼                                         ▼
                                      ┌─────────────┐                         ┌──────────────┐
                                      │ PostgreSQL   │                         │ FAISS + Ollama │
                                      │ (业务数据)    │                         │ (向量检索+生成) │
                                      └─────────────┘                         └──────────────┘
```

**职责划分**：
- **React**：界面展示、SSE 流解析、状态管理
- **Java Gateway**：用户鉴权、会话与消息持久化、SSE 透传、业务编排，不做任何 AI 相关计算
- **Python RAG Service**：文档解析、向量检索、Prompt 编排、调用大模型生成回答

三层之间互不越权——Java 不理解 RAG 的具体实现，Python 不关心用户是谁，各自只做自己该做的事。

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | React 18、TypeScript、Vite、Ant Design、TanStack Query、Zustand、React Router |
| 业务网关 | Java 21、Spring Boot 3.5.6、MyBatis-Plus、PostgreSQL、WebFlux(WebClient) |
| AI 服务 | Python、FastAPI、LangChain、FAISS、Ollama |
| 部署 | Docker、Docker Compose |

## 核心技术点

### 1. RAG 检索编排

检索（向量相似度召回相关片段）→ 拼装（按模板组织资料与规则）→ 生成（模型基于资料流式作答），三步串联，模型被严格约束"只使用资料中的信息"，资料不足时明确回答"资料未提供相关信息"而非编造。多轮对话场景下，追问会先经过一次低温度的改写（condense question），把"它有什么优点？"这类指代消解为可独立检索的完整问题。

详见 [`docs/rag-concepts.md`](docs/rag-concepts.md)。

### 2. SSE 流式问答与透传

后端以 `token` / `citations` / `done` / `error` 四类事件推送，Java 网关原样透传 Python 生成的 SSE 流给前端，同时旁路累积完整回答与引用来源，流结束后落库，前端无感知网关的存在。接口契约见 [`docs/api-chat-stream.md`](docs/api-chat-stream.md)。

### 3. JWT 鉴权与用户数据隔离

采用 `HandlerInterceptor`（而非 Servlet Filter）实现鉴权拦截，所有业务查询强制带 `user_id` 条件，删除/更新操作额外做归属校验，防止越权访问。设计权衡见 [`docs/auth-design.md`](docs/auth-design.md)。

### 4. PostgreSQL JSONB 类型映射

`citations`（引用来源）以 JSONB 存储，自定义 `PostgresJsonbTypeHandler` 处理 Java 对象与 jsonb 列之间的序列化，并通过集成测试验证 MyBatis-Plus 查询路径（而非仅插入路径）正确调用该 TypeHandler，防止回归。

## 目录结构

```
docmind-rag/
├── web/              # React 前端
├── gateway/           # Java 业务网关
├── rag/               # Python RAG 服务
├── docs/              # 技术文档（决策记录、接口契约、踩坑笔记）
├── scripts/           # 项目管理 / 批量操作脚本
└── docker-compose.yml
```

## 本地运行

### 前置条件

- Docker / Docker Compose
- 本地已安装并启动 [Ollama](https://ollama.com)（Compose 里没包含，走 host.docker.internal 访问），拉取好所需模型：
  ```bash
  ollama pull qwen2.5:latest
  ```
- 本地已下载向量模型
### 启动

```bash
git clone https://github.com/xichaoliu/docmind-rag.git
cd docmind-rag

cp .env.example .env   # 按需修改 JWT_SECRET 等配置

docker compose up --build
```

访问 [http://localhost](http://localhost)。

首次启动会自动初始化数据库表结构。前端注册一个账号即可开始使用：上传文档 → 等待状态变为"已就绪" → 开始提问。

### 分服务调试

```bash
# 只起数据库，确认建表
docker compose up postgres
docker compose exec postgres psql -U postgres -d docmind -c "\dt"

# Python 服务单独验证
docker compose up rag-service
curl -N -X POST http://localhost:8000/internal/chat/stream \
  -H "Content-Type: application/json" -d '{"question": "测试"}'

# Java 网关单独验证（需要 postgres 和 rag-service 已启动）
docker compose up gateway
curl -N -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" -d '{"question": "测试"}'
```

## 测试

```bash
# Python
cd rag && pytest

# Java（含集成测试，需要 Docker 支持 Testcontainers）
cd gateway && ./mvnw test

# 前端
cd web && npm test
```

测试覆盖的关键场景包括：多轮对话指代消解的正确性与降级处理、JSONB 字段的 MyBatis-Plus 查询映射回归、用户数据越权访问拦截、JWT 篡改校验、SSE 流解析的网络分包处理、流式新建会话时的状态一致性边界。

## 已知限制

- 本地大模型并发能力有限，多用户同时提问会相互影响响应速度
- FAISS 为单机内存索引，未做分布式扩展，不适合超大规模文档量
- PDF 扫描件（图片型 PDF）无法提取文字
- 服务间调用（Java → Python）暂未加内网鉴权，依赖网络层隔离（Python 服务不对外暴露端口）
- 未做 CI/CD、监控告警、多租户权限体系，面向个人/小规模使用场景，非生产就绪

## 文档

更多设计细节与踩坑记录见 [`docs/`](docs/) 目录：

- [`tech-decisions.md`](docs/tech-decisions.md) — 技术选型与权衡依据
- [`api-chat-stream.md`](docs/api-chat-stream.md) — SSE 接口契约
- [`rag-concepts.md`](docs/rag-concepts.md) — RAG 检索编排原理与局限性
- [`auth-design.md`](docs/auth-design.md) — 鉴权方案设计
- [`docker-troubleshooting.md`](docs/docker-troubleshooting.md) — 容器化部署踩坑记录
- [`gh-commands.md`](docs/gh-commands.md) — 项目管理命令速查

## License

MIT

## 致谢1

本项目一期的设计、决策与最终实现均由我本人完成，但整个开发过程中持续使用了 Claude（Anthropic）作为编程辅助工具，在此如实说明其在各阶段中扮演的角色，供阅读本仓库的人参考。

阶段 0-1：学习路线与 Python RAG 服务

从零开始规划"技术栈重建"路线时，Claude 帮助梳理了 Vue 转 React 的概念对照、各技术选型（Zustand/TanStack Query 的分工、PostgreSQL vs MySQL、MyBatis-Plus vs JPA）的权衡依据，以及 RAG 检索编排（检索→拼装→生成）的原理讲解。这一阶段 Claude 更多承担"讲明白概念"的角色，具体的代码重构、环境调试仍由我本人动手完成并反馈报错信息迭代解决。

阶段 2-3：前端界面与 Java 网关

前端的页面骨架、路由守卫、useChatStream 等 Hook 的初始实现由 Claude 生成代码起点，我在此基础上接入真实接口、调整交互逻辑、排查状态管理边界问题（如流式新建会话时消息被误清空的时序 bug）。Java 网关搭建阶段，从 Spring Boot 版本选型、MyBatis-Plus 代码生成，到 SSE 透传与 JSONB 类型映射的疑难问题排查，过程中出现的全部报错日志均由我实际运行、截图或粘贴给 Claude，由其辅助分析根因、给出修复方案，再由我验证、提交。其中 autoResultMap 这个较隐蔽的坑，最终定位依据来自我查阅资料后提出的猜测，Claude 协助核实并给出了标准修复方式。

阶段 4-5：文档管理与容器化部署

文档上传、级联删除、路径穿越防护等功能设计由 Claude 提供方案建议，具体实现、联调、异常处理由我完成。Docker 化部署过程中遇到的十余个问题（镜像拉取超时、跨平台依赖冲突、JSONB TypeHandler 查询失效、模型文件挂载缺失等），均是我本人执行命令、观察报错、提供日志，与 Claude 共同排查后解决，排错记录详见 docs/docker-troubleshooting.md。

关于项目归属的说明

Claude 没有自主访问过我的代码仓库、数据库或运行环境，所有代码的落地、测试、提交均由我本人在本地完成。本项目中的架构设计决策（如三层服务的职责划分）、技术选型取舍、以及绝大多数实际调试工作，均基于我自己的判断和操作完成；Claude 的作用更接近于一位可以随时提问的技术顾问与结对编程伙伴——提供知识背景、代码范例与问题排查思路，但不替代理解和决策本身。

我认为如实记录这一过程本身也是这个项目的一部分：它展示的不仅是最终代码，也包括一名从空窗期重新进入技术领域的开发者，如何借助 AI 工具高效学习、独立排查问题、并最终对所写代码建立真正理解的过程。

使用的 AI 工具：Claude（Anthropic），通过 claude.ai 网页对话完成全部沟通，未使用 Claude Code 或其他自动化编码代理，所有代码的运行、测试环节均由本人手动执行。

## 致谢2

二期开发与工程治理阶段，我同时使用了 DeepSeek（通过 DeepSeek Harness 本地客户端）作为主力协作工具。

需要先说明的一点差异：与致谢1 阶段的纯对话式使用不同，这个阶段的 AI 具备实际的文件读取、命令执行与网络查询能力。它不只是"提供建议"，而是参与到了"执行验证"这一环，因此分工边界需要更明确地界定。

问题诊断

二期的工作重心从"扩展功能"转向"收敛质量"。暴露出的问题集中在几类容易被忽略的语义边界上，AI 负责设计排查路径与执行交叉验证，我负责确认结论、修改代码并提交：

入库链路的状态一致性 —— 超时语义被误用为业务失败判据，使外部状态与本地记录产生不可回收的偏差
权限过滤的边界语义 —— 空集合与"不限制"在实现层被混同，形成越权读取面
检索参数与召回完整性 —— 默认参数在小占比数据上使召回归零，而下游仍按"已召回"继续执行
异步执行语义 —— 注解未生效，以及嵌套标注导致状态机提前流转
跨异步边界的上下文传播 —— 以线程本地存储承载请求级身份，在异步链路中失效

工具与脚本生成

多源数据一致性对账工具 —— 以业务数据为唯一事实源，比对三处存储的差异；默认只读，清理动作限定在磁盘侧，且在取不到事实源时强制中止以避免误删
检索效果评测体系 —— 分层评测用例与自动化跑分脚本，用于量化对比不同检索策略。实测问题改写策略使检索命中率从 17% 提升至 67%；其中对照组的设计暴露了纯语义检索在精确匹配场景下的固有短板，为引入混合检索提供了数据依据

关于边界的说明

这个阶段中 AI 实际执行了仓库读取、命令运行与脚本编写，但不参与任何方向决策。做什么、先做哪个，都是我自己判断的。所有代码修改、环境操作与提交均由我在本地完成；AI 给出的诊断结论，也由我运行验证后才落地。

一点观察

致谢1 里我写的是"借助 AI 高效学习"，这个阶段更像"借助 AI 做交叉验证"——它能在一分钟内跑完我手工要花半小时的对比实验，也能在我提出猜测后去查证确认。但猜测仍得由我提出：那些问题都是我在实际使用中撞到的，AI 的价值在于把"我怀疑是这个原因"变成"是这个原因，证据在这里"。

使用的 AI 工具：DeepSeek，通过 DeepSeek Harness 本地客户端完成，具备文件读写与命令执行能力；所有代码修改、环境操作与提交均由本人执行。

## 作者按

以上内容均由AI生成。
