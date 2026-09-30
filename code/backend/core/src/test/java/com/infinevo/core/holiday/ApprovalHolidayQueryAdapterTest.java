package com.infinevo.core.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.approval.DefaultHolidayQueryService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

/**
 * Unit tests for {@link ApprovalHolidayQueryAdapter} (W-17 F-1).
 */
class ApprovalHolidayQueryAdapterTest {

    private final UUID locationId = UUID.randomUUID();

    @Test
    @DisplayName("Holiday ranges are expanded to dates, clipped to the window; restricted holidays skipped")
    void expandsAndClipsRanges_skipsRestricted() {
        HolidayQueryService delegate = mock(HolidayQueryService.class);
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        when(delegate.holidaysBetween(locationId, from, to))
                .thenReturn(List.of(
                        holiday(LocalDate.of(2026, 2, 27), LocalDate.of(2026, 3, 2), false),
                        holiday(LocalDate.of(2026, 3, 15), LocalDate.of(2026, 3, 15), true),
                        holiday(LocalDate.of(2026, 3, 30), LocalDate.of(2026, 4, 3), false)));

        List<LocalDate> dates = new ApprovalHolidayQueryAdapter(delegate).holidaysBetween(locationId, from, to);

        assertThat(dates)
                .containsExactly(
                        LocalDate.of(2026, 3, 1),
                        LocalDate.of(2026, 3, 2),
                        LocalDate.of(2026, 3, 30),
                        LocalDate.of(2026, 3, 31));
    }

    @Test
    @DisplayName("The empty fallback is not a bean, so the adapter is the only one Spring can inject")
    void fallbackIsNotABean() {
        assertThat(DefaultHolidayQueryService.class.isAnnotationPresent(Component.class))
                .isFalse();
        assertThat(ApprovalHolidayQueryAdapter.class.isAnnotationPresent(Component.class))
                .isTrue();
    }

    private HolidayResponse holiday(LocalDate from, LocalDate to, boolean restricted) {
        return new HolidayResponse(UUID.randomUUID(), UUID.randomUUID(), "H", from, to, restricted, null);
    }
}
