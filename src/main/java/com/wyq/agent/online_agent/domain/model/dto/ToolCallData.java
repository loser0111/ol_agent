package com.wyq.agent.online_agent.domain.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ToolCallData {
    String toolName;
    String arguments;

    public ToolCallData(String toolName, String arguments) {
        this.toolName = toolName;
        this.arguments = arguments;
    }
}
