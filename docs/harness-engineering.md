# Harness Engineering

Harness engineering in eChat means building repeatable evaluation loops around prompts, context policies, models, retrieval, and runtime settings. The goal is not to clone any proprietary agent framework, but to provide enterprise controls for measuring behavior before changes reach users.

## Current Harness

The admin eval harness stores datasets, cases, runs, and results. A run can target a chatbot, model, or context policy override and records context snapshots, token budget reports, scores, pass/fail status, and errors. This supports regression checks for context policy edits and provider/model changes.

## Eval Enhancements

Golden conversation replay is configured per case through metadata. When `goldenReplay` is enabled on a run, the harness injects `metadata.goldenConversation` into the isolated context before the case input, without writing any production conversation rows.

```json
{
	"goldenConversation": [
		{ "role": "USER", "content": "My VPN client is failing." },
		{ "role": "ASSISTANT", "content": "Check your VPN profile first." }
	]
}
```

Rubric scoring is deterministic in this MVP. A run-level rubric can define weighted keyword criteria and a minimum score; a case-level `metadata.rubric` can override or extend it for a specific case. Each result stores `scores.rubricScoring` with per-criterion matches, score, and pass/fail status.

```json
{
	"minScore": 0.9,
	"criteria": [
		{ "name": "vpn reset coverage", "required": true, "keywords": ["VPN", "reset"], "weight": 2 },
		{ "name": "tone", "keywords": ["please", "thanks"], "threshold": 0.5 }
	]
}
```

Release gates are evaluated after all cases finish. The run summary records aggregate metrics, `releaseGate`, and `releaseGatePassed`. Supported checks include `minPassRate`, `minPassedCases`, `maxFailedCases`, `maxAverageLatencyMillis`, and `maxEstimatedCostUsd`.

```json
{
	"minPassRate": 0.95,
	"maxFailedCases": 0,
	"maxAverageLatencyMillis": 30000,
	"maxEstimatedCostUsd": 1.0
}
```

Cost and latency metrics are captured for every result under `scores.metrics`. Latency is measured around the provider call. Cost uses provider response metadata when `costUsd` or `estimatedCostUsd` is present; otherwise it estimates from `costPer1kTokensUsd` and total estimated tokens. Per-case `maxLatencyMillis` and `maxEstimatedCostUsd` budgets contribute to result pass/fail.

## Recommended Workflow

1. Create small datasets for critical chatbot jobs and known failure modes.
2. Add expected behavior, required keywords, and forbidden phrases where useful.
3. Run evaluations before changing production provider/model/context policy settings.
4. Compare token budgets, context warnings, and output scores before rollout.
5. Keep audit logs for configuration changes linked to evaluation runs.

## Enterprise Extensions

Future phases should add scheduled evaluations, human review queues, LLM-as-judge rubric adapters, historical baseline comparison, and policy enforcement that prevents risky context policies from being enabled without a passing release gate.