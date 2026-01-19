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

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.common.service.ConfigDetailService;
import com.publicissapient.kpidashboard.apis.model.ConfigDetails;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.common.service.TemplateConfigurationService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Rest controller to handle configuration properties */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Config Details Controller", description = "APIs for Configuration Details Management")
public class ConfigDetailsController {

	private final ConfigDetailService configDetailService;
	private final TemplateConfigurationService templateConfigurationService;

	/**
	 * Returns required properties from application.prop
	 *
	 * @return ResponseEntity<ConfigDetails>
	 */
	// Todo: to be removed after V2 become primary view
	@GetMapping(value = "/configDetails", produces = APPLICATION_JSON_VALUE)
	public ResponseEntity<ConfigDetails> getConfigDetails() {
		log.info("ConfigDetailsController::getConfigDetails start");
		ConfigDetails configDetails = configDetailService.getConfigDetails();
		log.info("ConfigDetailsController::getConfigDetails end");
		return ResponseEntity.status(HttpStatus.OK).body(configDetails);
	}

	/**
	 * Fetches the configuration template.
	 *
	 * @return a ServiceResponse containing the configuration template documents
	 */
	@GetMapping("/configuration")
	@ResponseStatus(HttpStatus.OK)
	public ServiceResponse getConfigurationTemplate() {
		return new ServiceResponse(
				true,
				"Configuration template fetched successfully.",
				templateConfigurationService.getConfigurationTemplate());
	}
}
