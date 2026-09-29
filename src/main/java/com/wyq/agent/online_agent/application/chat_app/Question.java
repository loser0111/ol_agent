package com.wyq.agent.online_agent.application.chat_app;

import com.wyq.agent.online_agent.enums.QuestionType;

import java.util.List;

public class Question {
    String questionId;
    String question;
    QuestionType type;
    List<Option> options;
    boolean required;
    Integer minSelect;    // 多选：最少选几个（null = 不限制）
    Integer maxSelect;    // 多选：最多选几个（null = 不限制）
}
