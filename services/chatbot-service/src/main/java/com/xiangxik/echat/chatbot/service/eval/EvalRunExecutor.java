package com.xiangxik.echat.chatbot.service.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.model.ContextPolicy;
import com.xiangxik.echat.chatbot.domain.model.EvalCase;
import com.xiangxik.echat.chatbot.domain.model.EvalResult;
import com.xiangxik.echat.chatbot.domain.model.EvalRun;
import com.xiangxik.echat.chatbot.domain.model.EvalRunStatus;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.model.ProviderConfig;
import com.xiangxik.echat.chatbot.domain.repository.EvalCaseRepository;
import com.xiangxik.echat.chatbot.domain.repository.EvalResultRepository;
import com.xiangxik.echat.chatbot.domain.repository.EvalRunRepository;
import com.xiangxik.echat.chatbot.service.ApiKeyProtector;
import com.xiangxik.echat.chatbot.service.ResourceNotFoundException;
import com.xiangxik.echat.chatbot.service.context.ContextAssemblyRequest;
import com.xiangxik.echat.chatbot.service.context.ContextAssemblyResult;
import com.xiangxik.echat.chatbot.service.context.ContextEngine;
import com.xiangxik.echat.chatbot.service.context.ContextPolicyDefinition;
import com.xiangxik.echat.chatbot.service.context.ContextPolicyValidator;
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
    private final ContextPolicyValidator contextPolicyValidator;
    private final ContextEngine contextEngine;
    private final LlmProviderClientRegistry clientRegistry;
    private final ApiKeyProtector apiKeyProtector;
    private final EvalScoringService scoringService;
    private final ObjectMapper objectMapper;

    public EvalRunExecutor(EvalRunRepository evalRunRepository,
                           EvalCaseRepository evalCaseRepository,
                           EvalResultRepository evalResultRepository,
                           ContextPolicyValidator contextPolicyValidator,
                           ContextEngine contextEngine,
                           LlmProviderClientRegistry clientRegistry,
                           ApiKeyProtector apiKeyProtector,
                           EvalScoringService scoringService,
                           ObjectMapper objectMapper) {
        this.evalRunRepository = evalRunRepository;
        this.evalCaseRepository = evalCaseRepository;
        this.evalResultRepository = evalResultRepository;
        this.contextPolicyValidator = contextPolicyValidator;
        this.contextEngine = contextEngine;
        this.clientRegistry = clientRegistry;
        this.apiKeyProtector = apiKeyProtector;
        this.scoringService = scoringService;
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
            for (EvalCase evalCase : cases) {
                EvalResult result = executeCase(run, evalCase, configuration);
                if (result.isPassed()) {
                    passedCount++;
                }
                evalResultRepository.save(result);
            }
            run.setStatus(EvalRunStatus.COMPLETED);
            run.setFinishedAt(Instant.now());
            run.setSummary(completedSummary(run.getSummary(), cases.size(), passedCount));
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
            ContextAssemblyResult contextResult = assembleContext(run, evalCase, configuration.policy());
            LlmChatResponse response = configuration.client().chat(configuration.provider(), configuration.model(),
                    configuration.apiKey(), llmRequest(run, evalCase, contextResult));
            EvalScore score = scoringService.score(evalCase, response.content(), contextResult.tokenBudgetReport(),
                    configuration.maxEstimatedTokens(), configuration.forbiddenPhrases());
            result.setOutput(response.content());
            result.setContextSnapshot(contextSnapshot(contextResult, evalCase));
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

    private ContextAssemblyResult assembleContext(EvalRun run, EvalCase evalCase, ContextPolicyDefinition policy) {
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("mode", "eval");
        runtime.put("isolated", true);
        runtime.put("runId", run.getId());
        runtime.put("caseId", evalCase.getId());
        runtime.put("timestamp", Instant.now().toString());
        Map<String, Object> metadata = sanitizeMetadata(evalCase.getMetadata());
        return contextEngine.assemble(policy, new ContextAssemblyRequest(run.getChatbot().getId(), null,
                "eval-run-" + run.getId(), evalCase.getInput(), metadata, List.of(), List.of(), List.of(),
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
        ContextPolicy contextPolicy = run.getContextPolicy() == null ? chatbot.getContextPolicy() : run.getContextPolicy();
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
        List<String> forbiddenPhrases = strings(run.getSummary().get("forbiddenPhrases"));
        LlmProviderClient client = clientRegistry.getClient(provider.getType());
        return new RunConfiguration(policy, model, provider, apiKeyProtector.decrypt(provider.getEncryptedApiKey()),
                client, maxEstimatedTokens, forbiddenPhrases);
    }

    private Map<String, Object> contextSnapshot(ContextAssemblyResult contextResult, EvalCase evalCase) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("isolated", true);
        snapshot.put("input", evalCase.getInput());
        snapshot.put("messages", contextResult.messages());
        snapshot.put("sections", contextResult.sections());
        snapshot.put("warnings", contextResult.warnings());
        return toMap(snapshot);
    }

    private Map<String, Object> completedSummary(Map<String, Object> existing, int totalCases, int passedCases) {
        Map<String, Object> summary = new LinkedHashMap<>(existing);
        summary.put("totalCases", totalCases);
        summary.put("passedCases", passedCases);
        summary.put("failedCases", totalCases - passedCases);
        summary.put("passRate", totalCases == 0 ? 0.0 : (double) passedCases / totalCases);
        return summary;
    }

    private Map<String, Object> failedSummary(Map<String, Object> existing, Exception ex) {
        Map<String, Object> summary = new LinkedHashMap<>(existing);
        summary.put("error", safeErrorMessage(ex));
        return summary;
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT);
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
            List<String> forbiddenPhrases
    ) {
    }
}
