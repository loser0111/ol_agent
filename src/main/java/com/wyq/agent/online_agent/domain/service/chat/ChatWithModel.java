package com.wyq.agent.online_agent.domain.service.chat;

import com.wyq.agent.online_agent.consts.Constant;
import com.wyq.agent.online_agent.domain.model.context.ChatContext;
import com.wyq.agent.online_agent.domain.model.dto.BaseResp;
import com.wyq.agent.online_agent.domain.model.dto.ChatResp;
import com.wyq.agent.online_agent.domain.model.dto.TokenData;
import com.wyq.agent.online_agent.domain.model.dto.ToolCallData;
import com.wyq.agent.online_agent.domain.model.dto.ToolResponseData;
import com.wyq.agent.online_agent.domain.model.messages.BizMessage;
import com.wyq.agent.online_agent.domain.model.model.Model;
import com.wyq.agent.online_agent.domain.service.message.MessageService;
import com.wyq.agent.online_agent.domain.service.model.ModelService;
import com.wyq.agent.online_agent.enums.BizError;
import com.wyq.agent.online_agent.enums.RespType;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 直接和大模型对话的阶段
 */
@Component
public class ChatWithModel implements ChatHandler {

    @Autowired
    ToolCallingManager toolCallingManager;

    @Autowired
    ModelService modelService;

    @Autowired
    MessageService messageService;

    @Override
    public String Name() {
        return "ChatWithModel";
    }

    @Override
    public void Handle(ChatContext context) {
        try {
            // 复用缓存的 ChatModel（Model 内部懒加载，避免递归重建 SDK 客户端）
            ChatModel chatModel = modelService.buildChatModel(context.getModel());
            // 使用循环而不是使用递归
            for (int i = 0; i < Constant.MAX_ITERATIONS; i++) {
                // ===== ① 组装 prompt（BizMessage implements Message，直接转型） =====
                List<Message> messages = context.getMessages().stream()
                        .map(BizMessage::toSpringAiMessage)
                        .collect(Collectors.toCollection(ArrayList::new));
                Prompt prompt = new Prompt(messages, options(context));
                List<ChatResponse> rawResponses = new ArrayList<>();
                AtomicReference<ChatResponse> aggregatedRef = new AtomicReference<>();
                new MessageAggregator()
                        .aggregate(
                                chatModel.stream(prompt).doOnNext(rawResponses::add),
                                aggregatedRef::set)   // ★ 完整响应在这里
                        .blockLast();
                ChatResponse aggregated = aggregatedRef.get();
                // ===== ④ 分支：有工具调用 / 无工具调用 =====
                if (aggregated.hasToolCalls()) {
                    // —— 工具调用：推事件 → 执行 → 更新消息 → 递归下一轮 ——
                    emitToolCalls(context, aggregated);
                    ToolExecutionResult result = toolCallingManager
                            .executeToolCalls(prompt, aggregated);
                    emitToolResults(context, result);
                    // ★ 只取本次新增：历史(BizMessage)跳过，新增的(spring-ai 类型)转换
                    List<BizMessage> newMsgs = result.conversationHistory().stream()
                            .filter(m -> !(m instanceof BizMessage))     // 关键过滤
                            .map(m -> this.toBizMessage(context.getSessionId(), m))
                            .collect(Collectors.toList());
                    newMsgs.forEach(context::AddMessage);
                    newMsgs.forEach(messageService::AddMessage);
                } else {
                    // —— 无工具调用：把聚合前的 token 流原样推给前端 ——
                    rawResponses.stream()
                            .map(r -> r.getResult().getOutput().getText())
                            .filter(Objects::nonNull)
                            .forEach(text -> emit(context, RespType.TOKEN,
                                    new TokenData(text)));
                    // 最终回答入会话消息
                    String answer = aggregated.getResult().getOutput().getText();
                    BizMessage message = BizMessage.makeAssistantMessage(context.getSessionId(), List.of(), List.of(), answer, Map.of());
                    context.getMessages().add(message);
                    messageService.AddMessage(message);
                    context.setIsStop(true); // 本轮结束标记
                    return;
                }
            }
            context.setIsStop(true);
            context.setError(new Error("达到最大迭代轮次: " + Constant.MAX_ITERATIONS));
            emit(context, RespType.ERROR, new BaseResp(-1L, "工具循环超限"));
        } catch (Exception e) {
            // ===== ⑤ 异常兜底：结束会话 + 推错误 =====
            context.setIsStop(true);
            context.setError(new Error(e.getMessage()));
            emit(context, RespType.ERROR, new BaseResp(-1L, e.getMessage()));
        }
        // 注意：complete() 由外层框架（Controller 的 executor finally）调用，这里不碰
    }

