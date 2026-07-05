package com.xiangxik.echat.chatbot.service.eval;

import com.xiangxik.echat.chatbot.domain.model.EvalCase;
import com.xiangxik.echat.chatbot.service.context.TokenBudgetReport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class EvalScoringService {

    public EvalScore score(EvalCase evalCase, String output, TokenBudgetReport tokenBudgetReport,
                           Integer maxEstimatedTokens, List<String> forbiddenPhrases) {
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
        boolean passed = nonEmptyAnswerPassed && keywordMatchPassed && tokenBudgetPassed && forbiddenPhrasePassed;

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
        scores.put("llmAsJudge", Map.of("status", "reserved"));
        return new EvalScore(scores, passed);
    }
}
