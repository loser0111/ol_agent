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

import java.util.List;
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

    /**
     * 查询某个用户的会话列表（分页，按更新时间倒序）
     *
     * @param uId    用户ID
     * @param offset 偏移量（从 0 开始）
     * @param limit  每页条数
     * @return 会话列表；无数据返回空列表
     */
    public List<Session> listSessions(String uId, int offset, int limit) {
        return sessionRepo.findByUId(uId, offset, limit);
    }

    /**
     * 查询某个用户的会话总数（分页用）
     */
    public long countSessions(String uId) {
        return sessionRepo.countByUId(uId);
    }
}
