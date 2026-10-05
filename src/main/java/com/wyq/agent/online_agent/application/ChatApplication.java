package com.wyq.agent.online_agent.application;

import com.wyq.agent.online_agent.domain.model.dto.ChatReq;
import com.wyq.agent.online_agent.domain.model.dto.ChatResp;
import org.antlr.v4.runtime.misc.Pair;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.Mapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Objects;

import static com.wyq.agent.online_agent.enums.BizError.DEFAULT_ERROR;

/**
 * 和大模型对话的application
 */
@RestController
@RequestMapping("/agent")
public class ChatApplication {

    @PostMapping(value="/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResp> chat(ChatReq req){
        ChatResp resp = new ChatResp();
        /// 找到对话的流程
        /// 判诗句
        /// 打包返回数据
        return Flux.just(resp);
    }
    public Pair<Long, String> check(ChatReq req) {
        if (Objects.isNull(req)) {
            return new Pair<>(DEFAULT_ERROR.getCode(), DEFAULT_ERROR.getMessage());
        }
        return new Pair<>(0L, "SUCCESS");
    }
}

