package com.xiangxik.echat.chatbot.api.admin;

import com.xiangxik.echat.chatbot.api.dto.EvalCaseRequest;
import com.xiangxik.echat.chatbot.api.dto.EvalCaseResponse;
import com.xiangxik.echat.chatbot.api.dto.EvalDatasetRequest;
import com.xiangxik.echat.chatbot.api.dto.EvalDatasetResponse;
import com.xiangxik.echat.chatbot.api.dto.EvalResultResponse;
import com.xiangxik.echat.chatbot.api.dto.EvalRunRequest;
import com.xiangxik.echat.chatbot.api.dto.EvalRunResponse;
import com.xiangxik.echat.chatbot.service.EvalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Eval Harness", description = "Manage datasets, cases, and isolated chatbot evaluation runs")
public class EvalAdminController {

    private final EvalService evalService;

    public EvalAdminController(EvalService evalService) {
        this.evalService = evalService;
    }

    @GetMapping("/eval-datasets")
    @Operation(summary = "List eval datasets")
    public List<EvalDatasetResponse> listDatasets() {
        return evalService.listDatasets();
    }

    @PostMapping("/eval-datasets")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an eval dataset")
    public EvalDatasetResponse createDataset(@Valid @RequestBody EvalDatasetRequest request) {
        return evalService.createDataset(request);
    }

    @GetMapping("/eval-datasets/{id}/cases")
    @Operation(summary = "List eval cases for a dataset")
    public List<EvalCaseResponse> listCases(@PathVariable Long id) {
        return evalService.listCases(id);
    }

    @PostMapping("/eval-datasets/{id}/cases")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an eval case")
    public EvalCaseResponse createCase(@PathVariable Long id, @Valid @RequestBody EvalCaseRequest request) {
        return evalService.createCase(id, request);
    }

    @PostMapping("/eval-runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Start an isolated eval run")
    public EvalRunResponse createRun(@Valid @RequestBody EvalRunRequest request) {
        return evalService.createRun(request);
    }

    @GetMapping("/eval-runs/{id}")
    @Operation(summary = "Get an eval run")
    public EvalRunResponse getRun(@PathVariable Long id) {
        return evalService.getRun(id);
    }

    @GetMapping("/eval-runs/{id}/results")
    @Operation(summary = "List eval results for a run")
    public List<EvalResultResponse> listResults(@PathVariable Long id) {
        return evalService.listResults(id);
    }
}
