# 03 — 逐条确认：新建写入对象；绑定只记对应关系

**What to build:** 已认证运维对未绑定草案按条接受或拒绝。接受新建即写入策展 Docker 容器（及若接受则写入第一条策展 `运行于`）。接受绑到已有：不改容器主键 / 不可变标签，不把名称或运行时 ID 写成可靠观测 `运行于`，只记住该现场实体已对应目标对象，并让它离开待并入列表。拒绝的条目不写。绑到仍健康标签命中的对象必须失败；同一草案上绑定与新建都接受必须失败。

**Blocked by:** 02 — 从不挂冲突的未绑定候选发起草案

**Status:** ready-for-agent

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**。Spec：[`docs/specs/unbound-identity-rebind.md`](../../../docs/specs/unbound-identity-rebind.md)。

确认单位是条目（合同逐条确认）。建底 `POST` 新建主机/容器仍可用；覆盖已有 `运行于` 仍拒绝。从候选并入禁止旁路 POST。

- [ ] 只接受新建、拒绝 `运行于` → 策展出现该容器（不可变标签 = 现场标签），无该 `运行于`；未接受条目仍是草案
- [ ] 先接受 `运行于`、尚未新建 → 失败，策展不变
- [ ] 新建所用 `archops.object_id` 已被占用 → 接受失败
- [ ] 接受绑到已有失联对象 X → X 的 `容器ID` / 不可变标签不变；「实际在哪」仍不得把弱线索当可靠 `运行于`；该 `runtimeId` 不再出现在待并入列表
- [ ] 再心跳同一 `runtimeId` 仍缺标/错标 → 仍不待并入、仍身份失联、仍不承诺升级链
- [ ] 绑定与新建都接受 → 第二次失败，不得把一个现场实体变成两个策展对象
- [ ] 绑到仍标签命中、升级链有效的对象 → 失败
- [ ] `MISSING_LABEL` 新建不是成功路径（无现场标签则无不可变 object id 可写）
- [ ] `UNKNOWN_OBJECT_ID` 绑到已有允许，且不得把错标签写成 X 的新主键
- [ ] 未认证不可审条；无冲突处理人要求
- [ ] 建底插入第一条 `运行于` 仍成功；覆盖已有仍 `CURATED_RUNS_ON_EXISTS`
- [ ] HTTP 可读条目已接受 / 已拒绝审计；无整单全接受、无操作计划、无策展对齐步骤

**Out of this ticket:** 标签命中后清失联并写观测（见 04）；选支闸门（见 05）；tracer 总套件（见 06）；UI。

## Comments

02 TDD-done 后再开。接受绑定后的「现场实体对应 X」是匹配状态，不是新合同词，也不是第四种冲突。不要做 04–07。

开工 prompt：[`docs/implement-unbound-identity-rebind-03-prompt.md`](../../../docs/implement-unbound-identity-rebind-03-prompt.md)。`/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：capability 票须 witnessed red；第一圈必须是已认证 POST 未绑定 CREATE 条目 accept 的诚实红灯，不要用未认证 401 或改策展处理人审条仍绿冒充。不要为装红灯删除 02 发起或改策展 accept 生产。

