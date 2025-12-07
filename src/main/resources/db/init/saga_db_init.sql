-- ============================================================
-- SAGA_DB 초기화 스크립트
-- Saga 분산 트랜잭션 관리 테이블 전용
-- ============================================================

-- ============================================================
-- DROP EXISTING TABLES (for clean initialization)
-- ============================================================
DROP TABLE IF EXISTS saga_step_log CASCADE;
DROP TABLE IF EXISTS saga_instance CASCADE;

-- ============================================================
-- 1. Saga 인스턴스 테이블
-- ============================================================
CREATE TABLE saga_instance (
    saga_id VARCHAR(36) PRIMARY KEY,
    saga_type VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'STARTED',
    executor_id VARCHAR(255),
    current_step VARCHAR(100),
    current_step_index INTEGER DEFAULT 0,
    context_data TEXT,
    compensation_data TEXT,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    failure_reason VARCHAR(1000),
    retry_count INTEGER DEFAULT 0,
    max_retries INTEGER DEFAULT 3,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    modified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_saga_status CHECK (status IN ('STARTED', 'PROCESSING', 'COMPLETED', 'COMPENSATING', 'COMPENSATED', 'FAILED'))
);

CREATE INDEX idx_saga_type ON saga_instance(saga_type);
CREATE INDEX idx_saga_status ON saga_instance(status);
CREATE INDEX idx_saga_created ON saga_instance(created_at);
CREATE INDEX idx_saga_executor ON saga_instance(executor_id);
CREATE INDEX idx_saga_completed ON saga_instance(completed_at);

COMMENT ON TABLE saga_instance IS 'Saga 인스턴스';
COMMENT ON COLUMN saga_instance.saga_id IS 'Saga ID (UUID)';
COMMENT ON COLUMN saga_instance.saga_type IS 'Saga 타입 (예: MissionCompletionSaga)';
COMMENT ON COLUMN saga_instance.status IS 'Saga 상태 (STARTED, PROCESSING, COMPLETED, COMPENSATING, COMPENSATED, FAILED)';
COMMENT ON COLUMN saga_instance.executor_id IS '실행자 ID';
COMMENT ON COLUMN saga_instance.current_step IS '현재 실행 중인 Step';
COMMENT ON COLUMN saga_instance.current_step_index IS '현재 Step 인덱스';
COMMENT ON COLUMN saga_instance.context_data IS 'Saga 컨텍스트 데이터 (JSON)';
COMMENT ON COLUMN saga_instance.compensation_data IS '보상 데이터 (JSON)';
COMMENT ON COLUMN saga_instance.started_at IS '시작 시간';
COMMENT ON COLUMN saga_instance.completed_at IS '완료 시간';
COMMENT ON COLUMN saga_instance.failure_reason IS '실패 사유';
COMMENT ON COLUMN saga_instance.retry_count IS '재시도 횟수';
COMMENT ON COLUMN saga_instance.max_retries IS '최대 재시도 횟수';

-- ============================================================
-- 2. Saga Step 실행 로그 테이블
-- ============================================================
CREATE TABLE saga_step_log (
    id BIGSERIAL PRIMARY KEY,
    saga_id VARCHAR(36) NOT NULL REFERENCES saga_instance(saga_id) ON DELETE CASCADE,
    step_name VARCHAR(100) NOT NULL,
    step_index INTEGER,
    status VARCHAR(20) NOT NULL,
    execution_type VARCHAR(20) DEFAULT 'FORWARD',
    duration_ms BIGINT,
    input_data TEXT,
    output_data TEXT,
    error_message VARCHAR(1000),
    stack_trace TEXT,
    retry_attempt INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_step_status CHECK (status IN ('PENDING', 'EXECUTING', 'COMPLETED', 'FAILED', 'COMPENSATING', 'COMPENSATED', 'SKIPPED')),
    CONSTRAINT chk_execution_type CHECK (execution_type IN ('FORWARD', 'COMPENSATION'))
);

CREATE INDEX idx_step_log_saga ON saga_step_log(saga_id);
CREATE INDEX idx_step_log_status ON saga_step_log(status);
CREATE INDEX idx_step_log_created ON saga_step_log(created_at);
CREATE INDEX idx_step_log_step_name ON saga_step_log(saga_id, step_name);

COMMENT ON TABLE saga_step_log IS 'Saga Step 실행 로그';
COMMENT ON COLUMN saga_step_log.id IS 'ID';
COMMENT ON COLUMN saga_step_log.saga_id IS 'Saga ID';
COMMENT ON COLUMN saga_step_log.step_name IS 'Step 이름';
COMMENT ON COLUMN saga_step_log.step_index IS 'Step 인덱스';
COMMENT ON COLUMN saga_step_log.status IS 'Step 상태 (PENDING, EXECUTING, COMPLETED, FAILED, COMPENSATING, COMPENSATED, SKIPPED)';
COMMENT ON COLUMN saga_step_log.execution_type IS '실행 유형 (FORWARD, COMPENSATION)';
COMMENT ON COLUMN saga_step_log.duration_ms IS '실행 시간 (밀리초)';
COMMENT ON COLUMN saga_step_log.input_data IS '입력 데이터 (JSON)';
COMMENT ON COLUMN saga_step_log.output_data IS '출력 데이터 (JSON)';
COMMENT ON COLUMN saga_step_log.error_message IS '에러 메시지';
COMMENT ON COLUMN saga_step_log.stack_trace IS '스택 트레이스';
COMMENT ON COLUMN saga_step_log.retry_attempt IS '재시도 횟수';

-- ============================================================
-- END OF INITIALIZATION
-- ============================================================
