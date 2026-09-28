package com.infinevo.core.approval;

/**
 * Standard approver kinds for approval steps (W-15.1, 12-core-contracts.md §5 row 6).
 */
public enum ApproverKind {
    REPORTING_MANAGER,
    INDIRECT_MANAGER,
    APPROVER_LEVEL_1,
    APPROVER_LEVEL_2,
    APPROVER_LEVEL_3,
    ROLE,
    NAMED_EMPLOYEE,
    PROJECT_MANAGER
}
