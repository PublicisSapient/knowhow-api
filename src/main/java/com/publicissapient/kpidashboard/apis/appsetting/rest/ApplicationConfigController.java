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

import com.publicissapient.kpidashboard.apis.appsetting.config.HelpConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
@Tag(name = "ApplicationConfiguration", description = "Endpoints for Application Configuration")
@Slf4j
public class ApplicationConfigController {

	private final PEBConfig pebConfig;
    private final HelpConfig helpConfig;

    public ApplicationConfigController(PEBConfig pebConfig, HelpConfig helpConfig) {
        this.pebConfig = pebConfig;
        this.helpConfig = helpConfig;
    }

	/**
	 * Retrieves application configuration including Economic Benefits and Help resources.
     * Returns configuration data with team size, cost parameters, and help resource URLs.
     *
	 * @return ResponseEntity with configuration data in format:
	 * {
	 *   "totalTeamSize": 30,
	 *   "avgCostPerTeamMember": 100000.0,
	 *   "timeDuration": "Per Year",
	 *   "productDocumentation": "https://docs.example.com/product",
	 *   "apiDocumentation": "https://docs.example.com/api",
	 *   "videoTutorials": "https://videos.example.com/tutorials",
	 *   "raiseTicket": "https://support.example.com/tickets",
	 *   "supportChannel": "https://chat.example.com/support"
	 * }
	 */
	@GetMapping(value = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(
			summary = "Get Application Configuration",
			description = "Retrieves comprehensive application configuration including economic benefits calculator parameters (team size, cost per member, time duration) and help resource URLs (documentation, tutorials, support channels) for the KnowHOW dashboard")
	@ApiResponses(
			value = {
				@ApiResponse(
						responseCode = "200",
						description = "Application configuration retrieved successfully. Returns economic benefits settings and help resource URLs in a structured format.",
						content = {
							@Content(
									mediaType = "application/json",
									schema = @Schema(implementation = ServiceResponse.class))
						})
			})
	public ResponseEntity<ServiceResponse> getApplicationConfig() {
        Map<String, Object> configData = new LinkedHashMap<>();
        getEconomicBenefitsConfigs(configData);
        getHelpConfig(configData);

		return ResponseEntity.ok(
				new ServiceResponse(
						true, "Application configuration retrieved successfully. Economic benefits parameters and help resources loaded.", configData));
	}


    /**
     * Retrieves Economic Benefits configuration parameters.
     * Populates the data map with team size, cost per member, and time duration.
     */
    public void getEconomicBenefitsConfigs(Map<String, Object> configData) {
        log.info("Fetching economic benefits configuration");
        configData.put(
                "totalTeamSize",
                pebConfig.getTotalDevelopers() != null ? pebConfig.getTotalDevelopers() : 30);
        configData.put(
                "avgCostPerTeamMember",
                pebConfig.getAvgCostPerDeveloper() != null
                        ? pebConfig.getAvgCostPerDeveloper()
                        : 100000.00);
        configData.put(
                "timeDuration",
                pebConfig.getTimeDuration() != null ? pebConfig.getTimeDuration() : "Per Year");
        log.info("Economic benefits configuration retrieved successfully");
    }



    /**
     * Retrieves all configured help resource URLs. Returns a map containing URLs for product
     * documentation, API documentation, video tutorials, ticket raising, and support channels.
     */
    public void getHelpConfig(Map<String, Object> resourceLinks ) {
        log.info("Fetching help configuration");
        resourceLinks.put(
                "productDocumentation",
                helpConfig.getProductDocumentationUrl() != null
                        ? helpConfig.getProductDocumentationUrl()
                        : "");
        resourceLinks.put(
                "apiDocumentation",
                helpConfig.getApiDocumentationUrl() != null ? helpConfig.getApiDocumentationUrl() : "");
        resourceLinks.put(
                "videoTutorials",
                helpConfig.getVideoTutorialsUrl() != null ? helpConfig.getVideoTutorialsUrl() : "");
        resourceLinks.put(
                "raiseTicket",
                helpConfig.getRaiseTicketUrl() != null ? helpConfig.getRaiseTicketUrl() : "");
        resourceLinks.put(
                "supportChannel",
                helpConfig.getSupportChannelUrl() != null ? helpConfig.getSupportChannelUrl() : "");
        log.info("Help configuration retrieved successfully");
    }
}
