/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/
package com.publicissapient.kpidashboard.apis.executive.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import org.jetbrains.annotations.NotNull;

import com.publicissapient.kpidashboard.apis.executive.dto.BoardMaturityDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ColumnDefinitionDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardDataDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveMatrixDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ProjectMetricsDTO;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;

/**
 * Mapper class to convert executive dashboard data to DTOs.
 */
public class ExecutiveDashboardMapper {

	/**
	 * Converts the raw results into the executive dashboard response DTO.
	 *
	 * @param finalResults
	 *            The map of project metrics by project ID
	 * @param projectConfigs
	 *            The map of project configurations by project ID
	 * @return The populated ExecutiveDashboardResponseDTO
	 */
	public static ExecutiveDashboardResponseDTO toExecutiveDashboardResponse(
			Map<String, Map<String, Integer>> finalResults, Map<String, OrganizationHierarchy> projectConfigs,
			Map<String, Map<String, Object>> projectEfficiencies) {

		List<ProjectMetricsDTO> rows = finalResults.entrySet().stream().map(entry -> {
			String uniqueId = entry.getKey();
			Map<String, Object> efficiency = projectEfficiencies.getOrDefault(uniqueId, new HashMap<>());
			return toProjectMetricsDTO(uniqueId, entry.getValue(), projectConfigs, efficiency);
		}).collect(Collectors.toList());

		// Get all unique board names from all projects
		List<String> boardNames = finalResults.values().stream().flatMap(boardScores -> boardScores.keySet().stream())
				.distinct().sorted().collect(Collectors.toList());

		// Create column definitions
		List<ColumnDefinitionDTO> columns = addColumns(boardNames);

		ExecutiveMatrixDTO matrix = ExecutiveMatrixDTO.builder().rows(rows).columns(columns).build();

		ExecutiveDashboardDataDTO data = ExecutiveDashboardDataDTO.builder().matrix(matrix).build();

		return ExecutiveDashboardResponseDTO.builder().data(data).build();
	}

	@NotNull
	private static List<ColumnDefinitionDTO> addColumns(List<String> boardNames) {
		List<ColumnDefinitionDTO> columns = new ArrayList<>();

		// Add static columns
		columns.add(ColumnDefinitionDTO.builder().field("id").header("Project ID").build());
		columns.add(ColumnDefinitionDTO.builder().field("name").header("Project name").build());
		columns.add(ColumnDefinitionDTO.builder().field("completion").header("Complete(%)").build());
		columns.add(ColumnDefinitionDTO.builder().field("health").header("Overall health").build());

		// Add dynamic board columns
		boardNames.forEach(boardName -> {
			String fieldName = boardName.toLowerCase();
			String header = capitalizeFirstLetter(boardName);
			columns.add(ColumnDefinitionDTO.builder().field(fieldName).header(header).build());
		});
		return columns;
	}

	private static ProjectMetricsDTO toProjectMetricsDTO(String uniqueId, Map<String, Integer> boardScores,
														 Map<String, OrganizationHierarchy> projectConfigs, Map<String, Object> efficiency) {

		OrganizationHierarchy organizationHierarchy = projectConfigs.get(uniqueId);

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

		CalculateHealth result = getCalculateHealth(efficiency);

		// Create the BoardMaturityDTO with the populated metrics map
		BoardMaturityDTO boardMaturity = BoardMaturityDTO.builder().metrics(metricsMap).build();

		return ProjectMetricsDTO.builder().id(uniqueId).completion(result.completion()).health(result.health())
				.name(organizationHierarchy != null ? organizationHierarchy.getNodeDisplayName() : "Unknown").boardMaturity(boardMaturity)
				.build();
	}

	@NotNull
	private static CalculateHealth getCalculateHealth(Map<String, Object> efficiency) {
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
		return new CalculateHealth(completion, health);
	}

	private static String capitalizeFirstLetter(String str) {
		if (str == null || str.isEmpty()) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
	}

	private record CalculateHealth(String completion, String health) {
	}
}
