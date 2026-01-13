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
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for project recommendations API. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(
		description =
				"Response containing batch-calculated project recommendations with summary and details")
public class RecommendationResponseDTO {

	@NotNull(message = "Summary must not be null")
	@Valid
	@Schema(description = "Summary with basic statistics and aggregated recommendation counts")
	private RecommendationSummaryDTO summary;

	@NotNull(message = "Details list must not be null")
	@Valid
	@Schema(description = "Detailed list of recommendations for each project")
	private List<ProjectRecommendationDTO> details;
}
