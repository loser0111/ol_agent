package com.wyq.agent.online_agent.domain.model.context;

import com.wyq.agent.online_agent.domain.model.dto.ChatReq;
import com.wyq.agent.online_agent.domain.model.dto.ChatResp;
import com.wyq.agent.online_agent.domain.model.messages.BizMessage;
import com.wyq.agent.online_agent.domain.model.model.Model;
import com.wyq.agent.online_agent.domain.model.session.Session;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Sinks;

import java.util.Collection;
import java.util.List;

@Getter
@Setter
public class ChatContext {

    // 会话请求
    ChatReq chatReq;

    // 会话相应
    ChatResp chatResp;

    // 系统提示词
    String systemPrompt;

    // sessionId
    String sessionId;

    // 会话的session
    Session session;

    // 本次会话的模型
    Model model;

    // 本次会话的message
    List<BizMessage> messages;

    // 本次会话可以使用的tools
    List<ToolCallback> tools;

    // 是否已经结束本次的会话了
    Boolean isStop;

    // 结束会话的原因
    Error error;

    // ① 创建发射器（unicast：只允许一个订阅者 = 一个前端连接）
    Sinks.Many<ChatResp> sink;

    // 当前和大模型之间的交互轮次
    int currentTurns;

    /**
     * 追加Message
     */

    public synchronized void AddMessage(BizMessage message) {
        messages.add(message);
    }

    public synchronized void resetMessages(List<BizMessage> messages) {
        this.messages = messages;
    }
}
