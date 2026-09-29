package com.wyq.agent.online_agent.application.chat_app;

import java.util.Map;

public class Option {
    String id;                    // 选项唯一 ID：必填，回传/校验/幂等用
    String label;                 // 展示文本：必填，前端按钮/选项文案
    String description;           // 辅助说明：可选，小字副标题
    String icon;                  // 图标/emoji：可选，前端增强
    boolean disabled;             // 是否禁用：可选，灰置不可选
    Map<String, Object> metadata;  // 扩展槽：可选，业务值/分组/排序等
}
