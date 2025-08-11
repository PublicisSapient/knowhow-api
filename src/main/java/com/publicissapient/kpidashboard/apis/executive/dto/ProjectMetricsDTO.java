package com.publicissapient.kpidashboard.apis.executive.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO representing the metrics for a single project in the executive dashboard.
 */
@Data
@Builder
public class ProjectMetricsDTO {
    /**
     * The unique identifier for the project.
     */
    private String id;
    
    /**
     * The display name of the project.
     */
    private String name;
    
    /**
     * The completion percentage of the project.
     * Format: "XX%" (e.g., "73%")
     */
    private String completion;
    
    /**
     * The health status of the project.
     * Example: "Healthy", "At Risk", "Critical"
     */
    private String health;
    
    /**
     * The board maturity metrics for the project.
     * Contains key-value pairs of board names and their maturity levels.
     */
    private BoardMaturityDTO boardMaturity;
}
