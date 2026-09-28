package com.infinevo.core.approval;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Objects;

/**
 * An individual step specification within an approval definition JSON array (W-15.1, spec section 6).
 */
public class ApprovalStepDefinition {

    public static final int DEFAULT_ESCALATE_AFTER_DAYS = 3;

    @JsonProperty("kind")
    private ApproverKind kind;

    @JsonProperty("assignee")
    private String assignee;

    @JsonProperty("escalate_after_days")
    private Integer escalateAfterDays;

    @JsonProperty("per_item")
    private Boolean perItem;

    public ApprovalStepDefinition() {
        this.escalateAfterDays = DEFAULT_ESCALATE_AFTER_DAYS;
        this.perItem = false;
    }

    @JsonCreator
    public ApprovalStepDefinition(
            @JsonProperty("kind") ApproverKind kind,
            @JsonProperty("assignee") String assignee,
            @JsonProperty("escalate_after_days") Integer escalateAfterDays,
            @JsonProperty("per_item") Boolean perItem) {
        this.kind = kind;
        this.assignee = assignee;
        this.escalateAfterDays = escalateAfterDays != null ? escalateAfterDays : DEFAULT_ESCALATE_AFTER_DAYS;
        this.perItem = perItem != null ? perItem : false;
    }

    public ApprovalStepDefinition(ApproverKind kind, String assignee) {
        this(kind, assignee, DEFAULT_ESCALATE_AFTER_DAYS, false);
    }

    public ApprovalStepDefinition(ApproverKind kind) {
        this(kind, null, DEFAULT_ESCALATE_AFTER_DAYS, false);
    }

    public ApproverKind getKind() {
        return kind;
    }

    public void setKind(ApproverKind kind) {
        this.kind = kind;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public Integer getEscalateAfterDays() {
        return escalateAfterDays != null ? escalateAfterDays : DEFAULT_ESCALATE_AFTER_DAYS;
    }

    public void setEscalateAfterDays(Integer escalateAfterDays) {
        this.escalateAfterDays = escalateAfterDays != null ? escalateAfterDays : DEFAULT_ESCALATE_AFTER_DAYS;
    }

    public Boolean getPerItem() {
        return perItem != null ? perItem : false;
    }

    public ApproverKind kind() {
        return getKind();
    }

    public String assignee() {
        return getAssignee();
    }

    public boolean perItem() {
        return Boolean.TRUE.equals(getPerItem());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApprovalStepDefinition that = (ApprovalStepDefinition) o;
        return kind == that.kind
                && Objects.equals(assignee, that.assignee)
                && Objects.equals(getEscalateAfterDays(), that.getEscalateAfterDays())
                && Objects.equals(getPerItem(), that.getPerItem());
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, assignee, getEscalateAfterDays(), getPerItem());
    }

    @Override
    public String toString() {
        return "ApprovalStepDefinition{" + "kind="
                + kind + ", assignee='"
                + assignee + '\'' + ", escalateAfterDays="
                + getEscalateAfterDays() + ", perItem="
                + getPerItem() + '}';
    }
}
