package com.wyq.agent.online_agent.domain.service.chat;

import com.wyq.agent.online_agent.consts.Constant;
import com.wyq.agent.online_agent.domain.model.messages.BizMessage;
import com.wyq.agent.online_agent.domain.service.tool.ToolCallGuard;
import com.wyq.agent.online_agent.support.TokenEstimator;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 修复的回归测试：上下文压缩（含边界冻结）、工具调用预算与重复检测、工具结果截断、token 估算。
 * 纯逻辑测试，不加载 Spring 上下文。
 */
class ContextBudgetTest {

    private final ContextCompactor compactor = new ContextCompactor();

    // ===================== ContextCompactor =====================

    @Test
    void compact_keeps_recent_messages_and_digests_old_tool_results() {
        List<BizMessage> history = buildToolLoopHistory(30, 5000);
        int before = compactor.estimate(history);

        ContextCompactor.Result r = compactor.compact(history, 6, Constant.MAX_PROMPT_TOKENS, 0);

        assertTrue(r.changed(), "老的工具结果应被压缩");
        assertTrue(r.estimatedTokens() < before, "压缩后 token 必须下降");
        assertFalse(r.estimatedTokens() > Constant.MAX_PROMPT_TOKENS, "压缩后不应超过预算");

        // ① 配对完整性：每条 TOOL 消息前面必须能找到带 toolCalls 的 assistant 消息
        assertPairingValid(r.messages());
        // ② 首条非 system 消息不能是孤立 TOOL
        assertFalse(firstNonSystem(r.messages()).getType() == MessageType.TOOL);
        // ③ 最后一条（当前提问）必须保留，且是原对象
        assertSame(history.get(history.size() - 1), r.messages().get(r.messages().size() - 1));
        // ④ 原列表不得被修改
        assertEquals(before, compactor.estimate(history), "压缩器不能改动入参消息");
    }

    @Test
    void compact_drops_oldest_rounds_when_over_budget_and_keeps_pairing() {
        List<BizMessage> history = buildToolLoopHistory(40, 4000);
        int tinyBudget = 4000;

        ContextCompactor.Result r = compactor.compact(history, 10, tinyBudget, 0);

        assertTrue(r.changed());
        assertPairingValid(r.messages());
        assertFalse(firstNonSystem(r.messages()).getType() == MessageType.TOOL,
                "丢弃后不能以孤立 tool 响应开头");
        assertSame(history.get(history.size() - 1), r.messages().get(r.messages().size() - 1),
                "最后一条用户提问不能被丢掉");
        assertTrue(r.messages().size() < history.size(), "超预算时必须真的丢了消息");
        // 丢整轮走的是增量扣减，结果必须与全量重算一致
        assertEquals(compactor.estimate(r.messages()), r.estimatedTokens(),
                "增量扣减算出的体积必须与全量重算一致");
    }

    @Test
    void compact_leaves_small_history_untouched() {
        List<BizMessage> history = new ArrayList<>(List.of(
                BizMessage.makeSystemMessage("s1", "system"),
                BizMessage.makeUserMessage("s1", "你好")));

        ContextCompactor.Result r = compactor.compact(history, Constant.KEEP_RECENT_MESSAGES,
                Constant.MAX_PROMPT_TOKENS, 0);

        assertFalse(r.changed());
        assertEquals(2, r.messages().size());
    }

    // ===================== 边界冻结（滞回） =====================

