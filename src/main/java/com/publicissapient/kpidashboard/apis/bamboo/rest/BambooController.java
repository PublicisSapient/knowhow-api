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

package com.publicissapient.kpidashboard.apis.bamboo.rest;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.bamboo.model.BambooBranchesResponseDTO;
import com.publicissapient.kpidashboard.apis.bamboo.model.BambooDeploymentProjectsResponseDTO;
import com.publicissapient.kpidashboard.apis.bamboo.model.BambooPlansResponseDTO;
import com.publicissapient.kpidashboard.apis.bamboo.service.BambooToolConfigServiceImpl;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Bamboo Controller", description = "APIs for Bamboo Tool Configurations")
public class BambooController {

	private final BambooToolConfigServiceImpl bambooToolConfigService;

	/**
	 * @param connectionId the bamboo server connection details
	 * @return @{@code ServiceResponse}
	 */
	@Operation(
			summary = "Get Bamboo Projects and Plan Keys",
			description =
					"Fetches the list of Bamboo Projects and their associated Plan Keys for the specified connection ID.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved Bamboo Projects and Plan Keys"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@GetMapping(value = "/bamboo/plans/{connectionId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getBambooProjectsAndPlanKeys(
			@Parameter(
							description = "Bamboo Connection ID",
							required = true,
							example = "64b0c7f5e1b2c3d4e5f67890")
					@PathVariable
					String connectionId) {
		ServiceResponse response;
		List<BambooPlansResponseDTO> projectKeyList =
				bambooToolConfigService.getProjectsAndPlanKeyList(connectionId);
		if (CollectionUtils.isEmpty(projectKeyList)) {
			response = new ServiceResponse(false, "No plans details found", null);
		} else {
			response = new ServiceResponse(true, "FETCHED_SUCCESSFULLY", projectKeyList);
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}

	/**
	 * @param connectionId the bamboo server connection details
	 * @param jobNameKey the bamboo server jobNameKey
	 * @return @{@code ServiceResponse}
	 */
	@Operation(
			summary = "Get Bamboo Branches Name and Keys",
			description =
					"Fetches the list of Bamboo Branches Names and their associated Keys for the specified connection ID and Job Name Key.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved Bamboo Branches Names and Keys"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@GetMapping(
			value = "/bamboo/branches/{connectionId}/{jobNameKey}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getBambooBranchesNameAndKeys(
			@Parameter(
							description = "Bamboo Connection ID",
							required = true,
							example = "64b0c7f5e1b2c3d4e5f67890")
					@PathVariable
					String connectionId,
			@Parameter(description = "Bamboo Job Name Key", required = true, example = "PROJECT-PLAN")
					@PathVariable
					String jobNameKey) {
		ServiceResponse response;
		List<BambooBranchesResponseDTO> projectKeyList =
				bambooToolConfigService.getBambooBranchesNameAndKeys(connectionId, jobNameKey);
		if (CollectionUtils.isEmpty(projectKeyList)) {
			response = new ServiceResponse(false, "No branches details found", null);
		} else {
			response = new ServiceResponse(true, "Fetched successfully", projectKeyList);
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}

	/**
	 * @param connectionId the bamboo server connection details
	 * @return @{@code ServiceResponse}
	 */
	@Operation(
			summary = "Get Bamboo Deployment Projects",
			description =
					"Fetches the list of Bamboo Deployment Projects for the specified connection ID.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved Bamboo Deployment Projects"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@GetMapping(value = "/bamboo/deploy/{connectionId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getBambooDeploymentProject(
			@Parameter(
							description = "Bamboo Connection ID",
							required = true,
							example = "64b0c7f5e1b2c3d4e5f67890")
					@PathVariable
					String connectionId) {
		ServiceResponse response;
		List<BambooDeploymentProjectsResponseDTO> projectKeyList =
				bambooToolConfigService.getDeploymentProjectList(connectionId);
		if (CollectionUtils.isEmpty(projectKeyList)) {
			response = new ServiceResponse(false, "No deployment project found", null);
		} else {
			response = new ServiceResponse(true, "FETCHED_SUCCESSFULLY", projectKeyList);
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}
}
