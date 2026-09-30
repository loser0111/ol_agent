package com.wyq.agent.online_agent.domain.service.chat;

import com.wyq.agent.online_agent.domain.model.context.ChatContext;

/**
 * 执行会话需要进行的三个阶段
 */
public interface ChatHandler {
    String Name();
    void Handle(ChatContext context);
}
