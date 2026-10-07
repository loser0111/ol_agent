package com.wyq.agent.online_agent.domain.service.model;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.wyq.agent.online_agent.domain.model.model.Model;
import com.wyq.agent.online_agent.enums.ModelProtocol;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

import static com.wyq.agent.online_agent.consts.Constant.DEFAULT_CONTENT_MAX_LENGTH;

@Component
public class ModelService {
    /**
     * 数据转化成ChatModel
     * @return ChatModel
     */
    public ChatModel buildAnthropic(Model model) {
        // ① 构建官方 SDK 客户端——apiKey、baseUrl（中转）都在这配
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(model.getApiKey())
                .baseUrl(model.getBaseUrl() == null
                        ? "https://api.anthropic.com"
                        : model.getBaseUrl())
                .build();

        // ② 用 SDK 客户端构建 ChatModel——这里只配模型选项
        return AnthropicChatModel.builder()
                .anthropicClient(client)
                .options(AnthropicChatOptions.builder()      // 注意：2.0.1 是 options() 不是 defaultOptions()
                        .model(model.getModelName())
                        .temperature(model.getTemperature())
                        .maxTokens(model.getContextMaxLength() == null
                                ? DEFAULT_CONTENT_MAX_LENGTH : model.getContextMaxLength())
                        .build())
                .build();
    }

    public ChatModel buildOpenAIModel(Model model) {
        // ① 官方 SDK 客户端（openai-java 已随 starter 传递进来，无需显式依赖）
        System.out.println(">>> [DIAG] buildOpenAIModel: modelName=" + model.getModelName()
                + ", apiKey=" + (model.getApiKey() == null ? "NULL" : "SET(len=" + model.getApiKey().length() + ")")
                + ", baseUrl=" + model.getBaseUrl()
                + ", protocol=" + model.getProtocol()
                + ", hash=" + System.identityHashCode(model));
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(model.getApiKey())
                .baseUrl(model.getBaseUrl() == null
                        ? "https://api.openai.com"
                        : model.getBaseUrl())
                .build();

        // ② 构建 ChatModel
        return OpenAiChatModel.builder()
                .openAiClient(client)
                .options(OpenAiChatOptions.builder()
                        .model(model.getModelName())
                        .temperature(model.getTemperature())
                        .maxTokens(model.getContextMaxLength() == null
                                ? DEFAULT_CONTENT_MAX_LENGTH : model.getContextMaxLength())
                        .apiKey(model.getApiKey())
                        .baseUrl(model.getBaseUrl() == null
                                ? "https://api.openai.com"
                                : model.getBaseUrl())
                        .maxTokens(8192)
                        .build())
                .build();
    }

    /**
     * 构建chatModel（懒加载缓存：避免每次工具循环递归重建 SDK 客户端）
     * @return
     */

    public ChatModel buildChatModel(Model model) {

        return  ModelProtocol.ANTHROPIC.equals(model.getProtocol())
                ? buildAnthropic(model)
                : buildOpenAIModel(model);
    }
}
