package com.xiangxik.echat.chatbot.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "chatbot_workflow_transitions")
public class ChatbotWorkflowTransition extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chatbot_id", nullable = false)
    private ChatbotConfig chatbot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_node_id", nullable = false)
    private ChatbotWorkflowNode fromNode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_node_id", nullable = false)
    private ChatbotWorkflowNode toNode;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "condition_expression", nullable = false, columnDefinition = "text")
    private String conditionExpression;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public ChatbotConfig getChatbot() {
        return chatbot;
    }

    public void setChatbot(ChatbotConfig chatbot) {
        this.chatbot = chatbot;
    }

    public ChatbotWorkflowNode getFromNode() {
        return fromNode;
    }

    public void setFromNode(ChatbotWorkflowNode fromNode) {
        this.fromNode = fromNode;
    }

    public ChatbotWorkflowNode getToNode() {
        return toNode;
    }

    public void setToNode(ChatbotWorkflowNode toNode) {
        this.toNode = toNode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getConditionExpression() {
        return conditionExpression;
    }

    public void setConditionExpression(String conditionExpression) {
        this.conditionExpression = conditionExpression;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}