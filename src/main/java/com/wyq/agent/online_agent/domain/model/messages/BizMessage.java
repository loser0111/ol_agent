package com.wyq.agent.online_agent.domain.model.messages;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.content.Media;

import java.util.List;
import java.util.Map;

/**
 * 和模型会话的message
 */
@Getter
@Builder
public class BizMessage implements Message {
    // 这里包装了所有的子类。这里的设置是否可以优化呢
    private final MessageType type;
    private final String content;
    private final Map<String, Object> metadata;
    private final List<AssistantMessage.ToolCall> toolCalls;
    protected final List<ToolResponseMessage.ToolResponse> responses;
    protected final List<Media> media;

    public BizMessage(MessageType type, String content, Map<String, Object> metadata, List<AssistantMessage.ToolCall> toolCallList, List<ToolResponseMessage.ToolResponse> responses, List<Media> media) {
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

    public static BizMessage makeUserMessage(String content) {
        return BizMessage.builder()
                .type(MessageType.USER)
                .content(content)
                .build();
    }

    public static BizMessage makeSystemMessage(String prompt) {
        return BizMessage.builder()
                .type(MessageType.SYSTEM)
                .content(prompt)
                .build();
    }

    public static BizMessage makeAssistantMessage(List<AssistantMessage.ToolCall> toolCalls, List<Media> media, Map<String, Object> metadata) {
        return BizMessage.builder()
                .type(MessageType.ASSISTANT)
                .toolCalls(toolCalls)
                .media(media)
                .metadata(metadata)
                .build();
    }

    public static BizMessage makeToolResponseMessage(List<ToolResponseMessage.ToolResponse> responses, Map<String, Object> metadata) {
        return BizMessage.builder()
                .type(MessageType.TOOL)
                .responses(responses)
                .metadata(metadata)
                .build();
    }
}