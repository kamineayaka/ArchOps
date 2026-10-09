# Spec: 冲突主线去掉协作身份

**Status**: spec published；工单 01 **done**；frontier = 工单 02  
**Basis**: ADR-0046（已接受）；`CONTEXT.md` 中被 ADR-0046 取代的协作身份句。不改冲突成立条件，不改 ADR-0043 / 0044 / 0045。  
**Testing seams (confirmed)**: **唯一验收主接缝 = 控制面公开 HTTP API**（含 Agent 心跳/快照 ingest）。`/implement` 按 [`docs/agents/tdd.md`](../agents/tdd.md) 走 **red → green → refactor**。薄 UI 只在该票 HTTP 已绿且票面要求时做。

## Problem

领域已无用户、协作身份上的角色、归属、已知悉、自任、指派、接受、处理人。主线只剩发现与解决。现码仍用已接受处理人门禁挡住诊断选支、批准并冻结、草案逐条确认、确认关闭，并把操作者标识写成这些动作上的领域事实。

## Solution

按 ADR-0046 的后果改 HTTP 行为，一张票一条可单独验收的竖切。发现自动发生，定义不变。四类人审动作保留，且在没有用户身份、没有处理人门禁时仍可完成；人审只表示动作被显式请求。这些动作不记录操作者。登录若仍存在，留在领域之外，不得写入冲突、操作计划、改策展草案、诊断选支或确认关闭。

## Tickets

1. **诊断选支**不经身份、不记操作者。修实际仍出待审操作计划；改理想仍出开放草案且不出计划。身份失联仍挡住这两支。诊断未就绪或过时仍拒绝。
2. **批准并冻结**不经身份、不记操作者。未批准不得执行。
3. **改策展草案逐条确认**（冲突上的改理想，以及未绑定草案的逐条接受/拒绝）不经身份、不记操作者。接受仍立即写策展并比对；未相等不得自动关闭。
4. **两侧相等后确认关闭**不经身份、不记操作者。未相等不得关闭。
5. **执行**已批准计划不再询问处理人，完成事件不记操作者。失败仍作废；不改步重试。
6. 已知悉、归属、自任、指派、接受、拒绝、转让以及「开计划」身份门禁退出领域。冲突读模型不再把它们当领域事实。
7. 薄 UI：上述动作不再展示处理人门禁。

## Out of Scope

- 解决断点：ABSENT 分叉开计划、占位 SSH 命令换成真命令、已完成计划写入观测
- 编排层、B-live、连接工作台、未绑定 10、执行引擎票 02
- 改冲突成立条件；把空洞、未绑定、身份失联折进冲突状态机
- 新 ADR；改 ADR-0043 / 0044 / 0045 正文；把 K8s 节点职责「角色」删掉
- Maven、JPA 当地基、Vue、Neo4j、LangChain；Redis 当真相

## Further Notes

- **Issue tracker**: [`.scratch/conflict-without-identity/issues/`](../../.scratch/conflict-without-identity/issues/)
- **Contract**: [`docs/adr/0046-conflict-discovery-and-resolution-without-identity.md`](../adr/0046-conflict-discovery-and-resolution-without-identity.md)
