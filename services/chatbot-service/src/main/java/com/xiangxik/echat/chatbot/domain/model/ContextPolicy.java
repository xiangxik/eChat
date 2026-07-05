package com.xiangxik.echat.chatbot.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "context_policies")
public class ContextPolicy extends AuditableEntity {

    @Column(name = "name", nullable = false, unique = true, length = 160)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "dsl_content", nullable = false, columnDefinition = "text")
    private String dslContent;

    @Column(name = "version", nullable = false)
    private int version = 1;

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

    public String getDslContent() {
        return dslContent;
    }

    public void setDslContent(String dslContent) {
        this.dslContent = dslContent;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}