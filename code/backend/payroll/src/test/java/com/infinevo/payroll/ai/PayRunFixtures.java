package com.infinevo.payroll.ai;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.payroll.payrun.EmployeePayRunResponse;
import com.infinevo.payroll.payrun.InclusionStatus;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.payrun.SkipReason;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Pay runs and their rows for the W-114.21 unit tests, as mocks of the response records. */
final class PayRunFixtures {

    private PayRunFixtures() {}

    static PayRunResponse run(String period, PayRunStatus status, String gross, String deductions, String net) {
        PayRunResponse r = mock(PayRunResponse.class);
        when(r.id()).thenReturn(UUID.randomUUID());
        when(r.period()).thenReturn(period);
        when(r.status()).thenReturn(status);
        when(r.runType()).thenReturn(PayRunType.REGULAR);
        when(r.totalGross()).thenReturn(new BigDecimal(gross));
        when(r.totalDeductions()).thenReturn(new BigDecimal(deductions));
        when(r.totalNetPay()).thenReturn(new BigDecimal(net));
        when(r.computedAt()).thenReturn(Instant.parse("2026-11-25T10:00:00Z"));
        return r;
    }

    static EmployeePayRunResponse row(UUID employeeId, String name, String number, String net, String benefits) {
        EmployeePayRunResponse r = mock(EmployeePayRunResponse.class);
        when(r.id()).thenReturn(UUID.randomUUID());
        when(r.employeeId()).thenReturn(employeeId);
        when(r.employeeName()).thenReturn(name);
        when(r.employeeNumber()).thenReturn(number);
        when(r.inclusionStatus()).thenReturn(InclusionStatus.INCLUDED);
        when(r.netPay()).thenReturn(net == null ? null : new BigDecimal(net));
        when(r.totalBenefits()).thenReturn(benefits == null ? null : new BigDecimal(benefits));
        return r;
    }

    static EmployeePayRunResponse skipped(UUID employeeId, String name, String number, SkipReason reason) {
        EmployeePayRunResponse r = mock(EmployeePayRunResponse.class);
        when(r.id()).thenReturn(UUID.randomUUID());
        when(r.employeeId()).thenReturn(employeeId);
        when(r.employeeName()).thenReturn(name);
        when(r.employeeNumber()).thenReturn(number);
        when(r.inclusionStatus()).thenReturn(InclusionStatus.SKIPPED);
        when(r.skipReason()).thenReturn(reason);
        return r;
    }
}
