package com.infinevo.payroll.statutory.payrun;

import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
import com.infinevo.payroll.payrun.LineKind;
import com.infinevo.payroll.payrun.LineSource;
import com.infinevo.payroll.payrun.PayLine;
import com.infinevo.payroll.payrun.PayLineContributor;
import com.infinevo.payroll.payrun.PayRunEmployeeContext;
import com.infinevo.payroll.payrun.PayRunType;
import com.infinevo.payroll.salary.EmployeeStatutoryProfileService;
import com.infinevo.payroll.salary.SalaryComponentItemResponse;
import com.infinevo.payroll.salary.StatutoryProfileResponse;
import com.infinevo.payroll.statutory.lines.SalaryStatutoryItemResponse;
import com.infinevo.payroll.statutory.lines.StatutoryComponentCode;
import com.infinevo.payroll.statutory.pt.ProfessionalTaxService;
import com.infinevo.payroll.statutory.settings.EpfSettingResponse;
import com.infinevo.payroll.statutory.settings.EsiSettingResponse;
import com.infinevo.payroll.statutory.settings.StatutorySettingsService;
import com.infinevo.shared.money.Money;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The {@code STATUTORY} contributor (W-31.4), after {@code PAY_INPUT} (300) and before {@code TAX} (500):
 *
 * <ul>
 *   <li>Employee EPF, employee ESI and professional tax as {@link LineKind#DEDUCTION} lines</li>
 *   <li>Employer EPS, employer EPF, EDLI, EPF administration and employer ESI as {@link LineKind#BENEFIT} lines</li>
 *   <li>Earned-wage scaling when {@code consider_earned_wage} is enabled</li>
 *   <li>Prorating the restricted ceiling when {@code prorate_restricted_wage} is enabled</li>
 *   <li>Statutory whole-rupee rounding per {@link StatutoryLineRounder}</li>
 * </ul>
 *
 * <p>Settings and work-location state lookups are cached per run / location to enforce the W-55 query budget.
 */
@Component
@Order(400)
public class StatutoryLineContributor implements PayLineContributor {

    static final String CODE_EPF_EE = StatutoryComponentCode.EPF_EMPLOYEE.name();
    static final String NAME_EPF_EE = "Provident fund - employee";

    static final String CODE_EPS_ER = StatutoryComponentCode.EPS_EMPLOYER.name();
    static final String NAME_EPS_ER = "Pension scheme - employer";

    static final String CODE_EPF_ER = StatutoryComponentCode.EPF_EMPLOYER.name();
    static final String NAME_EPF_ER = "Provident fund - employer";

    static final String CODE_EDLI = StatutoryComponentCode.EDLI.name();
    static final String NAME_EDLI = "EDLI contribution";

    static final String CODE_EPF_ADMIN = StatutoryComponentCode.EPF_ADMIN.name();
    static final String NAME_EPF_ADMIN = "EPF administration charge";

    static final String CODE_ESI_EE = StatutoryComponentCode.ESI_EMPLOYEE.name();
    static final String NAME_ESI_EE = "Employee state insurance - employee";

    static final String CODE_ESI_ER = StatutoryComponentCode.ESI_EMPLOYER.name();
    static final String NAME_ESI_ER = "Employee state insurance - employer";

    static final String CODE_PT = "PROFESSIONAL_TAX";
    static final String NAME_PT = "Professional tax";

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal DEFAULT_EMPLOYER_PF_RATE = new BigDecimal("12.0000");

    private final StatutorySettingsService statutorySettingsService;
    private final ProfessionalTaxService professionalTaxService;
    private final WorkLocationService workLocationService;
    private final EmployeeStatutoryProfileService employeeStatutoryProfileService;

    private final Map<UUID, EpfSettingResponse> epfCache = new ConcurrentHashMap<>();
    private final Map<UUID, EsiSettingResponse> esiCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> locationStateCache = new ConcurrentHashMap<>();
    private final Map<PtKey, Money> ptCache = new ConcurrentHashMap<>();

    private record PtKey(UUID payrunId, String stateCode, Money gross, String gender, LocalDate periodEnd) {}

    public StatutoryLineContributor(
            StatutorySettingsService statutorySettingsService,
            ProfessionalTaxService professionalTaxService,
            WorkLocationService workLocationService,
            EmployeeStatutoryProfileService employeeStatutoryProfileService) {
        this.statutorySettingsService =
                Objects.requireNonNull(statutorySettingsService, "statutorySettingsService must not be null");
        this.professionalTaxService =
                Objects.requireNonNull(professionalTaxService, "professionalTaxService must not be null");
        this.workLocationService = Objects.requireNonNull(workLocationService, "workLocationService must not be null");
        this.employeeStatutoryProfileService = Objects.requireNonNull(
                employeeStatutoryProfileService, "employeeStatutoryProfileService must not be null");
    }

    @Override
    public List<PayLine> contribute(PayRunEmployeeContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        if (ctx.runType() == PayRunType.OFF_CYCLE || ctx.version() == null) {
            return List.of();
        }

        EpfSettingResponse epf =
                epfCache.computeIfAbsent(ctx.payrunId(), id -> statutorySettingsService.epf(ctx.tenantId()));
        EsiSettingResponse esi =
                esiCache.computeIfAbsent(ctx.payrunId(), id -> statutorySettingsService.esi(ctx.tenantId()));

        StatutoryProfileResponse profile = ctx.statutoryProfile() != null
                ? ctx.statutoryProfile()
                : employeeStatutoryProfileService.get(ctx.employee().id());

        boolean considerEarnedWage = epf != null && epf.considerEarnedWage();
        BigDecimal paidDays = ctx.days() != null ? ctx.days().paidDays() : null;
        BigDecimal payableDays = ctx.basis() != null ? ctx.basis().payableDays() : null;
        boolean scaleByDays = considerEarnedWage
                && paidDays != null
                && payableDays != null
                && payableDays.signum() > 0
                && paidDays.compareTo(payableDays) < 0;

        Money basic = findBasicSalary(ctx);
        Money earnedBasic = computeScaled(basic, scaleByDays, paidDays, payableDays);

        Money wageCeiling = epf != null ? Money.of(epf.wageCeiling()) : Money.of("15000.0000");
        boolean prorateRestricted = epf != null && epf.prorateRestrictedWage();
        Money effectiveCeiling = (scaleByDays && prorateRestricted)
                ? computeScaled(wageCeiling, true, paidDays, payableDays)
                : wageCeiling;

        List<SalaryStatutoryItemResponse> versionStatutory = ctx.version().statutory();
        Map<String, SalaryStatutoryItemResponse> itemsByCode = new HashMap<>();
        if (versionStatutory != null) {
            for (SalaryStatutoryItemResponse item : versionStatutory) {
                itemsByCode.put(item.componentCode(), item);
            }
        }

        PayLine eePfLine = null;
        PayLine erEpsLine = null;
        PayLine erPfLine = null;
        PayLine edliLine = null;
        PayLine adminLine = null;
        PayLine eeEsiLine = null;
        PayLine erEsiLine = null;

        Money epsUnrounded = Money.ZERO;

        if (itemsByCode.containsKey(CODE_EPF_EE)) {
            SalaryStatutoryItemResponse item = itemsByCode.get(CODE_EPF_EE);
            Money eeBase =
                    (epf != null && epf.restrictEmployeeToCeiling()) ? min(earnedBasic, effectiveCeiling) : earnedBasic;
            Money unrounded = calculateRate(eeBase, item.rate());
            Money amount = StatutoryLineRounder.roundPf(unrounded);
            if (amount.isPositive()) {
                eePfLine = new PayLine(
                        LineKind.DEDUCTION, LineSource.STATUTORY, null, CODE_EPF_EE, NAME_EPF_EE, amount, false);
            }
        }

        if (itemsByCode.containsKey(CODE_EPS_ER)) {
            SalaryStatutoryItemResponse item = itemsByCode.get(CODE_EPS_ER);
            boolean higherWages = profile != null && profile.contributesEpsOnHigherWages();
            Money epsBase = higherWages ? earnedBasic : min(earnedBasic, effectiveCeiling);
            epsUnrounded = calculateRate(epsBase, item.rate());
            Money amount = StatutoryLineRounder.roundPf(epsUnrounded);
            if (amount.isPositive()) {
                erEpsLine = new PayLine(
                        LineKind.BENEFIT, LineSource.STATUTORY, null, CODE_EPS_ER, NAME_EPS_ER, amount, false);
            }
        }

        if (itemsByCode.containsKey(CODE_EPF_ER)) {
            Money erBase =
                    (epf != null && epf.restrictEmployerToCeiling()) ? min(earnedBasic, effectiveCeiling) : earnedBasic;
            BigDecimal erTotalRate = epf != null ? epf.employerRate() : DEFAULT_EMPLOYER_PF_RATE;
            Money erTotalUnrounded = calculateRate(erBase, erTotalRate);
            Money erPfAmount = itemsByCode.containsKey(CODE_EPS_ER)
                    ? StatutoryLineRounder.roundEmployerPf(erTotalUnrounded, epsUnrounded)
                    : StatutoryLineRounder.roundPf(erTotalUnrounded);
            if (erPfAmount.isPositive()) {
                erPfLine = new PayLine(
                        LineKind.BENEFIT, LineSource.STATUTORY, null, CODE_EPF_ER, NAME_EPF_ER, erPfAmount, false);
            }
        }

        if (itemsByCode.containsKey(CODE_EDLI)) {
            SalaryStatutoryItemResponse item = itemsByCode.get(CODE_EDLI);
            Money edliBase = min(earnedBasic, effectiveCeiling);
            Money unrounded = calculateRate(edliBase, item.rate());
            Money amount = StatutoryLineRounder.roundPf(unrounded);
            if (amount.isPositive()) {
                edliLine =
                        new PayLine(LineKind.BENEFIT, LineSource.STATUTORY, null, CODE_EDLI, NAME_EDLI, amount, false);
            }
        }

        if (itemsByCode.containsKey(CODE_EPF_ADMIN)) {
            SalaryStatutoryItemResponse item = itemsByCode.get(CODE_EPF_ADMIN);
            Money adminBase =
                    (epf != null && epf.restrictEmployerToCeiling()) ? min(earnedBasic, effectiveCeiling) : earnedBasic;
            Money unrounded = calculateRate(adminBase, item.rate());
            Money amount = StatutoryLineRounder.roundPf(unrounded);
            if (amount.isPositive()) {
                adminLine = new PayLine(
                        LineKind.BENEFIT, LineSource.STATUTORY, null, CODE_EPF_ADMIN, NAME_EPF_ADMIN, amount, false);
            }
        }

        if (itemsByCode.containsKey(CODE_ESI_EE)) {
            SalaryStatutoryItemResponse item = itemsByCode.get(CODE_ESI_EE);
            Money grossBase = Money.of(item.wageBase());
            Money earnedGross = computeScaled(grossBase, scaleByDays, paidDays, payableDays);
            Money unrounded = calculateRate(earnedGross, item.rate());
            Money amount = StatutoryLineRounder.roundEsi(unrounded);
            if (amount.isPositive()) {
                eeEsiLine = new PayLine(
                        LineKind.DEDUCTION, LineSource.STATUTORY, null, CODE_ESI_EE, NAME_ESI_EE, amount, false);
            }
        }

        if (itemsByCode.containsKey(CODE_ESI_ER)) {
            SalaryStatutoryItemResponse item = itemsByCode.get(CODE_ESI_ER);
            Money grossBase = Money.of(item.wageBase());
            Money earnedGross = computeScaled(grossBase, scaleByDays, paidDays, payableDays);
            Money unrounded = calculateRate(earnedGross, item.rate());
            Money amount = StatutoryLineRounder.roundEsi(unrounded);
            if (amount.isPositive()) {
                erEsiLine = new PayLine(
                        LineKind.BENEFIT, LineSource.STATUTORY, null, CODE_ESI_ER, NAME_ESI_ER, amount, false);
            }
        }

        PayLine ptLine = null;
        if (profile != null && profile.eligibleForPt()) {
            Money grossForPt = computeGrossForPt(ctx);
            String stateCode = resolveStateCode(ctx.employee().workLocationId());
            Money ptAmount = resolvePt(
                    ctx.payrunId(),
                    ctx.tenantId(),
                    stateCode,
                    grossForPt,
                    ctx.employee().gender(),
                    ctx.periodEnd());
            if (ptAmount != null && ptAmount.isPositive()) {
                ptLine = new PayLine(
                        LineKind.DEDUCTION,
                        LineSource.STATUTORY,
                        null,
                        CODE_PT,
                        NAME_PT,
                        StatutoryLineRounder.roundPt(ptAmount),
                        false);
            }
        }

        List<PayLine> lines = new ArrayList<>(8);
        if (eePfLine != null) {
            lines.add(eePfLine);
        }
        if (ptLine != null) {
            lines.add(ptLine);
        }
        if (eeEsiLine != null) {
            lines.add(eeEsiLine);
        }
        if (erEpsLine != null) {
            lines.add(erEpsLine);
        }
        if (erPfLine != null) {
            lines.add(erPfLine);
        }
        if (edliLine != null) {
            lines.add(edliLine);
        }
        if (adminLine != null) {
            lines.add(adminLine);
        }
        if (erEsiLine != null) {
            lines.add(erEsiLine);
        }
        return lines;
    }

    private static Money findBasicSalary(PayRunEmployeeContext ctx) {
        if (ctx.version().earnings() != null) {
            for (SalaryComponentItemResponse item : ctx.version().earnings()) {
                if ("BASIC".equalsIgnoreCase(item.componentCode()) || "BASIC".equalsIgnoreCase(item.componentName())) {
                    return Money.of(item.monthlyAmount());
                }
            }
        }
        if (ctx.version().statutory() != null) {
            for (SalaryStatutoryItemResponse item : ctx.version().statutory()) {
                if (item.componentCode().startsWith("EPF_")
                        || CODE_EDLI.equals(item.componentCode())
                        || CODE_EPS_ER.equals(item.componentCode())) {
                    return Money.of(item.wageBase());
                }
            }
        }
        return Money.ZERO;
    }

    private static Money computeScaled(Money base, boolean scale, BigDecimal paidDays, BigDecimal payableDays) {
        if (!scale || base == null || !base.isPositive()) {
            return base != null ? base : Money.ZERO;
        }
        if (paidDays == null || paidDays.signum() <= 0) {
            return Money.ZERO;
        }
        return base.multiply(paidDays).divide(payableDays);
    }

    private static Money calculateRate(Money base, BigDecimal rate) {
        if (base == null || !base.isPositive() || rate == null || rate.signum() <= 0) {
            return Money.ZERO;
        }
        BigDecimal amount = base.raw().multiply(rate).divide(HUNDRED, 4, RoundingMode.HALF_UP);
        return Money.of(amount);
    }

    private static Money min(Money a, Money b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static Money computeGrossForPt(PayRunEmployeeContext ctx) {
        Money grossEarnings = Money.ZERO;
        Money lopAmount = Money.ZERO;
        for (PayLine line : ctx.priorLines()) {
            if (line.kind() == LineKind.EARNING) {
                grossEarnings = grossEarnings.add(line.amount());
            } else if (line.kind() == LineKind.DEDUCTION && "LOP".equals(line.componentCode())) {
                lopAmount = lopAmount.add(line.amount());
            }
        }
        Money grossForPt = grossEarnings.subtract(lopAmount);
        return grossForPt.isNegative() ? Money.ZERO : grossForPt;
    }

    private String resolveStateCode(UUID locationId) {
        if (locationId == null) {
            return null;
        }
        return locationStateCache.computeIfAbsent(locationId, id -> {
            WorkLocationResponse loc = workLocationService.get(id);
            return loc != null ? loc.stateCode() : null;
        });
    }

    private Money resolvePt(
            UUID payrunId, UUID tenantId, String stateCode, Money gross, String gender, LocalDate periodEnd) {
        if (payrunId == null) {
            return professionalTaxService.resolve(tenantId, stateCode, gross, gender, periodEnd);
        }
        return ptCache.computeIfAbsent(
                new PtKey(payrunId, stateCode, gross, gender, periodEnd),
                k -> professionalTaxService.resolve(tenantId, k.stateCode(), k.gross(), k.gender(), k.periodEnd()));
    }
}
