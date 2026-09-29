package com.wyq.agent.online_agent.enums;

public enum SessionStatus {
    // 可以再次发起会话
    READY_TO_TALK(1),
    // agent正在和模型对话中,当前的状态包含模型调用咨询是否需要能力
    CHATTING(2),
    // 会话已经归档，不可以再进行交谈
    FINISHED(3)
    // 询问用户的选项
    ;
    SessionStatus(int code) {
        this.code = code;
    }
    final int code;
}
