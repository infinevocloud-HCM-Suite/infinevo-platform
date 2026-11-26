package com.infinevo.hrms.survey;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.org.DepartmentResponse;
import com.infinevo.core.org.DepartmentService;
import com.infinevo.core.org.WorkLocationResponse;
import com.infinevo.core.org.WorkLocationService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SurveyServiceImpl implements SurveyService {

    private static final String HMAC_SECRET = "infinevo-survey-token-secret-key-32b";

    private final SurveyRepository surveyRepository;
    private final SurveyQuestionRepository questionRepository;
    private final SurveyResponseRepository responseRepository;
    private final SurveyAnswerRepository answerRepository;
    private final DepartmentService departmentService;
    private final WorkLocationService workLocationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public SurveyServiceImpl(
            SurveyRepository surveyRepository,
            SurveyQuestionRepository questionRepository,
            SurveyResponseRepository responseRepository,
            SurveyAnswerRepository answerRepository,
            @Autowired(required = false) DepartmentService departmentService,
            @Autowired(required = false) WorkLocationService workLocationService,
            ObjectMapper objectMapper) {
        this(
                surveyRepository,
                questionRepository,
                responseRepository,
                answerRepository,
                departmentService,
                workLocationService,
                objectMapper,
                Clock.systemUTC());
    }

    public SurveyServiceImpl(
            SurveyRepository surveyRepository,
            SurveyQuestionRepository questionRepository,
            SurveyResponseRepository responseRepository,
            SurveyAnswerRepository answerRepository,
            DepartmentService departmentService,
            WorkLocationService workLocationService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.surveyRepository = Objects.requireNonNull(surveyRepository, "surveyRepository must not be null");
        this.questionRepository = Objects.requireNonNull(questionRepository, "questionRepository must not be null");
        this.responseRepository = Objects.requireNonNull(responseRepository, "responseRepository must not be null");
        this.answerRepository = Objects.requireNonNull(answerRepository, "answerRepository must not be null");
        this.departmentService = departmentService;
        this.workLocationService = workLocationService;
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public SurveyResponseDto createSurvey(UUID tenantId, SurveyRequest request, String createdBy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(request, "request must not be null");

        if (request.title() == null || request.title().isBlank()) {
            throw new SurveyValidationException("Survey title is required");
        }
        if (request.startDate() == null || request.endDate() == null) {
            throw new SurveyValidationException("Start date and end date are required");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new SurveyValidationException("End date cannot be before start date");
        }
        if (request.questions() == null || request.questions().isEmpty()) {
            throw new SurveyValidationException("Survey must have at least one question");
        }

        Survey survey = new Survey();
        survey.setTenantId(tenantId);
        survey.setTitle(request.title().trim());
        survey.setDescription(request.description());
        survey.setCategory(request.category() != null ? request.category() : SurveyCategory.GENERAL);
        survey.setAnonymous(request.isAnonymous() != null && request.isAnonymous());
        survey.setTargetType(request.targetType() != null ? request.targetType() : SurveyTargetType.ALL_EMPLOYEES);
        survey.setTargetId(request.targetId());
        survey.setStartDate(request.startDate());
        survey.setEndDate(request.endDate());
        survey.setStatus(SurveyStatus.DRAFT);
        survey.setCreatedBy(createdBy != null ? createdBy : "system");
        survey.setUpdatedBy(createdBy != null ? createdBy : "system");

        Survey savedSurvey = surveyRepository.save(survey);

        short order = 1;
        List<SurveyQuestionResponse> questionDtos = new ArrayList<>();
        for (SurveyQuestionRequest qReq : request.questions()) {
            if (qReq.questionText() == null || qReq.questionText().isBlank()) {
                throw new SurveyValidationException("Question text is required");
            }
            if (qReq.questionType() == null) {
                throw new SurveyValidationException("Question type is required");
            }

            SurveyQuestion question = new SurveyQuestion();
            question.setTenantId(tenantId);
            question.setSurveyId(savedSurvey.getId());
            question.setQuestionText(qReq.questionText().trim());
            question.setQuestionType(qReq.questionType());
            question.setRequired(qReq.isRequired() == null || qReq.isRequired());
            question.setSortOrder(qReq.sortOrder() != null ? qReq.sortOrder() : order++);
            question.setCreatedBy(createdBy != null ? createdBy : "system");

            if (qReq.options() != null && !qReq.options().isEmpty()) {
                try {
                    question.setOptionsJson(objectMapper.writeValueAsString(qReq.options()));
                } catch (Exception e) {
                    throw new SurveyValidationException("Failed to serialize question options");
                }
            }

            SurveyQuestion savedQuestion = questionRepository.save(question);
            questionDtos.add(mapQuestion(savedQuestion));
        }

        return mapSurvey(savedSurvey, questionDtos, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public SurveyResponseDto getSurvey(UUID tenantId, UUID surveyId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(surveyId, "surveyId must not be null");

        Survey survey = surveyRepository
                .findByIdAndTenantId(surveyId, tenantId)
                .orElseThrow(() -> new SurveyNotFoundException("Survey not found: " + surveyId));

        List<SurveyQuestion> questions =
                questionRepository.findByTenantIdAndSurveyIdOrderBySortOrderAsc(tenantId, surveyId);
        List<SurveyQuestionResponse> questionDtos =
                questions.stream().map(this::mapQuestion).toList();
        long responseCount = responseRepository.countByTenantIdAndSurveyId(tenantId, surveyId);

        return mapSurvey(survey, questionDtos, responseCount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SurveyResponseDto> listSurveys(UUID tenantId, SurveyStatus status) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");

        List<Survey> surveys = status != null
                ? surveyRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status)
                : surveyRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        return surveys.stream()
                .map(s -> {
                    long responseCount = responseRepository.countByTenantIdAndSurveyId(tenantId, s.getId());
                    return mapSurvey(s, List.of(), responseCount);
                })
                .toList();
    }

    @Override
    public SurveyResponseDto updateStatus(UUID tenantId, UUID surveyId, SurveyStatus newStatus, String updatedBy) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(surveyId, "surveyId must not be null");
        Objects.requireNonNull(newStatus, "newStatus must not be null");

        Survey survey = surveyRepository
                .findByIdAndTenantId(surveyId, tenantId)
                .orElseThrow(() -> new SurveyNotFoundException("Survey not found: " + surveyId));

        if (newStatus == SurveyStatus.PUBLISHED) {
            List<SurveyQuestion> questions =
                    questionRepository.findByTenantIdAndSurveyIdOrderBySortOrderAsc(tenantId, surveyId);
            if (questions.isEmpty()) {
                throw new SurveyValidationException("Cannot publish a survey without questions");
            }
        }

        survey.setStatus(newStatus);
        survey.setUpdatedBy(updatedBy != null ? updatedBy : "system");
        Survey saved = surveyRepository.save(survey);

        List<SurveyQuestion> questions =
                questionRepository.findByTenantIdAndSurveyIdOrderBySortOrderAsc(tenantId, surveyId);
        List<SurveyQuestionResponse> questionDtos =
                questions.stream().map(this::mapQuestion).toList();
        long responseCount = responseRepository.countByTenantIdAndSurveyId(tenantId, surveyId);

        return mapSurvey(saved, questionDtos, responseCount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveSurveyDto> listActiveSurveysForEmployee(UUID tenantId, EmployeeResponse employee) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(employee, "employee must not be null");

        LocalDate today = LocalDate.now(clock);
        List<Survey> activeSurveys = surveyRepository.findActiveSurveys(tenantId, SurveyStatus.PUBLISHED, today);

        List<ActiveSurveyDto> result = new ArrayList<>();
        for (Survey s : activeSurveys) {
            if (!isTargeted(s, employee)) {
                continue;
            }

            String token = computeSubmissionToken(tenantId, s.getId(), employee.id());
            boolean submitted =
                    responseRepository.existsByTenantIdAndSurveyIdAndSubmissionToken(tenantId, s.getId(), token);
            List<SurveyQuestion> questions =
                    questionRepository.findByTenantIdAndSurveyIdOrderBySortOrderAsc(tenantId, s.getId());

            result.add(new ActiveSurveyDto(
                    s.getId(),
                    s.getTitle(),
                    s.getDescription(),
                    s.getCategory(),
                    s.isAnonymous(),
                    s.getTargetType(),
                    s.getStartDate(),
                    s.getEndDate(),
                    questions.size(),
                    submitted));
        }

        return result;
    }

    @Override
    public UUID submitSurvey(UUID tenantId, UUID surveyId, EmployeeResponse employee, SurveySubmissionRequest request) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(surveyId, "surveyId must not be null");
        Objects.requireNonNull(employee, "employee must not be null");
        Objects.requireNonNull(request, "request must not be null");

        Survey survey = surveyRepository
                .findByIdAndTenantId(surveyId, tenantId)
                .orElseThrow(() -> new SurveyNotFoundException("Survey not found: " + surveyId));

        LocalDate today = LocalDate.now(clock);
        if (survey.getStatus() != SurveyStatus.PUBLISHED
                || today.isBefore(survey.getStartDate())
                || today.isAfter(survey.getEndDate())) {
            throw new SurveyValidationException("Survey is closed or outside active date window");
        }

        if (!isTargeted(survey, employee)) {
            throw new SurveyValidationException("Employee is not part of this survey's target audience");
        }

        String submissionToken = computeSubmissionToken(tenantId, surveyId, employee.id());
        if (responseRepository.existsByTenantIdAndSurveyIdAndSubmissionToken(tenantId, surveyId, submissionToken)) {
            throw new SurveyConflictException("Survey has already been submitted by this employee");
        }

        List<SurveyQuestion> questions =
                questionRepository.findByTenantIdAndSurveyIdOrderBySortOrderAsc(tenantId, surveyId);
        List<SurveyAnswerRequest> answerRequests = request.answers() != null ? request.answers() : List.of();

        for (SurveyQuestion q : questions) {
            SurveyAnswerRequest matchedAnswer = answerRequests.stream()
                    .filter(a -> a.questionId() != null && a.questionId().equals(q.getId()))
                    .findFirst()
                    .orElse(null);

            if (q.isRequired()) {
                if (matchedAnswer == null) {
                    throw new SurveyValidationException("Answer is required for question: " + q.getQuestionText());
                }
                validateAnswerValue(q, matchedAnswer);
            } else if (matchedAnswer != null) {
                validateAnswerValue(q, matchedAnswer);
            }
        }

        SurveyResponse response = new SurveyResponse();
        response.setTenantId(tenantId);
        response.setSurveyId(surveyId);
        response.setSubmissionToken(submissionToken);

        if (survey.isAnonymous()) {
            response.setEmployeeId(null);
            response.setSubmittedAt(today.atStartOfDay().toInstant(ZoneOffset.UTC));
        } else {
            response.setEmployeeId(employee.id());
            response.setSubmittedAt(Instant.now(clock));
        }

        SurveyResponse savedResponse = responseRepository.save(response);

        for (SurveyAnswerRequest ans : answerRequests) {
            SurveyAnswer answer = new SurveyAnswer();
            answer.setTenantId(tenantId);
            answer.setResponseId(savedResponse.getId());
            answer.setQuestionId(ans.questionId());
            answer.setRatingValue(ans.ratingValue());
            answer.setTextValue(ans.textValue());

            if (ans.selectedOptions() != null && !ans.selectedOptions().isEmpty()) {
                try {
                    answer.setSelectedOptions(objectMapper.writeValueAsString(ans.selectedOptions()));
                } catch (Exception e) {
                    throw new SurveyValidationException("Failed to serialize selected options");
                }
            }

            answerRepository.save(answer);
        }

        return savedResponse.getId();
    }

    private void validateAnswerValue(SurveyQuestion question, SurveyAnswerRequest answer) {
        switch (question.getQuestionType()) {
            case RATING_1_5 -> {
                if (answer.ratingValue() == null || answer.ratingValue() < 1 || answer.ratingValue() > 5) {
                    throw new SurveyValidationException(
                            "Rating value must be between 1 and 5 for question: " + question.getQuestionText());
                }
            }
            case SINGLE_CHOICE -> {
                if (answer.selectedOptions() == null || answer.selectedOptions().size() != 1) {
                    throw new SurveyValidationException(
                            "Exactly one option must be selected for single-choice question: "
                                    + question.getQuestionText());
                }
                List<String> validOptions = deserializeOptions(question.getOptionsJson());
                if (!validOptions.contains(answer.selectedOptions().get(0))) {
                    throw new SurveyValidationException(
                            "Selected option is not in available choices for question: " + question.getQuestionText());
                }
            }
            case MULTIPLE_CHOICE -> {
                if (answer.selectedOptions() == null || answer.selectedOptions().isEmpty()) {
                    throw new SurveyValidationException(
                            "At least one option must be selected for multiple-choice question: "
                                    + question.getQuestionText());
                }
                List<String> validOptions = deserializeOptions(question.getOptionsJson());
                for (String opt : answer.selectedOptions()) {
                    if (!validOptions.contains(opt)) {
                        throw new SurveyValidationException("Selected option is not in available choices for question: "
                                + question.getQuestionText());
                    }
                }
            }
            case TEXT -> {
                if (question.isRequired()
                        && (answer.textValue() == null || answer.textValue().isBlank())) {
                    throw new SurveyValidationException(
                            "Text feedback is required for question: " + question.getQuestionText());
                }
            }
        }
    }

    private boolean isTargeted(Survey survey, EmployeeResponse employee) {
        if (survey.getTargetType() == SurveyTargetType.ALL_EMPLOYEES) {
            return true;
        }
        if (survey.getTargetType() == SurveyTargetType.DEPARTMENT) {
            return survey.getTargetId() != null && survey.getTargetId().equals(employee.departmentId());
        }
        if (survey.getTargetType() == SurveyTargetType.WORK_LOCATION) {
            return survey.getTargetId() != null && survey.getTargetId().equals(employee.workLocationId());
        }
        return true;
    }

    public static String computeSubmissionToken(UUID tenantId, UUID surveyId, UUID employeeId) {
        String input = tenantId + ":" + surveyId + ":" + employeeId;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(HMAC_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] bytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate submission token", e);
        }
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

    private SurveyQuestionResponse mapQuestion(SurveyQuestion q) {
        return new SurveyQuestionResponse(
                q.getId(),
                q.getQuestionText(),
                q.getQuestionType(),
                deserializeOptions(q.getOptionsJson()),
                q.isRequired(),
                q.getSortOrder());
    }

    private SurveyResponseDto mapSurvey(Survey s, List<SurveyQuestionResponse> questions, long responseCount) {
        String targetName = null;
        if (s.getTargetType() == SurveyTargetType.DEPARTMENT && s.getTargetId() != null && departmentService != null) {
            try {
                DepartmentResponse dept = departmentService.get(s.getTargetId());
                if (dept != null) targetName = dept.name();
            } catch (Exception ignored) {
            }
        } else if (s.getTargetType() == SurveyTargetType.WORK_LOCATION
                && s.getTargetId() != null
                && workLocationService != null) {
            try {
                WorkLocationResponse loc = workLocationService.get(s.getTargetId());
                if (loc != null) targetName = loc.name();
            } catch (Exception ignored) {
            }
        }

        return new SurveyResponseDto(
                s.getId(),
                s.getTitle(),
                s.getDescription(),
                s.getCategory(),
                s.isAnonymous(),
                s.getTargetType(),
                s.getTargetId(),
                targetName,
                s.getStartDate(),
                s.getEndDate(),
                s.getStatus(),
                responseCount,
                questions,
                s.getCreatedAt(),
                s.getUpdatedAt());
    }
}
