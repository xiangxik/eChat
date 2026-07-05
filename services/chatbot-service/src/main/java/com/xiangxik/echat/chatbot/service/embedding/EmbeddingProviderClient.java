package com.xiangxik.echat.chatbot.service.embedding;

import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;

public interface EmbeddingProviderClient {

    boolean supports(ProviderType providerType);

    EmbeddingVector embed(ProviderConfig providerConfig, ModelConfig modelConfig, String apiKey, String input);
}