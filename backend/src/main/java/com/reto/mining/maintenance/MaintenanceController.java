package com.reto.mining.maintenance;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.reto.mining.maintenance.dto.MaintenanceRequest;
import com.reto.mining.maintenance.dto.MaintenanceResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/equipments/{equipmentId}/maintenances")
public class MaintenanceController {

    private final MaintenanceService service;

    public MaintenanceController(MaintenanceService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MaintenanceResponse register(@PathVariable Long equipmentId,
                                        @Valid @RequestBody(required = false) MaintenanceRequest request) {
        return service.register(equipmentId, request);
    }

    @GetMapping
    public List<MaintenanceResponse> history(@PathVariable Long equipmentId) {
        return service.history(equipmentId);
    }
}
