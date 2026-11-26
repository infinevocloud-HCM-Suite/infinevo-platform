package com.infinevo.hrms.survey;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QuestionResultDto(
        UUID questionId,
        String questionText,
        QuestionType questionType,
        Double averageRating,
        Map<Integer, Long> ratingDistribution,
        List<OptionCountDto> optionBreakdown,
        List<String> textAnswers) {}
