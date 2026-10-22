"""Run pipeline steps in order inside a test. Each step's run() returns True on success."""
from __future__ import annotations

import importlib

# Run order (W-68 §3). Steps not yet present on the branch are skipped by run_until().
ORDER = [
    "transformers.merge_employees",
    "loaders.load_tenant",
    "loaders.load_org",
    "loaders.load_employees",
    "loaders.load_users",
    "loaders.load_salary",
    "loaders.load_statutory",
    "loaders.load_payruns",
    "loaders.load_tax",
    "loaders.load_leave",
    "loaders.load_timesheets",
    "loaders.load_attendance",
    "loaders.load_claims",
    "loaders.load_notifications",
]


BASE = ORDER[:4]  # merge, tenant, org, employees: what every later loader needs


def run_steps(modules: list[str]) -> None:
    """Run exactly these steps, in the order given."""
    for module in modules:
        run_step(module)


def run_base_and(*modules: str) -> None:
    """The four base steps, then the named loader(s). Use this in a loader's own tests."""
    run_steps(BASE + list(modules))


def run_step(module: str) -> None:
    mod = importlib.import_module(module)
    result = mod.run()
    if module.startswith("transformers."):
        return
    assert result, f"{module}.run() reported failure"


def run_until(last: str) -> None:
    """Run every step from the start up to and including `last` (a module name)."""
    for module in ORDER:
        run_step(module)
        if module == last:
            return
    raise ValueError(f"{last} is not a pipeline step")
