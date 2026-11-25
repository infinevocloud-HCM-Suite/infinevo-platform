package com.infinevo.payroll.payrun.bankfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.report.ExportFormat;
import com.infinevo.shared.tenant.TenantContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** W-88.2 spec section 4.3 — template rules, the built-in, copy naming. */
class BankFileTemplateServiceTest {

    private static final UUID TENANT = UUID.randomUUID();

    private BankFileTemplateRepository repository;
    private BankFileTemplateServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        repository = mock(BankFileTemplateRepository.class);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> withId(inv.getArgument(0)));
        service = new BankFileTemplateServiceImpl(repository);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static BankFileTemplate withId(BankFileTemplate template) {
        try {
            var field = BankFileTemplate.class.getDeclaredField("id");
            field.setAccessible(true);
            if (field.get(template) == null) {
                field.set(template, UUID.randomUUID());
            }
            return template;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BankFileColumn field(String heading, BankFileField field, String format) {
        return new BankFileColumn(heading, BankFileColumnSource.FIELD, field, null, format);
    }

    private static BankFileTemplateRequest request(String name, List<BankFileColumn> columns) {
        return new BankFileTemplateRequest(name, ExportFormat.XLSX, false, columns);
    }

    @Test
    @DisplayName("create keeps the columns in order, trims, and takes the format and header switch")
    void createKeepsOrder() {
        BankFileTemplateResponse created = service.create(request(
                "  HDFC bulk  ",
                List.of(
                        new BankFileColumn("Txn", BankFileColumnSource.FIXED, null, "NEFT", null),
                        field("Beneficiary A/c", BankFileField.ACCOUNT_NUMBER, null),
                        field("Amount", BankFileField.NET_PAY, "PAISE"),
                        new BankFileColumn("#", BankFileColumnSource.SERIAL, null, null, null))));

        assertThat(created.name()).isEqualTo("HDFC bulk");
        assertThat(created.fileFormat()).isEqualTo(ExportFormat.XLSX);
        assertThat(created.includeHeader()).isFalse();
        assertThat(created.builtIn()).isFalse();
        assertThat(created.columns())
                .extracting(BankFileColumn::heading)
                .containsExactly("Txn", "Beneficiary A/c", "Amount", "#");
        assertThat(created.columns().get(2).format()).isEqualTo("PAISE");
    }

    @Test
    @DisplayName("a name already used by the company, ignoring case, is a conflict")
    void duplicateNameIsConflict() {
        when(repository.existsByTenantIdAndNameIgnoreCase(TENANT, "HDFC bulk")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request("HDFC bulk", List.of(field("A", BankFileField.IFSC, null)))))
                .isInstanceOf(BankFileTemplateException.class)
                .extracting(e -> ((BankFileTemplateException) e).kind())
                .isEqualTo(BankFileTemplateException.Kind.CONFLICT);
    }

    @Test
    @DisplayName("each rule in section 4.3 is a 400 naming the column")
    void validationRules() {
        assertThatThrownBy(() -> service.create(request(" ", List.of(field("A", BankFileField.IFSC, null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name is required");
        assertThatThrownBy(() -> service.create(request("X", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one column");
        assertThatThrownBy(() -> service.create(request(
                        "X", List.of(field("A", BankFileField.IFSC, null), field("a", BankFileField.BANK_NAME, null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("column 2")
                .hasMessageContaining("used twice");
        assertThatThrownBy(() -> service.create(request("X", List.of(field("A", BankFileField.IFSC, "dd/MM/yyyy")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("takes no format");
        assertThatThrownBy(() -> service.create(request("X", List.of(field("A", BankFileField.PAID_ON, "yyyyMMdd")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("format must be one of");
        assertThatThrownBy(() -> service.create(
                        request("X", List.of(new BankFileColumn("A", BankFileColumnSource.FIELD, null, null, null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("field is required");
        assertThatThrownBy(() -> service.create(request(
                        "X", List.of(new BankFileColumn("A\nB", BankFileColumnSource.SERIAL, null, null, null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("one line");
        List<BankFileColumn> tooMany = new ArrayList<>();
        for (int i = 0; i < 41; i++) {
            tooMany.add(new BankFileColumn("C" + i, BankFileColumnSource.SERIAL, null, null, null));
        }
        assertThatThrownBy(() -> service.create(request("X", tooMany)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most 40");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("the built-in comes first, matches W-88's ten columns, and cannot be changed or deleted")
    void builtIn() {
        when(repository.findByTenantIdOrderByNameAsc(TENANT)).thenReturn(List.of());
        List<BankFileTemplateResponse> all = service.list();
        assertThat(all).hasSize(1);
        BankFileTemplateResponse builtIn = all.get(0);
        assertThat(builtIn.id()).isEqualTo(BankFileTemplateService.BUILT_IN_ID);
        assertThat(builtIn.builtIn()).isTrue();
        assertThat(builtIn.columns()).hasSize(10);
        assertThat(service.get("generic-neft")).isSameAs(builtIn);

        assertThatThrownBy(() ->
                        service.update("generic-neft", request("X", List.of(field("A", BankFileField.IFSC, null)))))
                .isInstanceOf(BankFileTemplateException.class)
                .hasMessageContaining("copy it");
        assertThatThrownBy(() -> service.delete("generic-neft")).isInstanceOf(BankFileTemplateException.class);
    }

    @Test
    @DisplayName("copy names the new template Copy of …, numbered when taken")
    void copyNaming() {
        when(repository.existsByTenantIdAndNameIgnoreCase(TENANT, "Copy of Generic bulk NEFT"))
                .thenReturn(true);
        when(repository.existsByTenantIdAndNameIgnoreCase(TENANT, "Copy of Generic bulk NEFT (2)"))
                .thenReturn(false);

        BankFileTemplateResponse copy = service.copy("generic-neft");

        assertThat(copy.name()).isEqualTo("Copy of Generic bulk NEFT (2)");
        assertThat(copy.builtIn()).isFalse();
        assertThat(copy.columns()).hasSize(10);
    }

    @Test
    @DisplayName("an unknown or malformed id is not found")
    void notFound() {
        when(repository.findByIdAndTenantId(any(), eq(TENANT))).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> service.get(UUID.randomUUID().toString()))
                .isInstanceOf(BankFileTemplateException.class)
                .extracting(e -> ((BankFileTemplateException) e).kind())
                .isEqualTo(BankFileTemplateException.Kind.NOT_FOUND);
        assertThatThrownBy(() -> service.get("not-a-uuid")).isInstanceOf(BankFileTemplateException.class);
        verify(repository, never()).existsByTenantIdAndNameIgnoreCase(any(), anyString());
    }
}
