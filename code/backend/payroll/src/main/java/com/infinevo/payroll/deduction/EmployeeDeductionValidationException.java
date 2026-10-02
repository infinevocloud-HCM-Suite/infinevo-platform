package com.infinevo.payroll.deduction;

import java.io.Serial;

/**
 * A request the rules refuse (W-35.2 §4). Maps to {@code 400}. On a batch, {@link #line} is the
 * zero-based index of the first failing line, and nothing of the batch was written; it is null for a
 * failure that is not about one line.
 */
public class EmployeeDeductionValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Integer line;

    public EmployeeDeductionValidationException(Integer line, String message) {
        super(line == null ? message : "Line " + line + ": " + message);
        this.line = line;
    }

    public EmployeeDeductionValidationException(String message) {
        this(null, message);
    }

    public Integer line() {
        return line;
    }
}
