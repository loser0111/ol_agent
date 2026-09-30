package com.wyq.agent.online_agent.domain.model.dto;

import com.wyq.agent.online_agent.enums.RequestType;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
// 会话信息
public class ChatReq {
    String chatId;
    String subChatId;
    RequestType type;               // 新增 QUESTIONNAIRE
    String content;
    String choiceId;
    String optionId;        // CHOICE
    String grantId;
    Boolean granted;         // PERMISSION
    String questionnaireId;                  // QUESTIONNAIRE：必填
    List<Answer> answers;                  // QUESTIONNAIRE：整体提交
    String ModelName; // 这里是模型交互的名称选项
}
