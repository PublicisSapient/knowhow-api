package com.publicissapient.kpidashboard.apis.executive.dto;

import lombok.Builder;
import lombok.Data;

/**
 * The root DTO for the executive dashboard API response.
 * Wraps all the dashboard data in a 'data' field.
 */
@Data
@Builder
public class ExecutiveDashboardResponseDTO {
    /**
     * The main data container for the executive dashboard response.
     */
    private ExecutiveDashboardDataDTO data;
}
