package com.infinevo.payroll.payrun.bankfile;

import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_A;
import static com.infinevo.payroll.payrun.PayRunTestSchema.TENANT_B;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.report.ExportFormat;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.payrun.InProcessPayRunWorker;
import com.infinevo.payroll.payrun.PayRunResponse;
import com.infinevo.payroll.payrun.PayRunService;
import com.infinevo.payroll.payrun.PayRunStatus;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.schedule.PayDayRule;
import com.infinevo.payroll.schedule.PayScheduleRequest;
import com.infinevo.payroll.schedule.PayScheduleService;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * W-88.2 over the real schema ({@code V224}) and a real approved run: a template's JSON columns survive the round
 * trip, another tenant cannot see it, and a file made from it is stored, listed and downloads byte for byte.
 */
@SpringBootTest(classes = PayrollTestApp.class)
class BankFileTemplateIT extends AbstractIntegrationTest {

    private static final YearMonth JULY = YearMonth.of(2026, 7);

    @Autowired
    private BankFileTemplateService templateService;

    @Autowired
    private BankFileExportService exportService;

    @Autowired
    private PayRunService payRunService;

    @Autowired
    private PayScheduleService scheduleService;

    @Autowired
    private InProcessPayRunWorker worker;

    private UUID paid;
    private UUID noAccount;
    private UUID runId;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayRunTestSchema.clean();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        PayRunTestSchema.clean();
        TenantContext.set(TENANT_A);
        scheduleService.upsert(new PayScheduleRequest(
                List.of(1, 2, 3, 4, 5), PayDayRule.LAST_DAY_OF_PERIOD, null, 25, LocalDate.of(2026, 1, 1)));
        PayRunTestSchema.Catalogue catalogue = PayRunTestSchema.insertWorkedExampleCatalogue(TENANT_A);
        paid = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "B-01", catalogue);
        noAccount = PayRunTestSchema.insertWorkedExampleEmployee(TENANT_A, "B-02", catalogue);
        // The worked-example bank row has a payment mode only; give B-01 an account and IFSC.
        PayRunTestSchema.execute(
                "UPDATE core.employee_bank SET bank_account_number = '000123456789', ifsc_code = 'hdfc0001234',"
                        + " account_holder_name = 'B ONE' WHERE tenant_id = ? AND employee_id = ?",
                TENANT_A,
                paid);

        PayRunResponse run = payRunService.create(JULY);
        payRunService.lock(run.id());
        assertThat(worker.computeNow(run.id()).status()).isEqualTo(PayRunStatus.COMPUTED);
        TenantContext.set(TENANT_A);
        assertThat(payRunService.approve(run.id()).status()).isEqualTo(PayRunStatus.APPROVED);
        runId = run.id();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static BankFileTemplateRequest hdfc() {
        return new BankFileTemplateRequest(
                "HDFC bulk",
                ExportFormat.CSV,
                true,
                List.of(
                        new BankFileColumn("Txn", BankFileColumnSource.FIXED, null, "N", null),
                        new BankFileColumn(
                                "Beneficiary",
                                BankFileColumnSource.FIELD,
                                BankFileField.ACCOUNT_HOLDER_NAME,
                                null,
                                null),
                        new BankFileColumn(
                                "Account", BankFileColumnSource.FIELD, BankFileField.ACCOUNT_NUMBER, null, null),
                        new BankFileColumn("IFSC", BankFileColumnSource.FIELD, BankFileField.IFSC, null, null),
                        new BankFileColumn(
                                "Amount", BankFileColumnSource.FIELD, BankFileField.NET_PAY, null, "WHOLE_RUPEES"),
                        new BankFileColumn(
                                "Month", BankFileColumnSource.FIELD, BankFileField.PAY_PERIOD, null, "MMM yyyy")));
    }

    @Test
    @DisplayName("a template's columns survive the JSON round trip; tenant B sees only the built-in")
    void roundTripAndIsolation() {
        BankFileTemplateResponse created = templateService.create(hdfc());

        BankFileTemplateResponse read = templateService.get(created.id());
        assertThat(read.columns()).isEqualTo(hdfc().columns());
        assertThat(templateService.list())
                .extracting(BankFileTemplateResponse::name)
                .containsExactly("Generic bulk NEFT", "HDFC bulk");

        TenantContext.set(TENANT_B);
        assertThat(templateService.list())
                .extracting(BankFileTemplateResponse::name)
                .containsExactly("Generic bulk NEFT");
        assertThatThrownBy(() -> templateService.get(created.id())).isInstanceOf(BankFileTemplateException.class);
    }

    @Test
    @DisplayName("a file from the template pays B-01, leaves B-02 out with a reason, is stored, listed and downloads")
    void generateStoreListDownload() throws Exception {
        BankFileTemplateResponse template = templateService.create(hdfc());

        BankFilePreviewResponse preview = exportService.preview(runId, new BankFileExportRequest(template.id(), null));
        assertThat(preview.rowCount()).isEqualTo(1);
        assertThat(preview.omitted())
                .extracting(BankFileOmission::employeeNumber)
                .containsExactly("B-02");
        assertThat(preview.omitted().get(0).reason()).isEqualTo("blank bank account number");
        assertThat(preview.rows().get(0).subList(0, 4)).containsExactly("N", "B ONE", "000123456789", "HDFC0001234");
        assertThat(preview.rows().get(0).get(5)).isEqualTo("Jul 2026");

        BankFileExportResponse made = exportService.generate(runId, new BankFileExportRequest(template.id(), null));
        assertThat(made.fileName()).isEqualTo("HDFC_bulk_2026-07.csv");
        assertThat(made.rowCount()).isEqualTo(1);
        assertThat(made.templateId()).isEqualTo(UUID.fromString(template.id()));

        assertThat(exportService.list(runId))
                .extracting(BankFileExportResponse::id)
                .containsExactly(made.id());

        BankFileExportService.Download download = exportService.download(runId, made.id());
        String csv;
        try (InputStream in = download.content().content()) {
            csv = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(csv).startsWith("﻿Txn,Beneficiary,Account,IFSC,Amount,Month\r\nN,B ONE,000123456789,HDFC0001234,");
        assertThat(csv).doesNotContain("B-02");

        TenantContext.set(TENANT_B);
        assertThat(exportService.list(runId)).isEmpty();
    }

    @Test
    @DisplayName(
            "a selection narrows the file; an id outside the run is refused; deleting the template keeps the history")
    void selectionAndDelete() {
        BankFileTemplateResponse template = templateService.create(hdfc());

        assertThatThrownBy(() ->
                        exportService.generate(runId, new BankFileExportRequest(template.id(), List.of(noAccount))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No employee");
        UUID stranger = UUID.randomUUID();
        assertThatThrownBy(
                        () -> exportService.preview(runId, new BankFileExportRequest(template.id(), List.of(stranger))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(stranger.toString());

        BankFileExportResponse made =
                exportService.generate(runId, new BankFileExportRequest(template.id(), List.of(paid)));
        assertThat(made.selectedEmployeeCount()).isEqualTo(1);

        templateService.delete(template.id());
        BankFileExportResponse kept = exportService.list(runId).get(0);
        assertThat(kept.templateId()).isNull();
        assertThat(kept.templateName()).isEqualTo("HDFC bulk");
    }
}
