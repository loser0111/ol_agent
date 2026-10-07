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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

import static com.wyq.agent.online_agent.enums.BizError.*;

/**
 * 和大模型对话的application
 */
@RestController
@RequestMapping("/agent/session")
public class SessionApplication {

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
}