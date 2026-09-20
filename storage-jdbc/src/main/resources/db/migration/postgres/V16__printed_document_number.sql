-- Сквозной номер печатного документа, который ведёт сама касса.
-- Не зависит от ответа ОФД и не прерывается при работе в разрыве связи.
ALTER TABLE fiscal_document ADD COLUMN printed_document_number BIGINT;
