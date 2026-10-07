package com.wyq.agent.online_agent.domain.service.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * 把 {@link ToolCallGuard} 挂到具体工具上的包装器：
 * 调用前先过守卫（预算 / 重复检测），被拒绝时直接把提示当工具结果返回，不执行真正的工具。
 * 工具定义（名字/描述/参数）原样透传，模型看到的工具列表不变。
 */
public class GuardedToolCallback implements ToolCallback {

    private final ToolCallback delegate;
    private final ToolCallGuard guard;

    public GuardedToolCallback(ToolCallback delegate, ToolCallGuard guard) {
        this.delegate = delegate;
        this.guard = guard;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public String call(String toolInput) {
        String shortCircuit = guard.tryAcquire(getToolDefinition().name(), toolInput);
        if (shortCircuit != null) {
            return shortCircuit;
        }
        return delegate.call(toolInput);
    }
}
