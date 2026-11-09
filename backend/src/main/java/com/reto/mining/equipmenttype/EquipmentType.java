package com.reto.mining.equipmenttype;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tipo de equipo (camión, excavadora, perforadora) y su intervalo de mantenimiento. */
@Entity
@Table(name = "equipment_types")
public class EquipmentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "maintenance_interval_hours", nullable = false)
    private int maintenanceIntervalHours;

    protected EquipmentType() {
        // JPA
    }

    public EquipmentType(String code, String name, int maintenanceIntervalHours) {
        this.code = code;
        this.name = name;
        this.maintenanceIntervalHours = maintenanceIntervalHours;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getMaintenanceIntervalHours() { return maintenanceIntervalHours; }
}
