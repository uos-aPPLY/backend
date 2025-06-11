package com.apply.diarypic.global.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class MaintenanceController {

    @GetMapping("/maintenance-status")
    public Map<String, Object> getMaintenanceStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("isUnderMaintenance", false);
        response.put("message", "서비스가 정상적으로 운영 중입니다.");

        return response;
    }
}