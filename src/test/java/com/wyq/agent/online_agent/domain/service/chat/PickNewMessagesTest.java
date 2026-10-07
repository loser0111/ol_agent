package com.wyq.agent.online_agent.domain.service.chat;

import com.wyq.agent.online_agent.domain.model.messages.BizMessage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归测试：工具调用轮次里"只取本轮新增消息"。
 *
 * 背景 bug：ChatWithModel 曾用 {@code filter(m -> !(m instanceof BizMessage))} 从
 * conversationHistory 里筛新增消息，但历史在组装 prompt 时已转成 Spring AI 类型，
 * 该条件恒为真 —— 整个会话历史被重新 AddMessage/落库，context 与 t_message 双重膨胀。
 */
class PickNewMessagesTest {

    @Test
    void picks_only_this_round_messages_from_conversation_history() {
        List<Message> promptMessages = promptHistory();

        // 框架行为：conversationHistory = new ArrayList<>(prompt.getInstructions()) + 本轮两条
        AssistantMessage assistant = assistantWithToolCall("call-1", "read_file", "{\"path\":\"A.java\"}");
        ToolResponseMessage toolResponse = toolResponse("call-1", "read_file", "文件内容");
        List<Message> conversationHistory = new ArrayList<>(promptMessages);
        conversationHistory.add(assistant);
        conversationHistory.add(toolResponse);

        List<Message> picked = ChatWithModel.pickNewMessages(conversationHistory, promptMessages);

        assertEquals(2, picked.size(), "只应取本轮新增的 assistant/tool 两条");
        assertSame(assistant, picked.get(0));
        assertSame(toolResponse, picked.get(1));
    }

    @Test
    void context_does_not_grow_across_tool_rounds() {
        // 模拟 ChatWithModel 的循环：context 每轮追加新增消息，下一轮它就是 prompt 历史
        List<Message> context = new ArrayList<>(promptHistory());

        for (int round = 1; round <= 3; round++) {
            List<Message> promptMessages = new ArrayList<>(context);
            List<Message> conversationHistory = new ArrayList<>(promptMessages);
            conversationHistory.add(assistantWithToolCall(
                    "call-" + round, "read_file", "{\"path\":\"F" + round + ".java\"}"));
            conversationHistory.add(toolResponse("call-" + round, "read_file", "内容" + round));

            context.addAll(ChatWithModel.pickNewMessages(conversationHistory, promptMessages));
        }

        assertEquals(2 + 3 * 2, context.size(), "每轮只应新增 2 条，历史不得重复累积");
    }

    @Test
    void message_with_same_content_as_history_is_still_new() {
        Message system = BizMessage.toSpringAiMessage(BizMessage.makeSystemMessage("s1", "你是助手"));
        List<Message> promptMessages = List.of(system);
        // 内容与历史某条相同的「新对象」（模型又重复了同一句话）不能被当成历史丢掉
        UserMessage sameTextNewObject = UserMessage.builder().text("你是助手").build();
        List<Message> conversationHistory = new ArrayList<>(promptMessages);
        conversationHistory.add(sameTextNewObject);

        assertEquals(List.of(sameTextNewObject),
                ChatWithModel.pickNewMessages(conversationHistory, promptMessages));
    }

    @Test
    void handles_empty_inputs() {
        assertTrue(ChatWithModel.pickNewMessages(null, List.of()).isEmpty());
        assertTrue(ChatWithModel.pickNewMessages(List.of(), List.of()).isEmpty());
        assertTrue(ChatWithModel.pickNewMessages(List.of(), null).isEmpty());

        // 没有历史时全部算新增
        Message only = BizMessage.toSpringAiMessage(BizMessage.makeUserMessage("s1", "hi"));
        assertEquals(1, ChatWithModel.pickNewMessages(List.of(only), null).size());
    }

    // ===================== 辅助 =====================

    /** 真实组装路径：BizMessage 经 toSpringAiMessage 转成 Spring AI 消息（不再是 BizMessage 类型） */
    private List<Message> promptHistory() {
        return new ArrayList<>(List.of(
                BizMessage.toSpringAiMessage(BizMessage.makeSystemMessage("s1", "你是助手")),
                BizMessage.toSpringAiMessage(BizMessage.makeUserMessage("s1", "看下 A.java"))));
    }

    private AssistantMessage assistantWithToolCall(String id, String toolName, String arguments) {
        return AssistantMessage.builder()
                .content("我先读一下这个文件")
                .toolCalls(List.of(new AssistantMessage.ToolCall(id, "function", toolName, arguments)))
                .build();
    }

    private ToolResponseMessage toolResponse(String id, String toolName, String data) {
        return ToolResponseMessage.builder()
                .responses(List.of(new ToolResponseMessage.ToolResponse(id, toolName, data)))
                .build();
    }
}
