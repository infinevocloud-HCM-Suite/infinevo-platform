package com.infinevo.hrms.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SurveyServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    private SurveyRepository surveyRepository;
    private SurveyQuestionRepository questionRepository;
    private SurveyResponseRepository responseRepository;
    private SurveyAnswerRepository answerRepository;
    private SurveyServiceImpl service;

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
                new ObjectMapper());
    }

    @Test
    @DisplayName("createSurvey creates survey in DRAFT state with ordered questions")
    void createSurvey_success() {
        SurveyQuestionRequest q1 = new SurveyQuestionRequest(
                "Rate your work life balance", QuestionType.RATING_1_5, null, true, (short) 1);
        SurveyQuestionRequest q2 = new SurveyQuestionRequest(
                "Choose primary work tool", QuestionType.SINGLE_CHOICE, List.of("Laptop", "Desktop"), true, (short) 2);

        SurveyRequest request = new SurveyRequest(
                "Q4 Engagement Pulse",
                "Quarterly pulse survey",
                SurveyCategory.PULSE_POLL,
                true,
                SurveyTargetType.ALL_EMPLOYEES,
                null,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 15),
                List.of(q1, q2));

        when(surveyRepository.save(any(Survey.class))).thenAnswer(invocation -> {
            Survey s = invocation.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });

        when(questionRepository.save(any(SurveyQuestion.class))).thenAnswer(invocation -> {
            SurveyQuestion q = invocation.getArgument(0);
            q.setId(UUID.randomUUID());
            return q;
        });

        SurveyResponseDto created = service.createSurvey(TENANT_ID, request, "hr-admin");

        assertThat(created).isNotNull();
        assertThat(created.title()).isEqualTo("Q4 Engagement Pulse");
        assertThat(created.status()).isEqualTo(SurveyStatus.DRAFT);
        assertThat(created.isAnonymous()).isTrue();
        assertThat(created.questions()).hasSize(2);
        assertThat(created.questions().get(0).questionText()).isEqualTo("Rate your work life balance");
    }

    @Test
    @DisplayName("createSurvey throws validation exception when end date is before start date")
    void createSurvey_invalidDates_throws() {
        SurveyQuestionRequest q = new SurveyQuestionRequest("Rating", QuestionType.RATING_1_5, null, true, (short) 1);
        SurveyRequest req = new SurveyRequest(
                "Survey",
                "desc",
                SurveyCategory.GENERAL,
                false,
                SurveyTargetType.ALL_EMPLOYEES,
                null,
                LocalDate.of(2026, 12, 15),
                LocalDate.of(2026, 12, 1),
                List.of(q));

        assertThatThrownBy(() -> service.createSurvey(TENANT_ID, req, "admin"))
                .isInstanceOf(SurveyValidationException.class)
                .hasMessageContaining("End date cannot be before start date");
    }

    @Test
    @DisplayName("updateStatus publishes survey when valid")
    void updateStatus_publish_success() {
        UUID surveyId = UUID.randomUUID();
        Survey survey = new Survey();
        survey.setId(surveyId);
        survey.setTenantId(TENANT_ID);
        survey.setTitle("Annual Survey");
        survey.setStatus(SurveyStatus.DRAFT);
        survey.setStartDate(LocalDate.of(2026, 12, 1));
        survey.setEndDate(LocalDate.of(2026, 12, 31));

        SurveyQuestion q = new SurveyQuestion();
        q.setId(UUID.randomUUID());
        q.setTenantId(TENANT_ID);
        q.setSurveyId(surveyId);
        q.setQuestionText("Satisfaction");
        q.setQuestionType(QuestionType.RATING_1_5);

        when(surveyRepository.findByIdAndTenantId(surveyId, TENANT_ID)).thenReturn(Optional.of(survey));
        when(questionRepository.findByTenantIdAndSurveyIdOrderBySortOrderAsc(TENANT_ID, surveyId))
                .thenReturn(List.of(q));
        when(surveyRepository.save(any(Survey.class))).thenAnswer(inv -> inv.getArgument(0));

        SurveyResponseDto updated = service.updateStatus(TENANT_ID, surveyId, SurveyStatus.PUBLISHED, "hr-lead");

        assertThat(updated.status()).isEqualTo(SurveyStatus.PUBLISHED);
    }

    @Test
    @DisplayName("updateStatus throws exception when publishing survey with zero questions")
    void updateStatus_publishWithoutQuestions_throws() {
        UUID surveyId = UUID.randomUUID();
        Survey survey = new Survey();
        survey.setId(surveyId);
        survey.setTenantId(TENANT_ID);
        survey.setStatus(SurveyStatus.DRAFT);

        when(surveyRepository.findByIdAndTenantId(surveyId, TENANT_ID)).thenReturn(Optional.of(survey));
        when(questionRepository.findByTenantIdAndSurveyIdOrderBySortOrderAsc(TENANT_ID, surveyId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.updateStatus(TENANT_ID, surveyId, SurveyStatus.PUBLISHED, "hr-lead"))
                .isInstanceOf(SurveyValidationException.class)
                .hasMessageContaining("Cannot publish a survey without questions");
    }
}
