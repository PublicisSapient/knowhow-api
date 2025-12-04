/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.recommendations.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for project recommendations API.
 * Follows the same pattern as ProductivityResponse with summary and details structure.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response containing batch-calculated project recommendations")
public class RecommendationResponseDTO {

	@Schema(description = "List of project recommendations for the requested organizational level")
	private List<ProjectRecommendationDTO> recommendations;

	@Schema(description = "Total number of projects with recommendations", example = "15")
	private int totalProjects;

	@Schema(description = "Total number of projects queried", example = "20")
	private int totalProjectsQueried;

	@Schema(description = "Additional metadata about the recommendation retrieval", 
		example = "Retrieved 15 recommendations from 20 projects at project level")
	private String message;
}
