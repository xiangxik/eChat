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
@Table(name = "eval_results")
public class EvalResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private EvalRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private EvalCase evalCase;

    @Column(name = "output", columnDefinition = "text")
    private String output;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "context_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> contextSnapshot = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "token_budget_report", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> tokenBudgetReport = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scores", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> scores = new LinkedHashMap<>();

    @Column(name = "passed", nullable = false)
    private boolean passed;

    @Column(name = "error", columnDefinition = "text")
    private String error;

    public EvalRun getRun() {
        return run;
    }

    public void setRun(EvalRun run) {
        this.run = run;
    }

    public EvalCase getEvalCase() {
        return evalCase;
    }

    public void setEvalCase(EvalCase evalCase) {
        this.evalCase = evalCase;
    }

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public Map<String, Object> getContextSnapshot() {
        return contextSnapshot;
    }

    public void setContextSnapshot(Map<String, Object> contextSnapshot) {
        this.contextSnapshot = contextSnapshot == null ? new LinkedHashMap<>() : new LinkedHashMap<>(contextSnapshot);
    }

    public Map<String, Object> getTokenBudgetReport() {
        return tokenBudgetReport;
    }

    public void setTokenBudgetReport(Map<String, Object> tokenBudgetReport) {
        this.tokenBudgetReport = tokenBudgetReport == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tokenBudgetReport);
    }

    public Map<String, Object> getScores() {
        return scores;
    }

    public void setScores(Map<String, Object> scores) {
        this.scores = scores == null ? new LinkedHashMap<>() : new LinkedHashMap<>(scores);
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
