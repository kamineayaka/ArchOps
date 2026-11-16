"""HTTP acceptance for cluster, role, host, and role-liveness alerts.

Seam: the Python control plane HTTP API (docs/specs/big-data-platform.md).
"""

from __future__ import annotations

import shutil
import socket
import subprocess
import tempfile
import threading
import time
from pathlib import Path

import uvicorn
from fastapi.testclient import TestClient


def _post_ok(client: TestClient, path: str, payload: dict) -> dict:
    response = client.post(path, json=payload)
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["success"] is True
    assert body["code"] == "OK"
    assert body["message"] == "ok"
    assert isinstance(body["data"], dict)
    return body["data"]


def _host(client: TestClient, name: str = "host-a") -> dict:
    cluster = _post_ok(client, "/api/clusters", {"name": "east-cluster"})
    return _post_ok(client, f"/api/clusters/{cluster['id']}/hosts", {"name": name})


def _place(client: TestClient, host_id: str, body: dict) -> dict:
    return _post_ok(client, f"/api/hosts/{host_id}/roles", body)


def test_registered_namenode_and_nodemanager_configs_are_readable(client: TestClient):
    host = _host(client)
    _place(
        client,
        host["id"],
        {"role": "NameNode", "replicaCount": 3, "capacityBytes": 1099511627776},
    )
    _place(
        client,
        host["id"],
        {
            "role": "NodeManager",
            "queueName": "root.default",
            "memoryMb": 8192,
            "vcores": 4,
        },
    )

    response = client.get(f"/api/hosts/{host['id']}/roles")
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["success"] is True
    assert body["code"] == "OK"
    assert body["message"] == "ok"
    roles = {item["role"]: item for item in body["data"]}
    assert roles["NameNode"]["replicaCount"] == 3
    assert roles["NameNode"]["capacityBytes"] == 1099511627776
    assert roles["NodeManager"]["queueName"] == "root.default"
    assert roles["NodeManager"]["memoryMb"] == 8192
    assert roles["NodeManager"]["vcores"] == 4


def test_role_outside_namenode_datanode_resourcemanager_nodemanager_is_rejected(
    client: TestClient,
):
    host = _host(client)
    for role in ("JournalNode", "SparkDriver"):
        response = client.post(
            f"/api/hosts/{host['id']}/roles",
            json={"role": role, "replicaCount": 3, "capacityBytes": 1},
        )
        assert response.status_code == 400, response.text
        body = response.json()
        assert body["success"] is False
        assert body["code"] == "ROLE_NOT_ALLOWED"
        assert body["message"] == (
            "role must be NameNode, DataNode, ResourceManager, or NodeManager"
        )
        assert body["data"] is None

    listed = client.get(f"/api/hosts/{host['id']}/roles")
    assert listed.status_code == 200, listed.text
    assert listed.json()["data"] == []


def test_hdfs_role_requires_replica_and_capacity_and_yarn_role_requires_queue(
    client: TestClient,
):
    host = _host(client)
    for body in (
        {"role": "NameNode"},
        {"role": "DataNode", "replicaCount": 3},
        {"role": "NodeManager", "queueName": "root.default"},
        {"role": "ResourceManager"},
    ):
        response = client.post(f"/api/hosts/{host['id']}/roles", json=body)
        assert response.status_code == 400, response.text
        payload = response.json()
        assert payload["success"] is False
        assert payload["code"] == "ROLE_CONFIG_REQUIRED"
        assert payload["message"] == (
            "HDFS role requires replicaCount and capacityBytes; "
            "YARN role requires queueName, memoryMb, and vcores"
        )
        assert payload["data"] is None
    assert client.get(f"/api/hosts/{host['id']}/roles").json()["data"] == []


def test_role_down_report_opens_current_alert_with_raw_evidence(client: TestClient):
    host = _host(client)
    _place(
        client,
        host["id"],
        {"role": "NameNode", "replicaCount": 3, "capacityBytes": 1099511627776},
    )
    raw = "jps -l\n4123 org.apache.hadoop.hdfs.server.datanode.DataNode"

    response = client.post(
        f"/api/hosts/{host['id']}/role-liveness",
        json={"roleLiveness": [{"role": "NameNode", "up": False, "raw": raw}]},
    )
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["success"] is True
    assert body["code"] == "OK"
    assert body["message"] == "ok"

    listed = client.get("/api/alerts")
    assert listed.status_code == 200, listed.text
    payload = listed.json()
    assert payload["success"] is True
    assert payload["code"] == "OK"
    assert payload["message"] == "ok"
    assert len(payload["data"]) == 1
    alert = payload["data"][0]
    assert alert["hostId"] == host["id"]
    assert alert["role"] == "NameNode"
    assert alert["current"] is True
    assert alert["evidence"] == raw


