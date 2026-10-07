package com.wyq.agent.online_agent.consts;

public class Constant {

    public static final String COORDINATOR_SYSTEM_PROMPT = "AGENT.md";
    // 一次会话当中模型调用工具的最大轮次限制（硬熔断，兜底用）
    public static final int MAX_ITERATIONS = 100;

    public static final int DEFAULT_CONTENT_MAX_LENGTH = 320000;

    // ==================== 上下文与工具调用预算 ====================

    /**
     * 单次工具结果进入上下文的最大 token 数（真实 BPE 计数，cl100k_base，见 TokenEstimator）。
     * 超过则在行边界截断（见 TruncatingToolCallback）。
     */
    public static final int TOOL_RESULT_MAX_TOKENS = 8000;

    /**
     * 单次会话允许的工具调用总次数（预算）。
     * 用尽后工具不再执行，模型会被要求基于已有信息作答。
     */
    public static final int MAX_TOOL_CALLS_PER_TURN = 30;

    /**
     * 组装 prompt 时的最大 token 预算（真实 BPE 计数，cl100k_base）。
     * 注意：模型配置里的 contextMaxLength 常被填成远大于真实窗口的值（如 320000），
     * 不能直接当预算用，因此这里给一个保守上限，并取 min(该上限, contextMaxLength/2)。
     */
    public static final int MAX_PROMPT_TOKENS = 60000;

    /**
     * 组装 prompt 时保留的"最近消息"条数，更早的消息会被压缩成摘要占位（这是下限：压缩边界跨轮冻结，实际原样保留的条数可能更多，见 ContextCompactor）。
     * Agent.maxMessages 配置了则优先用它。
     */
    public static final int KEEP_RECENT_MESSAGES = 16;
}
