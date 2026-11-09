package com.reto.mining.equipmenttype;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentTypeRepository extends JpaRepository<EquipmentType, Long> {

    List<EquipmentType> findAllByOrderByNameAsc();
}
