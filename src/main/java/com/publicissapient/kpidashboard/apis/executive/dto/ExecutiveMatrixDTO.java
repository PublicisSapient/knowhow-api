package com.publicissapient.kpidashboard.apis.executive.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO representing the matrix data structure for the executive dashboard.
 * Contains a list of project metrics rows.
 */
@Data
@Builder
public class ExecutiveMatrixDTO {
    /**
     * List of project metrics rows.
     * Each row contains metrics for a specific project.
     */
    private List<ProjectMetricsDTO> rows;
}
