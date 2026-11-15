"""HTTP API: register a cluster, a host, and the role config on that host."""

from __future__ import annotations

from contextlib import asynccontextmanager
from uuid import uuid4

import psycopg
from fastapi import FastAPI
from fastapi.responses import JSONResponse

from archops_control.db import connect, migrate

ALLOWED_ROLES = ("NameNode", "DataNode", "ResourceManager", "NodeManager")
HDFS_ROLES = frozenset({"NameNode", "DataNode"})
ROLE_NOT_ALLOWED = (
    "role must be "
    + ", ".join(ALLOWED_ROLES[:-1])
    + ", or "
    + ALLOWED_ROLES[-1]
)
ROLE_CONFIG_REQUIRED = (
    "HDFS role requires replicaCount and capacityBytes; "
    "YARN role requires queueName, memoryMb, and vcores"
)


class ApiError(Exception):
    def __init__(self, status: int, code: str, message: str) -> None:
        self.status = status
        self.code = code
        self.message = message


def ok(data: object) -> dict:
    return {"success": True, "code": "OK", "message": "ok", "data": data}


def fail(code: str, message: str) -> dict:
    return {"success": False, "code": code, "message": message, "data": None}


def require_role(name: object) -> str:
    if name not in ALLOWED_ROLES:
        raise ApiError(400, "ROLE_NOT_ALLOWED", ROLE_NOT_ALLOWED)
    return str(name)


def _positive_int(value: object) -> bool:
    return isinstance(value, int) and not isinstance(value, bool) and value >= 1


def role_config(body: dict) -> tuple:
    role = require_role(body.get("role"))
    if role in HDFS_ROLES:
        if not _positive_int(body.get("replicaCount")) or not _positive_int(
            body.get("capacityBytes")
        ):
            raise ApiError(400, "ROLE_CONFIG_REQUIRED", ROLE_CONFIG_REQUIRED)
        return role, body["replicaCount"], body["capacityBytes"], None, None, None
    queue = body.get("queueName")
    if (
        not isinstance(queue, str)
        or not queue.strip()
        or not _positive_int(body.get("memoryMb"))
        or not _positive_int(body.get("vcores"))
    ):
        raise ApiError(400, "ROLE_CONFIG_REQUIRED", ROLE_CONFIG_REQUIRED)
    return role, None, None, queue, body["memoryMb"], body["vcores"]


def alert_json(row: dict) -> dict:
    return {
        "id": str(row["id"]),
        "hostId": str(row["host_id"]),
        "role": row["role"],
        "current": row["current"],
        "evidence": row["evidence"],
    }


def role_json(row: dict) -> dict:
    return {
        "id": str(row["id"]),
        "hostId": str(row["host_id"]),
        "role": row["role"],
        "replicaCount": row["replica_count"],
        "capacityBytes": row["capacity_bytes"],
        "queueName": row["queue_name"],
        "memoryMb": row["memory_mb"],
        "vcores": row["vcores"],
    }


_ROLE_COLUMNS = """
    id, host_id, role, replica_count, capacity_bytes, queue_name, memory_mb, vcores
"""
_ALERT_COLUMNS = "id, host_id, role, current, evidence"


def end_role_alert(conn: psycopg.Connection, host_id: str, item: dict) -> None:
    role = require_role(item.get("role"))
    conn.execute(
        """
        UPDATE alert
        SET current = false
        WHERE host_id = %s AND role = %s AND current
        """,
        (host_id, role),
    )


def record_role_down(conn: psycopg.Connection, host_id: str, item: dict) -> None:
    if item.get("up") is not False:
        return
    role = require_role(item.get("role"))
    placed = conn.execute(
        "SELECT 1 AS placed FROM service_role WHERE host_id = %s AND role = %s",
        (host_id, role),
    ).fetchone()
    if placed is None:
        return
    conn.execute(
        """
        INSERT INTO alert (id, host_id, role, current, evidence)
        VALUES (%s, %s, %s, true, %s)
        ON CONFLICT (host_id, role) WHERE current DO NOTHING
        """,
        (str(uuid4()), host_id, role, item["raw"]),
    )


def create_app() -> FastAPI:
    @asynccontextmanager
    async def lifespan(_app: FastAPI):
        migrate()
        yield

    app = FastAPI(lifespan=lifespan)

    @app.exception_handler(ApiError)
    def handle_api_error(_request: object, exc: ApiError) -> JSONResponse:
        return JSONResponse(
            status_code=exc.status,
            content=fail(exc.code, exc.message),
        )

    @app.post("/api/clusters")
    def create_cluster(body: dict) -> dict:
        cluster_id = str(uuid4())
        with connect() as conn:
            conn.execute(
                "INSERT INTO cluster (id, name) VALUES (%s, %s)",
                (cluster_id, body["name"]),
            )
        return ok({"id": cluster_id, "name": body["name"]})

    @app.post("/api/clusters/{cluster_id}/hosts")
    def create_host(cluster_id: str, body: dict) -> dict:
        host_id = str(uuid4())
        with connect() as conn:
            conn.execute(
                "INSERT INTO host (id, cluster_id, name) VALUES (%s, %s, %s)",
                (host_id, cluster_id, body["name"]),
            )
        return ok({"id": host_id, "clusterId": cluster_id, "name": body["name"]})

    @app.post("/api/hosts/{host_id}/roles")
    def create_role(host_id: str, body: dict) -> dict:
        role, replica_count, capacity_bytes, queue_name, memory_mb, vcores = role_config(
            body
        )
        role_id = str(uuid4())
        with connect() as conn:
            conn.execute(
                f"""
                INSERT INTO service_role (
                    {_ROLE_COLUMNS}
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                """,
                (
                    role_id,
                    host_id,
                    role,
                    replica_count,
                    capacity_bytes,
                    queue_name,
                    memory_mb,
                    vcores,
                ),
            )
            row = conn.execute(
                f"SELECT {_ROLE_COLUMNS} FROM service_role WHERE id = %s",
                (role_id,),
            ).fetchone()
        return ok(role_json(row))

    @app.get("/api/hosts/{host_id}/roles")
    def list_roles(host_id: str) -> dict:
        with connect() as conn:
            rows = conn.execute(
                f"""
                SELECT {_ROLE_COLUMNS}
                FROM service_role
                WHERE host_id = %s
                ORDER BY role
                """,
                (host_id,),
            ).fetchall()
        return ok([role_json(row) for row in rows])

    @app.post("/api/hosts/{host_id}/role-liveness")
    def report_role_liveness(host_id: str, body: dict) -> dict:
        entries = body.get("roleLiveness")
        if not entries:
            return ok(None)
        with connect() as conn:
            for item in entries:
                if item.get("up") is True:
                    end_role_alert(conn, host_id, item)
                else:
                    record_role_down(conn, host_id, item)
        return ok(None)

    @app.get("/api/alerts")
    def list_current_alerts() -> dict:
        with connect() as conn:
            rows = conn.execute(
                f"""
                SELECT {_ALERT_COLUMNS}
                FROM alert
                WHERE current
                ORDER BY role
                """
            ).fetchall()
        return ok([alert_json(row) for row in rows])

    @app.get("/api/alerts/{alert_id}")
    def get_alert(alert_id: str) -> dict:
        with connect() as conn:
            row = conn.execute(
                f"SELECT {_ALERT_COLUMNS} FROM alert WHERE id = %s",
                (alert_id,),
            ).fetchone()
        if row is None:
            raise ApiError(404, "NOT_FOUND", "Not found")
        return ok(alert_json(row))

    return app
