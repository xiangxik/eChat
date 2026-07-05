package com.xiangxik.echat.chatbot.api.admin;

import com.xiangxik.echat.chatbot.api.dto.ProviderConfigRequest;
import com.xiangxik.echat.chatbot.api.dto.ProviderConfigResponse;
import com.xiangxik.echat.chatbot.api.dto.ProviderConnectionTestResponse;
import com.xiangxik.echat.chatbot.service.ProviderConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/providers")
@Tag(name = "Provider Configs", description = "Manage LLM provider configuration")
public class ProviderConfigAdminController {

    private final ProviderConfigService providerConfigService;

    public ProviderConfigAdminController(ProviderConfigService providerConfigService) {
        this.providerConfigService = providerConfigService;
    }

    @GetMapping
    @Operation(summary = "List provider configurations")
    public List<ProviderConfigResponse> list() {
        return providerConfigService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a provider configuration")
    public ProviderConfigResponse get(@PathVariable Long id) {
        return providerConfigService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a provider configuration")
    public ProviderConfigResponse create(@Valid @RequestBody ProviderConfigRequest request) {
        return providerConfigService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a provider configuration")
    public ProviderConfigResponse update(@PathVariable Long id, @Valid @RequestBody ProviderConfigRequest request) {
        return providerConfigService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a provider configuration")
    public void delete(@PathVariable Long id) {
        providerConfigService.delete(id);
    }

    @PostMapping("/{id}/test-connection")
    @Operation(summary = "Test provider connection")
    public ProviderConnectionTestResponse testConnection(@PathVariable Long id) {
        return providerConfigService.testConnection(id);
    }
}