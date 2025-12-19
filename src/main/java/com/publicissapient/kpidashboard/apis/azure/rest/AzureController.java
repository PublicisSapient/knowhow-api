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

package com.publicissapient.kpidashboard.apis.azure.rest;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.azure.model.AzurePipelinesResponseDTO;
import com.publicissapient.kpidashboard.apis.azure.model.AzureTeamsDTO;
import com.publicissapient.kpidashboard.apis.azure.service.AzureToolConfigServiceImpl;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Azure Controller", description = "APIs for Azure Tool Configurations")
public class AzureController {

	private final AzureToolConfigServiceImpl azureToolConfigService;

	/**
	 * @param connectionId the Azure server connection details
	 * @return @{@code ServiceResponse}
	 */
	@Operation(
			summary = "Get Azure Pipelines",
			description = "Fetches the list of Azure Pipelines associated with the specified connection ID and version."
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved Azure Pipelines"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@GetMapping(
			value = "/azure/pipeline/{connectionId}/{version}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getAzurePipelineNameAndDefinitionIdList(
			@Parameter(description = "Azure Connection ID", required = true, example = "64b0c7f5e1b2c3d4e5f67890")
			@PathVariable String connectionId,
			@Parameter(description = "API Version", required = true, example = "6.0")
			@PathVariable String version) {
		ServiceResponse response;
		List<AzurePipelinesResponseDTO> pipelinesResponseList =
				azureToolConfigService.getAzurePipelineNameAndDefinitionIdList(connectionId, version);
		if (CollectionUtils.isEmpty(pipelinesResponseList)) {
			response = new ServiceResponse(false, "No Pipelines details found", null);
		} else {
			response = new ServiceResponse(true, "Fetched Pipelines Successfully", pipelinesResponseList);
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}

	/**
	 * @param connectionId the Azure connection details
	 * @return @{@code ServiceResponse}
	 */
	@Operation(
			summary = "Get Azure Release Pipelines",
			description = "Fetches the list of Azure Release Pipelines associated with the specified connection ID.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved Azure Release Pipelines"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@GetMapping(
			value = "/azure/release/{connectionId}/6.0",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getAzureReleaseNameAndDefinitionIdList(
			@Parameter(description = "Azure Connection ID", required = true, example = "64b0c7f5e1b2c3d4e5f67890")
			@PathVariable String connectionId) {
		ServiceResponse response;
		List<AzurePipelinesResponseDTO> releasesResponseList =
				azureToolConfigService.getAzureReleaseNameAndDefinitionIdList(connectionId);
		if (CollectionUtils.isEmpty(releasesResponseList)) {
			response = new ServiceResponse(false, "No Release Pipeline details found", null);
		} else {
			response =
					new ServiceResponse(
							true, "Fetched Release Pipeline details Successfully", releasesResponseList);
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}

	@Operation(
			summary = "Get Azure Teams",
			description = "Fetches the list of Azure Teams associated with the specified connection ID.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved Azure Teams"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@GetMapping(value = "/azure/teams/{connectionId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getAzureTeams(
			@Parameter(description = "Azure Connection ID", required = true, example = "64b0c7f5e1b2c3d4e5f67890")
			@PathVariable String connectionId) {
		ServiceResponse response;
		List<AzureTeamsDTO> teamsResponseList = azureToolConfigService.getAzureTeamsList(connectionId);
		if (CollectionUtils.isEmpty(teamsResponseList)) {
			response = new ServiceResponse(false, "No teams found", null);
		} else {
			response = new ServiceResponse(true, "Fetched teams details Successfully", teamsResponseList);
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}
}
