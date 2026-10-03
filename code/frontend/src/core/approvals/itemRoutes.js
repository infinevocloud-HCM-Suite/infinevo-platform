/**
 * Maps approval flow types to item view path builders (W-46.4 §5).
 * If null, the link is hidden until the respective module registers a route.
 */
export const itemRoutes = {
  LEAVE: (itemId) => (itemId ? `/leave/requests/${itemId}` : null),
  REGULARIZATION: null,
  OVERTIME: null,
  // The inbox passes subjectId, which for a claim is the claim id (W-47.4 §5, W-35.1 §3).
  REIMBURSEMENT: (id) => (id ? `/payroll/claims/${id}` : null),
  PROOF_OF_INVESTMENT: null,
  PAY_RUN: null,
  TIMESHEET: null,
};

export function getItemRoute(flowType, itemId) {
  if (!flowType) return null;
  const builder = itemRoutes[flowType];
  return typeof builder === 'function' ? builder(itemId) : null;
}
