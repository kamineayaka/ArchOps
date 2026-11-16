"""One repair step issued by the control plane and run by the host agent.

Failure stops the repair. The step output stays off the alert. The alert ends
only when a later collection reports the role up.
"""

from __future__ import annotations

import os
import socket
import subprocess
import tempfile
import threading
import time
from pathlib import Path

import uvicorn
from fastapi.testclient import TestClient

from test_cluster_role_liveness_http import (
    _Server,
    _assert_no_operator,
    _go_binary,
    _host,
    _place,
)

_CAUSE_KEYS = {
    "cause",
    "causeKind",
    "reason",
    "reasonKind",
    "category",
    "operator",
    "operatorId",
    "userId",
    "createdBy",
    "recordedBy",
    "actor",
    "actorId",
    "acceptedBy",
    "handledBy",
}
_NODEMANAGER = {
    "role": "NodeManager",
    "queueName": "root.default",
    "memoryMb": 8192,
    "vcores": 4,
}
_NAMENODE = {"role": "NameNode", "replicaCount": 3, "capacityBytes": 1099511627776}
_DATANODE = {"role": "DataNode", "replicaCount": 3, "capacityBytes": 1099511627776}
_START_NODEMANAGER = ["yarn", "--daemon", "start", "nodemanager"]
_REFRESH_QUEUES = ["yarn", "rmadmin", "-refreshQueues"]


def _keys(value: object) -> set[str]:
    found: set[str] = set()

    def walk(node: object) -> None:
        if isinstance(node, dict):
            for key, child in node.items():
                found.add(key)
                walk(child)
        elif isinstance(node, list):
            for child in node:
                walk(child)

    walk(value)
    return found


def _assert_no_cause(response) -> None:
    assert _keys(response.json()).isdisjoint(_CAUSE_KEYS)


def _alert_for(client: TestClient, host_id: str, role: str, raw: str) -> dict:
    reported = client.post(
        f"/api/hosts/{host_id}/role-liveness",
        json={"roleLiveness": [{"role": role, "up": False, "raw": raw}]},
    )
    assert reported.status_code == 200, reported.text
    alerts = client.get("/api/alerts").json()["data"]
    matched = [item for item in alerts if item["role"] == role]
    assert len(matched) == 1
    return matched[0]


def _start(client: TestClient, alert_id: str) -> dict:
    response = client.post(f"/api/alerts/{alert_id}/repairs")
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["success"] is True
    assert body["code"] == "OK"
    _assert_no_cause(response)
    return body["data"]


def _result(client: TestClient, host_id: str, repair_id: str, seq: int, exit_code: int, output: str, **extra):
    payload = {
        "repairId": repair_id,
        "seq": seq,
        "exitCode": exit_code,
        "output": output,
    }
    payload.update(extra)
    return client.post(f"/api/hosts/{host_id}/repair-results", json=payload)


