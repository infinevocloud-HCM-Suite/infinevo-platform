/** Scoped draft for "Add Employee" wizard — avoids clashing with other features. */

export const LS_ADD_EMP_WIZARD_EMPLOYEE_ID = "hrms_addEmployee_wizard_employeeId";
export const LS_ADD_EMP_WIZARD_STEP = "hrms_addEmployee_wizard_activeStep";

/** Legacy key (only cleared on logout / wizard complete; do not write new values here). */
const LS_LEGACY_CURRENT_EMPLOYEE_ID = "currentEmployeeId";

/** Remove wizard draft keys and legacy leftover from the old flow. */
export function clearAddEmployeeDraft() {
  try {
    localStorage.removeItem(LS_ADD_EMP_WIZARD_EMPLOYEE_ID);
    localStorage.removeItem(LS_ADD_EMP_WIZARD_STEP);
    localStorage.removeItem(LS_LEGACY_CURRENT_EMPLOYEE_ID);
  } catch {
    /* ignore */
  }
}

export function saveAddEmployeeWizardDraft({ employeeId, activeStep }) {
  try {
    if (employeeId != null && employeeId !== "") {
      localStorage.setItem(LS_ADD_EMP_WIZARD_EMPLOYEE_ID, String(employeeId));
    }
    if (typeof activeStep === "number" && activeStep >= 0 && activeStep <= 3) {
      localStorage.setItem(LS_ADD_EMP_WIZARD_STEP, String(activeStep));
    }
  } catch {
    /* ignore */
  }
}

export function readAddEmployeeWizardDraft() {
  try {
    let employeeId =
      localStorage.getItem(LS_ADD_EMP_WIZARD_EMPLOYEE_ID) || null;
    const stepRaw = localStorage.getItem(LS_ADD_EMP_WIZARD_STEP);
    let activeStep = stepRaw != null ? parseInt(stepRaw, 10) : NaN;

    /** One-shot read of legacy key if scoped keys missing (older sessions). */
    if (!employeeId) {
      const legacy = localStorage.getItem(LS_LEGACY_CURRENT_EMPLOYEE_ID);
      if (legacy) {
        employeeId = legacy;
      }
    }

    if (Number.isNaN(activeStep) || activeStep < 0 || activeStep > 3) {
      activeStep = employeeId ? 1 : 0;
    }

    return { employeeId, activeStep };
  } catch {
    return { employeeId: null, activeStep: 0 };
  }
}
