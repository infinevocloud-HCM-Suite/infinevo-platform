package com.itsdev.payroll.enumeration.payruns;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PayRunType {
    REGULAR("regular"),
    ONE_TIME_PAYOUT("one-time payout"),
    OFF_CYCLE("off cycle");

    private final String value;

    PayRunType(String value) { this.value = value; }

    @JsonValue
    public String getValue() { return value; }

    @JsonCreator
    public static PayRunType forValue(String v) {
        for (PayRunType t : values()) {
            if (t.value.equalsIgnoreCase(v)) return t;
        }
        throw new IllegalArgumentException("Unknown PayRunType: " + v);
    }
}
