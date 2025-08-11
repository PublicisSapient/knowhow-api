package com.publicissapient.kpidashboard.apis.executive.mapper;

import com.publicissapient.kpidashboard.apis.executive.dto.*;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Mapper class to convert executive dashboard data to DTOs.
 */
public class ExecutiveDashboardMapper {

    /**
     * Converts the raw results into the executive dashboard response DTO.
     *
     * @param finalResults   The map of project metrics by project ID
     * @param projectConfigs The map of project configurations by project ID
     * @return The populated ExecutiveDashboardResponseDTO
     */
    public static ExecutiveDashboardResponseDTO toExecutiveDashboardResponse(
            Map<String, Map<String, Integer>> finalResults,
            Map<String, ProjectBasicConfig> projectConfigs,
            Map<String, Map<String, Object>> projectEfficiencies) {
        
        List<ProjectMetricsDTO> rows = finalResults.entrySet().stream()
                .map(entry -> {
                    String projectId = entry.getKey();
                    Map<String, Object> efficiency = projectEfficiencies.getOrDefault(projectId, new HashMap<>());
                    return toProjectMetricsDTO(projectId, entry.getValue(), projectConfigs, efficiency);
                })
                .collect(Collectors.toList());

        ExecutiveMatrixDTO matrix = ExecutiveMatrixDTO.builder()
                .rows(rows)
                .build();

        ExecutiveDashboardDataDTO data = ExecutiveDashboardDataDTO.builder()
                .matrix(matrix)
                .build();

        return ExecutiveDashboardResponseDTO.builder()
                .data(data)
                .build();
    }
    
    // Overloaded method for backward compatibility
    public static ExecutiveDashboardResponseDTO toExecutiveDashboardResponse(
            Map<String, Map<String, Integer>> finalResults,
            Map<String, ProjectBasicConfig> projectConfigs) {
        return toExecutiveDashboardResponse(finalResults, projectConfigs, new HashMap<>());
    }

    private static ProjectMetricsDTO toProjectMetricsDTO(
            String projectNodeId,
            Map<String, Integer> boardScores,
            Map<String, ProjectBasicConfig> projectConfigs,
            Map<String, Object> efficiency) {
        
        ProjectBasicConfig projectConfig = projectConfigs.get(projectNodeId);
        
        // Create a new BoardMaturityDTO with a new HashMap for each project
        Map<String, String> metricsMap = new HashMap<>();
        
        // Populate the metrics map with board scores
        if (boardScores != null) {
            boardScores.forEach((boardName, score) -> {
                if (boardName != null && score != null) {
                    metricsMap.put(capitalizeFirstLetter(boardName.trim()), "M" + score);
                }
            });
        }
        
        // Get completion and health status from efficiency metrics
        String completion = "0%";
        String health = "Unhealthy";
        
        if (efficiency != null) {
            Double percentage = (Double) efficiency.get("percentage");
            if (percentage != null) {
                completion = Math.round(percentage) + "%";
                
                // Set health status based on percentage
                if (percentage >= 80) {
                    health = "Healthy";
                } else if (percentage >= 50) {
                    health = "Moderate";
                } else {
                    health = "Unhealthy";
                }
            }
        }
        
        // Create the BoardMaturityDTO with the populated metrics map
        BoardMaturityDTO boardMaturity = BoardMaturityDTO.builder()
                .metrics(metricsMap)
                .build();

        return ProjectMetricsDTO.builder()
                .id(projectNodeId)
                .completion(completion)
                .health(health)
                .name(projectConfig != null ? projectConfig.getProjectName() : "Unknown")
                .boardMaturity(boardMaturity)
                .build();
    }
    
    private static String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
