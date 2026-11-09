package com.reto.mining.equipment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.reto.mining.shared.exception.BusinessRuleException;
import com.reto.mining.shared.exception.ConflictException;
import com.reto.mining.shared.exception.NotFoundException;

/** Prueba la capa HTTP: validaciones y códigos de error (sin base de datos). */
@WebMvcTest(EquipmentController.class)
class EquipmentControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private EquipmentService service;

    @Test
    void invalidBodyReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/equipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"typeId\":null,\"hourMeter\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").exists())
                .andExpect(jsonPath("$.fieldErrors.typeId").exists())
                .andExpect(jsonPath("$.fieldErrors.hourMeter").exists());
        verifyNoInteractions(service);
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/equipments").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateCodeReturns409() throws Exception {
        when(service.create(any())).thenThrow(new ConflictException("Ya existe un equipo con el código CAM-001"));

        mvc.perform(post("/api/equipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CAM-001\",\"typeId\":1,\"hourMeter\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Ya existe un equipo con el código CAM-001"));
    }

    @Test
    void decreasingHourMeterReturns422() throws Exception {
        when(service.update(eq(1L), any())).thenThrow(new BusinessRuleException("El horómetro no puede disminuir"));

        mvc.perform(put("/api/equipments/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CAM-001\",\"typeId\":1,\"hourMeter\":5}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void missingEquipmentReturns404() throws Exception {
        when(service.get(7L)).thenThrow(new NotFoundException("No existe el equipo con id 7"));

        mvc.perform(get("/api/equipments/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
