package com.reto.mining.equipment.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Datos para crear o editar un equipo. */
public record EquipmentRequest(

        @NotBlank(message = "El código es obligatorio")
        @Size(max = 50, message = "El código admite máximo 50 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9 _-]*$",
                 message = "El código solo admite letras, números, espacios, guiones y guiones bajos")
        String code,

        @NotNull(message = "El tipo de equipo es obligatorio")
        Long typeId,

        @NotNull(message = "El horómetro es obligatorio")
        @DecimalMin(value = "0.0", message = "El horómetro debe ser mayor o igual a 0")
        @DecimalMax(value = "999999999.9", message = "El horómetro es demasiado alto")
        @Digits(integer = 9, fraction = 1, message = "El horómetro admite como máximo 1 decimal")
        BigDecimal hourMeter) {

    /** Código normalizado: sin espacios extremos y en mayúsculas (CAM-001). */
    public String normalizedCode() {
        return code.trim().toUpperCase();
    }
}
