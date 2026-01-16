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

package com.publicissapient.kpidashboard.apis.rbac.accessrequests.rest;

import javax.validation.Valid;

import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.abac.AccessRequestListener;
import com.publicissapient.kpidashboard.apis.abac.GrantAccessListener;
import com.publicissapient.kpidashboard.apis.abac.ProjectAccessManager;
import com.publicissapient.kpidashboard.apis.abac.RejectAccessListener;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.rbac.accessrequests.service.AccessRequestsHelperService;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.common.model.rbac.AccessRequest;
import com.publicissapient.kpidashboard.common.model.rbac.AccessRequestDTO;
import com.publicissapient.kpidashboard.common.model.rbac.AccessRequestDecision;
import com.publicissapient.kpidashboard.common.model.rbac.UserInfo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Access Requests Controller for all roles requests.
 *
 * @author anamital
 */
@RestController
@RequestMapping("/accessrequests")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Access Requests Controller", description = "APIs for Access Requests Management")
public class AccessRequestsController {

	/** Instantiates the AccessRequestsHelperService */
	private final AccessRequestsHelperService accessRequestsHelperService;

	private final ProjectAccessManager projectAccessManager;

	/**
	 * Gets all access requests data.
	 *
	 * @return responseEntity with data,message and status
	 */
	@Operation(
			summary = "Get all access requests",
			description =
					"Fetches all access requests data. Pre-authorization is required for this action.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved all access requests",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(responseCode = "403", description = "Access denied"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@GetMapping
	@PreAuthorize("hasPermission(null, 'GET_ACCESS_REQUESTS')")
	public ResponseEntity<ServiceResponse> getAllAccessRequests() {
		log.info("Getting all requests");
		return ResponseEntity.status(HttpStatus.OK)
				.body(accessRequestsHelperService.getAllAccessRequests());
	}

	/**
	 * Gets access request data at id.
	 *
	 * @param id the id of access request data
	 * @return responseEntity with data,message and status
	 */
	@Operation(
			summary = "Get access request by ID",
			description =
					"Fetches access request data for a specific ID. Pre-authorization is required for this action.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved access request",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(responseCode = "403", description = "Access denied"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@GetMapping(value = "/{id}")
	@PreAuthorize("hasPermission(null,'GET_ACCESS_REQUEST')")
	public ResponseEntity<ServiceResponse> getAccessRequestById(
			@Parameter(description = "The ID of the access request to retrieve", example = "12345")
					@PathVariable("id")
					String id) {
		log.info("Getting request@{}", id);
		return ResponseEntity.status(HttpStatus.OK)
				.body(accessRequestsHelperService.getAccessRequestById(id));
	}

	/**
	 * Gets all access requests data under a username.
	 *
	 * @param username the username of access request data
	 * @return responseEntity with data,message and status
	 */
	@Operation(
			summary = "Get all access requests under a username",
			description =
					"Fetches all access requests data associated with the specified username. "
							+ "Pre-authorization is required for this action.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved access requests for the user",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(responseCode = "403", description = "Access denied"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@GetMapping(value = "/user/{username}")
	@PreAuthorize("hasPermission(#username,'GET_ACCESS_REQUESTS_OF_USER')")
	public ResponseEntity<ServiceResponse> getAccessRequestByUsername(
			@Parameter(
							description = "The username whose access requests are to be retrieved",
							example = "john_doe")
					@PathVariable("username")
					String username) {
		log.info("Getting all requests under user {}", username);
		return ResponseEntity.status(HttpStatus.OK)
				.body(accessRequestsHelperService.getAccessRequestByUsername(username));
	}

	/**
	 * Gets all access requests data with a current status.
	 *
	 * @param status the status of access request data
	 * @return responseEntity with data,message and status
	 */
	@Operation(
			summary = "Get all access requests with a specific status",
			description =
					"Fetches all access requests data that match the specified status. "
							+ "Pre-authorization is required for this action.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved access requests with the specified status",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(responseCode = "403", description = "Access denied"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@GetMapping(value = "/status/{status}")
	@PreAuthorize("hasPermission(#status,'ACCESS_REQUEST_STATUS')")
	public ResponseEntity<ServiceResponse> getAccessRequestByStatus(
			@Parameter(description = "The status to filter the access requests", example = "PENDING")
					@PathVariable("status")
					String status) {
		log.info("Getting all requests with current status {}", status);
		return ResponseEntity.status(HttpStatus.OK)
				.body(accessRequestsHelperService.getAccessRequestByStatus(status));
	}

	/**
	 * Modify an access request data by id
	 *
	 * @param id access request id
	 * @param accessRequestDecision decision data
	 * @return updated access request
	 */
	@Operation(
			summary = "Modify an access request by ID",
			description =
					"Updates the status of an access request based on the provided ID and decision data."
							+ "Pre-authorization is required for this action.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully modified the access request",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(responseCode = "400", description = "Invalid request payload"),
				@ApiResponse(responseCode = "403", description = "Access denied"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@PutMapping(value = "/{id}")
	@PreAuthorize("hasPermission(null , 'GRANT_ACCESS')")
	public ResponseEntity<ServiceResponse> modifyAccessRequestById(
			@Parameter(description = "The ID of the access request to modify", example = "12345")
					@PathVariable("id")
					String id,
			@Parameter(
							description = "The decision data for the access request",
							example =
									"{\"status\": \"APPROVED\", \"message\": \"Approved for access.\", \"role\": \"ADMIN\","
											+ " \"userName\": \"john_doe\"}")
					@RequestBody
					AccessRequestDecision accessRequestDecision) {

		ServiceResponse[] serviceResponse = new ServiceResponse[1];

		if (Constant.ACCESS_REQUEST_STATUS_APPROVED.equalsIgnoreCase(
				accessRequestDecision.getStatus())) {
			log.info("Approve access {}", id);

			projectAccessManager.grantAccess(
					id,
					accessRequestDecision,
					new GrantAccessListener() {
						@Override
						public void onSuccess(UserInfo userInfo) {
							serviceResponse[0] = new ServiceResponse(true, "Granted", null);
						}

						@Override
						public void onFailure(AccessRequest accessRequest, String message) {
							serviceResponse[0] = new ServiceResponse(false, message, null);
						}
					});
		} else if (Constant.ACCESS_REQUEST_STATUS_REJECTED.equalsIgnoreCase(
				accessRequestDecision.getStatus())) {
			log.info("Reject access {}", id);
			projectAccessManager.rejectAccessRequest(
					id,
					accessRequestDecision.getMessage(),
					new RejectAccessListener() {
						@Override
						public void onSuccess(AccessRequest accessRequest) {
							serviceResponse[0] = new ServiceResponse(true, "Rejected Successfully", null);
						}

						@Override
						public void onFailure(AccessRequest accessRequest, String message) {
							serviceResponse[0] = new ServiceResponse(false, message, null);
						}
					});
		}

		return ResponseEntity.status(HttpStatus.OK).body(serviceResponse[0]);
	}

	/**
	 * Create access requests data.
	 *
	 * @param accessRequestDTO the access request dto
	 * @return responseEntity with data,message and status
	 */
	@Operation(
			summary = "Create a new access request",
			description =
					"Creates a new access request with the provided details. "
							+ "Pre-authorization is required for this action.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Access request created successfully",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(responseCode = "400", description = "Invalid request payload"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@PostMapping()
	@PreAuthorize("hasPermission(#accessRequestsDataDTO,'RAISE_ACCESS_REQUEST')")
	public ResponseEntity<ServiceResponse> createAccessRequests(
			@Parameter(
							description = "The access request details to be created",
							required = true,
							schema = @Schema(implementation = AccessRequestDTO.class),
							example =
									"{\"id\": \"507f1f77bcf86cd799439011\", \"username\": \"john_doe\", \"status\": \"PENDING\","
											+ " \"reviewComments\": \"Needs admin approval\", \"role\": \"ADMIN\", \"accessNode\": "
											+ "{\"accessLevel\": \"project\", \"accessItems\": { \"itemId\": \"1234\", \"itemName\": \"ABC\"}}, "
											+ "\"deleted\": false, \"createdDate\": \"2023-12-01T00:00:00Z\","
											+ " \"lastModifiedDate\": \"2023-12-01T00:00:00Z\"}")
					@Valid
					@RequestBody
					AccessRequestDTO accessRequestDTO) {
		ModelMapper modelMapper = new ModelMapper();
		AccessRequest accessRequestsData = modelMapper.map(accessRequestDTO, AccessRequest.class);
		log.info("creating new request");
		final ServiceResponse[] serviceResponse = {null};
		projectAccessManager.createAccessRequest(
				accessRequestsData,
				new AccessRequestListener() {

					@Override
					public void onSuccess(AccessRequest accessRequest) {
						String msg =
								accessRequest.getStatus().equals(Constant.ACCESS_REQUEST_STATUS_APPROVED)
										? "Request has been auto-approved. Please login again to start using KnowHOW."
										: "Request submitted.";
						serviceResponse[0] = new ServiceResponse(true, msg, accessRequest);
					}

					@Override
					public void onFailure(String message) {

						serviceResponse[0] = new ServiceResponse(false, message, null);
					}
				});

		return ResponseEntity.status(HttpStatus.OK).body(serviceResponse[0]);
	}

	/**
	 * Gets access request data at id.
	 *
	 * @param id id
	 * @return responseEntity with data,message and status
	 */
	@Operation(
			summary = "Delete an access request by ID",
			description = "Deletes an access request based on the provided ID.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Access request deleted successfully",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(responseCode = "404", description = "Access request not found"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@DeleteMapping("/{id}")
	public ResponseEntity<ServiceResponse> deleteAccessRequestById(
			@Parameter(description = "The ID of the access request to delete", example = "12345")
					@PathVariable("id")
					String id) {
		log.info("request received for deleting access request with id @{}", id);

		id = CommonUtils.handleCrossScriptingTaintedValue(id);
		ServiceResponse response = null;

		if (projectAccessManager.deleteAccessRequestById(id)) {

			response = new ServiceResponse(true, "Sucessfully deleted.", id);

		} else {
			response =
					new ServiceResponse(
							false, "Either id is wrong or you are not authorized to delete.", null);
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}

	/**
	 * Gets access requests count data with a pending status.
	 *
	 * @param status status
	 * @return responseEntity with data,message and status
	 */
	@Operation(
			summary = "Get notification count by status",
			description = "Retrieves the count of access requests with a specific status.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved count",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(responseCode = "400", description = "Invalid status provided"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@GetMapping(
			value = "/{status}/notification",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getNotificationByStatus(
			@Parameter(description = "The status to filter the access requests", example = "PENDING")
					@PathVariable("status")
					String status) {
		log.info("Getting requests count with current status {}", status);
		return ResponseEntity.status(HttpStatus.OK)
				.body(accessRequestsHelperService.getNotificationByStatus(status, false));
	}

	/**
	 * Gets access requests count data with a pending status.
	 *
	 * @param status status
	 * @return responseEntity with data,message and status
	 */
	@Operation(
			summary = "Get notification count by status for central",
			description =
					"Retrieves the count of access requests with a specific status, considering central authentication.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved count",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(responseCode = "400", description = "Invalid status provided"),
				@ApiResponse(responseCode = "500", description = "Internal server error")
			})
	@GetMapping(
			value = "/{status}/notification/central",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getNotificationByStatusForCentral(
			@Parameter(
							description =
									"The status to filter the access requests, with central authentication considered",
							example = "PENDING")
					@PathVariable("status")
					String status) {
		log.info("Getting requests count with current status {}", status);
		return ResponseEntity.status(HttpStatus.OK)
				.body(accessRequestsHelperService.getNotificationByStatus(status, true));
	}
}
