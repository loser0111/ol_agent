package com.wyq.agent.online_agent.domain.model.agent;

import com.wyq.agent.online_agent.domain.model.model.Model;
import com.wyq.agent.online_agent.enums.AgentType;
import lombok.Builder;
import lombok.Getter;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

@Getter
@Builder
public class Agent {
    public final AgentType agentType;
    public final String id;                   // "analyst" / "coder"
    final String name;                  // 显示名
    final String systemPrompt;          // 人设
    final Model model;              // 绑定的模型（多模型方案里直接引用）
    final List<ToolCallback> tools;    // 工具（含子 agent 工具）
    final int maxMessages;              // 记忆窗口条数 // 用来管理记忆信息
    final int maxDepth;                  // 子 agent 委派深度上限
    final int maxTurns;             // 防止死循环
}
