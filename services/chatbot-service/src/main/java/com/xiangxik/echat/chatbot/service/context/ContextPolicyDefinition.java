package com.xiangxik.echat.chatbot.service.context;

import java.util.List;

public record ContextPolicyDefinition(
        String name,
        int maxTokens,
        List<SystemBlock> systemBlocks,
        List<VariableDefinition> variables,
        List<BudgetReserve> budgetReserves,
        List<PolicyRule> rules,
        List<OutputSection> outputSections
) {

    public record SystemBlock(int priority, String content, int line) {
    }

    public record VariableDefinition(String name, String source, int maxMessages, int limit, int topK,
                                     double minScore, boolean optional, int line) {
    }

    public record BudgetReserve(String target, int tokens, String strategy, int line) {
    }

    public record PolicyRule(String type, String when, String target, String strategy, String minTrust,
                             double minTrustScore, String pattern, String replacement, int line) {
    }

    public record OutputSection(String name, boolean optional, int line) {
    }
}