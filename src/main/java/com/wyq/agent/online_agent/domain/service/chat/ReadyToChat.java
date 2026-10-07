package com.wyq.agent.online_agent.domain.service.chat;

import com.wyq.agent.online_agent.domain.model.context.ChatContext;
import com.wyq.agent.online_agent.domain.model.messages.BizMessage;
import com.wyq.agent.online_agent.domain.service.message.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
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
public class ReadyToChat implements ChatHandler {

    @Autowired
    MessageService messageService;

    @Override
    public String Name() {
        return "ReadyToChat";
    }

    @Override
    public void Handle(ChatContext context) {
        // 证明这是第一次进行会话，那么此时进行模型的拆解
        if (CollectionUtils.isEmpty(context.getMessages())) {
            BizMessage systemMessage = BizMessage.makeSystemMessage(context.getSessionId(), context.getSystemPrompt());
            BizMessage userMessage = BizMessage.makeUserMessage(context.getSessionId(), context.getChatReq().getContent());
            context.setMessages(new ArrayList<>(List.of(systemMessage, userMessage)));
            messageService.AddMessage(systemMessage);
            messageService.AddMessage(userMessage);
        } else {
            // 非第一次会话那么添加本次会话的内容给到大模型
            BizMessage userMessage = BizMessage.makeUserMessage(context.getSessionId(), context.getChatReq().getContent());
            context.getMessages().add(userMessage);
            messageService.AddMessage(userMessage);
        }
    }
}
