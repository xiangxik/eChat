package com.xiangxik.echat.chatbot.service.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
        applyTruncateRules(policy, drafts, reserves, truncatedSections);

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
                case "shortTermMemory" -> memoryDraft("shortTermMemory", variable, request.shortTermMemory());
                case "longTermMemory" -> memoryDraft("longTermMemory", variable, request.longTermMemory());
                case "retrievalResults" -> memoryDraft("retrievalResults", variable, request.retrievalResults());
                case "toolResults" -> memoryDraft("toolResults", variable, request.toolResults());
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
                                     List<ContextMemoryItem> items) {
        List<ContextMemoryItem> filtered = items.stream()
                .filter(item -> variable.minScore() <= 0 || item.score() >= variable.minScore())
                .limit(firstPositive(variable.topK(), variable.limit(), items.size()))
                .toList();
        String content = filtered.stream()
                .map(item -> "- " + item.content() + (item.score() > 0 ? " (score=" + item.score() + ")" : ""))
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

    private boolean evaluate(String expression, String target, ContextAssemblyRequest request) {
        String normalized = expression == null ? "" : expression.strip();
        if ("conversation.latestUserMessage.exists".equals(normalized)) {
            return request.latestUserMessage() != null && !request.latestUserMessage().isBlank();
        }
        if (normalized.startsWith(target + ".score >")) {
            double threshold = Double.parseDouble(normalized.substring((target + ".score >").length()).strip());
            return itemsFor(target, request).stream().anyMatch(item -> item.score() > threshold);
        }
        if ("metadata.sensitive == true".equals(normalized)) {
            return Boolean.TRUE.equals(request.metadata().get("sensitive"))
                    || itemsFor(target, request).stream().anyMatch(item -> Boolean.TRUE.equals(item.metadata().get("sensitive")));
        }
        return false;
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