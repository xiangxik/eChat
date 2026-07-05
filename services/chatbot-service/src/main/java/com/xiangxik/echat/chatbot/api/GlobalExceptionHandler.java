package com.xiangxik.echat.chatbot.api;

import com.xiangxik.echat.chatbot.api.dto.ApiErrorResponse;
import com.xiangxik.echat.chatbot.service.ChatRuntimeException;
import com.xiangxik.echat.chatbot.service.ResourceNotFoundException;
import com.xiangxik.echat.chatbot.service.context.ContextDslException;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(error("NOT_FOUND", ex.getMessage(), request, List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                            HttpServletRequest request) {
        List<ApiErrorResponse.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        return ResponseEntity.badRequest()
                .body(error("VALIDATION_FAILED", "Request validation failed", request, violations));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(error("BAD_REQUEST", ex.getMessage(), request, List.of()));
    }

    @ExceptionHandler(ChatRuntimeException.class)
    public ResponseEntity<ApiErrorResponse> handleChatRuntime(ChatRuntimeException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus())
                .body(error(ex.getCode(), ex.getMessage(), request, List.of()));
    }

    @ExceptionHandler(LlmProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleLlmProvider(LlmProviderException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(error("LLM_PROVIDER_ERROR", ex.getMessage(), request, List.of()));
    }

        @ExceptionHandler(ContextDslException.class)
        public ResponseEntity<ApiErrorResponse> handleContextDsl(ContextDslException ex, HttpServletRequest request) {
        List<ApiErrorResponse.FieldViolation> violations = ex.getErrors().stream()
            .map(error -> new ApiErrorResponse.FieldViolation("dslContent.line" + error.line() + "." + error.tag(),
                error.reason()))
            .toList();
        return ResponseEntity.badRequest()
            .body(error("CONTEXT_DSL_INVALID", "Context policy DSL validation failed", request, violations));
        }

    private ApiErrorResponse error(String code, String message, HttpServletRequest request,
                                   List<ApiErrorResponse.FieldViolation> violations) {
        return new ApiErrorResponse(Instant.now(), code, message, request.getRequestURI(), violations);
    }

    private ApiErrorResponse.FieldViolation toViolation(FieldError fieldError) {
        return new ApiErrorResponse.FieldViolation(fieldError.getField(), fieldError.getDefaultMessage());
    }
}