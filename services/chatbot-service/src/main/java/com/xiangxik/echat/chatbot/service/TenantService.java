package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.api.dto.TenantRequest;
import com.xiangxik.echat.chatbot.api.dto.TenantResponse;
import com.xiangxik.echat.chatbot.config.ChatbotProperties;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderType;
import com.xiangxik.echat.chatbot.domain.model.Tenant;
import com.xiangxik.echat.chatbot.domain.repository.ProviderConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.TenantRepository;
import com.xiangxik.echat.chatbot.security.AdminAccessPolicy;
import com.xiangxik.echat.chatbot.security.AdminPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class TenantService {

    public static final String DEFAULT_TENANT_ID = "default";

    private final TenantRepository tenantRepository;
    private final ProviderConfigRepository providerConfigRepository;
    private final ChatbotProperties properties;

    public TenantService(TenantRepository tenantRepository, ProviderConfigRepository providerConfigRepository,
                         ChatbotProperties properties) {
        this.tenantRepository = tenantRepository;
        this.providerConfigRepository = providerConfigRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> listVisible() {
        return listVisible(AdminListQuery.empty());
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> listVisible(AdminListQuery query) {
        List<TenantResponse> tenants;
        if (currentPrincipalIsSuperAdmin()) {
            tenants = tenantRepository.findAllByOrderByTenantIdAsc().stream().map(this::toResponse).toList();
        } else {
            String tenantId = currentTenantId();
            tenants = tenantRepository.findByTenantId(tenantId).stream().map(this::toResponse).toList();
        }
        return AdminListQuerySupport.apply(tenants, query, tenant -> matchesListQuery(tenant, query), tenantSorters(), "tenantId");
    }

    private boolean matchesListQuery(TenantResponse tenant, AdminListQuery query) {
        return AdminListQuerySupport.containsAny(query.search(), tenant.tenantId(), tenant.name(),
                tenant.enabled() ? "enabled" : "disabled")
                && AdminListQuerySupport.contains(tenant.tenantId(), query.value("tenantId"))
                && AdminListQuerySupport.contains(tenant.name(), query.value("name"))
                && AdminListQuerySupport.equalsBoolean(tenant.enabled(), query.booleanValue("enabled"));
    }

    private Map<String, java.util.function.Function<TenantResponse, ?>> tenantSorters() {
        return Map.of(
                "tenantId", TenantResponse::tenantId,
                "name", TenantResponse::name,
                "enabled", TenantResponse::enabled,
                "createdAt", TenantResponse::createdAt,
                "updatedAt", TenantResponse::updatedAt
        );
    }

    @Transactional
    public TenantResponse create(TenantRequest request) {
        requireSuperAdmin();
        String tenantId = normalizeTenantId(request.tenantId());
        if (tenantRepository.existsByTenantId(tenantId)) {
            throw new IllegalArgumentException("Tenant already exists: " + tenantId);
        }
        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setName(request.name().strip());
        tenant.setEnabled(request.enabled() == null || request.enabled());
        Tenant saved = tenantRepository.save(tenant);
        seedTenantProviders(tenantId);
        return toResponse(saved);
    }

    @Transactional
    public Tenant ensureTenant(String tenantId, String name) {
        String normalized = normalizeTenantId(tenantId);
        return tenantRepository.findByTenantId(normalized).orElseGet(() -> {
            Tenant tenant = new Tenant();
            tenant.setTenantId(normalized);
            tenant.setName(StringUtils.hasText(name) ? name.strip() : normalized);
            tenant.setEnabled(true);
            return tenantRepository.save(tenant);
        });
    }

    @Transactional(readOnly = true)
    public String currentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AdminPrincipal principal) {
            if (principal.roles().contains("SUPER_ADMIN")) {
                String selectedTenant = selectedTenantId();
                if (StringUtils.hasText(selectedTenant)) {
                    return normalizeTenantId(selectedTenant);
                }
            }
            if (StringUtils.hasText(principal.tenantId())) {
                return normalizeTenantId(principal.tenantId());
            }
        }
        String selectedTenant = selectedTenantId();
        return StringUtils.hasText(selectedTenant) ? normalizeTenantId(selectedTenant) : DEFAULT_TENANT_ID;
    }

    public String normalizeTenantId(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return DEFAULT_TENANT_ID;
        }
        return tenantId.strip().toLowerCase(Locale.ROOT);
    }

    public boolean currentPrincipalIsSuperAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AdminPrincipal principal
                && principal.roles().contains("SUPER_ADMIN");
    }

    public void requireSuperAdmin() {
        if (!currentPrincipalIsSuperAdmin()) {
            throw new IllegalArgumentException("Only SUPER_ADMIN can manage tenants");
        }
    }

    @Transactional
    public void seedTenantProviders(String tenantId) {
        String normalized = normalizeTenantId(tenantId);
        for (ChatbotProperties.ProviderSeedProperties seed : properties.bootstrap().providers()) {
            if (providerConfigRepository.findByTenantIdAndName(normalized, seed.name()).isPresent()) {
                continue;
            }
            ProviderConfig provider = new ProviderConfig();
            provider.setTenantId(normalized);
            provider.setName(seed.name());
            provider.setType(ProviderType.valueOf(seed.type().name()));
            provider.setBaseUrl(seed.baseUrl());
            provider.setEnabled(false);
            providerConfigRepository.save(provider);
        }
    }

    private String selectedTenantId() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String tenantId = request.getHeader(AdminAccessPolicy.TENANT_HEADER);
        if (!StringUtils.hasText(tenantId)) {
            tenantId = request.getParameter("tenantId");
        }
        return tenantId;
    }

    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(tenant.getId(), tenant.getTenantId(), tenant.getName(), tenant.isEnabled(),
                tenant.getCreatedAt(), tenant.getUpdatedAt());
    }
}