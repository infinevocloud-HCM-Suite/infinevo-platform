package com.infinevo.payroll.fbp;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.component.CalculationType;
import com.infinevo.payroll.component.Earning;
import com.infinevo.payroll.component.EarningRepository;
import com.infinevo.payroll.component.Reimbursement;
import com.infinevo.payroll.component.ReimbursementRepository;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for Flexible Benefit Plan service and database operations (W-27.1).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class FbpPlanIT extends AbstractIntegrationTest {

    @Autowired
    private FbpPlanService fbpPlanService;

    @Autowired
    private EarningRepository earningRepository;

    @Autowired
    private ReimbursementRepository reimbursementRepository;

    @BeforeAll
    static void initSchema() throws Exception {
        PayrollTestSchema.apply();
        PayrollTestSchema.seedTenants();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET before any PUT returns exists: false and writes no row to database")
    void getBeforePutWritesNoRow() throws SQLException {
        TenantContext.set(TENANT_A);

        FbpPlanResponse response = fbpPlanService.get();

        assertThat(response).isNotNull();
        assertThat(response.exists()).isFalse();
        assertThat(response.id()).isNull();
        assertThat(response.isEnabled()).isFalse();
        assertThat(response.isLocked()).isFalse();
        assertThat(response.notifyOnRelease()).isTrue();
        assertThat(response.notifyOnLock()).isTrue();
        assertThat(response.reminderDaysBeforeClose()).containsExactly(5, 1);

        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM payroll.fbp WHERE tenant_id = ?")) {
            ps.setObject(1, TENANT_A);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isZero();
            }
        }
    }

    @Test
    @DisplayName("PUT twice leaves exactly one row in the database")
    void putTwiceLeavesOneRow() throws SQLException {
        TenantContext.set(TENANT_A);

        FbpPlanRequest req1 = new FbpPlanRequest(
                true, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 15), true, true, List.of(5, 1));
        FbpPlanResponse res1 = fbpPlanService.upsert(req1);

        assertThat(res1.exists()).isTrue();
        assertThat(res1.id()).isNotNull();
        assertThat(res1.isEnabled()).isTrue();
        assertThat(res1.windowOpensOn()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(res1.windowClosesOn()).isEqualTo(LocalDate.of(2026, 4, 15));

        FbpPlanRequest req2 = new FbpPlanRequest(
                true, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 25), false, true, List.of(10, 3));
        FbpPlanResponse res2 = fbpPlanService.upsert(req2);

        assertThat(res2.id()).isEqualTo(res1.id());
        assertThat(res2.windowClosesOn()).isEqualTo(LocalDate.of(2026, 4, 25));
        assertThat(res2.notifyOnRelease()).isFalse();
        assertThat(res2.reminderDaysBeforeClose()).containsExactly(10, 3);

        try (Connection conn = PayrollTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM payroll.fbp WHERE tenant_id = ?")) {
            ps.setObject(1, TENANT_A);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("PUT with empty reminder list turns reminders off and persists empty list")
    void putWithEmptyReminderListTurnsRemindersOff() {
        TenantContext.set(TENANT_A);

        FbpPlanRequest req =
                new FbpPlanRequest(true, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 25), true, true, List.of());
        FbpPlanResponse res = fbpPlanService.upsert(req);

        assertThat(res.reminderDaysBeforeClose()).isEmpty();

        FbpPlanResponse fetched = fbpPlanService.get();
        assertThat(fetched.reminderDaysBeforeClose()).isEmpty();
    }

    @Test
    @DisplayName("Lock then unlock round-trips state and toggles window open calculation")
    void lockUnlockRoundTrip() {
        TenantContext.set(TENANT_A);

        FbpPlanRequest req = new FbpPlanRequest(
                true, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 20), true, true, List.of(5, 1));
        fbpPlanService.upsert(req);

        LocalDate testDate = LocalDate.of(2026, 4, 10);
        assertThat(fbpPlanService.isWindowOpen(testDate)).isTrue();

        FbpPlanResponse locked = fbpPlanService.lock();
        assertThat(locked.isLocked()).isTrue();
        assertThat(locked.lockedAt()).isNotNull();
        assertThat(fbpPlanService.isWindowOpen(testDate)).isFalse();

        FbpPlanResponse unlocked = fbpPlanService.unlock();
        assertThat(unlocked.isLocked()).isFalse();
        assertThat(unlocked.lockedAt()).isNull();
        assertThat(fbpPlanService.isWindowOpen(testDate)).isTrue();
    }

    @Test
    @DisplayName("components lists only rows flagged FBP and active")
    void componentsFilterTest() {
        TenantContext.set(TENANT_A);

        // 1. Earning flagged FBP and active -> Should be returned
        Earning fbpEarning = new Earning(TENANT_A, "test");
        fbpEarning.setCode("FUEL");
        fbpEarning.setName("Fuel Allowance");
        fbpEarning.setEarningType("ALLOWANCE");
        fbpEarning.setCalculationType(CalculationType.FLAT);
        fbpEarning.setDefaultValue(new BigDecimal("2400.0000"));
        fbpEarning.setMaxLimit(new BigDecimal("5000.0000"));
        fbpEarning.setFbpComponent(true);
        fbpEarning.setActive(true);
        earningRepository.save(fbpEarning);

        // 2. Earning NOT flagged FBP -> Should be excluded
        Earning nonFbpEarning = new Earning(TENANT_A, "test");
        nonFbpEarning.setCode("BASIC");
        nonFbpEarning.setName("Basic Pay");
        nonFbpEarning.setEarningType("BASIC");
        nonFbpEarning.setCalculationType(CalculationType.FLAT);
        nonFbpEarning.setDefaultValue(new BigDecimal("40000.0000"));
        nonFbpEarning.setFbpComponent(false);
        nonFbpEarning.setActive(true);
        earningRepository.save(nonFbpEarning);

        // 3. Earning flagged FBP but INACTIVE -> Should be excluded
        Earning inactiveFbpEarning = new Earning(TENANT_A, "test");
        inactiveFbpEarning.setCode("DRIVER");
        inactiveFbpEarning.setName("Driver Allowance");
        inactiveFbpEarning.setEarningType("ALLOWANCE");
        inactiveFbpEarning.setCalculationType(CalculationType.FLAT);
        inactiveFbpEarning.setDefaultValue(new BigDecimal("3000.0000"));
        inactiveFbpEarning.setFbpComponent(true);
        inactiveFbpEarning.setActive(false);
        earningRepository.save(inactiveFbpEarning);

        // 4. Reimbursement flagged FBP and active -> Should be returned
        Reimbursement fbpReimbursement = new Reimbursement(TENANT_A, "test");
        fbpReimbursement.setCode("MEAL");
        fbpReimbursement.setName("Meal Voucher");
        fbpReimbursement.setCalculationType(CalculationType.FLAT);
        fbpReimbursement.setDefaultValue(new BigDecimal("3000.0000"));
        fbpReimbursement.setMaxLimit(new BigDecimal("6000.0000"));
        fbpReimbursement.setReimbursementType("FOOD");
        fbpReimbursement.setFbpComponent(true);
        fbpReimbursement.setActive(true);
        reimbursementRepository.save(fbpReimbursement);

        // 5. Reimbursement NOT flagged FBP -> Should be excluded
        Reimbursement nonFbpReimbursement = new Reimbursement(TENANT_A, "test");
        nonFbpReimbursement.setCode("MEDICAL");
        nonFbpReimbursement.setName("Medical Reimbursement");
        nonFbpReimbursement.setCalculationType(CalculationType.FLAT);
        nonFbpReimbursement.setDefaultValue(new BigDecimal("1250.0000"));
        nonFbpReimbursement.setReimbursementType("HEALTH");
        nonFbpReimbursement.setFbpComponent(false);
        nonFbpReimbursement.setActive(true);
        reimbursementRepository.save(nonFbpReimbursement);

        List<FbpComponentResponse> components = fbpPlanService.components();

        assertThat(components).hasSize(2);

        FbpComponentResponse earningComp = components.stream()
                .filter(c -> c.code().equals("FUEL"))
                .findFirst()
                .orElseThrow();
        assertThat(earningComp.kind()).isEqualTo("EARNING");
        assertThat(earningComp.name()).isEqualTo("Fuel Allowance");
        assertThat(earningComp.maxLimit()).isEqualByComparingTo("5000.0000");

        FbpComponentResponse reimbursementComp = components.stream()
                .filter(c -> c.code().equals("MEAL"))
                .findFirst()
                .orElseThrow();
        assertThat(reimbursementComp.kind()).isEqualTo("REIMBURSEMENT");
        assertThat(reimbursementComp.name()).isEqualTo("Meal Voucher");
        assertThat(reimbursementComp.maxLimit()).isEqualByComparingTo("6000.0000");
    }
}
