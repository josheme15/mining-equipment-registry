package com.reto.mining.equipment.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.reto.mining.equipment.Equipment;
import com.reto.mining.equipmenttype.EquipmentTypeResponse;

/** Equipo con su estado de mantenimiento ya calculado por el backend. */
public record EquipmentResponse(
        Long id,
        String code,
        EquipmentTypeResponse type,
        BigDecimal hourMeter,
        BigDecimal lastMaintenanceHourMeter,
        BigDecimal hoursSinceMaintenance,
        BigDecimal hoursUntilMaintenance,
        BigDecimal nextMaintenanceHourMeter,
        BigDecimal overdueHours,
        boolean requiresMaintenance,
        Instant createdAt,
        Instant updatedAt) {

    public static EquipmentResponse from(Equipment e) {
        return new EquipmentResponse(
                e.getId(),
                e.getCode(),
                EquipmentTypeResponse.from(e.getType()),
                e.getHourMeter(),
                e.getLastMaintenanceHourMeter(),
                e.hoursSinceMaintenance(),
                e.hoursUntilMaintenance(),
                e.nextMaintenanceHourMeter(),
                e.overdueHours(),
                e.requiresMaintenance(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
