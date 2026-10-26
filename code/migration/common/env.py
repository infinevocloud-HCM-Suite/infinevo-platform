"""Configuration comes from the environment only (W-68 §3): no host, user or password
has a fallback value. A missing variable stops the process with exit code 2."""
from __future__ import annotations

import os
import sys


def require(name: str, allow_empty: bool = False) -> str:
    value = os.environ.get(name)
    if value is None or (not allow_empty and value == ""):
        print(f"ERROR: environment variable {name} is not set", file=sys.stderr)
        sys.exit(2)
    return value


def optional(name: str, default: str) -> str:
    """For non-secret tuning values only (ports, batch sizes, directories)."""
    value = os.environ.get(name)
    return default if value in (None, "") else value
