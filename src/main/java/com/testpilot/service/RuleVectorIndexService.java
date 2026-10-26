package com.testpilot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testpilot.domain.ArrangeRule;
import com.testpilot.repository.ArrangeRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds and searches pgvector embeddings for interface orchestration rules. */
@Service
@RequiredArgsConstructor
public class RuleVectorIndexService {

    private final ArrangeRuleRepository arrangeRuleRepository;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    @Value("${testpilot.rules.recommendation.similarity-threshold:0.70}")
    private double similarityThreshold;

    @Value("${testpilot.rules.recommendation.max-results:8}")
    private int maxResults;

    public Map<Long, Double> recommend(List<ArrangeRule> candidates, String query) {
        if (candidates == null || candidates.isEmpty() || query == null || query.isBlank()) return Map.of();

        for (ArrangeRule rule : candidates) {
            if (!Boolean.TRUE.equals(rule.getVectorized())) index(rule);
        }

        List<Object> ruleIds = candidates.stream().map(ArrangeRule::getId).map(id -> (Object) id).toList();
        FilterExpressionBuilder filters = new FilterExpressionBuilder();
        int resultLimit = Math.max(1, Math.min(maxResults, candidates.size()));
        List<Document> matches = vectorStore.similaritySearch(SearchRequest.builder()
                .query(query)
                .topK(resultLimit)
                .similarityThreshold(similarityThreshold)
                .filterExpression(filters.in("ruleId", ruleIds).build())
                .build());

        Map<Long, Double> scores = new LinkedHashMap<>();
        if (matches == null) return scores;
        for (Document document : matches) {
            Object rawId = document.getMetadata().get("ruleId");
            if (!(rawId instanceof Number id) || document.getScore() == null) continue;
            double score = document.getScore();
            if (score > similarityThreshold) scores.put(id.longValue(), score);
        }
        return scores;
    }

    public void remove(Long ruleId) {
        if (ruleId == null) return;
        FilterExpressionBuilder filters = new FilterExpressionBuilder();
        vectorStore.delete(filters.eq("ruleId", ruleId).build());
    }

    private void index(ArrangeRule rule) {
        FilterExpressionBuilder filters = new FilterExpressionBuilder();
        vectorStore.delete(filters.eq("ruleId", rule.getId()).build());

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("ruleId", rule.getId());
        metadata.put("ruleName", rule.getName() == null ? "" : rule.getName());
        Document document = new Document(UUID.randomUUID().toString(), ruleContent(rule), metadata);
        vectorStore.add(List.of(document));

        rule.setVectorized(true);
        arrangeRuleRepository.save(rule);
    }

    private String ruleContent(ArrangeRule rule) {
        StringBuilder content = new StringBuilder();
        content.append("编排规则名称：").append(rule.getName() == null ? "" : rule.getName()).append('\n');
        if (rule.getDescription() != null && !rule.getDescription().isBlank()) {
            content.append("场景说明：").append(rule.getDescription()).append('\n');
        }
        content.append("接口调用顺序：\n");
        try {
            JsonNode steps = objectMapper.readTree(rule.getSteps() == null ? "[]" : rule.getSteps());
            if (steps.isArray()) {
                int sequence = 0;
                for (JsonNode step : steps) {
                    String method = step.path("method").asText("API").toUpperCase();
                    String url = step.path("url").asText("");
                    String name = step.path("name").asText("");
                    content.append(++sequence).append(". ").append(method).append(' ')
                            .append(url.isBlank() ? name : url).append('\n');
                }
            }
        } catch (Exception ignored) {
            content.append(rule.getSteps() == null ? "" : rule.getSteps());
        }
        return content.toString();
    }
}
