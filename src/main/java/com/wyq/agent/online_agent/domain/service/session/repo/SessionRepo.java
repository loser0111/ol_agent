package com.wyq.agent.online_agent.domain.service.session.repo;

import com.wyq.agent.online_agent.config.ModelConfiguration;
import com.wyq.agent.online_agent.domain.model.model.Model;
import com.wyq.agent.online_agent.domain.model.session.Session;
import com.wyq.agent.online_agent.domain.service.model.ModelService;
import com.wyq.agent.online_agent.enums.SessionAccessControl;
import com.wyq.agent.online_agent.enums.SessionStatus;
import com.wyq.agent.online_agent.enums.SessionType;
import com.wyq.agent.online_agent.infra.mysql.Po.SessionPo;
import com.wyq.agent.online_agent.infra.mysql.mapper.SessionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Repository
public class SessionRepo {
    @Autowired
    ModelConfiguration modelConfiguration;

    @Autowired
    SessionMapper sessionMapper;

    public Session createSession(Model model, SessionAccessControl sessionAccessControl,
                              SessionType sessionType, SessionStatus sessionStatus) {
        // 创建一个session
        Session session = new Session();
        session.setSessionType(sessionType);
        session.setSessionStatus(sessionStatus);
        session.setAccessControl(sessionAccessControl);
        session.setUId(session.getUId());
        session.setModel(model);
        session.setSessionId(UUID.randomUUID().toString().replace("-", ""));
        session.setModel(model);
        createSession(session);
        return session;
    }

    /**
     *
     * @param session
     * @return
     */
    public Session createSession(Session session) {
        sessionMapper.insert(convert2SessionPo(session));
        return session;
    }

    /**
     * 更新session
     * @param session
     * @return
     */
    public Session upodateSession(Session session) {
        sessionMapper.update(convert2SessionPo(session));
        return session;
    }


    /**
     * 查询session
     * @param sessionId
     * @return
     */
    public Session findBySessionId(String sessionId) {
        SessionPo sessionPo = sessionMapper.findBySessionId(sessionId);
        return convert2Session(sessionPo);
    }

    /**
     * 相互转化的函数
     * @param sessionPo
     * @return
     */
    public Session convert2Session(SessionPo sessionPo) {
        if (Objects.nonNull(sessionPo)) {
            Session session = new Session();
            session.setSessionName(sessionPo.getSessionName());
            session.setSessionId(sessionPo.getSessionId());
            session.setSessionStatus(SessionStatus.findByCode(sessionPo.getSessionStatus()));
            session.setSessionType(SessionType.findByCode(sessionPo.getSessionType()));
            session.setAccessControl(SessionAccessControl.findByCode(sessionPo.getAccessControl()));
            session.setUId(sessionPo.getUId());
            session.setModel(modelConfiguration.findByName(sessionPo.getModelName()));
            return session;
        }
        return null;
    }

    /**
     * 更新函数
     * @param session
     * @return
     */
    public SessionPo convert2SessionPo(Session session) {
        if (Objects.nonNull(session)) {
            SessionPo sessionPo = new SessionPo();
            sessionPo.setSessionName(session.getSessionName());
            sessionPo.setSessionId(session.getSessionId());
            sessionPo.setSessionStatus(session.getSessionStatus().getCode());
            sessionPo.setSessionType(session.getSessionType().getCode());
            sessionPo.setAccessControl(session.getAccessControl().getCode());
            sessionPo.setUId(session.getUId());
            sessionPo.setModelName(session.getModel().getModelName());
            return sessionPo;
        }
        return null;
    }
    /**
     * 删除
     * @param sessionId
     * @return
     */
    public int deleteSession(String sessionId) {
        return sessionMapper.delete(sessionId);
    }
}
