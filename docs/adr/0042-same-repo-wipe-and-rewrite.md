# 旧代码处置：同仓清空重写（C1）

K1 全量推倒下，采用同仓清空重写：移除既有业务实现（旧 `backend/`、`frontend/` 等领域与 UI 代码），在原 `backend/` + `frontend/` 路径重生绿场工程；另增 Agent 工程（如 `agent/`）承载心跳与快照。保留 `CONTEXT.md`、`docs/adr/`、`docs/mvp-vertical-slice.md` 等合同与计划文档；`.git` 历史可回溯旧实现作只读参考。禁止保留旧域包并行「兼容层」。部署目录可随后按绿场重写，不作为旧语义延续。
