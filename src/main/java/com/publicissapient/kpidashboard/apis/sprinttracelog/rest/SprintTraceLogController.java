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

package com.publicissapient.kpidashboard.apis.sprinttracelog.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.sprinttracelog.service.SprintTraceLogService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/activeIteration")
@RequiredArgsConstructor
@Tag(name = "Sprint Trace Log Controller", description = "APIs for Sprint Trace Log Management")
public class SprintTraceLogController {
	private final SprintTraceLogService sprintTraceLogService;

	@Operation(
			summary = "Get Active Iteration Fetch Status",
			description = "API to get the status of active iteration data fetch for a given sprint ID")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully fetched active iteration fetch status"),
				@ApiResponse(responseCode = "400", description = "Invalid sprint ID supplied"),
				@ApiResponse(
						responseCode = "500",
						description = "Internal server error while fetching active iteration status")
			})
	@GetMapping("/fetchStatus/{sprintId}")
	public ResponseEntity<ServiceResponse> getActiveItrFetchStatus(
			@Parameter(
							description = "Sprint ID for which to fetch the active iteration status",
							required = true,
							example = "605c72ef4f1a2565f0e4b0b5")
					@PathVariable
					String sprintId) {
		ServiceResponse response = sprintTraceLogService.getActiveSprintFetchStatus(sprintId);
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}
}
