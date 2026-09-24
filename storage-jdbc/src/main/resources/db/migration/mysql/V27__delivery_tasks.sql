-- Задачи доставки чека покупателю: по одной на канал и вид нагрузки.
-- Чек уходит покупателю в фоне и досылается после перезапуска узла.
-- Получатель (destination) - контакт покупателя из чека, в журнал не пишется.
CREATE TABLE IF NOT EXISTS delivery_tasks (
    id VARCHAR(255) PRIMARY KEY,
    cashbox_id VARCHAR(64) NOT NULL,
    document_id VARCHAR(255) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    destination TEXT,
    payload_type VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at BIGINT NOT NULL,
    failure_code VARCHAR(64),
    failure_ru TEXT,
    failure_kk TEXT,
    failure_en TEXT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_delivery_tasks_document
    ON delivery_tasks (document_id);

CREATE INDEX idx_delivery_tasks_due
    ON delivery_tasks (status, next_attempt_at);
