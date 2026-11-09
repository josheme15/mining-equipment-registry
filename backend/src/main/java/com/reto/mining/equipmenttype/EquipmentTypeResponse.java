package com.reto.mining.equipmenttype;

public record EquipmentTypeResponse(Long id, String code, String name, int maintenanceIntervalHours) {

    public static EquipmentTypeResponse from(EquipmentType type) {
        return new EquipmentTypeResponse(type.getId(), type.getCode(), type.getName(),
                type.getMaintenanceIntervalHours());
    }
}
