package com.testpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.testpilot.common.BusinessException;
import com.testpilot.domain.AgentTask;
import com.testpilot.domain.ArrangeRule;
import com.testpilot.domain.CapturedApi;
import com.testpilot.domain.TestCase;
import com.testpilot.enums.CaseStatus;
import com.testpilot.repository.AgentTaskRepository;
import com.testpilot.repository.ArrangeRuleRepository;
import com.testpilot.repository.CapturedApiRepository;
import com.testpilot.repository.TestCaseRepository;
import com.testpilot.security.UserContext;
import com.testpilot.tool.OpenApiPersistTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Generates JSON-only case drafts per orchestrated scene and saves them only on explicit user action. */
@Service
public class TaskCaseGenerationService {
    private final ChatClient agent;
    private final ObjectMapper mapper;
    private final AgentTaskRepository tasks;
    private final ArrangeRuleRepository rules;
    private final CapturedApiRepository apis;
    private final TestCaseRepository cases;
    private final OpenApiPersistTool platformSync;
    private final PromptTemplateService promptTemplates;

    public TaskCaseGenerationService(@Qualifier("caseDraftAgent") ChatClient caseDraftAgent, ObjectMapper mapper, AgentTaskRepository tasks,
                                     ArrangeRuleRepository rules, CapturedApiRepository apis, TestCaseRepository cases,
                                     OpenApiPersistTool platformSync, PromptTemplateService promptTemplates) {
        this.agent = caseDraftAgent;
        this.mapper = mapper;
        this.tasks = tasks;
        this.rules = rules;
        this.apis = apis;
        this.cases = cases;
        this.platformSync = platformSync;
        this.promptTemplates = promptTemplates;
    }