    @Test
    void compact_freezes_anchor_so_tail_is_not_rewritten_every_round() {
        List<BizMessage> history = buildToolLoopHistory(30, 5000);

        ContextCompactor.Result first = compactor.compact(history, 6, Constant.MAX_PROMPT_TOKENS, 0);
        assertTrue(first.anchor() > 0, "第一次压缩应给出边界");

        // 会话继续两轮：历史只追加，旧消息对象不变（真实场景就是这样）
        List<BizMessage> grown = new ArrayList<>(history);
        grown.add(BizMessage.makeUserMessage("s1", "继续"));
        grown.add(BizMessage.builder()
                .sessionId("s1")
                .type(MessageType.ASSISTANT)
                .content("好")
                .build());

        ContextCompactor.Result second =
                compactor.compact(grown, 6, Constant.MAX_PROMPT_TOKENS, first.anchor());

        // 预算内必须沿用上次的边界：
        // 若每轮按 size - keepRecent 重算，边界会一直前移，把"上一轮原样发出、这一轮被改写"的
        // 那几条反复推进，上游前缀缓存在边界处每轮失效。
        assertEquals(first.anchor(), second.anchor(), "预算内边界必须冻结，不能每轮往前挪");

        assertEquals(grown.size(), second.messages().size(), "预算充裕时不该丢消息");
        for (int i = second.anchor(); i < grown.size(); i++) {
            assertSame(grown.get(i), second.messages().get(i), "边界之后的消息必须原样保留");
        }
    }

    @Test
    void compact_advances_anchor_in_one_jump_when_over_budget() {
        List<BizMessage> history = buildToolLoopHistory(20, 5000);
        // 第一次宽松预算：只切出冷启动边界
        ContextCompactor.Result wide = compactor.compact(history, 6, Constant.MAX_PROMPT_TOKENS, 0);

        // 会话继续长，预算收紧 → 必须一次推到位
        List<BizMessage> grown = new ArrayList<>(history);
        grown.addAll(buildToolLoopHistory(10, 5000).subList(2, 22));

        int tightBudget = 6000;
        ContextCompactor.Result narrow = compactor.compact(grown, 6, tightBudget, wide.anchor());
        // 冷启动（不带锚点）会直接按 size - keepRecent 切，作为"新边界"的参照
        ContextCompactor.Result coldStart = compactor.compact(grown, 6, tightBudget, 0);

        assertTrue(narrow.anchor() > wide.anchor() + 1,
                "超预算时边界应一次推到位，而不是每轮只挪一格");
        assertEquals(coldStart.anchor(), narrow.anchor(),
                "超预算时应直接推到新边界（等价于冷启动切分）");
    }

    // ===================== ToolCallGuard =====================

    @Test
    void guard_shortCircuits_duplicate_call_without_consuming_budget() {
        ToolCallGuard guard = new ToolCallGuard(3);

        assertNull0(guard.tryAcquire("read_file", "{ \"path\": \"A.java\" }"));
        assertEquals(1, guard.used());

        // 同工具 + 同参数（仅空白不同）→ 短路，且不占预算
        String hint = guard.tryAcquire("read_file", "{  \"path\":   \"A.java\" }");
        assertNotNull(hint);
        assertTrue(hint.contains("重复调用已跳过"));
        assertEquals(1, guard.used(), "重复调用不应消耗预算");

        // 换参数 → 放行
        assertNull0(guard.tryAcquire("read_file", "{ \"path\": \"B.java\" }"));
        assertEquals(2, guard.used());
    }

    @Test
    void guard_blocks_when_budget_exhausted() {
        ToolCallGuard guard = new ToolCallGuard(2);
        assertNull0(guard.tryAcquire("grep", "{\"pattern\":\"a\"}"));
        assertNull0(guard.tryAcquire("grep", "{\"pattern\":\"b\"}"));
        assertTrue(guard.exhausted());

        String hint = guard.tryAcquire("grep", "{\"pattern\":\"c\"}");
        assertNotNull(hint);
        assertTrue(hint.contains("预算已用尽"));
        assertEquals(2, guard.used());
    }

    // ===================== TokenEstimator =====================

    @Test
    void estimator_counts_real_bpe_tokens() {
        // 真实 BPE（cl100k_base）：长重复串会被合并，100 个 'a' 只有 13 个 token；
        // 中文没有这种合并空间，10 个字就是 10 个 token（约 1 token/字）。
        // 旧启发式（ASCII÷4）会把前者算成 25，误差接近 2 倍。
        assertEquals(13, TokenEstimator.estimate("a".repeat(100)));
        assertEquals(10, TokenEstimator.estimate("中".repeat(10)));

        // 关键回归：中文/混排不能再用"字符数 ÷ 4"估算，否则预算会被严重低估
        String zh = "这是一段用于估算的中文文本，包含标点符号和 English words 混排。";
        assertTrue(TokenEstimator.estimate(zh) > zh.length() / 4,
                "中文按字符数 ÷ 4 估会低估，实际应更接近 1 token/字");
    }

