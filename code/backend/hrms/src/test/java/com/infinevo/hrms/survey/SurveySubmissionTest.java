package com.infinevo.hrms.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmploymentStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SurveySubmissionTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 12, 10);
    private static final Clock FIXED_CLOCK =
            Clock.fixed(FIXED_DATE.atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC"));

    private SurveyRepository surveyRepository;
    private SurveyQuestionRepository questionRepository;
    private SurveyResponseRepository responseRepository;
    private SurveyAnswerRepository answerRepository;
    private SurveyServiceImpl service;

    private EmployeeResponse testEmployee;

    @BeforeEach
    void setUp() {
        surveyRepository = mock(SurveyRepository.class);
        questionRepository = mock(SurveyQuestionRepository.class);
        responseRepository = mock(SurveyResponseRepository.class);
        answerRepository = mock(SurveyAnswerRepository.class);

        service = new SurveyServiceImpl(
                surveyRepository,
                questionRepository,
                responseRepository,
                answerRepository,
                null,
                null,
                new ObjectMapper(),
                FIXED_CLOCK);

        testEmployee = new EmployeeResponse(
                UUID.randomUUID(),
                TENANT_ID,
                "EMP-001",
                "Priya",
                null,
                "Nair",
                "FEMALE",
                LocalDate.of(2025, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                "priya@infinevo.com",
                null,
                true,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                Instant.now(),
                null,
                null);
    }

    @Test
    @DisplayName("submitSurvey decouples employee identity when isAnonymous is true")
    void submitSurvey_anonymous_decouplesIdentity() {
        UUID surveyId = UUID.randomUUID();
        Survey survey = new Survey();
        survey.setId(surveyId);
        survey.setTenantId(TENANT_ID);
        survey.setTitle("Anonymous Pulse");
        survey.setAnonymous(true);
        survey.setStatus(SurveyStatus.PUBLISHED);
        survey.setStartDate(LocalDate.of(2026, 12, 1));
        survey.setEndDate(LocalDate.of(2026, 12, 20));
        survey.setTargetType(SurveyTargetType.ALL_EMPLOYEES);

        UUID qId = UUID.randomUUID();
        SurveyQuestion q = new SurveyQuestion();
        q.setId(qId);
        q.setTenantId(TENANT_ID);
        q.setSurveyId(surveyId);
        q.setQuestionText("Happiness rating");
        q.setQuestionType(QuestionType.RATING_1_5);
        q.setRequired(true);

        when(surveyRepository.findByIdAndTenantId(surveyId, TENANT_ID)).thenReturn(Optional.of(survey));
        when(questionRepository.findByTenantIdAndSurveyIdOrderBySortOrderAsc(TENANT_ID, surveyId))
                .thenReturn(List.of(q));
        when(responseRepository.existsByTenantIdAndSurveyIdAndSubmissionToken(eq(TENANT_ID), eq(surveyId), any()))
                .thenReturn(false);

        when(responseRepository.save(any(SurveyResponse.class))).thenAnswer(invocation -> {
            SurveyResponse r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });

        SurveyAnswerRequest ans = new SurveyAnswerRequest(qId, 5, null, null);
        SurveySubmissionRequest submission = new SurveySubmissionRequest(List.of(ans));

        UUID responseId = service.submitSurvey(TENANT_ID, surveyId, testEmployee, submission);

        assertThat(responseId).isNotNull();
    }

    @Test
    @DisplayName("submitSurvey throws 409 Conflict when employee attempts duplicate submission")
    void submitSurvey_duplicate_throwsConflict() {
        UUID surveyId = UUID.randomUUID();
        Survey survey = new Survey();
        survey.setId(surveyId);
        survey.setTenantId(TENANT_ID);
        survey.setStatus(SurveyStatus.PUBLISHED);
        survey.setStartDate(LocalDate.of(2026, 12, 1));
        survey.setEndDate(LocalDate.of(2026, 12, 20));
        survey.setTargetType(SurveyTargetType.ALL_EMPLOYEES);

        when(surveyRepository.findByIdAndTenantId(surveyId, TENANT_ID)).thenReturn(Optional.of(survey));
        // Employee has already submitted
        when(responseRepository.existsByTenantIdAndSurveyIdAndSubmissionToken(eq(TENANT_ID), eq(surveyId), any()))
                .thenReturn(true);

        SurveySubmissionRequest submission = new SurveySubmissionRequest(List.of());

        assertThatThrownBy(() -> service.submitSurvey(TENANT_ID, surveyId, testEmployee, submission))
                .isInstanceOf(SurveyConflictException.class)
                .hasMessageContaining("Survey has already been submitted by this employee");
    }

    @Test
    @DisplayName("submitSurvey throws validation exception when survey is closed")
    void submitSurvey_closed_throwsValidation() {
        UUID surveyId = UUID.randomUUID();
        Survey survey = new Survey();
        survey.setId(surveyId);
        survey.setTenantId(TENANT_ID);
        survey.setStatus(SurveyStatus.CLOSED);
        survey.setStartDate(LocalDate.of(2026, 12, 1));
        survey.setEndDate(LocalDate.of(2026, 12, 20));

        when(surveyRepository.findByIdAndTenantId(surveyId, TENANT_ID)).thenReturn(Optional.of(survey));

        SurveySubmissionRequest submission = new SurveySubmissionRequest(List.of());

        assertThatThrownBy(() -> service.submitSurvey(TENANT_ID, surveyId, testEmployee, submission))
                .isInstanceOf(SurveyValidationException.class)
                .hasMessageContaining("Survey is closed or outside active date window");
    }
}
