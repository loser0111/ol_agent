package com.wyq.agent.online_agent.domain.model.agent;

import com.wyq.agent.online_agent.config.ModelConfiguration;
import com.wyq.agent.online_agent.domain.model.dto.ChatResp;
import com.wyq.agent.online_agent.domain.model.model.Model;
import com.wyq.agent.online_agent.enums.AgentType;
import lombok.*;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Sinks;

import java.util.List;

/**
 * 直接定义的一个agent,
 * 一个agent一般包括：
 * - 模型信息
 * - 可用工具列表
 * - 系统提示词
 * - agent类型
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Agent {
    // 必须填写的参数
    public AgentType agentType;   // agent的类型
    String name;                  // 显示名
    String systemPrompt;          // 人设 // 系统默认的agent信息
    Model model;                  // 绑定的模型（多模型方案里直接引用）
    List<ToolCallback> tools;     // 工具（含子 agent 工具）
    int maxMessages;              // 记忆窗口条数 // 用来管理记忆信息
    int maxTurns;                 // 防止死循环
    boolean canCreateSubAgent;    // 是否可以生成子代里

    // 和前端会话的使用的推送信息
    Sinks.Many<ChatResp> sink;

}
