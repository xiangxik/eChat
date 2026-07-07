package com.xiangxik.echat.chatbot.service.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ContextEngine {

    private final TokenEstimator tokenEstimator;

    public ContextEngine(TokenEstimator tokenEstimator) {
        this.tokenEstimator = tokenEstimator;
    }

    public ContextAssemblyResult assemble(ContextPolicyDefinition policy, ContextAssemblyRequest request) {
        Map<String, SectionDraft> drafts = resolveDrafts(policy, request);
        Map<String, Integer> reserves = policy.budgetReserves().stream()
                .collect(Collectors.toMap(ContextPolicyDefinition.BudgetReserve::target,
                        ContextPolicyDefinition.BudgetReserve::tokens, (first, second) -> second, LinkedHashMap::new));
        List<String> warnings = new ArrayList<>();
        List<String> truncatedSections = new ArrayList<>();

        applyIncludeRules(policy, request, drafts);
        applyExcludeRules(policy, request, drafts);
        applyRedactRules(policy, request, drafts, warnings);
        applyTruncateRules(policy, drafts, reserves, truncatedSections);
        applyHardBudgetReserves(policy, drafts, truncatedSections);

        List<ContextSection> sections = new ArrayList<>();
        List<ContextMessage> messages = new ArrayList<>();
        Map<String, Integer> actualTokens = new LinkedHashMap<>();
        int total = 0;
        for (ContextPolicyDefinition.OutputSection outputSection : policy.outputSections()) {
            SectionDraft draft = drafts.get(outputSection.name());
            if (draft == null || !draft.included || draft.content().isBlank()) {
                if (!outputSection.optional()) {
                    warnings.add("Required section is empty or excluded: " + outputSection.name());
                }
                continue;
            }
            int tokens = tokenEstimator.estimate(draft.content());
            sections.add(new ContextSection(outputSection.name(), draft.role, draft.content(), tokens, true));
            messages.add(new ContextMessage(draft.role, draft.content(), Map.of("section", outputSection.name())));
            actualTokens.put(outputSection.name(), tokens);
            total += tokens;
        }
        if (total > policy.maxTokens()) {
            warnings.add("Assembled context exceeds maxTokens: " + total + " > " + policy.maxTokens());
        }

        return new ContextAssemblyResult(List.copyOf(messages), List.copyOf(sections),
                new TokenBudgetReport(policy.maxTokens(), Map.copyOf(reserves), Map.copyOf(actualTokens), total,
                        List.copyOf(truncatedSections)), List.copyOf(warnings));
    }

    private Map<String, SectionDraft> resolveDrafts(ContextPolicyDefinition policy, ContextAssemblyRequest request) {
        Map<String, SectionDraft> drafts = new LinkedHashMap<>();
        String system = policy.systemBlocks().stream()
                .sorted(Comparator.comparingInt(ContextPolicyDefinition.SystemBlock::priority).reversed())
                .map(ContextPolicyDefinition.SystemBlock::content)
                .filter(content -> !content.isBlank())
                .collect(Collectors.joining("\n\n"));
        drafts.put("system", new SectionDraft("SYSTEM", system, true, List.of()));

        for (ContextPolicyDefinition.VariableDefinition variable : policy.variables()) {
            drafts.put(variable.name(), switch (variable.name()) {
                case "conversation" -> conversationDraft(variable, request);
                case "shortTermMemory" -> memoryDraft("shortTermMemory", variable, policy, request.shortTermMemory());
                case "longTermMemory" -> memoryDraft("longTermMemory", variable, policy, request.longTermMemory());
                case "retrievalResults" -> memoryDraft("retrievalResults", variable, policy, request.retrievalResults());
                case "toolResults" -> memoryDraft("toolResults", variable, policy, request.toolResults());
                case "userProfile" -> mapDraft("userProfile", request.userProfile());
                case "runtime" -> mapDraft("runtime", request.runtime());
                case "metadata" -> mapDraft("metadata", request.metadata());
                default -> new SectionDraft("USER", "", variable.optional(), List.of());
            });
        }
        return drafts;
    }

    private SectionDraft conversationDraft(ContextPolicyDefinition.VariableDefinition variable,
                                           ContextAssemblyRequest request) {
        List<ContextMessage> messages = new ArrayList<>(request.conversation());
        if (request.latestUserMessage() != null && !request.latestUserMessage().isBlank()
                && messages.stream().noneMatch(message -> request.latestUserMessage().equals(message.content()))) {
            messages.add(new ContextMessage("USER", request.latestUserMessage(), Map.of("latest", true)));
        }
        if (variable.maxMessages() > 0 && messages.size() > variable.maxMessages()) {
            messages = messages.subList(messages.size() - variable.maxMessages(), messages.size());
        }
        String content = messages.stream()
                .map(message -> message.role().toUpperCase(Locale.ROOT) + ": " + message.content())
                .collect(Collectors.joining("\n"));
        return new SectionDraft("USER", content, true, messages);
    }

    private SectionDraft memoryDraft(String name, ContextPolicyDefinition.VariableDefinition variable,
                                     ContextPolicyDefinition policy, List<ContextMemoryItem> items) {
        List<ContextMemoryItem> filtered = items.stream()
                .filter(item -> variable.minScore() <= 0 || item.score() >= variable.minScore())
                .filter(item -> passesTrustRules(name, policy, item))
                .limit(firstPositive(variable.topK(), variable.limit(), items.size()))
                .toList();
        String content = filtered.stream()
                .map(item -> "- " + redactedItemContent(name, policy, item) + trustLabel(item)
                        + (item.score() > 0 ? " (score=" + item.score() + ")" : ""))
                .collect(Collectors.joining("\n"));
        return new SectionDraft("USER", content.isBlank() ? "" : "[" + name + "]\n" + content, true, List.of());
    }

    private SectionDraft mapDraft(String name, Map<String, Object> values) {
        String content = values.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + Objects.toString(entry.getValue()))
                .collect(Collectors.joining("\n"));
        return new SectionDraft("USER", content.isBlank() ? "" : "[" + name + "]\n" + content, true, List.of());
    }

    private void applyIncludeRules(ContextPolicyDefinition policy, ContextAssemblyRequest request,
                                   Map<String, SectionDraft> drafts) {
        Map<String, List<ContextPolicyDefinition.PolicyRule>> includeRules = policy.rules().stream()
                .filter(rule -> "include".equals(rule.type()))
                .collect(Collectors.groupingBy(ContextPolicyDefinition.PolicyRule::target));
        includeRules.forEach((target, rules) -> {
            SectionDraft draft = drafts.get(target);
            if (draft != null) {
                drafts.put(target, draft.withIncluded(rules.stream().anyMatch(rule -> evaluate(rule.when(), target, request))));
            }
        });
    }

    private void applyExcludeRules(ContextPolicyDefinition policy, ContextAssemblyRequest request,
                                   Map<String, SectionDraft> drafts) {
        for (ContextPolicyDefinition.PolicyRule rule : policy.rules()) {
            if ("exclude".equals(rule.type()) && evaluate(rule.when(), rule.target(), request)) {
                SectionDraft draft = drafts.get(rule.target());
                if (draft != null) {
                    drafts.put(rule.target(), draft.withIncluded(false));
                }
            }
        }
    }

    private void applyRedactRules(ContextPolicyDefinition policy, ContextAssemblyRequest request,
                                  Map<String, SectionDraft> drafts, List<String> warnings) {
        for (ContextPolicyDefinition.PolicyRule rule : policy.rules()) {
            if (!"redact".equals(rule.type()) || !ruleApplies(rule, request)) {
                continue;
            }
            SectionDraft draft = drafts.get(rule.target());
            if (draft == null || draft.content().isBlank()) {
                continue;
            }
            try {
                String redacted = Pattern.compile(rule.pattern()).matcher(draft.content()).replaceAll(rule.replacement());
                drafts.put(rule.target(), new SectionDraft(draft.role, redacted, draft.included, draft.messages));
            } catch (PatternSyntaxException ex) {
                warnings.add("Invalid redact pattern for section: " + rule.target());
            }
        }
    }

    private void applyTruncateRules(ContextPolicyDefinition policy, Map<String, SectionDraft> drafts,
                                    Map<String, Integer> reserves, List<String> truncatedSections) {
        for (ContextPolicyDefinition.PolicyRule rule : policy.rules()) {
            if (!"truncate".equals(rule.type())) {
                continue;
            }
            SectionDraft draft = drafts.get(rule.target());
            Integer reserve = reserves.get(rule.target());
            if (draft == null || reserve == null || tokenEstimator.estimate(draft.content()) <= reserve) {
                continue;
            }
            if ("conversation".equals(rule.target()) && "oldest-first".equals(rule.strategy()) && !draft.messages.isEmpty()) {
                List<ContextMessage> messages = new ArrayList<>(draft.messages);
                while (messages.size() > 1 && tokenEstimator.estimate(renderConversation(messages)) > reserve) {
                    messages.removeFirst();
                }
                drafts.put(rule.target(), new SectionDraft(draft.role, renderConversation(messages), draft.included, messages));
                truncatedSections.add(rule.target());
            }
        }
    }

    private void applyHardBudgetReserves(ContextPolicyDefinition policy, Map<String, SectionDraft> drafts,
                                         List<String> truncatedSections) {
        for (ContextPolicyDefinition.BudgetReserve reserve : policy.budgetReserves()) {
            if (!"hard".equals(reserve.strategy())) {
                continue;
            }
            SectionDraft draft = drafts.get(reserve.target());
            if (draft == null || tokenEstimator.estimate(draft.content()) <= reserve.tokens()) {
                continue;
            }
            if ("conversation".equals(reserve.target()) && !draft.messages.isEmpty()) {
                drafts.put(reserve.target(), truncateConversationDraft(draft, reserve.tokens()));
            } else {
                drafts.put(reserve.target(), truncateByLines(draft, reserve.tokens()));
            }
            if (!truncatedSections.contains(reserve.target())) {
                truncatedSections.add(reserve.target());
            }
        }
    }

    private boolean evaluate(String expression, String target, ContextAssemblyRequest request) {
        String normalized = expression == null ? "" : expression.strip();
        if ("conversation.latestUserMessage.exists".equals(normalized)) {
            return request.latestUserMessage() != null && !request.latestUserMessage().isBlank();
        }
        if (normalized.equals(target + ".exists")) {
            return !itemsFor(target, request).isEmpty();
        }
        if (normalized.startsWith(target + ".count >")) {
            int threshold = Integer.parseInt(normalized.substring((target + ".count >").length()).strip());
            return itemsFor(target, request).size() > threshold;
        }
        if (normalized.startsWith(target + ".score >")) {
            double threshold = Double.parseDouble(normalized.substring((target + ".score >").length()).strip());
            return itemsFor(target, request).stream().anyMatch(item -> item.score() > threshold);
        }
        if (matchesMapExpression(normalized, "metadata", request.metadata())) {
            return true;
        }
        if (matchesMapExpression(normalized, "runtime", request.runtime())) {
            return true;
        }
        if ("metadata.sensitive == true".equals(normalized)) {
            return Boolean.TRUE.equals(request.metadata().get("sensitive"))
                    || itemsFor(target, request).stream().anyMatch(item -> Boolean.TRUE.equals(item.metadata().get("sensitive")));
        }
        return false;
    }

    private boolean ruleApplies(ContextPolicyDefinition.PolicyRule rule, ContextAssemblyRequest request) {
        return rule.when().isBlank() || evaluate(rule.when(), rule.target(), request);
    }

    private boolean passesTrustRules(String target, ContextPolicyDefinition policy, ContextMemoryItem item) {
        for (ContextPolicyDefinition.PolicyRule rule : policy.rules()) {
            if (!"filter".equals(rule.type()) || !target.equals(rule.target())) {
                continue;
            }
            if (!rule.minTrust().isBlank() && trustScore(item) < trustLevel(rule.minTrust())) {
                return false;
            }
            if (rule.minTrustScore() > 0 && trustScore(item) < rule.minTrustScore()) {
                return false;
            }
        }
        return true;
    }

    private String redactedItemContent(String target, ContextPolicyDefinition policy, ContextMemoryItem item) {
        String content = item.content();
        for (ContextPolicyDefinition.PolicyRule rule : policy.rules()) {
            if (!"redact".equals(rule.type()) || !target.equals(rule.target()) || !ruleAppliesToItem(rule, item)) {
                continue;
            }
            content = Pattern.compile(rule.pattern()).matcher(content).replaceAll(rule.replacement());
        }
        return content;
    }

    private boolean ruleAppliesToItem(ContextPolicyDefinition.PolicyRule rule, ContextMemoryItem item) {
        if (rule.when().isBlank()) {
            return true;
        }
        return "item.sensitive == true".equals(rule.when()) && Boolean.TRUE.equals(item.metadata().get("sensitive"));
    }

    private String trustLabel(ContextMemoryItem item) {
        Object source = firstPresent(item.metadata(), "source", "sourceId");
        Object trust = firstPresent(item.metadata(), "sourceTrust", "trust", "trustLevel", "trustScore");
        if (source == null && trust == null) {
            return "";
        }
        return " (source=" + Objects.toString(source, "unknown") + ", trust=" + Objects.toString(trust, "unknown") + ")";
    }

    private double trustScore(ContextMemoryItem item) {
        Object trustScore = item.metadata().get("trustScore");
        if (trustScore instanceof Number number) {
            return number.doubleValue();
        }
        Object trust = firstPresent(item.metadata(), "sourceTrust", "trust", "trustLevel");
        return trust == null ? 0 : trustLevel(Objects.toString(trust));
    }

    private double trustLevel(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "verified" -> 90;
            case "trusted" -> 70;
            case "internal" -> 50;
            case "untrusted" -> 0;
            default -> parseNumber(value);
        };
    }

    private Object firstPresent(Map<String, Object> values, String... keys) {
        for (String key : keys) {
            if (values.containsKey(key)) {
                return values.get(key);
            }
        }
        return null;
    }

    private double parseNumber(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private boolean matchesMapExpression(String expression, String prefix, Map<String, Object> values) {
        String marker = prefix + ".";
        if (!expression.startsWith(marker) || !expression.contains(" == ")) {
            return false;
        }
        String[] parts = expression.substring(marker.length()).split(" == ", 2);
        if (parts.length != 2) {
            return false;
        }
        Object actual = values.get(parts[0].strip());
        String expected = parts[1].strip().replaceAll("^['\"]|['\"]$", "");
        return Objects.toString(actual, "").equals(expected);
    }

    private SectionDraft truncateConversationDraft(SectionDraft draft, int reserve) {
        List<ContextMessage> messages = new ArrayList<>(draft.messages);
        while (messages.size() > 1 && tokenEstimator.estimate(renderConversation(messages)) > reserve) {
            messages.removeFirst();
        }
        return new SectionDraft(draft.role, renderConversation(messages), draft.included, messages);
    }

    private SectionDraft truncateByLines(SectionDraft draft, int reserve) {
        List<String> lines = new ArrayList<>(draft.content().lines().toList());
        while (lines.size() > 1 && tokenEstimator.estimate(String.join("\n", lines)) > reserve) {
            lines.removeLast();
        }
        return new SectionDraft(draft.role, String.join("\n", lines), draft.included, draft.messages);
    }

    private List<ContextMemoryItem> itemsFor(String target, ContextAssemblyRequest request) {
        return switch (target) {
            case "shortTermMemory" -> request.shortTermMemory();
            case "longTermMemory" -> request.longTermMemory();
            case "retrievalResults" -> request.retrievalResults();
            case "toolResults" -> request.toolResults();
            default -> List.of();
        };
    }

    private int firstPositive(int first, int second, int fallback) {
        if (first > 0) {
            return first;
        }
        if (second > 0) {
            return second;
        }
        return fallback;
    }

    private String renderConversation(List<ContextMessage> messages) {
        return messages.stream()
                .map(message -> message.role().toUpperCase(Locale.ROOT) + ": " + message.content())
                .collect(Collectors.joining("\n"));
    }

    private record SectionDraft(String role, String content, boolean included, List<ContextMessage> messages) {

        SectionDraft withIncluded(boolean included) {
            return new SectionDraft(role, content, included, messages);
        }
    }
}