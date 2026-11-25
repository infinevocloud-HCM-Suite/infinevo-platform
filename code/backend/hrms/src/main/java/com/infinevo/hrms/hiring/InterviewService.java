package com.infinevo.hrms.hiring;

import java.util.List;
import java.util.UUID;

/**
 * Service managing interview scheduling, rounds, and evaluation scorecards (W-112).
 */
public interface InterviewService {

    InterviewResponse scheduleInterview(UUID tenantId, UUID applicationId, InterviewRequest request, String createdBy);

    InterviewResponse getInterview(UUID tenantId, UUID id);

    List<InterviewResponse> listInterviewsForApplication(UUID tenantId, UUID applicationId);

    List<InterviewResponse> listInterviewsForInterviewer(UUID tenantId, UUID interviewerId);

    List<InterviewResponse> listAllInterviews(UUID tenantId);

    InterviewFeedbackResponse submitFeedback(
            UUID tenantId, UUID interviewId, UUID interviewerId, InterviewFeedbackRequest request);

    List<InterviewFeedbackResponse> listFeedback(UUID tenantId, UUID interviewId);
}
