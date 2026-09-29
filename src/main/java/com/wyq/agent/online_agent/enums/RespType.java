package com.wyq.agent.online_agent.enums;

public enum RespType {
    // —— 对话类 ——
    TOKEN,              // 流式回答片段
    ANSWER,             // 整段回答（非流式场景）
    DONE,               // 本轮正常结束（无 data）

    // —— 过程类 ——
    TOOL_CALL,          // 模型正在调用工具
    TOOL_RESPONSE,      // 工具执行结果
    STATUS,             // 状态提示（"正在分析…"），可选

    // —— 交互类 ——
    OPTIONS,            // 单选选项框
    QUESTIONNAIRE,      // 多题问卷（含单选/多选）
    PERMISSION_REQUEST, // 授权弹窗

    // —— 异常类 ——
    ERROR               // 错误（data = ErrorData
}
