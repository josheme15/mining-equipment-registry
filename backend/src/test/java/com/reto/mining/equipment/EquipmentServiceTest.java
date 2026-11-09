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
        when(equipmentRepository.findWithTypeById(1L))
                .thenReturn(Optional.of(new Equipment("CAM-001", truck, BigDecimal.TEN)));
        saveReturnsArgument();

        EquipmentResponse updated = service.update(1L, new UpdateEquipmentRequest(new BigDecimal("20.5")));

        assertThat(updated.code()).isEqualTo("CAM-001");
        assertThat(updated.type().name()).isEqualTo("Camión");
        assertThat(updated.hourMeter()).isEqualByComparingTo("20.5");
    }

    @Test
    void getMissingEquipmentThrowsNotFound() {
        when(equipmentRepository.findWithTypeById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(42L)).isInstanceOf(NotFoundException.class);
    }
}
