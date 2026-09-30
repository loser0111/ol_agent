package com.wyq.agent.online_agent.domain.service.message.repo;

import com.wyq.agent.online_agent.domain.model.messages.BizMessage;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MessageRepo {
    /**
     * 创建一个Message
     * @param bizMessage
     * @return
     */
    public BizMessage createMessage(BizMessage bizMessage) {
        return bizMessage;
    }

    /**
     * 查询Message
     * @param sessionId
     * @return
     */
    // 直接查询空的MessageId
    public List<BizMessage> findBySessionId(String sessionId){
        // TODO 查询所有的message
        return List.of();
    }

    /**
     * 保存Message
     * @param bizMessage
     */
    public void saveMessage(BizMessage bizMessage){

    }
}
