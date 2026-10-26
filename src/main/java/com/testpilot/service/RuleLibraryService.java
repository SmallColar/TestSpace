package com.testpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testpilot.common.BusinessException;
import com.testpilot.common.Perms;
import com.testpilot.domain.ArrangeRule;
import com.testpilot.domain.CapturedApi;
import com.testpilot.dto.RuleFromHistoryRequest;
import com.testpilot.dto.RuleFromRecordRequest;
import com.testpilot.dto.RuleRequest;
import com.testpilot.dto.RuleResponse;
import com.testpilot.dto.StepExtractResponse;
import com.testpilot.enums.RuleSource;
import com.testpilot.repository.ArrangeRuleRepository;
import com.testpilot.repository.CapturedApiRepository;
import com.testpilot.repository.UserRepository;
import com.testpilot.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编排规则库（spec/SPEC-2026-10-15.md §4）。
 * <p>
 * 规则支持人工录入、录制接口编排、历史规则引用和 AI 分析用例文件。
 */
@Service
@RequiredArgsConstructor
public class RuleLibraryService {

    private final ArrangeRuleRepository arrangeRuleRepository;
    private final CapturedApiRepository capturedApiRepository;
    private final ObjectMapper objectMapper;
    @Qualifier("caseDraftAgent")
    private final ChatClient caseDraftAgent;
    private final UserRepository userRepository;
    private final PromptTemplateService promptTemplates;
    private final RuleVectorIndexService ruleVectorIndexService;

    // ===== 查看（共享可见）=====

