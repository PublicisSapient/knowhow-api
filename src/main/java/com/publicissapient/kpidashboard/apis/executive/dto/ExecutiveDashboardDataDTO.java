package com.publicissapient.kpidashboard.apis.executive.dto;

import lombok.Builder;
import lombok.Data;

/**
 * DTO representing the data container for the executive dashboard response.
 * Contains the matrix structure with all project metrics.
 */
@Data
@Builder
public class ExecutiveDashboardDataDTO {
    /**
     * The matrix structure containing all project metrics.
     */
    private ExecutiveMatrixDTO matrix;
}
