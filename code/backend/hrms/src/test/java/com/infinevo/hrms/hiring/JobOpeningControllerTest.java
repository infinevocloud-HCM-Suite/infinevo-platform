package com.infinevo.hrms.hiring;

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

class JobOpeningControllerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private MockMvc mvc;
    private JobOpeningService jobOpeningService;
    private JobApplicationService jobApplicationService;
    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);

        jobOpeningService = mock(JobOpeningService.class);
        jobApplicationService = mock(JobApplicationService.class);
        JobOpeningController controller = new JobOpeningController(jobOpeningService, jobApplicationService);

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
    @DisplayName("GET /api/v1/hrms/job-openings returns 200 with list of openings")
    void listOpenings_returns200() throws Exception {
        JobOpeningResponse opening = new JobOpeningResponse(
                UUID.randomUUID(),
                "JOB-101",
                "Frontend Architect",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                JobOpeningEmploymentType.FULL_TIME,
                1,
                0,
                null,
                null,
                null,
                null,
                null,
                JobOpeningStatus.OPEN,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                Instant.now(),
                Instant.now());

        when(jobOpeningService.listOpenings(TENANT_ID, null)).thenReturn(List.of(opening));

        mvc.perform(get("/api/v1/hrms/job-openings").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.data[0].openingCode").value("JOB-101"))
                .andExpect(jsonPath("$.data[0].title").value("Frontend Architect"));
    }

    @Test
    @DisplayName("POST /api/v1/hrms/job-openings returns 201 when valid")
    void createOpening_returns201() throws Exception {
        JobOpeningRequest req = new JobOpeningRequest(
                "JOB-102",
                "Product Manager",
                null,
                null,
                null,
                null,
                JobOpeningEmploymentType.FULL_TIME,
                2,
                null,
                null,
                null,
                null,
                "Lead roadmap",
                LocalDate.of(2026, 12, 31));

        JobOpeningResponse res = new JobOpeningResponse(
                UUID.randomUUID(),
                "JOB-102",
                "Product Manager",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                JobOpeningEmploymentType.FULL_TIME,
                2,
                0,
                null,
                null,
                null,
                null,
                "Lead roadmap",
                JobOpeningStatus.DRAFT,
                LocalDate.of(2026, 12, 31),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                Instant.now(),
                Instant.now());

        when(jobOpeningService.createOpening(eq(TENANT_ID), any(JobOpeningRequest.class), any()))
                .thenReturn(res);

        mvc.perform(post("/api/v1/hrms/job-openings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.openingCode").value("JOB-102"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("POST /api/v1/hrms/job-openings returns 409 when openingCode already exists")
    void createOpening_duplicateCode_returns409() throws Exception {
        JobOpeningRequest req = new JobOpeningRequest(
                "JOB-EXISTS", "PM", null, null, null, null, null, 1, null, null, null, null, null, null);

        when(jobOpeningService.createOpening(eq(TENANT_ID), any(JobOpeningRequest.class), any()))
                .thenThrow(new HiringConflictException("Job opening code already exists: JOB-EXISTS"));

        mvc.perform(post("/api/v1/hrms/job-openings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Job opening code already exists: JOB-EXISTS"));
    }

    @Test
    @DisplayName("PATCH /api/v1/hrms/job-openings/{id}/status updates status and returns 200")
    void changeStatus_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        JobOpeningStatusUpdateRequest req = new JobOpeningStatusUpdateRequest(JobOpeningStatus.OPEN);

        JobOpeningResponse res = new JobOpeningResponse(
                id,
                "JOB-103",
                "QA Engineer",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                JobOpeningEmploymentType.FULL_TIME,
                1,
                0,
                null,
                null,
                null,
                null,
                null,
                JobOpeningStatus.OPEN,
                null,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                Instant.now(),
                Instant.now());

        when(jobOpeningService.changeStatus(eq(TENANT_ID), eq(id), eq(JobOpeningStatus.OPEN), any()))
                .thenReturn(res);

        mvc.perform(patch("/api/v1/hrms/job-openings/" + id + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"));
    }
}