    @Transactional(readOnly = true)
    public List<RuleResponse> list() {
        return arrangeRuleRepository.findAllByOrderByIdDesc().stream()
                .map(r -> withCreator(RuleResponse.from(r, Perms.canEdit(r.getUserId())), r.getUserId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public RuleResponse detail(Long id) {
        ArrangeRule rule = load(id);
        return withCreator(RuleResponse.from(rule, Perms.canEdit(rule.getUserId())), rule.getUserId());
    }

    // ===== 增删改（只有自己的能改）=====

    @Transactional
    public RuleResponse create(RuleRequest req) {
        ArrangeRule rule = new ArrangeRule();
        rule.setUserId(UserContext.userId());
        rule.setName(req.getName() == null ? "未命名规则" : req.getName());
        rule.setDescription(req.getDescription());
        rule.setSteps(req.getSteps());
        rule.setSource(parseSource(req.getSource(), RuleSource.MANUAL));
        rule.setTaskId(req.getTaskId());
        rule.setVersion(1);
        rule.setVectorized(false);
        return withCreator(RuleResponse.from(arrangeRuleRepository.save(rule), true), rule.getUserId());
    }

    @Transactional
    public RuleResponse copy(Long sourceId, String name) {
        ArrangeRule source = load(sourceId);
        if (name == null || name.isBlank()) throw new BusinessException(40001, "规则名称不能为空");
        ArrangeRule copy = new ArrangeRule();
        copy.setUserId(UserContext.userId());
        copy.setDerivedFrom(source.getId());
        copy.setName(name.trim());
        copy.setDescription(source.getDescription());
        copy.setSteps(source.getSteps());
        copy.setSource(RuleSource.MANUAL_HISTORY);
        copy.setVersion(1);
        copy.setEnabled(true);
        copy.setVectorized(false);
        return withCreator(RuleResponse.from(arrangeRuleRepository.save(copy), true), copy.getUserId());
    }

    @Transactional
    public RuleResponse update(RuleRequest req) {
        ArrangeRule rule = load(req.getId());
        Perms.requireEdit(rule.getUserId(), "编排规则");
        if (req.getName() != null) {
            rule.setName(req.getName());
        }
        if (req.getDescription() != null) {
            rule.setDescription(req.getDescription());
        }
        if (req.getSteps() != null) {
            rule.setSteps(req.getSteps());
        }
        if (req.getEnabled() != null) {
            rule.setEnabled(req.getEnabled());
        }
        rule.setVectorized(false);
        return withCreator(RuleResponse.from(arrangeRuleRepository.save(rule), true), rule.getUserId());
    }

    @Transactional
    public void delete(Long id) {
        ArrangeRule rule = load(id);
        Perms.requireEdit(rule.getUserId(), "编排规则");
        ruleVectorIndexService.remove(rule.getId());
        arrangeRuleRepository.delete(rule);
    }

    // ===== 来源 a：从录制结果拖拽 API =====

    @Transactional
    public RuleResponse fromRecord(RuleFromRecordRequest req) {
        if (req.getTaskId() == null) throw new BusinessException(40001, "缺少录制任务 ID");
        if (req.getApiIds() == null || req.getApiIds().isEmpty()) {
            throw new BusinessException(40010, "请至少拖入一个接口");
        }
        List<Map<String, Object>> steps = new ArrayList<>();
        int seq = 1;
        for (Long apiId : req.getApiIds()) {
            CapturedApi api = capturedApiRepository.findById(apiId)
                    .orElseThrow(() -> new BusinessException(40400, "录制接口不存在：" + apiId));
            if (!req.getTaskId().equals(api.getTaskId())) throw new BusinessException(40300, "接口不属于当前任务");
            steps.add(stepOf(seq++, api.getMethod(), api.getUrl(), api.getId()));
        }

        ArrangeRule rule = new ArrangeRule();
        rule.setUserId(UserContext.userId());
        rule.setTaskId(req.getTaskId());
        rule.setName(req.getName() == null || req.getName().isBlank()
                ? "录制编排 " + java.time.LocalDateTime.now().toLocalDate() : req.getName());
        rule.setDescription(req.getDescription());
        rule.setSource(RuleSource.RECORD);
        rule.setSteps(toJson(steps));
        rule.setVersion(1);
        rule.setVectorized(false);
        return withCreator(RuleResponse.from(arrangeRuleRepository.save(rule), true), rule.getUserId());
    }

    // ===== 来源 b：基于历史规则新建 / 更新 =====

    @Transactional
    public RuleResponse fromHistory(RuleFromHistoryRequest req) {
        ArrangeRule history = load(req.getHistoryRuleId());
        // 参考别人规则是允许的（规则库共享可见），但产物一定归当前用户
        boolean updateHistory = "UPDATE_HISTORY".equalsIgnoreCase(req.getMode())
                || "UPDATE".equalsIgnoreCase(req.getMode());
        if (updateHistory) {
            Perms.requireEdit(history.getUserId(), "编排规则");
        }
        String name = req.getName() != null && !req.getName().isBlank() ? req.getName().trim() : history.getName();
        String description = req.getDescription() != null ? req.getDescription() : history.getDescription();
        String steps = req.getSteps() != null && !req.getSteps().isBlank() ? req.getSteps() : history.getSteps();
        RuleSource source = parseSource(req.getSource(), RuleSource.MANUAL_HISTORY);
        if (updateHistory) {
            history.setName(name);
            history.setDescription(description);
            history.setSteps(steps);
            history.setSource(source);
            history.setVersion(history.getVersion() == null ? 2 : history.getVersion() + 1);
            history.setVectorized(false);
            arrangeRuleRepository.save(history);
        }
        // A task always gets its own scene row. Updating the shared rule won't detach old tasks.
        ArrangeRule rule = new ArrangeRule();
        rule.setUserId(UserContext.userId());
        rule.setDerivedFrom(history.getId());
        rule.setTaskId(req.getTaskId() != null ? req.getTaskId() : history.getTaskId());
        rule.setName(name);
        rule.setDescription(description);
        rule.setSteps(steps);
        rule.setSource(source);
        rule.setVersion(1);
        rule.setVectorized(false);
        return withCreator(RuleResponse.from(arrangeRuleRepository.save(rule), true), rule.getUserId());
    }

    /** AI analysis of uploaded test documentation; output is strictly interface order, no parameter data. */
    public StepExtractResponse analyzeUploadedCase(String fileName, String content) {
        if (content == null || content.isBlank()) throw new BusinessException(40011, "上传内容为空");
        String response = caseDraftAgent.prompt()
                .system(promptTemplates.render("rule-analysis.system"))
                .user(promptTemplates.render("rule-analysis.user", Map.of(
                        "fileName", fileName == null ? "用例文档" : fileName,
                        "content", content)))
                .call().content();
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode stepsNode = root.path("steps");
            if (!stepsNode.isArray() || stepsNode.isEmpty()) throw new IllegalArgumentException("no steps");
            List<Map<String, Object>> steps = new ArrayList<>();
            List<String> lines = new ArrayList<>();
            int seq = 0;
            for (JsonNode node : stepsNode) {
                Map<String, Object> step = new LinkedHashMap<>();
                step.put("seq", ++seq);
                step.put("name", node.path("name").asText("接口 " + seq));
                step.put("method", node.path("method").asText("API").toUpperCase());
                step.put("url", node.path("url").asText(""));
                step.put("apiId", null);
                steps.add(step);
                lines.add(seq + ". " + step.get("method") + " " + (step.get("url").toString().isBlank() ? step.get("name") : step.get("url")));
            }
            String ruleName = root.path("ruleName").asText(fileName == null ? "用例分析规则" : fileName.replaceAll("\\.[^.]+$", ""));
            return new StepExtractResponse(ruleName, toJson(steps), lines);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(50004, "AI 未能从测试用例中提取接口编排，请调整文档后重试");
        }
    }

    public List<RuleResponse> recommendForTask(Long taskId, List<Long> apiIds, String sceneDescription) {
        if (apiIds == null || apiIds.isEmpty()) return List.of();
        List<Map<String, Object>> selectedApis = new ArrayList<>();
        for (Long apiId : apiIds) {
            CapturedApi api = capturedApiRepository.findById(apiId)
                    .orElseThrow(() -> new BusinessException(40400, "录制接口不存在：" + apiId));
            if (!taskId.equals(api.getTaskId())) throw new BusinessException(40300, "接口不属于当前任务");
            selectedApis.add(Map.of("method", api.getMethod() == null ? "" : api.getMethod(), "url", api.getUrl()));
        }
        List<ArrangeRule> candidates = arrangeRuleRepository.findAllByOrderByIdDesc().stream()
                .filter(rule -> !taskId.equals(rule.getTaskId()))
                .filter(rule -> !Boolean.FALSE.equals(rule.getEnabled()))
                .toList();
        if (candidates.isEmpty()) return List.of();

        StringBuilder query = new StringBuilder();
        if (sceneDescription != null && !sceneDescription.isBlank()) {
            query.append("场景说明：").append(sceneDescription).append('\n');
        }
        query.append("本次录制接口调用顺序：\n");
        for (int i = 0; i < selectedApis.size(); i++) {
            Map<String, Object> api = selectedApis.get(i);
            query.append(i + 1).append(". ").append(api.get("method")).append(' ').append(api.get("url")).append('\n');
        }
        try {
            Map<Long, Double> scores = ruleVectorIndexService.recommend(candidates, query.toString());
            Map<Long, ArrangeRule> byId = new LinkedHashMap<>();
            candidates.forEach(rule -> byId.put(rule.getId(), rule));
            List<RuleResponse> result = new ArrayList<>();
            scores.entrySet().stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                    .forEach(entry -> {
                        ArrangeRule rule = byId.get(entry.getKey());
                        if (rule != null) {
                            RuleResponse response = RuleResponse.from(rule, Perms.canEdit(rule.getUserId()));
                            response.setSimilarityScore(entry.getValue());
                            result.add(withCreator(response, rule.getUserId()));
                        }
                    });
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(50004, "向量检索暂不可用，请检查 Embedding 服务配置和 pgvector 数据库扩展");
        }
    }

    // ===== 辅助 =====

    @Transactional(readOnly = true)
    public ArrangeRule load(Long id) {
        return arrangeRuleRepository.findById(id)
                .orElseThrow(() -> new BusinessException(40400, "编排规则不存在：" + id));
    }

    private Map<String, Object> stepOf(int seq, String method, String url, Long apiId) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("seq", seq);
        s.put("api", method + " " + url);
        s.put("name", method + " " + url);
        s.put("method", method);
        s.put("url", url);
        if (apiId != null) {
            s.put("apiId", apiId);
        }
        return s;
    }

    private RuleSource parseSource(String value, RuleSource fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return RuleSource.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return "[]";
        }
    }

    private RuleResponse withCreator(RuleResponse response, Long userId) {
        if (userId != null) userRepository.findById(userId).ifPresent(user -> response.setCreatorName(
                user.getNickname() == null || user.getNickname().isBlank() ? user.getUsername() : user.getNickname()));
        return response;
    }
}
