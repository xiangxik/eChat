package com.xiangxik.echat.chatbot.service.context;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPolicyParserValidatorEngineTest {

    private final ContextPolicyParser parser = new ContextPolicyParser();
    private final ContextPolicyValidator validator = new ContextPolicyValidator(parser);
    private final ContextEngine engine = new ContextEngine(new SimpleTokenEstimator());

    @Test
    void parsesAndValidatesSampleDsl() {
        ContextPolicyDefinition policy = parser.parse(sampleDsl());
        ContextPolicyValidationResult validation = validator.validate(sampleDsl());

        assertThat(policy.name()).isEqualTo("support-bot-v1");
        assertThat(policy.maxTokens()).isEqualTo(12000);
        assertThat(policy.variables()).extracting(ContextPolicyDefinition.VariableDefinition::name)
                .containsExactly("conversation", "shortTermMemory", "longTermMemory", "userProfile", "retrievalResults");
        assertThat(policy.budgetReserves()).hasSize(4);
        assertThat(policy.rules()).extracting(ContextPolicyDefinition.PolicyRule::type)
                .containsExactly("include", "include", "exclude", "truncate");
        assertThat(validation.valid()).isTrue();
        assertThat(validation.errors()).isEmpty();
    }

    @Test
    void validatorReportsClearDslErrors() {
        ContextPolicyValidationResult validation = validator.validate("""
                <contextPolicy name="bad" maxTokens="100">
                  <variables>
                    <var name="unknown" source="unknown.source" />
                  </variables>
                  <budget>
                    <reserve target="missing" tokens="500" />
                  </budget>
                  <rules>
                    <truncate target="unknown" strategy="newest-first" />
                  </rules>
                  <output>
                    <section name="missing" />
                  </output>
                </contextPolicy>
                """);

        assertThat(validation.valid()).isFalse();
        assertThat(validation.errors()).extracting(ContextDslError::tag)
                .contains("var", "reserve", "truncate", "section");
        assertThat(validation.errors()).extracting(ContextDslError::reason)
                .anyMatch(reason -> reason.contains("Unsupported variable"))
                .anyMatch(reason -> reason.contains("Reserved tokens exceed maxTokens"));
    }

    @Test
    void engineAssemblesContextWithRulesAndBudget() {
        ContextPolicyDefinition policy = validator.validateAndParse(sampleDsl());
        ContextAssemblyRequest request = new ContextAssemblyRequest(
                1L,
                2L,
                "user-1",
                "My VPN is down again.",
                Map.of(),
                List.of(
                        new ContextMessage("USER", "Older message that should be removed when budget is tight."
                                .repeat(80), Map.of()),
                        new ContextMessage("ASSISTANT", "Previous answer", Map.of())
                ),
                List.of(new ContextMemoryItem("User prefers concise answers", 0, Map.of())),
                List.of(
                        new ContextMemoryItem("VPN outage last week", 0.80, Map.of()),
                        new ContextMemoryItem("Low confidence note", 0.50, Map.of())
                ),
                Map.of("tier", "enterprise"),
                List.of(new ContextMemoryItem("Public KB article", 0.91, Map.of())),
                List.of(),
                Map.of("locale", "en-US")
        );

        ContextAssemblyResult result = engine.assemble(policy, request);

        assertThat(result.messages()).extracting(ContextMessage::role).contains("SYSTEM", "USER");
        assertThat(result.sections()).extracting(ContextSection::name)
                .containsExactly("system", "userProfile", "shortTermMemory", "longTermMemory", "conversation", "retrievalResults");
        assertThat(section(result, "longTermMemory").content()).contains("VPN outage last week")
                .doesNotContain("Low confidence note");
        assertThat(section(result, "conversation").content()).contains("My VPN is down again")
                .doesNotContain("Older message");
        assertThat(result.tokenBudgetReport().truncatedSections()).contains("conversation");
    }

    @Test
    void engineExcludesSensitiveRetrievalResults() {
        ContextPolicyDefinition policy = validator.validateAndParse(sampleDsl());
        ContextAssemblyRequest request = new ContextAssemblyRequest(
                1L,
                2L,
                "user-1",
                "Need help",
                Map.of(),
                List.of(),
                List.of(),
                List.of(new ContextMemoryItem("Relevant memory", 0.90, Map.of())),
                Map.of(),
                List.of(new ContextMemoryItem("Secret search result", 0.90, Map.of("sensitive", true))),
                List.of(),
                Map.of()
        );

        ContextAssemblyResult result = engine.assemble(policy, request);

        assertThat(result.sections()).extracting(ContextSection::name).doesNotContain("retrievalResults");
    }

    private ContextSection section(ContextAssemblyResult result, String name) {
        return result.sections().stream()
                .filter(section -> name.equals(section.name()))
                .findFirst()
                .orElseThrow();
    }

    private String sampleDsl() {
        return """
                <contextPolicy name="support-bot-v1" maxTokens="12000">
                  <system priority="100">
                    You are a helpful enterprise support assistant.
                  </system>

                  <variables>
                    <var name="conversation" source="conversation.messages" maxMessages="20" />
                    <var name="shortTermMemory" source="memory.shortTerm" limit="10" />
                    <var name="longTermMemory" source="memory.longTerm" topK="8" minScore="0.72" />
                    <var name="userProfile" source="user.profile" optional="true" />
                    <var name="retrievalResults" source="retrieval.vector" topK="6" />
                  </variables>

                  <budget>
                    <reserve target="system" tokens="1200" />
                    <reserve target="conversation" tokens="120" />
                    <reserve target="longTermMemory" tokens="2500" />
                    <reserve target="retrievalResults" tokens="2500" />
                  </budget>

                  <rules>
                    <include when="conversation.latestUserMessage.exists" target="conversation" />
                    <include when="longTermMemory.score &gt; 0.72" target="longTermMemory" />
                    <exclude when="metadata.sensitive == true" target="retrievalResults" />
                    <truncate target="conversation" strategy="oldest-first" />
                  </rules>

                  <output>
                    <section name="system" />
                    <section name="userProfile" optional="true" />
                    <section name="shortTermMemory" optional="true" />
                    <section name="longTermMemory" optional="true" />
                    <section name="conversation" />
                    <section name="retrievalResults" optional="true" />
                  </output>
                </contextPolicy>
                """;
    }
}