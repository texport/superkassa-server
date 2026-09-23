-- Счёт неверных пинов по кассе. Держался в памяти процесса, и перезапуск
-- узла снимал блокировку, заработанную перебором пина.
CREATE TABLE IF NOT EXISTS pin_attempts (
    cashbox_id TEXT PRIMARY KEY,
    failures INTEGER NOT NULL,
    locked_until INTEGER NOT NULL
);
