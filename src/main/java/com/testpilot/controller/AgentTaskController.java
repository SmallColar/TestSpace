package com.testpilot.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.testpilot.common.BusinessException;
import com.testpilot.common.R;
import com.testpilot.domain.AgentTask;
import com.testpilot.domain.ArrangeRule;
import com.testpilot.domain.CapturedApi;
import com.testpilot.dto.CreateTaskRequest;
import com.testpilot.dto.RuleFromHistoryRequest;
import com.testpilot.dto.RuleFromRecordRequest;
import com.testpilot.dto.RuleResponse;
import com.testpilot.dto.TaskResponse;
import com.testpilot.repository.CapturedApiRepository;
import com.testpilot.service.AgentTaskService;
import com.testpilot.service.RecordingAgentService;
import com.testpilot.service.RuleLibraryService;
import com.testpilot.service.TaskCaseGenerationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** API for the explicit task flow: draft → AI recording → scenes → generated cases → save. */
@RestController
@RequestMapping("/api/tasks")
public class AgentTaskController {
    private final AgentTaskService taskService;
    private final RecordingAgentService recordingAgent;
    private final TaskCaseGenerationService caseGeneration;
    private final RuleLibraryService ruleLibrary;
    private final CapturedApiRepository capturedApis;

    public AgentTaskController(AgentTaskService taskService, RecordingAgentService recordingAgent,
                               TaskCaseGenerationService caseGeneration, RuleLibraryService ruleLibrary,
                               CapturedApiRepository capturedApis) {
        this.taskService = taskService;
        this.recordingAgent = recordingAgent;
        this.caseGeneration = caseGeneration;
        this.ruleLibrary = ruleLibrary;
        this.capturedApis = capturedApis;
    }

    @PostMapping
    public R<TaskResponse> create(@RequestBody CreateTaskRequest request) {
        return R.ok(TaskResponse.from(taskService.createTask(request)));
    }

    public record CopyTaskRequest(Long sourceTaskId, String name, String targetUrl, String recordingGoal,
                                  String browserProvider) {}
    @PostMapping("/copy")
    public R<TaskResponse> copy(@RequestBody CopyTaskRequest request) {
        CreateTaskRequest copy = new CreateTaskRequest();
        copy.setName(request.name()); copy.setTargetUrl(request.targetUrl());
        copy.setRecordingGoal(request.recordingGoal());
        copy.setBrowserProvider(request.browserProvider());
        return R.ok(TaskResponse.from(taskService.copyTask(request.sourceTaskId(), copy)));
    }

    @PutMapping("/update")
    public R<TaskResponse> update(@RequestBody CreateTaskRequest request) {
        return R.ok(TaskResponse.from(taskService.updateRecordingInfo(request)));
    }

    @GetMapping
    public R<List<TaskResponse>> list() {
        return R.ok(taskService.listTasks().stream().map(TaskResponse::from).collect(Collectors.toList()));
    }

    @GetMapping("/detail")
    public R<TaskResponse> detail(@RequestParam Long id) {
        return R.ok(TaskResponse.from(taskService.getTask(id)));
    }

    @DeleteMapping("/delete")
    public R<Void> delete(@RequestParam Long id) {
        taskService.deleteTask(id);
        return R.ok();
    }

    @PostMapping("/recording/start")
    public R<TaskResponse> startRecording(@RequestBody Map<String, Long> request) {
        Long taskId = request.get("taskId");
        if (taskId == null) throw new BusinessException(40001, "缺少任务 ID");
        AgentTask task = taskService.markRecordingStarted(taskId);
        recordingAgent.start(taskId);
        return R.ok(TaskResponse.from(task));
    }

    @PostMapping("/recording/restart")
    public R<TaskResponse> restartRecording(@RequestBody Map<String, Long> request) {
        Long taskId = request.get("taskId");
        if (taskId == null) throw new BusinessException(40001, "缺少任务 ID");
        AgentTask task = taskService.prepareRerecord(taskId);
        recordingAgent.start(taskId);
        return R.ok(TaskResponse.from(task));
    }

    @PostMapping("/recording/terminate")
    public R<TaskResponse> terminateRecording(@RequestBody Map<String, Long> request) {
        Long taskId = request.get("taskId");
        if (taskId == null) throw new BusinessException(40001, "缺少任务 ID");
        taskService.requireVisible(taskId);
        return R.ok(TaskResponse.from(recordingAgent.terminate(taskId)));
    }

    public record ConfirmRecordingRequest(Long taskId, List<Long> apiIds) {}

    @PostMapping("/recording/confirm")
    public R<TaskResponse> confirmRecording(@RequestBody ConfirmRecordingRequest request) {
        if (request.taskId() == null) throw new BusinessException(40001, "缺少任务 ID");
        return R.ok(TaskResponse.from(taskService.confirmRecording(request.taskId(), request.apiIds())));
    }

    @GetMapping("/recording/status")
    public R<TaskResponse> recordingStatus(@RequestParam Long id) {
        return R.ok(TaskResponse.from(taskService.getTask(id)));
    }

    @GetMapping("/apis")
    public R<List<CapturedApi>> apis(@RequestParam Long id) {
        taskService.requireVisible(id);
        return R.ok(capturedApis.findByTaskIdOrderBySeqAsc(id));
    }

    @GetMapping("/rules")
    public R<List<ArrangeRule>> rules(@RequestParam Long id) {
        return R.ok(taskService.rulesOfTask(id));
    }

    @PostMapping("/rules/from-record")
    public R<RuleResponse> rulesFromRecord(@RequestBody RuleFromRecordRequest request) {
        taskService.requireVisible(request.getTaskId());
        return R.ok(ruleLibrary.fromRecord(request));
    }

    @PostMapping("/rules/from-history")
    public R<RuleResponse> rulesFromHistory(@RequestBody RuleFromHistoryRequest request) {
        taskService.requireVisible(request.getTaskId());
        return R.ok(ruleLibrary.fromHistory(request));
    }

    @PostMapping("/rules/complete")
    public R<TaskResponse> completeOrchestration(@RequestBody Map<String, Long> request) {
        return R.ok(TaskResponse.from(taskService.completeOrchestration(request.get("taskId"))));
    }

    public record GenerateCasesRequest(Long taskId, String prompt) {}
    public record SaveCaseGenerationPromptRequest(Long taskId, String prompt) {}
    public record SaveCasesRequest(Long taskId, List<String> draftIds) {}

    @PostMapping("/cases/generate")
    public R<JsonNode> generateCases(@RequestBody GenerateCasesRequest request) {
        return R.ok(caseGeneration.generate(request.taskId(), request.prompt()));
    }

    @PutMapping("/cases/prompt")
    public R<Void> saveCaseGenerationPrompt(@RequestBody SaveCaseGenerationPromptRequest request) {
        caseGeneration.savePrompt(request.taskId(), request.prompt());
        return R.ok();
    }

    @GetMapping("/cases/generated")
    public R<JsonNode> generatedCases(@RequestParam Long id) {
        return R.ok(caseGeneration.getGenerated(id));
    }

    @PostMapping("/cases/save")
    public R<Integer> saveCases(@RequestBody SaveCasesRequest request) {
        return R.ok(caseGeneration.save(request.taskId(), request.draftIds()));
    }
}
