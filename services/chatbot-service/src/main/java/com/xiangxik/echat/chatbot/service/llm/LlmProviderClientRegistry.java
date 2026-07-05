package com.xiangxik.echat.chatbot.service.llm;

import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LlmProviderClientRegistry {

    private final List<LlmProviderClient> clients;

    public LlmProviderClientRegistry(List<LlmProviderClient> clients) {
        this.clients = clients;
    }

    public LlmProviderClient getClient(ProviderType providerType) {
        return clients.stream()
                .filter(client -> client.supports(providerType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported provider type: " + providerType));
    }
}