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

package com.publicissapient.kpidashboard.apis.peb.productivity.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.peb.productivity.dto.ProductivityRequest;
import com.publicissapient.kpidashboard.apis.peb.productivity.dto.ProductivityResponse;
import com.publicissapient.kpidashboard.apis.peb.productivity.service.ProductivityService;
import com.publicissapient.kpidashboard.common.shared.enums.TemporalAggregationUnit;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/v1/peb/productivity")
@RequiredArgsConstructor
@Tag(
		name = "PEB Productivity API v1",
		description =
				"APIs for retrieving productivity metrics and analytics data based on organizational hierarchy levels")
public class ProductivityControllerV1 {
	private final ProductivityService productivityService;

	@Operation(
			summary = "Get productivity data by hierarchy level",
			description =
					"Retrieves comprehensive productivity metrics and KPIs for a specific organizational hierarchy level. "
							+ "The hierarchy level determines the scope and aggregation of productivity data returned.",
			operationId = "getPebProductivityData")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Productivity data retrieved successfully",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ProductivityResponse.class))),
				@ApiResponse(
						responseCode = "400",
						description =
								"""
					Bad Request will be returned in the following situations:
					- received level name was empty
					- requested level is too low on the organizational hierarchy
					"""),
				@ApiResponse(
						responseCode = "403",
						description =
								"""
					Forbidden will be returned in the following situations:
					- user doesn't have access to any data
					"""),
				@ApiResponse(
						responseCode = "404",
						description =
								"""
					Not Found will be returned in the following situations:
					- requested level name does not exist
					""",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(
						responseCode = "500",
						description =
								"""
					Internal Server Error will be returned in the following situations:
					- multiple levels were found corresponding with the requested level name
					- no organizational level could be found relating to a 'project' entity
					- an unexpected error occurred when processing the request
					""",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class)))
			})
	@GetMapping({"", "/"})
	public ResponseEntity<ServiceResponse> getPebProductivityData(
			@Parameter(
							name = "levelName",
							description =
									"The name of the organizational hierarchy level for which to retrieve productivity "
											+ "data",
							required = true,
							example = "project")
					@RequestParam
					@NotBlank(message = "The level name is required")
					String levelName,
			@Parameter(
							name = "parentNodeId",
							description =
									"The node id of the organizational entity parent for "
											+ "which to retrieve productivity data",
							example = "110ab8b5-0f89-4de1-b353-e9c70d506fe0")
					@RequestParam(required = false)
					String parentNodeId) {

		return ResponseEntity.ok(
				this.productivityService.getProductivityForLevel(
						new ProductivityRequest(levelName, parentNodeId)));
	}

	@Operation(
			summary = "Get productivity trends by hierarchy level",
			description =
					"""
			Retrieves historical productivity trend data for a specific organizational hierarchy level over time. This endpoint provides time-series productivity analytics aggregated by weekly intervals, showing how productivity metrics evolve across different time periods.
			""")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Productivity trends data retrieved successfully",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(
						responseCode = "400",
						description =
								"""
					Bad Request will be returned in the following situations:
					- received level name was empty
					- requested level is too low on the organizational hierarchy
					"""),
				@ApiResponse(
						responseCode = "403",
						description =
								"""
					Forbidden will be returned in the following situations:
					- user doesn't have access to any data
					"""),
				@ApiResponse(
						responseCode = "404",
						description =
								"""
					Not Found will be returned in the following situations:
					- requested level name does not exist
					"""),
				@ApiResponse(
						responseCode = "500",
						description =
								"""
					Internal Server Error will be returned in the following situations:
					- multiple levels were found corresponding with the requested level name
					- no organizational level could be found relating to a 'project' entity
					- an unexpected error occurred when processing the request
					""")
			})
	@GetMapping("/trends")
	public ResponseEntity<ServiceResponse> getPebProductivityTrends(
			@Parameter(
							name = "levelName",
							description =
									"The name of the organizational hierarchy level for which to"
											+ " retrieve productivity trends data",
							required = true,
							example = "project")
					@RequestParam
					@NotBlank(message = "The level name is required")
					String levelName) {
		return ResponseEntity.ok(
				this.productivityService.getProductivityTrendsForLevel(
						levelName,
						TemporalAggregationUnit.WEEK,
						ProductivityService.DEFAULT_NUMBER_OF_TREND_DATA_POINTS));
	}
}
