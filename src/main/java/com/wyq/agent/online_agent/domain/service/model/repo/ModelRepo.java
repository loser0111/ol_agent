package com.wyq.agent.online_agent.domain.service.model.repo;

import com.wyq.agent.online_agent.config.ModelConfiguration;
import com.wyq.agent.online_agent.domain.model.model.Model;
import org.springframework.stereotype.Repository;

@Repository
public class ModelRepo {

    private final ModelConfiguration modelConfiguration;

    public ModelRepo(ModelConfiguration modelConfiguration) {
        this.modelConfiguration = modelConfiguration;
    }

    // 模型采用配置文件的方式进行管理，不再单独落DB
    public Model findByName(String modelName) {
        return modelConfiguration.findByName(modelName);
    }
}
