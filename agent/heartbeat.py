#!/usr/bin/env python3
"""ArchOps host-agent heartbeat stub (local runnable).

Delivery install path is systemd — see README.md. This script only proves a
local heartbeat loop; ingest API arrives in the vertical-slice conversation.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone


def emit_heartbeat(control_plane: str, agent_id: str, host_label: str) -> None:
    payload = {
        "agentId": agent_id,
        "hostLabel": host_label,
        "sentAt": datetime.now(timezone.utc).isoformat(),
        "kind": "heartbeat",
        # Snapshot / identity matching lands in vertical slice.
        "snapshot": {},
    }
    body = json.dumps(payload).encode("utf-8")
    url = control_plane.rstrip("/") + "/api/agent/heartbeat"
    req = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json", "User-Agent": "archops-agent-stub/0.1"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            print(f"[ok] {resp.status} {url}")
    except urllib.error.HTTPError as exc:
        # Scaffold control plane has no ingest yet — expect 404 until vertical slice.
        print(f"[warn] HTTP {exc.code} {url}: {exc.reason}", file=sys.stderr)
    except urllib.error.URLError as exc:
        print(f"[warn] unreachable {url}: {exc.reason}", file=sys.stderr)


def main() -> int:
    parser = argparse.ArgumentParser(description="ArchOps agent heartbeat stub")
    parser.add_argument(
        "--control-plane",
        default=os.environ.get("ARCHOPS_CONTROL_PLANE", "http://127.0.0.1:8080"),
        help="Control plane base URL",
    )
    parser.add_argument(
        "--agent-id",
        default=os.environ.get("ARCHOPS_AGENT_ID", "local-dev-agent"),
        help="Stable agent id for this host",
    )
    parser.add_argument(
        "--host-label",
        default=os.environ.get("ARCHOPS_HOST_LABEL", "local-dev"),
        help="Human-readable host label",
    )
    parser.add_argument(
        "--interval",
        type=float,
        default=float(os.environ.get("ARCHOPS_HEARTBEAT_INTERVAL", "30")),
        help="Seconds between heartbeats (0 = once)",
    )
    args = parser.parse_args()

    if args.interval <= 0:
        emit_heartbeat(args.control_plane, args.agent_id, args.host_label)
        return 0

    print(
        f"heartbeat stub → {args.control_plane} every {args.interval}s "
        f"(agentId={args.agent_id})"
    )
    while True:
        emit_heartbeat(args.control_plane, args.agent_id, args.host_label)
        time.sleep(args.interval)


if __name__ == "__main__":
    raise SystemExit(main())
