package com.reto.mining.equipment;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reto.mining.equipment.dto.CreateEquipmentRequest;
import com.reto.mining.equipment.dto.UpdateEquipmentRequest;
import com.reto.mining.equipment.dto.EquipmentResponse;
import com.reto.mining.equipmenttype.EquipmentType;
import com.reto.mining.equipmenttype.EquipmentTypeRepository;
import com.reto.mining.shared.exception.BusinessRuleException;
import com.reto.mining.shared.exception.NotFoundException;

@Service
@Transactional(readOnly = true)
public class EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentTypeRepository typeRepository;

    public EquipmentService(EquipmentRepository equipmentRepository, EquipmentTypeRepository typeRepository) {
        this.equipmentRepository = equipmentRepository;
        this.typeRepository = typeRepository;
    }

    public List<EquipmentResponse> list() {
        return equipmentRepository.findAllByOrderByCodeAsc().stream().map(EquipmentResponse::from).toList();
    }

    public EquipmentResponse get(Long id) {
        return EquipmentResponse.from(findOrThrow(id));
    }

    /**
     * Crea el equipo con el siguiente código de su tipo (CAM-001, CAM-002…). El tipo se lee con
     * bloqueo, así dos altas simultáneas del mismo tipo reciben códigos distintos.
     */
    @Transactional
    public EquipmentResponse create(CreateEquipmentRequest request) {
        EquipmentType type = typeRepository.findForUpdateById(request.typeId())
                .orElseThrow(() -> unknownType(request.typeId()));
        String code = type.takeNextCode();
        // Salta códigos que ya existan (p. ej. creados a mano antes de la autogeneración).
        while (equipmentRepository.existsByCode(code)) {
            code = type.takeNextCode();
        }
        Equipment equipment = new Equipment(code, type, request.hourMeter());
        return EquipmentResponse.from(equipmentRepository.saveAndFlush(equipment));
    }

    @Transactional
    public EquipmentResponse update(Long id, UpdateEquipmentRequest request) {
        Equipment equipment = findOrThrow(id);
        equipment.updateHourMeter(request.hourMeter());
        return EquipmentResponse.from(equipmentRepository.saveAndFlush(equipment));
    }

    /** Elimina el equipo; su historial de mantenimientos se borra en cascada (ON DELETE CASCADE). */
    @Transactional
    public void delete(Long id) {
        equipmentRepository.delete(findOrThrow(id));
    }

    /** Usado por otros módulos (mantenimientos). */
    public Equipment findOrThrow(Long id) {
        return equipmentRepository.findWithTypeById(id)
                .orElseThrow(() -> new NotFoundException("No existe el equipo con id " + id));
    }

    private static BusinessRuleException unknownType(Long typeId) {
        return new BusinessRuleException("El tipo de equipo " + typeId + " no existe");
    }
}
