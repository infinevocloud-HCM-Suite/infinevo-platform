package com.infinevo.shared.audit;

import static com.infinevo.shared.audit.AuditTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.shared.audit.AuditQueryService.AuditLogView;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-22.1 — insert, update and delete each write exactly one audit row, with the right operation
 * and the right diff (spec section 7).
 */
@SpringBootTest(classes = AuditTestApp.class)
class AuditCaptureIT extends AbstractIntegrationTest {

    @Autowired
    private AuditedProbeRepository probes;

    @Autowired
    private AuditQueryService queryService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeAll
    static void applySchema() throws Exception {
        AuditTestSchema.apply();
    }

    @BeforeEach
    void reset() throws Exception {
        TenantContext.clear();
        AuditTestSchema.seedTenants();
        AuditTestSchema.clearAll();
    }

    private <T> T inTenantTransaction(UUID tenantId, Supplier<T> work) {
        TenantContext.set(tenantId);
        try {
            return new TransactionTemplate(transactionManager).execute(status -> work.get());
        } finally {
            TenantContext.clear();
        }
    }

    private List<AuditLogView> auditRows() {
        TenantContext.set(TENANT_A);
        try {
            return queryService
                    .search(null, null, null, null, null, PageRequest.of(0, 50))
                    .getContent();
        } finally {
            TenantContext.clear();
        }
    }

    /** The embedded address's value. It must not appear anywhere in the captured row. */
    private static final String SECRET_LINE1 = "12 Secret Lane";

    private static final String SECRET_ZIP = "560001";

    private UUID insertProbe() {
        return inTenantTransaction(TENANT_A, () -> probes.save(new AuditedProbe(
                        TENANT_A,
                        "Acme",
                        new BigDecimal("1250.5000"),
                        "sk-live-secret",
                        new ProbeAddress(SECRET_LINE1, SECRET_ZIP)))
                .getId());
    }

    @Test
    @DisplayName("Insert writes exactly one INSERT row, with money as a string and the token redacted")
    void insertWritesOneRow() throws Exception {
        UUID probeId = insertProbe();

        assertThat(AuditTestSchema.countAuditRows(TENANT_A)).isEqualTo(1);
        AuditLogView row = auditRows().get(0);
        assertThat(row.operation()).isEqualTo("INSERT");
        assertThat(row.entitySchema()).isEqualTo("core");
        assertThat(row.entityTable()).isEqualTo("audited_probe");
        assertThat(row.entityId()).isEqualTo(probeId.toString());
        assertThat(row.actorLabel()).isEqualTo(AuditWriter.SYSTEM_ACTOR);
        assertThat(row.actorUserId()).isNull();
        assertThat(row.oldValues()).isNull();
        assertThat(row.newValues()).containsEntry("name", "Acme");
        // Money inside jsonb is a string, never a float - spec section 11.
        assertThat(row.newValues()).containsEntry("amount", "1250.5000");
        assertThat(row.newValues()).containsEntry("api_token", AuditWriter.REDACTED);
        assertThat(row.newValues().values()).doesNotContain("sk-live-secret");
    }

    @Test
    @DisplayName("Update writes exactly one UPDATE row naming only the column that changed")
    void updateWritesOneRowWithDiff() throws Exception {
        UUID probeId = insertProbe();
        AuditTestSchema.clearAudit();

        inTenantTransaction(TENANT_A, () -> {
            AuditedProbe probe = probes.findById(probeId).orElseThrow();
            probe.setName("Acme Ltd");
            return probes.save(probe);
        });

        assertThat(AuditTestSchema.countAuditRows(TENANT_A)).isEqualTo(1);
        AuditLogView row = auditRows().get(0);
        assertThat(row.operation()).isEqualTo("UPDATE");
        assertThat(row.changedColumns()).containsExactly("name");
        assertThat(row.oldValues()).containsExactly(org.assertj.core.api.Assertions.entry("name", "Acme"));
        assertThat(row.newValues()).containsExactly(org.assertj.core.api.Assertions.entry("name", "Acme Ltd"));
    }

