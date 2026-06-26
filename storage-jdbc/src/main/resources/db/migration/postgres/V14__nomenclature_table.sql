CREATE TABLE nomenclature_item (
    cashbox_id VARCHAR(100) NOT NULL,
    id BIGINT NOT NULL,
    code VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    name_kk VARCHAR(255),
    price BIGINT NOT NULL,
    measure_unit_code VARCHAR(50),
    vat_group VARCHAR(50),
    version INT NOT NULL,
    PRIMARY KEY (cashbox_id, id)
);

CREATE INDEX idx_nomenclature_item_code ON nomenclature_item (cashbox_id, code);
