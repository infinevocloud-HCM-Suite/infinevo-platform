package com.infinevo.hrms.survey;

import java.util.List;

public record SurveySubmissionRequest(List<SurveyAnswerRequest> answers) {}
