package com.infinevo.core.employee.detail;

/**
 * What a client may say about an employee's identity documents (W-13.2, spec section 4).
 *
 * <p><strong>There is no tenant field, and there must never be one.</strong> The tenant comes from
 * {@code TenantContext}, bound by the filter from the verified token before this record is read.
 *
 * <p>This is a replace, not a patch. Every field omitted is written as null, which is how a value is
 * cleared.
 *
 * @param aadhaarNumber twelve digits, spelled {@code aadhaar} — not the frozen {@code aadhar}
 *     ({@code legacy/HRMS_Backend/src/main/java/com/phegondev/usersmanagementsystem/entity/Identification.java:19-20}).
 *     The spelling is load-bearing: it is what the audit redaction deny-list matches on.
 * @param panNumber the {@code AAAAA9999A} form, upper case, as the frozen pattern requires
 *     ({@code Identification.java:23})
 */
public record EmployeeIdentificationRequest(
        String immigrationStatus,
        String aadhaarNumber,
        String panNumber,
        String personalTaxId,
        String socialInsuranceNumber,
        String idProofType,
        String idDocumentName,
        String idDocumentNumber,
        String addressProofType,
        String addressDocumentName,
        String addressDocumentNumber) {}
