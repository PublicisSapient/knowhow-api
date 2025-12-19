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

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.common.service.MetaDataIdentifierService;
import com.publicissapient.kpidashboard.common.model.jira.MetadataIdentifierDTO;

@RestController
@RequestMapping("/templates")
@RequiredArgsConstructor
@Tag(name = "MetaData Identifier Controller", description = "APIs for MetaData Identifier Management")
public class MetaDataIdentifierController {

	private final MetaDataIdentifierService metaDataIdentifierService;

	@Operation(summary = "Get Template Names",
			description = "API to get template names")
	@ApiResponses(value = {
			@ApiResponse(responseCode = "200", description = "Successfully fetched template names"),
			@ApiResponse(responseCode = "400", description = "Bad Request"),
			@ApiResponse(responseCode = "500", description = "Internal Server Error") })
	@GetMapping(value = {"/{basicConfigId}"})
	public ResponseEntity<List<MetadataIdentifierDTO>> getTemplateNames(
			@Parameter(description = "Basic Configuration Id", required = true, example = "60d21b8667d0d8992e610c85")
			@PathVariable String basicConfigId) {
		return new ResponseEntity<>(metaDataIdentifierService.getTemplateDetails(), HttpStatus.OK);
	}
}
