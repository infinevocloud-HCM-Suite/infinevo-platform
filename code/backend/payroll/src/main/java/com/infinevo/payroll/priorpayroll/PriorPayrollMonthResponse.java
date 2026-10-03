package com.infinevo.payroll.priorpayroll;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for an imported prior payroll month row (W-38.1 §4).
 */
public record PriorPayrollMonthResponse(
        UUID id,
        @JsonProperty("employee_id") UUID employeeId,
        @JsonProperty("employee_number") String employeeNumber,
        @JsonProperty("employee_name") String employeeName,
        String period,
        @JsonProperty("gross_earnings") BigDecimal grossEarnings,
        @JsonProperty("epf_employee") BigDecimal epfEmployee,
        @JsonProperty("esi_employee") BigDecimal esiEmployee,
        @JsonProperty("professional_tax") BigDecimal professionalTax,
        BigDecimal tds,
        @JsonProperty("net_pay") BigDecimal netPay,
        @JsonProperty("import_id") UUID importId,
        @JsonProperty("created_at") Instant createdAt) {

    public static PriorPayrollMonthResponse from(PriorPayrollMonth m, String employeeNumber, String employeeName) {
        if (m == null) {
            return null;
        }
        return new PriorPayrollMonthResponse(
                m.getId(),
                m.getEmployeeId(),
                employeeNumber,
                employeeName,
                m.getPeriod(),
                m.getGrossEarnings(),
                m.getEpfEmployee(),
                m.getEsiEmployee(),
                m.getProfessionalTax(),
                m.getTds(),
                m.getNetPay(),
                m.getImportId(),
                m.getCreatedAt());
    }
}
