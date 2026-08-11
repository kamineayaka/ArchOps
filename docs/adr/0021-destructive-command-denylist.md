---
status: superseded by ADR-0036
---

# 破坏性命令用高危名单；敏感读拒绝与写重审批分列

（业务库 DROP 已改为拒绝；原则与图结构路由见 ADR-0036。本 ADR 中「DROP TABLE orders 走重审批」作废。）

工作台 v1 以高危模式名单判定破坏性命令：命中则重审批，未命中可轻确认（仍受敏感读拒绝约束）。管理员可扩展名单；未覆盖不自动升格。操作计划整单人审已足够，计划内不二次破坏性分级。敏感读（如 SELECT * FROM orders）一律拒绝；破坏性写（如 DROP TABLE orders）走重审批。docker restart 可轻确认。
