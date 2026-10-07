package com.wyq.agent.online_agent.application;

import com.wyq.agent.online_agent.domain.model.agent.Agent;
import com.wyq.agent.online_agent.domain.model.dto.BaseResp;
import com.wyq.agent.online_agent.domain.model.dto.ChatReq;
import com.wyq.agent.online_agent.domain.model.dto.ChatResp;
import com.wyq.agent.online_agent.domain.service.agent.AgentService;
import com.wyq.agent.online_agent.domain.service.tool.FileService;
import com.wyq.agent.online_agent.domain.service.tool.TruncatingToolCallback;
import com.wyq.agent.online_agent.enums.BizError;
import com.wyq.agent.online_agent.enums.RespType;
import org.apache.logging.log4j.util.Strings;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
        // 文件工具挂载：外层包 TruncatingToolCallback，限制单次工具结果大小，防止上下文被大文件撑爆
        ToolCallback[] fileCallbacks = ToolCallbacks.from(fileService);
        List<ToolCallback> wrappedFileTools = Arrays.stream(fileCallbacks)
                .map(TruncatingToolCallback::new)
                .collect(Collectors.toList());
        // 生成代理会话的agent（constructOneCoordinator 内部创建推流 sink）
        Agent coordinator = agentService.constructOneCoordinator(prompt, req.getModelName(),
                wrappedFileTools,
                50, 100);
        Sinks.Many<ChatResp> sink = coordinator.getSink();

        // 异步执行阻塞式对话：ChatWithModel 会把 TOKEN/TOOL_CALL/TOOL_RESPONSE/ERROR
        // 实时推入 sink 并流向客户端；对话结束后补推最终事件（DONE/ERROR）并 complete 关闭流。
        // 注意：返回 sink.asFlux() 立即响应，中间事件无需等待整个对话完成。
        Mono.fromCallable(() -> agentService.chat(coordinator, req))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnNext(sink::tryEmitNext)          // 最终事件：正常 DONE / 已封装 ERROR
                .doOnSuccess(v -> sink.tryEmitComplete())  // 成功结束 → 关闭 SSE 流（Mono 无 doOnComplete）
                .doOnError(err -> {
                    // 兜底：对话链路抛出未预期异常时，也推一个错误事件并结束
                    sink.tryEmitNext(ChatResp.builder()
                            .chatId(req.getSessionId())
                            .type(RespType.ERROR)
                            .data(new BaseResp(DEFAULT_ERROR.getCode(),
                                    err.getMessage() == null ? err.toString() : err.getMessage()))
                            .timestamp(System.currentTimeMillis())
                            .build());
                    sink.tryEmitComplete();
                })
                .subscribe();

        return sink.asFlux();
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

