"""Which legacy organisations become tenants (W-67 R5).

config/org_mapping.json maps a Payroll organization id to
{"tenant_id", "name", "country_code", "keep" (default true), "founder" (one entry),
"hrms_only" (founder only, default true)}.
An organisation absent from the map, or with keep=false, is skipped by every loader.
HRMS has no organisation column (BUG-002), so HRMS rows belong to the founder tenant.
hrms_only=false leaves HRMS-only employees (cohort 2) and their rows behind (W-67 R1, 2026-10-26).
MIGRATION_ORG_MAPPING overrides the file path (tests use their own map).
"""
from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path

_DEFAULT = Path(__file__).resolve().parents[1] / "config" / "org_mapping.json"


def _flag(value, name: str, org_id) -> bool:
    """A JSON true/false only: "false" in quotes would read as true and change what migrates."""
    if not isinstance(value, bool):
        raise ValueError(f"org_mapping.json org {org_id}: {name} must be true or false, not {value!r}")
    return value


@dataclass(frozen=True)
class Tenant:
    legacy_org_id: str
    tenant_id: str
    name: str
    country_code: str
    founder: bool
    hrms_only: bool = True


class Tenants:
    def __init__(self, by_org: dict[str, Tenant]):
        self.by_org = by_org
        founders = [t for t in by_org.values() if t.founder]
        if len(founders) > 1:
            raise ValueError("org_mapping.json marks more than one founder tenant")
        self.founder = founders[0] if founders else None

    @classmethod
    def load(cls) -> "Tenants":
        path = Path(os.environ.get("MIGRATION_ORG_MAPPING") or _DEFAULT)
        with open(path, encoding="utf-8") as f:
            raw = json.load(f)
        by_org = {}
        for org_id, e in raw.items():
            if not e.get("keep", True):
                continue
            by_org[str(org_id)] = Tenant(
                legacy_org_id=str(org_id),
                tenant_id=e["tenant_id"],
                name=e["name"],
                country_code=e.get("country_code", "IN"),
                founder=bool(e.get("founder")),
                hrms_only=_flag(e.get("hrms_only", True), "hrms_only", org_id),
            )
        return cls(by_org)

    def for_org(self, legacy_org_id) -> Tenant | None:
        if legacy_org_id is None:
            return None
        return self.by_org.get(str(legacy_org_id))

    def all(self) -> list[Tenant]:
        return list(self.by_org.values())
