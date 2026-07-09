package com.xiangxik.echat.chatbot.api.admin;

import com.xiangxik.echat.chatbot.api.dto.ChatbotConfigRequest;
import com.xiangxik.echat.chatbot.api.dto.ChatbotConfigResponse;
import com.xiangxik.echat.chatbot.service.AdminListQuery;
import com.xiangxik.echat.chatbot.service.ChatbotConfigService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/chatbots")
@Tag(name = "Chatbot Configs", description = "Manage chatbot configuration")
public class ChatbotConfigAdminController {

    private final ChatbotConfigService chatbotConfigService;

    public ChatbotConfigAdminController(ChatbotConfigService chatbotConfigService) {
        this.chatbotConfigService = chatbotConfigService;
    }

    @GetMapping
    @Operation(summary = "List chatbot configurations")
    public List<ChatbotConfigResponse> list(@RequestParam Map<String, String> params) {
        return chatbotConfigService.list(AdminListQuery.from(params));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a chatbot configuration")
    public ChatbotConfigResponse get(@PathVariable Long id) {
        return chatbotConfigService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a chatbot configuration")
    public ChatbotConfigResponse create(@Valid @RequestBody ChatbotConfigRequest request) {
        return chatbotConfigService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a chatbot configuration")
    public ChatbotConfigResponse update(@PathVariable Long id, @Valid @RequestBody ChatbotConfigRequest request) {
        return chatbotConfigService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a chatbot configuration")
    public void delete(@PathVariable Long id) {
        chatbotConfigService.delete(id);
    }
}