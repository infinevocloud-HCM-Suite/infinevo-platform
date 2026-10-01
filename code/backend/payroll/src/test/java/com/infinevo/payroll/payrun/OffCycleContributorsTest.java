package com.infinevo.payroll.payrun;

import static com.infinevo.payroll.payrun.StructureFixtures.JULY;
import static com.infinevo.payroll.payrun.StructureFixtures.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.infinevo.core.lop.LopRounding;
import com.infinevo.core.payinput.PayInputKind;
import com.infinevo.core.payinput.PayInputResponse;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-30.2 §7 — on an off-cycle context {@code STRUCTURE} and {@code LOP} produce nothing and the day
 * figures are zero, while {@code PAY_INPUT} pays the inputs; on a regular context both behave as
 * their own tests say.
 */
class OffCycleContributorsTest {

    private EarningRepository earningRepository;
    private StructureLineContributor structure;
    private LopLineContributor lop;
    private PayInputLineContributor payInput;

    @BeforeEach
    void setUp() {
        earningRepository = mock(EarningRepository.class);
        structure = new StructureLineContributor(earningRepository);
        lop = new LopLineContributor();
        payInput = new PayInputLineContributor();
    }

    @Test
    @DisplayName("Off-cycle: no STRUCTURE and no LOP line, zero days; a one-time payout is one EARNING line")
    void offCyclePaysInputsOnly() {
        PayRunEmployeeContext ctx = offCycleContext(
                List.of(LopFixtures.input(PayInputKind.ONE_TIME_PAYOUT, null, "10000.0000")),
                // A structure line before LOP would be priced by a regular run; here LOP must ignore it.
                List.of(LopFixtures.structure(LineKind.EARNING, UUID.randomUUID(), "BASIC", "30000.0000")));

        assertThat(structure.contribute(ctx)).isEmpty();
        assertThat(lop.contribute(ctx)).isEmpty();
        assertThat(payInput.contribute(ctx))
                .extracting(PayLine::kind, PayLine::source, PayLine::componentCode, PayLine::amount)
                .containsExactly(
                        tuple(LineKind.EARNING, LineSource.PAY_INPUT, "ONE_TIME_PAYOUT", Money.of("10000.0000")));
        assertThat(ctx.days().lopDays()).isEqualByComparingTo("0");
        assertThat(ctx.days().unpaidDays()).isEqualByComparingTo("0");
        assertThat(ctx.days().paidDays()).isEqualByComparingTo("0");
        // The catalogue is never read: the guard is the first thing the contributor does.
        verifyNoInteractions(earningRepository);
    }

    @Test
    @DisplayName("Off-cycle: an ad-hoc deduction is one DEDUCTION line")
    void offCycleDeduction() {
        PayRunEmployeeContext ctx =
                offCycleContext(List.of(LopFixtures.input(PayInputKind.AD_HOC_DEDUCTION, null, "500")), List.of());

        assertThat(payInput.contribute(ctx))
                .extracting(PayLine::kind, PayLine::amount)
                .containsExactly(tuple(LineKind.DEDUCTION, Money.of("500")));
    }

    @Test
    @DisplayName("Regular: STRUCTURE still turns the version into lines")
    void regularStructureUnchanged() {
        UUID basic = UUID.randomUUID();
        // Built before the stubbing: the fixture is itself a stubbed mock.
        List<Earning> catalogue = List.of(StructureFixtures.earning(basic, false, true));
        when(earningRepository.findAllByTenantIdAndDeletedFalse(TENANT)).thenReturn(catalogue);
        PayRunEmployeeContext ctx = StructureFixtures.context(
                StructureFixtures.version(
                        List.of(StructureFixtures.fixed(basic, "BASIC", "Basic", "20000.0000")), List.of(), List.of()),
                JULY,
                LocalDate.of(2025, 1, 1));

        assertThat(ctx.runType()).isEqualTo(PayRunType.REGULAR);
        assertThat(structure.contribute(ctx))
                .extracting(PayLine::componentCode, PayLine::amount)
                .containsExactly(tuple("BASIC", Money.of("20000.0000")));
    }

    @Test
    @DisplayName("Regular: LOP still prices loss-of-pay days")
    void regularLopUnchanged() {
        UUID basic = UUID.randomUUID();
        PayRunEmployeeContext ctx = LopFixtures.context(
                JULY,
                new BigDecimal("31"),
                new BigDecimal("31"),
                LopRounding.HALF_UP_2,
                LocalDate.of(2025, 1, 1),
                null,
                List.of(LopFixtures.input(PayInputKind.LOP_DAYS, "1", null)),
                Set.of(basic),
                List.of(LopFixtures.structure(LineKind.EARNING, basic, "BASIC", "31000.0000")));

        assertThat(lop.contribute(ctx))
                .extracting(PayLine::componentCode, PayLine::amount)
                .containsExactly(tuple(LopLineContributor.LOP_CODE, Money.of("1000.0000")));
    }

    @Test
    @DisplayName("A regular context still needs a salary version and a basis; an off-cycle one does not")
    void contextRequirements() {
        assertThatThrownBy(() -> context(PayRunType.REGULAR, List.of(), List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("version");
        assertThat(context(PayRunType.OFF_CYCLE, List.of(), List.of()).version())
                .isNull();
    }

    private static PayRunEmployeeContext offCycleContext(List<PayInputResponse> inputs, List<PayLine> priorLines) {
        return context(PayRunType.OFF_CYCLE, inputs, priorLines);
    }

    private static PayRunEmployeeContext context(
            PayRunType runType, List<PayInputResponse> inputs, List<PayLine> priorLines) {
        return new PayRunEmployeeContext(
                TENANT,
                UUID.randomUUID(),
                UUID.randomUUID(),
                JULY,
                JULY.atDay(1),
                JULY.atEndOfMonth(),
                LopFixtures.employee(LocalDate.of(2025, 1, 1), null),
                null,
                null,
                LopRounding.HALF_UP_2,
                inputs,
                PayRunDays.zero(),
                Set.of(),
                priorLines,
                runType);
    }
}
