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

package com.publicissapient.kpidashboard.apis.kpimaturity.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.kpimaturity.dto.KpiMaturityRequest;
import com.publicissapient.kpidashboard.apis.kpimaturity.service.KpiMaturityService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.shared.enums.ProjectDeliveryMethodology;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequestMapping("/v1/kpi-maturity")
@RequiredArgsConstructor
@Tag(
		name = "KPI Maturity",
		description = "APIs for managing and retrieving KPI maturity assessments")
public class KpiMaturityController {

	private final KpiMaturityService kpiMaturityService;

	@Operation(
			summary = "Retrieve KPI Maturity Assessment",
			description =
					"Fetches KPI maturity data based on organizational level, delivery methodology, and optional parent node. "
							+ "This endpoint provides comprehensive maturity metrics for performance indicators at different organizational levels.",
			operationId = "getKpiMaturity")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved KPI maturity data",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(
						responseCode = "400",
						description = "Invalid request parameters",
						content = @Content(mediaType = "application/json")),
				@ApiResponse(
						responseCode = "404",
						description = "KPI maturity data not found for the specified parameters",
						content = @Content(mediaType = "application/json")),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error",
						content = @Content(mediaType = "application/json"))
			})
	@GetMapping({"", "/"})
	public ResponseEntity<ServiceResponse> getKpiMaturity(
			@Parameter(
							name = "levelName",
							description =
									"The organizational level name for which KPI maturity is " + "requested",
							required = true,
							example = "engagement")
					@RequestParam
					@NotBlank(message = "The 'levelName' is required")
					String levelName,
			@Parameter(
							name = "deliveryMethodology",
							description =
									"The project delivery methodology used for KPI assessment. "
											+ "Determines which KPI frameworks and benchmarks to apply.",
							required = true,
							schema = @Schema(implementation = ProjectDeliveryMethodology.class))
					@RequestParam
					ProjectDeliveryMethodology deliveryMethodology,
			@Parameter(
							name = "parentNodeId",
							description =
									"Optional identifier of the parent organizational node. "
											+ "Used for hierarchical KPI maturity analysis and rollup calculations. "
											+ "If not provided, treats the request as a root-level assessment.")
					@RequestParam(required = false)
					String parentNodeId) {

		return ResponseEntity.ok(
				this.kpiMaturityService.getKpiMaturity(
						KpiMaturityRequest.builder()
								.levelName(levelName)
								.deliveryMethodology(deliveryMethodology)
								.parentNodeId(parentNodeId)
								.build()));
	}
}
