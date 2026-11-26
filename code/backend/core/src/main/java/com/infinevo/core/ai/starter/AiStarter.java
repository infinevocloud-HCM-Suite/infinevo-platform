package com.infinevo.core.ai.starter;

import java.util.List;

/**
 * One suggested question or task for an empty chat (W-114.22, rule 1).
 *
 * @param id stable id, unique in the registry
 * @param label what the chip shows, at most 40 characters
 * @param prompt what is sent when the chip is clicked
 * @param kind a question, or a task the helper does after Confirm
 * @param toolName the tool that answers it; the starter exists for a caller only when that tool is offered to them
 * @param screenKey a menu key that must also be in the caller's menu, or {@code null}
 * @param roles the role codes it is written for; they only order the list
 */
public record AiStarter(
        String id, String label, String prompt, Kind kind, String toolName, String screenKey, List<String> roles) {

    public enum Kind {
        QUESTION,
        TASK
    }

    public AiStarter {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
