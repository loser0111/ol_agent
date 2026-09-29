package com.wyq.agent.online_agent.enums;

public enum SessionAccessControl {
    ALWAYS_ASK(1), // 总是询问
    ALWAYS_ALLOW(2), // 总是允许
    ASK_AS_NEEDED(3) // 按需访问
    ;

    SessionAccessControl(int code) {
        this.code = code;
    }

    public final int code;
}
