package com.infinevo.core.holiday;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.infinevo.shared.error.ApiError;
import com.infinevo.shared.error.ApiErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class HolidayCalendarControllerTest {

    private HolidayCalendarService calendarService;
    private HolidayQueryService queryService;
    private HolidayCalendarController controller;

    @BeforeEach
    void setUp() {
        calendarService = mock(HolidayCalendarService.class);
        queryService = mock(HolidayQueryService.class);
        controller = new HolidayCalendarController(calendarService, queryService);
    }

    @Test
    @DisplayName(
            "W-17 (Issue #17): DataIntegrityViolationException on concurrent race returns HTTP 409 Conflict instead of 500")
    void dataIntegrityViolationReturns409Conflict() {
        DataIntegrityViolationException ex =
                new DataIntegrityViolationException("duplicate key value violates unique constraint");

        ResponseEntity<ApiErrorResponse> response = controller.handleConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ApiError.CONFLICT.name());
        assertThat(response.getBody().message())
                .contains("A resource with these details already exists or a concurrent conflict occurred");
    }

    @Test
    @DisplayName("IllegalStateException returns HTTP 409 Conflict")
    void illegalStateExceptionReturns409Conflict() {
        IllegalStateException ex = new IllegalStateException("A default holiday calendar already exists");

        ResponseEntity<ApiErrorResponse> response = controller.handleConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ApiError.CONFLICT.name());
        assertThat(response.getBody().message()).contains("A default holiday calendar already exists");
    }
}
