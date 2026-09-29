package com.wyq.agent.online_agent.application.chat_app;

import com.wyq.agent.online_agent.enums.RespType;

public class ChatResp {
    private String respId;          // 响应唯一 ID（UUID），幂等/去重/审计用
    private String chatId;          // 主会话（服务端生成，首次请求返回给前端）
    private String subChatId;       // 子 agent 会话（可选，为空则前端忽略）
    private RespType type;          // 响应类型：决定 data 怎么解析
    private long timestamp;         // 毫秒时间戳
    private Object data;            // 载荷（按 type 强转/反序列化）
    private String message;         // 人类可读说明（错误信息/状态说明，可选）
}

// 对话类
/*
public record TokenData(String content) {}

// 过程类
public record ToolCallData(String toolName, String arguments) {}
public record ToolResponseData(String toolName, Object response) {}

// 交互类（与请求侧对称）
public record OptionsData(String choiceId, String question, List<Option> options) {}
public record Option(String id, String label, String description) {}

public record QuestionnaireData(String questionnaireId, List<Question> questions) {}
public record Question(String questionId, String question, QuestionType type,
                       List<Option> options, boolean required,
                       Integer minSelect, Integer maxSelect) {}

public record PermissionRequestData(String grantId, String toolName,
                                    String requiredPermission, String question) {}

// 异常类
public record ErrorData(String code, String message) {}   // code: AUTH_TIMEOUT / PERMISSION_DENIED ...
 */
