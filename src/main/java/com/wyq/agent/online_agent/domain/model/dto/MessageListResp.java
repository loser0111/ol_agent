package com.wyq.agent.online_agent.domain.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话消息列表响应：GET /agent/session/{sessionId}/messages?page=1&pageSize=50
 *
 * <p>结构与前端 {@code ChatMessage} 对齐：
 * id / role / content / toolCalls / toolResponses / createdAt(时间戳)。</p>
 *
 * <p>排序：按消息时间【正序】返回（老的在前，新的在后，便于前端从上到下直接渲染）。
 * 分页语义：第 1 页 = 最近的一页；即先按时间倒序取该页，再翻转为正序输出。</p>
 *
 * <p>无数据时 messages 为【空数组】（不是 null）。</p>
 */
@Getter
@Setter
public class MessageListResp {

    /** 消息元素，按时间正序 */
    private List<MessageItem> messages = new ArrayList<>();

    /** 该会话下未删除消息总数（分页用） */
    private Long total = 0L;

    /** 当前页码，从 1 开始 */
    private Integer page = 1;

    /** 每页条数 */
    private Integer pageSize = 50;

    /** 统一响应码：code=0 表示成功，非 0 见 BizError */
    private BaseResp baseResp;

    /** 单条消息 */
    @Getter
    @Setter
    public static class MessageItem {
        /** 消息唯一ID（对应前端 ChatMessage.id） */
        private String id;
        /** 角色：小写 user / assistant / tool / system（由后端 MessageType 归一） */
        private String role;
        /** 文本内容 */
        private String content;
        /** 创建时间（epoch 毫秒） */
        private Long createdAt;
        /** 助手消息本次发起的工具调用 */
        private List<ToolCallItem> toolCalls = new ArrayList<>();
        /** 工具调用的返回结果 */
        private List<ToolResponseItem> toolResponses = new ArrayList<>();
    }

    /** 工具调用项 */
    @Getter
    @Setter
    public static class ToolCallItem {
        private String id;
        private String type;
        private String name;
        private String arguments;
    }

    /** 工具返回项 */
    @Getter
    @Setter
    public static class ToolResponseItem {
        private String id;
        private String name;
        private String response;
    }
}
