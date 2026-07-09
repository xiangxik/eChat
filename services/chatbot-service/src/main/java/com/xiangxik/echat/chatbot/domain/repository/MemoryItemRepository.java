package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.MemoryItem;
import com.xiangxik.echat.chatbot.domain.model.MemoryScope;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryItemRepository extends JpaRepository<MemoryItem, Long> {

    List<MemoryItem> findByTenantIdAndChatbotIdOrderByUpdatedAtDesc(String tenantId, Long chatbotId);

    List<MemoryItem> findByTenantIdAndChatbotIdAndScopeOrderByUpdatedAtDesc(String tenantId, Long chatbotId,
                                                                             MemoryScope scope);

    List<MemoryItem> findByTenantIdAndChatbotIdAndUserIdOrderByUpdatedAtDesc(String tenantId, Long chatbotId,
                                                                              String userId);

    java.util.Optional<MemoryItem> findByTenantIdAndId(String tenantId, Long id);

        @Query(value = """
                        SELECT id, 1 - (embedding <=> CAST(:embedding AS vector)) AS score
                        FROM memory_items
                        WHERE tenant_id = :tenantId
                            AND chatbot_id = :chatbotId
                            AND scope IN ('LONG_TERM', 'GLOBAL')
                            AND embedding IS NOT NULL
                            AND (CAST(:userId AS varchar) IS NULL OR user_id = CAST(:userId AS varchar) OR user_id IS NULL)
                            AND (1 - (embedding <=> CAST(:embedding AS vector))) >= :minScore
                        ORDER BY embedding <=> CAST(:embedding AS vector)
                        LIMIT :topK
                        """, nativeQuery = true)
                List<MemoryItemSearchHit> searchLongTermByEmbedding(@Param("tenantId") String tenantId,
                                                                    @Param("chatbotId") Long chatbotId,
                                                                                                                @Param("userId") String userId,
                                                                                                                @Param("embedding") String embedding,
                                                                                                                @Param("minScore") double minScore,
                                                                                                                @Param("topK") int topK);
}