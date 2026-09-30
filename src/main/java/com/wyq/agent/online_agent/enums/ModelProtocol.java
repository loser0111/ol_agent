package com.wyq.agent.online_agent.enums;

/**
 * 模型通讯协议
 */
public enum ModelProtocol {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    ;
    ModelProtocol(String protocol) {
        this.protocol = protocol;
    }
    final String protocol;
}
