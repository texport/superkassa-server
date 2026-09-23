-- Счёт неверных пинов по кассе. Держался в памяти процесса, и перезапуск
-- узла снимал блокировку, заработанную перебором пина.
CREATE TABLE IF NOT EXISTS pin_attempts (
    cashbox_id VARCHAR(64) PRIMARY KEY,
    failures INT NOT NULL,
    locked_until BIGINT NOT NULL
);
