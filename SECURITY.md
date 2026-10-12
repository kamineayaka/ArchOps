# 安全策略

## 报告漏洞

如发现安全漏洞，请负责任地披露：

1. **不要**公开提 GitHub Issue。
2. 邮件联系维护者，说明问题与复现步骤。
3. 请预留最多 72 小时等待首次回复。

## 支持版本

仅最新发布线接受安全修复。

## 部署与凭证

- 凭证加密键是环境变量 `ARCHOPS_CREDENTIALS_ENCRYPTION_KEY_BASE64`（AES 密钥，Base64，16/24/32 字节）。空白拒绝启动。不要使用 `CREDENTIALS_MASTER_KEY`。
- 领域登录不在 ADR-0046 里。冲突链不按用户身份授权，也不记录操作者。没有默认 `admin` 密码。不要把 `JWT_SECRET` 或 `X-ArchOps-User-Id` 当成冲突门禁的下一步。
- 交付目标是三个 Kubernetes Deployment（控制面、执行引擎、AI 编排层，ADR-0049）。Compose 只是本地开发便利。不要把「在 Compose 前放 TLS」写成现行交付。
- 模型 HTTPS 与密钥在编排层。控制面不持模型密钥。
- 没有 `CORS_ALLOWED_ORIGINS` 这一现行开关，也没有审计日志哈希链这一现行要求。
