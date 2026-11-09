package com.reto.mining.maintenance;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceRepository extends JpaRepository<Maintenance, Long> {

    List<Maintenance> findByEquipmentIdOrderByPerformedAtDescIdDesc(Long equipmentId);
}
