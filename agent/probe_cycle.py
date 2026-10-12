#!/usr/bin/env python3
"""Pull probe tasks on heartbeat, run them, and post structured results.

The agent does not judge. An injectable runner supplies command output.
Contract: docs/contracts/agent-heartbeat-snapshot.md
"""

from __future__ import annotations

import argparse
import json
import subprocess
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


def subprocess_runner(task: dict) -> CommandOutcome:
    """Run the probe command on this host. The control plane does not SSH it.

    intervalSeconds is the control plane's overdue clock, not a kill timer.
    A command that never returns is a missed report because this cycle does not post.
    """
    completed = subprocess.run(
        task["command"],
        shell=True,
        capture_output=True,
        text=True,
    )
    return CommandOutcome(completed.stdout, completed.stderr, completed.returncode, False)


def results_for_tasks(tasks: list[dict], runner) -> list[dict]:
    results = []
    for task in tasks:
        result = result_for_task(task, runner(task))
        if result is not None:
            results.append(result)
    return results


def result_for_task(task: dict, outcome: CommandOutcome) -> dict | None:
    """A timed-out command is a missed report: omit it so the control plane can hollow that task."""
    if outcome.timed_out:
        return None
    value = structured_value(outcome)
    if value is None:
        return {
            "taskId": task["id"],
            "readable": False,
            "evidence": raw_evidence(outcome),
        }
    return {
        "taskId": task["id"],
        "readable": True,
        "value": value,
        "evidence": outcome.stdout,
    }


def structured_value(outcome: CommandOutcome):
    if outcome.exit_code != 0:
        return None
    try:
        return json.loads(outcome.stdout)
    except json.JSONDecodeError:
        return None


def raw_evidence(outcome: CommandOutcome) -> str:
    if outcome.stdout:
        return outcome.stdout
    if outcome.stderr:
        return outcome.stderr
    return "command error"


def pull_and_report(
    control_plane: str,
    agent_id: str,
    host_id: str,
    runner,
    *,
    snapshot: dict | None = None,
    poster=None,
) -> list[dict]:
    send = poster or (lambda payload: post_heartbeat(control_plane, payload))
    first = {"agentId": agent_id, "hostId": host_id, "sentAt": now()}
    if snapshot is not None:
        first["snapshot"] = snapshot
    pulled = send(first)
    tasks = (pulled.get("data") or {}).get("probeTasks") or []
    results = results_for_tasks(tasks, runner)
    if results:
        send(
            {
                "agentId": agent_id,
                "hostId": host_id,
                "sentAt": now(),
                "probeResults": results,
            }
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
    except urllib.error.URLError as exc:
        raise ProbeCycleError(f"unreachable {url}: {exc.reason}") from exc


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
