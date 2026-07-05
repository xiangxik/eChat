package com.xiangxik.echat.chatbot.service.llm;

import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import java.util.function.Consumer;

public interface LlmProviderClient {

    boolean supports(ProviderType providerType);

    LlmProviderTestResult testConnection(ProviderConfig providerConfig, String apiKey);

    LlmProviderTestResult testGeneration(ProviderConfig providerConfig, ModelConfig modelConfig, String apiKey);

    LlmChatResponse chat(ProviderConfig providerConfig, ModelConfig modelConfig, String apiKey,
                         LlmChatRequest request);

    void streamChat(ProviderConfig providerConfig, ModelConfig modelConfig, String apiKey,
                    LlmChatRequest request, Consumer<LlmStreamEvent> eventConsumer);
}