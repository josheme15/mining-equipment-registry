package com.reto.mining.maintenance;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reto.mining.equipment.Equipment;
import com.reto.mining.equipment.EquipmentService;
import com.reto.mining.maintenance.dto.MaintenanceRequest;
import com.reto.mining.maintenance.dto.MaintenanceResponse;

@Service
@Transactional(readOnly = true)
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final EquipmentService equipmentService;

    public MaintenanceService(MaintenanceRepository maintenanceRepository, EquipmentService equipmentService) {
        this.maintenanceRepository = maintenanceRepository;
        this.equipmentService = equipmentService;
    }

    /**
     * Registra un mantenimiento: guarda el historial y reinicia el ciclo del equipo.
     * Ambas cosas ocurren en la misma transacción: o se guardan las dos, o ninguna.
     */
    @Transactional
    public MaintenanceResponse register(Long equipmentId, MaintenanceRequest request) {
        Equipment equipment = equipmentService.findOrThrow(equipmentId);
        String notes = request == null ? null : request.normalizedNotes();
        Maintenance saved = maintenanceRepository.save(equipment.registerMaintenance(notes));
        return MaintenanceResponse.from(saved, equipmentId);
    }

    public List<MaintenanceResponse> history(Long equipmentId) {
        equipmentService.findOrThrow(equipmentId); // 404 si el equipo no existe
        return maintenanceRepository.findByEquipmentIdOrderByPerformedAtDescIdDesc(equipmentId).stream()
                .map(m -> MaintenanceResponse.from(m, equipmentId))
                .toList();
    }
}
