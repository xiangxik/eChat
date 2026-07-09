package com.xiangxik.echat.chatbot.api.admin;

import com.xiangxik.echat.chatbot.api.dto.ModelConfigRequest;
import com.xiangxik.echat.chatbot.api.dto.ModelConfigResponse;
import com.xiangxik.echat.chatbot.api.dto.ModelGenerationTestResponse;
import com.xiangxik.echat.chatbot.api.dto.ModelOptionResponse;
import com.xiangxik.echat.chatbot.service.AdminListQuery;
import com.xiangxik.echat.chatbot.service.ModelConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/models")
@Tag(name = "Model Configs", description = "Manage model configuration")
public class ModelConfigAdminController {

    private final ModelConfigService modelConfigService;

    public ModelConfigAdminController(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    @GetMapping
    @Operation(summary = "List model configurations")
    public List<ModelConfigResponse> list(@RequestParam Map<String, String> params) {
        return modelConfigService.list(AdminListQuery.from(params));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a model configuration")
    public ModelConfigResponse get(@PathVariable Long id) {
        return modelConfigService.get(id);
    }

    @GetMapping("/options")
    @Operation(summary = "List known model options for a provider")
    public List<ModelOptionResponse> listOptions(@RequestParam Long providerId) {
        return modelConfigService.listOptions(providerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a model configuration")
    public ModelConfigResponse create(@Valid @RequestBody ModelConfigRequest request) {
        return modelConfigService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a model configuration")
    public ModelConfigResponse update(@PathVariable Long id, @Valid @RequestBody ModelConfigRequest request) {
        return modelConfigService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a model configuration")
    public void delete(@PathVariable Long id) {
        modelConfigService.delete(id);
    }

    @PostMapping("/{id}/test-generation")
    @Operation(summary = "Test model generation")
    public ModelGenerationTestResponse testGeneration(@PathVariable Long id) {
        return modelConfigService.testGeneration(id);
    }
}