package com.xiangxik.echat.chatbot.api.admin;

import com.xiangxik.echat.chatbot.api.dto.TenantRequest;
import com.xiangxik.echat.chatbot.api.dto.TenantResponse;
import com.xiangxik.echat.chatbot.service.AdminListQuery;
import com.xiangxik.echat.chatbot.service.TenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tenants")
@Tag(name = "Tenants", description = "Manage SaaS tenants")
public class TenantAdminController {

    private final TenantService tenantService;

    public TenantAdminController(TenantService tenantService) {
        this.tenantService = tenantService;
    }

    @GetMapping
    @Operation(summary = "List visible tenants")
    public List<TenantResponse> list(@RequestParam Map<String, String> params) {
        return tenantService.listVisible(AdminListQuery.from(params));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a tenant with independent bootstrap data")
    public TenantResponse create(@Valid @RequestBody TenantRequest request) {
        return tenantService.create(request);
    }
}