    // ================= 辅助方法 =================

    /** 统一推事件 */
    private void emit(ChatContext ctx, RespType type, Object data) {
        ctx.getSink().tryEmitNext(ChatResp.builder()
                .chatId(ctx.getSessionId())
                .type(type)
                .data(data)
                .timestamp(System.currentTimeMillis())
                .baseResp(new BaseResp(BizError.SUCCESS.getCode(), BizError.SUCCESS.getMessage()))
                .build());
    }

    /** 把模型发起的工具调用推给前端（TOOL_CALL） */
    private void emitToolCalls(ChatContext ctx, ChatResponse response) {
        response.getResult().getOutput().getToolCalls()
                .forEach(tc -> emit(ctx, RespType.TOOL_CALL,
                        new ToolCallData(tc.name(), tc.arguments())));
    }

    /** 把工具执行结果推给前端（TOOL_RESPONSE） */
    private void emitToolResults(ChatContext ctx, ToolExecutionResult result) {
        result.conversationHistory().stream()
                .filter(m -> m instanceof ToolResponseMessage)
                .flatMap(m -> ((ToolResponseMessage) m).getResponses().stream())
                .forEach(tr -> emit(ctx, RespType.TOOL_RESPONSE,
                        new ToolResponseData(tr.name(), tr.responseData())));
    }

    /** ToolExecutionResult 完整消息历史 → BizMessage 列表（全量替换用） */
    private List<BizMessage> toBizMessages(String sessionId, ToolExecutionResult result) {
        return result.conversationHistory().stream()
                .map(m -> this.toBizMessage(sessionId, m))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** 框架 Message → BizMessage（工具响应消息走工厂方法，保留 responses 结构） */
    private BizMessage toBizMessage(String sessionId, Message message) {
        // ① 本身就是 BizMessage（如历史里的 user/assistant）→ 原样保留
        if (message instanceof BizMessage biz) {
            return biz;
        }

        // ② 工具响应消息 → 用工厂方法（携带 responses 列表）
        if (message instanceof ToolResponseMessage toolResponse) {
            return BizMessage.makeToolResponseMessage(
                    sessionId,
                    toolResponse.getResponses(),
                    message.getMetadata());
        }

        // ③ 其他（System/User/Assistant/ToolCall）→ 通用转换
        if (message instanceof AssistantMessage am) {
            // ★ 补 toolCalls：工具调用消息要保留调用参数
            return BizMessage.builder()
                    .sessionId(sessionId)
                    .type(MessageType.ASSISTANT)
                    .content(am.getText())
                    .metadata(am.getMetadata())
                    .toolCalls(am.getToolCalls())
                    .build();
        }

        // ③ 其他（System/User/Assistant/ToolCall）→ 通用转换
        return BizMessage.builder()
                .type(message.getMessageType())
                .sessionId(sessionId)
                .content(message.getText())
                .metadata(message.getMetadata())
                .build();
    }

    /**
     * 从本次会话的 Model 配置组装选项
     * 注意：prompt 级 options 必须传全（Anthropic 2.0 不合并模型默认值），baseUrl 已在客户端配置
     */
    private OpenAiChatOptions options(ChatContext context) {
        Model model = context.getModel();
        List<ToolCallback> tools = context.getTools();
        return OpenAiChatOptions.builder()
                .model(model.getModelName())
                .baseUrl(model.getBaseUrl())
                .temperature(model.getTemperature() == null
                        ? 0.7 : model.getTemperature().floatValue())
                .maxTokens(model.getContextMaxLength() == null
                        ? Constant.DEFAULT_CONTENT_MAX_LENGTH : model.getContextMaxLength())
                .apiKey(model.getApiKey())
                .toolCallbacks(tools == null ? new ToolCallback[0]
                        : tools.toArray(new ToolCallback[0]))   // ★ 挂工具定义
                .build();
    }
}
