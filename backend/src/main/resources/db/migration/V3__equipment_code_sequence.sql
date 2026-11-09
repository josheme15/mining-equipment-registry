-- Código de equipo autogenerado por tipo: <PREFIJO>-<correlativo de 3 dígitos> (CAM-001, EXC-001, PERF-001).
-- next_sequence es el siguiente correlativo a asignar; nunca retrocede, así los códigos de
-- equipos eliminados no se reutilizan.
ALTER TABLE equipment_types
    ADD COLUMN code_prefix   VARCHAR(10) NULL,
    ADD COLUMN next_sequence INT         NOT NULL DEFAULT 1;

UPDATE equipment_types SET code_prefix = 'CAM'  WHERE code = 'TRUCK';
UPDATE equipment_types SET code_prefix = 'EXC'  WHERE code = 'EXCAVATOR';
UPDATE equipment_types SET code_prefix = 'PERF' WHERE code = 'DRILL';

ALTER TABLE equipment_types
    MODIFY code_prefix VARCHAR(10) NOT NULL,
    ADD CONSTRAINT uk_equipment_types_code_prefix UNIQUE (code_prefix),
    ADD CONSTRAINT ck_equipment_types_next_sequence CHECK (next_sequence >= 1);

-- Si ya existen equipos con códigos de ese formato (creados a mano antes de este cambio),
-- el correlativo continúa después del mayor para no chocar con ellos.
UPDATE equipment_types t
SET t.next_sequence = 1 + COALESCE((
        SELECT MAX(CAST(SUBSTRING(e.code, CHAR_LENGTH(t.code_prefix) + 2) AS UNSIGNED))
        FROM equipments e
        WHERE e.code REGEXP CONCAT('^', t.code_prefix, '-[0-9]+$')
    ), 0);
