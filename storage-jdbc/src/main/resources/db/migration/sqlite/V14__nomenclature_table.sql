CREATE TABLE IF NOT EXISTS nomenclature_item (
    cashbox_id TEXT NOT NULL,
    id INTEGER NOT NULL,
    code TEXT NOT NULL,
    name TEXT NOT NULL,
    name_kk TEXT,
    price INTEGER NOT NULL,
    measure_unit_code TEXT,
    vat_group TEXT,
    version INTEGER NOT NULL,
    PRIMARY KEY (cashbox_id, id)
);

CREATE INDEX IF NOT EXISTS idx_nomenclature_item_code
    ON nomenclature_item (cashbox_id, code);
