package com.wyq.agent.online_agent.domain.model.messages;

import kotlin.collections.MapsKt;
import lombok.*;
import org.apache.ibatis.util.MapUtil;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.content.Media;

import java.util.List;
import java.util.Map;

/**
 * 和模型会话的message
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
public class BizMessage implements Message {
    // 这里包装了所有的子类。这里的设置是否可以优化呢
    private String messageId;
    private String sessionId;
    private MessageType type;
    private String content;
    private Map<String, Object> metadata;
    private List<AssistantMessage.ToolCall> toolCalls;
    protected List<ToolResponseMessage.ToolResponse> responses;
    protected List<Media> media;

    public BizMessage(String messageId, String sessionId, MessageType type, String content, Map<String, Object> metadata, List<AssistantMessage.ToolCall> toolCallList, List<ToolResponseMessage.ToolResponse> responses, List<Media> media) {
        this.messageId = messageId;
        this.sessionId = sessionId;
        this.type = type;
        this.content = content;
        this.metadata = metadata;
        this.toolCalls = toolCallList;
        this.responses = responses;
        this.media = media;
    }

    @Override
    public MessageType getMessageType() { return type; }

    @Override
    public @Nullable String getText() {
        return content;
    }

    @Override public Map<String, Object> getMetadata() { return metadata; }

    public static BizMessage makeUserMessage(String sessionId,String content) {
        return BizMessage.builder()
                .type(MessageType.USER)
                .sessionId(sessionId)
                .content(content)
                .build();
    }

    public static BizMessage makeSystemMessage(String sessionId,String prompt) {
        return BizMessage.builder()
                .type(MessageType.SYSTEM)
                .sessionId(sessionId)
                .content(prompt)
                .build();
    }

    public static BizMessage makeAssistantMessage(String sessionId, List<AssistantMessage.ToolCall> toolCalls, List<Media> media, String answer, Map<String, Object> metadata) {
        return BizMessage.builder()
                .type(MessageType.ASSISTANT)
                .sessionId(sessionId)
                .toolCalls(toolCalls)
                .media(media)
                .content(answer)
                .metadata(metadata)
                .build();
    }

    public static BizMessage makeToolResponseMessage(String sessionId, List<ToolResponseMessage.ToolResponse> responses, Map<String, Object> metadata) {
        return BizMessage.builder()
                .type(MessageType.TOOL)
                .sessionId(sessionId)
                .responses(responses)
                .metadata(metadata)
                .build();
    }


    public static Message toSpringAiMessage(BizMessage m) {
        return switch (m.getType()) {
            case USER -> UserMessage.builder()
                    .text(m.getContent())
                    .metadata(m.getMetadata() == null ? Map.of() : m.getMetadata())
                    .build();
            case SYSTEM -> SystemMessage.builder()
                    .text(m.getContent())
                    .metadata(m.getMetadata() == null ? Map.of() : m.getMetadata())
                    .build();

            case ASSISTANT -> AssistantMessage.builder()
                    .content(m.getContent())                                    // ★ text → content
                    .toolCalls(m.getToolCalls() == null ? List.of() : m.getToolCalls())
                    .properties(m.getMetadata() == null ? Map.of() : m.getMetadata())   // ★ metadata → properties
                    .build();

            case TOOL -> ToolResponseMessage.builder()                       // ★ protected 构造不可用，改 builder
                    .responses(m.getResponses() == null ? List.of() : m.getResponses())
                    .metadata(m.getMetadata() == null ? Map.of() : m.getMetadata())
                    .build();
            default -> new UserMessage(m.getContent() == null ? "" : m.getContent());
        };
    }
}