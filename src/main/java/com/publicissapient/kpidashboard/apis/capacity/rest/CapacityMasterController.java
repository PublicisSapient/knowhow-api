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

package com.publicissapient.kpidashboard.apis.capacity.rest;

import java.util.List;
import javax.validation.Valid;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import com.publicissapient.kpidashboard.apis.abac.ContextAwarePolicyEnforcement;
import com.publicissapient.kpidashboard.apis.capacity.service.CapacityMasterService;
import com.publicissapient.kpidashboard.apis.capacity.service.HappinessKpiCapacityImpl;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.constant.Role;
import com.publicissapient.kpidashboard.common.model.application.CapacityMaster;
import com.publicissapient.kpidashboard.common.model.jira.HappinessKpiDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * @author narsingh9
 */
@RestController
@RequestMapping("/capacity")
@RequiredArgsConstructor
@Tag(name = "Capacity Master Controller", description = "APIs for Capacity Master Management")
public class CapacityMasterController {

	private final CapacityMasterService capacityMasterService;

	private final ContextAwarePolicyEnforcement policy;

	private final HappinessKpiCapacityImpl happinessKpiService;

	/**
	 * This api saves capacity data.
	 *
	 * @param capacityMaster data to be saved
	 * @return service response entity
	 */
	@Operation(summary = "Add Capacity Data", description = "API to add capacity data")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Successfully added Capacity Data"),
				@ApiResponse(responseCode = "400", description = "Bad Request"),
				@ApiResponse(responseCode = "500", description = "Internal Server Error")
			})
	@PostMapping(
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> addCapacity(
			@Parameter(description = "Capacity Master Object", required = true) @RequestBody
					CapacityMaster capacityMaster) {
		ServiceResponse response = new ServiceResponse(false, "Failed to add Capacity Data", null);
		try {
			policy.checkPermission(capacityMaster, "SAVE_UPDATE_CAPACITY");
			capacityMaster = capacityMasterService.processCapacityData(capacityMaster);

			if (null != capacityMaster) {
				response = new ServiceResponse(true, "Successfully added Capacity Data", capacityMaster);
			}
		} catch (AccessDeniedException ade) {
			response = new ServiceResponse(false, "Unauthorized", null);
		}

		return ResponseEntity.status(HttpStatus.OK).body(response);
	}

	@Operation(
			summary = "Get the Capacities",
			description = "API to get the capacities by basicProjectConfigId")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Successfully fetched Capacity Data"),
				@ApiResponse(responseCode = "400", description = "Bad Request"),
				@ApiResponse(responseCode = "500", description = "Internal Server Error")
			})
	@GetMapping("/{basicProjectConfigId}")
	public ResponseEntity<ServiceResponse> getCapacities(
			@Parameter(
							description = "Basic Project Configuration Id",
							required = true,
							example = "5f8d04b3e1b2c12d4c8e4b56")
					@PathVariable
					String basicProjectConfigId) {
		ServiceResponse response = null;

		List<CapacityMaster> capacities = capacityMasterService.getCapacities(basicProjectConfigId);
		if (CollectionUtils.isNotEmpty(capacities)) {
			response = new ServiceResponse(true, "Capacity Data", capacities);
		} else {
			response = new ServiceResponse(false, "No data", null);
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}

	@Operation(
			summary = "Add ar update assignee for capacity",
			description = "API to add or update assignee for capacity")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Successfully added or updated assignee"),
				@ApiResponse(responseCode = "400", description = "Bad Request"),
				@ApiResponse(responseCode = "500", description = "Internal Server Error")
			})
	@PostMapping(value = "/assignee", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> saveOrUpdateAssignee(
			@Parameter(description = "Capacity Master Object", required = true) @Valid @RequestBody
					CapacityMaster capacityMaster) {
		policy.checkPermission(capacityMaster, "SAVE_UPDATE_CAPACITY");
		ServiceResponse response = new ServiceResponse(false, "Failed to add Capacity Data", null);
		try {
			capacityMaster = capacityMasterService.processCapacityData(capacityMaster);
			if (null != capacityMaster) {
				response = new ServiceResponse(true, "Successfully added Capacity Data", capacityMaster);
			}
		} catch (AccessDeniedException ade) {
			response = new ServiceResponse(false, "Unauthorized", null);
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}

	@Operation(
			summary = "Get suggestions for assignee roles",
			description = "API to get suggestions for assignee roles")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Successfully fetched Roles"),
				@ApiResponse(responseCode = "400", description = "Bad Request"),
				@ApiResponse(responseCode = "500", description = "Internal Server Error")
			})
	@GetMapping("/assignee/roles")
	public ResponseEntity<ServiceResponse> assigneeRolesSuggestion() {
		return ResponseEntity.status(HttpStatus.OK)
				.body(new ServiceResponse(true, "All Roles", Role.getAllRoles()));
	}

	@Operation(summary = "Save Happiness KPI Data", description = "API to save Happiness KPI Data")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Successfully saved Happiness KPI Data"),
				@ApiResponse(responseCode = "400", description = "Bad Request"),
				@ApiResponse(responseCode = "500", description = "Internal Server Error")
			})
	@PostMapping(
			value = "/jira/happiness",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> saveHappinessKPIData(
			@Parameter(description = "Happiness KPI Data Transfer Object", required = true)
					@Valid
					@RequestBody
					HappinessKpiDTO happinessKpiDTO) {
		return ResponseEntity.status(HttpStatus.OK)
				.body(happinessKpiService.saveHappinessKpiData(happinessKpiDTO));
	}
}
