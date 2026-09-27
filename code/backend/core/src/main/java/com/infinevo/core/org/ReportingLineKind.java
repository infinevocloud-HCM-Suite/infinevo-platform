package com.infinevo.core.org;

/**
 * Kind of reporting line (W-14.2, spec section 4).
 *
 * <ul>
 *   <li>{@code PRIMARY}: Single primary reporting manager in force at a time.
 *   <li>{@code INDIRECT}: Secondary / dotted-line manager.
 *   <li>{@code APPROVER_L1}: Level-1 approver override.
 *   <li>{@code APPROVER_L2}: Level-2 approver override.
 *   <li>{@code APPROVER_L3}: Level-3 approver override.
 * </ul>
 */
public enum ReportingLineKind {
    PRIMARY,
    INDIRECT,
    APPROVER_L1,
    APPROVER_L2,
    APPROVER_L3
}
