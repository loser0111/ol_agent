package com.wyq.agent.online_agent.domain.service.chat;

import com.wyq.agent.online_agent.domain.model.dto.ChatReq;
import com.wyq.agent.online_agent.domain.model.context.ChatContext;
import com.wyq.agent.online_agent.domain.model.session.Session;
import com.wyq.agent.online_agent.domain.service.message.repo.MessageRepo;
import com.wyq.agent.online_agent.domain.service.session.repo.SessionRepo;
import org.apache.logging.log4j.util.Strings;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.stereotype.Service;
import org.springframework.validation.ObjectError;

import java.util.Objects;

@Service
public class GenerateChatContext {

    private final SessionRepo sessionRepo;
    private final MessageRepo messageRepo;

    public GenerateChatContext(SessionRepo sessionRepo, MessageRepo messageRepo) {
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
    }

    /**
     * 构建会话需要用到的参数，包括工具列表，系统提示词，message等信息
     * @param req
     * @return
     */
//    public ChatContext generateChatContext(ChatReq req) {
//        ChatContext context = new ChatContext();
//        Session session = null;
//
//        session = sessionRepo.createSession();
//        // 查询会话
//        context.setSession(session);
//        context.setModel(session.getModel());
//        // 会话历史
//        context.setMessages(messageRepo.findBySessionId(session.getSessionId()));
//        return context;
//    }
}
