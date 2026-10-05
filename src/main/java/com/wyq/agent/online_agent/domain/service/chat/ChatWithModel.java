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
import com.wyq.agent.online_agent.domain.service.model.ModelService;
import com.wyq.agent.online_agent.enums.BizError;
import com.wyq.agent.online_agent.enums.RespType;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class ChatWithModel implements ChatHandler {

    @Autowired
    ToolCallingManager toolCallingManager;

    @Autowired
    ModelService modelService;

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
                List<Message> messages = new ArrayList<>(context.getMessages());
                Prompt prompt = new Prompt(messages, options(context.getModel()));
                // ===== ② 流式调用 + 聚合判断（单次请求：收集 token 片段 + 聚合判断） =====
                List<ChatResponse> rawResponses = new ArrayList<>();
                ChatResponse aggregated = new MessageAggregator()
                        .aggregate(
                                chatModel.stream(prompt).doOnNext(rawResponses::add),
                                ignored -> {})          // 聚合完成回调（暂时不用，留空）
                        .blockLast();                   // 聚合流最后一条 = 完整响应
                // ===== ④ 分支：有工具调用 / 无工具调用 =====
                if (aggregated.hasToolCalls()) {
                    // —— 工具调用：推事件 → 执行 → 更新消息 → 递归下一轮 ——
                    emitToolCalls(context, aggregated);
                    ToolExecutionResult result = toolCallingManager
                            .executeToolCalls(prompt, aggregated);
                    emitToolResults(context, result);
                    // 全量回写：完整历史 + 工具消息（ToolResponseMessage 走工厂转换）
                    context.setMessages(toBizMessages(result));
                } else {
                    // —— 无工具调用：把聚合前的 token 流原样推给前端 ——
                    rawResponses.stream()
                            .map(r -> r.getResult().getOutput().getText())
                            .filter(Objects::nonNull)
                            .forEach(text -> emit(context, RespType.TOKEN,
                                    new TokenData(text)));
                    // 最终回答入会话消息
                    String answer = aggregated.getResult().getOutput().getText();
                    context.getMessages().add(BizMessage.makeAssistantMessage(List.of(), List.of(), answer, Map.of()));
                    context.setIsStop(true);              // 本轮结束标记
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
    private List<BizMessage> toBizMessages(ToolExecutionResult result) {
        return result.conversationHistory().stream()
                .map(this::toBizMessage)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** 框架 Message → BizMessage（工具响应消息走工厂方法，保留 responses 结构） */
    private BizMessage toBizMessage(Message message) {
        // ① 本身就是 BizMessage（如历史里的 user/assistant）→ 原样保留
        if (message instanceof BizMessage biz) {
            return biz;
        }

        // ② 工具响应消息 → 用工厂方法（携带 responses 列表）
        if (message instanceof ToolResponseMessage toolResponse) {
            return BizMessage.makeToolResponseMessage(
                    toolResponse.getResponses(),
                    message.getMetadata());
        }

        // ③ 其他（System/User/Assistant/ToolCall）→ 通用转换
        return BizMessage.builder()
                .type(message.getMessageType())
                .content(message.getText())
                .metadata(message.getMetadata())
                .build();
    }

    /**
     * 从本次会话的 Model 配置组装选项
     * 注意：prompt 级 options 必须传全（Anthropic 2.0 不合并模型默认值），baseUrl 已在客户端配置
     */
    private OpenAiChatOptions options(Model model) {
        return OpenAiChatOptions.builder()
                .model(model.getModelName())
                .temperature(model.getTemperature() == null
                        ? 0.7 : model.getTemperature().floatValue())
                .maxTokens(model.getContextMaxLength() == null
                        ? Constant.DEFAULT_CONTENT_MAX_LENGTH : model.getContextMaxLength())
                .build();
    }
}
