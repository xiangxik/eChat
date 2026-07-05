package com.xiangxik.echat.chatbot.service.context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import static java.util.Map.entry;

@Component
public class ContextPolicyValidator {

    private static final Set<String> BUILT_IN_VARIABLES = Set.of("conversation", "context", "shortTermMemory",
            "longTermMemory", "userProfile", "retrievalResults", "toolResults", "runtime", "metadata");
        private static final Map<String, Set<String>> ALLOWED_ATTRIBUTES = Map.ofEntries(
            entry("contextPolicy", Set.of("name", "maxTokens")),
            entry("system", Set.of("priority")),
            entry("variables", Set.of()),
            entry("var", Set.of("name", "source", "maxMessages", "limit", "topK", "minScore", "optional")),
            entry("budget", Set.of()),
            entry("reserve", Set.of("target", "tokens")),
            entry("rules", Set.of()),
            entry("include", Set.of("when", "target")),
            entry("exclude", Set.of("when", "target")),
            entry("truncate", Set.of("target", "strategy")),
            entry("output", Set.of()),
            entry("section", Set.of("name", "optional"))
    );

    private final ContextPolicyParser parser;

    public ContextPolicyValidator(ContextPolicyParser parser) {
        this.parser = parser;
    }

    public ContextPolicyValidationResult validate(String dslContent) {
        List<ContextDslError> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        ContextPolicyDefinition policy;
        try {
            policy = parser.parse(dslContent);
            validateTagsAndAttributes(parser.parseDocument(dslContent).getDocumentElement(), dslContent, errors);
            validatePolicy(policy, errors, warnings);
        } catch (ContextDslException ex) {
            errors.addAll(ex.getErrors());
            return new ContextPolicyValidationResult(false, List.copyOf(errors), List.of(), null, 0);
        }
        return new ContextPolicyValidationResult(errors.isEmpty(), List.copyOf(errors), List.copyOf(warnings),
                policy.name(), policy.maxTokens());
    }

    public ContextPolicyDefinition validateAndParse(String dslContent) {
        ContextPolicyValidationResult result = validate(dslContent);
        if (!result.valid()) {
            throw new ContextDslException(result.errors());
        }
        return parser.parse(dslContent);
    }

    private void validateTagsAndAttributes(Element element, String dslContent, List<ContextDslError> errors) {
        String tag = element.getTagName();
        if (!ALLOWED_ATTRIBUTES.containsKey(tag)) {
            errors.add(new ContextDslError(ContextPolicyParser.lineOf(dslContent, tag), tag, "Unsupported tag"));
            return;
        }
        Set<String> allowed = ALLOWED_ATTRIBUTES.get(tag);
        for (int index = 0; index < element.getAttributes().getLength(); index++) {
            String attribute = element.getAttributes().item(index).getNodeName();
            if (!allowed.contains(attribute)) {
                errors.add(new ContextDslError(ContextPolicyParser.lineOf(dslContent, tag), tag,
                        "Unsupported attribute: " + attribute));
            }
        }
        for (Element child : ContextPolicyParser.childElements(element)) {
            validateTagsAndAttributes(child, dslContent, errors);
        }
    }

    private void validatePolicy(ContextPolicyDefinition policy, List<ContextDslError> errors, List<String> warnings) {
        if (policy.maxTokens() <= 0) {
            errors.add(new ContextDslError(1, "contextPolicy", "maxTokens must be greater than 0"));
        }
        if (policy.systemBlocks().isEmpty()) {
            warnings.add("Policy has no system section");
        }
        if (policy.outputSections().isEmpty()) {
            errors.add(new ContextDslError(1, "output", "At least one output section is required"));
        }

        Map<String, ContextPolicyDefinition.VariableDefinition> variables = new LinkedHashMap<>();
        for (ContextPolicyDefinition.VariableDefinition variable : policy.variables()) {
            if (!BUILT_IN_VARIABLES.contains(variable.name())) {
                errors.add(new ContextDslError(variable.line(), "var", "Unsupported variable: " + variable.name()));
            }
            if (variables.put(variable.name(), variable) != null) {
                errors.add(new ContextDslError(variable.line(), "var", "Duplicate variable: " + variable.name()));
            }
            if (variable.limit() < 0 || variable.maxMessages() < 0 || variable.topK() < 0 || variable.minScore() < 0) {
                errors.add(new ContextDslError(variable.line(), "var", "Variable limits must not be negative"));
            }
        }

        int reservedTokens = 0;
        for (ContextPolicyDefinition.BudgetReserve reserve : policy.budgetReserves()) {
            if (reserve.tokens() <= 0) {
                errors.add(new ContextDslError(reserve.line(), "reserve", "Reserved tokens must be greater than 0"));
            }
            if (!"system".equals(reserve.target()) && !variables.containsKey(reserve.target())) {
                errors.add(new ContextDslError(reserve.line(), "reserve", "Budget target is not a declared section: "
                        + reserve.target()));
            }
            reservedTokens += Math.max(reserve.tokens(), 0);
        }
        if (policy.maxTokens() > 0 && reservedTokens > policy.maxTokens()) {
            errors.add(new ContextDslError(1, "budget", "Reserved tokens exceed maxTokens"));
        }

        for (ContextPolicyDefinition.PolicyRule rule : policy.rules()) {
            if (!variables.containsKey(rule.target())) {
                errors.add(new ContextDslError(rule.line(), rule.type(), "Rule target is not a declared variable: "
                        + rule.target()));
            }
            if (("include".equals(rule.type()) || "exclude".equals(rule.type())) && rule.when().isBlank()) {
                errors.add(new ContextDslError(rule.line(), rule.type(), "Rule requires a when expression"));
            }
            if ("truncate".equals(rule.type()) && !List.of("oldest-first").contains(rule.strategy())) {
                errors.add(new ContextDslError(rule.line(), "truncate", "Unsupported truncate strategy: " + rule.strategy()));
            }
        }

        for (ContextPolicyDefinition.OutputSection section : policy.outputSections()) {
            if (!"system".equals(section.name()) && !variables.containsKey(section.name())) {
                errors.add(new ContextDslError(section.line(), "section", "Output section is not declared: " + section.name()));
            }
        }
    }
}