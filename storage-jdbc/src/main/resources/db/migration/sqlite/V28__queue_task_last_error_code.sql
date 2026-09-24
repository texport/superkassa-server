-- Код отказа БФД при последней попытке досылки: по нему очередь узла
-- показывает причину отказа, не разбирая текст ошибки.
ALTER TABLE queue_task ADD COLUMN last_error_code INTEGER;
