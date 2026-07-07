package com.xiangxik.echat.chatbot.service;

import com.xiangxik.echat.chatbot.api.dto.EvalCaseRequest;
import com.xiangxik.echat.chatbot.api.dto.EvalCaseResponse;
import com.xiangxik.echat.chatbot.api.dto.EvalDatasetRequest;
import com.xiangxik.echat.chatbot.api.dto.EvalDatasetResponse;
import com.xiangxik.echat.chatbot.api.dto.EvalResultResponse;
import com.xiangxik.echat.chatbot.api.dto.EvalRunRequest;
import com.xiangxik.echat.chatbot.api.dto.EvalRunResponse;
import com.xiangxik.echat.chatbot.domain.model.ChatbotConfig;
import com.xiangxik.echat.chatbot.domain.model.ContextPolicy;
import com.xiangxik.echat.chatbot.domain.model.EvalCase;
import com.xiangxik.echat.chatbot.domain.model.EvalDataset;
import com.xiangxik.echat.chatbot.domain.model.EvalResult;
import com.xiangxik.echat.chatbot.domain.model.EvalRun;
import com.xiangxik.echat.chatbot.domain.model.EvalRunStatus;
import com.xiangxik.echat.chatbot.domain.model.ModelConfig;
import com.xiangxik.echat.chatbot.domain.repository.ChatbotConfigRepository;
import com.xiangxik.echat.chatbot.domain.repository.ContextPolicyRepository;
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
    private final ModelConfigRepository modelConfigRepository;
    private final ContextPolicyRepository contextPolicyRepository;
    private final EvalRunExecutor evalRunExecutor;

    public EvalService(EvalDatasetRepository evalDatasetRepository,
                       EvalCaseRepository evalCaseRepository,
                       EvalRunRepository evalRunRepository,
                       EvalResultRepository evalResultRepository,
                       ChatbotConfigRepository chatbotConfigRepository,
                       ModelConfigRepository modelConfigRepository,
                       ContextPolicyRepository contextPolicyRepository,
                       EvalRunExecutor evalRunExecutor) {
        this.evalDatasetRepository = evalDatasetRepository;
        this.evalCaseRepository = evalCaseRepository;
        this.evalRunRepository = evalRunRepository;
        this.evalResultRepository = evalResultRepository;
        this.chatbotConfigRepository = chatbotConfigRepository;
        this.modelConfigRepository = modelConfigRepository;
        this.contextPolicyRepository = contextPolicyRepository;
        this.evalRunExecutor = evalRunExecutor;
    }

    @Transactional(readOnly = true)
    public List<EvalDatasetResponse> listDatasets() {
        return evalDatasetRepository.findAllByOrderByUpdatedAtDesc().stream().map(this::toDatasetResponse).toList();
    }

    @Transactional
    public EvalDatasetResponse createDataset(EvalDatasetRequest request) {
        ChatbotConfig chatbot = chatbotConfigRepository.findById(request.chatbotId())
            .orElseThrow(() -> new ResourceNotFoundException("Chatbot", request.chatbotId()));
        EvalDataset dataset = new EvalDataset();
        dataset.setName(request.name().strip());
        dataset.setDescription(request.description());
        dataset.setChatbot(chatbot);
        return toDatasetResponse(evalDatasetRepository.save(dataset));
    }

    @Transactional(readOnly = true)
    public List<EvalCaseResponse> listCases(Long datasetId) {
        requireDataset(datasetId);
        return evalCaseRepository.findByDatasetIdOrderByIdAsc(datasetId).stream().map(this::toCaseResponse).toList();
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
        ChatbotConfig chatbot = request.chatbotId() == null ? dataset.getChatbot() : chatbotConfigRepository.findById(request.chatbotId())
            .orElseThrow(() -> new ResourceNotFoundException("Chatbot", request.chatbotId()));
        ContextPolicy contextPolicy = request.contextPolicyId() == null ? chatbot.getContextPolicy() : contextPolicyRepository.findById(request.contextPolicyId())
            .orElseThrow(() -> new ResourceNotFoundException("Context policy", request.contextPolicyId()));
        ModelConfig model = request.modelId() == null ? contextPolicy == null ? null : contextPolicy.getModel() : modelConfigRepository.findById(request.modelId())
            .orElseThrow(() -> new ResourceNotFoundException("Model", request.modelId()));

        EvalRun run = new EvalRun();
        run.setDataset(dataset);
        run.setChatbot(chatbot);
        run.setContextPolicy(contextPolicy);
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

    @Transactional(readOnly = true)
    public EvalRunResponse getRun(Long id) {
        return toRunResponse(evalRunRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Eval run", id)));
    }

    @Transactional(readOnly = true)
    public List<EvalResultResponse> listResults(Long runId) {
        requireRun(runId);
        return evalResultRepository.findByRunIdOrderByIdAsc(runId).stream().map(this::toResultResponse).toList();
    }

    private EvalDataset requireDataset(Long id) {
        return evalDatasetRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Eval dataset", id));
    }

    private EvalRun requireRun(Long id) {
        return evalRunRepository.findById(id)
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
        Long contextPolicyId = run.getContextPolicy() == null ? null : run.getContextPolicy().getId();
        return new EvalRunResponse(run.getId(), run.getDataset().getId(), run.getChatbot().getId(), modelId,
                contextPolicyId, run.getStatus().name(), run.getStartedAt(), run.getFinishedAt(), run.getSummary());
    }

    private EvalResultResponse toResultResponse(EvalResult result) {
        return new EvalResultResponse(result.getId(), result.getRun().getId(), result.getEvalCase().getId(),
                result.getOutput(), result.getContextSnapshot(), result.getTokenBudgetReport(), result.getScores(),
                result.isPassed(), result.getError());
    }
}
