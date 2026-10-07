package com.wyq.agent.online_agent.config;

// 自定义文件（properties 或 yml 都行）
// config/models.properties:
// agent.models[0].id=analyst
// agent.models[0].name=数据分析专家
// agent.models[0].model=glm-5.3

import com.wyq.agent.online_agent.domain.model.model.Model;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
@ConfigurationProperties(prefix = "agent")
@Component
public class ModelConfiguration {
    /**
     * 配置文件中配置的模型
     */
    private List<Model> models = new ArrayList<>();

    @PostConstruct
    public void init() {
        System.out.println(">>> [DIAG] ModelConfiguration.models.size = " + (models == null ? -1 : models.size()));
        if (models != null) {
            for (Model m : models) {
                System.out.println(">>> [DIAG] modelName=" + m.getModelName()
                        + " apiKey=" + (m.getApiKey() == null ? "NULL" : "SET")
                        + " baseUrl=" + m.getBaseUrl()
                        + " protocol=" + m.getProtocol()
                        + " temp=" + m.getTemperature()
                        + " hash=" + System.identityHashCode(m));
            }
        }
    }
    /**
     * 通过模型名称获取模型本身
     * @param modelName
     * @return
     */
    public Model findByName(String modelName) {
        for (Model model : models) {
            System.out.println(model.getModelName() + "<>" + modelName);
            if (Objects.equals(model.getModelName(), modelName)) {
                return model;
            }
        }
        return null;
    }
}