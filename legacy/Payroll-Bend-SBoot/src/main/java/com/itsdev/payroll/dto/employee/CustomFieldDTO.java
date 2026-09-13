package com.itsdev.payroll.dto.employee;


import com.fasterxml.jackson.annotation.JsonProperty;

public class CustomFieldDTO {
    @JsonProperty("key")
    private String fieldKey;
    @JsonProperty("value")
    private String fieldValue;

    // Getters & Setters
    public String getFieldKey() {
        return fieldKey;
    }
    public void setFieldKey(String fieldKey) {
        this.fieldKey = fieldKey;
    }

    public String getFieldValue() {
        return fieldValue;
    }
    public void setFieldValue(String fieldValue) {
        this.fieldValue = fieldValue;
    }
}

