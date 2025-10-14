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

package com.publicissapient.kpidashboard.apis.ai.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import javax.validation.constraints.NotNull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.ai.dto.request.sprint.goals.SummarizeSprintGoalsRequestDTO;
import com.publicissapient.kpidashboard.apis.ai.dto.response.search.kpi.SearchKpiResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.dto.response.sprint.goals.SummarizeSprintGoalsResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.service.search.kpi.SearchKPIService;
import com.publicissapient.kpidashboard.apis.ai.service.sprint.goals.SprintGoalsService;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.kpiintegration.service.impl.KpiRecommendationServiceImpl;
import com.publicissapient.kpidashboard.apis.model.KpiRecommendationRequestDTO;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor
@RestController
@RequestMapping("/ai")
@Slf4j
@Tag(name = "AI", description = "Endpoints for managing the AI-generated content")
public class AiController {

	private final SprintGoalsService sprintGoalsService;

	@Autowired private KpiRecommendationServiceImpl kpiRecommendationServiceImpl;

	private final SearchKPIService searchKPIService;

	@PostMapping("/sprint-goals/summary")
	@Operation(
			summary = "Summarize Sprint Goals",
			description = "Generates a summary of the provided sprint goals using AI")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully generated the sprint goals summary",
						content = {
							@Content(
									mediaType = "application/json",
									schema = @Schema(implementation = SummarizeSprintGoalsResponseDTO.class))
						}),
				@ApiResponse(
						responseCode = "400",
						description =
								"""
					Bad request. Can happen in one of the following cases:
					- No request body was provided
					- The request body does not contain the required fields
					"""),
				@ApiResponse(
						responseCode = "500",
						description =
								"""
					Unexpected server error occurred. Can happen in one of the following cases:
					- AI gateway failed to process the request
					- Prompt configuration is invalid
					""")
			})
	public ResponseEntity<SummarizeSprintGoalsResponseDTO> summarizeSprintGoals(
			@Valid @RequestBody SummarizeSprintGoalsRequestDTO summarizeSprintGoalsRequestDTO)
			throws EntityNotFoundException, IOException {
		return ResponseEntity.ok(
				sprintGoalsService.summarizeSprintGoals(summarizeSprintGoalsRequestDTO));
	}

	@PostMapping(value = "/kpiRecommendation", produces = APPLICATION_JSON_VALUE)
	@Operation(
			summary = "Get KPI Recommendation",
			description =
					"Generates Health of the selected project and recommendations based on the provided kpi request")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully generated project recommendation",
						content = {
							@Content(
									mediaType = "application/json",
									schema = @Schema(implementation = SummarizeSprintGoalsResponseDTO.class))
						}),
				@ApiResponse(
						responseCode = "400",
						description =
								"""
					Bad request. Can happen in one of the following cases:
					- No request body was provided
					- The request body does not contain the required fields
					"""),
				@ApiResponse(
						responseCode = "503",
						description =
								"""
					Unexpected server error occurred. Can happen in one of the following cases:
					- AI gateway failed to process the request
					- Prompt configuration is invalid
					""")
			})
	public ResponseEntity<ServiceResponse> getKpiRecommendation(
			@NotNull @RequestBody KpiRecommendationRequestDTO kpiRecommendationRequestDTO) {
		return ResponseEntity.ok()
				.body(
						kpiRecommendationServiceImpl.getProjectWiseKpiRecommendation(
								kpiRecommendationRequestDTO));
	}

	@GetMapping(value = "/kpisearch", produces = APPLICATION_JSON_VALUE)
	@Operation(
			summary = "Get KPI Search",
			description = "Produces a possible kpis match for the user entered message")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully matched kpis",
						content = {
							@Content(
									mediaType = "application/json",
									schema = @Schema(implementation = SummarizeSprintGoalsResponseDTO.class))
						}),
				@ApiResponse(
						responseCode = "503",
						description =
								"""
					Unexpected server error occurred. Can happen in one of the following cases:
					- AI gateway failed to process the request
					- Prompt configuration is invalid
					""")
			})
	public ResponseEntity<SearchKpiResponseDTO> getRelevantKPIs(
			@RequestParam(required = true) String query) throws EntityNotFoundException {
		return ResponseEntity.ok().body(searchKPIService.searchRelatedKpi(query));
	}
}
