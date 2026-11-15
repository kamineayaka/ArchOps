"""HTTP acceptance for cluster, role, host, and role-liveness alerts.

Seam: the Python control plane HTTP API (docs/specs/big-data-platform.md).
"""

from __future__ import annotations

import os

import psycopg
import pytest
from fastapi.testclient import TestClient

DATABASE_URL = os.environ.get(
    "ARCHOPS_PLATFORM_DATABASE_URL",
    "postgresql://archops:archops@127.0.0.1:5432/archops_platform",
)
ADMIN_URL = os.environ.get(
    "ARCHOPS_PLATFORM_ADMIN_URL",
    "postgresql://archops:archops@127.0.0.1:5432/archops",
)
RESET_TABLES = ("alert", "service_role", "host", "cluster")


def _ensure_database() -> None:
    with psycopg.connect(ADMIN_URL, autocommit=True) as conn:
        exists = conn.execute(
            "SELECT 1 FROM pg_database WHERE datname = %s",
            ("archops_platform",),
        ).fetchone()
        if exists is None:
            conn.execute("CREATE DATABASE archops_platform")


def _truncate() -> None:
    with psycopg.connect(DATABASE_URL, autocommit=True) as conn:
        rows = conn.execute(
            """
            SELECT tablename FROM pg_tables
            WHERE schemaname = 'public' AND tablename = ANY(%s)
            """,
            (list(RESET_TABLES),),
        ).fetchall()
        present = {row[0] for row in rows}
        ordered = [name for name in RESET_TABLES if name in present]
        if ordered:
            conn.execute("TRUNCATE " + ", ".join(ordered) + " CASCADE")


@pytest.fixture()
def client():
    _ensure_database()
    os.environ["ARCHOPS_PLATFORM_DATABASE_URL"] = DATABASE_URL
    from archops_control.app import create_app

    app = create_app()
    with TestClient(app) as test_client:
        _truncate()
        yield test_client


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
