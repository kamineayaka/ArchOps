"""One issued repair step for a current role alert.

The control plane freezes the script when the repair starts and hands the
agent only the step it has issued. The agent does not pick the next step.
A non-zero exit stops the repair. Step output stays on the repair step and
is never copied onto the alert.
"""

from __future__ import annotations

from uuid import uuid4

import psycopg
from psycopg.types.json import Json

from archops_control.errors import ApiError

PLAYBOOKS: dict[str, tuple[tuple[str, ...], ...]] = {
    "NameNode": (("hdfs", "--daemon", "start", "namenode"),),
    "DataNode": (("hdfs", "--daemon", "start", "datanode"),),
    "ResourceManager": (("yarn", "--daemon", "start", "resourcemanager"),),
    "NodeManager": (
        ("yarn", "--daemon", "start", "nodemanager"),
        ("yarn", "rmadmin", "-refreshQueues"),
    ),
}

_OUTPUT_LIMIT = 8192
_REPAIR_COLUMNS = "id, alert_id, host_id, role, status, playbook, issued_seq"
_STEP_COLUMNS = "repair_id, seq, argv, state, exit_code, output"


def _int(value: object, message: str) -> int:
    if isinstance(value, bool) or not isinstance(value, int):
        raise ApiError(400, "REPAIR_RESULT_INVALID", message)
    return value


def _steps(conn: psycopg.Connection, repair_id: str) -> list[dict]:
    return conn.execute(
        f"""
        SELECT {_STEP_COLUMNS}
        FROM repair_step
        WHERE repair_id = %s
        ORDER BY seq
        """,
        (repair_id,),
    ).fetchall()


def repair_json(repair: dict, steps: list[dict]) -> dict:
    issued = None
    for step in steps:
        if step["state"] == "issued":
            issued = {"seq": step["seq"], "argv": step["argv"]}
    return {
        "id": str(repair["id"]),
        "alertId": str(repair["alert_id"]),
        "hostId": str(repair["host_id"]),
        "role": repair["role"],
        "status": repair["status"],
        "issued": issued,
        "steps": [
            {
                "seq": step["seq"],
                "argv": step["argv"],
                "state": step["state"],
                "exitCode": step["exit_code"],
                "output": step["output"],
            }
            for step in steps
        ],
    }


def _load(conn: psycopg.Connection, repair_id: str) -> dict:
    repair = conn.execute(
        f"SELECT {_REPAIR_COLUMNS} FROM repair WHERE id = %s",
        (repair_id,),
    ).fetchone()
    return repair_json(repair, _steps(conn, repair_id))


def load_repair(conn: psycopg.Connection, repair_id: str) -> dict:
    repair = conn.execute(
        f"SELECT {_REPAIR_COLUMNS} FROM repair WHERE id = %s",
        (repair_id,),
    ).fetchone()
    if repair is None:
        raise ApiError(404, "NOT_FOUND", "Not found")
    return repair_json(repair, _steps(conn, str(repair["id"])))


def start_repair(conn: psycopg.Connection, alert_id: str) -> dict:
    alert = conn.execute(
        "SELECT id, host_id, role, current FROM alert WHERE id = %s",
        (alert_id,),
    ).fetchone()
    if alert is None:
        raise ApiError(404, "NOT_FOUND", "Not found")
    existing = conn.execute(
        f"SELECT {_REPAIR_COLUMNS} FROM repair WHERE alert_id = %s",
        (alert_id,),
    ).fetchone()
    if existing is not None:
        return repair_json(existing, _steps(conn, str(existing["id"])))
    if alert["current"] is not True:
        raise ApiError(409, "ALERT_NOT_CURRENT", "Alert is not current")
    busy = conn.execute(
        "SELECT 1 AS busy FROM repair WHERE host_id = %s AND status = 'open'",
        (alert["host_id"],),
    ).fetchone()
    if busy is not None:
        raise ApiError(
            409,
            "HOST_REPAIR_BUSY",
            "Host already has an issued repair step",
        )
    playbook = [list(step) for step in PLAYBOOKS[alert["role"]]]
    repair_id = str(uuid4())
    conn.execute(
        f"""
        INSERT INTO repair ({_REPAIR_COLUMNS})
        VALUES (%s, %s, %s, %s, 'open', %s, 1)
        """,
        (
            repair_id,
            alert_id,
            alert["host_id"],
            alert["role"],
            Json(playbook),
        ),
    )
    conn.execute(
        f"""
        INSERT INTO repair_step ({_STEP_COLUMNS})
        VALUES (%s, 1, %s, 'issued', NULL, NULL)
        """,
        (repair_id, Json(playbook[0])),
    )
    return _load(conn, repair_id)


