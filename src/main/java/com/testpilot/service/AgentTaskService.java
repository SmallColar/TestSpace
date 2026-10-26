package com.testpilot.service;

import com.testpilot.common.BusinessException;
import com.testpilot.domain.AgentTask;
import com.testpilot.domain.ArrangeRule;
import com.testpilot.domain.CapturedApi;
import com.testpilot.domain.TestCase;
import com.testpilot.dto.CreateTaskRequest;
import com.testpilot.enums.TaskStage;
import com.testpilot.enums.TaskStatus;
import com.testpilot.repository.AgentTaskRepository;
import com.testpilot.repository.ArrangeRuleRepository;
import com.testpilot.repository.CapturedApiRepository;
import com.testpilot.repository.TestCaseRepository;
import com.testpilot.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Persists automation task drafts and enforces task ownership/admin access. */
@Service
@RequiredArgsConstructor
public class AgentTaskService {
    private final AgentTaskRepository tasks;
    private final CapturedApiRepository capturedApis;
    private final ArrangeRuleRepository rules;
    private final TestCaseRepository cases;

    @Value("${testpilot.browser.provider:chrome-devtools-mcp}")
    private String defaultBrowserProvider;

    public AgentTask createTask(CreateTaskRequest request) {
        if (request.getName() == null || request.getName().isBlank()) throw new BusinessException(40001, "任务名称不能为空");
        if (request.getTargetUrl() == null || request.getTargetUrl().isBlank()) throw new BusinessException(40001, "目标网址不能为空");
        if (request.getRecordingGoal() == null || request.getRecordingGoal().isBlank()) throw new BusinessException(40001, "探索目标与要求提示词不能为空");
        AgentTask task = new AgentTask();
        task.setUserId(UserContext.userId());
        task.setName(request.getName().trim());
        task.setTargetUrl(request.getTargetUrl().trim());
        task.setRecordingGoal(request.getRecordingGoal().trim());
        task.setBrowserProvider(nonBlank(request.getBrowserProvider(), defaultBrowserProvider));
        task.setStage(TaskStage.RECORD);
        task.setStatus(TaskStatus.PENDING);
        task.setResultSummary("任务已创建，等待 AI 浏览器录制");
        return tasks.save(task);
    }

    public AgentTask copyTask(Long sourceTaskId, CreateTaskRequest request) {
        String name = request.getName();
        if (name == null || name.isBlank()) throw new BusinessException(40001, "新任务名称不能为空");
        AgentTask source = loadVisible(sourceTaskId);
        String targetUrl = nonBlank(request.getTargetUrl(), source.getTargetUrl());
        String recordingGoal = nonBlank(request.getRecordingGoal(), source.getRecordingGoal());
        String browserProvider = nonBlank(request.getBrowserProvider(), source.getBrowserProvider());
        AgentTask copy = new AgentTask();
        copy.setUserId(UserContext.userId());
        copy.setName(name.trim());
        copy.setTargetUrl(targetUrl);
        copy.setRecordingGoal(recordingGoal);
        copy.setBrowserProvider(browserProvider);
        copy.setStage(TaskStage.RECORD);
        copy.setStatus(TaskStatus.PENDING);
        copy.setResultSummary("由历史任务复制，等待 AI 浏览器录制");
        return tasks.save(copy);
    }

    public AgentTask updateRecordingInfo(CreateTaskRequest request) {
        if (request.getId() == null) throw new BusinessException(40001, "缺少任务 ID");
        AgentTask task = loadVisible(request.getId());
        if (task.getStatus() == TaskStatus.RUNNING) throw new BusinessException(40005, "录制正在执行，不能修改录制配置");
        if (request.getName() == null || request.getName().isBlank()) throw new BusinessException(40001, "任务名称不能为空");
        if (request.getTargetUrl() == null || request.getTargetUrl().isBlank()) throw new BusinessException(40001, "目标网址不能为空");
        if (request.getRecordingGoal() == null || request.getRecordingGoal().isBlank()) throw new BusinessException(40001, "探索目标与要求提示词不能为空");
        task.setName(request.getName().trim());
        task.setTargetUrl(request.getTargetUrl().trim());
        task.setRecordingGoal(request.getRecordingGoal().trim());
        task.setBrowserProvider(nonBlank(request.getBrowserProvider(), task.getBrowserProvider()));
        return tasks.save(task);
    }

