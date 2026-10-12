---
status: accepted; 取代把编排层与控制面做成 HTTP / WebClient 的句子，以及把三进程交付写成镜像清单、把 Kubernetes 留作 Later 的句子。不改 ADR-0045 的控制面→执行引擎 gRPC。不改 ADR-0047 / ADR-0048。不改冲突、空洞、身份失联、未绑定、匿名人审
---

# 三进程在 Kubernetes 上各自部署；编排层与控制面走 gRPC

控制面不拆成更小的服务。控制面、执行引擎、AI 编排层三个进程在 Kubernetes 上各自独立部署。内部跳用 gRPC，因为要高性能。云涛 欧已钉死此决议。编排层的 Deployment 属于合同，但本 ADR 不建造编排层，也不加一个空的占位进程。这里的寻址是 Kubernetes Service 的 DNS 名，不是领域里的「发现」。

## 决议

1. **控制面不拆。** 控制面仍是一个进程，可以多副本。不把它再拆成更小的服务。
2. **三个进程，各自独立部署在 Kubernetes 上：** 控制面、执行引擎、AI 编排层。编排层有自己的 Deployment，这是合同的一部分。本 ADR 不建造编排层，也不加一个空的占位进程。
3. **Host Agent** 留在被观测的主机上，一台主机至多一个（ADR-0048）。它不是集群里用来替换主机上 Agent 的工作负载。
4. **寻址**用 Kubernetes Service 的 DNS 名。配置用 ConfigMap / Secret。这不是冲突的「发现」。
5. 不用 Eureka。不在内部 gRPC 前面放 Spring Cloud Gateway。内部 gRPC 按 gRPC 调用，不转进 HTTP 网关。
6. 这些调用不需要 Spring Cloud Kubernetes 这个库。机制是 Kubernetes Service 加上 ConfigMap / Secret。不采用 Netflix Spring Cloud。
7. **控制面 → 执行引擎**仍是 gRPC（ADR-0045）。不改成 HTTP。
8. **编排层 ↔ 控制面**由 HTTP / WebClient 改为 gRPC。内部跳用 gRPC，是为了高性能。编排层仍不调用执行引擎。执行仍是控制面一次下发一步。
9. 控制面仍不持模型密钥。编排层的建议不是新的冲突状态，也不能定稿策展（ADR-0047）。模型 HTTPS 白名单与密钥仍在编排层（ADR-0041、ADR-0044 决议 6），不改成 gRPC，也不放进控制面的 Secret。

## 不再有效的句子

下列句子被本 ADR 取代。旧 ADR 正文不在本变更里改写；读到它们时，以本 ADR 为准：

- CONTEXT 技术栈里「AI 编排层（WebClient 出站）」把编排层与控制面这一跳写成 WebClient。这一跳改为 gRPC。模型出站仍是编排层上的 HTTPS 白名单，不改成 gRPC。
- CONTEXT 技术栈里「交付 `archops:latest` + 执行引擎 + 编排层 + Postgres + Redis」把三进程的交付目标写成这份镜像清单。三进程改为在 Kubernetes 上各自独立部署。PostgreSQL 与 Redis 仍是数据面（ADR-0043）。
- ADR-0044 后果里「交付形态变为：`archops:latest`（控制面+前端静态）+ **执行引擎** + **AI 编排层** + postgres + redis；Host Agent 仍不进控制面默认 Compose。」三进程的部署目标改为 Kubernetes 上各自的 Deployment。Host Agent 仍留在被观测主机上。四进程切分不改：控制面、执行引擎、AI 编排层、Host Agent。
- ADR-0044 决议 2 的枢纽协议未另写运输。与 ADR-0041 的受控出站、以及把编排层出站做成 WebClient 的读法合在一起，这一跳被当成 HTTP / WebClient。派作业、交回建议或计划、推送逐步详细事件仍然有效；这一跳的运输改为 gRPC。ADR-0044 决议 6（模型 HTTPS 白名单与密钥在编排层、控制面不直连 LLM）仍然有效。
- ADR-0041 里把编排层与控制面这一跳读成与模型出站同一种 HTTP / WebClient。模型 HTTPS 白名单、禁载荷、密钥在编排层、控制面不持模型密钥、不直连 LLM，仍然有效。
- ADR-0043「交付」行，以及后果里的部署形态 `archops:latest` + 执行引擎 + AI 编排层 + Postgres + Redis，作为三进程的交付目标。ADR-0043「编排 | Compose 可作交付底；K8s / Operator **Later**」把 Kubernetes 留作 Later。三进程的部署目标现在是 Kubernetes。Operator 仍 Later。公开 API 仍是 REST `/api/...`。

## 明确不改

- 冲突只在两侧都有当前可用值且不等时成立。空洞 ≠ 冲突。
- 探测任务仍是控制面上的数据；交回仍由控制面判决（ADR-0048）。不改任务规则。
- 身份失联仍是标志。未绑定留在冲突案件之外。
- 没有用户、处理人。人审不记录操作者（ADR-0046）。
- ADR-0045 不改：控制面 → 执行引擎仍是 gRPC + Protobuf；探活仍是 `grpc.health.v1`；执行引擎的客户端证书仍只给控制面。编排层仍不得持有该证书，因为编排层不调用执行引擎。
- 执行仍是控制面单步代发。编排层仍不直连执行引擎，也不直连主机。
- 公开 API 仍是 REST。Host Agent 的心跳与快照仍直连控制面写观测（ADR-0044）。本 ADR 不把这条改成 gRPC，也不把 Agent 放进集群。
- ADR-0047 不改写。建议不是新状态，不能定稿策展。
- 本 ADR 不写 Kubernetes 清单，不改 Compose，不实现代码。

## 考虑过而拒绝的

- 把控制面再拆成更小的服务：枢纽仍是一个进程。
- Eureka，或采用 Netflix Spring Cloud：寻址用 Kubernetes Service 的 DNS 名，配置用 ConfigMap / Secret。
- 以 Spring Cloud Kubernetes 这个库作为这些调用的前提：Service 与 ConfigMap / Secret 已经够用。
- 在内部 gRPC 前面放 Spring Cloud Gateway，或把内部 gRPC 转进 HTTP 网关：内部跳要的是 gRPC 的性能，不绕回 HTTP。
- 把控制面 → 执行引擎改回 HTTP：ADR-0045 已钉死 gRPC。
- 编排层与控制面继续用 HTTP / WebClient：内部跳改为 gRPC。
- 编排层直接调用执行引擎：执行引擎仍只有控制面这一个调用方。
- 用集群里的工作负载替换主机上的 Host Agent：探测留在被观测主机上，一台至多一个。
- 本 ADR 里建造编排层，或先放一个空的占位进程：Deployment 属于合同，进程本身另做。
- 把模型出站从 HTTPS 改成 gRPC：白名单外部 API 仍是 HTTPS，密钥仍不在控制面。

## 后果

以后的部署按此合同：三个进程各自一个 Deployment，用 Service DNS 名互相寻址，用 ConfigMap / Secret 拿配置。本 ADR 不提供清单、迁移或工单。CONTEXT 只替换本 ADR 取代的句子，并指向本 ADR。
