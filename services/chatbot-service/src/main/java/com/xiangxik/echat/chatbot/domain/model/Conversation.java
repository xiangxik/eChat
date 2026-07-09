package com.xiangxik.echat.chatbot.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "conversations")
public class Conversation extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, length = 160)
    private String tenantId = "default";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chatbot_id", nullable = false)
    private ChatbotConfig chatbot;

    @Column(name = "user_id", length = 128)
    private String userId;

    @Column(name = "anonymous_session_id", length = 128)
    private String anonymousSessionId;

    @Column(name = "title", length = 240)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ConversationStatus status = ConversationStatus.ACTIVE;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_workflow_node_id")
    private ChatbotWorkflowNode currentWorkflowNode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "workflow_state", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> workflowState = new LinkedHashMap<>();

    public ChatbotConfig getChatbot() {
        return chatbot;
    }

    public void setChatbot(ChatbotConfig chatbot) {
        this.chatbot = chatbot;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getAnonymousSessionId() {
        return anonymousSessionId;
    }

    public void setAnonymousSessionId(String anonymousSessionId) {
        this.anonymousSessionId = anonymousSessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public void setStatus(ConversationStatus status) {
        this.status = status;
    }

    public ChatbotWorkflowNode getCurrentWorkflowNode() {
        return currentWorkflowNode;
    }

    public void setCurrentWorkflowNode(ChatbotWorkflowNode currentWorkflowNode) {
        this.currentWorkflowNode = currentWorkflowNode;
    }

    public Map<String, Object> getWorkflowState() {
        return workflowState;
    }

    public void setWorkflowState(Map<String, Object> workflowState) {
        this.workflowState = workflowState == null ? new LinkedHashMap<>() : new LinkedHashMap<>(workflowState);
    }
}