package com.infinevo.core.ai.action;

import com.infinevo.core.ai.AiActionField;
import java.util.List;

/**
 * What an action card shows (W-114.8): a one-line summary and every value that will be sent. {@code link} is an app
 * path where the user can read the item before deciding (W-114.18 rule T4), or {@code null}. It reaches the card only,
 * through {@code GET /api/v1/ai/actions/{id}}: the propose tool's answer to the model never carries it, because a path
 * can hold an id.
 */
public record AiActionDescription(String summary, List<AiActionField> fields, String link) {

    public AiActionDescription {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public AiActionDescription(String summary, List<AiActionField> fields) {
        this(summary, fields, null);
    }
}