    public JsonNode generate(Long taskId, String userPrompt) {
        AgentTask task = loadVisible(taskId);
        task.setCaseGenerationPrompt(normalizePrompt(userPrompt));
        tasks.save(task);
        List<ArrangeRule> scenes = rules.findByTaskId(taskId);
        if (scenes.isEmpty()) throw new BusinessException(40010, "请先完成接口与场景编排");
        List<CapturedApi> captured = apis.findByTaskIdOrderBySeqAsc(taskId);
        String context;
        try {
            ObjectNode input = mapper.createObjectNode();
            input.put("taskName", task.getName());
            input.set("scenes", mapper.valueToTree(scenes));
            input.set("capturedApis", mapper.valueToTree(captured));
            context = mapper.writeValueAsString(input);
        } catch (Exception e) {
            throw new BusinessException(50004, "无法整理场景和录制接口数据");
        }
        String response = agent.prompt()
                .system(promptTemplates.render("case-generation.system"))
                .user(promptTemplates.render("case-generation.user", Map.of(
                        "context", context,
                        "userPrompt", task.getCaseGenerationPrompt() == null ? "（未提供）" : task.getCaseGenerationPrompt())))
                .call().content();
        try {
            JsonNode root = mapper.readTree(normalizeJson(response));
            JsonNode groups = root.path("scenes");
            if (!groups.isArray() || groups.isEmpty()) throw new IllegalArgumentException("missing scenes");
            for (JsonNode group : groups) {
                if (!(group instanceof ObjectNode scene)) throw new IllegalArgumentException("invalid scene");
                ArrangeRule sourceRule = scenes.stream()
                        .filter(rule -> rule.getId().equals(scene.path("sceneId").asLong(-1))
                                || rule.getName().equals(scene.path("sceneName").asText()))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("unknown scene"));
                scene.put("sceneId", sourceRule.getId());
                scene.put("sceneName", sourceRule.getName());
                JsonNode sourceSteps = mapper.readTree(sourceRule.getSteps());
                if (!sourceSteps.isArray() || sourceSteps.isEmpty()) throw new IllegalArgumentException("empty source scene");
                if (!scene.path("publicParams").isArray()) scene.set("publicParams", mapper.createArrayNode());
                JsonNode generated = scene.path("cases");
                if (!(generated instanceof ArrayNode caseList) || caseList.isEmpty()) throw new IllegalArgumentException("missing cases");
                for (JsonNode draft : caseList) {
                    if (!(draft instanceof ObjectNode obj)) throw new IllegalArgumentException("invalid case");
                    if (!(obj.path("steps") instanceof ArrayNode draftSteps) || draftSteps.size() != sourceSteps.size())
                        throw new IllegalArgumentException("case steps do not match scene");
                    obj.put("draftId", UUID.randomUUID().toString());
                    for (int i = 0; i < draftSteps.size(); i++) {
                        if (!(draftSteps.get(i) instanceof ObjectNode step)) throw new IllegalArgumentException("invalid step");
                        JsonNode sourceStep = sourceSteps.get(i);
                        if (sourceStep.hasNonNull("apiId")) step.put("apiId", sourceStep.path("apiId").asLong());
                        step.put("sequence", i + 1);
                        step.put("method", sourceStep.path("method").asText("API"));
                        step.put("url", sourceStep.path("url").asText(""));
                        JsonNode input = step.path("input");
                        if (!input.isObject()) step.set("input", mapper.createObjectNode().put("format", "JSON").set("jsonBody", mapper.createObjectNode()));
                    }
                }
            }
            task.setGeneratedCases(mapper.writeValueAsString(root));
            task.setStage(com.testpilot.enums.TaskStage.GENERATE);
            task.setStatus(com.testpilot.enums.TaskStatus.PENDING);
            task.setResultSummary("AI 已生成用例草稿；确认后保存至用例库");
            tasks.save(task);
            return root;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(50004, "AI 用例结果格式错误，请重试生成");
        }
    }

    public void savePrompt(Long taskId, String userPrompt) {
        AgentTask task = loadVisible(taskId);
        task.setCaseGenerationPrompt(normalizePrompt(userPrompt));
        tasks.save(task);
    }

    public JsonNode getGenerated(Long taskId) {
        AgentTask task = loadVisible(taskId);
        try {
            return task.getGeneratedCases() == null || task.getGeneratedCases().isBlank()
                    ? mapper.createObjectNode().putArray("scenes")
                    : mapper.readTree(task.getGeneratedCases());
        } catch (Exception e) {
            throw new BusinessException(50004, "用例草稿读取失败");
        }
    }

    public int save(Long taskId, List<String> draftIds) {
        AgentTask task = loadVisible(taskId);
        JsonNode root = getGenerated(taskId);
        Set<String> selected = draftIds == null ? Set.of() : new HashSet<>(draftIds);
        if (selected.isEmpty()) throw new BusinessException(40001, "请选择至少一个生成用例");
        int saved = 0;
        int synced = 0;
        for (JsonNode scene : root.path("scenes")) {
            Long ruleId = scene.path("sceneId").isNumber() ? scene.path("sceneId").asLong() : null;
            for (JsonNode draft : scene.path("cases")) {
                String draftId = draft.path("draftId").asText();
                if (!selected.contains(draftId)) continue;
                String uniqueKey = "task-" + taskId + "-draft-" + draftId;
                if (cases.findByUniqueKey(uniqueKey).isPresent()) continue;
                TestCase testCase = new TestCase();
                testCase.setUserId(task.getUserId());
                testCase.setTaskId(taskId);
                testCase.setRuleId(ruleId);
                testCase.setName(draft.path("name").asText("AI 生成用例"));
                testCase.setDescription(draft.path("description").asText(""));
                testCase.setSteps(toJson(draft.path("steps")));
                testCase.setPublicVars(toJson(scene.path("publicParams")));
                testCase.setSourceType("TASK_GENERATED");
                testCase.setUniqueKey(uniqueKey);
                testCase.setStatus(CaseStatus.CONFIRMED);
                testCase.setEnabled(true);
                TestCase savedCase = cases.save(testCase);
                String syncResult = platformSync.persistCase(savedCase.getName(), savedCase.getSteps(),
                        savedCase.getAssertions(), savedCase.getPublicVars(), uniqueKey);
                if (syncResult != null && syncResult.startsWith("写入成功")) synced++;
                saved++;
            }
        }
        if (saved == 0) throw new BusinessException(40001, "请选择至少一个生成用例");
        task.setStatus(com.testpilot.enums.TaskStatus.COMPLETED);
        task.setStage(com.testpilot.enums.TaskStage.GENERATE);
        task.setResultSummary("已保存自动化用例 " + saved + " 条，平台同步成功 " + synced + " 条");
        tasks.save(task);
        return saved;
    }

    private AgentTask loadVisible(Long id) {
        AgentTask task = tasks.findById(id).orElseThrow(() -> new BusinessException(40400, "任务不存在：" + id));
        if (!UserContext.isAdmin() && !task.getUserId().equals(UserContext.userId())) {
            throw new BusinessException(40300, "无权访问该任务：" + id);
        }
        return task;
    }

    private String toJson(JsonNode value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new BusinessException(50004, "AI 用例步骤序列化失败"); }
    }

    private String normalizeJson(String value) {
        if (value == null) return "";
        String text = value.trim().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        return start >= 0 && end >= start ? text.substring(start, end + 1) : text;
    }

    private String normalizePrompt(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
