package com.wyq.agent.online_agent.application.chat_app;

import com.wyq.agent.online_agent.enums.RequestType;

import java.util.List;

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
}
