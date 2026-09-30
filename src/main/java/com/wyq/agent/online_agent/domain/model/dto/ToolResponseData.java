package com.wyq.agent.online_agent.domain.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ToolResponseData {
    String toolName;
    Object response;

    public ToolResponseData(String toolName, Object response) {
        this.toolName = toolName;
        this.response = response;
    }
}
