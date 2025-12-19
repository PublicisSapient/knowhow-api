/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.apis.bitbucket.rest;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.bson.types.ObjectId;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.bitbucket.dto.ScmConnectionMetaDataDTO;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmRepositoryService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/scm/config")
@Tag(name = "SCM Repositories Controller", description = "APIs for SCM Repositories Management")
public class ScmReposController {

	private final ScmRepositoryService scmRepositoryService;

	public ScmReposController(ScmRepositoryService scmRepositoryService) {
		this.scmRepositoryService = scmRepositoryService;
	}

	@GetMapping("/connection/{connectionId}")
	@Operation(
			summary = "Get SCM repositories by connection ID",
			description =
					"Retrieves all SCM repositories associated with a specific connection ID. "
							+ "Repositories are returned sorted by last updated timestamp with the latest first.")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved repositories or no repositories found",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class))),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error",
						content =
								@Content(
										mediaType = "application/json",
										schema = @Schema(implementation = ServiceResponse.class)))
			})
	public ResponseEntity<ServiceResponse> getScmReposByConnectionId(
			@Parameter(
							description = "MongoDB ObjectId of the connection",
							required = true,
							example = "507f1f77bcf86cd799439011",
							schema = @Schema(type = "string", pattern = "^[0-9a-fA-F]{24}$"))
					@PathVariable("connectionId")
					String connectionId) {
		if (ObjectId.isValid(connectionId)) {
			ScmConnectionMetaDataDTO scmConnectionMetaDataDTO =
					scmRepositoryService.getScmRepositoryListByConnectionId(new ObjectId(connectionId));
			ServiceResponse serviceResponse =
					new ServiceResponse(
							true, "Fetched Repositories for given connection", scmConnectionMetaDataDTO);
			return ResponseEntity.ok(serviceResponse);
		}
		return ResponseEntity.ok(new ServiceResponse(false, "Invalid connectionId", null));
	}
}
