package com.wyq.agent.online_agent.domain.service.agent;

import com.wyq.agent.online_agent.config.ModelConfiguration;
import com.wyq.agent.online_agent.domain.model.agent.Agent;
import com.wyq.agent.online_agent.domain.model.context.ChatContext;
import com.wyq.agent.online_agent.domain.model.dto.ChatReq;
import com.wyq.agent.online_agent.domain.model.dto.ChatResp;
import com.wyq.agent.online_agent.domain.model.messages.BizMessage;
import com.wyq.agent.online_agent.domain.model.model.Model;
import com.wyq.agent.online_agent.domain.model.session.Session;
import com.wyq.agent.online_agent.domain.service.chat.ReactAgentService;
import com.wyq.agent.online_agent.domain.service.message.repo.MessageRepo;
import com.wyq.agent.online_agent.domain.service.session.repo.SessionRepo;
import com.wyq.agent.online_agent.enums.AgentType;
import com.wyq.agent.online_agent.enums.BizError;
import com.wyq.agent.online_agent.enums.SessionStatus;
import com.wyq.agent.online_agent.enums.SessionType;
import org.apache.logging.log4j.util.Strings;
import org.springframework.ai.tool.ToolCallback;
import tools.jackson.core.io.CharTypes;

import java.util.ArrayList;
import java.util.List;

public class AgentService {

    private final ModelConfiguration modelConfiguration;

    private final ReactAgentService reactAgentService;

    private final SessionRepo sessionRepo;

    private final MessageRepo messageRepo;


    public AgentService(ModelConfiguration modelConfiguration, ReactAgentService reactAgentService, SessionRepo sessionRepo, MessageRepo messageRepo) {
        this.modelConfiguration = modelConfiguration;
        this.reactAgentService = reactAgentService;
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
    }

    /**
     * 生成一个主Agent
     * @param prompt
     * @param modelName
     * @param tools
     * @param maxMessages
     * @param maxTurns
     * @return
     */
    public Agent constructOneCoordinator(String prompt, String modelName, List<ToolCallback> tools,
                                         int maxMessages, int maxTurns) {
        Agent agent = new Agent();
        agent.setAgentType(AgentType.COORDINATOR);
        agent.setName("COORDINATOR");
        agent.setModel(modelConfiguration.findByName(modelName));
        agent.setCanCreateSubAgent(true);
        agent.setMaxMessages(maxMessages);
        agent.setMaxTurns(maxTurns);
        agent.setSystemPrompt(prompt);
        return agent;
    }

    /**
     * 生成一个特定的subAgent
     * @param Name
     * @param prompt
     * @param modelName
     * @param tools
     * @param maxMessages
     * @param maxTurns
     * @return
     */
    public Agent constructWorkAgent(String Name, String prompt, String modelName, List<ToolCallback> tools,
                                    int maxMessages, int maxTurns) {
        Agent agent = new Agent();
        agent.setAgentType(AgentType.WORKER);
        agent.setName("WORKER-" + Name);
        agent.setModel(modelConfiguration.findByName(modelName));
        agent.setCanCreateSubAgent(false);
        agent.setMaxMessages(maxMessages);
        agent.setMaxTurns(maxTurns);
        agent.setSystemPrompt(prompt);
        return agent;
    }

    /**
     * 对话，这里使用后的是
     * @return
     */
    public ChatResp chat(Agent agent, ChatReq req) {
        // agent调用chatService实现内容
        ChatContext context = convert2ChatContext(agent, req);
        // 大模型对话
        return reactAgentService.chat(context);
    }

    /**
     *
     * @param agent
     * @param req
     * @return
     */
    public ChatContext convert2ChatContext(Agent agent, ChatReq req) {
        ChatContext chatContext = new ChatContext();
        chatContext.setChatReq(req);
        // 加载session
        Session session = getSession(agent, req);
        // 加载历史信息
        List<BizMessage> messages = getMessages(session.getSessionId());
        // 设置数据
        chatContext.setModel(agent.getModel());
        chatContext.setIsStop(false);
        chatContext.setSession(session);
        chatContext.setMessages(messages);
        chatContext.setCurrentTurns(0);
        chatContext.setSessionId(session.getSessionId());
        return chatContext;
    }

    /**
     * 查询Session信息
     * @param agent
     * @param chatReq
     * @return
     */
    public Session getSession(Agent agent, ChatReq chatReq) {
        if (Strings.isBlank(chatReq.getChatId())) {
            sessionRepo.createSession(agent.getModel(), chatReq.getSessionAccessControl(), SessionType.COORDINATOR, SessionStatus.READY_TO_TALK);
        }
        return sessionRepo.findBySessionId(chatReq.getChatId());
    }

    /**
     * 查询历史会话数据
     * @param sessionId
     * @return
     */
    public List<BizMessage> getMessages(String sessionId) {
        return messageRepo.findBySessionId(sessionId);
    }
}
