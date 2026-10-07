package com.wyq.agent.online_agent.domain.service.message;

import com.wyq.agent.online_agent.domain.model.messages.BizMessage;
import com.wyq.agent.online_agent.domain.service.message.repo.MessageRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class MessageService {

    @Autowired
    MessageRepo messageRepo;

    public void AddMessage(BizMessage message) {
        message.setMessageId(UUID.randomUUID().toString().replace("-",""));
        messageRepo.createMessage(message);
    }

    /**
     * 查询某个会话的消息列表（分页，时间正序）
     *
     * @param sessionId 会话ID
     * @param offset    偏移量（从 0 开始）
     * @param limit     每页条数
     * @return 消息列表；无数据返回空列表
     */
    public List<BizMessage> listMessages(String sessionId, int offset, int limit) {
        return messageRepo.findBySessionId(sessionId, offset, limit);
    }

    /**
     * 查询某个会话的消息总数（分页用）
     */
    public long countMessages(String sessionId) {
        return messageRepo.countBySessionId(sessionId);
    }
}