def test_failed_step_stops_and_recovery_collection_ends_the_alert(client: TestClient):
    host = _host(client)
    _place(client, host["id"], _NODEMANAGER)
    raw = "jps -l\n1 DataNode"
    alert = _alert_for(client, host["id"], "NodeManager", raw)
    repair = _start(client, alert["id"])
    assert repair["status"] == "open"
    assert repair["issued"] == {"seq": 1, "argv": _START_NODEMANAGER}
    assert repair["steps"] == [
        {
            "seq": 1,
            "argv": _START_NODEMANAGER,
            "state": "issued",
            "exitCode": None,
            "output": None,
        }
    ]
    assert client.get(f"/api/hosts/{host['id']}/repair-step").json()["data"] == {
        "repairId": repair["id"],
        "seq": 1,
        "argv": _START_NODEMANAGER,
    }

    marker = "repair-output-not-evidence"
    failed = _result(client, host["id"], repair["id"], 1, 1, marker, cause="disk")
    assert failed.status_code == 200, failed.text
    _assert_no_cause(failed)
    assert marker in failed.text
    assert "disk" not in failed.text
    assert "refreshQueues" not in failed.text
    assert "argv" not in failed.json()["data"]
    receipt = failed.json()["data"]
    assert receipt["status"] == "stopped"
    assert receipt["state"] == "failed"
    assert receipt["exitCode"] == 1
    assert receipt["output"] == marker
    stopped = client.get(f"/api/repairs/{repair['id']}").json()["data"]
    assert stopped["status"] == "stopped"
    assert stopped["issued"] is None
    assert stopped["steps"] == [
        {
            "seq": 1,
            "argv": _START_NODEMANAGER,
            "state": "failed",
            "exitCode": 1,
            "output": marker,
        }
    ]
    assert client.get(f"/api/hosts/{host['id']}/repair-step").json()["data"] is None

    skipped = _result(client, host["id"], repair["id"], 2, 0, "should-not-run")
    assert skipped.status_code == 409, skipped.text
    assert skipped.json()["code"] == "STEP_NOT_ISSUED"
    retried = _result(client, host["id"], repair["id"], 1, 0, "retried-output")
    assert retried.status_code == 200, retried.text
    assert retried.json()["data"]["status"] == "stopped"
    assert retried.json()["data"]["exitCode"] == 1
    assert retried.json()["data"]["output"] == marker
    again = client.post(f"/api/alerts/{alert['id']}/repairs")
    assert again.status_code == 200, again.text
    assert again.json()["data"]["status"] == "stopped"
    assert "refreshQueues" not in again.text

    fetched = client.get(f"/api/alerts/{alert['id']}")
    assert fetched.status_code == 200, fetched.text
    body = fetched.json()["data"]
    assert set(body) == {"id", "hostId", "role", "current", "evidence"}
    assert body["current"] is True
    assert body["evidence"] == raw
    assert marker not in fetched.text
    assert marker not in client.get("/api/alerts").text

    up = client.post(
        f"/api/hosts/{host['id']}/role-liveness",
        json={
            "roleLiveness": [
                {"role": "NodeManager", "up": True, "raw": "jps -l\n9 NodeManager"}
            ]
        },
    )
    assert up.status_code == 200, up.text
    ended = client.get(f"/api/alerts/{alert['id']}").json()["data"]
    assert ended["current"] is False
    assert ended["evidence"] == raw
    assert marker not in ended["evidence"]
    assert client.get("/api/alerts").json()["data"] == []


def test_successful_steps_stay_off_the_alert_until_a_later_collection(client: TestClient):
    host = _host(client)
    _place(client, host["id"], _NODEMANAGER)
    raw = "jps -l\n1 DataNode"
    alert = _alert_for(client, host["id"], "NodeManager", raw)
    repair = _start(client, alert["id"])
    first = "start-output-not-evidence"
    started = _result(client, host["id"], repair["id"], 1, 0, first)
    assert started.status_code == 200, started.text
    assert started.json()["data"]["status"] == "open"
    assert "argv" not in started.json()["data"]
    assert "refreshQueues" not in started.text
    issued = client.get(f"/api/hosts/{host['id']}/repair-step")
    assert issued.status_code == 200, issued.text
    assert issued.json()["data"] == {
        "repairId": repair["id"],
        "seq": 2,
        "argv": _REFRESH_QUEUES,
    }
    assert first not in issued.text
    still = client.get(f"/api/alerts/{alert['id']}").json()["data"]
    assert still["current"] is True
    assert still["evidence"] == raw
    assert first not in client.get(f"/api/alerts/{alert['id']}").text

    second = "refresh-output-not-evidence"
    finished = _result(client, host["id"], repair["id"], 2, 0, second)
    assert finished.status_code == 200, finished.text
    assert finished.json()["data"]["status"] == "completed"
    assert "argv" not in finished.json()["data"]
    stored = client.get(f"/api/repairs/{repair['id']}").json()["data"]
    assert stored["issued"] is None
    assert [step["seq"] for step in stored["steps"]] == [1, 2]
    assert client.get(f"/api/hosts/{host['id']}/repair-step").json()["data"] is None
    still = client.get(f"/api/alerts/{alert['id']}").json()["data"]
    assert still["current"] is True
    assert still["evidence"] == raw
    assert first not in still["evidence"]
    assert second not in client.get(f"/api/alerts/{alert['id']}").text

    up = client.post(
        f"/api/hosts/{host['id']}/role-liveness",
        json={
            "roleLiveness": [
                {"role": "NodeManager", "up": True, "raw": "jps -l\n9 NodeManager"}
            ]
        },
    )
    assert up.status_code == 200, up.text
    ended = client.get(f"/api/alerts/{alert['id']}").json()["data"]
    assert ended["current"] is False
    assert ended["evidence"] == raw


