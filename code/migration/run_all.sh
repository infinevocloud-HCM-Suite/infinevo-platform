#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# run_all.sh — the whole legacy data migration, in W-68 §3 order (W-68 to W-70).
#
#   extract   HRMS and Payroll MySQL -> staging/*.json (and staging/organisations.md)
#   transform merge employees across the two systems -> stg_merged_employees.json, id_map.json
#   load      tenant, org, employees, users, salary, statutory, payruns, tax, leave,
#             timesheets, attendance, claims, notifications, documents (W-69)
#   reconcile LOP mismatch log and financial reconciliation (W-70)
#
# Every loader ledgers itself in core.migration_ledger per tenant and skips a tenant whose
# input is unchanged, so a re-run after a failure resumes rather than repeats.
# Stops at the first failed step (set -euo pipefail); a loader exits 1 if any tenant failed.
# Configuration is environment only (config/config.example.yaml lists the variables).
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_BIN="${PYTHON_BIN:-python3}"
export PYTHONPATH="${SCRIPT_DIR}${PYTHONPATH:+:${PYTHONPATH}}"

echo "================================================================="
echo " Infinevo data migration - started $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "================================================================="

STEP_NO=0
run_step() {
  local label="$1" module="$2"
  STEP_NO=$((STEP_NO + 1))
  echo ""
  echo ">>> [${STEP_NO}] ${label} (${module})"
  local start; start="$(date +%s)"
  "$PYTHON_BIN" -m "$module"
  echo ">>> [${STEP_NO}] done in $(( $(date +%s) - start ))s"
}

run_step "Extract HRMS"                         extractors.extract_hrms
run_step "Extract Payroll"                      extractors.extract_payroll
run_step "Merge employees (3 cohorts)"          transformers.merge_employees
run_step "Tenants and subscriptions"            loaders.load_tenant
run_step "Departments, designations, locations, pay schedule, tax deductor" loaders.load_org
run_step "Employees and details, reporting lines" loaders.load_employees
run_step "Users, roles, invitations"            loaders.load_users
run_step "Salary components and CTC"            loaders.load_salary
run_step "EPF, ESI, professional tax"           loaders.load_statutory
run_step "Pay runs and prior payroll"           loaders.load_payruns
run_step "Tax declarations, proofs, TDS"        loaders.load_tax
run_step "Leave, holidays"                      loaders.load_leave
run_step "Projects, tasks, timesheets"          loaders.load_timesheets
run_step "Attendance, clock sessions, overtime" loaders.load_attendance
run_step "Claims and deductions"                loaders.load_claims
run_step "Notifications"                        loaders.load_notifications
run_step "Documents (W-69)"                     loaders.load_documents
run_step "LOP mismatch log (W-70)"              reconciliation.lop_mismatch_generator
run_step "Financial reconciliation (W-70)"      reconciliation.financial_reconciliation

echo ""
echo "================================================================="
echo " Data migration completed $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "================================================================="
