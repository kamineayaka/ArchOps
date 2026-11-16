"""Shared HTTP fixtures for the Python control plane."""

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
RESET_TABLES = ("repair_step", "repair", "alert", "service_role", "host", "cluster")


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
