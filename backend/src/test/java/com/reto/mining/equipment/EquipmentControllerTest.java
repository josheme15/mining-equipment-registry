package com.reto.mining.equipment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.reto.mining.shared.exception.BusinessRuleException;
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
                        .content("{\"typeId\":null,\"hourMeter\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.typeId").exists())
                .andExpect(jsonPath("$.fieldErrors.hourMeter").exists());
        verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "hourMeter={0} → 400")
    @ValueSource(strings = {"0", "0.0", "-1", "1.25", "null", "\"\""})
    void invalidHourMeterReturns400(String hourMeter) throws Exception {
        mvc.perform(post("/api/equipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"CAM-001\",\"typeId\":1,\"hourMeter\":" + hourMeter + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.hourMeter").exists());
        verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "PUT hourMeter={0} → 400")
    @ValueSource(strings = {"0", "-1", "1.25", "null"})
    void invalidHourMeterOnUpdateReturns400(String hourMeter) throws Exception {
        mvc.perform(put("/api/equipments/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hourMeter\":" + hourMeter + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.hourMeter").exists());
        verifyNoInteractions(service);
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/equipments").contentType(MediaType.APPLICATION_JSON).content("{nope"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void dataIntegrityViolationReturns409() throws Exception {
        when(service.create(any())).thenThrow(new DataIntegrityViolationException("Duplicate entry 'CAM-001'"));

        mvc.perform(post("/api/equipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeId\":1,\"hourMeter\":10}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void unsupportedMethodReturns405NotA500() throws Exception {
        mvc.perform(patch("/api/equipments/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.message").value("El método PATCH no está permitido en esta ruta"));
        mvc.perform(delete("/api/equipments"))
                .andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }

    @Test
    void nonJsonBodyReturns415NotA500() throws Exception {
        mvc.perform(post("/api/equipments").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));
        verifyNoInteractions(service);
    }

    @Test
    void decreasingHourMeterReturns422() throws Exception {
        when(service.update(eq(1L), any())).thenThrow(new BusinessRuleException("El horómetro no puede disminuir"));

        mvc.perform(put("/api/equipments/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hourMeter\":5}"))
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
