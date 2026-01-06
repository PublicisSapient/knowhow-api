package com.publicissapient.kpidashboard.apis.recommendations.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Summary containing aggregate statistics from all recommendations. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Summary with aggregate recommendation statistics")
public class RecommendationSummaryDTO {

	@NotBlank(message = "Level name must not be blank")
	@Schema(description = "Organizational hierarchy level name", example = "project")
	private String levelName;

	@NotNull(message = "Total projects with recommendations must not be null")
	@Schema(description = "Total number of projects with recommendations", example = "15")
	private Integer totalProjectsWithRecommendations;

	@NotNull(message = "Total projects queried must not be null")
	@Schema(description = "Total number of projects queried at this level", example = "20")
	private Integer totalProjectsQueried;

	@NotNull(message = "Total recommendations must not be null")
	@Schema(description = "Total number of recommendations across all projects", example = "45")
	private Integer totalRecommendations;

	@NotBlank(message = "Summary message must not be blank")
	@Schema(description = "Summary message with retrieval details")
	private String message;
}
