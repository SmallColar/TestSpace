package com.testpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testpilot.browser.BrowserAutomationProvider;
import com.testpilot.browser.BrowserProviderRegistry;
import com.testpilot.browser.NetworkRequest;
import com.testpilot.browser.RecordingBrowserTools;
import com.testpilot.common.BusinessException;
import com.testpilot.domain.AgentTask;
import com.testpilot.domain.CapturedApi;
import com.testpilot.enums.TaskStage;
import com.testpilot.enums.TaskStatus;
import com.testpilot.repository.AgentTaskRepository;
import com.testpilot.repository.CapturedApiRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;

/** Runs a task-scoped LLM browser agent and persists its network observations. */
@Service
public class RecordingAgentService {
    private static final Pattern SECRET = Pattern.compile(
            "(?i)((?:\\\"|')?(?:password|passwd|token|authorization|cookie|secret|api[-_]?key)(?:\\\"|')?\\s*[:=]\\s*(?:\\\"|')?)(?:Bearer\\s+)?([^\\\"'&,}\\s]+(?:\\s+[^\\\"'&,}\\s]+)?)"
    );

    private final ChatClient agent;
    private final BrowserProviderRegistry providers;
    private final CapturedApiRepository capturedApis;
    private final AgentTaskRepository tasks;
    private final ObjectMapper mapper;
    private final Semaphore browserSemaphore;
    private final PromptTemplateService promptTemplates;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final ConcurrentMap<Long, FutureTask<Void>> executions = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, BrowserAutomationProvider> activeBrowsers = new ConcurrentHashMap<>();
    private final Set<Long> cancellationRequested = ConcurrentHashMap.newKeySet();

