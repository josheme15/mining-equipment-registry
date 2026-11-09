package com.reto.mining.equipment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.reto.mining.equipmenttype.EquipmentType;
import com.reto.mining.maintenance.Maintenance;
import com.reto.mining.shared.exception.BusinessRuleException;

/** Pruebas de la regla de mantenimiento (sin Spring ni base de datos). */
class EquipmentTest {

    private static final EquipmentType TRUCK = new EquipmentType("TRUCK", "Camión", 250, "CAM");
    private static final EquipmentType EXCAVATOR = new EquipmentType("EXCAVATOR", "Excavadora", 500, "EXC");

    private static BigDecimal h(String value) {
        return new BigDecimal(value);
    }

    @Test
    @DisplayName("Por debajo del intervalo no requiere mantenimiento")
    void belowInterval() {
        Equipment truck = new Equipment("CAM-001", TRUCK, h("249.9"));

        assertThat(truck.requiresMaintenance()).isFalse();
        assertThat(truck.hoursUntilMaintenance()).isEqualByComparingTo("0.1");
    }

    @Test
    @DisplayName("Al alcanzar exactamente el intervalo requiere mantenimiento")
    void exactlyAtInterval() {
        Equipment truck = new Equipment("CAM-001", TRUCK, h("250"));

        assertThat(truck.requiresMaintenance()).isTrue();
        assertThat(truck.hoursUntilMaintenance()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Superado el intervalo sigue requiriendo y las horas restantes no son negativas")
    void beyondInterval() {
        Equipment truck = new Equipment("CAM-001", TRUCK, h("400"));

        assertThat(truck.requiresMaintenance()).isTrue();
        assertThat(truck.hoursUntilMaintenance()).isEqualByComparingTo("0");
        assertThat(truck.hoursSinceMaintenance()).isEqualByComparingTo("400");
    }

    @Test
    @DisplayName("Registrar mantenimiento guarda el horómetro y reinicia el ciclo")
    void maintenanceResetsCycle() {
        Equipment truck = new Equipment("CAM-001", TRUCK, h("260"));

        Maintenance maintenance = truck.registerMaintenance("Cambio de aceite");

        assertThat(maintenance.getHourMeterAt()).isEqualByComparingTo("260");
        assertThat(maintenance.getNotes()).isEqualTo("Cambio de aceite");
        assertThat(maintenance.getPerformedAt()).isNotNull();
        assertThat(truck.getLastMaintenanceHourMeter()).isEqualByComparingTo("260");
        assertThat(truck.requiresMaintenance()).isFalse();
        assertThat(truck.hoursUntilMaintenance()).isEqualByComparingTo("250");
    }

    @Test
    @DisplayName("Tras el mantenimiento, vuelve a requerirlo al completar otro intervalo")
    void nextCycle() {
        Equipment truck = new Equipment("CAM-001", TRUCK, h("260"));
        truck.registerMaintenance(null);

        truck.updateHourMeter(h("509.9"));
        assertThat(truck.requiresMaintenance()).isFalse();

        truck.updateHourMeter(h("510"));
        assertThat(truck.requiresMaintenance()).isTrue();
    }

    @Test
    @DisplayName("Cada tipo usa su propio intervalo")
    void intervalDependsOnType() {
        Equipment truck = new Equipment("CAM-001", TRUCK, h("300"));
        Equipment excavator = new Equipment("EXC-001", EXCAVATOR, h("300"));

        assertThat(truck.requiresMaintenance()).isTrue();
        assertThat(excavator.requiresMaintenance()).isFalse();
    }

    @Test
    @DisplayName("El horómetro no puede disminuir")
    void hourMeterCannotDecrease() {
        Equipment truck = new Equipment("CAM-001", TRUCK, h("100"));

        assertThatThrownBy(() -> truck.updateHourMeter(h("99.9")))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no puede disminuir");
        assertThat(truck.getHourMeter()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("El horómetro debe ser mayor a 0 (ni negativo ni cero)")
    void hourMeterMustBePositive() {
        assertThatThrownBy(() -> new Equipment("CAM-001", TRUCK, h("-1")))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new Equipment("CAM-001", TRUCK, h("0")))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(new Equipment("CAM-001", TRUCK, h("0.1")).getHourMeter()).isEqualByComparingTo("0.1");
    }
}
