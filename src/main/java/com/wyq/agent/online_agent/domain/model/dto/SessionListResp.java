package com.wyq.agent.online_agent.domain.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话列表响应：GET /agent/session/list?uId=xx&page=1&pageSize=20
 *
 * <p>结构与前端 {@code SessionMeta} 对齐：
 * sessionId / sessionName(=标题) / modelName / accessControl / createdAt(时间戳)，
 * 另外补充 sessionStatus、sessionType、updatedAt。</p>
 *
 * <p>无数据时 sessions 为【空数组】（不是 null）。</p>
 */
@Getter
@Setter
public class SessionListResp {

    /** 会话元素，按更新时间倒序（最近活跃在最前） */
    private List<SessionItem> sessions = new ArrayList<>();

    /** 该 uId 下未删除会话总数（分页用） */
    private Long total = 0L;

    /** 当前页码，从 1 开始 */
    private Integer page = 1;

    /** 每页条数 */
    private Integer pageSize = 20;

    /** 统一响应码：code=0 表示成功，非 0 见 BizError */
    private BaseResp baseResp;

    /** 单个会话项 */
    @Getter
    @Setter
    public static class SessionItem {
        /** 会话唯一ID（对应前端 SessionMeta.sessionId，即“id”） */
        private String sessionId;
        /** 会话名称（对应前端标题 SessionMeta.sessionName，即“title”） */
        private String sessionName;
        /** 模型名 */
        private String modelName;
        /** 访问控制：ALWAYS_ASK / ALWAYS_ALLOW / ASK_AS_NEEDED */
        private String accessControl;
        /** 会话状态：READY_TO_TALK / CHATTING / FINISHED */
        private String sessionStatus;
        /** 会话类型：COORDINATOR / WORKER */
        private String sessionType;
        /** 创建时间（epoch 毫秒） */
        private Long createdAt;
        /** 更新时间（epoch 毫秒） */
        private Long updatedAt;
    }
}
