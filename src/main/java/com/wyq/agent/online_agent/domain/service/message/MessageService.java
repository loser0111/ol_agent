package com.wyq.agent.online_agent.domain.service.message;

import com.wyq.agent.online_agent.domain.model.messages.BizMessage;
import com.wyq.agent.online_agent.domain.service.message.repo.MessageRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MessageService {

    @Autowired
    MessageRepo messageRepo;

    public void AddMessage(BizMessage message) {
        message.setMessageId(UUID.randomUUID().toString().replace("-",""));
        messageRepo.createMessage(message);
    }
}
