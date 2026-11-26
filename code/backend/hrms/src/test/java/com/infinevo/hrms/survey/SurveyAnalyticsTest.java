package com.infinevo.hrms.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SurveyAnalyticsTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    private SurveyRepository surveyRepository;
    private SurveyQuestionRepository questionRepository;
    private SurveyResponseRepository responseRepository;
    private SurveyAnswerRepository answerRepository;
    private SurveyAnalyticsServiceImpl analyticsService;

    @BeforeEach
    void setUp() {
        surveyRepository = mock(SurveyRepository.class);
        questionRepository = mock(SurveyQuestionRepository.class);
        responseRepository = mock(SurveyResponseRepository.class);
        answerRepository = mock(SurveyAnswerRepository.class);

        analyticsService = new SurveyAnalyticsServiceImpl(
                surveyRepository, questionRepository, responseRepository, answerRepository, null, new ObjectMapper());
    }

    @Test
    @DisplayName("getResults withholds results when survey is anonymous and responses count is less than 5")
    void getResults_anonymousUnderThreshold_withheld() {
        UUID surveyId = UUID.randomUUID();
        Survey survey = new Survey();
        survey.setId(surveyId);
        survey.setTenantId(TENANT_ID);
        survey.setTitle("Secret Feedback");
        survey.setAnonymous(true);

        when(surveyRepository.findByIdAndTenantId(surveyId, TENANT_ID)).thenReturn(Optional.of(survey));
        when(responseRepository.countByTenantIdAndSurveyId(TENANT_ID, surveyId)).thenReturn(3L);

        SurveyResultsResponse results = analyticsService.getResults(TENANT_ID, surveyId);

        assertThat(results.resultsWithheld()).isTrue();
        assertThat(results.totalResponses()).isEqualTo(3L);
        assertThat(results.withholdingReason()).contains("at least 5 responses");
        assertThat(results.questionResults()).isEmpty();
    }

    @Test
    @DisplayName("getResults reveals calculations when anonymous responses reach threshold of 5")
    void getResults_anonymousReachesThreshold_revealsCalculations() {
        UUID surveyId = UUID.randomUUID();
        Survey survey = new Survey();
        survey.setId(surveyId);
        survey.setTenantId(TENANT_ID);
        survey.setTitle("Secret Feedback");
        survey.setAnonymous(true);

        UUID qId = UUID.randomUUID();
        SurveyQuestion q = new SurveyQuestion();
        q.setId(qId);
        q.setTenantId(TENANT_ID);
        q.setSurveyId(surveyId);
        q.setQuestionText("Rating 1 to 5");
        q.setQuestionType(QuestionType.RATING_1_5);

        SurveyAnswer a1 = new SurveyAnswer();
        a1.setRatingValue(5);
        SurveyAnswer a2 = new SurveyAnswer();
        a2.setRatingValue(4);
        SurveyAnswer a3 = new SurveyAnswer();
        a3.setRatingValue(5);
        SurveyAnswer a4 = new SurveyAnswer();
        a4.setRatingValue(4);
        SurveyAnswer a5 = new SurveyAnswer();
        a5.setRatingValue(2);

        when(surveyRepository.findByIdAndTenantId(surveyId, TENANT_ID)).thenReturn(Optional.of(survey));
        when(responseRepository.countByTenantIdAndSurveyId(TENANT_ID, surveyId)).thenReturn(5L);
        when(questionRepository.findByTenantIdAndSurveyIdOrderBySortOrderAsc(TENANT_ID, surveyId))
                .thenReturn(List.of(q));
        when(answerRepository.findByTenantIdAndQuestionId(TENANT_ID, qId)).thenReturn(List.of(a1, a2, a3, a4, a5));

        SurveyResultsResponse results = analyticsService.getResults(TENANT_ID, surveyId);

        assertThat(results.resultsWithheld()).isFalse();
        assertThat(results.totalResponses()).isEqualTo(5L);
        assertThat(results.questionResults()).hasSize(1);

        QuestionResultDto qRes = results.questionResults().get(0);
        // (5 + 4 + 5 + 4 + 2) / 5 = 20 / 5 = 4.00
        assertThat(qRes.averageRating()).isEqualTo(4.00);
        assertThat(qRes.ratingDistribution().get(5)).isEqualTo(2L);
        assertThat(qRes.ratingDistribution().get(4)).isEqualTo(2L);
        assertThat(qRes.ratingDistribution().get(2)).isEqualTo(1L);
    }
}
