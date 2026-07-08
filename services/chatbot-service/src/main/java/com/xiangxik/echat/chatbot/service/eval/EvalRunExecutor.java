package com.xiangxik.echat.chatbot.service.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.model.ChatbotWorkflowNode;
import com.xiangxik.echat.chatbot.domain.model.ContextPolicy;
import com.xiangxik.echat.chatbot.domain.model.EvalCase;
import com.xiangxik.echat.chatbot.domain.model.EvalResult;
import com.xiangxik.echat.chatbot.domain.model.EvalRun;
import com.xiangxik.echat.chatbot.domain.model.EvalRunStatus;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotWorkflowNodeRepository;
import com.xiangxik.echat.chatbot.domain.repository.EvalCaseRepository;
import com.xiangxik.echat.chatbot.domain.repository.EvalResultRepository;
import com.xiangxik.echat.chatbot.domain.repository.EvalRunRepository;
import com.xiangxik.echat.chatbot.service.ApiKeyProtector;
import com.xiangxik.echat.chatbot.service.ResourceNotFoundException;
import com.xiangxik.echat.chatbot.service.context.ContextAssemblyRequest;
import com.xiangxik.echat.chatbot.service.context.ContextAssemblyResult;
import com.xiangxik.echat.chatbot.service.context.ContextEngine;
import com.xiangxik.echat.chatbot.service.context.ContextMessage;
import com.xiangxik.echat.chatbot.service.context.ContextPolicyDefinition;
import com.xiangxik.echat.chatbot.service.context.ContextPolicyValidator;
import com.xiangxik.echat.chatbot.service.context.TokenEstimator;
import com.xiangxik.echat.chatbot.service.llm.LlmChatMessage;
import com.xiangxik.echat.chatbot.service.llm.LlmChatRequest;
import com.xiangxik.echat.chatbot.service.llm.LlmChatResponse;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderClient;
import com.xiangxik.echat.chatbot.service.llm.LlmProviderClientRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class EvalRunExecutor {

    private static final Logger log = LoggerFactory.getLogger(EvalRunExecutor.class);

    private final EvalRunRepository evalRunRepository;
    private final EvalCaseRepository evalCaseRepository;
    private final EvalResultRepository evalResultRepository;
    private final ChatbotWorkflowNodeRepository workflowNodeRepository;
    private final ContextPolicyValidator contextPolicyValidator;
    private final ContextEngine contextEngine;
    private final LlmProviderClientRegistry clientRegistry;
    private final ApiKeyProtector apiKeyProtector;
    private final EvalScoringService scoringService;
    private final TokenEstimator tokenEstimator;
    private final ObjectMapper objectMapper;

    public EvalRunExecutor(EvalRunRepository evalRunRepository,
                           EvalCaseRepository evalCaseRepository,
                           EvalResultRepository evalResultRepository,
                           ChatbotWorkflowNodeRepository workflowNodeRepository,
                           ContextPolicyValidator contextPolicyValidator,
                           ContextEngine contextEngine,
                           LlmProviderClientRegistry clientRegistry,
                           ApiKeyProtector apiKeyProtector,
                           EvalScoringService scoringService,
                           TokenEstimator tokenEstimator,
                           ObjectMapper objectMapper) {
        this.evalRunRepository = evalRunRepository;
        this.evalCaseRepository = evalCaseRepository;
        this.evalResultRepository = evalResultRepository;
        this.workflowNodeRepository = workflowNodeRepository;
        this.contextPolicyValidator = contextPolicyValidator;
        this.contextEngine = contextEngine;
        this.clientRegistry = clientRegistry;
        this.apiKeyProtector = apiKeyProtector;
        this.scoringService = scoringService;
        this.tokenEstimator = tokenEstimator;
        this.objectMapper = objectMapper;
    }

    @Async
    @Transactional
    public void execute(Long runId) {
        EvalRun run = evalRunRepository.findById(runId)
            .orElseThrow(() -> new ResourceNotFoundException("Eval run", runId));
        run.setStatus(EvalRunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        evalRunRepository.saveAndFlush(run);

        try {
            RunConfiguration configuration = configuration(run);
            List<EvalCase> cases = evalCaseRepository.findByDatasetIdOrderByIdAsc(run.getDataset().getId());
            int passedCount = 0;
            long totalLatencyMillis = 0L;
            double totalEstimatedCostUsd = 0.0;
            for (EvalCase evalCase : cases) {
                EvalResult result = executeCase(run, evalCase, configuration);
                if (result.isPassed()) {
                    passedCount++;
                }
                Map<String, Object> metrics = map(result.getScores().get("metrics"));
                totalLatencyMillis += longNumber(metrics.get("latencyMillis"));
                totalEstimatedCostUsd += decimal(metrics.get("estimatedCostUsd"));
                evalResultRepository.save(result);
            }
            run.setStatus(EvalRunStatus.COMPLETED);
            run.setFinishedAt(Instant.now());
            run.setSummary(completedSummary(run.getSummary(), cases.size(), passedCount,
                    totalLatencyMillis, totalEstimatedCostUsd));
            evalRunRepository.save(run);
            log.info("audit.eval.run.completed runId={} datasetId={} passed={} total={}", run.getId(),
                    run.getDataset().getId(), passedCount, cases.size());
        } catch (Exception ex) {
            run.setStatus(EvalRunStatus.FAILED);
            run.setFinishedAt(Instant.now());
            run.setSummary(failedSummary(run.getSummary(), ex));
            evalRunRepository.save(run);
            log.warn("audit.eval.run.failed runId={} reason={}", run.getId(), ex.getMessage());
        }
    }

    private EvalResult executeCase(EvalRun run, EvalCase evalCase, RunConfiguration configuration) {
        EvalResult result = new EvalResult();
        result.setRun(run);
        result.setEvalCase(evalCase);
        try {
            List<ContextMessage> goldenConversation = goldenConversation(evalCase, configuration.goldenReplay());
            ContextAssemblyResult contextResult = assembleContext(run, evalCase, configuration.policy(), goldenConversation);
            Instant startedAt = Instant.now();
            LlmChatResponse response = configuration.client().chat(configuration.provider(), configuration.model(),
                    configuration.apiKey(), llmRequest(run, evalCase, contextResult));
            long latencyMillis = Duration.between(startedAt, Instant.now()).toMillis();
            Map<String, Object> metrics = metrics(contextResult, response, latencyMillis,
                configuration.costPer1kTokensUsd());
            EvalScore score = scoringService.score(evalCase, response.content(), contextResult.tokenBudgetReport(),
                configuration.maxEstimatedTokens(), configuration.forbiddenPhrases(),
                mergedRubric(configuration.rubric(), map(evalCase.getMetadata().get("rubric"))), metrics,
                configuration.maxLatencyMillis(), configuration.maxEstimatedCostUsd());
            result.setOutput(response.content());
            result.setContextSnapshot(contextSnapshot(contextResult, evalCase, goldenConversation, configuration.goldenReplay()));
            result.setTokenBudgetReport(toMap(contextResult.tokenBudgetReport()));
            result.setScores(score.scores());
            result.setPassed(score.passed());
        } catch (Exception ex) {
            result.setContextSnapshot(Map.of("caseId", evalCase.getId(), "isolated", true));
            result.setScores(Map.of("error", true));
            result.setPassed(false);
            result.setError(safeErrorMessage(ex));
        }
        return result;
    }

    private ContextAssemblyResult assembleContext(EvalRun run, EvalCase evalCase, ContextPolicyDefinition policy,
                                                  List<ContextMessage> goldenConversation) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("mode", "eval");
        runtime.put("isolated", true);
        runtime.put("runId", run.getId());
        runtime.put("caseId", evalCase.getId());
        runtime.put("goldenReplayMessages", goldenConversation.size());
        runtime.put("timestamp", Instant.now().toString());
        Map<String, Object> metadata = sanitizeMetadata(evalCase.getMetadata());
        return contextEngine.assemble(policy, new ContextAssemblyRequest(run.getChatbot().getId(), null,
                "eval-run-" + run.getId(), evalCase.getInput(), metadata, goldenConversation, List.of(), List.of(),
                Map.of(), List.of(), List.of(), runtime));
    }

    private LlmChatRequest llmRequest(EvalRun run, EvalCase evalCase, ContextAssemblyResult contextResult) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("evalRunId", run.getId());
        metadata.put("evalCaseId", evalCase.getId());
        metadata.put("isolated", true);
        metadata.put("expectedBehavior", evalCase.getExpectedBehavior());
        return new LlmChatRequest(contextResult.messages().stream()
                .map(contextMessage -> new LlmChatMessage(toProviderRole(contextMessage.role()),
                        contextMessage.content(), contextMessage.metadata()))
                .toList(), metadata, "eval-run-" + run.getId() + "-case-" + evalCase.getId(),
                "eval-run-" + run.getId(), Duration.ofSeconds(60));
    }

    private RunConfiguration configuration(EvalRun run) {
        ChatbotConfig chatbot = run.getChatbot();
        ContextPolicy contextPolicy = run.getContextPolicy() == null ? defaultWorkflowPolicy(chatbot) : run.getContextPolicy();
        if (contextPolicy == null || !contextPolicy.isEnabled()) {
            throw new IllegalStateException("Eval run has no enabled context policy");
        }
        ModelConfig model = run.getModel() == null ? contextPolicy.getModel() : run.getModel();
        if (model == null || !model.isEnabled()) {
            throw new IllegalStateException("Eval run has no enabled model");
        }
        ProviderConfig provider = model.getProvider();
        if (provider == null || !provider.isEnabled()) {
            throw new IllegalStateException("Eval run model has no enabled provider");
        }
        ContextPolicyDefinition policy = contextPolicyValidator.validateAndParse(contextPolicy.getDslContent());
        Integer maxEstimatedTokens = number(run.getSummary().get("maxEstimatedTokens"));
        Integer maxLatencyMillis = number(run.getSummary().get("maxLatencyMillis"));
        Double maxEstimatedCostUsd = decimalObject(run.getSummary().get("maxEstimatedCostUsd"));
        Double costPer1kTokensUsd = decimalObject(run.getSummary().get("costPer1kTokensUsd"));
        List<String> forbiddenPhrases = strings(run.getSummary().get("forbiddenPhrases"));
        Map<String, Object> rubric = map(run.getSummary().get("rubric"));
        LlmProviderClient client = clientRegistry.getClient(provider.getType());
        return new RunConfiguration(policy, model, provider, apiKeyProtector.decrypt(provider.getEncryptedApiKey()),
                client, maxEstimatedTokens, maxLatencyMillis, maxEstimatedCostUsd, costPer1kTokensUsd,
                forbiddenPhrases, rubric, booleanValue(run.getSummary().get("goldenReplay"), true));
    }

    private ContextPolicy defaultWorkflowPolicy(ChatbotConfig chatbot) {
        ChatbotWorkflowNode startNode = workflowNodeRepository.findByChatbotIdAndStartTrueAndEnabledTrue(chatbot.getId())
                .orElseThrow(() -> new IllegalStateException("Eval run chatbot has no enabled workflow start node"));
        return startNode.getContextPolicy();
    }

    private Map<String, Object> contextSnapshot(ContextAssemblyResult contextResult, EvalCase evalCase,
                                                List<ContextMessage> goldenConversation, boolean goldenReplay) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("isolated", true);
        snapshot.put("input", evalCase.getInput());
        snapshot.put("goldenReplay", Map.of(
                "enabled", goldenReplay,
                "replayedMessages", goldenConversation.size()
        ));
        snapshot.put("messages", contextResult.messages());
        snapshot.put("sections", contextResult.sections());
        snapshot.put("warnings", contextResult.warnings());
        return toMap(snapshot);
    }

    private Map<String, Object> completedSummary(Map<String, Object> existing, int totalCases, int passedCases,
                                                 long totalLatencyMillis, double totalEstimatedCostUsd) {
        Map<String, Object> summary = new LinkedHashMap<>(existing);
        double passRate = totalCases == 0 ? 0.0 : (double) passedCases / totalCases;
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalLatencyMillis", totalLatencyMillis);
        metrics.put("averageLatencyMillis", totalCases == 0 ? 0.0 : (double) totalLatencyMillis / totalCases);
        metrics.put("totalEstimatedCostUsd", totalEstimatedCostUsd);
        metrics.put("averageEstimatedCostUsd", totalCases == 0 ? 0.0 : totalEstimatedCostUsd / totalCases);
        summary.put("totalCases", totalCases);
        summary.put("passedCases", passedCases);
        summary.put("failedCases", totalCases - passedCases);
        summary.put("passRate", passRate);
        summary.put("metrics", metrics);
        Map<String, Object> releaseGate = releaseGate(summary, totalCases, passedCases, passRate, metrics);
        summary.put("releaseGate", releaseGate);
        summary.put("releaseGatePassed", releaseGate.get("passed"));
        return summary;
    }

    private Map<String, Object> failedSummary(Map<String, Object> existing, Exception ex) {
        Map<String, Object> summary = new LinkedHashMap<>(existing);
        summary.put("error", safeErrorMessage(ex));
        return summary;
    }

    private List<ContextMessage> goldenConversation(EvalCase evalCase, boolean enabled) {
        if (!enabled) {
            return List.of();
        }
        Object value = evalCase.getMetadata().get("goldenConversation");
        if (!(value instanceof List<?> messages)) {
            return List.of();
        }
        List<ContextMessage> replay = new java.util.ArrayList<>();
        for (int index = 0; index < messages.size(); index++) {
            if (!(messages.get(index) instanceof Map<?, ?> message)) {
                continue;
            }
            String content = Objects.toString(message.get("content"), "").strip();
            if (!StringUtils.hasText(content)) {
                continue;
            }
            String role = Objects.toString(message.get("role"), "USER").toUpperCase(Locale.ROOT);
            replay.add(new ContextMessage(role, content, Map.of("goldenReplay", true, "index", index)));
        }
        return List.copyOf(replay);
    }

    private Map<String, Object> metrics(ContextAssemblyResult contextResult, LlmChatResponse response,
                                        long latencyMillis, Double costPer1kTokensUsd) {
        Map<String, Object> responseMetadata = response.metadata() == null ? Map.of() : response.metadata();
        int promptTokens = contextResult.tokenBudgetReport().totalEstimatedTokens();
        int outputTokens = firstNumber(responseMetadata, "outputTokens", "completionTokens")
                .map(Number::intValue)
                .orElseGet(() -> tokenEstimator.estimate(response.content()));
        int totalTokens = firstNumber(responseMetadata, "totalTokens")
                .map(Number::intValue)
                .orElse(promptTokens + outputTokens);
        double estimatedCostUsd = firstNumber(responseMetadata, "costUsd", "estimatedCostUsd")
                .map(Number::doubleValue)
                .orElse(costPer1kTokensUsd == null ? 0.0 : totalTokens / 1000.0 * costPer1kTokensUsd);
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("latencyMillis", latencyMillis);
        metrics.put("promptEstimatedTokens", promptTokens);
        metrics.put("outputEstimatedTokens", outputTokens);
        metrics.put("totalEstimatedTokens", totalTokens);
        metrics.put("estimatedCostUsd", estimatedCostUsd);
        metrics.put("finishReason", response.finishReason());
        return metrics;
    }

    private Map<String, Object> mergedRubric(Map<String, Object> runRubric, Map<String, Object> caseRubric) {
        if (caseRubric.isEmpty()) {
            return runRubric;
        }
        Map<String, Object> merged = new LinkedHashMap<>(runRubric);
        merged.putAll(caseRubric);
        return merged;
    }

    private Map<String, Object> releaseGate(Map<String, Object> summary, int totalCases, int passedCases,
                                            double passRate, Map<String, Object> metrics) {
        Map<String, Object> configured = map(summary.get("releaseGate"));
        if (configured.isEmpty()) {
            return Map.of("configured", false, "passed", true);
        }
        List<Map<String, Object>> checks = new java.util.ArrayList<>();
        addGateCheck(checks, "minPassRate", configured.get("minPassRate"), passRate >= decimal(configured.get("minPassRate")), passRate);
        addGateCheck(checks, "minPassedCases", configured.get("minPassedCases"), passedCases >= longNumber(configured.get("minPassedCases")), passedCases);
        addGateCheck(checks, "maxFailedCases", configured.get("maxFailedCases"), totalCases - passedCases <= longNumber(configured.get("maxFailedCases")), totalCases - passedCases);
        addGateCheck(checks, "maxAverageLatencyMillis", configured.get("maxAverageLatencyMillis"),
                decimal(metrics.get("averageLatencyMillis")) <= decimal(configured.get("maxAverageLatencyMillis")),
                metrics.get("averageLatencyMillis"));
        addGateCheck(checks, "maxEstimatedCostUsd", configured.get("maxEstimatedCostUsd"),
                decimal(metrics.get("totalEstimatedCostUsd")) <= decimal(configured.get("maxEstimatedCostUsd")),
                metrics.get("totalEstimatedCostUsd"));
        boolean passed = checks.stream().allMatch(check -> Boolean.TRUE.equals(check.get("passed")));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("configured", true);
        result.put("passed", passed);
        result.put("checks", checks);
        return result;
    }

    private void addGateCheck(List<Map<String, Object>> checks, String name, Object threshold,
                              boolean passed, Object actual) {
        if (threshold == null) {
            return;
        }
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("name", name);
        check.put("threshold", threshold);
        check.put("actual", actual);
        check.put("passed", passed);
        checks.add(check);
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
            if (List.of("goldenconversation", "rubric").contains(normalized)) {
                return;
            }
            if (normalized.contains("apikey") || normalized.contains("api_key") || normalized.contains("secret")
                    || normalized.contains("password") || normalized.contains("token")) {
                sanitized.put(key, "[FILTERED]");
            } else {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    private String toProviderRole(String role) {
        return Objects.toString(role, "user").toLowerCase(Locale.ROOT);
    }

    private Integer number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private java.util.Optional<Number> firstNumber(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            Object value = source.get(key);
            if (value instanceof Number number) {
                return java.util.Optional.of(number);
            }
        }
        return java.util.Optional.empty();
    }

    private Double decimalObject(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private long longNumber(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private boolean booleanValue(Object value, boolean defaultValue) {
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, mapValue) -> result.put(Objects.toString(key), mapValue));
        return result;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(Objects::toString).filter(StringUtils::hasText).toList();
    }

    private Map<String, Object> toMap(Object value) {
        return objectMapper.convertValue(value, new TypeReference<>() {
        });
    }

    private String safeErrorMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "Eval case failed";
    }

    private record RunConfiguration(
            ContextPolicyDefinition policy,
            ModelConfig model,
            ProviderConfig provider,
            String apiKey,
            LlmProviderClient client,
            Integer maxEstimatedTokens,
            Integer maxLatencyMillis,
            Double maxEstimatedCostUsd,
            Double costPer1kTokensUsd,
            List<String> forbiddenPhrases,
            Map<String, Object> rubric,
            boolean goldenReplay
    ) {
    }
}
