package com.xiangxik.echat.chatbot.service.embedding;

import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingProviderClientRegistry {

    private final List<EmbeddingProviderClient> clients;

    public EmbeddingProviderClientRegistry(List<EmbeddingProviderClient> clients) {
        this.clients = List.copyOf(clients);
    }

    public EmbeddingProviderClient getClient(ProviderType providerType) {
        return clients.stream()
                .filter(client -> client.supports(providerType))
                .findFirst()
                .orElseThrow(() -> new LlmProviderException("No embedding client registered for provider type " + providerType));
    }
}