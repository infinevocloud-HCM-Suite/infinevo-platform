package com.infinevo.core.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.subscription.SubscriptionPlan;
import com.infinevo.core.subscription.SupportPlan;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BillingPricingCalculatorTest {

    private final BillingPricingCalculator calculator = new BillingPricingCalculator();

    @Test
    void starterMonthly_enforcesMinimumTenUsers() {
        BillingPricingCalculator.PricingResult result =
                calculator.calculate(SubscriptionPlan.STARTER, SupportPlan.STANDARD, BillingFrequency.MONTHLY, 4);

        assertThat(result.billableHeadcount()).isEqualTo(10);
        assertThat(result.subtotal()).isEqualByComparingTo("490.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(result.taxableAmount()).isEqualByComparingTo("490.00");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).amount()).isEqualByComparingTo("490.00");
    }

    @Test
    void growthAnnual_appliesFifteenPercentDiscountAndPrioritySupport() {
        BillingPricingCalculator.PricingResult result =
                calculator.calculate(SubscriptionPlan.GROWTH, SupportPlan.PRIORITY, BillingFrequency.ANNUAL, 30);

        // Plan gross: 30 * 79 * 12 = 28440.00; 15% discount = 4266.00
        // Priority support: 4999 * 12 = 59988.00
        // Subtotal = 88428.00; Taxable = 84162.00
        assertThat(result.billableHeadcount()).isEqualTo(30);
        assertThat(result.subtotal()).isEqualByComparingTo("88428.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("4266.00");
        assertThat(result.taxableAmount()).isEqualByComparingTo("84162.00");
        assertThat(result.items()).hasSize(2);
        BigDecimal itemsSum = result.items().stream()
                .map(BillingPricingCalculator.LineItemSpec::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(itemsSum).isEqualByComparingTo(result.subtotal());
    }

    @Test
    void enterpriseMonthly_enforcesMinimumHundredUsers() {
        BillingPricingCalculator.PricingResult result =
                calculator.calculate(SubscriptionPlan.ENTERPRISE, null, BillingFrequency.MONTHLY, 60);

        assertThat(result.billableHeadcount()).isEqualTo(100);
        assertThat(result.subtotal()).isEqualByComparingTo("11900.00");
        assertThat(result.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(result.taxableAmount()).isEqualByComparingTo("11900.00");
    }

    @Test
    void negativeHeadcount_rejected() {
        assertThatThrownBy(() -> calculator.calculate(
                        SubscriptionPlan.STARTER, SupportPlan.STANDARD, BillingFrequency.MONTHLY, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
