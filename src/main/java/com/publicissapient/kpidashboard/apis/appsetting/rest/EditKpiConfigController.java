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

package com.publicissapient.kpidashboard.apis.appsetting.rest;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.publicissapient.kpidashboard.apis.appsetting.service.EditKpiConfigService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.common.model.jira.MetadataValue;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller for edit KPI Config.
 *
 * @author jagmongr
 */
@Slf4j
@RestController
@RequestMapping("/editConfig")
@RequiredArgsConstructor
@Tag(name = "Edit KPI Config API", description = "APIs for editing KPI Configurations")
public class EditKpiConfigController {

	private final EditKpiConfigService editKpiConfigService;

	/**
	 * Gets KPI configuration data for the environment by type.
	 *
	 * @param projectBasicConfigId for project config id
	 * @return responseEntity with data,message and status
	 */
	@GetMapping(
			value = "/jira/editKpi/{projectBasicConfigId}/{kpiCode}",
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> fetchTypeValues(
			@PathVariable String projectBasicConfigId, @PathVariable String kpiCode) {
		projectBasicConfigId = CommonUtils.handleCrossScriptingTaintedValue(projectBasicConfigId);
		kpiCode = CommonUtils.handleCrossScriptingTaintedValue(kpiCode);
		log.info("Fetching data in KPI edit configuration for :{}", projectBasicConfigId);
		Map<String, List<MetadataValue>> data =
				editKpiConfigService.getDataForType(projectBasicConfigId, kpiCode);
		ServiceResponse serviceResponse = new ServiceResponse(true, "Success", data);
		return ResponseEntity.status(HttpStatus.OK).body(serviceResponse);
	}
}
