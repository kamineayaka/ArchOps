---
status: "票 01 ready-for-agent。只做集群/角色/主机配置，以及 HDFS/YARN 角色是否在的故障与告警。不含自愈换命令，不含对象存储与 K8s 计算。"
---

# Spec: 大数据平台第一版（HDFS 与 YARN）

**Status**: 工单 01 **ready-for-agent**。  
**Basis**: [`docs/adr/0050-big-data-platform-ops.md`](../adr/0050-big-data-platform-ops.md)、[`docs/adr/0051-host-agent-collects-and-repairs.md`](../adr/0051-host-agent-collects-and-repairs.md)、[`docs/adr/0052-python-control-plane-go-agent.md`](../adr/0052-python-control-plane-go-agent.md)。判断机制已取消。控制面是新的 Python 服务，Agent 是 Go。不改 `backend/`。  
**Testing seams (confirmed)**: 新 Python 控制面的 HTTP API。`/implement` 走 [`docs/agents/tdd.md`](../agents/tdd.md)，red → green → refactor。前端不在本票。修复不在本票。

## Problem Statement

产品主线已改为只运维大数据平台。仓库里还没有集群、服务角色、主机这三层，也没有「角色不在则告警」。继续加通用公民或主机之间可达会漂移。

## Solution

1. 人可以登记一个大数据集群，并在其下登记 HDFS 或 YARN 的服务角色，把角色放到一台主机上。角色只允许 NameNode、DataNode、ResourceManager、NodeManager。一台主机可以有多个角色。
2. 资源配置在同一条记录上：HDFS 角色带副本数与容量；YARN 角色带队列名、内存与 vcore。这是人写下的配置，不是两轨对账。
3. Go Host Agent 交回该主机各角色是否在。控制面不 SSH。Agent 不判决。本票不发修复命令。
4. 角色不在，控制面记下当前故障，并给出告警。角色恢复在，告警结束。故障只保留采集到的原始证据，没有原因种类。
5. 告警不记录操作者，不要求确认关闭。

## User Stories

1. 人登记集群、主机，以及该主机上的 NameNode 或 NodeManager，并能读回角色、副本或队列配置。
2. 登记 DataNode、ResourceManager 以外的角色名被拒绝。
3. 心跳报告某角色不在：出现一条当前告警，证据是交回的原始结果。
4. 随后心跳报告该角色在：同一条告警结束。
5. 同一主机上另一个角色的在与不在，不改第一条告警。
6. 请求不带用户身份，响应不记录操作者。

## Out of Scope

- 修复命令。采集与修复都在 Host Agent（ADR-0051），修复另票再做。
- 容量耗尽、节点丢失。本票的故障只有「角色不在」。ADR-0050 仍把二者留在故障发现的定义里，另票再做。
- 对象存储，Spark on K8s，Flink on K8s。
- 删除现有冲突、草案、探测接口。
- 原因种类、处理人、已知悉、确认关闭。
- Kubernetes 清单、编排层进程、模型建议正文。
- 前端页面。

## Implementation targets

- 新的 Python 控制面（FastAPI + PostgreSQL）：登记与读回集群、主机、角色配置；接收角色存活；读当前告警。
- 新的 PostgreSQL 迁移。不改 `backend/` 里已有的 Flyway。
- Go Host Agent 上报本机角色是否在。不上报时不制造告警。
