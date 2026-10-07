package com.wyq.agent.online_agent.domain.service.tool;

import com.wyq.agent.online_agent.consts.Constant;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单次会话的工具调用守卫：**预算控制** + **重复调用短路**。
 *
 * 背景：原实现只靠 Constant.MAX_ITERATIONS 做熔断，既不阻止低效调用，也不阻止
 * "同工具同参数反复调用"，结果大量轮次被浪费在重复读取上，并把上下文迅速推高。
 *
 * 语义：
 * - 每次调用先 {@link #tryAcquire(String, String)}：
 *   - 命中重复（同工具 + 同参数，参数忽略空白差异）→ 返回短路提示，**不执行**、不占预算；
 *   - 预算用尽 → 返回提示，**不执行**，要求模型基于已有信息作答；
 *   - 否则登记本次调用、预算 +1，返回 null 表示放行。
 * - 短路提示本身很短，不会再把上下文推高；被跳过的调用也不会进入消息历史。
 *
 * 线程安全：由单次会话的单线程循环使用，方法加 synchronized 仅为防御。
 */
public class ToolCallGuard {

    /** 重复调用的短路提示（刻意不含原结果全文，避免重复内容再次进上下文） */
    private static final String DUPLICATE_HINT_PREFIX = "【重复调用已跳过】";

    private final int budget;
    private final Map<String, Integer> callCounts = new LinkedHashMap<>();
    private int used;

    public ToolCallGuard() {
        this(Constant.MAX_TOOL_CALLS_PER_TURN);
    }

    public ToolCallGuard(int budget) {
        this.budget = budget > 0 ? budget : Constant.MAX_TOOL_CALLS_PER_TURN;
    }

    /**
     * 尝试取得一次工具调用资格。
     *
     * @return null 表示放行；非 null 表示不执行，直接把它作为工具结果返回给模型
     */
    public synchronized String tryAcquire(String toolName, String arguments) {
        String key = key(toolName, arguments);
        Integer seen = callCounts.get(key);

        if (seen != null) {
            return DUPLICATE_HINT_PREFIX + "工具 " + toolName + " 的这次调用与之前第 " + seen
                    + " 次调用的参数完全相同，返回内容也相同，因此本次不再执行。"
                    + "请直接使用上一条工具结果继续，不要重复调用；若确实需要新信息，请改变参数（例如换 offset/limit 或换关键词）。";
        }

        if (used >= budget) {
            return "【工具调用预算已用尽】本次会话最多允许 " + budget + " 次工具调用，现已用完，本次调用未执行。"
                    + "请立刻停止调用工具，基于已获得的信息给出最终回答；信息不足时请明确说明缺少什么，而不是继续尝试调用。";
        }

        used++;
        callCounts.put(key, used);
        return null;
    }

    /** 已使用的工具调用次数 */
    public synchronized int used() {
        return used;
    }

    /** 预算是否已用尽 */
    public synchronized boolean exhausted() {
        return used >= budget;
    }

    /** 是否已触发过重复调用（用于前端提示） */
    public synchronized boolean hasDuplicate() {
        return callCounts.size() < used;
    }

    public int budget() {
        return budget;
    }

    /** 同工具 + 同参数视为重复；参数去掉首尾与内部多余空白，避免格式化差异造成漏判 */
    private String key(String toolName, String arguments) {
        String normalized = arguments == null ? "" : arguments.trim().replaceAll("\\s+", " ");
        return toolName + "\u0000" + normalized;
    }
}
