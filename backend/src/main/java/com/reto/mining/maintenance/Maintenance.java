package com.reto.mining.maintenance;

import java.math.BigDecimal;
import java.time.Instant;

import com.reto.mining.equipment.Equipment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Registro histórico de un mantenimiento realizado a un equipo. */
@Entity
@Table(name = "maintenances")
public class Maintenance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Column(name = "hour_meter_at", nullable = false, precision = 10, scale = 1)
    private BigDecimal hourMeterAt;

    @Column(length = 500)
    private String notes;

    protected Maintenance() {
        // JPA
    }

    /** Se crea solo desde {@link Equipment#registerMaintenance(String)}. */
    public Maintenance(Equipment equipment, BigDecimal hourMeterAt, String notes) {
        this.equipment = equipment;
        this.hourMeterAt = hourMeterAt;
        this.notes = notes;
        this.performedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Equipment getEquipment() { return equipment; }
    public Instant getPerformedAt() { return performedAt; }
    public BigDecimal getHourMeterAt() { return hourMeterAt; }
    public String getNotes() { return notes; }
}
