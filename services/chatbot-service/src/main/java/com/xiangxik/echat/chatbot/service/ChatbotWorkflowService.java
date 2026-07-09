package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.api.dto.ChatbotWorkflowNodeRequest;
import com.xiangxik.echat.chatbot.api.dto.ChatbotWorkflowNodeResponse;
import com.xiangxik.echat.chatbot.api.dto.ChatbotWorkflowRequest;
import com.xiangxik.echat.chatbot.api.dto.ChatbotWorkflowResponse;
import com.xiangxik.echat.chatbot.api.dto.ChatbotWorkflowTransitionRequest;
import com.xiangxik.echat.chatbot.api.dto.ChatbotWorkflowTransitionResponse;
import com.xiangxik.echat.chatbot.api.dto.ChatbotWorkflowValidationResponse;
import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.model.ChatbotWorkflowNode;
import com.xiangxik.echat.chatbot.domain.model.ChatbotWorkflowTransition;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotWorkflowNodeRepository;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotWorkflowTransitionRepository;
import com.xiangxik.echat.chatbot.domain.repository.ModelConfigRepository;
import com.xiangxik.echat.chatbot.service.context.ContextPolicyValidationResult;
import com.xiangxik.echat.chatbot.service.context.ContextPolicyValidator;
import com.xiangxik.echat.chatbot.service.workflow.WorkflowTransitionEvaluator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChatbotWorkflowService {

    public static final String START_NODE_KEY = "Start";
    public static final String DEFAULT_START_NODE_DSL = """
            <contextPolicy name="default-welcome" maxTokens="512">
                <system priority="100">Reply with exactly: Welcome! How can I help you today?</system>
                <output>
                    <section name="system" />
                </output>
            </contextPolicy>
            """;

    private final ChatbotConfigRepository chatbotConfigRepository;
    private final ChatbotWorkflowNodeRepository nodeRepository;
    private final ChatbotWorkflowTransitionRepository transitionRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final ContextPolicyValidator contextPolicyValidator;
    private final WorkflowTransitionEvaluator transitionEvaluator;
    private final AuditLogService auditLogService;
    private final TenantService tenantService;

    public ChatbotWorkflowService(ChatbotConfigRepository chatbotConfigRepository,
                                  ChatbotWorkflowNodeRepository nodeRepository,
                                  ChatbotWorkflowTransitionRepository transitionRepository,
                                  ModelConfigRepository modelConfigRepository,
                                  ContextPolicyValidator contextPolicyValidator,
                                  WorkflowTransitionEvaluator transitionEvaluator,
                                  AuditLogService auditLogService,
                                  TenantService tenantService) {
        this.chatbotConfigRepository = chatbotConfigRepository;
        this.nodeRepository = nodeRepository;
        this.transitionRepository = transitionRepository;
        this.modelConfigRepository = modelConfigRepository;
        this.contextPolicyValidator = contextPolicyValidator;
        this.transitionEvaluator = transitionEvaluator;
        this.auditLogService = auditLogService;
        this.tenantService = tenantService;
    }

    @Transactional(readOnly = true)
    public ChatbotWorkflowResponse get(Long chatbotId) {
        findChatbot(chatbotId);
        return toResponse(chatbotId);
    }

    @Transactional(readOnly = true)
    public ChatbotWorkflowValidationResponse validate(Long chatbotId, ChatbotWorkflowRequest request) {
        findChatbot(chatbotId);
        return validateRequest(withRequiredStartNode(request));
    }

    @Transactional
    public ChatbotWorkflowResponse save(Long chatbotId, ChatbotWorkflowRequest request) {
        ChatbotConfig chatbot = findChatbot(chatbotId);
        ChatbotWorkflowRequest normalizedRequest = withRequiredStartNode(request);
        ChatbotWorkflowValidationResponse validation = validateRequest(normalizedRequest);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Invalid chatbot workflow: " + String.join("; ", validation.errors()));
        }

        List<ChatbotWorkflowNode> existingNodes = nodeRepository.findByChatbotIdOrderByNodeKeyAsc(chatbotId);
        Map<String, ChatbotWorkflowNode> reusableNodes = existingNodes.stream()
                .collect(Collectors.toMap(ChatbotWorkflowNode::getNodeKey, Function.identity(), (first, second) -> first, LinkedHashMap::new));

        List<ChatbotWorkflowTransition> existingTransitions = transitionRepository.findByChatbotIdOrderByFromNodeNodeKeyAscPriorityAscIdAsc(chatbotId);
        transitionRepository.deleteAll(existingTransitions);

        Map<Long, ModelConfig> models = modelConfigRepository.findByProviderTenantIdOrderByDisplayNameAsc(
                        tenantService.currentTenantId()).stream()
                .filter(model -> normalizedRequest.nodes().stream()
            .map(ChatbotWorkflowNodeRequest::modelId)
                .filter(Objects::nonNull)
                        .collect(Collectors.toSet()).contains(model.getId()))
            .collect(Collectors.toMap(ModelConfig::getId, Function.identity()));

        Map<String, ChatbotWorkflowNode> savedNodes = new LinkedHashMap<>();
        for (ChatbotWorkflowNodeRequest nodeRequest : normalizedRequest.nodes()) {
            String nodeKey = normalizeKey(nodeRequest.nodeKey());
            ChatbotWorkflowNode node = reusableNodes.remove(nodeKey);
            if (node == null) {
                node = new ChatbotWorkflowNode();
                node.setChatbot(chatbot);
                node.setNodeKey(nodeKey);
            }
            node.setName(nodeRequest.name().strip());
            node.setDescription(blankToNull(nodeRequest.description()));
            node.setDslContent(nodeRequest.dslContent().strip());
            node.setVersion(nodeRequest.version() == null ? 1 : nodeRequest.version());
            node.setModel(nodeRequest.modelId() == null ? null : models.get(nodeRequest.modelId()));
            node.setEnabled(nodeRequest.enabled() == null || nodeRequest.enabled());
            node.setStart(Boolean.TRUE.equals(nodeRequest.start()));
            node.setMetadata(copyMap(nodeRequest.metadata()));
            savedNodes.put(nodeKey, nodeRepository.save(node));
        }

        if (!reusableNodes.isEmpty()) {
            nodeRepository.deleteAll(reusableNodes.values());
        }

        List<ChatbotWorkflowTransition> transitions = new ArrayList<>();
        for (ChatbotWorkflowTransitionRequest transitionRequest : normalizedRequest.transitions()) {
            ChatbotWorkflowTransition transition = new ChatbotWorkflowTransition();
            transition.setChatbot(chatbot);
            transition.setName(transitionRequest.name().strip());
            transition.setFromNode(savedNodes.get(normalizeKey(transitionRequest.fromNodeKey())));
            transition.setToNode(savedNodes.get(normalizeKey(transitionRequest.toNodeKey())));
            transition.setPriority(transitionRequest.priority() == null ? 0 : transitionRequest.priority());
            transition.setEnabled(transitionRequest.enabled() == null || transitionRequest.enabled());
            transition.setConditionExpression(transitionRequest.conditionExpression().strip());
            transition.setMetadata(copyMap(transitionRequest.metadata()));
            transitions.add(transition);
        }
        transitionRepository.saveAll(transitions);

        auditLogService.recordAdmin("CHATBOT_WORKFLOW_UPDATED", "ChatbotConfig", chatbotId,
                Map.of("nodeCount", savedNodes.size(), "transitionCount", transitions.size()));
        return toResponse(chatbotId);
    }

    private ChatbotWorkflowValidationResponse validateRequest(ChatbotWorkflowRequest request) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (request == null) {
            return new ChatbotWorkflowValidationResponse(false, List.of("Workflow request is required"), warnings);
        }

        List<ChatbotWorkflowNodeRequest> nodes = request.nodes() == null ? List.of() : request.nodes();
        List<ChatbotWorkflowTransitionRequest> transitions = request.transitions() == null ? List.of() : request.transitions();
        if (nodes.isEmpty()) {
            errors.add("At least one workflow node is required");
        }
        boolean hasStartNode = nodes.stream().anyMatch(node -> START_NODE_KEY.equals(normalizeKey(node.nodeKey())));
        if (!hasStartNode) {
            errors.add("Workflow must include the built-in Start node");
        }

        Map<String, ChatbotWorkflowNodeRequest> nodesByKey = new LinkedHashMap<>();
        int enabledStartCount = 0;
        Map<Long, ModelConfig> models = modelConfigRepository.findByProviderTenantIdOrderByDisplayNameAsc(
                        tenantService.currentTenantId()).stream()
                .filter(model -> nodes.stream()
            .map(ChatbotWorkflowNodeRequest::modelId)
                .filter(Objects::nonNull)
                        .collect(Collectors.toSet()).contains(model.getId()))
            .collect(Collectors.toMap(ModelConfig::getId, Function.identity()));
        for (ChatbotWorkflowNodeRequest node : nodes) {
            String nodeKey = normalizeKey(node.nodeKey());
            if (!StringUtils.hasText(nodeKey)) {
                errors.add("Node key is required");
                continue;
            }
            if (nodesByKey.put(nodeKey, node) != null) {
                errors.add("Duplicate node key: " + nodeKey);
            }
            boolean enabled = node.enabled() == null || node.enabled();
            if (START_NODE_KEY.equals(nodeKey)) {
                if (!enabled) {
                    errors.add("Start node cannot be disabled");
                }
                if (!Boolean.TRUE.equals(node.start())) {
                    errors.add("Start node must be the workflow start node");
                }
                if (node.modelId() != null) {
                    errors.add("Start node cannot use a model");
                }
            }
            if (enabled && Boolean.TRUE.equals(node.start())) {
                enabledStartCount++;
            }
            if (!StringUtils.hasText(node.dslContent())) {
                errors.add("Node " + nodeKey + " requires context DSL content");
            } else {
                ContextPolicyValidationResult policyValidation = contextPolicyValidator.validate(node.dslContent());
                if (!policyValidation.valid()) {
                    errors.add("Node " + nodeKey + " has invalid context DSL");
                }
            }
            if (node.version() != null && node.version() <= 0) {
                errors.add("Node " + nodeKey + " version must be greater than 0");
            }
            if (node.modelId() != null) {
                ModelConfig model = models.get(node.modelId());
                if (model == null) {
                    errors.add("Node " + nodeKey + " references missing model: " + node.modelId());
                } else if (enabled && !model.isEnabled()) {
                    errors.add("Node " + nodeKey + " references a disabled model: " + node.modelId());
                }
            }
        }
        if (enabledStartCount != 1) {
            errors.add("Workflow must have exactly one enabled start node");
        }

        Map<String, Integer> prioritiesBySourceAndPriority = new HashMap<>();
        for (ChatbotWorkflowTransitionRequest transition : transitions) {
            String fromKey = normalizeKey(transition.fromNodeKey());
            String toKey = normalizeKey(transition.toNodeKey());
            ChatbotWorkflowNodeRequest fromNode = nodesByKey.get(fromKey);
            ChatbotWorkflowNodeRequest toNode = nodesByKey.get(toKey);
            if (fromNode == null) {
                errors.add("Transition " + transition.name() + " references missing from node: " + fromKey);
            }
            if (toNode == null) {
                errors.add("Transition " + transition.name() + " references missing to node: " + toKey);
            }
            boolean enabled = transition.enabled() == null || transition.enabled();
            if (enabled && fromNode != null && Boolean.FALSE.equals(fromNode.enabled())) {
                errors.add("Enabled transition " + transition.name() + " starts from disabled node: " + fromKey);
            }
            if (enabled && toNode != null && Boolean.FALSE.equals(toNode.enabled())) {
                errors.add("Enabled transition " + transition.name() + " targets disabled node: " + toKey);
            }
            int priority = transition.priority() == null ? 0 : transition.priority();
            String priorityKey = fromKey + ":" + priority;
            int count = prioritiesBySourceAndPriority.merge(priorityKey, 1, Integer::sum);
            if (count > 1) {
                warnings.add("Multiple transitions from " + fromKey + " share priority " + priority + "; id order will break ties");
            }
            try {
                transitionEvaluator.validateExpression(transition.conditionExpression());
            } catch (IllegalArgumentException ex) {
                errors.add("Transition " + transition.name() + " has invalid condition: " + ex.getMessage());
            }
        }
        return new ChatbotWorkflowValidationResponse(errors.isEmpty(), List.copyOf(errors), List.copyOf(warnings));
    }

    private ChatbotConfig findChatbot(Long chatbotId) {
        return chatbotConfigRepository.findByTenantIdAndId(tenantService.currentTenantId(), chatbotId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatbotConfig", chatbotId));
    }

    private ChatbotWorkflowRequest withRequiredStartNode(ChatbotWorkflowRequest request) {
        List<ChatbotWorkflowNodeRequest> nodes = new ArrayList<>(request == null || request.nodes() == null ? List.of() : request.nodes());
        List<ChatbotWorkflowTransitionRequest> transitions = request == null || request.transitions() == null ? List.of() : request.transitions();
        int startIndex = -1;
        for (int index = 0; index < nodes.size(); index++) {
            if (START_NODE_KEY.equals(normalizeKey(nodes.get(index).nodeKey()))) {
                startIndex = index;
                break;
            }
        }
        ChatbotWorkflowNodeRequest existingStart = startIndex >= 0 ? nodes.get(startIndex) : null;
        ChatbotWorkflowNodeRequest startNode = new ChatbotWorkflowNodeRequest(START_NODE_KEY,
                existingStart == null ? START_NODE_KEY : existingStart.name(),
                existingStart == null ? "Built-in workflow entry node" : existingStart.description(),
                existingStart == null ? DEFAULT_START_NODE_DSL : existingStart.dslContent(),
                existingStart == null ? 1 : existingStart.version(),
                null,
                existingStart == null || existingStart.enabled() == null || existingStart.enabled(),
                existingStart == null ? true : existingStart.start(),
                existingStart == null ? Map.of("x", 56, "y", 64) : existingStart.metadata());
        if (startIndex >= 0) {
            nodes.set(startIndex, startNode);
        } else {
            nodes.add(0, startNode);
        }
        return new ChatbotWorkflowRequest(nodes, transitions);
    }

    private ChatbotWorkflowResponse toResponse(Long chatbotId) {
        List<ChatbotWorkflowNodeResponse> nodes = nodeRepository.findByChatbotIdOrderByNodeKeyAsc(chatbotId).stream()
                .map(this::toNodeResponse)
                .toList();
        List<ChatbotWorkflowTransitionResponse> transitions = transitionRepository.findByChatbotIdOrderByFromNodeNodeKeyAscPriorityAscIdAsc(chatbotId).stream()
                .map(this::toTransitionResponse)
                .toList();
        return new ChatbotWorkflowResponse(chatbotId, nodes, transitions);
    }

    private ChatbotWorkflowNodeResponse toNodeResponse(ChatbotWorkflowNode node) {
        Long modelId = node.getModel() == null ? null : node.getModel().getId();
        return new ChatbotWorkflowNodeResponse(node.getId(), node.getNodeKey(), node.getName(), node.getDescription(),
            node.getDslContent(), node.getVersion(), modelId, node.isEnabled(), node.isStart(), node.getMetadata(),
                node.getCreatedAt(), node.getUpdatedAt());
    }

    private ChatbotWorkflowTransitionResponse toTransitionResponse(ChatbotWorkflowTransition transition) {
        return new ChatbotWorkflowTransitionResponse(transition.getId(), transition.getName(),
                transition.getFromNode().getNodeKey(), transition.getToNode().getNodeKey(), transition.getPriority(),
                transition.isEnabled(), transition.getConditionExpression(), transition.getMetadata(),
                transition.getCreatedAt(), transition.getUpdatedAt());
    }

    private String normalizeKey(String value) {
        return value == null ? "" : value.strip();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private Map<String, Object> copyMap(Map<String, Object> value) {
        return value == null ? new LinkedHashMap<>() : new LinkedHashMap<>(value);
    }
}