-- Alinea la base de datos con la regla de la aplicación: el horómetro debe ser mayor a 0
-- (antes se permitía 0). Compatible con la versión anterior del código, que ya exigía > 0.
ALTER TABLE equipments DROP CHECK ck_equipments_hour_meter;
ALTER TABLE equipments ADD CONSTRAINT ck_equipments_hour_meter CHECK (hour_meter > 0);
