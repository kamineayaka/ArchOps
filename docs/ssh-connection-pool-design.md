# SSH Connection Pool

Server-side SSH connection pool for CloudOps, inspired by OpsKat `internal/sshpool/pool.go`.

## Architecture

```
SshClient (singleton)
    └── AssetSshDialer (credential + auth)
            └── SshConnectionPool (key: userId + assetId)
                    ├── SshExecTool (exec channel)
                    ├── TerminalWebSocketHandler (shell channel)
                    └── REST warm/evict API
```

## Pool semantics

- **Key**: `(userId, assetId)` — sessions are not shared across operators.
- **Get/Release**: reference counting; idle entries evicted after 5 minutes (configurable).
- **Warm**: `POST /api/ssh/pool/{assetId}/warm` pre-authenticates without opening a terminal.
- **AI targets**: `ai_conversations.target_asset_ids` — `ssh_exec` uses these when `assetId` is omitted.

## API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/ssh/pool` | List pooled connections for current user |
| POST | `/api/ssh/pool/{assetId}/warm` | Pre-connect asset into pool |
| DELETE | `/api/ssh/pool/{assetId}` | Force evict pooled connection |
| PUT | `/api/ai/conversations/{id}/targets` | Set AI conversation target assets |

## Configuration

```yaml
cloudops:
  ssh-pool:
    idle-timeout: 5m
    connect-timeout: 15s
```
