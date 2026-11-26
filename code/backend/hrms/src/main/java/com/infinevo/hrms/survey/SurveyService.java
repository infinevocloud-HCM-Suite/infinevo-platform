package com.infinevo.hrms.survey;

import com.infinevo.core.employee.EmployeeResponse;
import java.util.List;
import java.util.UUID;

public interface SurveyService {

    SurveyResponseDto createSurvey(UUID tenantId, SurveyRequest request, String createdBy);

    SurveyResponseDto getSurvey(UUID tenantId, UUID surveyId);

    List<SurveyResponseDto> listSurveys(UUID tenantId, SurveyStatus status);

    SurveyResponseDto updateStatus(UUID tenantId, UUID surveyId, SurveyStatus newStatus, String updatedBy);

    List<ActiveSurveyDto> listActiveSurveysForEmployee(UUID tenantId, EmployeeResponse employee);

    UUID submitSurvey(UUID tenantId, UUID surveyId, EmployeeResponse employee, SurveySubmissionRequest request);
}
