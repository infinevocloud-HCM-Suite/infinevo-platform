package com.infinevo.core.ai.action;

import com.infinevo.core.ai.AiActionField;
import java.util.List;

/** What an action card shows (W-114.8): a one-line summary and every value that will be sent. */
public record AiActionDescription(String summary, List<AiActionField> fields) {

    public AiActionDescription {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
