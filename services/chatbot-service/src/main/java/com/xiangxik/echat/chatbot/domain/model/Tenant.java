package com.xiangxik.echat.chatbot.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "tenants")
public class Tenant extends AuditableEntity {

    @Column(name = "tenant_id", nullable = false, unique = true, length = 160)
    private String tenantId;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}