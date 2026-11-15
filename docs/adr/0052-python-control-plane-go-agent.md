---
status: accepted; 取代 ADR-0043 里「控制面 Java / Gradle / Spring Boot」和「Host Agent Python」作为现行栈的句子。不改 ADR-0050 的产品范围，不改 ADR-0051 的采集与修复都在 Host Agent。本 ADR 不改代码
---

# 控制面用 Python；Host Agent 用 Go

云涛 欧已钉死：Java 对这个中后台过重，控制面改为 Python。Host Agent 改为 Go，因为每台主机一个常驻进程，要单二进制、占内存小。现有 Java 与 Python Agent 留到按新栈重写，本 ADR 不删除它们。

## 决议

1. **控制面是 Python。** HTTP API 用 FastAPI。数据用 PostgreSQL。Redis 仍只做锁、缓存、队列，不当真相。
2. **Host Agent 是 Go。** 每台跑 HDFS 或 YARN 角色的主机一个进程。它采集本机角色是否在，并执行控制面一次发来的一步修复（ADR-0051）。
3. **前端仍是 React。** 不改成别的。
4. **AI 编排层仍是单独进程。** 与控制面是仅有的进程间边界。Host Agent 在主机上，不是微服务。编排层语言本 ADR 不定。
5. **第一版不部署执行引擎。** 修复不走 SSH。执行引擎代码可以留下，直到重写时再删。

## 不再有效的句子

- ADR-0043 控制面必须用 Java 21、Spring Boot、Gradle、MyBatis-Plus。现行控制面栈改为 Python 与 FastAPI。
- ADR-0043 Host Agent 必须用 Python 3.12。现行 Agent 改为 Go。
- 「禁止换栈」读成永远留在 Java。栈以本 ADR 为准。

## 明确不改

- 产品仍只运维大数据平台。第一版仍是虚机上的 HDFS 与 YARN（ADR-0050）。
- 采集与修复仍都在 Host Agent，控制面一次只发一步（ADR-0051）。
- 公开 API 仍是 REST `/api/...`。
- 本 ADR 不重写控制面，不重写 Agent，不删 Java 代码。

## 考虑过而拒绝的

- 控制面继续用 Java：对这个中后台过重。
- 控制面也用 Go：管理接口用 Python 写得更快，这里看不出性能差。
- Agent 留在 Python：常驻进程要更小的内存和单二进制。
- Agent 用 Rust：更小，但没必要。

## 后果

票 01 仍是现有 Java 仓库里的配置与告警，还没开始。重写按本 ADR 另开，不在本 ADR 里动代码。
