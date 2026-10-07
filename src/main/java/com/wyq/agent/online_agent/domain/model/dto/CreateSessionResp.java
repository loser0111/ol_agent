package com.wyq.agent.online_agent.domain.model.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSessionResp {
    String sessionId;
    BaseResp baseResp;
}
