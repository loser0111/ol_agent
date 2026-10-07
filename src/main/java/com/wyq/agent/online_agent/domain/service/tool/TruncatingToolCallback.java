package com.wyq.agent.online_agent.domain.service.tool;

import com.wyq.agent.online_agent.consts.Constant;
import com.wyq.agent.online_agent.support.TokenEstimator;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * 工具结果截断包装器：限制单次工具调用返回给模型的内容体积，
 * 防止大文件读取把上下文撑爆。工具定义（名字/描述/参数）原样透传。
 *
 * 与旧实现（按固定字符数硬切）的区别：
 * 1. 预算按 **估算 token** 计（{@link Constant#TOOL_RESULT_MAX_TOKENS}），对中英文都成立；
 * 2. 在**行边界**处截断，不会把某一行代码切断（旧实现 substring 会切断一行，模型常因此重读）；
 * 3. 截断提示不再写"请分片继续读"（那等于每轮都催模型再调一次），改为要求"先用 grep 定位再按 offset/limit 读精确区间"。
 */
public class TruncatingToolCallback implements ToolCallback {

    /** 单次工具结果进入上下文的最大估算 token 数 */
    private static final int MAX_RESULT_TOKENS = Constant.TOOL_RESULT_MAX_TOKENS;

    /** 截断点至少保留预算的这个比例，避免"只显示两行"的退化结果 */
    private static final double MIN_KEEP_RATIO = 0.5;

    private final ToolCallback delegate;

    public TruncatingToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        String raw = delegate.call(toolInput);
        if (raw == null) {
            return null;
        }
        int totalTokens = TokenEstimator.estimate(raw);
        if (totalTokens <= MAX_RESULT_TOKENS) {
            return raw;
        }

        // ① 按 token 预算定位截断点
        int cut = TokenEstimator.prefixIndexForTokens(raw, MAX_RESULT_TOKENS);
        // ② 回退到行边界，避免切断某一行
        int lineCut = TokenEstimator.lastLineBreakBefore(raw, cut, (int) (cut * MIN_KEEP_RATIO));
        String kept = raw.substring(0, lineCut);

        int keptTokens = TokenEstimator.estimate(kept);
        int keptLines = TokenEstimator.countLines(kept);
        int totalLines = TokenEstimator.countLines(raw);

        return kept
                + "\n…（结果超长，已按行截断：本次返回 " + keptLines + "/" + totalLines
                + " 行、约 " + keptTokens + " tokens，剩余内容未加载。"
                + "不要整文件重读或逐段续读；请先用 grep 定位目标行，再用 read_file 的 offset/limit 只读该区间）";
    }
}
