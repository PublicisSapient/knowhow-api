/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.appsetting.rest;


import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.appsetting.config.PEBConfig;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/peb")
@Tag(name = "PotentialEconomicBenefit", description = "Endpoints for PEB Calculator Configuration")
@Slf4j
public class PEBConfigController {

	@Autowired private PEBConfig pebConfig;

	public void setPebConfig(PEBConfig pebConfig) {
		this.pebConfig = pebConfig;
	}

	/**
	 * Retrieves Potential Economic Benefits configuration.
	 *
	 * @return ResponseEntity containing PEB configuration parameters
	 */
	@GetMapping(value = "/configs", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(
			summary = "Get Economic Benefits Configuration",
			description = "Retrieves Potential Economic Benefits calculator configuration parameters")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Successfully retrieved PEB configuration",
						content = {
							@Content(
									mediaType = "application/json",
									schema = @Schema(implementation = ServiceResponse.class))
						})
			})
	public ResponseEntity<ServiceResponse> getEconomicBenefitsConfig() {
		log.info("Fetching economic benefits configuration");
		Map<String, Object> pebData = new LinkedHashMap<>();
		pebData.put(
				"totalDevelopers",
				pebConfig.getTotalDevelopers() != null ? pebConfig.getTotalDevelopers() : 30);
		pebData.put(
				"avgCostPerDeveloper",
				pebConfig.getAvgCostPerDeveloper() != null
						? pebConfig.getAvgCostPerDeveloper()
						: 100000.00);
		pebData.put(
				"timeDuration",
				pebConfig.getTimeDuration() != null ? pebConfig.getTimeDuration() : "Per Year");
		log.info("Economic benefits configuration retrieved successfully");
		return ResponseEntity.ok(
				new ServiceResponse(
						true, "Economic benefits configuration retrieved successfully", pebData));
	}
}
