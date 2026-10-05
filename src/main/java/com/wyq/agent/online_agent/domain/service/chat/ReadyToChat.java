package com.wyq.agent.online_agent.domain.service.chat;

import com.wyq.agent.online_agent.domain.model.context.ChatContext;
import org.springframework.stereotype.Component;

/**
 * 当前节点的功能：
 * 1. 准备系统提示词语
 * 2. 准备历史请求的messages
 * 3. 加载需要的tools
 * 4. 加载所搜的skills
 */
@Component
public class ReadyToChat implements ChatHandler{
    @Override
    public String Name() {
        return "ReadyToChat";
    }

    @Override
    public void Handle(ChatContext context) {
        return;
    }
}
