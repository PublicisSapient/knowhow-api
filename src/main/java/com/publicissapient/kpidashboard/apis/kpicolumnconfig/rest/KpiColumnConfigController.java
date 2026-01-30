/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 *    http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.kpicolumnconfig.rest;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.kpicolumnconfig.service.KpiColumnConfigService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.model.application.KpiColumnConfigDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/kpi-column-config")
@RequiredArgsConstructor
@Tag(name = "KPI Column Config API", description = "APIs for KPI Column Configuration Management")
public class KpiColumnConfigController {

	private final KpiColumnConfigService kpiColumnConfigService;

	/**
	 * Api to get kpi column configurations
	 *
	 * @return response
	 */
	@Operation(
			summary = "Get KPI Column Configuration",
			description = "Retrieves the column configuration for a specific KPI within a project")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved KPI column configuration",
						content = {
							@Content(
									mediaType = "application/json",
									schema = @Schema(implementation = ServiceResponse.class))
						}),
				@ApiResponse(
						responseCode = "404",
						description = "KPI column configuration not found",
						content = @Content),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error",
						content = @Content)
			})
	@GetMapping(
			value = "/{basicProjectConfigId}/{kpiId}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getKpiColumnConfiguration(
			@Parameter(
							description = "Basic Project Configuration ID",
							example = "60d21b4667d0d8992e610c85",
							required = true)
					@PathVariable
					String basicProjectConfigId,
			@Parameter(description = "KPI ID", example = "kpi123", required = true) @PathVariable
					String kpiId) {
		KpiColumnConfigDTO kpiColumnConfigDTO =
				kpiColumnConfigService.getByKpiColumnConfig(basicProjectConfigId, kpiId);

		ServiceResponse response = new ServiceResponse(false, "No data found", null);
		if (null != kpiColumnConfigDTO) {
			response = new ServiceResponse(true, "Fetched successfully", kpiColumnConfigDTO);
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}

	/**
	 * Api to save kpi column config
	 *
	 * @param kpiColumnConfigDTO *
	 * @return response
	 */
	@Operation(
			summary = "Post request to save KPI Column Configuration",
			description = "Saves or updates the column configuration for a specific KPI within a project")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "KPI column configuration saved successfully",
						content = {
							@Content(
									mediaType = "application/json",
									schema = @Schema(implementation = ServiceResponse.class))
						}),
				@ApiResponse(
						responseCode = "400",
						description = "Invalid input data for KPI column configuration",
						content = @Content),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error",
						content = @Content)
			})
	@PostMapping(
			value = "/kpiColumnConfig",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> saveKpiColumnConfig(
			@Parameter(
							description = "KPI Column Configuration data to be saved or updated",
							required = true)
					@Valid
					@RequestBody
					KpiColumnConfigDTO kpiColumnConfigDTO) {
		return ResponseEntity.status(HttpStatus.OK)
				.body(kpiColumnConfigService.saveKpiColumnConfig(kpiColumnConfigDTO));
	}
}
