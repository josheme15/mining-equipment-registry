package com.reto.mining.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.reto.mining.equipment.dto.CreateEquipmentRequest;
import com.reto.mining.equipment.dto.EquipmentResponse;
import com.reto.mining.equipment.dto.UpdateEquipmentRequest;
import com.reto.mining.equipmenttype.EquipmentType;
import com.reto.mining.equipmenttype.EquipmentTypeRepository;
import com.reto.mining.shared.exception.BusinessRuleException;
import com.reto.mining.shared.exception.NotFoundException;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private EquipmentTypeRepository typeRepository;

    @InjectMocks
    private EquipmentService service;

    private EquipmentType truck;
    private EquipmentType drill;

    @BeforeEach
    void setUp() {
        truck = new EquipmentType("TRUCK", "Camión", 250, "CAM");
        drill = new EquipmentType("DRILL", "Perforadora", 400, "PERF");
    }

    private void saveReturnsArgument() {
        when(equipmentRepository.saveAndFlush(any(Equipment.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createGeneratesSequentialCodePerType() {
        when(typeRepository.findForUpdateById(1L)).thenReturn(Optional.of(truck));
        when(typeRepository.findForUpdateById(3L)).thenReturn(Optional.of(drill));
        when(equipmentRepository.existsByCode(anyString())).thenReturn(false);
        saveReturnsArgument();

        EquipmentResponse first = service.create(new CreateEquipmentRequest(1L, new BigDecimal("300")));
        EquipmentResponse second = service.create(new CreateEquipmentRequest(1L, BigDecimal.TEN));
        EquipmentResponse firstDrill = service.create(new CreateEquipmentRequest(3L, BigDecimal.TEN));

        assertThat(first.code()).isEqualTo("CAM-001");
        assertThat(second.code()).isEqualTo("CAM-002");
        assertThat(firstDrill.code()).isEqualTo("PERF-001");
        assertThat(first.requiresMaintenance()).isTrue();
        assertThat(truck.peekNextCode()).isEqualTo("CAM-003");
    }

    @Test
    void createSkipsCodesThatAlreadyExist() {
        when(typeRepository.findForUpdateById(1L)).thenReturn(Optional.of(truck));
        when(equipmentRepository.existsByCode("CAM-001")).thenReturn(true); // creado a mano antes
        when(equipmentRepository.existsByCode("CAM-002")).thenReturn(false);
        saveReturnsArgument();

        assertThat(service.create(new CreateEquipmentRequest(1L, BigDecimal.TEN)).code()).isEqualTo("CAM-002");
    }

    @Test
    void createRejectsUnknownType() {
        when(typeRepository.findForUpdateById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateEquipmentRequest(99L, BigDecimal.TEN)))
                .isInstanceOf(BusinessRuleException.class);
        verify(equipmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void updateChangesOnlyHourMeter() {
        when(equipmentRepository.findForUpdateById(1L))
                .thenReturn(Optional.of(new Equipment("CAM-001", truck, BigDecimal.TEN)));
        saveReturnsArgument();

        EquipmentResponse updated = service.update(1L, new UpdateEquipmentRequest(null, new BigDecimal("20.5")));

        assertThat(updated.code()).isEqualTo("CAM-001");
        assertThat(updated.type().name()).isEqualTo("Camión");
        assertThat(updated.hourMeter()).isEqualByComparingTo("20.5");
    }

    @Test
    void updateWithSameTypeKeepsCode() {
        Equipment equipment = new Equipment("CAM-001", truck, BigDecimal.TEN);
        when(equipmentRepository.findForUpdateById(1L)).thenReturn(Optional.of(equipment));
        saveReturnsArgument();
        ReflectionTestUtils.setField(truck, "id", 1L);

        assertThat(service.update(1L, new UpdateEquipmentRequest(1L, BigDecimal.TEN)).code()).isEqualTo("CAM-001");
        verify(typeRepository, never()).findForUpdateById(any());
    }

    @Test
    void changingTypeAssignsCodeOfNewTypeAndKeepsHistory() {
        Equipment equipment = new Equipment("CAM-004", truck, new BigDecimal("300"));
        equipment.registerMaintenance("Preventivo"); // ciclo en 0 a las 300 h
        equipment.updateHourMeter(new BigDecimal("700"));
        assertThat(equipment.requiresMaintenance()).isTrue(); // 400 h ≥ 250 (camión)
        ReflectionTestUtils.setField(truck, "id", 1L);
        ReflectionTestUtils.setField(drill, "id", 3L);
        when(equipmentRepository.findForUpdateById(4L)).thenReturn(Optional.of(equipment));
        when(typeRepository.findForUpdateById(3L)).thenReturn(Optional.of(drill));
        when(equipmentRepository.existsByCode(anyString())).thenReturn(false);
        saveReturnsArgument();

        EquipmentResponse updated = service.update(4L, new UpdateEquipmentRequest(3L, new BigDecimal("700")));

        assertThat(updated.code()).isEqualTo("PERF-001");
        assertThat(updated.type().name()).isEqualTo("Perforadora");
        assertThat(updated.lastMaintenanceHourMeter()).isEqualByComparingTo("300"); // historial intacto
        assertThat(updated.requiresMaintenance()).isTrue(); // 400 h ≥ 400 (perforadora)
        assertThat(drill.peekNextCode()).isEqualTo("PERF-002");
    }

    @Test
    void changingToUnknownTypeFails() {
        ReflectionTestUtils.setField(truck, "id", 1L);
        when(equipmentRepository.findForUpdateById(1L))
                .thenReturn(Optional.of(new Equipment("CAM-001", truck, BigDecimal.TEN)));
        when(typeRepository.findForUpdateById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, new UpdateEquipmentRequest(99L, BigDecimal.TEN)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void getMissingEquipmentThrowsNotFound() {
        when(equipmentRepository.findWithTypeById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(42L)).isInstanceOf(NotFoundException.class);
    }
}
