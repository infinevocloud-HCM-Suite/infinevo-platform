package com.infinevo.payroll.component;

import java.io.Serial;
import java.util.UUID;

/**
 * Thrown when a salary component is not found in the bound tenant (W-26.1).
 */
public class ComponentNotFoundException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ComponentNotFoundException(String componentType, UUID id) {
        super("No " + componentType + " with id " + id + " found in this tenant");
    }
}
