package com.infinevo.hrms.hiring;

import com.infinevo.core.employee.detail.EmploymentType;

/**
 * Employment type for job openings (W-112).
 */
public enum JobOpeningEmploymentType {
    FULL_TIME,
    PART_TIME,
    CONTRACT,
    INTERN;

    public EmploymentType toCoreEmploymentType() {
        return switch (this) {
            case FULL_TIME -> EmploymentType.PERMANENT;
            case PART_TIME -> EmploymentType.PART_TIME;
            case CONTRACT -> EmploymentType.CONTRACT;
            case INTERN -> EmploymentType.INTERN;
        };
    }
}
