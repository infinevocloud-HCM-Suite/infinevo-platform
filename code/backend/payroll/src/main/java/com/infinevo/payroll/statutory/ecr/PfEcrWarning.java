package com.infinevo.payroll.statutory.ecr;

import java.util.UUID;

/**
 * Pre-filing validation warning for an employee row in a PF ECR return (W-87).
 */
public record PfEcrWarning(
        UUID employeeId, String employeeNumber, String employeeName, String warningCode, String message) {}
