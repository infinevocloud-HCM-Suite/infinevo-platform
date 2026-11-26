package com.infinevo.hrms.survey;

import java.util.List;
import java.util.UUID;

public record SurveyAnswerRequest(
        UUID questionId, Integer ratingValue, List<String> selectedOptions, String textValue) {}
