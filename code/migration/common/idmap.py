"""staging/id_map.json: {legacy_table: {legacy_id: target_uuid}} (W-68 §3).

Written by the transform step, appended by every loader. Every foreign key a later loader
needs is resolved through it; an id that is not in the map sends the row to quarantine."""
from __future__ import annotations

import json

from . import staging

FILE = "id_map.json"


class IdMap:
    def __init__(self, data: dict | None = None):
        self._data: dict[str, dict[str, str]] = data or {}

    @classmethod
    def load(cls) -> "IdMap":
        p = staging.stg_path(FILE)
        if not p.exists():
            return cls()
        with open(p, encoding="utf-8") as f:
            return cls(json.load(f))

    def save(self) -> None:
        staging.write(FILE, self._data)

    def put(self, table: str, legacy_id, target_id: str) -> None:
        if legacy_id is None or legacy_id == "":
            return
        self._data.setdefault(table, {})[str(legacy_id)] = str(target_id)

    def get(self, table: str, legacy_id) -> str | None:
        if legacy_id is None or legacy_id == "":
            return None
        return self._data.get(table, {}).get(str(legacy_id))

    def table(self, table: str) -> dict[str, str]:
        return dict(self._data.get(table, {}))
