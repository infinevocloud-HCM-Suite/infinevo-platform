package com.infinevo.hrms.survey;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SurveyControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MockMvc mvc;
    private SurveyService surveyService;
    private SurveyAnalyticsService analyticsService;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);

        surveyService = mock(SurveyService.class);
        analyticsService = mock(SurveyAnalyticsService.class);
        SurveyController controller = new SurveyController(surveyService, analyticsService);

        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("GET /api/v1/hrms/surveys returns 200 with survey list")
    void listSurveys_returns200() throws Exception {
        SurveyResponseDto dto = new SurveyResponseDto(
                UUID.randomUUID(),
                "Annual Pulse",
                "Description",
                SurveyCategory.PULSE_POLL,
                false,
                SurveyTargetType.ALL_EMPLOYEES,
                null,
                null,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 31),
                SurveyStatus.DRAFT,
                0,
                List.of(),
                Instant.now(),
                Instant.now());

        when(surveyService.listSurveys(TENANT_ID, null)).thenReturn(List.of(dto));

        mvc.perform(get("/api/v1/hrms/surveys").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data[0].title").value("Annual Pulse"));
    }

    @Test
    @DisplayName("POST /api/v1/hrms/surveys returns 201 on success")
    void createSurvey_returns201() throws Exception {
        SurveyQuestionRequest q =
                new SurveyQuestionRequest("Rate your day", QuestionType.RATING_1_5, null, true, (short) 1);
        SurveyRequest req = new SurveyRequest(
                "Daily Pulse",
                "Daily check",
                SurveyCategory.PULSE_POLL,
                false,
                SurveyTargetType.ALL_EMPLOYEES,
                null,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 31),
                List.of(q));

        SurveyResponseDto res = new SurveyResponseDto(
                UUID.randomUUID(),
                "Daily Pulse",
                "Daily check",
                SurveyCategory.PULSE_POLL,
                false,
                SurveyTargetType.ALL_EMPLOYEES,
                null,
                null,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 31),
                SurveyStatus.DRAFT,
                0,
                List.of(),
                Instant.now(),
                Instant.now());

        when(surveyService.createSurvey(eq(TENANT_ID), any(SurveyRequest.class), any()))
                .thenReturn(res);

        mvc.perform(post("/api/v1/hrms/surveys")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data.title").value("Daily Pulse"));
    }

    @Test
    @DisplayName("PATCH /api/v1/hrms/surveys/{id}/status updates status")
    void updateStatus_returns200() throws Exception {
        UUID surveyId = UUID.randomUUID();
        SurveyStatusUpdateRequest req = new SurveyStatusUpdateRequest(SurveyStatus.PUBLISHED);

        SurveyResponseDto res = new SurveyResponseDto(
                surveyId,
                "Published Pulse",
                "Description",
                SurveyCategory.PULSE_POLL,
                false,
                SurveyTargetType.ALL_EMPLOYEES,
                null,
                null,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 31),
                SurveyStatus.PUBLISHED,
                0,
                List.of(),
                Instant.now(),
                Instant.now());

        when(surveyService.updateStatus(eq(TENANT_ID), eq(surveyId), eq(SurveyStatus.PUBLISHED), any()))
                .thenReturn(res);

        mvc.perform(patch("/api/v1/hrms/surveys/" + surveyId + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("GET /api/v1/hrms/surveys/{id}/results returns aggregated analytics")
    void getResults_returns200() throws Exception {
        UUID surveyId = UUID.randomUUID();
        SurveyResultsResponse res =
                new SurveyResultsResponse(surveyId, "Feedback Results", false, 100, 45, 45.0, false, null, List.of());

        when(analyticsService.getResults(TENANT_ID, surveyId)).thenReturn(res);

        mvc.perform(get("/api/v1/hrms/surveys/" + surveyId + "/results").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalResponses").value(45))
                .andExpect(jsonPath("$.data.participationRate").value(45.0));
    }
}
