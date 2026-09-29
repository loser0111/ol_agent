package com.wyq.agent.online_agent.enums;

public enum AgentType {
    // 协调者
    COORDINATOR(1),
    // 子agent
    WORKER(2),
    ;
    AgentType(int code) {
        this.code = code;
    }
    final int code;
}
