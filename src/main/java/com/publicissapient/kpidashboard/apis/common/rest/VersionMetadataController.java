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
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.common.service.VersionMetadataService;
import com.publicissapient.kpidashboard.apis.model.VersionDetails;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Rest controller to handle version related requests.
 *
 * @author vijmishr1
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Version Metadata API", description = "APIs for fetching Version Metadata")
public class VersionMetadataController {

	private final VersionMetadataService versionMetadataService;

	/**
	 * Gets version details.
	 *
	 * @return the version details
	 */
	@GetMapping(value = "/getversionmetadata", produces = APPLICATION_JSON_VALUE)
	public ResponseEntity<VersionDetails> getVersionDetails() {
		log.debug("VersionMetadataController::getVersionDetails start");
		VersionDetails versionDetails = versionMetadataService.getVersionMetadata();
		return new ResponseEntity<>(versionDetails, HttpStatus.OK);
	}
}
