package com.xiangxik.echat.chatbot.api.admin;

import com.xiangxik.echat.chatbot.api.dto.ChatbotWorkflowRequest;
import com.xiangxik.echat.chatbot.api.dto.ChatbotWorkflowResponse;
import com.xiangxik.echat.chatbot.api.dto.ChatbotWorkflowValidationResponse;
import com.xiangxik.echat.chatbot.service.ChatbotWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/chatbots/{chatbotId}/workflow")
@Tag(name = "Chatbot Workflows", description = "Manage per-chatbot workflow state machines")
public class ChatbotWorkflowAdminController {

    private final ChatbotWorkflowService chatbotWorkflowService;

    public ChatbotWorkflowAdminController(ChatbotWorkflowService chatbotWorkflowService) {
        this.chatbotWorkflowService = chatbotWorkflowService;
    }

    @GetMapping
    @Operation(summary = "Get chatbot workflow")
    public ChatbotWorkflowResponse get(@PathVariable Long chatbotId) {
        return chatbotWorkflowService.get(chatbotId);
    }

    @PutMapping
    @Operation(summary = "Save chatbot workflow")
    public ChatbotWorkflowResponse save(@PathVariable Long chatbotId,
                                        @Valid @RequestBody ChatbotWorkflowRequest request) {
        return chatbotWorkflowService.save(chatbotId, request);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate chatbot workflow")
    public ChatbotWorkflowValidationResponse validate(@PathVariable Long chatbotId,
                                                      @Valid @RequestBody ChatbotWorkflowRequest request) {
        return chatbotWorkflowService.validate(chatbotId, request);
    }
}