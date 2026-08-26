---
status: accepted; 服务拆分、SSH 进程位置、AI 出站落点由 ADR-0044 修订
---

# 技术选型（完整选型会冻结）

在 ADR-0039 领域合同冻结与 ADR-0040/0042 绿场重写前提下，经**完整技术选型会**重新选定实现栈。本 ADR **取代**此前草案表述（含「v1 不上 Redis」「前端 Vue」等）。选型服务冻结合同与隔离区镜像交付；**不**改领域语义。

「一图双轨」是领域模型，**不**强制第一天使用图数据库。目标架构可含图库；v1 交付受运维约束可先 PG only。

## 决议

| 层 | 选型 |
|---|---|
| 控制面 | Java 21 + Spring Boot 3 + **Gradle（Kotlin DSL）** |
| 持久化 | **MyBatis-Plus 为主**（显式 SQL；双轨比对、邻域 CTE、计划 JSON 等） |
| API | REST `/api/...` + 统一响应体；认证可先临时头，后 JWT |
| 前端 | **React + TypeScript + Ant Design**；生产**同镜像内嵌**静态资源 |
| 数据面（真相） | **PostgreSQL 16**（策展/观测/冲突/计划/用户等 SSOT） |
| 图存储 | **目标**：PG + 专用图库（如 Neo4j）（D2）。**v1**：仅 Postgres，图语义用边表 + 有限深度 CTE；图库 **Later** |
| Redis | **v1 必选**：队列、分布式锁、会话、缓存。控制面 **多副本**。Redis **不是**关系真相 SSOT |
| Host Agent | **Python 3.12+**；交付主推 **systemd 安装（S1）**；可选 agent 镜像 Later；源码手工跑仅开发 |
| SSH | **Apache MINA SSHD**（ADR-0044：**执行引擎进程**内连接池）；计划互斥靠 Redis 锁，由控制面在代发前加；禁止旁路直连 |
| AI 出站 | Spring **WebClient** 调白名单外部 API（ADR-0041）；落点在 **AI 编排层**（ADR-0044）；控制面不持模型密钥；**禁止** LangChain 类重型编排框架 |
| 交付 | `archops:latest`（API + 前端静态）多副本 + **执行引擎** + **AI 编排层** + `postgres` + `redis`（ADR-0044）；Agent 不进控制面默认 Compose |
| 入口 | v1 可用编排 LB / 宿主机端口；反向代理与统一 TLS **Later** |
| 服务拆分 | **ADR-0044**：执行引擎与 AI 编排层独立进程；控制面仍可多副本。其余 worker 拆分仍 Later |
| 编排 | Compose 可作交付底；K8s / Operator **Later**（非 v1 前提） |

## 三栏：V1 / Later / Forbid

### V1（本版就要）

- PostgreSQL 真相与协作库  
- Redis（队列 / 锁 / 会话 / 缓存）与控制面多副本  
- MyBatis-Plus、Gradle、MINA SSHD（执行引擎）、WebClient（编排层出站）  
- React + Ant Design（同镜像静态）  
- Python Agent + systemd 主推交付  

### Later（允许，须新 ADR，不得静默加）

- 专用图库（Neo4j 等）与拓扑查询迁出/同步  
- 可选 `archops-agent` 镜像  
- 控制面再拆其它 worker（诊断编排层与执行引擎已由 ADR-0044 拆出）  
- 前端独立镜像、反向代理 / 统一 TLS  
- K8s Operator、指标告警大盘、AI 自我迭代落地（领域已标 Later）  

### Forbid（可预见期内不做 / 禁止）

- 用 Maven 取代已定 Gradle  
- LangChain 类重型框架作诊断编排主干  
- 将 Redis 当作关系真相 SSOT  
- 用工作台或旁路绕过操作计划冻结（合同禁区）  

## 后果

- 文档与规则以本 ADR 为准。选型前 JPA/Vue 等脚手架已删除；实现须按本 ADR 重建，不得当作「已有代码即栈」。  
- 部署形态：`image: archops:latest`（可带仓库前缀）+ 执行引擎 + AI 编排层 + Postgres + Redis；控制面副本数 ≥ 2 为设计前提。见 ADR-0044。  
- 图库未引入前，禁止在代码中写死 Neo4j 为必选依赖。
