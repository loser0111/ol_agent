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
import com.wyq.agent.online_agent.domain.service.tool.GuardedToolCallback;
import com.wyq.agent.online_agent.domain.service.tool.ToolCallGuard;
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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    @Autowired
    ContextCompactor contextCompactor;

    @Override
    public String Name() {
        return "ChatWithModel";
    }

    @Override
    public void Handle(ChatContext context) {
        try {
            // 复用缓存的 ChatModel（Model 内部懒加载，避免递归重建 SDK 客户端）
            ChatModel chatModel = modelService.buildChatModel(context.getModel());
            // 本次会话的工具守卫：预算控制 + 重复调用短路，整个会话共用一份状态（防止同参数反复读取）
            ToolCallGuard guard = new ToolCallGuard(Constant.MAX_TOOL_CALLS_PER_TURN);
            List<ToolCallback> tools = guardTools(context.getTools(), guard);
            context.setToolCallGuard(guard);
            boolean budgetWarned = false;

            // 使用循环而不是使用递归
            for (int i = 0; i < Constant.MAX_ITERATIONS; i++) {
                // ===== ① 组装 prompt：先压缩历史把体积压进预算，再转成 spring-ai 消息 =====
                // compactAnchor 是上次压缩用的边界，回传给压缩器复用（边界只在超预算时才推进）
                ContextCompactor.Result compaction = contextCompactor.compact(
                        context.getMessages(),
                        context.getMaxMessages(),
                        promptTokenBudget(context),
                        context.getCompactAnchor());
                context.setCompactAnchor(compaction.anchor());
                if (compaction.changed()) {
                    emit(context, RespType.STATUS, new BaseResp(0L,
                            "已压缩历史上下文（当前约 " + compaction.estimatedTokens() + " tokens）"));
                }
                List<Message> messages = compaction.messages().stream()
                        .filter(m -> m != null && m.getType() != null)
                        .map(BizMessage::toSpringAiMessage)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(ArrayList::new));
                Prompt prompt = new Prompt(messages, options(context, tools));
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
                    // ★ 先推文本：模型"先说一句 + 再调工具"时，这段文本不能只落库不推流
                    emitTextTokens(context, rawResponses);
                    emitToolCalls(context, aggregated);
                    ToolExecutionResult result = toolCallingManager
                            .executeToolCalls(prompt, aggregated);
                    emitToolResults(context, result);
                    // ★ 只取本次新增消息（见 pickNewMessages 注释），否则整个会话历史会被重复落库
                    List<BizMessage> newMsgs = pickNewMessages(result.conversationHistory(), messages).stream()
                            .map(m -> this.toBizMessage(context.getSessionId(), m))
                            .collect(Collectors.toList());
                    newMsgs.forEach(context::AddMessage);
                    newMsgs.forEach(messageService::AddMessage);
                    // 预算用尽时提示一次，让前端知道后面会基于已有信息作答
                    if (guard.exhausted() && !budgetWarned) {
                        budgetWarned = true;
                        emit(context, RespType.STATUS, new BaseResp(0L,
                                "工具调用已达本次会话预算上限（" + guard.budget() + " 次），后续将基于已有信息作答"));
                    }
                } else {
                    // —— 无工具调用：把聚合前的 token 流原样推给前端 ——
                    emitTextTokens(context, rawResponses);
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

    /** 给本会话的工具统一套上守卫（预算 + 重复调用短路） */
    private List<ToolCallback> guardTools(List<ToolCallback> tools, ToolCallGuard guard) {
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        return tools.stream()
                .filter(Objects::nonNull)
                .map(t -> (ToolCallback) new GuardedToolCallback(t, guard))
                .collect(Collectors.toList());
    }

    /**
     * prompt 的 token 预算：取 min(保守上限, 配置窗口/2)。
     * 不直接用 contextMaxLength 是因为配置里常填得远大于真实窗口（如 320000）。
     */
    private int promptTokenBudget(ChatContext context) {
        int budget = Constant.MAX_PROMPT_TOKENS;
        Model model = context.getModel();
        Integer configured = model == null ? null : model.getContextMaxLength();
        if (configured != null && configured > 0) {
            budget = Math.min(budget, configured / 2);
        }
        return budget;
    }

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

    /**
     * 把本轮模型输出的文本分片推给前端（TOKEN）。
     * 无论本轮是否带工具调用都要调用：模型经常"先说一句再调工具"，
     * 这段文本会随 assistant 消息落库，不推给前端就会出现
     * "DB 里有大模型回复、前端不展示"。
     */
    private void emitTextTokens(ChatContext ctx, List<ChatResponse> rawResponses) {
        rawResponses.stream()
                .map(ChatWithModel::textOf)
                .filter(text -> text != null && !text.isEmpty())
                .forEach(text -> emit(ctx, RespType.TOKEN, new TokenData(text)));
    }

    /** 取一个流式分片的文本，容忍收尾分片里 result/output 为空 */
    private static String textOf(ChatResponse response) {
        if (response == null || response.getResult() == null
                || response.getResult().getOutput() == null) {
            return null;
        }
        return response.getResult().getOutput().getText();
    }

    /**
     * 从 {@link ToolExecutionResult#conversationHistory()} 里挑出"本轮新增"的消息。
     *
     * conversationHistory = 本次 prompt 的指令 + 本轮新增（assistant 工具调用消息、tool 响应消息）。
     * 依据 Spring AI 实现 DefaultToolCallingManager#buildConversationHistoryAfterToolExecution：
     * 先 {@code new ArrayList<>(prompt.getInstructions())} 再 add 两个新消息，即历史对象是
     * <b>同一批引用</b> 被浅拷贝进来的，因此按对象身份（Identity）即可准确排除历史。
     *
     * 注意不能用「/{@code m instanceof BizMessage} 才排除」这种类型过滤：
     * 历史在组装 prompt 时已经被 {@code BizMessage::toSpringAiMessage} 转成 Spring AI 类型，
     * 该条件恒为真、等于没过滤 —— 后果是把整个会话历史重新 AddMessage 一遍：
     * context 里同一条消息出现多份、t_message 反复插入重复行、下一轮 prompt 又被放大，
     * 既污染数据也放大上下文体积。
     */
    static List<Message> pickNewMessages(List<Message> conversationHistory, List<Message> promptMessages) {
        if (conversationHistory == null || conversationHistory.isEmpty()) {
            return List.of();
        }
        Set<Message> history = Collections.newSetFromMap(new IdentityHashMap<>());
        if (promptMessages != null) {
            history.addAll(promptMessages);
        }
        return conversationHistory.stream()
                .filter(Objects::nonNull)
                .filter(m -> !history.contains(m))
                .collect(Collectors.toList());
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
    private OpenAiChatOptions options(ChatContext context, List<ToolCallback> tools) {
        Model model = context.getModel();
        return OpenAiChatOptions.builder()
                .model(model.getModelName())
                .baseUrl(model.getBaseUrl())
                .temperature(model.getTemperature() == null
                        ? 0.7 : model.getTemperature().floatValue())
                .maxTokens(model.getContextMaxLength() == null
                        ? Constant.DEFAULT_CONTENT_MAX_LENGTH : model.getContextMaxLength())
                .apiKey(model.getApiKey())
                .toolCallbacks(tools == null ? new ToolCallback[0]
                        : tools.toArray(new ToolCallback[0]))   // ★ 挂工具定义（含守卫包装）
                .build();
    }
}
