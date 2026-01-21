-- Configuration tables for analysis workflows

CREATE TABLE analysis_config (
    id BIGSERIAL PRIMARY KEY,
    file_type VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE task_config (
    id BIGSERIAL PRIMARY KEY,
    analysis_config_id BIGINT NOT NULL REFERENCES analysis_config(id),
    engine_type VARCHAR(50) NOT NULL,
    task_order INT NOT NULL,
    depends_on_task_config_id BIGINT REFERENCES task_config(id),
    timeout_seconds INT NOT NULL DEFAULT 300,
    max_retries INT NOT NULL DEFAULT 3,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(analysis_config_id, task_order)
);

CREATE INDEX idx_task_config_analysis ON task_config(analysis_config_id);
CREATE INDEX idx_task_config_depends ON task_config(depends_on_task_config_id);

-- Insert sample APK analysis configuration
INSERT INTO analysis_config (file_type, name, version, active) 
VALUES ('APK', 'Android Security Analysis v1', 1, true);

-- Task 1: Static Analysis (no dependency)
INSERT INTO task_config (analysis_config_id, engine_type, task_order, depends_on_task_config_id, timeout_seconds, max_retries)
VALUES (1, 'STATIC_ANALYSIS', 1, NULL, 300, 3);

-- Task 2: Signature Check (no dependency, runs in parallel)
INSERT INTO task_config (analysis_config_id, engine_type, task_order, depends_on_task_config_id, timeout_seconds, max_retries)
VALUES (1, 'SIGNATURE_CHECK', 2, NULL, 120, 3);

-- Task 3: Decompiler (depends on Task 1)
INSERT INTO task_config (analysis_config_id, engine_type, task_order, depends_on_task_config_id, timeout_seconds, max_retries)
VALUES (1, 'DECOMPILER', 3, 1, 600, 2);

-- Task 4: Dynamic Analysis (depends on Task 3)
INSERT INTO task_config (analysis_config_id, engine_type, task_order, depends_on_task_config_id, timeout_seconds, max_retries)
VALUES (1, 'DYNAMIC_ANALYSIS', 4, 3, 1800, 1);

-- Insert sample IPA analysis configuration
INSERT INTO analysis_config (file_type, name, version, active) 
VALUES ('IPA', 'iOS Security Analysis v1', 1, true);

-- Task 1: Static Analysis (no dependency)
INSERT INTO task_config (analysis_config_id, engine_type, task_order, depends_on_task_config_id, timeout_seconds, max_retries)
VALUES (2, 'STATIC_ANALYSIS', 1, NULL, 300, 3);

-- Task 2: Signature Check (no dependency)
INSERT INTO task_config (analysis_config_id, engine_type, task_order, depends_on_task_config_id, timeout_seconds, max_retries)
VALUES (2, 'SIGNATURE_CHECK', 2, NULL, 120, 3);