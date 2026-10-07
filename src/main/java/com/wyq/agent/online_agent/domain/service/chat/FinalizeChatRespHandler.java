package com.wyq.agent.online_agent.domain.service.chat;

import com.wyq.agent.online_agent.domain.model.context.ChatContext;
import com.wyq.agent.online_agent.domain.model.dto.BaseResp;
import com.wyq.agent.online_agent.domain.model.dto.ChatResp;
import com.wyq.agent.online_agent.domain.model.messages.BizMessage;
import com.wyq.agent.online_agent.enums.BizError;
import com.wyq.agent.online_agent.enums.RespType;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Component;

@Component
public class FinalizeChatRespHandler implements ChatHandler {

    @Override
    public String Name() { return "FinalizeChatResp"; }

    @Override
    public void Handle(ChatContext context) {
        // ① 错误分支：对话异常，直接封装错误
        if (context.getError() != null) {
            context.setChatResp(ChatResp.builder()
                    .chatId(context.getSessionId())
                    .type(RespType.ERROR)
                    .data(new BaseResp(-1L, context.getError().getMessage()))
                    .timestamp(System.currentTimeMillis())
                    .build());
            return;
        }

        // ② 正常分支：倒序找最后一条 assistant 回答（工具消息不是回答）
        BizMessage answer = context.getMessages().stream()
                .filter(m -> m.getMessageType() == MessageType.ASSISTANT)  // 或按 type 字段判断
                .reduce((first, second) -> second)                          // 取最后一条
                .orElseThrow(() -> new IllegalStateException("对话未产出回答"));

        context.setChatResp(ChatResp.builder()
                .chatId(context.getSessionId())
                .type(RespType.DONE)
                .data(answer.getContent())     // 或包一层 AnswerData
                .timestamp(System.currentTimeMillis())
                .baseResp(new BaseResp(BizError.SUCCESS.getCode(), BizError.SUCCESS.getMessage()))
                .build());
    }
}
