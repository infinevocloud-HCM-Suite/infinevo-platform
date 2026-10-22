"""Deterministic ids (W-68 Task 3 "Upsert"): the same legacy row always gets the same
target id, so a second run updates in place instead of inserting twice."""
from __future__ import annotations

import re
import uuid

# The namespace merge_employees.py has used since the branch; employee ids depend on it.
NAMESPACE = uuid.UUID("6ba7b810-9dad-11d1-80b4-00c04fd430c8")


def det_id(tenant_id: str, legacy_table: str, legacy_id) -> str:
    """uuid5(namespace, 'tenant_id:legacy_table:legacy_id')."""
    return str(uuid.uuid5(NAMESPACE, f"{tenant_id}:{legacy_table}:{legacy_id}"))


def child_id(parent_id: str, label: str) -> str:
    """A one-to-one child row keyed by its parent (employee_personal of an employee)."""
    return str(uuid.uuid5(uuid.UUID(str(parent_id)), label))


def slug_code(name: str, max_len: int = 50) -> str:
    """Upper-snake code from a display name: 'Human Resources' -> 'HUMAN_RESOURCES'."""
    code = re.sub(r"[^A-Za-z0-9]+", "_", (name or "").strip()).strip("_").upper()
    return (code or "UNNAMED")[:max_len]


def unique_codes(names: list[str], max_len: int = 50) -> list[str]:
    """One code per input, in order: slug of the name, and _2, _3 ... where two rows of
    the tenant would otherwise share a code (unique (tenant_id, code) in the target)."""
    out, used = [], set()
    for name in names:
        base = slug_code(name, max_len)
        code, n = base, 2
        while code in used:
            suffix = f"_{n}"
            code = base[: max_len - len(suffix)] + suffix
            n += 1
        used.add(code)
        out.append(code)
    return out
