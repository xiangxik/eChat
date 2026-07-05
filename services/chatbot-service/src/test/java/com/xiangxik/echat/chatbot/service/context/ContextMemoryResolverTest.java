package com.xiangxik.echat.chatbot.service.context;

import com.xiangxik.echat.chatbot.domain.model.Message;
import com.xiangxik.echat.chatbot.service.MemoryService;
import com.xiangxik.echat.chatbot.service.ShortTermMemoryCache;
import com.xiangxik.echat.chatbot.service.retrieval.RetrievalProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ContextMemoryResolverTest {

    @Test
    void resolvesShortTermLongTermAndRetrievalUsingPolicyLimitsAndScores() {
        ShortTermMemoryCache shortTermMemoryCache = mock(ShortTermMemoryCache.class);
        MemoryService memoryService = mock(MemoryService.class);
        ContextMemoryItem cachedShortTerm = new ContextMemoryItem("recent cached turn", 1.0, Map.of());
        ContextMemoryItem longTerm = new ContextMemoryItem("vpn long-term memory", 0.91, Map.of());
        when(shortTermMemoryCache.list(20L, 3)).thenReturn(List.of(cachedShortTerm));
        when(memoryService.searchLongTerm(10L, "user-1", "vpn down", 2, 0.75)).thenReturn(List.of(longTerm));

        RetrievalProvider retrievalProvider = request -> List.of(
                new ContextMemoryItem("high score doc", 0.92, Map.of()),
                new ContextMemoryItem("low score doc", 0.40, Map.of()),
                new ContextMemoryItem("medium score doc", 0.81, Map.of())
        );
        ContextMemoryResolver resolver = new ContextMemoryResolver(shortTermMemoryCache, memoryService,
                List.of(retrievalProvider));

        ContextMemoryBundle bundle = resolver.resolve(policy(), 10L, 20L, "user-1", "vpn down",
                Map.of("channel", "test"), List.<Message>of());

        assertThat(bundle.shortTermMemory()).containsExactly(cachedShortTerm);
        assertThat(bundle.longTermMemory()).containsExactly(longTerm);
        assertThat(bundle.retrievalResults()).extracting(ContextMemoryItem::content)
                .containsExactly("high score doc", "medium score doc");
        verify(memoryService).searchLongTerm(10L, "user-1", "vpn down", 2, 0.75);
    }

    private ContextPolicyDefinition policy() {
        return new ContextPolicyDefinition(
                "memory-test",
                4000,
                List.of(),
                List.of(
                        new ContextPolicyDefinition.VariableDefinition("shortTermMemory", "memory.shortTerm", 0,
                                3, 0, 0, true, 1),
                        new ContextPolicyDefinition.VariableDefinition("longTermMemory", "memory.longTerm", 0,
                                0, 2, 0.75, true, 1),
                        new ContextPolicyDefinition.VariableDefinition("retrievalResults", "retrieval.vector", 0,
                                0, 2, 0.70, true, 1)
                ),
                List.of(),
                List.of(),
                List.of()
        );
    }
}