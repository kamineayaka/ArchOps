---
status: accepted; 取代 ADR-0050 里「修复仍由执行引擎 SSH、Host Agent 只上报」的句子。不改 ADR-0050 的产品范围：第一版仍是虚机上的 HDFS 与 YARN。本 ADR 不改代码
---

# 第一版由 Host Agent 采集并执行修复

云涛 欧已钉死：跑 HDFS 或 YARN 角色的主机，由该主机上的 Host Agent 采集本机角色是否在，并执行修复命令。第一版退出用 SSH 连进主机的执行引擎。

## 决议

1. **采集与修复都在 Host Agent。** 每台跑角色的主机装一个 Agent。它上报本机 NameNode、DataNode、ResourceManager、NodeManager 是否在，并执行控制面发来的一步修复（例如拉起 DataNode、刷新 NodeManager）。
2. **没有 Agent 的主机不能修。** 跑角色的主机都要装。第一版不保留执行引擎 SSH 作为后备。
3. **控制面仍一次只发一步。** Agent 不能自己决定下一步。失败就停。修复结果不写成新的观测；角色是否恢复，看下一次采集。
4. **集群级状态仍由控制面去问。** 容量与副本问 NameNode JMX。队列与计算资源问 ResourceManager REST。这两处不代替各台主机的存活上报。
5. **K8s 上的计算以后另说。** Spark on K8s、Flink on K8s 仍只是边界。到那时用 API Server，不把本 ADR 的每台主机 Agent 套到 Pod 上。

## 不再有效的句子

- ADR-0050 决议 7 里「自愈沿用执行引擎、控制面把步骤代发给执行引擎」的读法。第一版修复步骤发给该主机的 Host Agent。单步、失败即停仍然有效。
- ADR-0044 / ADR-0045 里「改变主机只经执行引擎 SSH」作为第一版 HDFS/YARN 的手段。执行引擎进程可以留下，第一版不调用它去修 HDFS/YARN。

## 明确不改

- 产品仍只运维大数据平台。第一版对象仍只有 HDFS 与 YARN，且不进 K8s（ADR-0050）。
- 不引入原因种类。不记录操作者。
- 本 ADR 不替换占位命令，不删除执行引擎代码，不实现修复。

## 后果

票 01 仍只做集群、角色、主机配置，以及角色不在的告警。修复发到 Host Agent 另票再做。
