package com.infinevo.core.ai;

import java.util.UUID;

/**
 * No chat with this id belongs to the caller (W-114.3). Another user's chat, and another tenant's, are reported the
 * same way as one that does not exist, so the answer gives away nothing. Answers {@code 404 NOT_FOUND}.
 */
public class AiSessionNotFoundException extends RuntimeException {

    public AiSessionNotFoundException(UUID id) {
        super("AI session " + id + " not found");
    }
}