    @Test
    void estimator_prefix_index_yields_prefix_within_budget() {
        // 截断式编码返回"最后一个被处理字符的下标（含）"，故前缀是 [0, idx+1)
        assertEquals(5, TokenEstimator.prefixIndexForTokens("中".repeat(10), 5));
        assertEquals(80, TokenEstimator.prefixIndexForTokens("a".repeat(100), 10));
        // 整段都在预算内 → 返回全长
        assertEquals(10, TokenEstimator.prefixIndexForTokens("中".repeat(10), 1000));
        // 预算非法 → 0
        assertEquals(0, TokenEstimator.prefixIndexForTokens("中".repeat(10), 0));
        assertEquals(0, TokenEstimator.prefixIndexForTokens(null, 10));
    }

    @Test
    void estimator_tolerates_special_token_literals() {
        // 工具结果里完全可能出现 "<|endoftext|>"（例如 dump 模型文件、prompt 模板）：
        // jtokkit 的 countTokens/encode 遇到特殊串会抛 UnsupportedOperationException，
        // 估算本身不能把对话打断，故一律走 Ordinary 版本。
        String text = "a<|endoftext|>b";
        assertTrue(TokenEstimator.estimate(text) > 0);
        assertTrue(TokenEstimator.prefixIndexForTokens(text, 1) >= 0);
    }

    // ===================== 辅助 =====================

    /** 造一段"工具循环"历史：system + 用户问题 + N 轮（assistant toolCalls + tool 结果） + 最后提问 */
    private List<BizMessage> buildToolLoopHistory(int rounds, int toolResultChars) {
        List<BizMessage> msgs = new ArrayList<>();
        msgs.add(BizMessage.makeSystemMessage("s1", "你是助手"));
        msgs.add(BizMessage.makeUserMessage("s1", "分析这个项目"));
        for (int i = 0; i < rounds; i++) {
            String callId = "call-" + i;
            msgs.add(BizMessage.builder()
                    .sessionId("s1")
                    .type(MessageType.ASSISTANT)
                    .content("")
                    .toolCalls(List.of(new AssistantMessage.ToolCall(
                            callId, "function", "read_file", "{\"path\":\"File" + i + ".java\"}")))
                    .build());
            msgs.add(BizMessage.builder()
                    .sessionId("s1")
                    .type(MessageType.TOOL)
                    .responses(List.of(new ToolResponseMessage.ToolResponse(
                            callId, "read_file", "x".repeat(toolResultChars))))
                    .build());
        }
        msgs.add(BizMessage.makeUserMessage("s1", "总结一下"));
        return msgs;
    }

    /** 校验 tool 响应与 assistant toolCalls 的配对关系完整（API 会拒绝不配对的消息序列） */
    private void assertPairingValid(List<BizMessage> msgs) {
        String pendingCallId = null;
        for (BizMessage m : msgs) {
            if (m.getType() == MessageType.ASSISTANT && m.getToolCalls() != null && !m.getToolCalls().isEmpty()) {
                assertTrue(pendingCallId == null, "上一条 tool 调用没有对应的结果");
                pendingCallId = m.getToolCalls().get(0).id();
            } else if (m.getType() == MessageType.TOOL) {
                assertNotNull(pendingCallId, "出现没有 assistant toolCalls 的孤立 tool 响应");
                for (ToolResponseMessage.ToolResponse tr : m.getResponses()) {
                    assertEquals(pendingCallId, tr.id(), "tool 响应 id 必须与 toolCall id 一致");
                }
                pendingCallId = null;
            }
        }
    }

    private BizMessage firstNonSystem(List<BizMessage> msgs) {
        for (BizMessage m : msgs) {
            if (m.getType() != MessageType.SYSTEM) {
                return m;
            }
        }
        throw new IllegalStateException("没有非 system 消息");
    }

    private void assertNull0(String s) {
        assertTrue(s == null, "应放行（返回 null），实际: " + s);
    }
}