def issued_step(conn: psycopg.Connection, host_id: str) -> dict | None:
    row = conn.execute(
        """
        SELECT r.id AS repair_id, s.seq, s.argv
        FROM repair r
        JOIN repair_step s ON s.repair_id = r.id
        WHERE r.host_id = %s AND r.status = 'open' AND s.state = 'issued'
        ORDER BY s.seq
        LIMIT 1
        """,
        (host_id,),
    ).fetchone()
    if row is None:
        return None
    return {
        "repairId": str(row["repair_id"]),
        "seq": row["seq"],
        "argv": row["argv"],
    }


def record_result(conn: psycopg.Connection, host_id: str, body: dict) -> dict:
    repair_id = body.get("repairId")
    if not isinstance(repair_id, str) or not repair_id:
        raise ApiError(400, "REPAIR_RESULT_INVALID", "repairId is required")
    seq = _int(body.get("seq"), "seq must be an integer")
    exit_code = _int(body.get("exitCode"), "exitCode must be an integer")
    output = body.get("output")
    if not isinstance(output, str):
        raise ApiError(400, "REPAIR_RESULT_INVALID", "output must be a string")
    output = output[:_OUTPUT_LIMIT]
    repair = conn.execute(
        f"SELECT {_REPAIR_COLUMNS} FROM repair WHERE id = %s FOR UPDATE",
        (repair_id,),
    ).fetchone()
    if repair is None or str(repair["host_id"]) != host_id:
        raise ApiError(404, "NOT_FOUND", "Not found")
    step = conn.execute(
        f"""
        SELECT {_STEP_COLUMNS}
        FROM repair_step
        WHERE repair_id = %s AND seq = %s
        FOR UPDATE
        """,
        (repair_id, seq),
    ).fetchone()
    if step is None or seq != repair["issued_seq"]:
        raise ApiError(409, "STEP_NOT_ISSUED", "That step is not issued")
    if step["state"] != "issued":
        return _receipt(conn, repair_id, seq)
    state = "succeeded" if exit_code == 0 else "failed"
    conn.execute(
        """
        UPDATE repair_step
        SET state = %s, exit_code = %s, output = %s
        WHERE repair_id = %s AND seq = %s AND state = 'issued'
        """,
        (state, exit_code, output, repair_id, seq),
    )
    playbook = repair["playbook"]
    if exit_code != 0:
        conn.execute(
            "UPDATE repair SET status = 'stopped' WHERE id = %s",
            (repair_id,),
        )
    elif seq < len(playbook):
        next_seq = seq + 1
        conn.execute(
            f"""
            INSERT INTO repair_step ({_STEP_COLUMNS})
            VALUES (%s, %s, %s, 'issued', NULL, NULL)
            """,
            (repair_id, next_seq, Json(list(playbook[next_seq - 1]))),
        )
        conn.execute(
            "UPDATE repair SET issued_seq = %s WHERE id = %s",
            (next_seq, repair_id),
        )
    else:
        conn.execute(
            "UPDATE repair SET status = 'completed' WHERE id = %s",
            (repair_id,),
        )
    return _receipt(conn, repair_id, seq)


def _receipt(conn: psycopg.Connection, repair_id: str, seq: int) -> dict:
    repair = conn.execute(
        f"SELECT {_REPAIR_COLUMNS} FROM repair WHERE id = %s",
        (repair_id,),
    ).fetchone()
    step = conn.execute(
        f"""
        SELECT {_STEP_COLUMNS}
        FROM repair_step
        WHERE repair_id = %s AND seq = %s
        """,
        (repair_id, seq),
    ).fetchone()
    return {
        "repairId": str(repair["id"]),
        "seq": step["seq"],
        "state": step["state"],
        "exitCode": step["exit_code"],
        "output": step["output"],
        "status": repair["status"],
    }
