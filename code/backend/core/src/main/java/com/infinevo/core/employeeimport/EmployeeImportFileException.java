package com.infinevo.core.employeeimport;

import java.io.Serial;

/** The import file as a whole cannot be read or used (W-73.7). Maps to {@code 400}. */
public class EmployeeImportFileException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EmployeeImportFileException(String message) {
        super(message);
    }
}
