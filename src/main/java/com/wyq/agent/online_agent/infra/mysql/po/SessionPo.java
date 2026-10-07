package com.wyq.agent.online_agent.infra.mysql.po;

import lombok.Data;

import java.sql.Timestamp;

@Data
public class SessionPo {
    // 持久化Id（不会用于业务信息）
    private Long id;
    // 会话唯一ID
    private String sessionId;
    // 会话名称
    private String sessionName;
    // 会话模型控制
    private String modelName;
    // 会话的权限控制
    private Integer accessControl;
    // 会话状态
    private Integer sessionStatus;
    // 会话类型
    private Integer sessionType;
    // Uid
    private String uId;
    // 创建时间
    private Timestamp createTime;
    // 更新时间
    private Timestamp updateTime;
    // 是否已经删除
    private Boolean isDelete;
    // 额外信息
    private String extra;
}
