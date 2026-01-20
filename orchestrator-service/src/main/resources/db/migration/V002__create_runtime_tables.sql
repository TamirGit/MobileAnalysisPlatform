-- Runtime tables for analysis execution

CREATE TABLE analysis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_path VARCHAR(500) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    analysis_config_id BIGINT NOT NULL REFERENCES analysis_config(id),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_analysis_status ON analysis(status);
CREATE INDEX idx_analysis_file_type ON analysis(file_type);
CREATE INDEX idx_analysis_created_at ON analysis(created_at);

CREATE TABLE analysis_task (
    id BIGSERIAL PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analysis(id),
    task_config_id BIGINT NOT NULL REFERENCES task_config(id),
    engine_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    depends_on_task_id BIGINT REFERENCES analysis_task(id),
    attempts INT NOT NULL DEFAULT 0,
    output_path VARCHAR(500),
    error_message TEXT,
    idempotency_key UUID NOT NULL UNIQUE,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    last_heartbeat_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(analysis_id, task_config_id)
);

CREATE INDEX idx_analysis_task_analysis ON analysis_task(analysis_id);
CREATE INDEX idx_analysis_task_status ON analysis_task(status);
CREATE INDEX idx_analysis_task_idempotency ON analysis_task(idempotency_key);
CREATE INDEX idx_analysis_task_depends ON analysis_task(depends_on_task_id);
CREATE INDEX idx_analysis_task_heartbeat ON analysis_task(last_heartbeat_at) WHERE status = 'RUNNING';