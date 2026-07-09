package com.xiangxik.echat.chatbot.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "eval_datasets")
public class EvalDataset extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, length = 160)
    private String tenantId = "default";

    @Column(name = "name", nullable = false, unique = true, length = 180)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chatbot_id", nullable = false)
    private ChatbotConfig chatbot;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ChatbotConfig getChatbot() {
        return chatbot;
    }

    public void setChatbot(ChatbotConfig chatbot) {
        this.chatbot = chatbot;
    }
}
