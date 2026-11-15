"""Run the Python control plane. Port defaults to 8081 so it can sit beside the Java process."""

from __future__ import annotations

import os

import uvicorn

from archops_control.app import create_app


def main() -> None:
    port = int(os.environ.get("ARCHOPS_PLATFORM_PORT", "8081"))
    uvicorn.run(create_app(), host="0.0.0.0", port=port)


if __name__ == "__main__":
    main()
