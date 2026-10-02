package com.infinevo.hrms.project;

import java.io.Serial;

/**
 * Thrown when an employee is already assigned to a project (maps to 409).
 */
public class DuplicateAssignmentException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicateAssignmentException(String message) {
        super(message);
    }
}
