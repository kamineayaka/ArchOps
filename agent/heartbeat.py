#!/usr/bin/env python3
"""ArchOps host-agent heartbeat (+ optional snapshot) client.

Contract: docs/contracts/agent-heartbeat-snapshot.md
Delivery install path is systemd — see README.md.
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


def build_payload(
    agent_id: str,
    host_id: str,
    *,
    include_snapshot: bool,
    object_id: str | None,
    runtime_id: str | None,
    container_name: str | None,
    absent_object_ids: list[str],
    identity_lost_object_ids: list[str],
    unlabeled: bool,
) -> dict:
    payload: dict = {
        "agentId": agent_id,
        "hostId": host_id,
        "sentAt": datetime.now(timezone.utc).isoformat(),
    }
    if not include_snapshot:
        return payload

    containers: list[dict] = []
    if unlabeled:
        containers.append(
            {
                "runtimeId": runtime_id or "docker-unlabeled",
                "name": container_name or "mystery",
                "labels": {},
            }
        )
    elif object_id:
        containers.append(
            {
                "runtimeId": runtime_id or "docker-runtime",
                "name": container_name or "app",
                "labels": {"archops.object_id": object_id},
            }
        )

    payload["snapshot"] = {
        "containers": containers,
        "absentObjectIds": absent_object_ids,
        "identityLostObjectIds": identity_lost_object_ids,
    }
    return payload


def emit_heartbeat(control_plane: str, payload: dict) -> int:
    body = json.dumps(payload).encode("utf-8")
    url = control_plane.rstrip("/") + "/api/agent/heartbeat"
    req = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json", "User-Agent": "archops-agent-stub/0.2"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            raw = resp.read().decode("utf-8")
            print(f"[ok] {resp.status} {url}")
            print(raw)
            return 0
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        print(f"[warn] HTTP {exc.code} {url}: {exc.reason}\n{detail}", file=sys.stderr)
        return 1
    except urllib.error.URLError as exc:
        print(f"[warn] unreachable {url}: {exc.reason}", file=sys.stderr)
        return 1


def main() -> int:
    parser = argparse.ArgumentParser(description="ArchOps agent heartbeat (+ snapshot)")
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
        "--host-id",
        default=os.environ.get("ARCHOPS_HOST_ID", ""),
        help="Curated physical host id (required)",
    )
    parser.add_argument(
        "--host-label",
        default=os.environ.get("ARCHOPS_HOST_LABEL", "local-dev"),
        help="Deprecated human label (ignored by ingest; kept for env compat)",
    )
    parser.add_argument(
        "--interval",
        type=float,
        default=float(os.environ.get("ARCHOPS_HEARTBEAT_INTERVAL", "30")),
        help="Seconds between heartbeats (0 = once)",
    )
    parser.add_argument(
        "--snapshot",
        action="store_true",
        default=os.environ.get("ARCHOPS_SEND_SNAPSHOT", "").lower() in {"1", "true", "yes"},
        help="Attach a status snapshot",
    )
    parser.add_argument(
        "--object-id",
        default=os.environ.get("ARCHOPS_OBJECT_ID", ""),
        help="archops.object_id label value for a present container",
    )
    parser.add_argument(
        "--runtime-id",
        default=os.environ.get("ARCHOPS_RUNTIME_ID", "docker-runtime"),
        help="Docker runtime id clue",
    )
    parser.add_argument(
        "--container-name",
        default=os.environ.get("ARCHOPS_CONTAINER_NAME", "app"),
        help="Container name clue",
    )
    parser.add_argument(
        "--absent-object-id",
        action="append",
        default=[],
        help="Assert curated object id absent (repeatable) → 观测消失",
    )
    parser.add_argument(
        "--identity-lost-object-id",
        action="append",
        default=[],
        help="Mark identity lost for object id (repeatable)",
    )
    parser.add_argument(
        "--unlabeled",
        action="store_true",
        help="Send an unlabeled container (未绑定 / no upgrade chain)",
    )
    args = parser.parse_args()

    if not args.host_id:
        print("error: --host-id / ARCHOPS_HOST_ID is required (curated physical host id)", file=sys.stderr)
        return 2

    def once() -> int:
        payload = build_payload(
            args.agent_id,
            args.host_id,
            include_snapshot=args.snapshot or args.unlabeled or bool(args.object_id)
            or bool(args.absent_object_id) or bool(args.identity_lost_object_id),
            object_id=args.object_id or None,
            runtime_id=args.runtime_id,
            container_name=args.container_name,
            absent_object_ids=args.absent_object_id,
            identity_lost_object_ids=args.identity_lost_object_id,
            unlabeled=args.unlabeled,
        )
        return emit_heartbeat(args.control_plane, payload)

    if args.interval <= 0:
        return once()

    print(
        f"heartbeat → {args.control_plane} every {args.interval}s "
        f"(agentId={args.agent_id}, hostId={args.host_id})"
    )
    while True:
        once()
        time.sleep(args.interval)


if __name__ == "__main__":
    raise SystemExit(main())
