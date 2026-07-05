package com.xiangxik.echat.chatbot.api.admin;

import com.xiangxik.echat.chatbot.api.dto.MemoryItemRequest;
import com.xiangxik.echat.chatbot.api.dto.MemoryItemResponse;
import com.xiangxik.echat.chatbot.api.dto.MemorySearchRequest;
import com.xiangxik.echat.chatbot.api.dto.MemorySearchResponse;
import com.xiangxik.echat.chatbot.domain.model.MemoryScope;
import com.xiangxik.echat.chatbot.service.MemoryService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/memories")
@Tag(name = "Memories", description = "Manage chatbot memory items")
public class MemoryAdminController {

    private final MemoryService memoryService;

    public MemoryAdminController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping
    @Operation(summary = "List memory items")
    public List<MemoryItemResponse> list(@RequestParam Long chatbotId,
                                         @RequestParam(required = false) MemoryScope scope,
                                         @RequestParam(required = false) String userId) {
        return memoryService.list(chatbotId, scope, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a memory item")
    public MemoryItemResponse create(@Valid @RequestBody MemoryItemRequest request) {
        return memoryService.create(request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a memory item")
    public MemoryItemResponse update(@PathVariable Long id, @Valid @RequestBody MemoryItemRequest request) {
        return memoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a memory item")
    public void delete(@PathVariable Long id) {
        memoryService.delete(id);
    }

    @PostMapping("/search")
    @Operation(summary = "Vector search long-term memory")
    public List<MemorySearchResponse> search(@Valid @RequestBody MemorySearchRequest request) {
        return memoryService.search(request.chatbotId(), request.userId(), request.query(), request.topK(),
                request.minScore());
    }
}