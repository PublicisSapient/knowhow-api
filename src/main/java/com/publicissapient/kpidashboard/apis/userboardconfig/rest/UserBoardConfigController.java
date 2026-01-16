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

package com.publicissapient.kpidashboard.apis.userboardconfig.rest;

import java.util.ArrayList;
import java.util.Optional;
import javax.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.common.service.ConfigDetailService;
import com.publicissapient.kpidashboard.apis.model.ConfigDetails;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.model.UserBoardDTO;
import com.publicissapient.kpidashboard.apis.userboardconfig.service.UserBoardConfigService;
import com.publicissapient.kpidashboard.common.model.userboardconfig.ConfigLevel;
import com.publicissapient.kpidashboard.common.model.userboardconfig.ProjectListRequested;
import com.publicissapient.kpidashboard.common.model.userboardconfig.UserBoardConfigDTO;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Rest controller for user board config
 *
 * @author narsingh9
 */
@RestController
@RequestMapping("/user-board-config")
@Tag(name = "User Board Config API", description = "APIs for managing user board configurations")
@RequiredArgsConstructor
public class UserBoardConfigController {

	private static final String NO_DATA_FOUND = "No data found";

	private final UserBoardConfigService userBoardConfigService;
	private final ConfigDetailService configDetailService;

	/**
	 * Api to get user based configurations
	 *
	 * @return response
	 */
	@Operation(summary = "Get user board configurations",
			description = "Fetches combined user board and general config details for specific projects.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved config",
					content = @Content(mediaType = "application/json")),
			@ApiResponse(responseCode = "400", description = "Invalid project list provided"),
			@ApiResponse(responseCode = "401", description = "User is not authenticated"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@PostMapping(
			value = "/getBoardConfig",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getUserBoardConfigurations(
			@Parameter(
					description = "List of requested project configurations",
					required = true,
					example = """
							{"basicProjectConfigIds": ["projConfigId1", "projConfigId2"]}
							"""
			)
			@Valid @RequestBody ProjectListRequested listOfRequestedProj) {
		UserBoardConfigDTO userBoardConfigDTO =
				userBoardConfigService.getBoardConfig(ConfigLevel.USER, listOfRequestedProj);
		if (userBoardConfigDTO == null) {
			return ResponseEntity.status(HttpStatus.OK)
					.body(new ServiceResponse(false, NO_DATA_FOUND, null));
		}

		ConfigDetails configDetails = configDetailService.getConfigDetails();

		// Create a UserBoardDTO to hold the combined data
		UserBoardDTO userBoardDTO = new UserBoardDTO();
		userBoardDTO.setUserBoardConfigDTO(userBoardConfigDTO);
		userBoardDTO.setConfigDetails(configDetails);

		ServiceResponse response =
				new ServiceResponse(true, "Project Config Fetched successfully", userBoardDTO);
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}

	/**
	 * Api to save user based config
	 *
	 * @param userBoardConfigDTO userBoardConfigDTO
	 * @return response
	 */
	@Operation(summary = "Save user config", description = "Saves or updates personal board configuration for the current user.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Config saved successfully",
					content = @Content(mediaType = "application/json")),
			@ApiResponse(responseCode = "400", description = "Invalid configuration data"),
			@ApiResponse(responseCode = "403", description = "Forbidden: Insufficient permissions"),
			@ApiResponse(responseCode = "500", description = "Error saving configuration")
	})
	@PostMapping(
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> saveUserBoardConfig(
			@Parameter(
					description = "User board configuration data to be saved",
					required = true)
			@Valid @RequestBody UserBoardConfigDTO userBoardConfigDTO) {
		ServiceResponse response =
				userBoardConfigService.saveBoardConfig(userBoardConfigDTO, ConfigLevel.USER, null);
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}

	/**
	 * Api to get project board config
	 *
	 * @return response
	 */
	@Operation(summary = "Get project board configuration for admin",
			description = "Fetches the board configuration for a specific project. Admin access required.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved project config",
					content = @Content(mediaType = "application/json")),
			@ApiResponse(responseCode = "400", description = "Invalid project configuration ID"),
			@ApiResponse(responseCode = "403", description = "Forbidden: Admin access required"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@GetMapping(value = "/{basicProjectConfigId}", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getUserBoardConfigurationAdmin(
			@Parameter(
					description = "Basic Project Configuration ID",
					required = true,
					example = "projConfigId1")
			@PathVariable String basicProjectConfigId) {
		ProjectListRequested projectListRequested = new ProjectListRequested();
		Optional.ofNullable(projectListRequested.getBasicProjectConfigIds())
				.orElseGet(
						() -> {
							projectListRequested.setBasicProjectConfigIds(new ArrayList<>());
							return projectListRequested.getBasicProjectConfigIds();
						})
				.add(basicProjectConfigId);
		UserBoardConfigDTO userBoardConfigDTO =
				userBoardConfigService.getBoardConfig(ConfigLevel.PROJECT, projectListRequested);
		ServiceResponse response = new ServiceResponse(false, NO_DATA_FOUND, null);
		if (null != userBoardConfigDTO) {
			response =
					new ServiceResponse(true, "Project Config Fetched successfully", userBoardConfigDTO);
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}

	/**
	 * Api to save project board config
	 *
	 * @param userBoardConfigDTO userBoardConfigDTO
	 * @return response
	 */
	@Operation(summary = "Save project board configuration for admin",
			description = "Saves or updates the board configuration for a specific project. Admin access required.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Project config saved successfully",
					content = @Content(mediaType = "application/json")),
			@ApiResponse(responseCode = "400", description = "Invalid configuration data"),
			@ApiResponse(responseCode = "403", description = "Forbidden: Admin access required"),
			@ApiResponse(responseCode = "500", description = "Error saving project configuration")
	})
	@PostMapping(
			value = "/saveAdmin/{basicProjectConfigId}",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> saveUserBoardConfigAdmin(
			@Parameter(
					description = "User board configuration data to be saved for the project",
					required = true)
			@Valid @RequestBody UserBoardConfigDTO userBoardConfigDTO,
			@Parameter(
					description = "Basic Project Configuration ID",
					required = true,
					example = "projConfigId1")
			@PathVariable String basicProjectConfigId) {
		ServiceResponse response =
				userBoardConfigService.saveBoardConfig(
						userBoardConfigDTO, ConfigLevel.PROJECT, basicProjectConfigId);
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}
}
