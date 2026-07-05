CREATE TABLE eval_datasets (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(180) NOT NULL UNIQUE,
    description TEXT,
    chatbot_id BIGINT NOT NULL REFERENCES chatbot_configs(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE eval_cases (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES eval_datasets(id) ON DELETE CASCADE,
    input TEXT NOT NULL,
    expected_behavior TEXT,
    expected_keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE eval_runs (
    id BIGSERIAL PRIMARY KEY,
    dataset_id BIGINT NOT NULL REFERENCES eval_datasets(id) ON DELETE RESTRICT,
    chatbot_id BIGINT NOT NULL REFERENCES chatbot_configs(id) ON DELETE RESTRICT,
    model_id BIGINT REFERENCES model_configs(id) ON DELETE SET NULL,
    context_policy_id BIGINT REFERENCES context_policies(id) ON DELETE SET NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT eval_runs_status_check CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED'))
);

CREATE TABLE eval_results (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES eval_runs(id) ON DELETE CASCADE,
    case_id BIGINT NOT NULL REFERENCES eval_cases(id) ON DELETE RESTRICT,
    output TEXT,
    context_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    token_budget_report JSONB NOT NULL DEFAULT '{}'::jsonb,
    scores JSONB NOT NULL DEFAULT '{}'::jsonb,
    passed BOOLEAN NOT NULL DEFAULT FALSE,
    error TEXT
);

CREATE INDEX eval_datasets_chatbot_idx ON eval_datasets(chatbot_id);
CREATE INDEX eval_cases_dataset_idx ON eval_cases(dataset_id, id ASC);
CREATE INDEX eval_runs_dataset_status_idx ON eval_runs(dataset_id, status);
CREATE INDEX eval_runs_chatbot_started_idx ON eval_runs(chatbot_id, started_at DESC);
CREATE INDEX eval_results_run_idx ON eval_results(run_id, id ASC);
CREATE INDEX eval_results_case_idx ON eval_results(case_id);
