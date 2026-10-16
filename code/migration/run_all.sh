#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# run_all.sh — Complete Data Migration Pipeline Execution (W-68, W-69, W-70, W-71)
#
# Executes the migration stream end-to-end:
#   1. Extract HRMS legacy tables (22 tables)
#   2. Extract Payroll legacy tables (11 tables)
#   3. Merge 3 cohorts into unified employees
#   4. Load PostgreSQL core schema & statutory profile
#   5. Load historical payruns & employee payrun lines
#   6. Load leave calendar, policies, allocations, and requests
#   7. Load employee documents and POI proofs into core.document
#   8. Provision users in Keycloak & link user accounts
#   9. Generate LOP mismatch audit log (W-70)
#  10. Run financial reconciliation audit (0.00 INR delta check)
#
# Fails fast on any error (set -euo pipefail).
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_BIN="${PYTHON_BIN:-python3}"

echo "================================================================="
echo " Infinevo Data Migration Pipeline — Full Run"
echo " Started at: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo " Base Directory: ${SCRIPT_DIR}"
echo "================================================================="

run_step() {
  local step_num="$1"
  local step_name="$2"
  local script_path="$3"

  echo ""
  echo ">>> [Step ${step_num}] ${step_name}..."
  local start_time
  start_time="$(date +%s)"

  "$PYTHON_BIN" "${SCRIPT_DIR}/${script_path}"

  local end_time
  end_time="$(date +%s)"
  local duration=$(( end_time - start_time ))
  echo ">>> [Step ${step_num}] COMPLETED in ${duration}s"
}

# 1. Extractors
run_step 1 "Extracting Legacy HRMS MySQL Tables" "extractors/extract_hrms.py"
run_step 2 "Extracting Legacy Payroll MySQL Tables" "extractors/extract_payroll.py"

# 2. Transformer
run_step 3 "Merging Employees across 3 Cohorts" "transformers/merge_employees.py"

# 3. Loaders
run_step 4 "Loading PostgreSQL Core Employees & Statutory Profiles" "loaders/load_postgres.py"
run_step 5 "Loading Historical Payruns & Employee Payruns" "loaders/load_payruns.py"
run_step 6 "Loading Leave Types, Policies, Allocations & Requests" "loaders/load_leave.py"
run_step 7 "Loading Employee Documents & POI Proofs" "loaders/load_documents.py"
run_step 8 "Provisioning Keycloak Users & Linking User Accounts" "loaders/load_keycloak.py"

# 4. Reconciliation & Audit
run_step 9 "Generating LOP Mismatch Audit Log" "reconciliation/lop_mismatch_generator.py"
run_step 10 "Running Financial Reconciliation (0.00 INR Delta Proof)" "reconciliation/financial_reconciliation.py"

echo ""
echo "================================================================="
echo " Data Migration Pipeline COMPLETED SUCCESSFULLY"
echo " Finished at: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
echo "================================================================="
