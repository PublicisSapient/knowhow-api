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

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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

/**
 * @author narsingh9
 */
@RestController
@RequestMapping("/capacity")
@RequiredArgsConstructor
@Tag(name = "Capacity Master Controller", description = "APIs for Capacity Master Management")
public class CapacityMasterController {

	private final  CapacityMasterService capacityMasterService;

	private final ContextAwarePolicyEnforcement policy;

	private final HappinessKpiCapacityImpl happinessKpiService;

	/**
	 * This api saves capacity data.
	 *
	 * @param capacityMaster data to be saved
	 * @return service response entity
	 */
	@PostMapping(
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> addCapacity(@RequestBody CapacityMaster capacityMaster) {
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

	@GetMapping("/{basicProjectConfigId}")
	public ResponseEntity<ServiceResponse> getCapacities(@PathVariable String basicProjectConfigId) {
		ServiceResponse response = null;

		List<CapacityMaster> capacities = capacityMasterService.getCapacities(basicProjectConfigId);
		if (CollectionUtils.isNotEmpty(capacities)) {
			response = new ServiceResponse(true, "Capacity Data", capacities);
		} else {
			response = new ServiceResponse(false, "No data", null);
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}

	@PostMapping(value = "/assignee", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> saveOrUpdateAssignee(
			@Valid @RequestBody CapacityMaster capacityMaster) {
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

	@GetMapping("/assignee/roles")
	public ResponseEntity<ServiceResponse> assigneeRolesSuggestion() {
		return ResponseEntity.status(HttpStatus.OK)
				.body(new ServiceResponse(true, "All Roles", Role.getAllRoles()));
	}

	@PostMapping(
			value = "/jira/happiness",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> saveHappinessKPIData(
			@Valid @RequestBody HappinessKpiDTO happinessKpiDTO) {
		return ResponseEntity.status(HttpStatus.OK)
				.body(happinessKpiService.saveHappinessKpiData(happinessKpiDTO));
	}
}
