package com.infinevo.core.payinput;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit test for {@link PayInputController}'s D-144 endpoints: the posting read, the lock (always
 * LOCKED over HTTP), no unlock endpoint, and the {@code 409} a CLOSED month answers with. The permission each carries
 * is the guard's to test ({@code PayInputGuardIT}); this is the mapping only.
 */
class PayInputControllerTest {

    private static final YearMonth OCTOBER = YearMonth.of(2026, 10);
    private static final YearMonth NOVEMBER = YearMonth.of(2026, 11);

    private MockMvc mvc;
    private PayInputService service;

    @BeforeEach
    void setUp() {
        service = mock(PayInputService.class);
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mvc = MockMvcBuilders.standaloneSetup(new PayInputController(service))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    @DisplayName("GET /posting/{period}: OPEN, posts to the month asked for")
    void postingOpen() throws Exception {
        when(service.posting(OCTOBER)).thenReturn(new PayInputPosting(OCTOBER, OCTOBER, PayInputPostingState.OPEN));

        mvc.perform(get("/api/v1/pay-inputs/posting/2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested_period").value("2026-10"))
                .andExpect(jsonPath("$.posted_period").value("2026-10"))
                .andExpect(jsonPath("$.state").value("OPEN"));
    }

    @Test
    @DisplayName("GET /posting/{period}: LOCKED still posts to the month")
    void postingLocked() throws Exception {
        when(service.posting(OCTOBER)).thenReturn(new PayInputPosting(OCTOBER, OCTOBER, PayInputPostingState.LOCKED));

        mvc.perform(get("/api/v1/pay-inputs/posting/2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.posted_period").value("2026-10"))
                .andExpect(jsonPath("$.state").value("LOCKED"));
    }

    @Test
    @DisplayName("GET /posting/{period}: CLOSED names the first open month")
    void postingClosed() throws Exception {
        when(service.posting(OCTOBER)).thenReturn(new PayInputPosting(OCTOBER, NOVEMBER, PayInputPostingState.CLOSED));

        mvc.perform(get("/api/v1/pay-inputs/posting/2026-10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requested_period").value("2026-10"))
                .andExpect(jsonPath("$.posted_period").value("2026-11"))
                .andExpect(jsonPath("$.state").value("CLOSED"));
    }

    @Test
    @DisplayName("GET /posting/{period}: a malformed month is a 400")
    void postingMalformed() throws Exception {
        mvc.perform(get("/api/v1/pay-inputs/posting/October")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("POST /periods/{period}/lock always locks at LOCKED")
    void lockIsAlwaysLocked() throws Exception {
        mvc.perform(post("/api/v1/pay-inputs/periods/2026-10/lock")).andExpect(status().isOk());

        verify(service).lock(OCTOBER, PayInputLockStage.LOCKED);
    }

    @Test
    @DisplayName("POST /periods/{period}/lock ignores a body asking for CLOSED - closing is payroll's, not HTTP's")
    void lockIgnoresAStageBody() throws Exception {
        mvc.perform(post("/api/v1/pay-inputs/periods/2026-10/lock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stage\":\"CLOSED\"}"))
                .andExpect(status().isOk());

        verify(service).lock(OCTOBER, PayInputLockStage.LOCKED);
        verify(service, never()).lock(OCTOBER, PayInputLockStage.CLOSED);
    }

    @Test
    @DisplayName("DELETE /periods/{period}/lock is not an endpoint")
    void noDeleteEndpoint() throws Exception {
        mvc.perform(delete("/api/v1/pay-inputs/periods/2026-10/lock")).andExpect(status().isMethodNotAllowed());
        verify(service, never()).unlock(any());
    }

    @Test
    @DisplayName("POST /pay-inputs for a CLOSED month: 409 naming the month to use")
    void recordClosed() throws Exception {
        when(service.recordEntry(any())).thenThrow(PayInputService.PeriodClosedException.forEntry(OCTOBER, NOVEMBER));

        mvc.perform(
                        post("/api/v1/pay-inputs")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"employee_id":"8f0c6a51-63a4-4c3e-9b55-0d1b9f1c2a11","period":"2026-10",
                                 "kind":"LOP_DAYS","quantity":"1.00","source_ref":"manual-lop:x","note":"absent"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message")
                        .value("October 2026 is closed (run approved). Enter this for November 2026."));
    }
}
