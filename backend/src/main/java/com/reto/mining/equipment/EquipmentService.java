package com.reto.mining.equipment;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reto.mining.equipment.dto.EquipmentRequest;
import com.reto.mining.equipment.dto.EquipmentResponse;
import com.reto.mining.equipmenttype.EquipmentType;
import com.reto.mining.equipmenttype.EquipmentTypeRepository;
import com.reto.mining.shared.exception.BusinessRuleException;
import com.reto.mining.shared.exception.ConflictException;
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

    @Transactional
    public EquipmentResponse create(EquipmentRequest request) {
        String code = request.normalizedCode();
        if (equipmentRepository.existsByCode(code)) {
            throw new ConflictException("Ya existe un equipo con el código " + code);
        }
        Equipment equipment = new Equipment(code, findType(request.typeId()), request.hourMeter());
        return EquipmentResponse.from(equipmentRepository.saveAndFlush(equipment));
    }

    @Transactional
    public EquipmentResponse update(Long id, EquipmentRequest request) {
        Equipment equipment = findOrThrow(id);
        String code = request.normalizedCode();
        if (equipmentRepository.existsByCodeAndIdNot(code, id)) {
            throw new ConflictException("Ya existe otro equipo con el código " + code);
        }
        equipment.update(code, findType(request.typeId()), request.hourMeter());
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

    private EquipmentType findType(Long typeId) {
        return typeRepository.findById(typeId)
                .orElseThrow(() -> new BusinessRuleException("El tipo de equipo " + typeId + " no existe"));
    }
}
