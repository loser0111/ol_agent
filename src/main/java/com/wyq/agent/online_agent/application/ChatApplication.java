package com.wyq.agent.online_agent.application;

import com.wyq.agent.online_agent.domain.model.agent.Agent;
import com.wyq.agent.online_agent.domain.model.dto.ChatReq;
import com.wyq.agent.online_agent.domain.model.dto.ChatResp;
import com.wyq.agent.online_agent.domain.service.agent.AgentService;
import org.antlr.v4.runtime.misc.Pair;
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
import java.util.List;
import java.util.Objects;

import static com.wyq.agent.online_agent.enums.BizError.DEFAULT_ERROR;

/**
 * 和大模型对话的application
 */
@RestController
@RequestMapping("/agent")
public class ChatApplication {

    @Autowired
    private AgentService agentService;

    private

    @PostMapping(value="/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    Flux<ChatResp> chat(@RequestBody ChatReq req) throws IOException {
        System.out.println(req.toString());
        ChatResp resp = new ChatResp();

        // 获取系统提示词
        String prompt = agentService.coordinatorSystemPrompt();

        // 生成代理会话的agent
        Agent coordinator = agentService.constructOneCoordinator(prompt, req.getModelName(), List.of(),
                50, 100);
        // 大模型会话
        resp = agentService.chat(coordinator, req);

        return Flux.just(resp);
    }
    public Pair<Long, String> check(ChatReq req) {
        if (Objects.isNull(req)) {
            return new Pair<>(DEFAULT_ERROR.getCode(), DEFAULT_ERROR.getMessage());
        }
        return new Pair<>(0L, "SUCCESS");
    }
}

