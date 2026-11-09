package com.reto.mining.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.reto.mining.equipment.dto.EquipmentRequest;
import com.reto.mining.equipment.dto.EquipmentResponse;
import com.reto.mining.equipmenttype.EquipmentType;
import com.reto.mining.equipmenttype.EquipmentTypeRepository;
import com.reto.mining.shared.exception.BusinessRuleException;
import com.reto.mining.shared.exception.ConflictException;
import com.reto.mining.shared.exception.NotFoundException;

@ExtendWith(MockitoExtension.class)
class EquipmentServiceTest {

    @Mock
    private EquipmentRepository equipmentRepository;

    @Mock
    private EquipmentTypeRepository typeRepository;

    @InjectMocks
    private EquipmentService service;

    private final EquipmentType truck = new EquipmentType("TRUCK", "Camión", 250);

    @Test
    void createNormalizesCodeAndComputesStatus() {
        when(equipmentRepository.existsByCode("CAM-001")).thenReturn(false);
        when(typeRepository.findById(1L)).thenReturn(Optional.of(truck));
        when(equipmentRepository.saveAndFlush(any(Equipment.class))).thenAnswer(inv -> inv.getArgument(0));

        EquipmentResponse response = service.create(new EquipmentRequest("  cam-001 ", 1L, new BigDecimal("300")));

        assertThat(response.code()).isEqualTo("CAM-001");
        assertThat(response.requiresMaintenance()).isTrue();
        assertThat(response.type().name()).isEqualTo("Camión");
    }

    @Test
    void createRejectsDuplicateCode() {
        when(equipmentRepository.existsByCode("CAM-001")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new EquipmentRequest("CAM-001", 1L, BigDecimal.TEN)))
                .isInstanceOf(ConflictException.class);
        verify(equipmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createRejectsUnknownType() {
        when(equipmentRepository.existsByCode("CAM-001")).thenReturn(false);
        when(typeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new EquipmentRequest("CAM-001", 99L, BigDecimal.TEN)))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void updateRejectsCodeOfAnotherEquipment() {
        when(equipmentRepository.findWithTypeById(1L))
                .thenReturn(Optional.of(new Equipment("CAM-001", truck, BigDecimal.TEN)));
        when(equipmentRepository.existsByCodeAndIdNot("CAM-002", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, new EquipmentRequest("CAM-002", 1L, BigDecimal.TEN)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void getMissingEquipmentThrowsNotFound() {
        when(equipmentRepository.findWithTypeById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(42L)).isInstanceOf(NotFoundException.class);
    }
}
