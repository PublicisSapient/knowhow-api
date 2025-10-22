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

package com.publicissapient.kpidashboard.apis.productivity.rest;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.productivity.dto.ProductivityGainCalculationRequestDTO;
import com.publicissapient.kpidashboard.apis.productivity.service.ProductivityGainService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/productivity")
@RequiredArgsConstructor
@Tag(
		name = "Productivity",
		description =
				"Endpoints to calculate the 'productivity' of an hierarchical level based on multiple KPIs")
public class ProductivityController {

	private final ProductivityGainService productivityGainService;

	@Operation(
			summary = "Calculate productivity",
			description =
					"Determines the 'productivity' gain for an entire hierarchical level or for all entries under a specific parentId based on multiple KPIs")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully calculated the 'productivity' gain",
						content = {
							@Content(
									mediaType = "application/json",
									schema = @Schema(implementation = ServiceResponse.class))
						}),
				@ApiResponse(
						responseCode = "400",
						description =
								"""
					Bad request. Will be returned if any of the following cases occurs:
					- No hierarchy 'level' was provided
					- No hierarchy 'label' was provided
					- The requested level does not exist
					- The requested label does not exist
					- The level and the label are corresponding to different hierarchy entities
					- The level and the label are not corresponding to the next hierarchical child level and label of the 'parentId' when 'parentId' is provided
					"""),
				@ApiResponse(
						responseCode = "403",
						description = "Current user does not have access to this API"),
				@ApiResponse(responseCode = "500", description = "Unexpected server error happened")
			})
	@PostMapping("/calculate")
	public ServiceResponse calculateProductivity(
			@Valid @RequestBody
					ProductivityGainCalculationRequestDTO productivityGainCalculationRequestDTO) {
		return productivityGainService.processProductivityCalculationRequest(
				productivityGainCalculationRequestDTO);
	}
}
