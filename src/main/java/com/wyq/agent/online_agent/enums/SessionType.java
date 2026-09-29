package com.wyq.agent.online_agent.enums;

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
}
