# 02 — 策展：物理主机、容器与「运行于」

**What to build:** 运维可通过控制面 HTTP API 在策展真相中创建物理主机 A/B、Docker 容器 X（写入不可变对象标签约定 `archops.object_id=<容器ID>`），并确认策展事实「X 运行于 A」；随后能按规范问法读出「应该在哪」。人工录入即可（草案可极简为直接确认写入）。从空骨架增补 Flyway 表与 curated 模块，关系语义落 Postgres，不用 Neo4j。

**Blocked by:** 01 — 临时身份头与高级/一般角色门禁

**Status:** ready-for-agent

- [ ] 可创建两台物理主机与一台 Docker 容器，容器带 `archops.object_id` 约定
- [ ] 可写入并读回策展事实：容器 X `运行于` 主机 A
- [ ] 「应该在哪」类读取返回策展值（规范问法纪律）
- [ ] 策展写入经认证；持久化在 PostgreSQL；仅用 additive Flyway
- [ ] 不实现 AI 起草草案全流程；不引入图库
