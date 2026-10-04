/**
 * Maps approval flow types to item view path builders (W-46.4 §5).
 * If null, the link is hidden until the respective module registers a route.
 */
export const itemRoutes = {
  LEAVE: (itemId) => (itemId ? `/leave/requests/${itemId}` : null),
  // W-48.5 §5: the subject id is the request id.
  REGULARIZATION: (id) => (id ? `/hrms/regularizations/${id}` : null),
  OVERTIME: (id) => (id ? `/hrms/overtime-requests/${id}` : null),
  // The inbox passes subjectId, which for a claim is the claim id (W-47.4 §5, W-35.1 §3).
  REIMBURSEMENT: (id) => (id ? `/payroll/claims/${id}` : null),
  PROOF_OF_INVESTMENT: null,
  PAY_RUN: null,
  // The approval subject is the project entry (W-48.3 §5, TimesheetSubmitService.java:16).
  TIMESHEET: (itemId) => (itemId ? `/hrms/timesheet-review/entries/${itemId}` : null),
};

export function getItemRoute(flowType, itemId) {
  if (!flowType) return null;
  const builder = itemRoutes[flowType];
  return typeof builder === 'function' ? builder(itemId) : null;
}
