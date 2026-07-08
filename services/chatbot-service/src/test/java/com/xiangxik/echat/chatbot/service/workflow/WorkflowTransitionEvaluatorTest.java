package com.xiangxik.echat.chatbot.service.workflow;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowTransitionEvaluatorTest {

    private final WorkflowTransitionEvaluator evaluator = new WorkflowTransitionEvaluator();

    @Test
    void evaluatesMessageMetadataAndBooleanOperators() {
        WorkflowTransitionEvaluationContext context = new WorkflowTransitionEvaluationContext(1L, 2L, "start",
                "I need billing help", "assistant response", Map.of("tier", "gold"), Map.of("handoff", true));

        assertThat(evaluator.evaluate("currentNode.key == 'start' && user.message contains 'billing'", context)).isTrue();
        assertThat(evaluator.evaluate("metadata.tier == 'gold' && workflowState.handoff", context)).isTrue();
        assertThat(evaluator.evaluate("assistant.message matches 'assistant.*'", context)).isTrue();
        assertThat(evaluator.evaluate("user.message contains 'sales' || metadata.tier == 'silver'", context)).isFalse();
    }

    @Test
    void rejectsUnknownVariablesDuringEvaluation() {
        WorkflowTransitionEvaluationContext context = new WorkflowTransitionEvaluationContext(1L, 2L, "start",
                "hello", "assistant response", Map.of(), Map.of());

        assertThatThrownBy(() -> evaluator.evaluate("unknown.value == 'x'", context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown workflow condition variable");
    }

    @Test
    void validatesExpressionSyntax() {
        evaluator.validateExpression("user.message contains 'billing' && !workflowState.done");

        assertThatThrownBy(() -> evaluator.validateExpression("user.message contains"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}