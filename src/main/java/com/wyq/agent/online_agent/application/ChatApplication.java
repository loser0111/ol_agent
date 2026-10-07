package com.wyq.agent.online_agent.application;

import com.wyq.agent.online_agent.domain.model.agent.Agent;
import com.wyq.agent.online_agent.domain.model.dto.BaseResp;
import com.wyq.agent.online_agent.domain.model.dto.ChatReq;
import com.wyq.agent.online_agent.domain.model.dto.ChatResp;
import com.wyq.agent.online_agent.domain.service.agent.AgentService;
import com.wyq.agent.online_agent.domain.service.tool.FileService;
import com.wyq.agent.online_agent.enums.BizError;
import org.antlr.v4.runtime.misc.Pair;
import org.apache.logging.log4j.util.Strings;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.Mapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static com.wyq.agent.online_agent.enums.BizError.*;

/**
 * 和大模型对话的application
 */
@RestController
@RequestMapping("/agent")
public class ChatApplication {

    @Autowired
    private AgentService agentService;

    @Autowired
    private FileService fileService;

    @PostMapping(value="/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ChatResp> chat(@RequestBody ChatReq req) throws IOException {

        System.out.println(req.toString());
        ChatResp resp = new ChatResp();

        BizError checkError = check(req);
        if (Objects.nonNull(checkError)) {
            resp.setBaseResp(BaseResp.builder().code(checkError.getCode()).message(checkError.getMessage()).build());
            return Flux.just(resp);
        }

        // 获取系统提示词
        String prompt = agentService.coordinatorSystemPrompt();
        // TODO 补充本次会话工具和skills信息
        // TODO 补充本次会话的memory信息
        // 生成代理会话的agent
        Agent coordinator = agentService.constructOneCoordinator(prompt, req.getModelName(),
                Arrays.asList(ToolCallbacks.from(fileService)),
                50, 100);
        // 大模型会话
        try{
            resp = agentService.chat(coordinator, req);
        } catch (BizError bizError) {
            resp.setBaseResp(BaseResp.builder().code(bizError.getCode()).message(bizError.getMessage()).build());
        } catch (RuntimeException error) {
            resp.setBaseResp(BaseResp.builder().code(DEFAULT_ERROR.getCode()).message(error.getMessage()).build());
        }

        return Flux.just(resp);
    }
    public BizError check(ChatReq req) {
        if (Objects.isNull(req)) {
            return DEFAULT_ERROR;
        }
        if (Strings.isBlank(req.getSessionId())) {
            return INVALID_SESSION_INFO;
        }
        if (Strings.isBlank(req.getUId())) {
            return INVALID_USER_INFO;
        }
        return null;
    }
}

