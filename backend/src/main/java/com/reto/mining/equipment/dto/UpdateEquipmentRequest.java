package com.reto.mining.equipment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Datos editables de un equipo. {@code typeId} es opcional: si cambia, el backend le asigna un
 * código nuevo del correlativo de ese tipo (CAM-004 → EXC-006). El código nunca se envía.
 */
public record UpdateEquipmentRequest(

        Long typeId,

        @NotNull(message = "El horómetro es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "El horómetro debe ser mayor a 0")
        @DecimalMax(value = "999999999.9", message = "El horómetro es demasiado alto")
        @Digits(integer = 9, fraction = 1, message = "El horómetro admite como máximo 1 decimal")
        BigDecimal hourMeter) {
}
