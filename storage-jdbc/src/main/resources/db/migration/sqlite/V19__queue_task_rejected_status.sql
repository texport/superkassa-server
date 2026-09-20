-- Терминальное состояние задачи очереди.
--
-- Отвергнутую ОФД по существу команду и команду, запрос для которой
-- не удалось собрать, повторять бессмысленно: без этого состояния
-- очередь повторяла такую задачу вечно.
--
-- SQLite не умеет менять ограничение CHECK, поэтому таблица
-- пересоздаётся с переносом строк.

CREATE TABLE queue_task_new (
    id TEXT PRIMARY KEY,
    cashbox_id TEXT NOT NULL,
    lane TEXT NOT NULL CHECK (lane IN ('ONLINE', 'OFFLINE')),
    type TEXT NOT NULL,
    payload_ref TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'SENT', 'FAILED', 'REJECTED')),
    attempt INTEGER NOT NULL DEFAULT 0,
    next_attempt_at INTEGER,
    last_error TEXT
);

INSERT INTO queue_task_new
SELECT id, cashbox_id, lane, type, payload_ref, created_at, status, attempt, next_attempt_at, last_error
FROM queue_task;

DROP TABLE queue_task;

ALTER TABLE queue_task_new RENAME TO queue_task;

CREATE INDEX IF NOT EXISTS idx_queue_task_cashbox_lane_status
    ON queue_task (cashbox_id, lane, status, created_at);

CREATE INDEX IF NOT EXISTS idx_queue_task_next_attempt
    ON queue_task (next_attempt_at) WHERE next_attempt_at IS NOT NULL;
