-- Терминальное состояние задачи очереди: отвергнутую по существу команду
-- повторять бессмысленно. Имя ограничения задаётся Postgres по умолчанию
-- как «таблица_столбец_check».
ALTER TABLE queue_task DROP CONSTRAINT IF EXISTS queue_task_status_check;

ALTER TABLE queue_task
    ADD CONSTRAINT queue_task_status_check
    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'SENT', 'FAILED', 'REJECTED'));
