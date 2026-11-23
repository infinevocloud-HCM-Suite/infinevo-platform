package com.infinevo.core.ai;

/**
 * The bound tenant's AI helper state (W-114.1): the switch, the monthly request cap ({@code null} until set), the
 * requests counted this month, and the month as {@code YYYY-MM} in UTC.
 */
public record AiStatus(boolean enabled, Integer monthlyRequestCap, int usedThisMonth, String month) {}
