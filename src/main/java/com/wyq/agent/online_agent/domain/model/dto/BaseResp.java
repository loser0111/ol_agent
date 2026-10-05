package com.wyq.agent.online_agent.domain.model.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class BaseResp {
    Long code;
    String message;

     public BaseResp(Long code, String message) {
         this.code = code;
         this.message = message;
     }
}
