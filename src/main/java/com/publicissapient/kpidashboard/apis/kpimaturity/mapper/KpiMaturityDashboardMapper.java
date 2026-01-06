/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */
package com.publicissapient.kpidashboard.apis.kpimaturity.mapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;

import com.publicissapient.kpidashboard.apis.kpimaturity.dto.BoardMaturityDTO;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.ColumnDefinitionDTO;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityDashboardDataDTO;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityMatrixDTO;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityResponseDTO;
import com.publicissapient.kpidashboard.apis.kpimaturity.dto.OrganizationEntityMaturityMetricsDTO;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;

/** Mapper class to convert executive dashboard data to DTOs. */
public class KpiMaturityDashboardMapper {

	/**
	 * Converts the raw results into the executive dashboard response DTO.
	 *
	 * @param finalResults The map of project metrics by project ID
	 * @param projectConfigs The map of project configurations by project ID
	 * @param levelName
	 * @return The populated ExecutiveDashboardResponseDTO
	 */
	public static KpiMaturityResponseDTO toExecutiveDashboardResponse(
			Map<String, Map<String, String>> finalResults,
			Map<String, OrganizationHierarchy> projectConfigs,
			Map<String, Map<String, Object>> projectEfficiencies,
			String levelName) {

		List<OrganizationEntityMaturityMetricsDTO> rows =
				finalResults.entrySet().stream()
						.map(
								entry -> {
									String uniqueId = entry.getKey();
									Map<String, Object> efficiency =
											projectEfficiencies.getOrDefault(uniqueId, new HashMap<>());
									return toProjectMetricsDTO(
											uniqueId, entry.getValue(), projectConfigs, efficiency);
								})
						.toList();

		// Get all unique board names from all projects
		List<String> boardNames =
				finalResults.values().stream()
						.flatMap(boardScores -> boardScores.keySet().stream())
						.distinct()
						.sorted()
						.toList();

		// Create column definitions
		List<ColumnDefinitionDTO> columns = addColumns(boardNames, levelName);

		KpiMaturityMatrixDTO matrix =
				KpiMaturityMatrixDTO.builder().rows(rows).columns(columns).build();

		KpiMaturityDashboardDataDTO data = KpiMaturityDashboardDataDTO.builder().matrix(matrix).build();

		return KpiMaturityResponseDTO.builder().data(data).build();
	}

	@NotNull
	private static List<ColumnDefinitionDTO> addColumns(List<String> boardNames, String levelName) {
		List<ColumnDefinitionDTO> columns = new ArrayList<>();

		// Add static columns
		columns.add(ColumnDefinitionDTO.builder().field("id").header("Project ID").build());
		columns.add(ColumnDefinitionDTO.builder().field("name").header(levelName + " Name").build());
		columns.add(ColumnDefinitionDTO.builder().field("completion").header("Efficiency(%)").build());
		columns.add(ColumnDefinitionDTO.builder().field("health").header("Overall Health").build());

		// Add dynamic board columns
		boardNames.forEach(
				boardName -> {
					String fieldName = boardName.toLowerCase();
					String header = capitalizeFirstLetter(boardName);
					columns.add(ColumnDefinitionDTO.builder().field(fieldName).header(header).build());
				});
		return columns;
	}

	private static OrganizationEntityMaturityMetricsDTO toProjectMetricsDTO(
			String uniqueId,
			Map<String, String> boardScores,
			Map<String, OrganizationHierarchy> projectConfigs,
			Map<String, Object> efficiency) {

		OrganizationHierarchy organizationHierarchy = projectConfigs.get(uniqueId);

		// Create a new BoardMaturityDTO with a new HashMap for each project
		Map<String, String> metricsMap = new HashMap<>();

		// Populate the metrics map with board scores
		if (boardScores != null) {
			boardScores.forEach(
					(boardName, score) -> {
						if (boardName != null && score != null) {
							if (score.equalsIgnoreCase("NA")) {
								metricsMap.put(boardName.trim().toLowerCase(), score);
							} else {
								metricsMap.put(
										boardName.trim().toLowerCase(),
										"M" + (int) Math.ceil(Double.parseDouble(score)));
							}
						}
					});
		}

		CalculateHealth result = getCalculateHealth(efficiency);

		// Create the BoardMaturityDTO with the populated metrics map
		BoardMaturityDTO boardMaturity = BoardMaturityDTO.builder().metrics(metricsMap).build();

		return OrganizationEntityMaturityMetricsDTO.builder()
				.id(uniqueId)
				.completion(result.completion())
				.health(result.health())
				.name(
						organizationHierarchy != null ? organizationHierarchy.getNodeDisplayName() : "Unknown")
				.boardMaturity(boardMaturity)
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
		if (str.equalsIgnoreCase("dora")) {
			return "DORA";
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
	}

	private record CalculateHealth(String completion, String health) {}
}
