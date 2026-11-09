package com.reto.mining.maintenance.dto;

import jakarta.validation.constraints.Size;

/** Datos opcionales al registrar un mantenimiento. El horómetro se toma del equipo. */
public record MaintenanceRequest(
        @Size(max = 500, message = "Las notas admiten máximo 500 caracteres")
        String notes) {

    public String normalizedNotes() {
        return notes == null || notes.isBlank() ? null : notes.trim();
    }
}