def test_a_host_with_an_issued_step_does_not_receive_another(client: TestClient):
    host = _host(client)
    _place(client, host["id"], _NAMENODE)
    _place(client, host["id"], _DATANODE)
    raw = "jps -l\nempty"
    namenode = _alert_for(client, host["id"], "NameNode", raw)
    datanode = _alert_for(client, host["id"], "DataNode", raw)
    first = _start(client, namenode["id"])
    assert first["issued"]["argv"] == ["hdfs", "--daemon", "start", "namenode"]
    second = client.post(f"/api/alerts/{datanode['id']}/repairs")
    assert second.status_code == 409, second.text
    assert second.json()["code"] == "HOST_REPAIR_BUSY"
    assert second.json()["success"] is False
    pulled = client.get(f"/api/hosts/{host['id']}/repair-step").json()["data"]
    assert pulled == {
        "repairId": first["id"],
        "seq": 1,
        "argv": ["hdfs", "--daemon", "start", "namenode"],
    }


def test_repair_endpoints_record_no_operator(client: TestClient):
    user_id = "user-should-not-be-stored"
    headers = {"X-ArchOps-User-Id": user_id}
    host = _host(client)
    _place(client, host["id"], _NODEMANAGER)
    raw = "jps -l\n1 DataNode"
    reported = client.post(
        f"/api/hosts/{host['id']}/role-liveness",
        json={"roleLiveness": [{"role": "NodeManager", "up": False, "raw": raw}]},
        headers=headers,
    )
    _assert_no_operator(reported, user_id)
    alert_id = client.get("/api/alerts").json()["data"][0]["id"]
    started = client.post(f"/api/alerts/{alert_id}/repairs", headers=headers)
    _assert_no_operator(started, user_id)
    assert started.status_code == 200, started.text
    repair_id = started.json()["data"]["id"]
    failed = client.post(
        f"/api/hosts/{host['id']}/repair-results",
        json={
            "repairId": repair_id,
            "seq": 1,
            "exitCode": 1,
            "output": "repair-output-not-evidence",
            "operator": user_id,
        },
        headers=headers,
    )
    _assert_no_operator(failed, user_id)
    assert failed.status_code == 200, failed.text
    fetched = client.get(f"/api/repairs/{repair_id}", headers=headers)
    _assert_no_operator(fetched, user_id)
    assert user_id not in fetched.text
    pulled = client.get(f"/api/hosts/{host['id']}/repair-step", headers=headers)
    _assert_no_operator(pulled, user_id)
    assert pulled.json()["data"] is None


def _proc(root: Path, pid: str, cmdline: bytes) -> None:
    directory = root / pid
    directory.mkdir(parents=True)
    (directory / "cmdline").write_bytes(cmdline)


def _run_agent(binary: Path, base: str, host_id: str, proc: Path, path: str) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env["PATH"] = path
    env["GOTOOLCHAIN"] = "local"
    return subprocess.run(
        [
            str(binary),
            "--control-plane",
            base,
            "--host-id",
            host_id,
            "--role",
            "NodeManager",
            "--proc-root",
            str(proc),
        ],
        capture_output=True,
        text=True,
        check=False,
        env=env,
    )


