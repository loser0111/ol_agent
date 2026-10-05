package com.wyq.agent.online_agent.domain.service.chat;

import com.wyq.agent.online_agent.domain.model.context.ChatContext;
import com.wyq.agent.online_agent.domain.model.dto.ChatReq;
import com.wyq.agent.online_agent.domain.model.dto.ChatResp;
import org.springframework.stereotype.Service;

/**
 * 生成一个ReactAgent对象，来全权负责本次的会话管理
 */
@Service
public class ReactAgentService {

    private final ReadyToChat readyToChat;

    private final ChatWithModel chatWithModel;

    public ReactAgentService(GenerateChatContext generateChatContext, ReadyToChat readyToChat, ChatWithModel chatWithModel) {
        this.readyToChat = readyToChat;
        this.chatWithModel = chatWithModel;
    }

    // 走chat的流程编排
    public ChatResp chat(ChatContext context) {
        // 加载历史数据
        readyToChat.Handle(context);
        // 调用大模型进行多轮对话
        chatWithModel.Handle(context);
        return context.getChatResp();
    }
}
