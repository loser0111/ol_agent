package com.wyq.agent.online_agent.application;

import com.wyq.agent.online_agent.config.ModelConfiguration;
import com.wyq.agent.online_agent.domain.model.dto.*;
import com.wyq.agent.online_agent.domain.model.model.Model;
import com.wyq.agent.online_agent.domain.model.session.Session;
import com.wyq.agent.online_agent.domain.service.session.SessionService;
import com.wyq.agent.online_agent.enums.BizError;
import com.wyq.agent.online_agent.enums.SessionAccessControl;
import com.wyq.agent.online_agent.enums.SessionType;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

import static com.wyq.agent.online_agent.enums.BizError.*;

/**
 * 和大模型对话的application
 */
@RestController
@RequestMapping("/agent/session")
public class SessionApplication {

    /** 会话列表默认每页条数 */
    private static final int DEFAULT_SESSION_PAGE_SIZE = 20;

    /** 每页条数上限，防止一次拉取过多 */
    private static final int MAX_PAGE_SIZE = 200;

    @Autowired
    SessionService sessionService;

    @Autowired
    ModelConfiguration modelConfiguration;

    @PostMapping("/create")
    public CreateSessionResp createSession(@RequestBody  CreateSessionReq req) {
        CreateSessionResp resp = new CreateSessionResp();
        // check
        BizError error = checkCreate(req);
        if (Objects.nonNull(error)) {
            resp.setBaseResp(new BaseResp(error.getCode(), error.getMessage()));
            return resp;
        }
        // 准备创建参数
        Model model = modelConfiguration.findByName(req.getModelName());
        SessionAccessControl sessionAccessControl = req.getSessionAccessControl();
        String uId = req.getUId();
        try {
            Session session = sessionService.createSession(uId, model, sessionAccessControl, SessionType.COORDINATOR);
            resp.setSessionId(session.getSessionId());
            resp.setBaseResp(BaseResp.builder().code(SUCCESS.getCode()).message(SUCCESS.getMessage()).build());
        } catch ( BizError bizError) {
            resp.setBaseResp(BaseResp.builder().code(bizError.getCode()).message(bizError.getMessage()).build());
        } catch (RuntimeException e) {
            resp.setBaseResp(BaseResp.builder().code(DEFAULT_ERROR.getCode()).message(e.getMessage()).build());
        }
        // 返回信息
        return resp;
    }

    @PostMapping("/delete")
    public DeleteSessionResp deleteSession(@RequestBody DeleteSessionReq req) {
        DeleteSessionResp resp = new DeleteSessionResp();
        // check
        BizError error = checkDelete(req);
        if (Objects.nonNull(error)) {
            resp.setBaseResp(new BaseResp(error.getCode(), error.getMessage()));
            return resp;
        }
        // 准备参数
        try{
            sessionService.deleteSession(req.getUId(), req.getSessionId());
            resp.setBaseResp(BaseResp.builder().code(SUCCESS.getCode()).message(SUCCESS.getMessage()).build());
        } catch ( BizError bizError) {
            resp.setBaseResp(BaseResp.builder().code(bizError.getCode()).message(bizError.getMessage()).build());
        } catch (RuntimeException e) {
            resp.setBaseResp(BaseResp.builder().code(DEFAULT_ERROR.getCode()).message(e.getMessage()).build());
        }
        return resp;
    }

    /**
     * 会话列表：GET /agent/session/list?uId=xx&page=1&pageSize=20
     *
     * <p>排序：按会话更新时间倒序（最近活跃在最前）。
     * 无数据时返回空数组 {@code sessions: []}，不算错误。</p>
     */
    @GetMapping("/list")
    public SessionListResp listSessions(@RequestParam(value = "uId", required = false) String uId,
                                        @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
                                        @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize) {
        SessionListResp resp = new SessionListResp();
        // check
        if (Strings.isBlank(uId)) {
            resp.setBaseResp(new BaseResp(INVALID_USER_INFO.getCode(), INVALID_USER_INFO.getMessage()));
            return resp;
        }
        // 归一化分页参数
        int safePage = normalizePage(page);
        int safePageSize = normalizePageSize(pageSize, DEFAULT_SESSION_PAGE_SIZE);
        resp.setPage(safePage);
        resp.setPageSize(safePageSize);
        try {
            long total = sessionService.countSessions(uId);
            List<Session> sessions = sessionService.listSessions(uId, (safePage - 1) * safePageSize, safePageSize);
            for (Session session : sessions) {
                resp.getSessions().add(convert2SessionItem(session));
            }
            resp.setTotal(total);
            resp.setBaseResp(BaseResp.builder().code(SUCCESS.getCode()).message(SUCCESS.getMessage()).build());
        } catch (RuntimeException e) {
            resp.setBaseResp(BaseResp.builder().code(DEFAULT_ERROR.getCode()).message(e.getMessage()).build());
        }
        return resp;
    }

    /**
     * 创建检查
     * @param req
     * @return
     */
    public BizError checkCreate(CreateSessionReq req) {
        if (Objects.isNull(req)) {
            return DEFAULT_ERROR;
        }
        if (Strings.isBlank(req.getUId())) {
            return INVALID_USER_INFO;
        }
        if(Strings.isBlank(req.getModelName()) || Objects.isNull(modelConfiguration.findByName(req.getModelName()))) {
            return INVALID_MODEL_NAME;
        }
        if (Objects.isNull(req.getSessionAccessControl())) {
            return INVALID_ACCESS_MODE;
        }
        return null;
    }
    public BizError checkDelete (DeleteSessionReq req) {
        if (Objects.isNull(req)) {
            return DEFAULT_ERROR;
        }
        if (Strings.isBlank(req.getUId())) {
            return INVALID_USER_INFO;
        }
        if (Strings.isBlank(req.getSessionId())) {
            return INVALID_SESSION_INFO;
        }
        return null;
    }

    /** 页码归一：非正数一律当第 1 页 */
    private int normalizePage(Integer page) {
        return (Objects.isNull(page) || page < 1) ? 1 : page;
    }

    /** 每页条数归一：非正数取默认值，超过上限则截断 */
    private int normalizePageSize(Integer pageSize, int defaultSize) {
        if (Objects.isNull(pageSize) || pageSize < 1) {
            return defaultSize;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /** Session 领域对象 → 会话列表项 */
    private SessionListResp.SessionItem convert2SessionItem(Session session) {
        SessionListResp.SessionItem item = new SessionListResp.SessionItem();
        item.setSessionId(session.getSessionId());
        item.setSessionName(session.getSessionName());
        item.setModelName(Objects.isNull(session.getModel()) ? null : session.getModel().getModelName());
        item.setAccessControl(Objects.isNull(session.getAccessControl()) ? null : session.getAccessControl().name());
        item.setSessionStatus(Objects.isNull(session.getSessionStatus()) ? null : session.getSessionStatus().name());
        item.setSessionType(Objects.isNull(session.getSessionType()) ? null : session.getSessionType().name());
        item.setCreatedAt(session.getCreateTime());
        item.setUpdatedAt(session.getUpdateTime());
        return item;
    }
}
