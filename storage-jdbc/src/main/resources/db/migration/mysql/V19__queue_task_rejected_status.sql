-- Терминальное состояние задачи очереди: отвергнутую по существу команду
-- повторять бессмысленно.
ALTER TABLE queue_task DROP CHECK queue_task_chk_1;

ALTER TABLE queue_task
    ADD CONSTRAINT queue_task_status_check
    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'SENT', 'FAILED', 'REJECTED'));
