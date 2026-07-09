package com.xiangxik.echat.chatbot.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "eval_runs")
public class EvalRun extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, length = 160)
    private String tenantId = "default";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private EvalDataset dataset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chatbot_id", nullable = false)
    private ChatbotConfig chatbot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_id")
    private ModelConfig model;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private EvalRunStatus status = EvalRunStatus.PENDING;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> summary = new LinkedHashMap<>();

    public EvalDataset getDataset() {
        return dataset;
    }

    public void setDataset(EvalDataset dataset) {
        this.dataset = dataset;
    }

    public ChatbotConfig getChatbot() {
        return chatbot;
    }

    public void setChatbot(ChatbotConfig chatbot) {
        this.chatbot = chatbot;
    }

    public ModelConfig getModel() {
        return model;
    }

    public void setModel(ModelConfig model) {
        this.model = model;
    }

    public EvalRunStatus getStatus() {
        return status;
    }

    public void setStatus(EvalRunStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Map<String, Object> getSummary() {
        return summary;
    }

    public void setSummary(Map<String, Object> summary) {
        this.summary = summary == null ? new LinkedHashMap<>() : new LinkedHashMap<>(summary);
    }
}
