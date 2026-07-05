package com.xiangxik.echat.chatbot.api.admin;

import com.xiangxik.echat.chatbot.api.dto.ContextPolicyRequest;
import com.xiangxik.echat.chatbot.api.dto.ContextPolicyResponse;
import com.xiangxik.echat.chatbot.api.dto.ContextPolicyValidateRequest;
import com.xiangxik.echat.chatbot.api.dto.ContextPolicyValidationResponse;
import com.xiangxik.echat.chatbot.service.ContextPolicyService;
import com.xiangxik.echat.chatbot.service.context.ContextAssemblyRequest;
import com.xiangxik.echat.chatbot.service.context.ContextAssemblyResult;
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
@RequestMapping("/api/admin/context-policies")
@Tag(name = "Context Policies", description = "Manage declarative context policies")
public class ContextPolicyAdminController {

    private final ContextPolicyService contextPolicyService;

    public ContextPolicyAdminController(ContextPolicyService contextPolicyService) {
        this.contextPolicyService = contextPolicyService;
    }

    @GetMapping
    @Operation(summary = "List context policies")
    public List<ContextPolicyResponse> list() {
        return contextPolicyService.list();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a context policy")
    public ContextPolicyResponse get(@PathVariable Long id) {
        return contextPolicyService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a context policy")
    public ContextPolicyResponse create(@Valid @RequestBody ContextPolicyRequest request) {
        return contextPolicyService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a context policy")
    public ContextPolicyResponse update(@PathVariable Long id, @Valid @RequestBody ContextPolicyRequest request) {
        return contextPolicyService.update(id, request);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate context policy DSL")
    public ContextPolicyValidationResponse validate(@Valid @RequestBody ContextPolicyValidateRequest request) {
        return contextPolicyService.validate(request.dslContent());
    }

    @PostMapping("/{id}/preview")
    @Operation(summary = "Preview assembled context for a policy")
    public ContextAssemblyResult preview(@PathVariable Long id, @RequestBody ContextAssemblyRequest request) {
        return contextPolicyService.preview(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a context policy")
    public void delete(@PathVariable Long id) {
        contextPolicyService.delete(id);
    }
}