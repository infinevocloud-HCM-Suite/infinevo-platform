package com.infinevo.hrms.project;

import java.io.Serial;

/** Thrown when a project or task cannot be deleted because a live timesheet refers to it (maps to 409). */
public class ResourceInUseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceInUseException(String message) {
        super(message);
    }
}
