package com.infinevo.core.overtime;

/**
 * Who created a {@code core.overtime_request} row (W-39.2 §4). {@code REQUEST} is reserved for
 * {@code W-40}: an employee-submitted, manager-approved overtime request, which will write
 * {@code source = 'REQUEST'} through {@link OvertimeService}, never the table directly.
 */
public enum OvertimeSource {
    ADMIN,
    REQUEST
}
