---
status: "现行产品。HDFS 与 YARN 的集群、角色、主机、角色存活告警，以及 Host Agent 上的一步修复。不含对象存储与 K8s 计算。"
---

# Spec: 大数据平台第一版（HDFS 与 YARN）

**Basis**: [`docs/adr/0050-big-data-platform-ops.md`](../adr/0050-big-data-platform-ops.md)、[`docs/adr/0051-host-agent-collects-and-repairs.md`](../adr/0051-host-agent-collects-and-repairs.md)、[`docs/adr/0052-python-control-plane-go-agent.md`](../adr/0052-python-control-plane-go-agent.md)。判断机制已取消。控制面是 `control-plane/` 的 Python 服务，Agent 是 `host-agent/` 的 Go 进程。不改 `backend/`。  
**测试**: 控制面 HTTP API，以及 `host-agent` 的 `go test`。

## 产品

第一版只运维虚机上的 HDFS 与 YARN。人登记集群、主机和服务角色，并写下配置。Host Agent 采集角色是否在。角色不在则告警。控制面一次发一步修复，Agent 执行。失败即停。角色恢复只靠下一次采集。

## 行为

1. 人可以登记一个大数据集群，并在其下登记 HDFS 或 YARN 的服务角色，把角色放到一台主机上。角色只允许 NameNode、DataNode、ResourceManager、NodeManager。一台主机可以有多个角色。
2. 资源配置在同一条记录上：HDFS 角色带副本数与容量；YARN 角色带队列名、内存与 vcore。这是人写下的配置。
3. Go Host Agent 交回该主机各角色是否在。控制面不 SSH。没有角色存活字段的上报不开告警。
4. 角色不在，控制面记下当前告警，证据是采集到的原始结果。角色在，同一条告警结束。没有原因种类，不记录操作者。
5. 对一条当前告警，控制面冻结一份脚本，并只把当前这一步的命令交给该主机的 Agent。NameNode、DataNode、ResourceManager 的脚本是拉起对应进程。NodeManager 先拉起进程，这一步成功之后才发出刷新 YARN 队列。
6. Agent 执行收到的这一步，把退出码和输出交回，然后停止。它不自己选下一步，也不在同一次运行里再要下一步。
7. 退出码不是 0 就停。未发出的后续命令不再发出，同一步也不改成成功再跑。
8. 修复输出记在这一步上。它不写入告警证据。修复成功也不结束告警。
9. 下一次采集看到该角色在，对应告警结束，证据仍是当初的采集结果。

## 不做

- 容量耗尽、节点丢失。
- 对象存储，Spark on K8s，Flink on K8s。
- 删除 `backend/` 里已有的冲突、草案、探测接口。
- 原因种类、处理人、已知悉、确认关闭。
- Kubernetes 清单、编排层进程、模型建议正文。
- 用执行引擎 SSH 修 HDFS 或 YARN。
- 前端页面。
