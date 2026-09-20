-- Адрес ОФД, заданный вручную при заведении кассы.
-- Провайдер CUSTOM держит хост и порт не в перечислении узла, а у самой
-- кассы: без этих колонок касса на своём адресе не пережила бы перезапуск.
-- У касс справочных ОФД обе колонки пустые.
ALTER TABLE cashbox ADD COLUMN IF NOT EXISTS ofd_host TEXT;
ALTER TABLE cashbox ADD COLUMN IF NOT EXISTS ofd_port INTEGER;
