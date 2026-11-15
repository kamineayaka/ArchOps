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


def test_registered_namenode_and_nodemanager_configs_are_readable(client: TestClient):
    cluster = _post_ok(client, "/api/clusters", {"name": "east-cluster"})
    host = _post_ok(
        client,
        f"/api/clusters/{cluster['id']}/hosts",
        {"name": "host-a"},
    )
    _post_ok(
        client,
        f"/api/hosts/{host['id']}/roles",
        {"role": "NameNode", "replicaCount": 3, "capacityBytes": 1099511627776},
    )
    _post_ok(
        client,
        f"/api/hosts/{host['id']}/roles",
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
