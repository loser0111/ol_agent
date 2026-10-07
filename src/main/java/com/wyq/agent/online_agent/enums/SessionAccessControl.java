package com.wyq.agent.online_agent.enums;

import lombok.Getter;

@Getter
public enum SessionAccessControl {
    ALWAYS_ASK(1), // 总是询问
    ALWAYS_ALLOW(2), // 总是允许
    ASK_AS_NEEDED(3) // 按需访问
    ;

    SessionAccessControl(int code) {
        this.code = code;
    }

    public final int code;

    public static SessionAccessControl findByCode(Integer code) {
        for (SessionAccessControl value : SessionAccessControl.values()) {
            if(value.code == code) {
                return value;
            }
        }
        return null;
    }
}
