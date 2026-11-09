package com.reto.mining.equipmenttype;

import java.util.List;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/equipment-types")
public class EquipmentTypeController {

    private final EquipmentTypeRepository repository;

    public EquipmentTypeController(EquipmentTypeRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<EquipmentTypeResponse> list() {
        return repository.findAllByOrderByNameAsc().stream().map(EquipmentTypeResponse::from).toList();
    }
}
