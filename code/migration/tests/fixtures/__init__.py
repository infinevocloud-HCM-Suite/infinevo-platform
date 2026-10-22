"""Synthetic legacy data, shaped exactly like the extractors' staging files.

No real person is in here: names, PAN, Aadhaar and bank numbers are invented. Each area
lives in its own module fx_<area>.py exposing `rows() -> {staging_file_name: [row, ...]}`;
write_all() merges every module, so a loader's fixture is added without touching others.

The shape follows W-68 Task 4: two tenants (legacy org 148 is the founder tenant and
also owns the HRMS data; org 201 is a Payroll-only customer), overlapping employees,
one duplicate and one record with no joining date.
"""
from __future__ import annotations

import importlib
import json
import pkgutil
from pathlib import Path

FOUNDER_ORG = "148"
CUSTOMER_ORG = "201"
DELETED_ORG = "202"
UNMAPPED_ORG = "203"
FOUNDER_TENANT = "a0000000-0000-0000-0000-000000000001"
CUSTOMER_TENANT = "c0000000-0000-0000-0000-000000000201"
DELETED_TENANT = "c0000000-0000-0000-0000-000000000202"

ORG_MAPPING = {
    FOUNDER_ORG: {"tenant_id": FOUNDER_TENANT, "name": "Infinevo Founder Co", "country_code": "IN", "founder": True},
    CUSTOMER_ORG: {"tenant_id": CUSTOMER_TENANT, "name": "Acme Customer Pvt Ltd", "country_code": "IN"},
    DELETED_ORG: {"tenant_id": DELETED_TENANT, "name": "Gone Ltd", "country_code": "IN"},
}

LIVE_TENANTS = (FOUNDER_TENANT, CUSTOMER_TENANT)


def all_rows() -> dict[str, list]:
    merged: dict[str, list] = {}
    pkg = Path(__file__).parent
    for mod in sorted(m.name for m in pkgutil.iter_modules([str(pkg)]) if m.name.startswith("fx_")):
        module = importlib.import_module(f"tests.fixtures.{mod}")
        for name, rows in module.rows().items():
            if name in merged:
                raise ValueError(f"staging file {name} defined by two fixture modules")
            merged[name] = rows
    return merged


def write_all(staging_dir: Path) -> None:
    for name, rows in all_rows().items():
        (Path(staging_dir) / name).write_text(json.dumps(rows, indent=1, default=str), encoding="utf-8")
