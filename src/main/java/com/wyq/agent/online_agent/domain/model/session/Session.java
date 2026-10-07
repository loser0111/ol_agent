package com.wyq.agent.online_agent.domain.model.session;

import com.wyq.agent.online_agent.domain.model.model.Model;
import com.wyq.agent.online_agent.enums.SessionAccessControl;
import com.wyq.agent.online_agent.enums.SessionStatus;
import com.wyq.agent.online_agent.enums.SessionType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Session {
    // 最终需要绑定一个background, 否则模型并不知道当前需要处理的业务对象是哪些。
    // 会话Id
    String sessionId;
    // 会话名称
    String sessionName;
    // 会话模型
    Model model;
    // uId
    String uId;
    // 会话的权限控制
    SessionAccessControl accessControl;
    // 这两个不属于session的范畴, 而是属于内容的范畴
    // TODO skills 技能配置
    // todo tools 工具配置
    // message会话列表不再接入到模型当中
    // 状态会入库，让对象从哪里开始管理
    SessionStatus sessionStatus;
    // 会话类型
    SessionType sessionType;
    // 创建时间（epoch 毫秒，只读展示用，来自 t_session.create_time）
    Long createTime;
    // 更新时间（epoch 毫秒，只读展示用，来自 t_session.update_time）
    Long updateTime;
}
