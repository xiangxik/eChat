package com.xiangxik.echat.chatbot.domain.repository;

import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.model.ModelType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {

    Optional<ModelConfig> findByProviderIdAndModelName(Long providerId, String modelName);

    Optional<ModelConfig> findByProviderTenantIdAndId(String tenantId, Long id);

    List<ModelConfig> findByProviderIdOrderByDisplayNameAsc(Long providerId);

    List<ModelConfig> findByProviderTenantIdOrderByDisplayNameAsc(String tenantId);

    List<ModelConfig> findByProviderTenantIdAndProviderIdOrderByDisplayNameAsc(String tenantId, Long providerId);

    List<ModelConfig> findByProviderTenantIdAndEnabledTrueAndModelTypeOrderByDisplayNameAsc(String tenantId,
                                                                                            ModelType modelType);
}