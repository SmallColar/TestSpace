package com.testpilot.controller;

import com.testpilot.common.R;
import com.testpilot.dto.RuleRequest;
import com.testpilot.dto.RuleResponse;
import com.testpilot.dto.StepExtractResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import com.testpilot.service.RuleLibraryService;
import com.testpilot.service.AgentTaskService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 编排规则库。
 * <p>
 * 查看共享（所有登录用户可见全部），编辑仅限自己的，管理员可编辑全部。
 */
@RestController
@RequestMapping("/api/rules")
public class RuleLibraryController {

    private final RuleLibraryService ruleLibraryService;
    private final ObjectMapper objectMapper;
    private final AgentTaskService taskService;

    public RuleLibraryController(RuleLibraryService ruleLibraryService, ObjectMapper objectMapper, AgentTaskService taskService) {
        this.ruleLibraryService = ruleLibraryService;
        this.objectMapper = objectMapper;
        this.taskService = taskService;
    }

    @GetMapping
    public R<List<RuleResponse>> list() {
        return R.ok(ruleLibraryService.list());
    }

    @GetMapping("/detail")
    public R<RuleResponse> detail(@RequestParam Long id) {
        return R.ok(ruleLibraryService.detail(id));
    }

    public record RecommendationRequest(Long taskId, List<Long> apiIds, String sceneDescription) {}

    @PostMapping("/recommend")
    public R<List<RuleResponse>> recommend(@RequestBody RecommendationRequest request) {
        taskService.requireVisible(request.taskId());
        return R.ok(ruleLibraryService.recommendForTask(request.taskId(), request.apiIds(), request.sceneDescription()));
    }

    @PostMapping(value = "/analyze-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<StepExtractResponse> analyzeUpload(@RequestPart("file") MultipartFile file) {
        if (file.isEmpty()) throw new com.testpilot.common.BusinessException(40011, "请选择测试用例文件");
        if (file.getSize() > 10L * 1024 * 1024) throw new com.testpilot.common.BusinessException(40011, "文件不能超过 10 MB");
        try {
            String fileName = file.getOriginalFilename() == null ? "用例文件" : file.getOriginalFilename();
            String content = fileName.toLowerCase().endsWith(".xmind")
                    ? extractXmindText(file.getBytes()) : new String(file.getBytes(), StandardCharsets.UTF_8);
            return R.ok(ruleLibraryService.analyzeUploadedCase(fileName, content));
        } catch (java.io.IOException e) {
            throw new com.testpilot.common.BusinessException(40011, "无法读取上传的用例文件");
        }
    }

    private String extractXmindText(byte[] bytes) throws java.io.IOException {
        String json = null;
        String xml = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("content.json".equals(entry.getName()) || "content.xml".equals(entry.getName())) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    zip.transferTo(out);
                    if (entry.getName().endsWith(".json")) json = out.toString(StandardCharsets.UTF_8);
                    else xml = out.toString(StandardCharsets.UTF_8);
                    break;
                }
            }
        }
        StringBuilder titles = new StringBuilder();
        if (json != null) {
            JsonNode root = objectMapper.readTree(json);
            collectTitles(root, titles);
        } else if (xml != null) {
            Matcher matcher = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.DOTALL).matcher(xml);
            while (matcher.find()) titles.append(matcher.group(1).replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">"))
                    .append('\n');
        }
        if (titles.isEmpty()) throw new com.testpilot.common.BusinessException(40011, "XMind 文件中未找到可分析的主题内容");
        return titles.toString();
    }

    private void collectTitles(JsonNode node, StringBuilder output) {
        if (node.isObject()) {
            JsonNode title = node.get("title");
            if (title != null && title.isTextual() && !title.asText().isBlank()) output.append(title.asText()).append('\n');
            node.fields().forEachRemaining(field -> collectTitles(field.getValue(), output));
        } else if (node.isArray()) node.forEach(child -> collectTitles(child, output));
    }

    @PostMapping
    public R<RuleResponse> create(@RequestBody RuleRequest req) {
        return R.ok(ruleLibraryService.create(req));
    }

    public record CopyRuleRequest(Long sourceRuleId, String name) {}

    @PostMapping("/copy")
    public R<RuleResponse> copy(@RequestBody CopyRuleRequest req) {
        return R.ok(ruleLibraryService.copy(req.sourceRuleId(), req.name()));
    }

    @PutMapping("/update")
    public R<RuleResponse> update(@RequestBody RuleRequest req) {
        return R.ok(ruleLibraryService.update(req));
    }

    @DeleteMapping("/delete")
    public R<Void> delete(@RequestParam Long id) {
        ruleLibraryService.delete(id);
        return R.ok();
    }
}
