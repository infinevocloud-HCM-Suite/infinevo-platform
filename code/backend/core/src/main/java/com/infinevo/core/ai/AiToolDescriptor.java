package com.infinevo.core.ai;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * What the model is told about a tool (W-114.3): its name, what it does and its input schema. It carries no way to run
 * the tool, so the only path to {@link AiTool#execute} is {@link AiToolRegistry#execute}, which checks the caller.
 */
public record AiToolDescriptor(String name, String description, JsonNode inputSchema) {

    static AiToolDescriptor of(AiTool tool) {
        return new AiToolDescriptor(tool.name(), tool.description(), tool.inputSchema());
    }
}
