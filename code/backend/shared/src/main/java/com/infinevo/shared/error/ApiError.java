package com.infinevo.shared.error;

/**
 * One error code vocabulary for the whole platform.
 *
 * <p>Clients branch on {@link #code()}, never on the message. Messages are for humans and
 * may be reworded or translated; codes are contract.
 */
public enum ApiError {
    VALIDATION_FAILED("The request was not valid"),
    NOT_FOUND("The requested resource does not exist"),
    CONFLICT("The request conflicts with the current state"),

    UNAUTHENTICATED("Authentication is required"),
    FORBIDDEN("Not permitted"),
    TENANT_NOT_BOUND("No tenant is bound to this request"),
    MODULE_NOT_ENTITLED("This tenant does not hold the module required"),

    INTERNAL("Something went wrong");

    private final String defaultMessage;

    ApiError(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return name();
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
