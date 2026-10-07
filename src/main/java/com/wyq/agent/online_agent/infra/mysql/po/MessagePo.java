package com.wyq.agent.online_agent.infra.mysql.po;

import lombok.Data;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.content.Media;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@Data
public class MessagePo {
    // id参数
    private Long id;
    // messageId
    private String messageId;
    // 会话类型Id
    private String sessionId;
    // message类型
    private String type;
    // 会话中的content
    private String content;
    // 会话的MetaData
    private String metadata;
    // ASSISTANT 本次调用的工具列表
    private String toolCalls;
    // TOOLS 工具调用的结果
    private String responses;
    // 多媒体类型
    private String media;
    // 创建时间
    private Timestamp createTime;
    // 是否已经删除了
    private Boolean isDelete;
    // 其他额外信息
    private String extra;
}
