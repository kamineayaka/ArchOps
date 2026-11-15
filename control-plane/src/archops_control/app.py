"""HTTP API: register a cluster, a host, and the role config on that host."""

from __future__ import annotations

from contextlib import asynccontextmanager
from uuid import uuid4

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from archops_control.db import connect, migrate

ALLOWED_ROLES = ("NameNode", "DataNode", "ResourceManager", "NodeManager")
ROLE_NOT_ALLOWED = (
    "role must be "
    + ", ".join(ALLOWED_ROLES[:-1])
    + ", or "
    + ALLOWED_ROLES[-1]
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
        role = require_role(body.get("role"))
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
                    body.get("replicaCount"),
                    body.get("capacityBytes"),
                    body.get("queueName"),
                    body.get("memoryMb"),
                    body.get("vcores"),
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

    return app
