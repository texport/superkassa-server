-- Задачи доставки чека покупателю: по одной на канал и вид нагрузки.
-- Чек уходит покупателю в фоне и досылается после перезапуска узла.
-- Получатель (destination) - контакт покупателя из чека, в журнал не пишется.
CREATE TABLE IF NOT EXISTS delivery_tasks (
    id TEXT PRIMARY KEY,
    cashbox_id TEXT NOT NULL,
    document_id TEXT NOT NULL,
    channel TEXT NOT NULL,
    destination TEXT,
    payload_type TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at INTEGER NOT NULL,
    failure_code TEXT,
    failure_ru TEXT,
    failure_kk TEXT,
    failure_en TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_delivery_tasks_document
    ON delivery_tasks (document_id);

CREATE INDEX IF NOT EXISTS idx_delivery_tasks_due
    ON delivery_tasks (status, next_attempt_at);
