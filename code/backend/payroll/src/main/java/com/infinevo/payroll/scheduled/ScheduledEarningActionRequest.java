package com.infinevo.payroll.scheduled;

/** The optional reason sent with pause, resume or cancel (W-73.6 §4). The body may be empty. */
public record ScheduledEarningActionRequest(String reason) {}
