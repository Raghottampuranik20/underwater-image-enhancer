package com.uie.enhancer.controller;

import com.uie.enhancer.service.ai.OnnxEnhancementService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    private final OnnxEnhancementService onnxEnhancementService;

    public HealthController(OnnxEnhancementService onnxEnhancementService) {
        this.onnxEnhancementService = onnxEnhancementService;
    }

    @GetMapping("/api/v1/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "aiModelLoaded", onnxEnhancementService.isAvailable()
        );
    }
}
