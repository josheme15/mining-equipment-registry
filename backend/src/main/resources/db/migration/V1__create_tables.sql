-- Tipos de equipo con su intervalo de mantenimiento (en horas de horómetro)
CREATE TABLE equipment_types (
    id                         BIGINT       NOT NULL AUTO_INCREMENT,
    code                       VARCHAR(30)  NOT NULL,
    name                       VARCHAR(50)  NOT NULL,
    maintenance_interval_hours INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_equipment_types_code UNIQUE (code),
    CONSTRAINT ck_equipment_types_interval CHECK (maintenance_interval_hours > 0)
);

-- Equipos. last_maintenance_hour_meter marca el inicio del ciclo actual.
CREATE TABLE equipments (
    id                          BIGINT        NOT NULL AUTO_INCREMENT,
    code                        VARCHAR(50)   NOT NULL,
    equipment_type_id           BIGINT        NOT NULL,
    hour_meter                  DECIMAL(10,1) NOT NULL DEFAULT 0,
    last_maintenance_hour_meter DECIMAL(10,1) NOT NULL DEFAULT 0,
    created_at                  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_equipments_code UNIQUE (code),
    CONSTRAINT fk_equipments_type FOREIGN KEY (equipment_type_id) REFERENCES equipment_types (id),
    CONSTRAINT ck_equipments_hour_meter CHECK (hour_meter >= 0),
    CONSTRAINT ck_equipments_cycle CHECK (last_maintenance_hour_meter >= 0 AND last_maintenance_hour_meter <= hour_meter)
);

-- Historial de mantenimientos. Se elimina junto con su equipo.
CREATE TABLE maintenances (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    equipment_id  BIGINT        NOT NULL,
    performed_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    hour_meter_at DECIMAL(10,1) NOT NULL,
    notes         VARCHAR(500),
    PRIMARY KEY (id),
    CONSTRAINT fk_maintenances_equipment FOREIGN KEY (equipment_id) REFERENCES equipments (id) ON DELETE CASCADE,
    INDEX idx_maintenances_equipment (equipment_id, performed_at)
);
