package com.itsdev.payroll.specs;

import com.itsdev.payroll.entity.employee.BasicDetails;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

// --- Choose the correct JoinType import for your project ---
// If your project uses javax.persistence annotations, keep the line below:


// If your project uses jakarta.persistence annotations, comment the javax import above
// and uncomment the jakarta import below instead:
import jakarta.persistence.criteria.JoinType;

public class BasicDetailsSpecs {

    public static Specification<BasicDetails> hasOrganization(String organizationId) {
        return (root, query, cb) ->
                cb.equal(root.join("organization", JoinType.LEFT).get("organizationId"), organizationId);
    }

    public static Specification<BasicDetails> hasWorkLocation(String workLocationId) {
        if (!StringUtils.hasText(workLocationId)) return null;
        return (root, query, cb) -> cb.equal(
                root.join("workLocation", JoinType.LEFT).get("workLocationId"),
                workLocationId
        );
    }

    public static Specification<BasicDetails> hasDepartment(String departmentId) {
        if (!StringUtils.hasText(departmentId)) return null;
        return (root, query, cb) -> cb.equal(
                root.join("department", JoinType.LEFT).get("departmentId"),
                departmentId
        );
    }

    public static Specification<BasicDetails> hasDesignation(String designationId) {
        if (!StringUtils.hasText(designationId)) return null;
        return (root, query, cb) -> cb.equal(
                root.join("designation", JoinType.LEFT).get("designationId"),
                designationId
        );
    }
}
