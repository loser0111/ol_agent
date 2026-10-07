package com.wyq.agent.online_agent.domain.service.chat;

import com.wyq.agent.online_agent.consts.Constant;
import com.wyq.agent.online_agent.domain.model.messages.BizMessage;
import com.wyq.agent.online_agent.support.TokenEstimator;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文压缩器：把"发给模型的 prompt"控制在 token 预算内。
 *
 * 为什么需要它：原实现每轮都把 context.messages 全量转成 prompt 发出去（ChatWithModel），
 * 而历史只增不减、工具结果又都写进历史，于是轮次一多必然超窗口；且被污染的历史会随会话持久化，
 * 下次进同一会话直接超限。
 *
 * 压缩策略（两级，尽量少破坏信息）：
 * 1. **老消息原地瘦身**（保留结构，不破坏 tool_call ↔ tool_response 配对）：
 *    - 老的 TOOL 消息：把 responses 的 responseData 换成一行动态摘要（保留 id/name，配对关系不变）；
 *    - 老的 ASSISTANT 消息：长正文截断到 {@link #OLD_ASSISTANT_KEEP_CHARS} 字符。
 * 2. **仍超预算则丢最老的整轮**：从头部成组丢弃（assistant tool_calls + 其后全部 tool 响应一起丢），
 *    绝不产生"孤立 tool 响应"，也绝不丢最后一条用户提问。
 *
 * <h3>边界的滞回与冻结（为什么要有 anchor）</h3>
 * 瘦身的"边界"= 从头开始被摘要到哪一条为止。若每轮都按 {@code size - keepRecent} 重算，
 * 边界会随对话每轮前移一格，把"上一轮还原样发出、这一轮被改写成摘要"的那几条反复推进，
 * 于是供应商侧的前缀缓存（OpenAI 兼容协议的自动 prompt caching）在边界处每轮失效，
 * 尾部 keepRecent 条原始内容每轮全价重算 prefill。<p>
 * 因此边界改为**跨轮冻结**：只有真的超预算时才推进，且一次推到位（推到"新边界"）。
 * 不超预算就一直沿用上次的边界 —— 对上游来说等价于"只在尾部追加"，能被缓存吃满。<p>
 * 注意：摘要串是确定性的（取自原始数据的长度/行数，不做 LLM 总结），且摘要自身很短、
 * 有幂等阈值，所以同一条老消息在任意一轮生成的摘要都完全一致，冻结边界下公共前缀是单调增长的。
 * <b>不要改成让 LLM 写历史摘要</b>：每轮措辞都会漂移，公共前缀会整体作废。
 *
 * <h3>已知权衡</h3>
 * 若"最近 keepRecent 条原样内容"本身就超预算（例如连续多次工具结果都接近单条上限），
 * 就会每轮都落到第 3 步丢整轮，此时前缀缓存命中率约等于 0。这是刻意的取舍：
 * 丢最老的整轮丢的是模型多半已消化的旧信息，而把最近几条（当前这轮刚取回的工具结果）
 * 拿去摘要会直接让模型答不出话。要缓解请调小 {@code Agent.maxMessages} 或调大预算。
 *
 * 重要：本类**不修改**传入的消息对象，而是返回一份新列表（新对象），
 * 因此不会污染持久化历史与推送给前端的内容。
 */
@Component
public class ContextCompactor {

    /** 老 assistant 消息正文保留的字符数 */
    private static final int OLD_ASSISTANT_KEEP_CHARS = 1000;

    /** tool response 小于这个字符数时不必压缩（压缩本身也是开销） */
    private static final int TOOL_RESPONSE_DIGEST_THRESHOLD = 400;

    /** 丢弃整轮时的最大循环次数，防御性上界 */
    private static final int MAX_DROP_ROUNDS = 1000;

    /**
     * 压缩结果。
     *
     * @param messages        可直接用于组装 prompt 的消息列表（新对象，与入参隔离）
     * @param changed         本次是否发生了压缩（用于向前端提示）
     * @param estimatedTokens 压缩后的估算 token 数
     * @param anchor          本次使用的边界：第一个"保持原样"的消息下标。调用方需把它存回
     *                        {@code ChatContext.compactAnchor} 并在下次调用时传回来（滞回）
     */
    public record Result(List<BizMessage> messages, boolean changed, int estimatedTokens, int anchor) {
    }

    /**
     * @param source      会话完整消息（不会被修改；同一会话内只追加，所以边界下标可以跨轮复用）
     * @param maxMessages 最近多少条消息必须保持原样（下限）；<=0 时用默认值
     * @param tokenBudget prompt 的估算 token 预算；<=0 时用默认值
     * @param anchor      上次返回的边界；<=0 表示没有（冷启动，本次直接按 maxMessages 切）
     */
    public Result compact(List<BizMessage> source, int maxMessages, int tokenBudget, int anchor) {
        if (source == null || source.isEmpty()) {
            return new Result(new ArrayList<>(), false, 0, 0);
        }

        int keepRecent = maxMessages > 0 ? maxMessages : Constant.KEEP_RECENT_MESSAGES;
        int budget = tokenBudget > 0 ? tokenBudget : Constant.MAX_PROMPT_TOKENS;

        List<BizMessage> out = new ArrayList<>(source);

        // "新"边界：最近 keepRecent 条必须保持原样（keepRecent 是原样条数的下限）
        int freshCutoff = Math.max(0, out.size() - keepRecent);
        // 冻结边界：沿用上次的锚点，不随每轮滑动；锚点最多被推到新边界，不会越过它
        int frozen = anchor > 0 ? Math.min(anchor, freshCutoff) : freshCutoff;
        int cutoff = alignToTurnStart(out, frozen);

        // ===== ① 老消息原地瘦身（只处理 [0, cutoff)，cutoff 跨轮冻结 ⇒ 公共前缀稳定） =====
        boolean changed = digestPrefix(out, 0, cutoff);
        int estimated = estimate(out);

        // ===== ② 真的超预算才推进边界，且一次推到位 =====
        // 滞回：不超预算就沿用冻结边界，避免"每轮把 2 条本来原样的消息改写成摘要"
        // ——那会让"上一轮原样发出、这一轮被改写"的尾部每轮失效，前缀缓存白付。
        if (estimated > budget && cutoff < freshCutoff) {
            int target = alignToTurnStart(out, freshCutoff);
            if (target > cutoff) {
                changed |= digestPrefix(out, cutoff, target);
                cutoff = target;
                estimated = estimate(out);
            }
        }

        // ===== ③ 仍超预算 → 丢最老的整轮（最后手段，会牺牲前缀缓存，见类注释） =====
        if (estimated > budget) {
            // 增量扣减：一次压缩可能连丢很多轮，若每丢一轮就把整段 prompt 重新估算一遍，
            // 真实 BPE 下（40 万字符约 43ms）会把一次压缩拖到秒级。这里先按当前坐标记下
            // 每条消息的体积，之后每丢一轮只减去被丢掉那几条的份额。
            int start = firstNonSystemIndex(out);
            int[] costs = new int[out.size()];
            for (int j = start; j < out.size(); j++) {
                costs[j] = costOf(out.get(j));
            }
            int dropped = 0;   // 已从 start 起累计丢掉多少条（把"当前坐标"映射回 costs 下标）
            int rounds = 0;
            while (estimated > budget && rounds++ < MAX_DROP_ROUNDS) {
                int removed = dropOldestRound(out, start);
                if (removed == 0) {
                    break;
                }
                for (int j = start + dropped, last = start + dropped + removed; j < last; j++) {
                    estimated -= costs[j];
                }
                dropped += removed;
                changed = true;
            }
        }

        // anchor 记的是 source 下标（丢整轮只作用在副本 out 上，不改变 source），可以直接返回
        return new Result(out, changed, Math.max(0, estimated), Math.max(0, cutoff));
    }

    /** 估算整段消息的 token 数（正文 + 工具参数 + 工具结果） */
    public int estimate(List<BizMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (BizMessage m : messages) {
            total += costOf(m);
        }
        return total;
    }

    /** 单条消息的体积：正文 + 工具调用参数 + 工具结果（null 消息按 0 计） */
    private int costOf(BizMessage m) {
        if (m == null) {
            return 0;
        }
        int total = TokenEstimator.estimate(m.getContent());
        if (m.getToolCalls() != null) {
            for (AssistantMessage.ToolCall tc : m.getToolCalls()) {
                total += TokenEstimator.estimate(tc.name()) + TokenEstimator.estimate(tc.arguments());
            }
        }
        if (m.getResponses() != null) {
            for (ToolResponseMessage.ToolResponse tr : m.getResponses()) {
                total += TokenEstimator.estimate(tr.responseData());
            }
        }
        return total;
    }

    // ===================== 内部实现 =====================

    /**
     * 把 [from, to) 区间的老消息原地瘦身（替换 out 里的元素，不改动源消息对象）。
     *
     * @return 是否发生了修改
     */
    private boolean digestPrefix(List<BizMessage> msgs, int from, int to) {
        boolean changed = false;
        int end = Math.min(to, msgs.size());
        for (int i = Math.max(0, from); i < end; i++) {
            BizMessage m = msgs.get(i);
            if (m == null || m.getType() == null) {
                continue;
            }
            switch (m.getType()) {
                case SYSTEM, USER -> {
                    // 系统提示词与用户输入都很小且关键，保留
                }
                case TOOL -> {
                    List<ToolResponseMessage.ToolResponse> shrunk = digestResponses(m.getResponses());
                    if (shrunk != null) {
                        msgs.set(i, copyToolMessage(m, shrunk));
                        changed = true;
                    }
                }
                case ASSISTANT -> {
                    String content = m.getContent();
                    if (content != null && content.length() > OLD_ASSISTANT_KEEP_CHARS) {
                        msgs.set(i, copyAssistantMessage(m,
                                content.substring(0, OLD_ASSISTANT_KEEP_CHARS) + "…（历史回答已截断）"));
                        changed = true;
                    }
                }
                default -> {
                }
            }
        }
        return changed;
    }

    /**
     * 边界量化：把边界对齐到"轮"的起点。
     *
     * 若边界正好落在一条 TOOL 消息上，就往前退到该工具组的第一条，让同一个工具组
     * 要么整组保持原样、要么整组被摘要，不出现"半组"。<p>
     * 两个好处：① 边界取值更确定、可复现（同一份消息算出的边界永远一致，便于跨轮冻结）；
     * ② 工具组不会被从中间切开，摘要与原始内容的分界点和"轮"的粒度一致。
     */
    private int alignToTurnStart(List<BizMessage> msgs, int idx) {
        int i = Math.min(idx, msgs.size());
        while (i > 0 && i < msgs.size()) {
            BizMessage m = msgs.get(i);
            if (m == null || m.getType() == null || m.getType() != MessageType.TOOL) {
                break;
            }
            i--;
        }
        return i;
    }

    /**
     * 把过长的 tool response 内容替换为摘要占位。
     * 保留 id/name（配对关系不能变），仅压缩 responseData。
     *
     * @return null 表示无需压缩
     */
    private List<ToolResponseMessage.ToolResponse> digestResponses(List<ToolResponseMessage.ToolResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return null;
        }
        boolean need = false;
        for (ToolResponseMessage.ToolResponse tr : responses) {
            String data = tr.responseData();
            if (data != null && data.length() > TOOL_RESPONSE_DIGEST_THRESHOLD) {
                need = true;
                break;
            }
        }
        if (!need) {
            return null;
        }

        List<ToolResponseMessage.ToolResponse> result = new ArrayList<>(responses.size());
        for (ToolResponseMessage.ToolResponse tr : responses) {
            String data = tr.responseData();
            if (data == null || data.length() <= TOOL_RESPONSE_DIGEST_THRESHOLD) {
                result.add(tr);
                continue;
            }
            String digest = "【历史工具结果已压缩】" + tr.name() + " 曾返回约 "
                    + TokenEstimator.estimate(data) + " tokens / " + TokenEstimator.countLines(data)
                    + " 行，内容已省略（结论仍在你的回答里）。如需该内容，请重新精确读取："
                    + "先用 grep 定位目标行，再用 read_file 的 offset/limit 只读该片段。";
            result.add(new ToolResponseMessage.ToolResponse(tr.id(), tr.name(), digest));
        }
        return result;
    }

    /** 第一条非 SYSTEM 消息的下标（SYSTEM 消息只出现在开头，压缩过程中不会被丢掉） */
    private int firstNonSystemIndex(List<BizMessage> msgs) {
        int i = 0;
        while (i < msgs.size() && msgs.get(i) != null
                && msgs.get(i).getType() == MessageType.SYSTEM) {
            i++;
        }
        return i;
    }

    /**
     * 从 start 处丢弃"一轮"消息。
     * 一轮 = 一条带 toolCalls 的 assistant 消息 + 其后连续的 tool 响应；
     * 普通 user/assistant 消息按单条丢弃。
     *
     * @return 本次实际丢掉的条数；0 表示无法安全丢弃（会破坏配对或只剩最后一条提问）
     */
    private int dropOldestRound(List<BizMessage> msgs, int start) {
        int i = start;
        // 至少给最后一条（当前用户提问）留位置
        if (i >= msgs.size() - 1) {
            return 0;
        }

        BizMessage first = msgs.get(i);
        if (first == null) {
            msgs.remove(i);
            return 1;
        }
        MessageType type = first.getType();
        if (type == null) {
            msgs.remove(i);
            return 1;
        }

        int end = i + 1;
        if (type == MessageType.TOOL) {
            // 头部出现"孤立 tool 响应"（其 assistant 已不在）：单独删掉反而更不安全，交回上层停止压缩
            return 0;
        }
        if (type == MessageType.ASSISTANT && first.getToolCalls() != null && !first.getToolCalls().isEmpty()) {
            while (end < msgs.size() && msgs.get(end) != null
                    && msgs.get(end).getType() == MessageType.TOOL) {
                end++;
            }
            // 其后若没有其他消息（说明正处在等工具响应的中间态），不能丢
            if (end >= msgs.size()) {
                return 0;
            }
        }

        msgs.subList(i, end).clear();
        return end - i;
    }

    /** 复制一条 TOOL 消息并替换 responses（不改原对象） */
    private BizMessage copyToolMessage(BizMessage src, List<ToolResponseMessage.ToolResponse> responses) {
        return BizMessage.builder()
                .messageId(src.getMessageId())
                .sessionId(src.getSessionId())
                .type(src.getType())
                .content(src.getContent())
                .metadata(src.getMetadata())
                .toolCalls(src.getToolCalls())
                .responses(responses)
                .media(src.getMedia())
                .build();
    }

    /** 复制一条 ASSISTANT 消息并替换正文（不改原对象） */
    private BizMessage copyAssistantMessage(BizMessage src, String content) {
        return BizMessage.builder()
                .messageId(src.getMessageId())
                .sessionId(src.getSessionId())
                .type(src.getType())
                .content(content)
                .metadata(src.getMetadata())
                .toolCalls(src.getToolCalls())
                .responses(src.getResponses())
                .media(src.getMedia())
                .build();
    }
}
