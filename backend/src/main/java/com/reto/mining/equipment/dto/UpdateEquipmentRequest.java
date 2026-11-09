package com.reto.mining.equipment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Datos editables de un equipo: solo el horómetro. El código y el tipo son su identidad
 * (CAM-001 es un camión) y no cambian después de crearlo.
 */
public record UpdateEquipmentRequest(

        @NotNull(message = "El horómetro es obligatorio")
        @DecimalMin(value = "0.0", inclusive = false, message = "El horómetro debe ser mayor a 0")
        @DecimalMax(value = "999999999.9", message = "El horómetro es demasiado alto")
        @Digits(integer = 9, fraction = 1, message = "El horómetro admite como máximo 1 decimal")
        BigDecimal hourMeter) {
}
