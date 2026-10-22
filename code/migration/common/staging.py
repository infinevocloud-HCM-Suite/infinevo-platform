"""Reading staging files. The directory is MIGRATION_STAGING_DIR, else code/migration/staging."""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path

_DEFAULT = Path(__file__).resolve().parents[1] / "staging"


def staging_dir() -> Path:
    d = Path(os.environ.get("MIGRATION_STAGING_DIR") or _DEFAULT)
    d.mkdir(parents=True, exist_ok=True)
    return d


def stg_path(name: str) -> Path:
    return staging_dir() / name


def load(name: str) -> list[dict]:
    """A staging JSON list; a missing file is an empty list (the table had no rows)."""
    p = stg_path(name)
    if not p.exists():
        return []
    with open(p, encoding="utf-8") as f:
        data = json.load(f)
    if not isinstance(data, list):
        raise ValueError(f"{p} is not a JSON list")
    return data


def write(name: str, rows) -> Path:
    p = stg_path(name)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(rows, f, indent=2, default=str)
    return p


def checksum(names: list[str]) -> str:
    """sha256 over the named staging files, in the order given; absent files count as empty."""
    h = hashlib.sha256()
    for name in names:
        h.update(name.encode())
        p = stg_path(name)
        if p.exists():
            h.update(p.read_bytes())
        h.update(b"\0")
    return h.hexdigest()
