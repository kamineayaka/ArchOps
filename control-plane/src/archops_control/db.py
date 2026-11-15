"""PostgreSQL connection and forward-only SQL migrations."""

from __future__ import annotations

import os
from collections.abc import Iterator
from contextlib import contextmanager
from pathlib import Path

import psycopg
from psycopg.rows import dict_row

MIGRATIONS = Path(__file__).resolve().parents[2] / "migrations"


def database_url() -> str:
    return os.environ["ARCHOPS_PLATFORM_DATABASE_URL"]


def migrate() -> None:
    with psycopg.connect(database_url(), autocommit=True) as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS schema_migration (
                version text PRIMARY KEY
            )
            """
        )
        applied = {
            row[0]
            for row in conn.execute("SELECT version FROM schema_migration").fetchall()
        }
        for path in sorted(MIGRATIONS.glob("*.sql")):
            if path.name in applied:
                continue
            conn.execute(path.read_text())
            conn.execute(
                "INSERT INTO schema_migration (version) VALUES (%s)",
                (path.name,),
            )


@contextmanager
def connect() -> Iterator[psycopg.Connection]:
    with psycopg.connect(database_url(), row_factory=dict_row) as conn:
        yield conn
        conn.commit()
