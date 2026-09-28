package com.infinevo.core.approval;

/**
 * Standard approval flow types across Infinevo platform (W-15.1, 12-core-contracts.md §5 row 6).
 */
public enum ApprovalFlowType {
    LEAVE,
    REGULARIZATION,
    OVERTIME,
    REIMBURSEMENT,
    PROOF_OF_INVESTMENT,
    PAY_RUN,
    TIMESHEET
}
