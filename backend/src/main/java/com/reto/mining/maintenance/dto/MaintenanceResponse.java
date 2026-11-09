package com.reto.mining.maintenance.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.reto.mining.maintenance.Maintenance;

public record MaintenanceResponse(Long id, Long equipmentId, Instant performedAt, BigDecimal hourMeterAt, String notes) {

    public static MaintenanceResponse from(Maintenance m, Long equipmentId) {
        return new MaintenanceResponse(m.getId(), equipmentId, m.getPerformedAt(), m.getHourMeterAt(), m.getNotes());
    }
}
