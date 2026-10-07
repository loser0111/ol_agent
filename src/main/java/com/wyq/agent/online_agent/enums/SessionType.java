package com.wyq.agent.online_agent.enums;

import lombok.Getter;

@Getter
public enum SessionType {
    // 协调者
    COORDINATOR(1),
    // 子agent
    WORKER(2),
    ;
    SessionType(int code) {
        this.code = code;
    }
    final int code;

    public static SessionType findByCode(Integer code) {
        for (SessionType value : SessionType.values()) {
            if(value.code == code) {
                return value;
            }
        }
        return null;
    }
}
