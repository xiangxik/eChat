package com.xiangxik.echat.chatbot.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "provider_configs")
public class ProviderConfig extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, length = 160)
    private String tenantId = "default";

    @Column(name = "name", nullable = false, unique = true, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 64)
    private ProviderType type;

    @Column(name = "base_url", length = 1024)
    private String baseUrl;

    @Column(name = "api_key_secret_ref", length = 512)
    private String apiKeySecretRef;

    @Column(name = "encrypted_api_key", columnDefinition = "text")
    private String encryptedApiKey;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProviderType getType() {
        return type;
    }

    public void setType(ProviderType type) {
        this.type = type;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKeySecretRef() {
        return apiKeySecretRef;
    }

    public void setApiKeySecretRef(String apiKeySecretRef) {
        this.apiKeySecretRef = apiKeySecretRef;
    }

    public String getEncryptedApiKey() {
        return encryptedApiKey;
    }

    public void setEncryptedApiKey(String encryptedApiKey) {
        this.encryptedApiKey = encryptedApiKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}