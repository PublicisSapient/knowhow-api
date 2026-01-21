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

package com.publicissapient.kpidashboard.apis.datamigration;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.datamigration.model.MigrateData;
import com.publicissapient.kpidashboard.apis.datamigration.service.DataMigrationService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/hierarchy/migrate")
@RequiredArgsConstructor
@Tag(name = "Data Migration Controller", description = "APIs for Data Migration Operations")
public class MigrationController {

	private final DataMigrationService dataMigrationService;

	@GetMapping(value = "/validate")
	public ResponseEntity<ServiceResponse> dataQualityCheck() {
		List<MigrateData> faultyProjects = dataMigrationService.dataMigration();
		if (CollectionUtils.isNotEmpty(faultyProjects)) {
			return ResponseEntity.status(HttpStatus.SC_METHOD_FAILURE)
					.body(new ServiceResponse(false, "Erroneous Projects", faultyProjects));
		} else {
			return ResponseEntity.status(HttpStatus.SC_NO_CONTENT).body(new ServiceResponse());
		}
	}

	@PutMapping(value = "/populateorganization") // put call
	public ResponseEntity<ServiceResponse> populateOrganizationHierarchy() {
		try {
			dataMigrationService.populateOrganizationHierarchy();
			return ResponseEntity.status(HttpStatus.SC_NO_CONTENT).body(new ServiceResponse());
		} catch (Exception ex) {
			return ResponseEntity.status(HttpStatus.SC_INTERNAL_SERVER_ERROR)
					.body(new ServiceResponse(false, "could not save to database", ex.getMessage()));
		}
	}
}
