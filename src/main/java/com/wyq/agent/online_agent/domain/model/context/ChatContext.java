package com.wyq.agent.online_agent.domain.model.context;

import com.wyq.agent.online_agent.domain.model.dto.ChatReq;
import com.wyq.agent.online_agent.domain.model.dto.ChatResp;
import com.wyq.agent.online_agent.domain.model.messages.BizMessage;
import com.wyq.agent.online_agent.domain.model.model.Model;
import com.wyq.agent.online_agent.domain.model.session.Session;
import com.wyq.agent.online_agent.domain.service.tool.ToolCallGuard;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Sinks;

import java.util.Collection;
import java.util.List;

@Getter
@Setter
public class ChatContext {

    // 会话请求
    ChatReq chatReq;

    // 会话相应
    ChatResp chatResp;

    // 系统提示词
    String systemPrompt;

    // sessionId
    String sessionId;

    // 会话的session
    Session session;

    // 本次会话的模型
    Model model;

    // 本次会话的message
    List<BizMessage> messages;

    // 本次会话可以使用的tools
    List<ToolCallback> tools;

    // 是否已经结束本次的会话了
    Boolean isStop;

    // 结束会话的原因
    Error error;

    // ① 创建发射器（unicast：只允许一个订阅者 = 一个前端连接）
    Sinks.Many<ChatResp> sink;

    // 当前和大模型之间的交互轮次
    int currentTurns;

    // 组装 prompt 时保留的最近消息条数（来自 Agent.maxMessages；<=0 时用默认值）
    int maxMessages;

    // 上下文压缩的边界：第一个"保持原样"的消息下标（由 ContextCompactor 跨轮返回并写回）。
    // 让边界只在超预算时才推进，避免每轮改写尾部消息、吃不满上游的前缀缓存。
    int compactAnchor;

    // 本次会话的工具调用守卫（预算 + 重复检测）；仅运行期使用，不持久化
    ToolCallGuard toolCallGuard;

    /**
     * 追加Message
     */

    public synchronized void AddMessage(BizMessage message) {
        messages.add(message);
    }

    public synchronized void resetMessages(List<BizMessage> messages) {
        this.messages = messages;
    }
}
