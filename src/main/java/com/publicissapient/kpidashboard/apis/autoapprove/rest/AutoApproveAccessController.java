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

package com.publicissapient.kpidashboard.apis.autoapprove.rest;

import java.util.Collections;
import java.util.List;
import javax.validation.Valid;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.autoapprove.service.AutoApproveAccessService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.model.rbac.AutoApproveAccessConfig;
import com.publicissapient.kpidashboard.common.model.rbac.AutoApproveAccessConfigDTO;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/autoapprove")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Auto Approve Access Controller", description = "APIs for Auto Approve Access Management")
public class AutoApproveAccessController {

	private final AutoApproveAccessService autoApproveService;

	@Operation(
			summary = "Save Auto Approve Roles",
			description = "Saves new auto approve access configuration. Pre-authorization is required to enable auto approve."
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully saved auto approve roles"),
			@ApiResponse(responseCode = "400", description = "Invalid input data"),
			@ApiResponse(responseCode = "403", description = "Forbidden access (user does not have permission to enable auto approve)"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@PostMapping
	@PreAuthorize("hasPermission(null, 'ENABLE_AUTO_APPROVE')")
	public ResponseEntity<ServiceResponse> saveAutoApproveRoles(
			@Parameter(description = "Auto Approve Access Configuration DTO", required = true,
			example = """
					{
					  "id": {
					    "timestamp": 1725342562222,
					    "date": "2025-12-19T16:27:19.316Z"
					  },
					  "enableAutoApprove": "string",
					  "roles": [
					    {
					      "id": {
					        "timestamp": 172534256474,
					        "date": "2025-12-19T16:27:19.316Z"
					      },
					      "roleName": "admin",
					      "roleDescription": "admin role",
					      "createdDate": "2025-12-19T16:27:19.316Z",
					      "lastModifiedDate": "2025-12-19T16:27:19.316Z",
					      "isDeleted": "false",
					      "permissions": [
					        {
					          "id": {
					            "timestamp": 172424255252,
					            "date": "2025-12-19T16:27:19.316Z"
					          },
					          "permissionName": "admin_permission",
					          "operationName": "auto_approve",
					          "resourceName": "project",
					          "resourceId": {
					            "timestamp": 172534256261,
					            "date": "2025-12-19T16:27:19.316Z"
					          },
					          "createdDate": "2025-12-19T16:27:19.316Z",
					          "lastModifiedDate": "2025-12-19T16:27:19.316Z",
					          "isDeleted": "false"
					        }
					      ],
					      "displayName": "ABCD"
					    }
					  ]
					}
					""")
			@Valid @RequestBody AutoApproveAccessConfigDTO autoAcessDTO) {
		final ModelMapper modelMapper = new ModelMapper();
		final AutoApproveAccessConfig autoApproveRole =
				modelMapper.map(autoAcessDTO, AutoApproveAccessConfig.class);
		AutoApproveAccessConfig approvalConfig =
				autoApproveService.saveAutoApproveConfig(autoApproveRole);
		log.info("saved Auto Approve Roles");
		return ResponseEntity.status(HttpStatus.OK)
				.body(
						new ServiceResponse(
								true, "Added new  auto approve role",
								Collections.singletonList(approvalConfig)));
	}

	@Operation(
			summary = "Get Auto Approve Configuration",
			description = "Fetches the current auto approve access configuration. Pre-authorization is required to enable auto approve."
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved auto approve configuration"),
			@ApiResponse(responseCode = "403", description = "Forbidden access (user does not have permission to enable auto approve)"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@GetMapping
	@PreAuthorize("hasPermission(null, 'ENABLE_AUTO_APPROVE')")
	public ResponseEntity<ServiceResponse> getAutoApproveConfig() {
		log.info("Getting all requests");
		AutoApproveAccessConfig autoAccessApprovalData = autoApproveService.getAutoApproveConfig();
		if (autoAccessApprovalData == null) {
			log.info("No roles found for auto approval in db");
			return ResponseEntity.status(HttpStatus.OK)
					.body(
							new ServiceResponse(
									false, "auto approval not configured",
									Collections.singletonList(autoAccessApprovalData)));
		}
		log.info("Fetched roles for auto access successfully");
		return ResponseEntity.status(HttpStatus.OK)
				.body(
						new ServiceResponse(
								true, "Found all roles for auto approval",
								List.of(autoAccessApprovalData)));
	}

	@Operation(
			summary = "Modify Auto Approve Configuration by ID",
			description = "Modifies the auto approve access configuration identified by the given ID." +
					"Pre-authorization is required to enable auto approve."
	)
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully modified auto approve configuration"),
			@ApiResponse(responseCode = "400", description = "Invalid ID supplied or bad request"),
			@ApiResponse(responseCode = "403", description = "Forbidden access (user does not have permission to enable auto approve)"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@PutMapping("/{id}")
	@PreAuthorize("hasPermission(null, 'ENABLE_AUTO_APPROVE')")
	public ResponseEntity<ServiceResponse> modifyAutoApprovConfigById(
			@Parameter(description = "Auto Approve Access Configuration ID", required = true, example = "64b0c7f5e1b2c3d4e5f67890")
			@PathVariable("id") String id,
			@Parameter(description = "Auto Approve Access Configuration DTO", required = true,
			example = """
					{
					  "id": {
					    "timestamp": 1725342562222,
					    "date": "2025-12-19T16:27:19.316Z"
					  },
					  "enableAutoApprove": "string",
					  "roles": [
					    {
					      "id": {
					        "timestamp": 172534256474,
					        "date": "2025-12-19T16:27:19.316Z"
					      },
					      "roleName": "admin",
					      "roleDescription": "admin role",
					      "createdDate": "2025-12-19T16:27:19.316Z",
					      "lastModifiedDate": "2025-12-19T16:27:19.316Z",
					      "isDeleted": "false",
					      "permissions": [
					        {
					          "id": {
					            "timestamp": 172424255252,
					            "date": "2025-12-19T16:27:19.316Z"
					          },
					          "permissionName": "admin_permission",
					          "operationName": "auto_approve",
					          "resourceName": "project",
					          "resourceId": {
					            "timestamp": 172534256261,
					            "date": "2025-12-19T16:27:19.316Z"
					          },
					          "createdDate": "2025-12-19T16:27:19.316Z",
					          "lastModifiedDate": "2025-12-19T16:27:19.316Z",
					          "isDeleted": "false"
					        }
					      ],
					      "displayName": "ABCD"
					    }
					  ]
					}
					""")
			@Valid @RequestBody AutoApproveAccessConfigDTO autoAcessDTO) {
		ModelMapper modelMapper = new ModelMapper();
		AutoApproveAccessConfig autoApproveRole =
				modelMapper.map(autoAcessDTO, AutoApproveAccessConfig.class);

		if (!ObjectId.isValid(id)) {
			log.info("Id not valid");
			return ResponseEntity.status(HttpStatus.OK)
					.body(
							new ServiceResponse(
									false, "access_request@" + id + " does not exist",
									Collections.singletonList(autoAcessDTO)));
		}

		AutoApproveAccessConfig autoApproveData =
				autoApproveService.modifyAutoApprovConfigById(id, autoApproveRole);
		log.info("Modifying request@{}", id);
		return ResponseEntity.status(HttpStatus.OK)
				.body(
						new ServiceResponse(
								true, "modified access_request@" + id,
								Collections.singletonList(autoApproveData)));
	}
}
