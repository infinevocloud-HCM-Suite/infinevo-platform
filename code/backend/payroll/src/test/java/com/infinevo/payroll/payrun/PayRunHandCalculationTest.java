package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.StructureFixtures.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.lop.WorkingDayBasisResponse;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.salary.SalaryComponentItemResponse;
import com.infinevo.payroll.salary.SalaryVersionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-29.3 §8 — the worked example, to the rupee, through all three contributors and W-29.2's sum:
 * W-29.2 §8's employee, joined 11 July, 1.5 LOP days, 3,000 overtime and a 500 ad-hoc deduction,
 * under ACTUAL_DAYS with weekends payable (payable days = divisor = 31).
 */
class PayRunHandCalculationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Test
    @DisplayName("§8: LOP 15,766.13, gross 48,000, deductions 16,266.13, net 33,733.87, benefits 1,132.26")
    void workedExample() {
        StructureFixtures.WorkedExample example = StructureFixtures.workedExample();
        SalaryVersionResponse version = example.version();
        EarningRepository earnings = mock(EarningRepository.class);
        when(earnings.findAllByTenantIdAndDeletedFalse(TENANT)).thenReturn(example.catalogue());

        // Basic, HRA, Special allowance and the employer PF benefit are pro-rata; the meal card is not.
        Set<UUID> proRata = Stream.concat(version.earnings().stream(), version.benefits().stream())
                .filter(i -> Set.of("BASIC", "HRA", "SPECIAL", "EMPLOYER_PF").contains(i.componentCode()))
                .map(SalaryComponentItemResponse::componentId)
                .collect(Collectors.toSet());
        List<PayInputResponse> inputs = List.of(
                LopFixtures.input(PayInputKind.LOP_DAYS, "1.5", null),
                LopFixtures.input(PayInputKind.OVERTIME, "10", "3000"),
                LopFixtures.input(PayInputKind.AD_HOC_DEDUCTION, "1", "500"));
        LocalDate joined = LocalDate.of(2026, 7, 11);
        BigDecimal thirtyOne = new BigDecimal("31.00");
        PayRunDays days = PayRunDays.of(
                thirtyOne,
                PayInputLineContributor.netLopDays(inputs),
                JULY.atDay(1),
                JULY.atEndOfMonth(),
                joined,
                null);
        PayRunEmployeeContext ctx = new PayRunEmployeeContext(
                TENANT,
                UUID.randomUUID(),
                UUID.randomUUID(),
                JULY,
                JULY.atDay(1),
                JULY.atEndOfMonth(),
                LopFixtures.employee(joined, null),
                version,
                new WorkingDayBasisResponse(thirtyOne, thirtyOne, UUID.randomUUID(), LopRounding.HALF_UP_2),
                LopRounding.HALF_UP_2,
                inputs,
                days,
                proRata,
                List.of());

        List<PayLine> lines = new ArrayList<>();
        for (PayLineContributor contributor : List.of(
                new StructureLineContributor(earnings), new LopLineContributor(), new PayInputLineContributor())) {
            lines.addAll(contributor.contribute(ctx.withPriorLines(lines)));
        }
        PayRunTotals totals = PayRunTotals.of(lines);

        assertThat(days.lopDays()).isEqualByComparingTo("1.50");
        assertThat(days.outsideDays()).isEqualByComparingTo("10");
        assertThat(days.unpaidDays()).isEqualByComparingTo("11.50");
        assertThat(days.paidDays()).isEqualByComparingTo("19.50");
        assertThat(lines)
                .filteredOn(l -> l.source() == LineSource.LOP && l.kind() == LineKind.DEDUCTION)
                .singleElement()
                .satisfies(l -> assertThat(l.amount().raw()).isEqualByComparingTo("15766.13"));
        assertThat(totals.grossEarnings().raw()).isEqualByComparingTo("48000.0000");
        assertThat(totals.totalReimbursements().raw()).isEqualByComparingTo("2000.0000");
        assertThat(totals.totalDeductions().raw()).isEqualByComparingTo("16266.1300");
        assertThat(totals.totalBenefits().toAmount()).isEqualByComparingTo("1132.26");
        assertThat(totals.netPay()).isEqualTo(new BigDecimal("33733.87"));
    }
}