    public RecordingAgentService(@Qualifier("recordingAgent") ChatClient caseGenerateAgent, BrowserProviderRegistry providers,
                                CapturedApiRepository capturedApis, AgentTaskRepository tasks,
                                ObjectMapper mapper, Semaphore browserSemaphore, PromptTemplateService promptTemplates,
                                @Qualifier("taskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.agent = caseGenerateAgent;
        this.providers = providers;
        this.capturedApis = capturedApis;
        this.tasks = tasks;
        this.mapper = mapper;
        this.browserSemaphore = browserSemaphore;
        this.promptTemplates = promptTemplates;
        this.taskExecutor = taskExecutor;
    }

    public void start(Long taskId) {
        FutureTask<Void> future = new FutureTask<>(() -> {
            execute(taskId);
            return null;
        }) {
            @Override
            public void run() {
                try {
                    super.run();
                } finally {
                    cleanup(taskId, this);
                }
            }
        };
        FutureTask<Void> existing = executions.putIfAbsent(taskId, future);
        if (existing != null) {
            markFailed(taskId, "上一次录制仍在退出，请稍后重试");
            throw new BusinessException(40005, "录制任务已经在执行");
        }
        try {
            taskExecutor.execute(future);
        } catch (RuntimeException e) {
            executions.remove(taskId, future);
            markFailed(taskId, "录制任务未能启动：" + safeMessage(e));
            throw e;
        }
    }

    public AgentTask terminate(Long taskId) {
        AgentTask task = tasks.findById(taskId)
                .orElseThrow(() -> new BusinessException(40400, "任务不存在：" + taskId));
        if (task.getStatus() != TaskStatus.RUNNING) {
            throw new BusinessException(40005, "当前任务不在执行中");
        }

        // Set the flag before interrupting/closing resources. The worker checks this
        // flag before persisting its final result, so it cannot overwrite termination.
        cancellationRequested.add(taskId);
        FutureTask<Void> future = executions.get(taskId);
        boolean removedFromQueue = future != null && future.cancel(true)
                && taskExecutor.getThreadPoolExecutor().remove(future);

        task.setStatus(TaskStatus.CANCELLED);
        task.setResultSummary(future == null
                ? "录制已终止：未检测到仍在运行的执行线程，已清理遗留状态"
                : "录制已由用户终止");
        AgentTask saved = tasks.save(task);

        BrowserAutomationProvider browser = activeBrowsers.get(taskId);
        if (browser != null) CompletableFuture.runAsync(() -> closeQuietly(browser));
        if (removedFromQueue) cleanup(taskId, future);
        if (future == null) cancellationRequested.remove(taskId);
        return saved;
    }

    private void cleanup(Long taskId, FutureTask<Void> future) {
        executions.remove(taskId, future);
        activeBrowsers.remove(taskId);
        cancellationRequested.remove(taskId);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOrphanedRecordings() {
        for (AgentTask task : tasks.findByStatusAndStage(TaskStatus.RUNNING, TaskStage.RECORD)) {
            task.setStatus(TaskStatus.FAILED);
            task.setResultSummary("服务重启后检测到未完成的录制任务，请重试或重新录制");
            tasks.save(task);
        }
    }

    private void execute(Long taskId) {
        AgentTask task = tasks.findById(taskId).orElse(null);
        if (task == null) return;
        BrowserAutomationProvider browser = null;
        boolean acquired = false;
        try {
            checkCancellation(taskId);
            if (task.getRecordingGoal() == null || task.getRecordingGoal().isBlank()) {
                throw new BusinessException(40001, "请先填写探索目标与要求提示词");
            }
            browserSemaphore.acquire();
            acquired = true;
            checkCancellation(taskId);
            browser = providers.create(task.getBrowserProvider());
            activeBrowsers.put(taskId, browser);
            checkCancellation(taskId);
            capturedApis.deleteByTaskId(taskId);
            browser.navigate(task.getTargetUrl());
            String initialSnapshot = browser.snapshot();
            agent.prompt()
                    .system(promptTemplates.render("recording.system"))
                    .user(promptTemplates.render("recording.user", Map.of(
                            "recordingGoal", task.getRecordingGoal(),
                            "pageSnapshot", initialSnapshot)))
                    .tools(new RecordingBrowserTools(browser))
                    .call().content();
            checkCancellation(taskId);
            List<NetworkRequest> observed = browser.getNetworkRequests();
            List<NetworkRequest> filtered = filter(task.getRecordingGoal(), observed);
            int seq = 0;
            for (NetworkRequest request : filtered) {
                checkCancellation(taskId);
                CapturedApi api = new CapturedApi();
                api.setTaskId(taskId);
                api.setSeq(++seq);
                api.setUrl(request.getUrl());
                api.setMethod(request.getMethod());
                api.setHttpStatus(request.getStatus());
                api.setRequestHeaders(mask(request.getRequestHeaders()));
                api.setRequestBody(mask(request.getRequestBody()));
                api.setResponseHeaders(mask(request.getResponseHeaders()));
                api.setResponseBody(mask(request.getResponseBody()));
                api.setBusiness(true);
                api.setChecked(true);
                capturedApis.save(api);
            }
            // Keep the task on the recording step until the user reviews and confirms
            // the captured interface list.
            task.setStage(com.testpilot.enums.TaskStage.RECORD);
            task.setStatus(com.testpilot.enums.TaskStatus.PENDING);
            task.setResultSummary("AI 录制完成，捕获接口 " + seq + " 个");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.setStatus(cancellationRequested.contains(taskId) ? TaskStatus.CANCELLED : TaskStatus.FAILED);
            task.setResultSummary(cancellationRequested.contains(taskId) ? "录制已由用户终止" : "AI 录制被中断");
        } catch (Exception e) {
            task.setStatus(cancellationRequested.contains(taskId) ? TaskStatus.CANCELLED : TaskStatus.FAILED);
            task.setResultSummary(cancellationRequested.contains(taskId)
                    ? "录制已由用户终止" : "AI 录制失败：" + safeMessage(e));
        } finally {
            if (browser != null) closeQuietly(browser);
            if (acquired) browserSemaphore.release();
            AgentTask latest = tasks.findById(taskId).orElse(task);
            if (cancellationRequested.contains(taskId)) {
                latest.setStatus(TaskStatus.CANCELLED);
                latest.setResultSummary("录制已由用户终止");
            } else {
                latest.setStage(task.getStage());
                latest.setStatus(task.getStatus());
                latest.setResultSummary(task.getResultSummary());
            }
            tasks.save(latest);
        }
    }

    private void checkCancellation(Long taskId) throws InterruptedException {
        if (Thread.currentThread().isInterrupted() || cancellationRequested.contains(taskId)) {
            throw new InterruptedException("Recording was cancelled");
        }
    }

    private void markFailed(Long taskId, String message) {
        tasks.findById(taskId).ifPresent(task -> {
            task.setStatus(TaskStatus.FAILED);
            task.setResultSummary(message);
            tasks.save(task);
        });
    }

    private void closeQuietly(BrowserAutomationProvider browser) {
        try {
            browser.close();
        } catch (Exception ignored) {
            // Best effort cleanup during cancellation/finalization.
        }
    }

    private List<NetworkRequest> filter(String explorationPrompt, List<NetworkRequest> requests) {
        if (explorationPrompt == null || explorationPrompt.isBlank() || requests.isEmpty()) return requests;
        String descriptions = requests.stream().limit(200).map(r -> r.getMethod() + " " + r.getUrl())
                .reduce((a, b) -> a + "\n" + b).orElse("");
        String result = agent.prompt()
                .system(promptTemplates.render("interface-filter.system"))
                .user(promptTemplates.render("interface-filter.user", Map.of(
                        "explorationPrompt", explorationPrompt,
                        "interfaces", descriptions)))
                .call().content();
        try {
            JsonNode ids = mapper.readTree(result);
            List<NetworkRequest> selected = new ArrayList<>();
            for (JsonNode id : ids) {
                int index = id.asInt(-1) - 1;
                if (index >= 0 && index < requests.size()) selected.add(requests.get(index));
            }
            return selected;
        } catch (Exception e) {
            throw new BusinessException(50004, "接口过滤结果无法解析，请调整过滤提示词后重试");
        }
    }

    private String mask(String value) {
        return value == null ? null : SECRET.matcher(value).replaceAll("$1***");
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : mask(message);
    }
}
