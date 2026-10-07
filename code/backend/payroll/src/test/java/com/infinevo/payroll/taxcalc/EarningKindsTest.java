package com.infinevo.payroll.taxcalc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EarningKindsTest {

    @Test
    void hraByCodeOrByNamedType() {
        assertThat(EarningKinds.isHra("HRA", "Fixed")).isTrue();
        assertThat(EarningKinds.isHra("E002", "House Rent Allowance")).isTrue();
        assertThat(EarningKinds.isHra("E002", "house rent allowance ")).isTrue();
        assertThat(EarningKinds.isHra("E002", "Dearness Allowance")).isFalse();
        assertThat(EarningKinds.isHra(null, null)).isFalse();
    }

    @Test
    void basicByCodeOrByNamedType() {
        assertThat(EarningKinds.isBasic("BASIC", null)).isTrue();
        assertThat(EarningKinds.isBasic("E001", "Basic")).isTrue();
        assertThat(EarningKinds.isBasic("E001", "Conveyance Allowance")).isFalse();
    }
}
