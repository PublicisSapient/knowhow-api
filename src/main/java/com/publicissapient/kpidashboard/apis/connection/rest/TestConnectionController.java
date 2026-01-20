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

package com.publicissapient.kpidashboard.apis.connection.rest;

import javax.validation.constraints.NotNull;

import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.publicissapient.kpidashboard.apis.connection.service.TestConnectionService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.model.connection.Connection;
import com.publicissapient.kpidashboard.common.model.connection.ConnectionDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/testconnection")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Test Connection API", description = "APIs for Testing Connections")
public class TestConnectionController {

	private final TestConnectionService testConnectionService;

	public static final String SONAR_CONNECTION_MSG = "validating Sonar connections credentials";

	/**
	 * Validate JIRA connection
	 *
	 * @param connectionDTO the connection DTO
	 * @return the response entity
	 */
	@Operation(
			summary = "Validate JIRA Connection",
			description = "Validates the connection credentials for JIRA.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection validated successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid connection details provided"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error occurred while validating connection")
			})
	@PostMapping(
			path = "/jira",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasPermission(#connectionDTO,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> validateJiraConnection(
			@Parameter(description = "Connection details for JIRA", required = true) @NotNull @RequestBody
					ConnectionDTO connectionDTO) {
		log.info("validating JIRA connections credentials");
		final ModelMapper modelMapper = new ModelMapper();
		final Connection connection = modelMapper.map(connectionDTO, Connection.class);
		return ResponseEntity.status(HttpStatus.OK)
				.body(testConnectionService.validateConnection(connection, Constant.TOOL_JIRA));
	}

	/**
	 * Validate Sonar connection
	 *
	 * @param connectionDTO the connection DTO
	 * @return the response entity
	 */
	@Operation(
			summary = "Validate Sonar Connection",
			description = "Validates the connection credentials for Sonar.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection validated successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid connection details provided"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error occurred while validating connection")
			})
	@PostMapping(
			path = "/sonar",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE) // NOSONAR
	@PreAuthorize("hasPermission(#connectionDTO,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> validateSonarConnection(
			@Parameter(description = "Connection details for Sonar", required = true)
					@NotNull
					@RequestBody
					ConnectionDTO connectionDTO) {
		log.info(SONAR_CONNECTION_MSG);
		final ModelMapper modelMapper = new ModelMapper();
		final Connection connection = modelMapper.map(connectionDTO, Connection.class);
		return ResponseEntity.status(HttpStatus.OK)
				.body(testConnectionService.validateConnection(connection, Constant.TOOL_SONAR));
	}

	/**
	 * Validate teamcity connection
	 *
	 * @param connectionDTO the connection DTO
	 * @return the response entity
	 */
	@Operation(
			summary = "Validate TeamCity Connection",
			description = "Validates the connection credentials for TeamCity.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection validated successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid connection details provided"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error occurred while validating connection")
			})
	@PostMapping(
			path = "/teamcity",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE) // NOSONAR
	@PreAuthorize("hasPermission(#connectionDTO,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> validateTeamcityConnection(
			@Parameter(description = "Connection details for TeamCity", required = true)
					@NotNull
					@RequestBody
					ConnectionDTO connectionDTO) {
		log.info("validating Teamcity connections credentials");
		final ModelMapper modelMapper = new ModelMapper();
		final Connection connection = modelMapper.map(connectionDTO, Connection.class);
		return ResponseEntity.status(HttpStatus.OK)
				.body(testConnectionService.validateConnection(connection, Constant.TOOL_TEAMCITY));
	}

	@Operation(
			summary = "Validate Zephyr Connection",
			description = "Validates the connection credentials for Zephyr.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection validated successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid connection details provided"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error occurred while validating connection")
			})
	@PostMapping(
			path = "/zephyr",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE) // NOSONAR
	@PreAuthorize("hasPermission(#connectionDTO,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> validateZephyrConnection(
			@Parameter(description = "Connection details for Zephyr", required = true)
					@NotNull
					@RequestBody
					ConnectionDTO connectionDTO) {
		log.info("validating Zephyr connections credentials");
		final ModelMapper modelMapper = new ModelMapper();
		final Connection connection = modelMapper.map(connectionDTO, Connection.class);
		return ResponseEntity.status(HttpStatus.OK)
				.body(testConnectionService.validateConnection(connection, Constant.TOOL_ZEPHYR));
	}

	/**
	 * Validate bamboo connection
	 *
	 * @param connectionDTO the connection DTO
	 * @return the response entity
	 */
	@Operation(
			summary = "Validate Bamboo Connection",
			description = "Validates the connection credentials for Bamboo.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection validated successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid connection details provided"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error occurred while validating connection")
			})
	@PostMapping(
			path = "/bamboo",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE) // NOSONAR
	@PreAuthorize("hasPermission(#connectionDTO,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> validateBambooConnection(
			@Parameter(description = "Connection details for Bamboo", required = true)
					@NotNull
					@RequestBody
					ConnectionDTO connectionDTO) {
		log.info("validating Bamboo connections credentials");
		final ModelMapper modelMapper = new ModelMapper();
		final Connection connection = modelMapper.map(connectionDTO, Connection.class);
		return ResponseEntity.status(HttpStatus.OK)
				.body(testConnectionService.validateConnection(connection, Constant.TOOL_BAMBOO));
	}

	/**
	 * Validate Jenkins connection
	 *
	 * @param connectionDTO the connection DTO
	 * @return the response entity
	 */
	@Operation(
			summary = "Validate Jenkins Connection",
			description = "Validates the connection credentials for Jenkins.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection validated successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid connection details provided"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error occurred while validating connection")
			})
	@PostMapping(
			path = "/jenkins",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE) // NOSONAR
	@PreAuthorize("hasPermission(#connectionDTO,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> validateJenkinsConnection(
			@Parameter(description = "Connection details for Jenkins", required = true)
					@NotNull
					@RequestBody
					ConnectionDTO connectionDTO) {
		log.info("validating Jenkins connections credentials");
		final ModelMapper modelMapper = new ModelMapper();
		final Connection connection = modelMapper.map(connectionDTO, Connection.class);
		return ResponseEntity.status(HttpStatus.OK)
				.body(testConnectionService.validateConnection(connection, Constant.TOOL_JENKINS));
	}

	@Operation(
			description = "Validate Gitlab Connection",
			summary = "Validates the connection credentials for Gitlab.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection validated successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid connection details provided"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error occurred while validating connection")
			})
	@PostMapping("/gitlab")
	@PreAuthorize("hasPermission(#connectionDTO,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> validateGitlabConnection(
			@Parameter(description = "Connection details for Gitlab", required = true)
					@NotNull
					@RequestBody
					ConnectionDTO connectionDTO) {
		log.info("validating Gitlab connections credentials");
		final ModelMapper modelMapper = new ModelMapper();
		final Connection connection = modelMapper.map(connectionDTO, Connection.class);
		return ResponseEntity.status(HttpStatus.OK)
				.body(testConnectionService.validateConnection(connection, Constant.TOOL_GITLAB));
	}

	/**
	 * Validate Bitbucket connection
	 *
	 * @param connectionDTO the connection DTO
	 * @return the response entity
	 */
	@Operation(
			summary = "Validate Bitbucket Connection",
			description = "Validates the connection credentials for Bitbucket.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection validated successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid connection details provided"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error occurred while validating connection")
			})
	@PostMapping(
			path = "/bitbucket",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE) // NOSONAR
	@PreAuthorize("hasPermission(#connectionDTO,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> validateBitbucketConnection(
			@Parameter(description = "Connection details for Bitbucket", required = true)
					@NotNull
					@RequestBody
					ConnectionDTO connectionDTO) {
		log.info("validating Bitbucket connections credentials");
		final ModelMapper modelMapper = new ModelMapper();
		final Connection connection = modelMapper.map(connectionDTO, Connection.class);
		return ResponseEntity.status(HttpStatus.OK)
				.body(testConnectionService.validateConnection(connection, Constant.TOOL_BITBUCKET));
	}

	@Operation(
			summary = "Validate Azure Board Connection",
			description = "Validates the connection credentials for Azure Board.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection validated successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid connection details provided"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error occurred while validating connection")
			})
	@PostMapping("/azureboard")
	@PreAuthorize("hasPermission(#connectionDTO,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> validateAzureBoardeConnection(
			@Parameter(description = "Connection details for Azure Board", required = true)
					@NotNull
					@RequestBody
					ConnectionDTO connectionDTO) {
		log.info("validating azure board connections credentials");
		final ModelMapper modelMapper = new ModelMapper();
		final Connection connection = modelMapper.map(connectionDTO, Connection.class);
		return ResponseEntity.status(HttpStatus.OK)
				.body(testConnectionService.validateConnection(connection, Constant.TOOL_AZURE));
	}

	@Operation(
			summary = "Validate Azure Repo Connection",
			description = "Validates the connection credentials for Azure Repo.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection validated successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid connection details provided"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error occurred while validating connection")
			})
	@PostMapping("/azurerepo")
	@PreAuthorize("hasPermission(#connectionDTO,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> validateAzureRepoConnection(
			@Parameter(description = "Connection details for Azure Repo", required = true)
					@NotNull
					@RequestBody
					ConnectionDTO connectionDTO) {
		log.info("validating azure repo connections credentials");
		final ModelMapper modelMapper = new ModelMapper();
		final Connection connection = modelMapper.map(connectionDTO, Connection.class);
		return ResponseEntity.status(HttpStatus.OK)
				.body(testConnectionService.validateConnection(connection, Constant.TOOL_AZUREREPO));
	}

	@Operation(
			summary = "Validate Azure Pipeline Connection",
			description = "Validates the connection credentials for Azure Pipeline.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection validated successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid connection details provided"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error occurred while validating connection")
			})
	@PostMapping("/azurepipeline")
	@PreAuthorize("hasPermission(#connectionDTO,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> validateAzurePipelineConnection(
			@Parameter(description = "Connection details for Azure Pipeline", required = true)
					@NotNull
					@RequestBody
					ConnectionDTO connectionDTO) {
		log.info("validating azure pipeline connections credentials");
		final ModelMapper modelMapper = new ModelMapper();
		final Connection connection = modelMapper.map(connectionDTO, Connection.class);
		return ResponseEntity.status(HttpStatus.OK)
				.body(testConnectionService.validateConnection(connection, Constant.TOOL_AZUREPIPELINE));
	}

	@Operation(
			summary = "Validate GitHub Connection",
			description = "Validates the connection credentials for GitHub.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection validated successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid connection details provided"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error occurred while validating connection")
			})
	@PostMapping("/github")
	@PreAuthorize("hasPermission(#connectionDTO,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> validateGitHubConnection(
			@Parameter(description = "Connection details for GitHub", required = true)
					@NotNull
					@RequestBody
					ConnectionDTO connectionDTO) {
		log.info(SONAR_CONNECTION_MSG);
		final ModelMapper modelMapper = new ModelMapper();
		final Connection connection = modelMapper.map(connectionDTO, Connection.class);
		return ResponseEntity.status(HttpStatus.OK)
				.body(testConnectionService.validateConnection(connection, Constant.TOOL_GITHUB));
	}

	/**
	 * Validate ArgoCD connection
	 *
	 * @param connectionDTO the connection DTO
	 * @return the response entity
	 */
	@Operation(
			summary = "Validate ArgoCD Connection",
			description = "Validates the connection credentials for ArgoCD.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection validated successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid connection details provided"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error occurred while validating connection")
			})
	@PostMapping(
			path = "/argocd",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasPermission(#connectionDTO,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> validateArgoCDConnection(
			@Parameter(description = "Connection details for ArgoCD", required = true)
					@NotNull
					@RequestBody
					ConnectionDTO connectionDTO) {
		log.info("validating ArgoCD connections credentials");
		final ModelMapper modelMapper = new ModelMapper();
		final Connection connection = modelMapper.map(connectionDTO, Connection.class);
		return ResponseEntity.status(HttpStatus.OK)
				.body(testConnectionService.validateConnection(connection, Constant.TOOL_ARGOCD));
	}

	@Operation(
			summary = "Get Zephyr Cloud URL Details",
			description = "Retrieves the Zephyr Cloud URL details.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Zephyr Cloud URL details retrieved successfully"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error occurred while retrieving details")
			})
	@GetMapping(value = "/zephyrcloudurl", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getZephyrCloudUrl() {
		return ResponseEntity.status(HttpStatus.OK)
				.body(testConnectionService.getZephyrCloudUrlDetails());
	}

	@Operation(
			summary = "Validate Rally Connection",
			description = "Validates the connection credentials for Rally.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection validated successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid connection details provided"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error occurred while validating connection")
			})
	@PostMapping(
			path = "/rally",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	@PreAuthorize("hasPermission(#connectionDTO,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> validateRallyConnection(
			@Parameter(description = "Connection details for Rally", required = true)
					@NotNull
					@RequestBody
					ConnectionDTO connectionDTO) {
		log.info("validating Rally connections credentials");
		final ModelMapper modelMapper = new ModelMapper();
		final Connection connection = modelMapper.map(connectionDTO, Connection.class);
		return ResponseEntity.status(HttpStatus.OK)
				.body(testConnectionService.validateConnection(connection, Constant.TOOL_RALLY));
	}
}
