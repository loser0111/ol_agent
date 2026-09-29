package com.wyq.agent.online_agent.enums;

// 工具模型模式
public enum ToolAccessControl {
    ALLOW(1),
    DENY(2),
    ASK(3),
    ;
    ToolAccessControl(int code) {
        this.code = code;
    }
    final int code;
}
