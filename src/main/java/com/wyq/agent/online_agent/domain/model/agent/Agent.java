package com.wyq.agent.online_agent.domain.model.agent;

import com.wyq.agent.online_agent.config.ModelConfiguration;
import com.wyq.agent.online_agent.domain.model.model.Model;
import com.wyq.agent.online_agent.enums.AgentType;
import lombok.*;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class Agent {

    public AgentType agentType;   // agent的类型
    String name;                  // 显示名
    String systemPrompt;          // 人设 // 系统默认的agent信息
    Model model;                  // 绑定的模型（多模型方案里直接引用）
    List<ToolCallback> tools;     // 工具（含子 agent 工具）
    int maxMessages;              // 记忆窗口条数 // 用来管理记忆信息
    int maxTurns;                 // 防止死循环
    boolean canCreateSubAgent;    // 是否可以生成子代里
    /**
     * 生成一个agent
     */
    public Agent() {

    }
}
