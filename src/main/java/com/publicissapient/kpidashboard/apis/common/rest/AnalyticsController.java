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

package com.publicissapient.kpidashboard.apis.common.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.common.service.CustomAnalyticsService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

import lombok.extern.java.Log;

@Log
@RestController
@RequiredArgsConstructor
@Tag(name = "Analytics Controller", description = "APIs for Analytics Management")
public class AnalyticsController {

	private final CustomAnalyticsService customAnalyticsService;

	/**
	 * Gets logo image file
	 *
	 * @return Logo
	 */
	@Operation(
			summary = "Get Analytics Switch",
			description = "Fetches the current status of the analytics feature switch.")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully retrieved analytics switch status"),
			@ApiResponse(responseCode = "500", description = "Internal server error")
	})
	@GetMapping(value = "/analytics/switch", produces = APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getAnalyticsSwitch() {
		log.info("Analytics Switch API called");
		return ResponseEntity.status(HttpStatus.OK)
				.body(new ServiceResponse(true, "Success", customAnalyticsService.getAnalyticsCheck()));
	}
}
