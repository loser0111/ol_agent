package com.wyq.agent.online_agent.domain.model.dto;

import com.wyq.agent.online_agent.enums.SessionAccessControl;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Service;

@Getter
@Setter
public class CreateSessionReq {
    String uId;
    String sessionName;
    String modelName;
    SessionAccessControl sessionAccessControl;
    // 本次工作的地址
    Base base;
}

