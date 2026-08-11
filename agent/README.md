# ArchOps Host Agent

Python **3.12+** host agent (ADR-0043). Delivery preference: **systemd unit install (S1)**.
Optional agent container image is Later. Source/manual run is for development only.

## Local run (scaffold)

```bash
python3 agent/heartbeat.py --control-plane http://127.0.0.1:8080 --interval 30
```

Environment overrides: `ARCHOPS_CONTROL_PLANE`, `ARCHOPS_AGENT_ID`, `ARCHOPS_HOST_LABEL`,
`ARCHOPS_HEARTBEAT_INTERVAL`.

The scaffold control plane has **no** `/api/agent/heartbeat` yet — the stub will log HTTP 404
until the vertical-slice conversation adds ingest. Stdlib only (no pip deps).

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
Environment=ARCHOPS_AGENT_ID=host-001
Environment=ARCHOPS_HOST_LABEL=host-001
Environment=ARCHOPS_HEARTBEAT_INTERVAL=30
ExecStart=/usr/bin/python3 /opt/archops-agent/heartbeat.py
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

3. `systemctl daemon-reload && systemctl enable --now archops-agent`

Agent is **not** part of the default control-plane Compose stack.
