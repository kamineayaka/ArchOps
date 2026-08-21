# 01 — 控制面推断身份失联；未绑定按现场实体 upsert；规范问法

**What to build:** Host Agent 心跳快照仍按标签匹配 Docker 容器。缺标 / 未知标签写成未绑定观测候选，但同一现场实体（`sourceHostId` + `runtimeId`）只保留一行并刷新观察时间。当上报主机是该容器的策展 `运行于` 宿主或当前可用观测宿主（从未写过观测则只看策展宿主），本快照未标签命中且未进入 `absentObjectIds` 时，控制面给该对象打身份失联——不必等 Agent 填写 `identityLostObjectIds`。他机快照不得给 X 打失联。规范问法「实际在哪」在失联时不得把旧宿主当实际，也不得把失联写成观测空洞或观测消失。未打标同名仍不承诺冲突升级链。

**Blocked by:** None — can start immediately

**Status:** ready-for-agent

**TDD:** `/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：**red → green → refactor**，一圈一条 HTTP 测试。先 witnessed red，再写本票生产代码。Spec：[`docs/specs/unbound-identity-rebind.md`](../../../docs/specs/unbound-identity-rebind.md)。

从竖切往上长：今日未绑定每次快照都插入新行；身份失联只信 Agent 声明；GET 未绑定不含 labels；标签稍后命中也不清失联。本票只把观测侧认得出、列得出、问得清；不发起草案、不绑定、不闸门选支。

- [ ] 缺标快照 → 未绑定 `MISSING_LABEL`，`upgradeChainPromised=false`；未知 `archops.object_id` → `UNKNOWN_OBJECT_ID`
- [ ] 同一 `sourceHostId` + `runtimeId` 再心跳 → upsert（刷新 `observedAt` / 名称 / 标签 / 原因），列表不因每拍多一行
- [ ] GET 未绑定含 labels（至少现场 `archops.object_id`）、`runtimeId`、`name`、`reason`、`sourceHostId`；`upgradeChainPromised` 恒为 false
- [ ] 策展/可用观测宿主上的快照未标签命中且未声明观测消失 → 该 Docker 容器身份失联；Agent `identityLostObjectIds` 在同一主机范围内仍有效
- [ ] 既非策展 `运行于` 宿主、也非当前可用观测宿主的快照，不得给该容器打身份失联
- [ ] `absentObjectIds` 仍写入观测消失（可用值不存在），不是身份失联，也不是观测空洞
- [ ] 「应该在哪」仍答策展；「实际在哪」在失联时同屏策展，且不得把失联前宿主当实际（`availability` 不得为 `PRESENT`；不得单因失联报 `HOLLOW` / `ABSENT`）
- [ ] 未打标同名路径：`by-merge-key` 仍不承诺升级链（竖切票 13 负面不回归）
- [ ] 心跳契约文档写明：控制面推断失联、未绑定 upsert、主机范围；不改 `CONTEXT.md`

**Out of this ticket:** 未绑定草案、绑定记忆、新建策展对象、失联时选支/诊断闸门、标签命中收尾、薄 UI、SSH、Y2、LLM。

## Comments

Frontier。一次只做本票。HTTP 主接缝；Flyway 只增不改历史。不要做 02–07。

开工 prompt：[`docs/implement-unbound-identity-rebind-01-prompt.md`](../../../docs/implement-unbound-identity-rebind-01-prompt.md)。`/implement` 走 [`docs/agents/tdd.md`](../../../docs/agents/tdd.md)：capability 票须 witnessed red；不要为装红灯删除竖切未打标 / 观测消失 / 命中 `运行于` 的生产。
