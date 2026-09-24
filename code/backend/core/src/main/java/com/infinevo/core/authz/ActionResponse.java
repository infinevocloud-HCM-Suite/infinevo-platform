package com.infinevo.core.authz;

/** One catalogue entry as {@code GET /api/v1/actions} returns it (W-11.1, spec section 4). */
public record ActionResponse(String code, String name, String module, String description) {

    public static ActionResponse from(Action action) {
        return new ActionResponse(action.getCode(), action.getName(), action.getModule(), action.getDescription());
    }
}
