package com.infinevo.payroll.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.notification.Anchor;
import com.infinevo.core.notification.ReminderRule;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindow;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindowRepository;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationRules;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ProofDueDateAnchorResolver} (W-34.3 spec section 7).
 */
@ExtendWith(MockitoExtension.class)
class ProofDueDateAnchorResolverTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final ZoneId ZONE = TaxDeclarationRules.ZONE;

    @Mock
    private IncomeTaxDeclarationWindowRepository windowRepository;

    @Mock
    private ReminderRule reminderRule;

    private ProofDueDateAnchorResolver resolver;

    @BeforeEach
    void setUp() {
        // default clock at 2026-06-15T10:00:00 IST (FY 2026-2027)
        Clock clock = Clock.fixed(Instant.parse("2026-06-15T04:30:00Z"), ZONE);
        resolver = new ProofDueDateAnchorResolver(windowRepository, clock);
    }

    @Test
    @DisplayName("anchor() returns Anchor.POI_DUE_DATE")
    void anchorIsPoiDueDate() {
        assertThat(resolver.anchor()).isEqualTo(Anchor.POI_DUE_DATE);
    }

    @Test
    @DisplayName("Returns due date when window exists, has opened, has due date, and is not locked")
    void returnsDueDateWhenWindowOpenAndSet() {
        IncomeTaxDeclarationWindow window = new IncomeTaxDeclarationWindow();
        window.setTenantId(TENANT_ID);
        window.setFinancialYear("2026-2027");
        LocalDate dueDate = LocalDate.of(2027, 1, 31);
        window.setPoiOpensOn(LocalDate.of(2026, 6, 1));
        window.setPoiDueDate(dueDate);
        window.setPoiLocked(false);

        when(windowRepository.findByTenantIdAndFinancialYear(TENANT_ID, "2026-2027"))
                .thenReturn(Optional.of(window));

        Optional<LocalDate> result = resolver.resolveAnchorDate(reminderRule, TENANT_ID);

        assertThat(result).contains(dueDate);
        verify(windowRepository).findByTenantIdAndFinancialYear(TENANT_ID, "2026-2027");
    }

    @Test
    @DisplayName("Returns empty when window is locked")
    void returnsEmptyWhenLocked() {
        IncomeTaxDeclarationWindow window = new IncomeTaxDeclarationWindow();
        window.setTenantId(TENANT_ID);
        window.setFinancialYear("2026-2027");
        window.setPoiDueDate(LocalDate.of(2027, 1, 31));
        window.setPoiLocked(true);

        when(windowRepository.findByTenantIdAndFinancialYear(TENANT_ID, "2026-2027"))
                .thenReturn(Optional.of(window));

        Optional<LocalDate> result = resolver.resolveAnchorDate(reminderRule, TENANT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Returns empty when due date is unset (null)")
    void returnsEmptyWhenDueDateUnset() {
        IncomeTaxDeclarationWindow window = new IncomeTaxDeclarationWindow();
        window.setTenantId(TENANT_ID);
        window.setFinancialYear("2026-2027");
        window.setPoiDueDate(null);
        window.setPoiLocked(false);

        when(windowRepository.findByTenantIdAndFinancialYear(TENANT_ID, "2026-2027"))
                .thenReturn(Optional.of(window));

        Optional<LocalDate> result = resolver.resolveAnchorDate(reminderRule, TENANT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Returns empty before the proof window opens: submit would refuse, so no reminder")
    void returnsEmptyBeforeWindowOpens() {
        IncomeTaxDeclarationWindow window = new IncomeTaxDeclarationWindow();
        window.setPoiOpensOn(LocalDate.of(2026, 6, 16)); // tomorrow
        window.setPoiDueDate(LocalDate.of(2027, 1, 31));
        window.setPoiLocked(false);

        when(windowRepository.findByTenantIdAndFinancialYear(TENANT_ID, "2026-2027"))
                .thenReturn(Optional.of(window));

        assertThat(resolver.resolveAnchorDate(reminderRule, TENANT_ID)).isEmpty();
    }

    @Test
    @DisplayName("Returns empty when the opening date is unset: the window has never opened")
    void returnsEmptyWhenOpensOnUnset() {
        IncomeTaxDeclarationWindow window = new IncomeTaxDeclarationWindow();
        window.setPoiOpensOn(null);
        window.setPoiDueDate(LocalDate.of(2027, 1, 31));
        window.setPoiLocked(false);

        when(windowRepository.findByTenantIdAndFinancialYear(TENANT_ID, "2026-2027"))
                .thenReturn(Optional.of(window));

        assertThat(resolver.resolveAnchorDate(reminderRule, TENANT_ID)).isEmpty();
    }

    @Test
    @DisplayName("Returns empty when no window exists for the financial year")
    void returnsEmptyWhenNoWindowForYear() {
        when(windowRepository.findByTenantIdAndFinancialYear(TENANT_ID, "2026-2027"))
                .thenReturn(Optional.empty());

        Optional<LocalDate> result = resolver.resolveAnchorDate(reminderRule, TENANT_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Financial year resolved as 2025-2026 on 31 March vs 2026-2027 on 1 April")
    void resolvesCorrectFinancialYearAcrossYearBoundary() {
        // 31 March 2026 at 23:59:00 IST -> FY 2025-2026
        Clock clockMarch31 = Clock.fixed(Instant.parse("2026-03-31T18:29:00Z"), ZONE);
        ProofDueDateAnchorResolver resolverMarch31 = new ProofDueDateAnchorResolver(windowRepository, clockMarch31);

        IncomeTaxDeclarationWindow windowPrev = new IncomeTaxDeclarationWindow();
        windowPrev.setPoiOpensOn(LocalDate.of(2025, 12, 1));
        windowPrev.setPoiDueDate(LocalDate.of(2026, 1, 15));
        windowPrev.setPoiLocked(false);

        when(windowRepository.findByTenantIdAndFinancialYear(TENANT_ID, "2025-2026"))
                .thenReturn(Optional.of(windowPrev));

        Optional<LocalDate> resultMarch31 = resolverMarch31.resolveAnchorDate(reminderRule, TENANT_ID);
        assertThat(resultMarch31).contains(LocalDate.of(2026, 1, 15));
        verify(windowRepository).findByTenantIdAndFinancialYear(TENANT_ID, "2025-2026");

        // 1 April 2026 at 00:01:00 IST -> FY 2026-2027
        Clock clockApril1 = Clock.fixed(Instant.parse("2026-03-31T18:31:00Z"), ZONE);
        ProofDueDateAnchorResolver resolverApril1 = new ProofDueDateAnchorResolver(windowRepository, clockApril1);

        IncomeTaxDeclarationWindow windowNext = new IncomeTaxDeclarationWindow();
        windowNext.setPoiOpensOn(LocalDate.of(2026, 4, 1));
        windowNext.setPoiDueDate(LocalDate.of(2027, 1, 15));
        windowNext.setPoiLocked(false);

        when(windowRepository.findByTenantIdAndFinancialYear(TENANT_ID, "2026-2027"))
                .thenReturn(Optional.of(windowNext));

        Optional<LocalDate> resultApril1 = resolverApril1.resolveAnchorDate(reminderRule, TENANT_ID);
        assertThat(resultApril1).contains(LocalDate.of(2027, 1, 15));
        verify(windowRepository).findByTenantIdAndFinancialYear(TENANT_ID, "2026-2027");
    }
}
