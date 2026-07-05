package com.xiangxik.echat.chatbot.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "chatbot_configs")
public class ChatbotConfig extends AuditableEntity {

    @Column(name = "name", nullable = false, unique = true, length = 160)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_model_id")
    private ModelConfig defaultModel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "context_policy_id")
    private ContextPolicy contextPolicy;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ModelConfig getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(ModelConfig defaultModel) {
        this.defaultModel = defaultModel;
    }

    public ContextPolicy getContextPolicy() {
        return contextPolicy;
    }

    public void setContextPolicy(ContextPolicy contextPolicy) {
        this.contextPolicy = contextPolicy;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}