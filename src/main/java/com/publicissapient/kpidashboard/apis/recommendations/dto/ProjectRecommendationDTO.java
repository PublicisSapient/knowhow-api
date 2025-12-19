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

import java.time.Instant;

import com.publicissapient.kpidashboard.common.model.recommendation.batch.Persona;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.Recommendation;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationLevel;
import com.publicissapient.kpidashboard.common.model.recommendation.batch.RecommendationMetadata;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a project's AI-generated recommendations. Maps from
 * RecommendationsActionPlan entity excluding internal fields.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Project-level AI-generated recommendations with action plans")
public class ProjectRecommendationDTO {

	@NotBlank(message = "Recommendation ID must not be blank")
	@Schema(description = "MongoDB ID of the recommendation document", example = "507f1f77bcf86cd799439011")
	private String id;

	@NotBlank(message = "Project ID must not be blank")
	@Schema(description = "Project identifier from hierarchy", example = "507f1f77bcf86cd799439011")
	private String projectId;

	@NotBlank(message = "Project name must not be blank")
	@Schema(description = "Human-readable project name", example = "KnowHOW")
	private String projectName;

	@Schema(description = "Target persona for the recommendations")
	private Persona persona;

	@Schema(description = "Organizational level of the recommendation")
	private RecommendationLevel level;

	@Schema(description = "Main recommendation with action plans and severity")
	private Recommendation recommendations;

	@Schema(description = "Metadata about KPIs and persona used for generating recommendations")
	private RecommendationMetadata metadata;

	@Schema(description = "Timestamp when the recommendation was generated", example = "2024-11-29T10:30:00Z")
	private Instant createdAt;
}
