package com.wyq.agent.online_agent.domain.service.chat;

import com.wyq.agent.online_agent.domain.model.context.ChatContext;
import com.wyq.agent.online_agent.domain.model.messages.BizMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

        // 证明这是第一次进行会话，那么此时进行模型的拆解
        if (CollectionUtils.isEmpty(context.getMessages())) {
            if (CollectionUtils.isEmpty(context.getMessages())) {
                context.setMessages(new ArrayList<>(List.of(
                        BizMessage.makeSystemMessage(context.getSystemPrompt()),
                        BizMessage.makeUserMessage(context.getChatReq().getContent()))));
            }
        }
    }
}
