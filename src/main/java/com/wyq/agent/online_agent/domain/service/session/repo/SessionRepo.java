package com.wyq.agent.online_agent.domain.service.session.repo;

import com.wyq.agent.online_agent.domain.model.model.Model;
import com.wyq.agent.online_agent.domain.model.session.Session;
import com.wyq.agent.online_agent.enums.SessionAccessControl;
import com.wyq.agent.online_agent.enums.SessionStatus;
import com.wyq.agent.online_agent.enums.SessionType;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepo {
    /**
     * 根据对应的sessionId获取会话内容
     * @param sessionId
     * @return
     */
    public Session queryBySessionId(String sessionId) {
        Session session = new Session();
        session.setSessionId(sessionId);
        return session;
    }

    /**
     * 创建对应的session
     * @return
     */
    public Session createSession() {
        // 创建一个session
        Session session = new Session();
        return session;
    }

    /**
     * 这里首次
     * @param model
     * @param sessionAccessControl
     * @param sessionType
     * @param sessionStatus
     * @return
     */
    public Session createSession(Model model, SessionAccessControl sessionAccessControl,
                              SessionType sessionType, SessionStatus sessionStatus) {
        // 创建一个session
        Session session = new Session();
        session.setSessionType(sessionType);
        session.setSessionStatus(sessionStatus);
        session.setAccessControl(sessionAccessControl);
        session.setModel(model);
        return session;
    }

    /**
     * 保存session信息
     * @param session
     */
    public void saveSession(Session session) {
        // TODO 保存基础会话信息到存储介质当中
    }

    /**
     * 查询session
     * @param sessionId
     * @return
     */
    public Session findBySessionId(String sessionId) {
        Session session = new Session();
        session.setSessionId(sessionId);
        return session;
    }
}
