package com.wyq.agent.online_agent.domain.service.session;

import com.wyq.agent.online_agent.domain.model.model.Model;
import com.wyq.agent.online_agent.domain.model.session.Session;
import com.wyq.agent.online_agent.domain.service.session.repo.SessionRepo;
import com.wyq.agent.online_agent.enums.BizError;
import com.wyq.agent.online_agent.enums.SessionAccessControl;
import com.wyq.agent.online_agent.enums.SessionStatus;
import com.wyq.agent.online_agent.enums.SessionType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.ObjectError;

import java.util.Objects;
import java.util.UUID;

/**
 * 用来管理Session的信息
 */
@Service
public class SessionService {

    @Autowired
    private SessionRepo sessionRepo;

    /**
     * 创建sessionName
     * @param model
     * @param sessionAccessControl
     * @param sessionType
     * @return
     */
    public Session createSession(String uId, Model model, SessionAccessControl sessionAccessControl, SessionType sessionType) throws BizError {
        Session session = new Session();
        session.setModel(model);
        session.setUId(uId);
        session.setSessionType(sessionType);
        session.setAccessControl(sessionAccessControl);
        session.setSessionStatus(SessionStatus.READY_TO_TALK);
        // 生成一个随机的uuid
        session.setSessionId(UUID.randomUUID().toString());
        sessionRepo.createSession(session);
        return session;
    }

    /**
     * 删除session
     * @param uId
     * @param sessionId
     * @return
     */
    public int deleteSession(String uId, String sessionId) throws BizError {
        Session session = sessionRepo.findBySessionId(sessionId);
        if (Objects.isNull(session)) {
            throw BizError.INVALID_SESSION_INFO;
        }
        if (!Objects.equals(uId, session.getUId())){
            throw BizError.INVALID_USER_INFO;
        }
        return sessionRepo.deleteSession(sessionId);
    }
    /**
     * 查找sessionId
     */
    public Session findSessionBySessionId(String sessionId) throws BizError{
        return sessionRepo.findBySessionId(sessionId);
    }

    public int nameSessionBySessionId(String sessionId, String sessionName) {
        return sessionRepo.name(sessionId, sessionName);
    }
}
