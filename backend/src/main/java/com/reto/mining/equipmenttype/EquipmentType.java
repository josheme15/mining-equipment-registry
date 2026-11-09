package com.reto.mining.equipmenttype;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Tipo de equipo (camión, excavadora, perforadora): su intervalo de mantenimiento y el
 * correlativo con el que se generan los códigos de sus equipos (CAM-001, CAM-002…).
 */
@Entity
@Table(name = "equipment_types")
public class EquipmentType {

    /** Dígitos mínimos del correlativo: CAM-001. Al pasar de 999 sigue con CAM-1000. */
    static final int SEQUENCE_DIGITS = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "maintenance_interval_hours", nullable = false)
    private int maintenanceIntervalHours;

    @Column(name = "code_prefix", nullable = false, unique = true, length = 10)
    private String codePrefix;

    @Column(name = "next_sequence", nullable = false)
    private int nextSequence;

    protected EquipmentType() {
        // JPA
    }

    public EquipmentType(String code, String name, int maintenanceIntervalHours, String codePrefix) {
        this.code = code;
        this.name = name;
        this.maintenanceIntervalHours = maintenanceIntervalHours;
        this.codePrefix = codePrefix;
        this.nextSequence = 1;
    }

    /** Código que recibirá el próximo equipo de este tipo (solo para mostrarlo; no lo reserva). */
    public String peekNextCode() {
        return formatCode(nextSequence);
    }

    /**
     * Asigna el siguiente código y avanza el correlativo. Debe llamarse con el tipo bloqueado
     * (ver {@link EquipmentTypeRepository#findForUpdateById}) para que dos altas simultáneas
     * no reciban el mismo código.
     */
    public String takeNextCode() {
        return formatCode(nextSequence++);
    }

    private String formatCode(int sequence) {
        return codePrefix + "-" + String.format("%0" + SEQUENCE_DIGITS + "d", sequence);
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public int getMaintenanceIntervalHours() { return maintenanceIntervalHours; }
    public String getCodePrefix() { return codePrefix; }
}