def test_second_down_report_keeps_one_current_alert_and_the_first_evidence(
    client: TestClient,
):
    host = _host(client)
    _place(
        client,
        host["id"],
        {"role": "NameNode", "replicaCount": 3, "capacityBytes": 1099511627776},
    )
    first = "jps -l\n1 DataNode"
    second = "jps -l\n2 DataNode"
    for raw in (first, second):
        response = client.post(
            f"/api/hosts/{host['id']}/role-liveness",
            json={"roleLiveness": [{"role": "NameNode", "up": False, "raw": raw}]},
        )
        assert response.status_code == 200, response.text
    alerts = client.get("/api/alerts").json()["data"]
    assert len(alerts) == 1
    assert alerts[0]["role"] == "NameNode"
    assert alerts[0]["current"] is True
    assert alerts[0]["evidence"] == first


def test_role_up_report_ends_the_same_alert(client: TestClient):
    host = _host(client)
    _place(
        client,
        host["id"],
        {"role": "NameNode", "replicaCount": 3, "capacityBytes": 1099511627776},
    )
    raw = "jps -l\n4123 org.apache.hadoop.hdfs.server.datanode.DataNode"
    down = client.post(
        f"/api/hosts/{host['id']}/role-liveness",
        json={"roleLiveness": [{"role": "NameNode", "up": False, "raw": raw}]},
    )
    assert down.status_code == 200, down.text
    open_alerts = client.get("/api/alerts").json()["data"]
    assert len(open_alerts) == 1
    alert_id = open_alerts[0]["id"]

    up = client.post(
        f"/api/hosts/{host['id']}/role-liveness",
        json={
            "roleLiveness": [
                {"role": "NameNode", "up": True, "raw": "jps -l\n99 NameNode"}
            ]
        },
    )
    assert up.status_code == 200, up.text

    ended = client.get(f"/api/alerts/{alert_id}")
    assert ended.status_code == 200, ended.text
    body = ended.json()
    assert body["success"] is True
    assert body["code"] == "OK"
    assert body["message"] == "ok"
    assert body["data"]["id"] == alert_id
    assert body["data"]["hostId"] == host["id"]
    assert body["data"]["role"] == "NameNode"
    assert body["data"]["current"] is False
    assert body["data"]["evidence"] == raw
    assert client.get("/api/alerts").json()["data"] == []


def test_other_role_liveness_leaves_the_first_alert_unchanged(client: TestClient):
    host = _host(client)
    _place(
        client,
        host["id"],
        {"role": "NameNode", "replicaCount": 3, "capacityBytes": 1099511627776},
    )
    _place(
        client,
        host["id"],
        {"role": "DataNode", "replicaCount": 3, "capacityBytes": 1099511627776},
    )
    raw = "jps -l\n4123 org.apache.hadoop.hdfs.server.datanode.DataNode"
    down = client.post(
        f"/api/hosts/{host['id']}/role-liveness",
        json={"roleLiveness": [{"role": "NameNode", "up": False, "raw": raw}]},
    )
    assert down.status_code == 200, down.text
    alert_id = client.get("/api/alerts").json()["data"][0]["id"]

    for item in (
        {"role": "DataNode", "up": False, "raw": "jps -l\n50 DataNode"},
        {"role": "DataNode", "up": True, "raw": "jps -l\n50 DataNode\n99 NameNode"},
    ):
        reported = client.post(
            f"/api/hosts/{host['id']}/role-liveness",
            json={"roleLiveness": [item]},
        )
        assert reported.status_code == 200, reported.text
        got = client.get(f"/api/alerts/{alert_id}")
        assert got.status_code == 200, got.text
        alert = got.json()["data"]
        assert alert["id"] == alert_id
        assert alert["hostId"] == host["id"]
        assert alert["role"] == "NameNode"
        assert alert["current"] is True
        assert alert["evidence"] == raw


def test_report_without_role_liveness_field_opens_no_alert(client: TestClient):
    host = _host(client)
    _place(
        client,
        host["id"],
        {"role": "NameNode", "replicaCount": 3, "capacityBytes": 1099511627776},
    )
    response = client.post(
        f"/api/hosts/{host['id']}/role-liveness",
        json={"sentAt": "2026-11-15T00:00:00Z"},
    )
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["success"] is True
    assert body["code"] == "OK"
    assert body["message"] == "ok"
    listed = client.get("/api/alerts")
    assert listed.status_code == 200, listed.text
    assert listed.json()["data"] == []


