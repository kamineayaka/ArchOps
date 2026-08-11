# 实现策略：全量推倒（K1），不以旧域模型为骨架

采用全量推倒：不以现有 ArchOps 业务模块（旧图 SSOT/架构提案/旧 Agent 流水线等）为演进骨架，按冻结合同（ADR-0039）重新实现领域核心。旧代码与文档仅作参考（SSH 池、凭证加密、Compose 交付等思路可借鉴），禁止把旧「提案合并」「单轨图库存」等语义偷偷迁入新实现。推荐仍使用成熟技术栈以降低绿场成本的表述已由 **ADR-0043** 正式选型取代（Java 21 + Spring Boot + Gradle、Vue 3、PostgreSQL、Python Agent、Compose 单镜像 `archops:latest`；v1 不引入 Neo4j/Redis）。模块边界与数据模型从合同重开，不复用旧包结构与旧 Flyway 语义链作为领域真相。竖切 MVP 见后续实现计划；未纳入竖切的能力不得借旧模块「顺便保留」。
