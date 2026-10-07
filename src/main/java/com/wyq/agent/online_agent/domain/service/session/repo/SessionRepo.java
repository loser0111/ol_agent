package com.wyq.agent.online_agent.domain.service.session.repo;

import com.wyq.agent.online_agent.config.ModelConfiguration;
import com.wyq.agent.online_agent.domain.model.model.Model;
import com.wyq.agent.online_agent.domain.model.session.Session;
import com.wyq.agent.online_agent.enums.SessionAccessControl;
import com.wyq.agent.online_agent.enums.SessionStatus;
import com.wyq.agent.online_agent.enums.SessionType;
import com.wyq.agent.online_agent.infra.mysql.po.SessionPo;
import com.wyq.agent.online_agent.infra.mysql.mapper.SessionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
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
     * 查询某个用户的会话列表（分页）
     * @param uId    用户ID
     * @param offset 偏移量（从 0 开始）
     * @param limit  每页条数
     * @return 按更新时间倒序的会话列表
     */
    public List<Session> findByUId(String uId, int offset, int limit) {
        List<Session> sessions = new ArrayList<>();
        for (SessionPo po : sessionMapper.findByUIdPage(uId, offset, limit)) {
            sessions.add(convert2Session(po));
        }
        return sessions;
    }

    /**
     * 查询某个用户的会话总数（分页用）
     */
    public long countByUId(String uId) {
        return sessionMapper.countByUId(uId);
    }

    public int name(String sessionId, String sessionName) {
        return sessionMapper.name(sessionId, sessionName);
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
            session.setCreateTime(toEpochMilli(sessionPo.getCreateTime()));
            session.setUpdateTime(toEpochMilli(sessionPo.getUpdateTime()));
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

    /** Timestamp → epoch 毫秒（null 安全） */
    private Long toEpochMilli(Timestamp timestamp) {
        return Objects.isNull(timestamp) ? null : timestamp.getTime();
    }
}
