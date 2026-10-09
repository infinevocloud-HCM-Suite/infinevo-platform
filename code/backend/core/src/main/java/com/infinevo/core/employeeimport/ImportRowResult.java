package com.infinevo.core.employeeimport;

import java.util.UUID;

/**
 * The outcome of one row (W-73.7 §4). On a dry run {@code OK} means "would be created" and both ids are
 * null; on an import it means the employee was created, and invited when the row asked.
 *
 * @param row the row number in the file, 1 for the first line under the header; for "Invite all" the
 *     position in the list of employees invited
 * @param employeeNumber as the row gave it, for a reader matching the result to the file
 */
public record ImportRowResult(
        int row, String employeeNumber, Status status, String message, UUID employeeId, UUID invitationId) {

    public enum Status {
        OK,
        ERROR
    }

    static ImportRowResult ok(int row, String employeeNumber, String message, UUID employeeId, UUID invitationId) {
        return new ImportRowResult(row, employeeNumber, Status.OK, message, employeeId, invitationId);
    }

    static ImportRowResult error(int row, String employeeNumber, String message) {
        return new ImportRowResult(row, employeeNumber, Status.ERROR, message, null, null);
    }

    boolean isError() {
        return status == Status.ERROR;
    }
}
