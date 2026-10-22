"""Rows a loader refuses: written to staging/stg_quarantined_<loader>.json with a reason,
counted in records_skipped, never loaded (W-68 Task 3 "Quarantine"). The entrypoint
uploads stg_quarantined_* with each rehearsal's results (W-71 Task 2 step 6), so a
record carries ids and the reason only - never names, PAN, Aadhaar or bank numbers."""
from __future__ import annotations

from . import staging


class Quarantine:
    def __init__(self, loader: str):
        self.loader = loader
        self.items: list[dict] = []

    def add(self, reason: str, legacy_table: str, legacy_id=None, tenant_id: str | None = None, **detail) -> None:
        item = {"reason": reason, "legacy_table": legacy_table, "legacy_id": legacy_id}
        if tenant_id:
            item["tenant_id"] = tenant_id
        item.update({k: v for k, v in detail.items() if v is not None})
        self.items.append(item)

    def count(self, tenant_id: str | None = None) -> int:
        if tenant_id is None:
            return len(self.items)
        return sum(1 for i in self.items if i.get("tenant_id") == tenant_id)

    def write(self) -> None:
        """Rewrite the file. Entries of tenants the ledger skipped as unchanged in this run
        are carried over from the previous file: their work did not run, so this run found
        nothing new for them, and dropping the old entries would hide them."""
        import json

        from .ledger import SKIPPED_UNCHANGED

        name = f"stg_quarantined_{self.loader}.json"
        keep = SKIPPED_UNCHANGED.get(self.loader, set())
        carried = []
        path = staging.stg_path(name)
        if keep and path.exists():
            with open(path, encoding="utf-8") as f:
                carried = [i for i in json.load(f) if i.get("tenant_id") in keep]
        staging.write(name, carried + [i for i in self.items if i.get("tenant_id") not in keep])