_FORBIDDEN_KEYS = {
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


def _assert_no_operator(response, user_id: str) -> None:
    assert user_id not in response.text
    found: set[str] = set()

    def walk(value: object) -> None:
        if isinstance(value, dict):
            for key, child in value.items():
                found.add(key)
                walk(child)
        elif isinstance(value, list):
            for child in value:
                walk(child)

    walk(response.json())
    assert found.isdisjoint(_FORBIDDEN_KEYS)


def test_register_report_and_read_alert_need_no_user_and_record_no_operator(
    client: TestClient,
):
    user_id = "user-should-not-be-stored"
    headers = {"X-ArchOps-User-Id": user_id}
    cluster = client.post("/api/clusters", json={"name": "east-cluster"}, headers=headers)
    _assert_no_operator(cluster, user_id)
    assert cluster.status_code == 200, cluster.text
    host = client.post(
        f"/api/clusters/{cluster.json()['data']['id']}/hosts",
        json={"name": "host-a"},
        headers=headers,
    )
    _assert_no_operator(host, user_id)
    assert host.status_code == 200, host.text
    host_id = host.json()["data"]["id"]
    placed = client.post(
        f"/api/hosts/{host_id}/roles",
        json={"role": "NameNode", "replicaCount": 3, "capacityBytes": 1099511627776},
        headers=headers,
    )
    _assert_no_operator(placed, user_id)
    assert placed.status_code == 200, placed.text
    raw = "jps -l\n4123 org.apache.hadoop.hdfs.server.datanode.DataNode"
    reported = client.post(
        f"/api/hosts/{host_id}/role-liveness",
        json={"roleLiveness": [{"role": "NameNode", "up": False, "raw": raw}]},
        headers=headers,
    )
    _assert_no_operator(reported, user_id)
    assert reported.status_code == 200, reported.text
    listed = client.get("/api/alerts", headers=headers)
    _assert_no_operator(listed, user_id)
    assert listed.status_code == 200, listed.text
    alert = listed.json()["data"][0]
    assert alert["evidence"] == raw
    fetched = client.get(f"/api/alerts/{alert['id']}", headers=headers)
    _assert_no_operator(fetched, user_id)
    assert fetched.status_code == 200, fetched.text
    assert fetched.json()["data"]["id"] == alert["id"]

    anonymous = client.get("/api/alerts")
    assert anonymous.status_code == 200, anonymous.text
    assert anonymous.json()["data"][0]["id"] == alert["id"]
    _assert_no_operator(anonymous, user_id)


def _go_binary() -> str:
    found = shutil.which("go")
    if found:
        return found
    candidate = Path.home() / ".local" / "go" / "bin" / "go"
    return str(candidate)


class _Server(uvicorn.Server):
    def install_signal_handlers(self) -> None:
        return


def test_go_agent_down_report_opens_alert_with_process_listing(client: TestClient):
    host = _host(client)
    _place(
        client,
        host["id"],
        {"role": "NameNode", "replicaCount": 3, "capacityBytes": 1099511627776},
    )
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    from archops_control.app import create_app

    server = _Server(
        uvicorn.Config(
            create_app(),
            host="127.0.0.1",
            port=port,
            log_level="error",
        )
    )
    thread = threading.Thread(target=server.run, daemon=True)
    thread.start()
    deadline = time.time() + 5
    while not server.started:
        if time.time() > deadline:
            raise AssertionError("control plane did not start")
        time.sleep(0.01)
    raw = "java -Dproc=org.apache.hadoop.hdfs.server.datanode.DataNode"
    try:
        with tempfile.TemporaryDirectory() as tmp:
            cmdline = Path(tmp) / "proc" / "4123" / "cmdline"
            cmdline.parent.mkdir(parents=True)
            cmdline.write_bytes(
                b"java\x00-Dproc=org.apache.hadoop.hdfs.server.datanode.DataNode\x00"
            )
            completed = subprocess.run(
                [
                    _go_binary(),
                    "run",
                    ".",
                    "--control-plane",
                    f"http://127.0.0.1:{port}",
                    "--host-id",
                    host["id"],
                    "--role",
                    "NameNode",
                    "--proc-root",
                    str(Path(tmp) / "proc"),
                ],
                cwd=Path(__file__).resolve().parents[2] / "host-agent",
                capture_output=True,
                text=True,
                check=False,
            )
        assert completed.returncode == 0, completed.stderr
    finally:
        server.should_exit = True
        thread.join(timeout=5)

    listed = client.get("/api/alerts")
    assert listed.status_code == 200, listed.text
    alerts = listed.json()["data"]
    assert len(alerts) == 1
    assert alerts[0]["hostId"] == host["id"]
    assert alerts[0]["role"] == "NameNode"
    assert alerts[0]["current"] is True
    assert alerts[0]["evidence"] == raw
