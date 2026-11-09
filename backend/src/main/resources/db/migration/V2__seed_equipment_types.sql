-- Intervalos de mantenimiento por tipo (horas). Se ajustan con una nueva migración si cambian.
INSERT INTO equipment_types (code, name, maintenance_interval_hours) VALUES
    ('TRUCK',     'Camión',      250),
    ('EXCAVATOR', 'Excavadora',  500),
    ('DRILL',     'Perforadora', 400);
