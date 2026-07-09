package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.api.dto.EvalCaseRequest;
import com.xiangxik.echat.chatbot.api.dto.EvalCaseResponse;
import com.xiangxik.echat.chatbot.api.dto.EvalDatasetRequest;
import com.xiangxik.echat.chatbot.api.dto.EvalDatasetResponse;
import com.xiangxik.echat.chatbot.api.dto.EvalResultResponse;
import com.xiangxik.echat.chatbot.api.dto.EvalRunRequest;
import com.xiangxik.echat.chatbot.api.dto.EvalRunResponse;
import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.model.ChatbotWorkflowNode;
import com.xiangxik.echat.chatbot.domain.model.EvalCase;
import com.xiangxik.echat.chatbot.domain.model.EvalDataset;
import com.xiangxik.echat.chatbot.domain.model.EvalResult;
import com.xiangxik.echat.chatbot.domain.model.EvalRun;
import com.xiangxik.echat.chatbot.domain.model.EvalRunStatus;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotWorkflowNodeRepository;
import com.xiangxik.echat.chatbot.domain.repository.EvalCaseRepository;
import com.xiangxik.echat.chatbot.domain.repository.EvalDatasetRepository;
import com.xiangxik.echat.chatbot.domain.repository.EvalResultRepository;
import com.xiangxik.echat.chatbot.domain.repository.EvalRunRepository;
import com.xiangxik.echat.chatbot.domain.repository.ModelConfigRepository;
import com.xiangxik.echat.chatbot.service.eval.EvalRunExecutor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class EvalService {

    private final EvalDatasetRepository evalDatasetRepository;
    private final EvalCaseRepository evalCaseRepository;
    private final EvalRunRepository evalRunRepository;
    private final EvalResultRepository evalResultRepository;
    private final ChatbotConfigRepository chatbotConfigRepository;
    private final ChatbotWorkflowNodeRepository workflowNodeRepository;
    private final ModelConfigRepository modelConfigRepository;
    private final EvalRunExecutor evalRunExecutor;
    private final TenantService tenantService;

    public EvalService(EvalDatasetRepository evalDatasetRepository,
                       EvalCaseRepository evalCaseRepository,
                       EvalRunRepository evalRunRepository,
                       EvalResultRepository evalResultRepository,
                       ChatbotConfigRepository chatbotConfigRepository,
                       ChatbotWorkflowNodeRepository workflowNodeRepository,
                       ModelConfigRepository modelConfigRepository,
                       EvalRunExecutor evalRunExecutor,
                       TenantService tenantService) {
        this.evalDatasetRepository = evalDatasetRepository;
        this.evalCaseRepository = evalCaseRepository;
        this.evalRunRepository = evalRunRepository;
        this.evalResultRepository = evalResultRepository;
        this.chatbotConfigRepository = chatbotConfigRepository;
        this.workflowNodeRepository = workflowNodeRepository;
        this.modelConfigRepository = modelConfigRepository;
        this.evalRunExecutor = evalRunExecutor;
        this.tenantService = tenantService;
    }

    @Transactional(readOnly = true)
    public List<EvalDatasetResponse> listDatasets() {
        return listDatasets(AdminListQuery.empty());
        }

        @Transactional(readOnly = true)
        public List<EvalDatasetResponse> listDatasets(AdminListQuery query) {
        return evalDatasetRepository.findByTenantIdOrderByUpdatedAtDesc(tenantService.currentTenantId()).stream()
                .map(this::toDatasetResponse)
            .collect(java.util.stream.Collectors.collectingAndThen(java.util.stream.Collectors.toList(),
                datasets -> AdminListQuerySupport.apply(datasets, query, dataset -> matchesDatasetListQuery(dataset, query), datasetSorters(), "updatedAt")))
            .stream()
                .toList();
    }

        private boolean matchesDatasetListQuery(EvalDatasetResponse dataset, AdminListQuery query) {
        return AdminListQuerySupport.containsAny(query.search(), dataset.name(), dataset.description(), dataset.chatbotId())
            && AdminListQuerySupport.contains(dataset.name(), query.value("name"))
            && AdminListQuerySupport.contains(dataset.description(), query.value("description"))
            && AdminListQuerySupport.equalsText(dataset.chatbotId(), query.value("chatbotId"));
        }

        private Map<String, java.util.function.Function<EvalDatasetResponse, ?>> datasetSorters() {
        return Map.of(
            "name", EvalDatasetResponse::name,
            "description", EvalDatasetResponse::description,
            "chatbotId", EvalDatasetResponse::chatbotId,
            "createdAt", EvalDatasetResponse::createdAt,
            "updatedAt", EvalDatasetResponse::updatedAt
        );
        }

    @Transactional
    public EvalDatasetResponse createDataset(EvalDatasetRequest request) {
        String tenantId = tenantService.currentTenantId();
        ChatbotConfig chatbot = chatbotConfigRepository.findByTenantIdAndId(tenantId, request.chatbotId())
            .orElseThrow(() -> new ResourceNotFoundException("Chatbot", request.chatbotId()));
        EvalDataset dataset = new EvalDataset();
        dataset.setTenantId(tenantId);
        dataset.setName(request.name().strip());
        dataset.setDescription(request.description());
        dataset.setChatbot(chatbot);
        return toDatasetResponse(evalDatasetRepository.save(dataset));
    }

    @Transactional(readOnly = true)
    public List<EvalCaseResponse> listCases(Long datasetId) {
        return listCases(datasetId, AdminListQuery.empty());
    }

    @Transactional(readOnly = true)
    public List<EvalCaseResponse> listCases(Long datasetId, AdminListQuery query) {
        requireDataset(datasetId);
        List<EvalCaseResponse> cases = evalCaseRepository.findByDatasetIdOrderByIdAsc(datasetId).stream()
                .map(this::toCaseResponse)
                .toList();
        return AdminListQuerySupport.apply(cases, query, evalCase -> matchesCaseListQuery(evalCase, query), caseSorters(), "id");
    }

    private boolean matchesCaseListQuery(EvalCaseResponse evalCase, AdminListQuery query) {
        return AdminListQuerySupport.containsAny(query.search(), evalCase.id(), evalCase.input(), evalCase.expectedBehavior(),
                evalCase.expectedKeywords())
                && AdminListQuerySupport.contains(evalCase.input(), query.value("input"))
                && AdminListQuerySupport.contains(evalCase.expectedBehavior(), query.value("expectedBehavior"))
                && (query.value("keyword") == null || evalCase.expectedKeywords().stream().anyMatch(keyword -> AdminListQuerySupport.contains(keyword, query.value("keyword"))));
    }

    private Map<String, java.util.function.Function<EvalCaseResponse, ?>> caseSorters() {
        return Map.of(
                "id", EvalCaseResponse::id,
                "input", EvalCaseResponse::input,
                "expectedBehavior", EvalCaseResponse::expectedBehavior
        );
    }

    @Transactional
    public EvalCaseResponse createCase(Long datasetId, EvalCaseRequest request) {
        EvalDataset dataset = requireDataset(datasetId);
        EvalCase evalCase = new EvalCase();
        evalCase.setDataset(dataset);
        evalCase.setInput(request.input().strip());
        evalCase.setExpectedBehavior(request.expectedBehavior());
        evalCase.setExpectedKeywords(request.expectedKeywords());
        evalCase.setMetadata(request.metadata());
        return toCaseResponse(evalCaseRepository.save(evalCase));
    }

    @Transactional
    public EvalRunResponse createRun(EvalRunRequest request) {
        EvalDataset dataset = requireDataset(request.datasetId());
        String tenantId = tenantService.currentTenantId();
        ChatbotConfig chatbot = request.chatbotId() == null ? dataset.getChatbot() : chatbotConfigRepository.findByTenantIdAndId(tenantId, request.chatbotId())
            .orElseThrow(() -> new ResourceNotFoundException("Chatbot", request.chatbotId()));
        ModelConfig model = request.modelId() == null ? defaultWorkflowModel(chatbot) : modelConfigRepository.findByProviderTenantIdAndId(tenantId, request.modelId())
            .orElseThrow(() -> new ResourceNotFoundException("Model", request.modelId()));

        EvalRun run = new EvalRun();
        run.setTenantId(tenantId);
        run.setDataset(dataset);
        run.setChatbot(chatbot);
        run.setModel(model);
        run.setStatus(EvalRunStatus.PENDING);
        run.setSummary(initialSummary(request));
        run = evalRunRepository.saveAndFlush(run);
        Long runId = run.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evalRunExecutor.execute(runId);
            }
        });
        return toRunResponse(run);
    }

    private ModelConfig defaultWorkflowModel(ChatbotConfig chatbot) {
        ChatbotWorkflowNode startNode = workflowNodeRepository.findByChatbotIdAndStartTrueAndEnabledTrue(chatbot.getId())
                .orElseThrow(() -> new IllegalArgumentException("Chatbot workflow start node is not configured"));
        return startNode.getModel();
    }

    @Transactional(readOnly = true)
    public EvalRunResponse getRun(Long id) {
        return toRunResponse(evalRunRepository.findByTenantIdAndId(tenantService.currentTenantId(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Eval run", id)));
    }

    @Transactional(readOnly = true)
    public List<EvalResultResponse> listResults(Long runId) {
        return listResults(runId, AdminListQuery.empty());
    }

    @Transactional(readOnly = true)
    public List<EvalResultResponse> listResults(Long runId, AdminListQuery query) {
        requireRun(runId);
        List<EvalResultResponse> results = evalResultRepository.findByRunIdOrderByIdAsc(runId).stream()
                .map(this::toResultResponse)
                .toList();
        return AdminListQuerySupport.apply(results, query, result -> matchesResultListQuery(result, query), resultSorters(), "id");
    }

    private boolean matchesResultListQuery(EvalResultResponse result, AdminListQuery query) {
        return AdminListQuerySupport.containsAny(query.search(), result.id(), result.caseId(), result.output(), result.error(),
                result.passed() ? "passed" : "failed")
                && AdminListQuerySupport.contains(result.output(), query.value("output"))
                && AdminListQuerySupport.contains(result.error(), query.value("error"))
                && AdminListQuerySupport.equalsBoolean(result.passed(), query.booleanValue("passed"));
    }

    private Map<String, java.util.function.Function<EvalResultResponse, ?>> resultSorters() {
        return Map.of(
                "id", EvalResultResponse::id,
                "caseId", EvalResultResponse::caseId,
                "passed", EvalResultResponse::passed,
                "output", EvalResultResponse::output,
                "error", EvalResultResponse::error
        );
    }

    private EvalDataset requireDataset(Long id) {
        return evalDatasetRepository.findByTenantIdAndId(tenantService.currentTenantId(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Eval dataset", id));
    }

    private EvalRun requireRun(Long id) {
        return evalRunRepository.findByTenantIdAndId(tenantService.currentTenantId(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Eval run", id));
    }

    private Map<String, Object> initialSummary(EvalRunRequest request) {
        Map<String, Object> summary = new LinkedHashMap<>(request.metadata());
        summary.put("maxEstimatedTokens", request.maxEstimatedTokens());
        summary.put("maxLatencyMillis", request.maxLatencyMillis());
        summary.put("maxEstimatedCostUsd", request.maxEstimatedCostUsd());
        summary.put("costPer1kTokensUsd", request.costPer1kTokensUsd());
        summary.put("goldenReplay", request.goldenReplay() == null || request.goldenReplay());
        summary.put("forbiddenPhrases", request.forbiddenPhrases());
        summary.put("rubric", request.rubric());
        summary.put("releaseGate", request.releaseGate());
        summary.put("scorers", List.of("keywordMatch", "nonEmptyAnswer", "maxTokenBudget", "forbiddenPhrase",
                "rubricScoring", "latencyBudget", "costBudget"));
        summary.put("llmAsJudge", "reserved");
        return summary;
    }

    private EvalDatasetResponse toDatasetResponse(EvalDataset dataset) {
        return new EvalDatasetResponse(dataset.getId(), dataset.getName(), dataset.getDescription(),
                dataset.getChatbot().getId(), dataset.getCreatedAt(), dataset.getUpdatedAt());
    }

    private EvalCaseResponse toCaseResponse(EvalCase evalCase) {
        return new EvalCaseResponse(evalCase.getId(), evalCase.getDataset().getId(), evalCase.getInput(),
                evalCase.getExpectedBehavior(), evalCase.getExpectedKeywords(), evalCase.getMetadata());
    }

    private EvalRunResponse toRunResponse(EvalRun run) {
        Long modelId = run.getModel() == null ? null : run.getModel().getId();
        return new EvalRunResponse(run.getId(), run.getDataset().getId(), run.getChatbot().getId(), modelId,
                run.getStatus().name(), run.getStartedAt(), run.getFinishedAt(), run.getSummary());
    }

    private EvalResultResponse toResultResponse(EvalResult result) {
        return new EvalResultResponse(result.getId(), result.getRun().getId(), result.getEvalCase().getId(),
                result.getOutput(), result.getContextSnapshot(), result.getTokenBudgetReport(), result.getScores(),
                result.isPassed(), result.getError());
    }
}
