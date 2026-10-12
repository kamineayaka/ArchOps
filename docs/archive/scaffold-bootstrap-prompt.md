# 新对话：按 ADR-0043 搭建绿场脚手架（Prompt）

将下面整段复制到**新对话**作为第一条用户消息即可。

---

```text
你是 ArchOps 的实现助手。本仓库领域合同与技术栈已冻结；选型前脚手架已清空。本对话只做「空脚手架 + 可启动的最小交付骨架」，不做竖切业务（策展/冲突/计划 API 等）。

## 必读（先读再改）

1. CONTEXT.md
2. docs/adr/0039-domain-contract-frozen.md
3. docs/adr/0040-greenfield-rewrite-k1.md
4. docs/adr/0042-same-repo-wipe-and-rewrite.md
5. docs/adr/0043-tech-stack.md          ← 技术栈唯一真相
6. docs/adr/0041-ai-egress-controlled-external-api.md
7. docs/mvp-vertical-slice.md           ← 只作范围边界参考，本对话不实现竖切故事
8. docs/dev-handoff.md
9. .cursor/rules/project-map.mdc

质量优先；有歧义先问再写。规则与代码冲突时以 ADR-0043 为准。

## 本对话目标（脚手架完成定义）

在空仓上创建：

1. backend/ — Java 21 + Spring Boot 3 + Gradle (Kotlin DSL) + MyBatis-Plus + Flyway
   - 仅：可启动的应用入口、健康检查 GET /api/health、统一 ApiResponse / BusinessException、Security 放行健康检查
   - application.yml：Postgres + Redis 连接配置（环境变量）
   - 空的包结构占位即可：com.archops.{common,curated,observed,conflict,plan,user,agent}（不要写业务 Service）
   - Flyway：仅最小 V1（例如 schema 占位或 app_meta），不要提前把竖切全表写完，除非你认为「空库可飞」必须有；表结构留给竖切对话
2. frontend/ — React + TypeScript + Vite + Ant Design
   - 最小页面：标题 ArchOps + 调用 /api/health 展示结果
   - 开发代理 /api → :8080
3. agent/ — Python 3.12+ 心跳 stub（README 说明 systemd 安装为交付主推；本对话给可本地跑的脚本即可）
4. deploy/ — Compose：archops 服务（image: archops:latest）+ postgres:16 + redis；设计前提多副本（compose 可先 replicas 或注释说明）
5. 仓库根 Dockerfile — 多阶段：构建前端静态 → 打进 Spring 静态资源 → bootJar → 运行镜像 tag 默认 archops:latest
6. deploy/scripts/build-images.sh — 构建 archops:latest
7. 更新 README.md 启动方式；更新 docs/dev-handoff.md「脚手架已按 0043 重建」

## 硬约束（Forbid / Later）

- 不要 Maven；不要 JPA 当地基；不要 Vue/Naive 当前端
- 不要引入 Neo4j（图库 Later）；v1 图语义以后落 PG
- Redis 要配置进应用与 Compose，但不要把 Redis 当关系真相 SSOT
- 不要 LangChain；AI/SSH 业务本对话不做（可在 build.gradle 预留依赖注释，或暂不引 MINA，竖切再加——二选一先问我）
- 不要实现策展/观测/冲突/计划/协作 API
- 不要复活旧 com.archops 业务模块语义
- 禁止修改已有 docs/adr 历史文件的语义；若脚手架细节需记，可新增 ADR 或只改 README/handoff

## 验收

- ./gradlew bootRun（或文档等价命令）+ Compose 起 postgres/redis 后，GET /api/health 成功
- npm run dev 能打开薄页并看到 health
- bash deploy/scripts/build-images.sh 能产出 archops:latest（若本机无 Docker，写明未验证但文件齐套）
- 仓库中无 Vue 脚手架、无 JPA 实体业务码

开始前用几句话复述你将创建的目录与不做清单，我确认后你再动手。
```

---

使用说明：新对话粘贴后，等助手复述目录与不做清单，你回复「确认，开干」再让它写文件。
