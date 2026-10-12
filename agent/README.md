# ArchOps Host Agent

Python **3.12+** host agent (ADR-0043). Delivery preference: **systemd unit install (S1)**.
Optional agent container image is Later. Source/manual run is for development only.

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

The heartbeat response carries this agent's probe task list (ADR-0048). This process runs each command locally and posts `probeResults` on the same ingest. It does not judge and it does not call a model. The control plane judges each returned result. An older process that ignores `probeTasks` can still heartbeat. Delivery remains the systemd unit; there is no task-push channel.

## systemd (delivery main path)

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
