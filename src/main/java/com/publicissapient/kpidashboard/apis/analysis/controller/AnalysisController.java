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

package com.publicissapient.kpidashboard.apis.analysis.controller;

import javax.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.analysis.analytics.aiusage.service.AiUsageAnalyticsService;
import com.publicissapient.kpidashboard.apis.analysis.analytics.shared.dto.BaseAnalyticsRequestDTO;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.service.SprintAnalyticsService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * REST controller exposing analysis dashboard endpoints.
 *
 * <p>Provides endpoints to compute analytics such as AI usage and sprint metrics.
 */
@RestController
@RequestMapping("/analysis")
@RequiredArgsConstructor
@Tag(name = "Analysis", description = "Compute analytics like AI usage and sprint metrics")
public class AnalysisController {

	private final AiUsageAnalyticsService aiUsageAnalyticsService;
	private final SprintAnalyticsService sprintAnalyticsService;

	/**
	 * Compute AI usage analytics for requested projects.
	 *
	 * @param aiUsageAnalyticsRequestDTO request containing project ids and filters
	 * @return {@link ServiceResponse} with AI usage analytics payload
	 */
	@Operation(
			summary = "Compute AI usage analytics",
			description = "Compute AI usage metrics for requested projects/sprints")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "AI usage analytics computed successfully",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(responseCode = "400", description = "Bad request - invalid input"),
				@ApiResponse(
						responseCode = "403",
						description = "Forbidden - no access to requested projects"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@PostMapping("/analytics/ai-usage/query")
	public ResponseEntity<ServiceResponse> computeAiUsageAnalyticsData(
			@RequestBody @Valid BaseAnalyticsRequestDTO aiUsageAnalyticsRequestDTO) {
		return ResponseEntity.ok(
				aiUsageAnalyticsService.computeAiUsageAnalyticsData(aiUsageAnalyticsRequestDTO));
	}

	/**
	 * Compute sprint analytics for requested projects and sprints.
	 *
	 * @param sprintAnalyticsRequestDTO request containing project ids and no of sprints to include
	 * @return {@link ServiceResponse} with sprint analytics payload
	 */
	@Operation(
			summary = "Compute sprint analytics",
			description = "Compute sprint-related metrics for requested projects/sprints")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Sprint analytics computed successfully",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(responseCode = "400", description = "Bad request - invalid input"),
				@ApiResponse(
						responseCode = "403",
						description = "Forbidden - no access to requested projects"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@PostMapping("/analytics/sprint/query")
	public ResponseEntity<ServiceResponse> computeSprintAnalyticsData(
			@RequestBody @Valid BaseAnalyticsRequestDTO sprintAnalyticsRequestDTO) {
		return ResponseEntity.ok(
				sprintAnalyticsService.computeSprintAnalyticsData(sprintAnalyticsRequestDTO));
	}
}
