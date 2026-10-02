package com.infinevo.hrms.project;

import java.io.Serial;

/**
 * Thrown when a project, task, or assignment resource is not found (maps to 404).
 */
public class ResourceNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
