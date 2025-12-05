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

package com.publicissapient.kpidashboard.apis.aiusage.rest.contract;

import com.publicissapient.kpidashboard.apis.aiusage.model.AIUsageStatistics;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;

import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import org.apache.coyote.BadRequestException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpServerErrorException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

import java.time.LocalDate;

@RequestMapping("/v1/ai-usage")
public interface AIUsageAPI {

	@GetMapping("/stats")
	@Operation(
			summary = "Get Latest AI Usage Metrics by Level Name",
			description = "Fetches the latest AI usage metrics for the specified organizational level",
			responses = {
				@ApiResponse(
						responseCode = "200",
						description = "Latest metric values retrieved successfully",
						content = @Content(schema = @Schema(implementation = AIUsageStatistics.class))),
				@ApiResponse(
						responseCode = "400",
						description = "Bad Request",
						content = @Content(schema = @Schema(implementation = BadRequestException.class))),
				@ApiResponse(
						responseCode = "404",
						description = "Not Found",
						content = @Content(schema = @Schema(implementation = EntityNotFoundException.class))),
				@ApiResponse(
						responseCode = "503",
						description = "Service Unavailable",
						content = @Content(schema = @Schema(implementation = HttpServerErrorException.class)))
			})
	ResponseEntity<ServiceResponse> getAIUsageStats(
			@Parameter(description = "Hierarchy level name", required = true, example = "account / vertical / bu")
			@RequestParam String levelName,
			@Parameter(description = "Flag to include user-level AI usage details in the response", example = "true")
			@RequestParam(required = false, defaultValue = "false")
			Boolean includeUsers,
			@Parameter(description = "Start date in YYYY-MM-DD format", example = "2025-01-01")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate startDate,
			@Parameter(description = "End date in YYYY-MM-DD format", example = "2025-01-31")
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
			LocalDate endDate,
			@PageableDefault(sort = "email", direction = Sort.Direction.ASC)
			@Parameter(description = "Pagination parameters for fetching users for AI usage statistics details")
			Pageable pageable)
			throws BadRequestException, EntityNotFoundException;
}