    @Test
    @DisplayName("Delete writes exactly one DELETE row carrying the old values")
    void deleteWritesOneRow() throws Exception {
        UUID probeId = insertProbe();
        AuditTestSchema.clearAudit();

        inTenantTransaction(TENANT_A, () -> {
            probes.deleteById(probeId);
            return null;
        });

        assertThat(AuditTestSchema.countAuditRows(TENANT_A)).isEqualTo(1);
        AuditLogView row = auditRows().get(0);
        assertThat(row.operation()).isEqualTo("DELETE");
        assertThat(row.entityId()).isEqualTo(probeId.toString());
        assertThat(row.oldValues()).containsEntry("name", "Acme");
        assertThat(row.newValues()).isNull();
    }

    @Test
    @DisplayName("An @Embedded component is captured as *** and its value appears nowhere in the row")
    void embeddedComponentIsRedactedEndToEnd() throws Exception {
        // W-13.2 spec section 2. `address` is one property over two columns, so the listener
        // cannot resolve it and the writer withholds it. Before the fix the whole object's
        // toString() was written here in clear, under a Java name no deny-list entry matches.
        insertProbe();

        AuditLogView row = auditRows().get(0);
        assertThat(row.newValues()).containsEntry("address", AuditWriter.REDACTED);
        assertThat(row.newValues().values()).doesNotContain(SECRET_LINE1, SECRET_ZIP);

        // Not only the parsed map: the raw row, every column of it.
        String raw = AuditTestSchema.rawAuditRowText(TENANT_A);
        assertThat(raw).doesNotContain(SECRET_LINE1);
        assertThat(raw).doesNotContain("Secret Lane");
        assertThat(raw).contains(AuditWriter.REDACTED);
        // Over-redaction would make the trail useless, so the ordinary columns are still there.
        assertThat(raw).contains("Acme");
    }

    @Test
    @DisplayName("Changing only the embedded component still names it, and still withholds both values")
    void embeddedComponentUpdateNamesButWithholds() throws Exception {
        UUID probeId = insertProbe();
        AuditTestSchema.clearAudit();

        inTenantTransaction(TENANT_A, () -> {
            AuditedProbe probe = probes.findById(probeId).orElseThrow();
            probe.setAddress(new ProbeAddress("9 Other Road", "560002"));
            return probes.save(probe);
        });

        assertThat(AuditTestSchema.countAuditRows(TENANT_A)).isEqualTo(1);
        AuditLogView row = auditRows().get(0);
        assertThat(row.operation()).isEqualTo("UPDATE");
        assertThat(row.changedColumns()).containsExactly("address");
        assertThat(row.oldValues()).containsEntry("address", AuditWriter.REDACTED);
        assertThat(row.newValues()).containsEntry("address", AuditWriter.REDACTED);

        String raw = AuditTestSchema.rawAuditRowText(TENANT_A);
        assertThat(raw).doesNotContain(SECRET_LINE1);
        assertThat(raw).doesNotContain("9 Other Road");
    }

    @Test
    @DisplayName("The query endpoint filters by entity and by actor")
    void queryFiltersByEntityAndActor() {
        insertProbe();

        TenantContext.set(TENANT_A);
        try {
            assertThat(queryService
                            .search("audited_probe", null, null, null, null, PageRequest.of(0, 50))
                            .getTotalElements())
                    .isEqualTo(1);
            assertThat(queryService
                            .search("some_other_table", null, null, null, null, PageRequest.of(0, 50))
                            .getTotalElements())
                    .isZero();
            assertThat(queryService
                            .search(null, null, AuditWriter.SYSTEM_ACTOR, null, null, PageRequest.of(0, 50))
                            .getTotalElements())
                    .isEqualTo(1);
            assertThat(queryService
                            .search(null, null, "someone-else", null, null, PageRequest.of(0, 50))
                            .getTotalElements())
                    .isZero();
        } finally {
            TenantContext.clear();
        }
    }
}
