package com.infinevo.hrms.hiring;

import com.infinevo.shared.audit.Audited;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Interview feedback scorecard entity (W-112).
 */
@Entity
@Table(name = "interview_feedback", schema = "hrms")
@Audited
public class InterviewFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "interview_id", nullable = false)
    private UUID interviewId;

    @Column(name = "interviewer_id", nullable = false)
    private UUID interviewerId;

    @Column(name = "rating", nullable = false)
    private int rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation", nullable = false, length = 32)
    private ScorecardRecommendation recommendation;

    @Column(name = "strengths", columnDefinition = "TEXT")
    private String strengths;

    @Column(name = "weaknesses", columnDefinition = "TEXT")
    private String weaknesses;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    public InterviewFeedback() {}

    @PrePersist
    public void onPrePersist() {
        if (submittedAt == null) {
            submittedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getInterviewId() {
        return interviewId;
    }

    public void setInterviewId(UUID interviewId) {
        this.interviewId = interviewId;
    }

    public UUID getInterviewerId() {
        return interviewerId;
    }

    public void setInterviewerId(UUID interviewerId) {
        this.interviewerId = interviewerId;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public ScorecardRecommendation getRecommendation() {
        return recommendation;
    }

    public void setRecommendation(ScorecardRecommendation recommendation) {
        this.recommendation = recommendation;
    }

    public String getStrengths() {
        return strengths;
    }

    public void setStrengths(String strengths) {
        this.strengths = strengths;
    }

    public String getWeaknesses() {
        return weaknesses;
    }

    public void setWeaknesses(String weaknesses) {
        this.weaknesses = weaknesses;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }
}
