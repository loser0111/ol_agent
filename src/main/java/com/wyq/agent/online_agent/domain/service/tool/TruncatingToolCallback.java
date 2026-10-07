package com.wyq.agent.online_agent.domain.service.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * 工具结果截断包装器：限制单次工具调用返回给模型的内容长度，
 * 防止大文件读取把上下文撑爆。工具定义（名字/描述/参数）原样透传。
 */
public class TruncatingToolCallback implements ToolCallback {

    /** 单次工具结果进入上下文的最大字符数 */
    private static final int MAX_RESULT_CHARS = 6000;

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
        if (raw.length() <= MAX_RESULT_CHARS) {
            return raw;
        }
        return raw.substring(0, MAX_RESULT_CHARS)
                + "\n…（结果过长已截断，剩余 " + (raw.length() - MAX_RESULT_CHARS)
                + " 字符；read_file 请用 offset/limit 分片继续，grep 请缩小范围）";
    }
}
