package com.infinevo.payroll.form16;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Envelope for Form 16 statements matching CONVENTIONS.md §3 and W-36.4.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Form16Response(int status, String message, Form16Statement data) {

    public static Form16Response ok(String message, Form16Statement data) {
        return new Form16Response(200, message, data);
    }
}
