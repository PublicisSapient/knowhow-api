/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 * /*******************************************************************************
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

package com.publicissapient.kpidashboard.apis.pushdata.controller;

import java.util.List;
import javax.validation.Valid;

import org.apache.commons.collections4.CollectionUtils;
import org.bson.types.ObjectId;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.pushdata.model.ExposeApiToken;
import com.publicissapient.kpidashboard.apis.pushdata.model.PushBuildDeploy;
import com.publicissapient.kpidashboard.apis.pushdata.model.PushDataTraceLog;
import com.publicissapient.kpidashboard.apis.pushdata.model.dto.PushBuildDeployDTO;
import com.publicissapient.kpidashboard.apis.pushdata.model.dto.PushDataTraceLogDTO;
import com.publicissapient.kpidashboard.apis.pushdata.service.AuthExposeAPIService;
import com.publicissapient.kpidashboard.apis.pushdata.service.PushBaseService;
import com.publicissapient.kpidashboard.apis.pushdata.service.PushDataTraceLogService;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Validated
@RestController
@RequestMapping("/pushData")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Push Data Controller", description = "APIs for Push Data Management")
public class PushDataController {

	final ModelMapper modelMapper = new ModelMapper();
	private final PushBaseService pushBuildService;
	private final AuthExposeAPIService authExposeAPIService;
	private final PushDataTraceLogService pushDataTraceLogService;

	/**
	 * push data api for build tools
	 *
	 * @param request The HTTP request containing headers and other request-related information.
	 * @param pushBuildDeployDTO The data transfer object containing build deployment details.
	 * @return A ResponseEntity containing the service response.
	 */
	@PostMapping(value = "/build", consumes = MediaType.APPLICATION_JSON_VALUE) // NOSONAR
	public ResponseEntity<ServiceResponse> savePushDataBuilds(
			HttpServletRequest request, @RequestBody @Valid PushBuildDeployDTO pushBuildDeployDTO) {
		PushDataTraceLog instance = PushDataTraceLog.getInstance();
		instance.setPushApiSource("build");
		ExposeApiToken exposeApiToken = authExposeAPIService.validateToken(request);
		PushBuildDeploy buildDeploy = modelMapper.map(pushBuildDeployDTO, PushBuildDeploy.class);
		return ResponseEntity.status(HttpStatus.OK)
				.body(
						new ServiceResponse(
								true,
								"Saved Records successfully",
								pushBuildService.processPushDataInput(
										buildDeploy, exposeApiToken.getBasicProjectConfigId())));
	}

	@GetMapping(value = "/tracelog/{basicConfigId}", consumes = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> getTraceLog(@PathVariable String basicConfigId) {
		List<PushDataTraceLogDTO> allLogs =
				pushDataTraceLogService.getByProjectConfigId(new ObjectId(basicConfigId));
		ServiceResponse response;
		if (CollectionUtils.isNotEmpty(allLogs)) {
			log.info("Fetching all logs of configId {} ", basicConfigId);
			response = new ServiceResponse(true, "Found Logs", allLogs);
		} else {
			response = new ServiceResponse(false, "No Logs Present", null);
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}
}
