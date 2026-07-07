# Context Engine

The Context Engine assembles deterministic LLM context from a declarative DSL and runtime variables. It is designed around context engineering principles: make inputs explicit, isolate untrusted content, budget tokens before provider calls, and emit warnings when context is truncated or incomplete.

## DSL Model

Policies use a React/XML-like structure with sections such as system, variables, memory, retrieval, and metadata. The parser converts DSL content into a neutral internal policy definition. The validator checks supported tags, token reserves, section references, and model compatibility before a policy can be saved or previewed.

Built-in variables include `conversation`, `context`, `shortTermMemory`, `longTermMemory`, `userProfile`, `retrievalResults`, `toolResults`, `runtime`, and `metadata`.

## Runtime Assembly

At chat time the service loads the conversation, resolves memory and retrieval inputs, builds a runtime object with `requestId`, `traceId`, timestamp, and cancellation support, then asks the Context Engine to produce ordered provider messages. The result includes messages, per-section token estimates, truncation information, and warnings.

## Isolation and Safety

User messages, memory, retrieval results, tool outputs, and metadata are treated as untrusted data. The MVP blocks common instruction override patterns before assembly and keeps runtime warnings visible through chat responses and admin policy preview. Future phases should add rule-based redaction, source trust labels, tenant isolation, and policy-level allow/deny conditions.

## Rule Engine MVP

The DSL now has a small internal rule layer that can evolve into a fuller rule engine without changing controllers. Supported policy controls:

- Sensitive information policy: `<redact target="retrievalResults" pattern="..." replacement="[REDACTED]" />` masks matching text before provider messages are produced. `when="item.sensitive == true"` applies redaction only to memory/retrieval/tool items marked sensitive.
- Source trust filtering: `<filter target="retrievalResults" minTrust="trusted" />` keeps only items whose metadata has `sourceTrust`, `trust`, `trustLevel`, or numeric `trustScore` at or above the threshold. Trust levels are `untrusted`, `internal`, `trusted`, and `verified`.
- Conditional inclusion: `<include when="metadata.environment == 'prod'" target="retrievalResults" />` supports metadata/runtime equality, `target.exists`, `target.count > n`, score checks, and latest-message checks.
- Budget strategy: `<reserve target="retrievalResults" tokens="800" strategy="hard" />` enforces a section-level budget by truncating lines after rendering. The default `soft` strategy records the reserve but does not force truncation.

Example:

```xml
<rules>
	<include when="metadata.environment == 'prod'" target="retrievalResults" />
	<filter target="retrievalResults" minTrust="trusted" />
	<redact target="retrievalResults" when="item.sensitive == true" pattern="(?i)(apiKey|token|password)=[^\s]+" replacement="[REDACTED_SECRET]" />
</rules>
<budget>
	<reserve target="retrievalResults" tokens="800" strategy="hard" />
</budget>
```

## Extension Path

The DSL should remain a stable authoring layer while the internal policy model evolves into a rule engine. Ranking, conditional inclusion, tool result filtering, retrieval reranking, and budget allocation should plug into the internal model rather than controller code.