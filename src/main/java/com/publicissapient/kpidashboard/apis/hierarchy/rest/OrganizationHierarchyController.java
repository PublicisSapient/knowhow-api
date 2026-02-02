/*
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.hierarchy.rest;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.hierarchy.dto.CreateHierarchyRequest;
import com.publicissapient.kpidashboard.apis.hierarchy.dto.UpdateHierarchyRequest;
import com.publicissapient.kpidashboard.apis.hierarchy.service.HierarchyOptionService;
import com.publicissapient.kpidashboard.apis.hierarchy.service.OrganizationHierarchyService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/hierarchy")
@RequiredArgsConstructor
@Tag(
		name = "Organization Hierarchy API",
		description = "APIs for Organization Hierarchy Management")
public class OrganizationHierarchyController {

	private final OrganizationHierarchyService organizationHierarchyService;

	private final HierarchyOptionService hierarchyOptionService;

	@Operation(
			summary = "Get Organization Hierarchies",
			description = "Retrieve all organization hierarchies.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved organization hierarchies"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@GetMapping
	public ResponseEntity<ServiceResponse> getHierarchyLevel() {

		List<OrganizationHierarchy> organizationHierarchies = organizationHierarchyService.findAll();

		if (CollectionUtils.isNotEmpty(organizationHierarchies)) {
			return ResponseEntity.status(HttpStatus.OK)
					.body(
							new ServiceResponse(
									true, "Fetched organization Hierarchies Successfully.", organizationHierarchies));
		} else {
			return ResponseEntity.status(HttpStatus.OK)
					.body(
							new ServiceResponse(
									false,
									"Organization hierarchy is not set up. Please configure it to proceed.",
									null));
		}
	}

	@Operation(
			summary = "Add Hierarchy Option",
			description = "Add a new hierarchy option under the specified parent hierarchy.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Hierarchy option added successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid input provided"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@PostMapping({"", "/{parentid}"})
	public ServiceResponse addHierarchyOption(
			@Parameter(description = "Parent Hierarchy ID", example = "60d21b4667d0d8992e610c85")
					@PathVariable(required = false)
					String parentid,
			@Parameter(description = "Hierarchy Creation Request Payload", required = true)
					@RequestBody
					@Valid
					CreateHierarchyRequest createHierarchyRequest) {
		return hierarchyOptionService.addHierarchyOption(createHierarchyRequest, parentid);
	}

	@Operation(
			summary = "Update Organization Hierarchy Name",
			description = "Update the display name of an existing organization hierarchy.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Hierarchy name updated successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid input provided"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@PutMapping("/{id}")
	public ServiceResponse updateHierarchy(
			@Parameter(description = "Hierarchy ID", example = "60d21b4667d0d8992e610c85") @PathVariable
					String id,
			@Parameter(description = "Hierarchy Update Request Payload", required = true)
					@Valid
					@RequestBody
					UpdateHierarchyRequest request) {
		return organizationHierarchyService.updateName(request.getDisplayName(), id);
	}
}
