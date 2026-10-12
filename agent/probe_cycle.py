#!/usr/bin/env python3
"""Pull probe tasks on heartbeat, run them, and post structured results.

The agent does not judge. An injectable runner supplies command output.
Contract: docs/contracts/agent-heartbeat-snapshot.md
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone


class ProbeCycleError(RuntimeError):
    pass


@dataclass(frozen=True)
class CommandOutcome:
    stdout: str
    stderr: str
    exit_code: int
    timed_out: bool


def results_for_tasks(tasks: list[dict], runner) -> list[dict]:
    results = []
    for task in tasks:
        outcome: CommandOutcome = runner(task)
        results.append(
            {
                "taskId": task["id"],
                "readable": True,
                "value": json.loads(outcome.stdout),
                "evidence": outcome.stdout,
            }
        )
    return results


def pull_and_report(control_plane: str, agent_id: str, host_id: str, runner) -> list[dict]:
    pulled = post_heartbeat(control_plane, {"agentId": agent_id, "hostId": host_id, "sentAt": now()})
    tasks = (pulled.get("data") or {}).get("probeTasks") or []
    results = results_for_tasks(tasks, runner)
    if results:
        post_heartbeat(
            control_plane,
            {
                "agentId": agent_id,
                "hostId": host_id,
                "sentAt": now(),
                "probeResults": results,
            },
        )
    return results


def post_heartbeat(control_plane: str, payload: dict) -> dict:
    body = json.dumps(payload).encode("utf-8")
    url = control_plane.rstrip("/") + "/api/agent/heartbeat"
    request = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": "application/json", "User-Agent": "archops-agent-stub/0.3"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise ProbeCycleError(f"HTTP {exc.code} {url}: {detail}") from exc


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def main() -> int:
    parser = argparse.ArgumentParser(description="Pull probe tasks and post results")
    parser.add_argument("--control-plane", required=True)
    parser.add_argument("--agent-id", required=True)
    parser.add_argument("--host-id", required=True)
    args = parser.parse_args()
    spec = json.load(sys.stdin)
    outcomes = spec["outcomesByCommand"]

    def runner(task: dict) -> CommandOutcome:
        found = outcomes[task["command"]]
        return CommandOutcome(
            stdout=found["stdout"],
            stderr=found.get("stderr", ""),
            exit_code=found.get("exitCode", 0),
            timed_out=found.get("timedOut", False),
        )

    try:
        results = pull_and_report(args.control_plane, args.agent_id, args.host_id, runner)
    except ProbeCycleError as exc:
        print(str(exc), file=sys.stderr)
        return 1
    json.dump({"probeResults": results}, sys.stdout)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
