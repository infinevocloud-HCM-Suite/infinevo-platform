package com.infinevo.payroll.component;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests verifying salary component validation rules (W-26.1).
 */
class ComponentValidationTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private EarningRepository earningRepository;
    private EarningServiceImpl earningService;

    @BeforeEach
    void setUp() {
        earningRepository = mock(EarningRepository.class);
        earningService = new EarningServiceImpl(earningRepository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("PERCENTAGE calculation type without percentage_of is refused")
    void percentageWithoutPercentageOfRefused() {
        assertThatThrownBy(() -> SalaryComponentValidator.validateCommon(
                        "HRA",
                        "House Rent Allowance",
                        null,
                        CalculationType.PERCENTAGE,
                        new BigDecimal("40.00"),
                        null,
                        null,
                        false))
                .isInstanceOf(ComponentValidationException.class)
                .satisfies(ex -> {
                    ComponentValidationException cve = (ComponentValidationException) ex;
                    assertThat(cve.getFieldErrors()).containsKey("percentageOf");
                });
    }

    @Test
    @DisplayName("FLAT calculation type with percentage_of is refused")
    void flatWithPercentageOfRefused() {
        assertThatThrownBy(() -> SalaryComponentValidator.validateCommon(
                        "BONUS",
                        "Performance Bonus",
                        null,
                        CalculationType.FLAT,
                        new BigDecimal("10000.00"),
                        PercentageOf.BASIC,
                        null,
                        false))
                .isInstanceOf(ComponentValidationException.class)
                .satisfies(ex -> {
                    ComponentValidationException cve = (ComponentValidationException) ex;
                    assertThat(cve.getFieldErrors()).containsKey("percentageOf");
                });
    }

    @Test
    @DisplayName("Negative default_value is refused")
    void negativeDefaultValueRefused() {
        assertThatThrownBy(() -> SalaryComponentValidator.validateCommon(
                        "ALLOWANCE",
                        "Special Allowance",
                        null,
                        CalculationType.FLAT,
                        new BigDecimal("-100.00"),
                        null,
                        null,
                        false))
                .isInstanceOf(ComponentValidationException.class)
                .satisfies(ex -> {
                    ComponentValidationException cve = (ComponentValidationException) ex;
                    assertThat(cve.getFieldErrors()).containsKey("defaultValue");
                });
    }

    @Test
    @DisplayName("Negative max_limit is refused")
    void negativeMaxLimitRefused() {
        assertThatThrownBy(() -> SalaryComponentValidator.validateCommon(
                        "MEDICAL",
                        "Medical Allowance",
                        null,
                        CalculationType.FLAT,
                        new BigDecimal("1250.00"),
                        null,
                        new BigDecimal("-5000.00"),
                        false))
                .isInstanceOf(ComponentValidationException.class)
                .satisfies(ex -> {
                    ComponentValidationException cve = (ComponentValidationException) ex;
                    assertThat(cve.getFieldErrors()).containsKey("maxLimit");
                });
    }

    @Test
    @DisplayName("Duplicate code within the same tenant is refused")
    void duplicateCodeWithinTenantRefused() {
        TenantContext.set(TENANT_A);
        when(earningRepository.existsByTenantIdAndCode(TENANT_A, "BASIC")).thenReturn(true);

        EarningRequest request = new EarningRequest(
                "BASIC",
                "Basic Salary",
                null,
                "FIXED",
                CalculationType.FLAT,
                new BigDecimal("50000.0000"),
                null,
                null,
                "MONTHLY",
                null,
                false,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                true,
                null,
                false,
                true);

        assertThatThrownBy(() -> earningService.create(request))
                .isInstanceOf(ComponentValidationException.class)
                .satisfies(ex -> {
                    ComponentValidationException cve = (ComponentValidationException) ex;
                    assertThat(cve.getFieldErrors()).containsKey("code");
                });
    }

    @Test
    @DisplayName("Same code in another tenant is allowed")
    void sameCodeInAnotherTenantAllowed() {
        TenantContext.set(TENANT_B);
        when(earningRepository.existsByTenantIdAndCode(TENANT_B, "BASIC")).thenReturn(false);
        when(earningRepository.save(any(Earning.class))).thenAnswer(invocation -> {
            Earning e = invocation.getArgument(0);
            return e;
        });

        EarningRequest request = new EarningRequest(
                "BASIC",
                "Basic Salary",
                null,
                "FIXED",
                CalculationType.FLAT,
                new BigDecimal("50000.0000"),
                null,
                null,
                "MONTHLY",
                null,
                false,
                true,
                true,
                true,
                false,
                false,
                false,
                false,
                true,
                null,
                false,
                true);

        assertThatCode(() -> earningService.create(request)).doesNotThrowAnyException();
    }
}
