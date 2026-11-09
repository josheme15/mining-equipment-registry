package com.reto.mining.equipment;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.reto.mining.equipmenttype.EquipmentType;
import com.reto.mining.maintenance.Maintenance;
import com.reto.mining.shared.exception.BusinessRuleException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Equipo minero.
 *
 * <p>Regla de mantenimiento: el ciclo actual empieza en {@code lastMaintenanceHourMeter}.
 * Cuando las horas recorridas desde ahí alcanzan el intervalo del tipo, el equipo
 * "requiere mantenimiento". Registrar un mantenimiento reinicia el ciclo.
 *
 * <p>Un equipo nuevo empieza su ciclo en 0 h: si se registra con un horómetro que ya
 * supera el intervalo, se marca como pendiente (criterio conservador: no hay historial).
 */
@Entity
@Table(name = "equipments")
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "equipment_type_id", nullable = false)
    private EquipmentType type;

    @Column(name = "hour_meter", nullable = false, precision = 10, scale = 1)
    private BigDecimal hourMeter;

    @Column(name = "last_maintenance_hour_meter", nullable = false, precision = 10, scale = 1)
    private BigDecimal lastMaintenanceHourMeter;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Equipment() {
        // JPA
    }

    public Equipment(String code, EquipmentType type, BigDecimal hourMeter) {
        requireNonNegative(hourMeter);
        this.code = code;
        this.type = type;
        this.hourMeter = hourMeter;
        this.lastMaintenanceHourMeter = BigDecimal.ZERO;
    }

    // ------------------------------------------------------------------ comportamiento

    /** Actualiza los datos editables. El horómetro nunca puede retroceder. */
    public void update(String code, EquipmentType type, BigDecimal newHourMeter) {
        requireNonNegative(newHourMeter);
        if (newHourMeter.compareTo(hourMeter) < 0) {
            throw new BusinessRuleException(
                    "El horómetro no puede disminuir (actual: " + hourMeter.toPlainString() + " h)");
        }
        this.code = code;
        this.type = type;
        this.hourMeter = newHourMeter;
    }

    /** Registra un mantenimiento con el horómetro actual y reinicia el ciclo. */
    public Maintenance registerMaintenance(String notes) {
        Maintenance maintenance = new Maintenance(this, hourMeter, notes);
        this.lastMaintenanceHourMeter = hourMeter;
        return maintenance;
    }

    /** Horas recorridas desde el último mantenimiento (o desde 0 si nunca tuvo). */
    public BigDecimal hoursSinceMaintenance() {
        return hourMeter.subtract(lastMaintenanceHourMeter);
    }

    /** Horas que faltan para el próximo mantenimiento (0 si ya le toca). */
    public BigDecimal hoursUntilMaintenance() {
        BigDecimal remaining = BigDecimal.valueOf(type.getMaintenanceIntervalHours()).subtract(hoursSinceMaintenance());
        return remaining.signum() > 0 ? remaining : BigDecimal.ZERO;
    }

    /** true si alcanzó (o superó) el intervalo de mantenimiento de su tipo. */
    public boolean requiresMaintenance() {
        return hoursSinceMaintenance().compareTo(BigDecimal.valueOf(type.getMaintenanceIntervalHours())) >= 0;
    }

    private static void requireNonNegative(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new BusinessRuleException("El horómetro debe ser mayor o igual a 0");
        }
    }

    // ------------------------------------------------------------------ getters

    public Long getId() { return id; }
    public String getCode() { return code; }
    public EquipmentType getType() { return type; }
    public BigDecimal getHourMeter() { return hourMeter; }
    public BigDecimal getLastMaintenanceHourMeter() { return lastMaintenanceHourMeter; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
