package com.wyq.agent.online_agent.domain.model.model;

public class Model {
    private String Name; // 假设不同的厂商提供了相同的model, 那么使用Name 取别名来缓解内容差别
    private String baseUrl; // baseURL
    private String apiKey; // apiKey
    private String modelName; // 具体的模型名称
    private Double temperature;
    private Integer ContextMaxLength; // 上下文最大长度管理
    private Boolean underStandImage; // 可以理解图片信息
    private Boolean generateImage; // 可以生成图片
}
