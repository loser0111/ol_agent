package com.wyq.agent.online_agent.domain.service.message.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyq.agent.online_agent.domain.model.messages.BizMessage;
import com.wyq.agent.online_agent.infra.mysql.po.MessagePo;
import com.wyq.agent.online_agent.infra.mysql.mapper.MessageMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class MessageRepo {

    @Autowired
    MessageMapper messageMapper;

    // ★ 自建实例：不依赖 Spring 自动配置的 ObjectMapper bean
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建Message
     * @param bizMessage
     * @return
     */
    public BizMessage createMessage(BizMessage bizMessage) {
        messageMapper.insert(convert2Po(bizMessage));
        return bizMessage;
    }

    /**
     * 查找Message
     * @param sessionId
     * @return
     */
    public List<BizMessage> findBySessionId(String sessionId) {
        List<BizMessage> messages = new ArrayList<>();
        for (MessagePo po : messageMapper.findBySessionId(sessionId)) {
            messages.add(convert2Message(po));
        }
        return messages;
    }

    // ===== 转换 =====

    private MessagePo convert2Po(BizMessage msg) {
        MessagePo po = new MessagePo();
        po.setMessageId(msg.getMessageId() == null ? UUID.randomUUID().toString().replace("-", "") : msg.getMessageId());
        if (msg.getSessionId() == null) {
            throw new IllegalArgumentException("保存消息必须携带 sessionId");
        }
        po.setSessionId(msg.getSessionId());
        po.setType(msg.getType() == null ? null : msg.getType().name());
        po.setContent(msg.getContent());
        po.setMetadata(toJson(msg.getMetadata()));
        po.setToolCalls(toJson(msg.getToolCalls()));
        po.setResponses(toJson(msg.getResponses()));
        po.setMedia(toJson(msg.getMedia()));
        return po;
    }

    private BizMessage convert2Message(MessagePo po) {
        return BizMessage.builder()
                .messageId(po.getMessageId())
                .sessionId(po.getSessionId())
                .type(po.getType() == null ? null : org.springframework.ai.chat.messages.MessageType.valueOf(po.getType()))
                .content(po.getContent())
                .metadata(fromJson(po.getMetadata(), new TypeReference<Map<String, Object>>() {}))
                .toolCalls(fromJson(po.getToolCalls(), new TypeReference<List<AssistantMessage.ToolCall>>() {}))
                .responses(fromJson(po.getResponses(), new TypeReference<List<ToolResponseMessage.ToolResponse>>() {}))
                .media(fromJson(po.getMedia(), new TypeReference<List<Media>>() {}))
                .build();
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;    // 序列化失败不阻塞主流程
        }
    }

    private <T> T fromJson(String json, TypeReference<T> typeRef) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            return null;    // 反序列化失败（如 JsonValue 兼容问题）不阻塞
        }
    }
}
