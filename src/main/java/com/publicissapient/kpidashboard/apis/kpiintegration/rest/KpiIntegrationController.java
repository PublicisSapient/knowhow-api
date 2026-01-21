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

package com.publicissapient.kpidashboard.apis.kpiintegration.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.kpiintegration.service.KpiIntegrationServiceImpl;
import com.publicissapient.kpidashboard.apis.kpiintegration.service.impl.KpiRecommendationServiceImpl;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRecommendationRequestDTO;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * exposed API for fetching kpi data
 *
 * @author kunkambl
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(
		name = "KPI Integration",
		description =
				"APIs for processing KPI requests and recommendations across Scrum and Kanban methodologies")
public class KpiIntegrationController {

	private final KpiIntegrationServiceImpl kpiIntegrationService;
	private final KpiRecommendationServiceImpl kpiRecommendationService;

	@Operation(
			summary = "Get Scrum KPI maturity values",
			description =
					"Processes Scrum-based KPI requests and returns calculated maturity values for the specified KPIs")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "KPI maturity values calculated successfully",
						content =
								@Content(
										mediaType = APPLICATION_JSON_VALUE,
										array = @ArraySchema(schema = @Schema(implementation = KpiElement.class)))),
				@ApiResponse(
						responseCode = "400",
						description = "Invalid request parameters or malformed KPI request",
						content = @Content(mediaType = APPLICATION_JSON_VALUE)),
				@ApiResponse(
						responseCode = "401",
						description = "Unauthorized - Invalid or missing authentication",
						content = @Content(mediaType = APPLICATION_JSON_VALUE)),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error during KPI processing",
						content = @Content(mediaType = APPLICATION_JSON_VALUE))
			})
	@PostMapping(value = "/kpiIntegrationValues", produces = APPLICATION_JSON_VALUE)
	public ResponseEntity<List<KpiElement>> getScrumKpiValues(
			@Parameter(
							description = "KPI request containing list of KPIs to process and filter criteria",
							required = true)
					@RequestBody
					KpiRequest kpiRequest) {
		return ResponseEntity.ok(kpiIntegrationService.processScrumKpiRequest(kpiRequest));
	}

	@Operation(
			summary = "Get Kanban KPI values",
			description =
					"Processes Kanban-based KPI requests and returns calculated values for the specified KPIs")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Kanban KPI values calculated successfully",
						content =
								@Content(
										mediaType = APPLICATION_JSON_VALUE,
										array = @ArraySchema(schema = @Schema(implementation = KpiElement.class)))),
				@ApiResponse(
						responseCode = "400",
						description = "Invalid request parameters or malformed KPI request",
						content = @Content(mediaType = APPLICATION_JSON_VALUE)),
				@ApiResponse(
						responseCode = "401",
						description = "Unauthorized - Invalid or missing authentication",
						content = @Content(mediaType = APPLICATION_JSON_VALUE)),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error during KPI processing",
						content = @Content(mediaType = APPLICATION_JSON_VALUE))
			})
	@PostMapping("/kpi-integration-values/kanban")
	public ResponseEntity<List<KpiElement>> getKanbanKpiValues(
			@Parameter(
							description =
									"KPI request containing list of Kanban KPIs to process and filter criteria",
							required = true)
					@RequestBody
					KpiRequest kpiRequest) {
		return ResponseEntity.ok(this.kpiIntegrationService.processKanbanKPIRequest(kpiRequest));
	}

	@Operation(
			summary = "Get KPI recommendations",
			description =
					"Provides project-wise KPI recommendations based on analysis criteria and historical data")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "KPI recommendations generated successfully",
						content =
								@Content(
										mediaType = APPLICATION_JSON_VALUE,
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(
						responseCode = "400",
						description = "Invalid recommendation request parameters",
						content = @Content(mediaType = APPLICATION_JSON_VALUE)),
				@ApiResponse(
						responseCode = "401",
						description = "Unauthorized - Invalid or missing authentication",
						content = @Content(mediaType = APPLICATION_JSON_VALUE)),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error during recommendation processing",
						content = @Content(mediaType = APPLICATION_JSON_VALUE))
			})
	@PostMapping(value = "/kpiRecommendation", produces = APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getKpiRecommendation(
			@Parameter(
							description =
									"KPI recommendation request containing project IDs and analysis criteria",
							required = true)
					@RequestBody
					KpiRecommendationRequestDTO kpiRecommendationRequestDTO) {
		return ResponseEntity.ok()
				.body(
						kpiRecommendationService.getProjectWiseKpiRecommendation(kpiRecommendationRequestDTO));
	}
}
