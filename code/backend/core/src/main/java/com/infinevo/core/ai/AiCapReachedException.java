package com.infinevo.core.ai;

/** The bound tenant has used this month's AI request cap (W-114.1). Answers {@code 429 AI_CAP_REACHED}. */
public class AiCapReachedException extends RuntimeException {

    private final int monthlyRequestCap;

    public AiCapReachedException(int monthlyRequestCap) {
        super("Your organisation has used all " + monthlyRequestCap
                + " AI helper requests for this month. The count starts again next month.");
        this.monthlyRequestCap = monthlyRequestCap;
    }

    public int monthlyRequestCap() {
        return monthlyRequestCap;
    }
}
