# 留存的 Python 心跳

现行 Host Agent 是仓库根目录的 `host-agent/`（Go）。它采集本机 HDFS/YARN 角色是否在，并执行控制面一次发来的一步修复。

本目录是留下的 Python 3.12+ 心跳 stub，对着 Java 控制面。它不是现行采集和修复路径。

**Payload contract**: [`docs/contracts/agent-heartbeat-snapshot.md`](../docs/contracts/agent-heartbeat-snapshot.md).

## Local run

Heartbeat only (freshness):

```bash
python3 agent/heartbeat.py \
  --control-plane http://127.0.0.1:8080 \
  --host-id host-<curated-id> \
  --interval 30
```

Heartbeat + snapshot (match curated container by `archops.object_id`):

```bash
python3 agent/heartbeat.py \
  --control-plane http://127.0.0.1:8080 \
  --host-id host-<curated-id> \
  --snapshot \
  --object-id ctr-x-001 \
  --interval 0
```

Environment overrides: `ARCHOPS_CONTROL_PLANE`, `ARCHOPS_AGENT_ID`, `ARCHOPS_HOST_ID`,
`ARCHOPS_HEARTBEAT_INTERVAL`, `ARCHOPS_SEND_SNAPSHOT`, `ARCHOPS_OBJECT_ID`,
`ARCHOPS_RUNTIME_ID`, `ARCHOPS_CONTAINER_NAME`.

Stdlib only (no pip deps). Endpoint: `POST /api/agent/heartbeat`.

The heartbeat response carries this stub's probe task list. This process can run those commands and post `probeResults`. That is not the current product. Collection and one-step repair live in `host-agent/`.

## systemd

1. Install script to e.g. `/opt/archops-agent/heartbeat.py` (Python 3.12+ on PATH).
2. Drop a unit such as `/etc/systemd/system/archops-agent.service`:

```ini
[Unit]
Description=ArchOps host agent heartbeat
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
Environment=ARCHOPS_CONTROL_PLANE=https://archops.example.internal
Environment=ARCHOPS_AGENT_ID=host-001-agent
Environment=ARCHOPS_HOST_ID=host-<curated-physical-host-id>
Environment=ARCHOPS_HEARTBEAT_INTERVAL=30
Environment=ARCHOPS_SEND_SNAPSHOT=true
Environment=ARCHOPS_OBJECT_ID=ctr-x-001
ExecStart=/usr/bin/python3 /opt/archops-agent/heartbeat.py
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

3. `systemctl daemon-reload && systemctl enable --now archops-agent`

Agent is **not** part of the default control-plane Compose stack. It stays on the host (ADR-0048 / ADR-0049). Compose is a dev convenience, not the delivery target.
