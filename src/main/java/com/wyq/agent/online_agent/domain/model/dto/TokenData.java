package com.wyq.agent.online_agent.domain.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TokenData {
    String content;

    public TokenData(String content) {
        this.content = content;
    }
}
