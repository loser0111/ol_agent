package com.wyq.agent.online_agent.domain.model.model;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.wyq.agent.online_agent.enums.ModelProtocol;
import lombok.Getter;
import lombok.Setter;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import static com.wyq.agent.online_agent.consts.Constant.DEFAULT_CONTENT_MAX_LENGTH;

@Getter
@Setter
public class Model {
    private String Name; // 假设不同的厂商提供了相同的model, 那么使用Name 取别名来缓解内容差别
    private String baseUrl; // baseURL
    private String apiKey; // apiKey
    private String modelName; // 具体的模型名称
    private Double temperature;
    private Integer ContextMaxLength; // 上下文最大长度管理
    private Boolean underStandImage; // 可以理解图片信息
    private Boolean generateImage; // 可以生成图片
    private ModelProtocol protocol; // 模型通讯协议

    /**
     * 数据转化成ChatModel
     * @return ChatModel
     */
    public ChatModel buildAnthropic() {
        // ① 构建官方 SDK 客户端——apiKey、baseUrl（中转）都在这配
        AnthropicClient client = AnthropicOkHttpClient.builder()
                .apiKey(getApiKey())
                .baseUrl(getBaseUrl() == null
                        ? "https://api.anthropic.com"
                        : getBaseUrl())
                .build();

        // ② 用 SDK 客户端构建 ChatModel——这里只配模型选项
        return AnthropicChatModel.builder()
                .anthropicClient(client)
                .options(AnthropicChatOptions.builder()      // 注意：2.0.1 是 options() 不是 defaultOptions()
                        .model(getModelName())
                        .temperature(getTemperature())
                        .maxTokens(getContextMaxLength() == null
                                ? DEFAULT_CONTENT_MAX_LENGTH : getContextMaxLength())
                        .build())
                .build();
    }

    public ChatModel buildOpenAIModel() {
        // ① 官方 SDK 客户端（openai-java 已随 starter 传递进来，无需显式依赖）
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(getApiKey())
                .baseUrl(getBaseUrl() == null
                        ? "https://api.openai.com"
                        : getBaseUrl())
                .build();

        // ② 构建 ChatModel
        return OpenAiChatModel.builder()
                .openAiClient(client)
                .options(OpenAiChatOptions.builder()
                        .model(getModelName())
                        .temperature(getTemperature())
                        .maxTokens(getContextMaxLength() == null
                                ? DEFAULT_CONTENT_MAX_LENGTH : getContextMaxLength())
                        .build())
                .build();
    }

    /**
     * 构建chatModel（懒加载缓存：避免每次工具循环递归重建 SDK 客户端）
     * @return
     */
    private volatile ChatModel cachedChatModel;

    public ChatModel buildChatModel() {
        if (cachedChatModel == null) {
            synchronized (this) {
                if (cachedChatModel == null) {
                    cachedChatModel = ModelProtocol.ANTHROPIC.equals(getProtocol())
                            ? buildAnthropic()
                            : buildOpenAIModel();
                }
            }
        }
        return cachedChatModel;
    }
}
