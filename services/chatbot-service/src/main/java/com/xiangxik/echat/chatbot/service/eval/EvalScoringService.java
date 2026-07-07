package com.xiangxik.echat.chatbot.service.eval;

import com.xiangxik.echat.chatbot.domain.model.EvalCase;
import com.xiangxik.echat.chatbot.service.context.TokenBudgetReport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EvalScoringService {

    public EvalScore score(EvalCase evalCase, String output, TokenBudgetReport tokenBudgetReport,
                                                   Integer maxEstimatedTokens, List<String> forbiddenPhrases, Map<String, Object> rubric,
                                                   Map<String, Object> metrics, Integer maxLatencyMillis, Double maxEstimatedCostUsd) {
        String safeOutput = output == null ? "" : output;
        String normalizedOutput = safeOutput.toLowerCase(Locale.ROOT);
        List<String> expectedKeywords = evalCase.getExpectedKeywords().stream()
                .filter(StringUtils::hasText)
                .map(String::strip)
                .toList();
        List<String> matchedKeywords = expectedKeywords.stream()
                .filter(keyword -> normalizedOutput.contains(keyword.toLowerCase(Locale.ROOT)))
                .toList();
        List<String> missingKeywords = new ArrayList<>(expectedKeywords);
        missingKeywords.removeAll(matchedKeywords);

        List<String> configuredForbiddenPhrases = forbiddenPhrases == null ? List.of() : forbiddenPhrases.stream()
                .filter(StringUtils::hasText)
                .map(String::strip)
                .toList();
        List<String> matchedForbiddenPhrases = configuredForbiddenPhrases.stream()
                .filter(phrase -> normalizedOutput.contains(phrase.toLowerCase(Locale.ROOT)))
                .toList();

        int tokenLimit = maxEstimatedTokens == null || maxEstimatedTokens <= 0
                ? tokenBudgetReport.maxTokens()
                : maxEstimatedTokens;
        boolean nonEmptyAnswerPassed = StringUtils.hasText(safeOutput);
        boolean keywordMatchPassed = missingKeywords.isEmpty();
        boolean tokenBudgetPassed = tokenBudgetReport.totalEstimatedTokens() <= tokenLimit;
        boolean forbiddenPhrasePassed = matchedForbiddenPhrases.isEmpty();
        Map<String, Object> rubricScore = rubricScore(rubric, normalizedOutput);
        boolean rubricPassed = Boolean.TRUE.equals(rubricScore.get("passed"));
        long latencyMillis = longNumber(metrics.get("latencyMillis"));
        double estimatedCostUsd = decimal(metrics.get("estimatedCostUsd"));
        boolean latencyPassed = maxLatencyMillis == null || maxLatencyMillis <= 0 || latencyMillis <= maxLatencyMillis;
        boolean costPassed = maxEstimatedCostUsd == null || maxEstimatedCostUsd < 0 || estimatedCostUsd <= maxEstimatedCostUsd;
        boolean passed = nonEmptyAnswerPassed && keywordMatchPassed && tokenBudgetPassed && forbiddenPhrasePassed
                && rubricPassed && latencyPassed && costPassed;

        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("nonEmptyAnswer", Map.of("passed", nonEmptyAnswerPassed));
        scores.put("keywordMatch", Map.of(
                "passed", keywordMatchPassed,
                "expected", expectedKeywords,
                "matched", matchedKeywords,
                "missing", missingKeywords
        ));
        scores.put("maxTokenBudget", Map.of(
                "passed", tokenBudgetPassed,
                "maxEstimatedTokens", tokenLimit,
                "actualEstimatedTokens", tokenBudgetReport.totalEstimatedTokens()
        ));
        scores.put("forbiddenPhrase", Map.of(
                "passed", forbiddenPhrasePassed,
                "configured", configuredForbiddenPhrases,
                "matched", matchedForbiddenPhrases
        ));
        scores.put("rubricScoring", rubricScore);
        scores.put("latencyBudget", Map.of(
                "passed", latencyPassed,
                "maxLatencyMillis", maxLatencyMillis == null ? 0 : maxLatencyMillis,
                "actualLatencyMillis", latencyMillis
        ));
        scores.put("costBudget", Map.of(
                "passed", costPassed,
                "maxEstimatedCostUsd", maxEstimatedCostUsd == null ? 0.0 : maxEstimatedCostUsd,
                "actualEstimatedCostUsd", estimatedCostUsd
        ));
        scores.put("metrics", metrics);
        scores.put("llmAsJudge", Map.of("status", "reserved"));
        return new EvalScore(scores, passed);
    }

    private Map<String, Object> rubricScore(Map<String, Object> rubric, String normalizedOutput) {
        if (rubric == null || rubric.isEmpty()) {
            return Map.of("passed", true, "configured", false);
        }
        List<?> criteria = rubric.get("criteria") instanceof List<?> list ? list : List.of();
        if (criteria.isEmpty()) {
            return Map.of("passed", true, "configured", true, "status", "no_criteria");
        }
        double minScore = decimalOrDefault(rubric.get("minScore"), 1.0);
        double weightedScore = 0.0;
        double totalWeight = 0.0;
        boolean requiredPassed = true;
        List<Map<String, Object>> details = new ArrayList<>();
        for (Object item : criteria) {
            if (!(item instanceof Map<?, ?> criterion)) {
                continue;
            }
            String name = Objects.toString(criterion.get("name"), "criterion");
            double weight = Math.max(0.0, decimalOrDefault(criterion.get("weight"), 1.0));
            boolean required = Boolean.TRUE.equals(criterion.get("required"));
            List<String> keywords = strings(firstPresent(criterion, "keywords", "requiredKeywords", "mustContain"));
            List<String> matched = keywords.stream()
                    .filter(keyword -> normalizedOutput.contains(keyword.toLowerCase(Locale.ROOT)))
                    .toList();
            double criterionScore = keywords.isEmpty() ? 1.0 : (double) matched.size() / keywords.size();
            double threshold = decimalOrDefault(criterion.get("threshold"), required ? 1.0 : 0.0);
            boolean criterionPassed = criterionScore >= threshold;
            if (required && !criterionPassed) {
                requiredPassed = false;
            }
            weightedScore += criterionScore * weight;
            totalWeight += weight;
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("name", name);
            detail.put("weight", weight);
            detail.put("required", required);
            detail.put("threshold", threshold);
            detail.put("score", criterionScore);
            detail.put("passed", criterionPassed);
            detail.put("keywords", keywords);
            detail.put("matched", matched);
            details.add(detail);
        }
        double finalScore = totalWeight == 0.0 ? 1.0 : weightedScore / totalWeight;
        boolean passed = finalScore >= minScore && requiredPassed;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("passed", passed);
        result.put("configured", true);
        result.put("score", finalScore);
        result.put("minScore", minScore);
        result.put("requiredPassed", requiredPassed);
        result.put("criteria", details);
        return result;
    }

    private Object firstPresent(Map<?, ?> source, String... keys) {
        for (String key : keys) {
            if (source.containsKey(key)) {
                return source.get(key);
            }
        }
        return null;
    }

    private List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(Objects::toString).filter(StringUtils::hasText).map(String::strip).toList();
    }

    private long longNumber(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double decimal(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private double decimalOrDefault(Object value, double defaultValue) {
        return value instanceof Number number ? number.doubleValue() : defaultValue;
    }
}
