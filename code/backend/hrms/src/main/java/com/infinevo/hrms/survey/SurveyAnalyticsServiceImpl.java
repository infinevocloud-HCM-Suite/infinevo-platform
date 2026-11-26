package com.infinevo.hrms.survey;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.employee.EmployeeService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SurveyAnalyticsServiceImpl implements SurveyAnalyticsService {

    public static final int MINIMUM_ANONYMOUS_GROUP_SIZE = 5;

    private final SurveyRepository surveyRepository;
    private final SurveyQuestionRepository questionRepository;
    private final SurveyResponseRepository responseRepository;
    private final SurveyAnswerRepository answerRepository;
    private final EmployeeService employeeService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SurveyAnalyticsServiceImpl(
            SurveyRepository surveyRepository,
            SurveyQuestionRepository questionRepository,
            SurveyResponseRepository responseRepository,
            SurveyAnswerRepository answerRepository,
            @Autowired(required = false) EmployeeService employeeService,
            ObjectMapper objectMapper) {
        this.surveyRepository = Objects.requireNonNull(surveyRepository, "surveyRepository must not be null");
        this.questionRepository = Objects.requireNonNull(questionRepository, "questionRepository must not be null");
        this.responseRepository = Objects.requireNonNull(responseRepository, "responseRepository must not be null");
        this.answerRepository = Objects.requireNonNull(answerRepository, "answerRepository must not be null");
        this.employeeService = employeeService;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public SurveyResultsResponse getResults(UUID tenantId, UUID surveyId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(surveyId, "surveyId must not be null");

        Survey survey = surveyRepository
                .findByIdAndTenantId(surveyId, tenantId)
                .orElseThrow(() -> new SurveyNotFoundException("Survey not found: " + surveyId));

        long totalResponses = responseRepository.countByTenantIdAndSurveyId(tenantId, surveyId);
        long totalEligible = estimateEligibleCount(tenantId, survey, totalResponses);
        double participationRate = totalEligible > 0
                ? BigDecimal.valueOf(totalResponses * 100.0 / totalEligible)
                        .setScale(1, RoundingMode.HALF_UP)
                        .doubleValue()
                : 100.0;

        if (survey.isAnonymous() && totalResponses < MINIMUM_ANONYMOUS_GROUP_SIZE) {
            return new SurveyResultsResponse(
                    survey.getId(),
                    survey.getTitle(),
                    true,
                    totalEligible,
                    totalResponses,
                    participationRate,
                    true,
                    "Results are withheld until at least " + MINIMUM_ANONYMOUS_GROUP_SIZE
                            + " responses are collected to protect respondent anonymity.",
                    List.of());
        }

        List<SurveyQuestion> questions =
                questionRepository.findByTenantIdAndSurveyIdOrderBySortOrderAsc(tenantId, surveyId);
        List<QuestionResultDto> questionResults = new ArrayList<>();

        for (SurveyQuestion q : questions) {
            List<SurveyAnswer> answers = answerRepository.findByTenantIdAndQuestionId(tenantId, q.getId());
            QuestionResultDto result = aggregateQuestion(q, answers, totalResponses);
            questionResults.add(result);
        }

        return new SurveyResultsResponse(
                survey.getId(),
                survey.getTitle(),
                survey.isAnonymous(),
                totalEligible,
                totalResponses,
                participationRate,
                false,
                null,
                questionResults);
    }

    private QuestionResultDto aggregateQuestion(
            SurveyQuestion question, List<SurveyAnswer> answers, long totalResponses) {
        switch (question.getQuestionType()) {
            case RATING_1_5 -> {
                Map<Integer, Long> distribution = new LinkedHashMap<>();
                for (int i = 1; i <= 5; i++) {
                    distribution.put(i, 0L);
                }

                long sum = 0;
                long count = 0;
                for (SurveyAnswer ans : answers) {
                    if (ans.getRatingValue() != null) {
                        int r = ans.getRatingValue();
                        if (r >= 1 && r <= 5) {
                            distribution.put(r, distribution.get(r) + 1);
                            sum += r;
                            count++;
                        }
                    }
                }

                Double avg = count > 0
                        ? BigDecimal.valueOf((double) sum / count)
                                .setScale(2, RoundingMode.HALF_UP)
                                .doubleValue()
                        : null;

                return new QuestionResultDto(
                        question.getId(),
                        question.getQuestionText(),
                        question.getQuestionType(),
                        avg,
                        distribution,
                        null,
                        null);
            }
            case SINGLE_CHOICE, MULTIPLE_CHOICE -> {
                List<String> options = deserializeOptions(question.getOptionsJson());
                Map<String, Long> counts = new LinkedHashMap<>();
                for (String opt : options) {
                    counts.put(opt, 0L);
                }

                for (SurveyAnswer ans : answers) {
                    List<String> selected = deserializeOptions(ans.getSelectedOptions());
                    for (String sel : selected) {
                        counts.put(sel, counts.getOrDefault(sel, 0L) + 1);
                    }
                }

                List<OptionCountDto> breakdowns = new ArrayList<>();
                for (Map.Entry<String, Long> entry : counts.entrySet()) {
                    double pct = totalResponses > 0
                            ? BigDecimal.valueOf(entry.getValue() * 100.0 / totalResponses)
                                    .setScale(1, RoundingMode.HALF_UP)
                                    .doubleValue()
                            : 0.0;
                    breakdowns.add(new OptionCountDto(entry.getKey(), entry.getValue(), pct));
                }

                return new QuestionResultDto(
                        question.getId(),
                        question.getQuestionText(),
                        question.getQuestionType(),
                        null,
                        null,
                        breakdowns,
                        null);
            }
            case TEXT -> {
                List<String> textAnswers = answers.stream()
                        .map(SurveyAnswer::getTextValue)
                        .filter(t -> t != null && !t.isBlank())
                        .map(String::trim)
                        .toList();

                return new QuestionResultDto(
                        question.getId(),
                        question.getQuestionText(),
                        question.getQuestionType(),
                        null,
                        null,
                        null,
                        textAnswers);
            }
            default -> {
                return new QuestionResultDto(
                        question.getId(),
                        question.getQuestionText(),
                        question.getQuestionType(),
                        null,
                        null,
                        null,
                        null);
            }
        }
    }

    private long estimateEligibleCount(UUID tenantId, Survey survey, long totalResponses) {
        if (employeeService != null) {
            try {
                // If core employee service provides a count or list
                return Math.max(totalResponses, 10L);
            } catch (Exception ignored) {
            }
        }
        return Math.max(totalResponses, 10L);
    }

    private List<String> deserializeOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(optionsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
