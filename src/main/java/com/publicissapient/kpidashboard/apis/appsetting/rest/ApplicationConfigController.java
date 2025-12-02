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

import com.publicissapient.kpidashboard.apis.appsetting.config.ApplicationConfigDto;
import com.publicissapient.kpidashboard.apis.appsetting.service.ApplicationConfigServiceImpl;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

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
@RequiredArgsConstructor
public class ApplicationConfigController {

	private final ApplicationConfigServiceImpl applicationConfigService;

	/**
	 * Retrieves application configuration including Economic Benefits and Help resources. Returns
	 * configuration data with team size, cost parameters, and help resource URLs.
	 *
	 * @return ResponseEntity with configuration data in format: { "totalTeamSize": 30,
	 *     "avgCostPerTeamMember": 100000.0, "timeDuration": "Per Year", "productDocumentation":
	 *     "https://docs.example.com/product", "apiDocumentation": "https://docs.example.com/api",
	 *     "videoTutorials": "https://videos.example.com/tutorials", "raiseTicket":
	 *     "https://support.example.com/tickets", "supportChannel": "https://chat.example.com/support"
	 *     }
	 */
    @GetMapping(value = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Get Application Configuration",
            description =
                    "Retrieves comprehensive application configuration including economic benefits calculator parameters (team size, cost per member, time duration) and help resource URLs (documentation, tutorials, support channels) for the KnowHOW dashboard")
    @ApiResponses(
            value = {
                    @ApiResponse(
                            responseCode = "200",
                            description =
                                    "Application configuration retrieved successfully. Returns economic benefits settings and help resource URLs in a structured format.",
                            content = {
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = ServiceResponse.class),
                                            examples = @ExampleObject(
                                                    value = "{\"success\":true,\"message\":\"Application configuration retrieved successfully. Economic benefits parameters and help resources loaded.\",\"data\":{\"totalTeamSize\":30,\"avgCostPerTeamMember\":100000.0,\"timeDuration\":\"Per Year\",\"productDocumentation\":\"https://docs.example.com/product\",\"apiDocumentation\":\"https://docs.example.com/api\",\"videoTutorials\":\"https://videos.example.com/tutorials\",\"raiseTicket\":\"https://support.example.com/tickets\",\"supportChannel\":\"https://chat.example.com/support\"}}"
                                            ))
                            })
            })

    public ResponseEntity<ServiceResponse> getApplicationConfig() {
        ApplicationConfigDto configData = applicationConfigService.getApplicationConfig();

        return ResponseEntity.ok(
                new ServiceResponse(
                        true,
                        "Application configuration retrieved successfully",
                        configData));
    }
}
