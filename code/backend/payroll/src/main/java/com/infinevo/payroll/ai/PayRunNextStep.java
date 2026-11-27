package com.infinevo.payroll.ai;

import com.infinevo.payroll.payrun.PayRunStatus;

/**
 * What the officer does next on a month's regular pay run (W-114.21), in words, with the action the step needs. It
 * follows the moves {@link PayRunStatus} allows and the action on each endpoint ({@code PayRunController}). The helper
 * never takes the step: there is no pay run action in it (founder, 2026-11-23).
 */
public final class PayRunNextStep {

    static final String EXECUTE = "payroll.run.execute";
    static final String APPROVE = "payroll.run.approve";
    static final String PUBLISH = "payroll.payslip.publish";

    private PayRunNextStep() {}

    /** The step, and the action it needs, or {@code null} when nobody has anything to press. */
    public record Step(String nextStep, String requiredAction) {}

    /** The step for a run in {@code status}, or for no run at all when {@code status} is {@code null}. */
    public static Step of(PayRunStatus status) {
        if (status == null) {
            return new Step("Create the run for this month on Payroll runs.", EXECUTE);
        }
        return switch (status) {
            case DRAFT -> new Step("Check the month's inputs, then lock the run.", EXECUTE);
            case LOCKED -> new Step("Compute the run.", EXECUTE);
            case COMPUTING -> new Step("The run is being computed. Wait for it to finish.", null);
            case FAILED ->
                new Step("The compute failed. Read the reason on the run, fix it, then compute again.", EXECUTE);
            case COMPUTED ->
                new Step("Review the figures. Approve the run, or compute again after a correction.", APPROVE);
            case APPROVED -> new Step("Record the payment to release the payslips.", PUBLISH);
            case PAID -> new Step("Nothing is left to do. Payslips are released.", null);
            case CANCELLED -> new Step("This run was cancelled. It cannot be reopened.", null);
        };
    }
}
