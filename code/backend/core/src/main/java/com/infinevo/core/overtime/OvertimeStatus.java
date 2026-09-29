package com.infinevo.core.overtime;

/**
 * A {@code core.overtime_request} row's state (W-39.2 §4). {@code PENDING} and {@code REJECTED}
 * are {@code W-40}'s to add, by widening the {@code CHECK} on the column — an administrator's
 * entry is approved the moment it is recorded (spec §1: overtime here is a manual, already-approved
 * form, not a request).
 */
public enum OvertimeStatus {
    APPROVED,
    CANCELLED
}
