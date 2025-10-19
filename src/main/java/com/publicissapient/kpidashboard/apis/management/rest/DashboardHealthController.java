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

package com.publicissapient.kpidashboard.apis.management.rest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.management.service.DashboardHealthService;
import com.publicissapient.kpidashboard.apis.model.HealthResponseDto;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("${dashboard.healthApiBasePath}")
@Tag(
		name = "Dashboard Health",
		description = "API endpoints for monitoring dashboard health metrics")
public class DashboardHealthController {

	private final DashboardHealthService dashboardHealthService;

	@Operation(
			summary = "Get overall dashboard health",
			description = "Retrieves aggregated health metrics for all dashboard types")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved dashboard health overview",
						content =
								@Content(
										mediaType = MediaType.APPLICATION_JSON_VALUE,
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error",
						content = @Content)
			})
	@GetMapping
	public ResponseEntity<ServiceResponse> getDashboardHealth() {
		log.debug("Received request for dashboard health overview");
		HealthResponseDto response = dashboardHealthService.getDashboardHealth();
		return ResponseEntity.ok(
				new ServiceResponse(true, "Dashboard health overview retrieved successfully", response));
	}

	@Operation(
			summary = "Get board type health",
			description = "Retrieves health metrics for a specific board type (e.g., scrum, kanban)")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved board type health",
						content =
								@Content(
										mediaType = MediaType.APPLICATION_JSON_VALUE,
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(
						responseCode = "400",
						description = "Invalid board type provided",
						content = @Content),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error",
						content = @Content)
			})
	@GetMapping("/{boardType}")
	public ResponseEntity<ServiceResponse> getBoardTypeHealth(@PathVariable String boardType) {
		log.debug("Received request for board type health: {}", boardType);
		HealthResponseDto response = dashboardHealthService.getBoardTypeHealth(boardType);
		return ResponseEntity.ok(
				new ServiceResponse(true, "Board type health retrieved successfully", response));
	}

	@Operation(
			summary = "Get dashboard detail health",
			description =
					"Retrieves detailed health metrics for a specific dashboard within a board type")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved dashboard detail health",
						content =
								@Content(
										mediaType = MediaType.APPLICATION_JSON_VALUE,
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(
						responseCode = "400",
						description = "Invalid board type or dashboard name provided",
						content = @Content),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error",
						content = @Content)
			})
	@GetMapping("/{boardType}/{dashboard}")
	public ResponseEntity<ServiceResponse> getDashboardDetailHealth(
			@PathVariable String boardType, @PathVariable String dashboard) {
		log.debug("Received request for dashboard detail health: {}/{}", boardType, dashboard);
		HealthResponseDto response =
				dashboardHealthService.getDashboardDetailHealth(boardType, dashboard);
		return ResponseEntity.ok(
				new ServiceResponse(true, "Dashboard detail health retrieved successfully", response));
	}
}