def test_go_agent_runs_the_failed_step_and_a_later_collection_ends_the_alert(
    client: TestClient,
):
    host = _host(client)
    _place(client, host["id"], _NODEMANAGER)
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    from archops_control.app import create_app

    server = _Server(
        uvicorn.Config(create_app(), host="127.0.0.1", port=port, log_level="error")
    )
    thread = threading.Thread(target=server.run, daemon=True)
    thread.start()
    deadline = time.time() + 5
    while not server.started:
        if time.time() > deadline:
            raise AssertionError("control plane did not start")
        time.sleep(0.01)
    base = f"http://127.0.0.1:{port}"
    down_raw = "java -Dproc=org.apache.hadoop.hdfs.server.datanode.DataNode"
    try:
        with tempfile_agent() as paths:
            binary, bin_dir, log_path, down_proc, up_proc = paths
            path = f"{bin_dir}{os.pathsep}{os.environ.get('PATH', '')}"
            first = _run_agent(binary, base, host["id"], down_proc, path)
            assert first.returncode == 0, first.stderr
            assert not log_path.exists()
            alerts = client.get("/api/alerts").json()["data"]
            assert len(alerts) == 1
            assert alerts[0]["evidence"] == down_raw
            alert_id = alerts[0]["id"]
            repair = _start(client, alert_id)
            assert repair["issued"]["argv"] == _START_NODEMANAGER

            failed = _run_agent(binary, base, host["id"], down_proc, path)
            assert failed.returncode == 0, failed.stderr
            assert log_path.read_text() == "--daemon start nodemanager\n"
            stored = client.get(f"/api/repairs/{repair['id']}").json()["data"]
            assert stored["status"] == "stopped"
            assert stored["issued"] is None
            assert len(stored["steps"]) == 1
            assert stored["steps"][0]["state"] == "failed"
            assert stored["steps"][0]["exitCode"] == 1
            assert stored["steps"][0]["argv"] == _START_NODEMANAGER
            assert "repair-output-not-evidence" in stored["steps"][0]["output"]
            assert "refreshQueues" not in client.get(f"/api/repairs/{repair['id']}").text
            open_alert = client.get(f"/api/alerts/{alert_id}").json()["data"]
            assert open_alert["current"] is True
            assert open_alert["evidence"] == down_raw
            assert "repair-output-not-evidence" not in client.get(f"/api/alerts/{alert_id}").text
            assert client.get(f"/api/hosts/{host['id']}/repair-step").json()["data"] is None

            stopped = _run_agent(binary, base, host["id"], down_proc, path)
            assert stopped.returncode == 0, stopped.stderr
            assert log_path.read_text() == "--daemon start nodemanager\n"

            recovered = _run_agent(binary, base, host["id"], up_proc, path)
            assert recovered.returncode == 0, recovered.stderr
            assert log_path.read_text() == "--daemon start nodemanager\n"
            ended = client.get(f"/api/alerts/{alert_id}").json()["data"]
            assert ended["current"] is False
            assert ended["role"] == "NodeManager"
            assert ended["evidence"] == down_raw
            assert client.get("/api/alerts").json()["data"] == []
    finally:
        server.should_exit = True
        thread.join(timeout=5)


class tempfile_agent:
    def __enter__(self):
        self._tmp = tempfile.TemporaryDirectory()
        root = Path(self._tmp.name)
        binary = root / "host-agent"
        build = subprocess.run(
            [_go_binary(), "build", "-o", str(binary), "."],
            cwd=Path(__file__).resolve().parents[2] / "host-agent",
            capture_output=True,
            text=True,
            check=False,
            env={**os.environ, "GOTOOLCHAIN": "local", "PATH": f"{os.environ.get('HOME', '')}/.local/go/bin:{os.environ.get('PATH', '')}"},
        )
        assert build.returncode == 0, build.stderr
        bin_dir = root / "bin"
        bin_dir.mkdir()
        log_path = root / "yarn.log"
        script = bin_dir / "yarn"
        script.write_text(
            "#!/bin/sh\n"
            f"printf '%s\\n' \"$*\" >> '{log_path}'\n"
            "echo repair-output-not-evidence\n"
            "exit 1\n"
        )
        script.chmod(0o755)
        down_proc = root / "down"
        up_proc = root / "up"
        _proc(
            down_proc,
            "4123",
            b"java\x00-Dproc=org.apache.hadoop.hdfs.server.datanode.DataNode\x00",
        )
        _proc(
            up_proc,
            "99",
            b"java\x00-Dproc=org.apache.hadoop.yarn.server.nodemanager.NodeManager\x00",
        )
        return binary, bin_dir, log_path, down_proc, up_proc

    def __exit__(self, exc_type, exc, tb):
        self._tmp.cleanup()
        return False
