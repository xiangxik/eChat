package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import java.util.List;

public final class DefaultProviderCatalog {

    private static final List<ProviderSeed> PROVIDERS = List.of(
            new ProviderSeed("Minimax Oversea", ProviderType.ANTHROPIC, "https://api.minimax.io/anthropic/v1"),
            new ProviderSeed("Minimax China", ProviderType.OPENAI_COMPATIBLE, "https://api.minimax.chat/v1"),
            new ProviderSeed("Qwen", ProviderType.OPENAI_COMPATIBLE, "https://dashscope.aliyuncs.com/compatible-mode/v1"),
            new ProviderSeed("Claude", ProviderType.ANTHROPIC, "https://api.anthropic.com/v1"),
            new ProviderSeed("OpenAI", ProviderType.OPENAI_COMPATIBLE, "https://api.openai.com/v1"),
            new ProviderSeed("Gemini", ProviderType.GEMINI, "https://generativelanguage.googleapis.com/v1beta")
    );

    private DefaultProviderCatalog() {
    }

    public static List<ProviderSeed> providers() {
        return PROVIDERS;
    }

    public record ProviderSeed(String name, ProviderType type, String baseUrl) {
    }
}