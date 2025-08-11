package com.publicissapient.kpidashboard.apis.executive.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * DTO representing the board maturity metrics for a project.
 * Dynamically holds key-value pairs of board names and their maturity levels.
 */
@Data
@Builder
public class BoardMaturityDTO {
    /**
     * Map of board names to their maturity levels.
     * Example: {"speed": "M1", "quality": "M3"}
     */
    @Builder.Default
    private Map<String, String> metrics = new HashMap<>();
    
    /**
     * Adds a board metric to the maturity map.
     * @param boardName The name of the board (e.g., "speed", "quality")
     * @param maturityLevel The maturity level (e.g., "M1", "M2")
     */
    public void addMetric(String boardName, String maturityLevel) {
        metrics.put(boardName, maturityLevel);
    }
    
    /**
     * Jackson annotation to serialize the metrics map as top-level properties.
     */
    @JsonAnyGetter
    public Map<String, String> getMetrics() {
        return metrics;
    }
}
