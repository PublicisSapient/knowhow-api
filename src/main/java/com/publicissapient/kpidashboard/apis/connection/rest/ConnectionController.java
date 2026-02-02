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

import javax.validation.Valid;

import org.apache.commons.lang3.StringUtils;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.connection.service.ConnectionService;
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

/**
 * Rest Controller for all connection requests.
 *
 * @author dilip
 * @author jagmongr
 */
@RestController
@RequestMapping("/connections")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Connection API", description = "APIs for Connection Management")
public class ConnectionController {

	private final ConnectionService connectionService;

	/**
	 * Fetch all connection data param type.
	 *
	 * @return the connection
	 * @param type type
	 */
	@Operation(
			summary = "Get Connections",
			description = "Retrieve all connections or filter by type.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Successfully retrieved connections"),
				@ApiResponse(responseCode = "400", description = "Invalid request parameters"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@GetMapping
	@PreAuthorize("hasPermission(#type,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> getAllConnection(
			@Parameter(description = "Type of connection to filter by", example = "JIRA")
					@RequestParam(name = "type", required = false)
					String type) {
		if (StringUtils.isEmpty(type)) {
			log.info("Fetching all connection");
			return ResponseEntity.status(HttpStatus.OK).body(connectionService.getAllConnection());

		} else {
			log.info("Fetching type@{}", type);
			return ResponseEntity.status(HttpStatus.OK).body(connectionService.getConnectionByType(type));
		}
	}

	/**
	 * save/add Connection details
	 *
	 * @param connectionDTO request object that is created in the database.
	 * @return responseEntity with data,message and status
	 */
	@Operation(
			summary = "Create Connection",
			description = "Create a new connection with the provided details.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection created successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid connection data"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@PostMapping
	@PreAuthorize("hasPermission(#connectionDTO,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> saveConnectionDetails(
			@Parameter(description = "Connection details to be created", required = true) @RequestBody
					ConnectionDTO connectionDTO) {

		final ModelMapper modelMapper = new ModelMapper();
		final Connection conn = modelMapper.map(connectionDTO, Connection.class);

		log.info("created and saved new connection");
		return ResponseEntity.status(HttpStatus.OK).body(connectionService.saveConnectionDetails(conn));
	}

	/**
	 * Modify/Update a connection by id.
	 *
	 * @param connectionDTO request object that replaces the connection data present at id.
	 * @return responseEntity with data,message and status
	 */
	@Operation(
			summary = "Update Connection",
			description = "Update an existing connection by its ID.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection updated successfully"),
				@ApiResponse(responseCode = "400", description = "Invalid connection data"),
				@ApiResponse(responseCode = "404", description = "Connection not found"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@PutMapping("/{id}")
	@PreAuthorize("hasPermission(#id,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> modifyConnectionById(
			@Parameter(
							description = "ID of the connection to be updated",
							required = true,
							example = "12345")
					@PathVariable
					String id,
			@Parameter(description = "Updated connection details", required = true) @Valid @RequestBody
					ConnectionDTO connectionDTO) {
		log.info("conn@{} updated", connectionDTO.getId());
		final ModelMapper modelMapper = new ModelMapper();
		final Connection conn = modelMapper.map(connectionDTO, Connection.class);
		return ResponseEntity.status(HttpStatus.OK).body(connectionService.updateConnection(id, conn));
	}

	/**
	 * delete a connection by id.
	 *
	 * @param id deleted the connection data present at id.
	 * @return responseEntity with data,message and status
	 */
	@Operation(
			summary = "Delete Connection",
			description = "Delete an existing connection by its ID.")
	@ApiResponses(
			value = {
				@ApiResponse(responseCode = "200", description = "Connection deleted successfully"),
				@ApiResponse(responseCode = "404", description = "Connection not found"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@DeleteMapping("/{id}")
	@PreAuthorize("hasPermission(#id,'CONNECTION_ACCESS')")
	public ResponseEntity<ServiceResponse> deleteConnection(
			@Parameter(
							description = "ID of the connection to be deleted",
							required = true,
							example = "12345")
					@PathVariable
					String id) {

		return ResponseEntity.status(HttpStatus.OK).body(connectionService.deleteConnection(id));
	}
}
