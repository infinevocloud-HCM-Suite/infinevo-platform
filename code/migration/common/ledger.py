"""core.migration_ledger and the per-tenant step runner (W-68 Task 3 "Ledger").

One ledger row per loader per tenant. A step whose DONE row carries the same input
checksum is skipped. Each tenant's work and its DONE row commit together, so a crash
leaves either the whole tenant step or none of it, and a re-run picks up where it stopped.
"""
from __future__ import annotations

import logging
import traceback
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Callable

from . import db
from .ids import NAMESPACE
from .tenants import Tenant

import uuid

log = logging.getLogger("migration.ledger")


@dataclass
class StepResult:
    processed: int = 0
    skipped: int = 0


@dataclass
class RunSummary:
    step: str
    done: list[str] = field(default_factory=list)
    skipped_unchanged: list[str] = field(default_factory=list)
    failed: dict[str, str] = field(default_factory=dict)

    @property
    def ok(self) -> bool:
        return not self.failed


# Tenants each step skipped as unchanged in this process, so Quarantine.write() can keep
# their earlier quarantine entries instead of losing them when it rewrites the file.
SKIPPED_UNCHANGED: dict[str, set[str]] = {}


def _ledger_id(tenant_id: str, step: str) -> str:
    return str(uuid.uuid5(NAMESPACE, f"ledger:{tenant_id}:{step}"))


def already_done(cur, tenant_id: str, step: str, checksum: str) -> bool:
    cur.execute(
        "SELECT 1 FROM core.migration_ledger WHERE tenant_id = %s AND step_name = %s "
        "AND status = 'DONE' AND input_checksum = %s",
        (tenant_id, step, checksum),
    )
    return cur.fetchone() is not None


def _write(cur, tenant_id: str, step: str, status: str, checksum: str, result: StepResult | None,
           started: datetime, error: str | None = None) -> None:
    cur.execute(
        """
        INSERT INTO core.migration_ledger
            (id, tenant_id, step_name, records_processed, records_skipped, input_checksum,
             status, started_at, completed_at, error_message)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        ON CONFLICT (id) DO UPDATE SET
            records_processed = EXCLUDED.records_processed,
            records_skipped = EXCLUDED.records_skipped,
            input_checksum = EXCLUDED.input_checksum,
            status = EXCLUDED.status,
            started_at = EXCLUDED.started_at,
            completed_at = EXCLUDED.completed_at,
            error_message = EXCLUDED.error_message
        """,
        (
            _ledger_id(tenant_id, step), tenant_id, step,
            result.processed if result else 0, result.skipped if result else 0, checksum,
            status, started, datetime.now(timezone.utc) if status != "RUNNING" else None,
            (error or "")[:4000] or None,
        ),
    )


def run_per_tenant(conn, step: str, tenants: list[Tenant], checksum: str,
                   work: Callable[[object, Tenant], StepResult], force: bool = False,
                   creates_tenants: bool = False) -> RunSummary:
    """Run work(cur, tenant) for each tenant in its own transaction, ledgered.

    work must not commit. Its writes and the DONE ledger row commit together. A failure
    rolls the tenant back and records FAILED in a separate transaction; the other tenants
    still run, and the summary says which failed.

    Only tenants already in core.tenant run - load_tenant decides which organisations
    become tenants (deleted, unmapped and missing ones do not). load_tenant itself passes
    creates_tenants=True.
    """
    summary = RunSummary(step)
    SKIPPED_UNCHANGED[step] = set()
    if not creates_tenants:
        with conn.cursor() as cur:
            cur.execute("SELECT tenant_id::text FROM core.tenant")
            existing = {r[0] for r in cur.fetchall()}
        conn.rollback()
        tenants = [t for t in tenants if t.tenant_id in existing]
    for tenant in tenants:
        tid = tenant.tenant_id
        started = datetime.now(timezone.utc)
        try:
            with conn.cursor() as cur:
                db.set_tenant(cur, tid)
                if not force and _tenant_exists(cur, tid) and already_done(cur, tid, step, checksum):
                    log.info("SKIP (DONE, checksum match) %s tenant=%s", step, tid)
                    summary.skipped_unchanged.append(tid)
                    SKIPPED_UNCHANGED.setdefault(step, set()).add(tid)
                    conn.rollback()
                    continue
                result = work(cur, tenant) or StepResult()
                _write(cur, tid, step, "DONE", checksum, result, started)
            conn.commit()
            summary.done.append(tid)
            log.info("DONE %s tenant=%s processed=%s skipped=%s", step, tid, result.processed, result.skipped)
        except Exception as exc:  # noqa: BLE001 - recorded, reported, and the step fails
            conn.rollback()
            summary.failed[tid] = f"{type(exc).__name__}: {exc}"
            log.error("FAILED %s tenant=%s: %s", step, tid, exc)
            log.debug(traceback.format_exc())
            try:
                with conn.cursor() as cur:
                    if _tenant_exists(cur, tid):
                        db.set_tenant(cur, tid)
                        _write(cur, tid, step, "FAILED", checksum, None, started, summary.failed[tid])
                conn.commit()
            except Exception:  # noqa: BLE001
                conn.rollback()
    return summary


def _tenant_exists(cur, tenant_id: str) -> bool:
    cur.execute("SELECT 1 FROM core.tenant WHERE tenant_id = %s", (tenant_id,))
    return cur.fetchone() is not None
