package com.reto.mining.equipmenttype;

/** {@code nextCode}: código que recibiría el próximo equipo de este tipo (referencial). */
public record EquipmentTypeResponse(Long id, String code, String name, int maintenanceIntervalHours,
                                    String codePrefix, String nextCode) {

    public static EquipmentTypeResponse from(EquipmentType type) {
        return new EquipmentTypeResponse(type.getId(), type.getCode(), type.getName(),
                type.getMaintenanceIntervalHours(), type.getCodePrefix(), type.peekNextCode());
    }
}