    @Transactional
    public AgentTask prepareRerecord(Long taskId) {
        AgentTask task = loadVisible(taskId);
        if (task.getStatus() == TaskStatus.RUNNING) throw new BusinessException(40005, "录制正在执行，不能重新录制");
        // Scenes also live in the shared rule library. Detach them from this task
        // instead of deleting them, so they remain available as historical rules.
        rules.detachFromTask(taskId);
        capturedApis.deleteByTaskId(taskId);
        task.setGeneratedCases(null);
        task.setStage(TaskStage.RECORD);
        task.setStatus(TaskStatus.RUNNING);
        task.setResultSummary("AI 正在按录制目标重新探索网站");
        return tasks.save(task);
    }

    @Transactional
    public AgentTask markRecordingStarted(Long taskId) {
        AgentTask task = loadVisible(taskId);
        if (task.getStatus() == TaskStatus.RUNNING) throw new BusinessException(40005, "AI 录制正在执行");
        task.setStatus(TaskStatus.RUNNING);
        task.setResultSummary("AI 正在按录制目标操作网站");
        return tasks.save(task);
    }

    @Transactional
    public AgentTask confirmRecording(Long taskId, List<Long> apiIds) {
        AgentTask task = loadVisible(taskId);
        if (task.getStatus() == TaskStatus.RUNNING) throw new BusinessException(40005, "录制正在执行，暂时不能确认接口");
        if (apiIds == null || apiIds.isEmpty()) throw new BusinessException(40010, "请至少保留一个接口再继续");
        if (apiIds.stream().anyMatch(Objects::isNull)) throw new BusinessException(40001, "接口 ID 不能为空");
        List<CapturedApi> captured = capturedApis.findByTaskIdOrderBySeqAsc(taskId);
        if (captured.isEmpty()) throw new BusinessException(40010, "当前任务还没有录制接口");
        Set<Long> capturedIds = captured.stream().map(CapturedApi::getId).collect(Collectors.toSet());
        if (!capturedIds.containsAll(apiIds)) throw new BusinessException(40300, "选择的接口中包含其他任务的接口");

        Set<Long> selectedIds = Set.copyOf(apiIds);
        boolean selectionChanged = captured.stream().anyMatch(api ->
                (!Boolean.FALSE.equals(api.getChecked())) != selectedIds.contains(api.getId()));
        captured.forEach(api -> api.setChecked(selectedIds.contains(api.getId())));
        capturedApis.saveAll(captured);

        // Reconfirming from a later step changes the foundation of orchestration.
        // Retire task copies of scenes and drafts, but keep saved cases and rules in
        // the shared rule library.
        if (task.getStage() != TaskStage.RECORD && selectionChanged) {
            rules.detachFromTask(taskId);
            task.setGeneratedCases(null);
        }
        task.setStage(TaskStage.PARSE);
        task.setStatus(TaskStatus.PENDING);
        task.setResultSummary("接口已确认，等待场景编排");
        return tasks.save(task);
    }

    public List<AgentTask> listTasks() {
        return UserContext.isAdmin() ? tasks.findAllByOrderByIdDesc() : tasks.findByUserIdOrderByIdDesc(UserContext.userId());
    }

    public AgentTask getTask(Long id) { return loadVisible(id); }

    public List<ArrangeRule> rulesOfTask(Long taskId) {
        loadVisible(taskId);
        return rules.findByTaskId(taskId);
    }

    public AgentTask completeOrchestration(Long taskId) {
        AgentTask task = loadVisible(taskId);
        if (rules.findByTaskId(taskId).isEmpty()) throw new BusinessException(40010, "请至少保存一个场景后再继续");
        task.setStage(TaskStage.GENERATE);
        task.setStatus(TaskStatus.PENDING);
        task.setResultSummary("场景编排已保存，等待 AI 生成用例");
        return tasks.save(task);
    }

    @Transactional
    public void deleteTask(Long taskId) {
        AgentTask task = loadVisible(taskId);
        if (task.getStatus() == TaskStatus.RUNNING) throw new BusinessException(40005, "录制正在执行，暂时不能删除");
        cases.deleteByTaskId(taskId);
        rules.deleteByTaskId(taskId);
        capturedApis.deleteByTaskId(taskId);
        tasks.delete(task);
    }

    public void requireVisible(Long taskId) { loadVisible(taskId); }

    private AgentTask loadVisible(Long id) {
        AgentTask task = tasks.findById(id).orElseThrow(() -> new BusinessException(40400, "任务不存在：" + id));
        if (!UserContext.isAdmin() && !task.getUserId().equals(UserContext.userId())) {
            throw new BusinessException(40300, "无权访问该任务：" + id);
        }
        return task;
    }

    private String nonBlank(String value, String fallback) { return value == null || value.isBlank() ? fallback : value.trim(); }
}
