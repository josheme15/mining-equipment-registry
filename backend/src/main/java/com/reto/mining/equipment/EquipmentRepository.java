package com.reto.mining.equipment;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    /** Carga el tipo en la misma consulta (evita N+1 al calcular el estado de mantenimiento). */
    @EntityGraph(attributePaths = "type")
    List<Equipment> findAllByOrderByCodeAsc();

    @EntityGraph(attributePaths = "type")
    Optional<Equipment> findWithTypeById(Long id);

    boolean existsByCode(String code);

    /** SELECT … FOR UPDATE: serializa operaciones simultáneas sobre el mismo equipo. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipment e join fetch e.type where e.id = :id")
    Optional<Equipment> findForUpdateById(Long id);
}
