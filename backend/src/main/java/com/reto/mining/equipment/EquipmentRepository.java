package com.reto.mining.equipment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    /** Carga el tipo en la misma consulta (evita N+1 al calcular el estado de mantenimiento). */
    @EntityGraph(attributePaths = "type")
    List<Equipment> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = "type")
    Optional<Equipment> findWithTypeById(Long id);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
