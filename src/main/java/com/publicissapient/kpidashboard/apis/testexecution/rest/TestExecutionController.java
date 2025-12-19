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

package com.publicissapient.kpidashboard.apis.testexecution.rest;

import java.util.List;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.abac.ContextAwarePolicyEnforcement;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;
import com.publicissapient.kpidashboard.apis.testexecution.service.TestExecutionService;
import com.publicissapient.kpidashboard.common.model.testexecution.TestExecutionData;

/**
 * @author sansharm13
 */
@RestController
@RequestMapping("/testexecution")
@RequiredArgsConstructor
@Tag(name = "Test Execution Controller", description = "APIs for Test Execution Data Management")
public class TestExecutionController {

	private static final String TEST_EXECUTION_STATUS = "SAVE_UPDATE_TEST_EXECUTION";

	private final TestExecutionService testExecutionService;
	private final ContextAwarePolicyEnforcement policy;

	/**
	 * This api saves test_execution data.
	 *
	 * @param testExecution data to be saved
	 * @return service response entity
	 */
	@PostMapping(
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<ServiceResponse> addTestExecutionData(
			@RequestBody TestExecutionData testExecution) {
		ServiceResponse response =
				new ServiceResponse(false, "Failed to add  Test Execution Data", null);
		try {
			policy.checkPermission(testExecution, TEST_EXECUTION_STATUS);
			testExecution = testExecutionService.processTestExecutionData(testExecution);
			if (null != testExecution) {
				response =
						new ServiceResponse(true, "Successfully added Test Execution Data", testExecution);
			}
		} catch (AccessDeniedException ade) {
			response = new ServiceResponse(false, "Unauthorized", null);
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}

	@GetMapping("/{basicProjectConfigId}")
	public ResponseEntity<ServiceResponse> getTestExecutions(
			@PathVariable String basicProjectConfigId) {
		ServiceResponse response = null;

		List<TestExecutionData> testExecutions =
				testExecutionService.getTestExecutions(basicProjectConfigId);
		if (CollectionUtils.isNotEmpty(testExecutions)) {
			response = new ServiceResponse(true, "Test Execution Data", testExecutions);
		} else {
			response = new ServiceResponse(false, "No data", null);
		}
		return ResponseEntity.status(HttpStatus.OK).body(response);
	}
}
