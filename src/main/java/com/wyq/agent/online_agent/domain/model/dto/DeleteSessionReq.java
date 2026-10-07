package com.wyq.agent.online_agent.domain.model.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DeleteSessionReq {
    String uId;
    String sessionId;
    Base base;
}
