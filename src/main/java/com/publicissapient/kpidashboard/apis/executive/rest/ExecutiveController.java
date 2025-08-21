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
package com.publicissapient.kpidashboard.apis.executive.rest;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.errors.ExecutiveDataException;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardRequestDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.service.ExecutiveService;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller for handling executive dashboard requests.
 */
/**
 * Controller for handling executive dashboard requests.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/executive")
@Slf4j
@Validated
public class ExecutiveController {

	private static final String EXECUTIVE_DASHBOARD_REQUEST = "Processing executive dashboard request for {} methodology with level: {}, label: {}";
	private static final String REQUEST_PROCESSED_SUCCESS = "Successfully processed executive dashboard request";
	private static final String REQUEST_PROCESSING_ERROR = "Error processing executive dashboard request";

	private final ExecutiveService executiveService;

	/**
	 * Retrieves executive dashboard data based on the provided request.
	 *
	 * @param request
	 *            The executive dashboard request containing filter criteria
	 * @param iskanban
	 *            Flag indicating whether to use Kanban (true) or Scrum (false)
	 *            strategy
	 * @return Executive dashboard response with project metrics
	 */
	/**
	 * Retrieves executive dashboard data based on the provided request.
	 *
	 * @param request
	 *            the executive dashboard request DTO containing filter criteria
	 * @param iskanban
	 *            flag indicating whether to use Kanban (true) or Scrum (false)
	 *            methodology
	 * @return response entity containing the executive dashboard data or error
	 *         message
	 */
	@PostMapping()
	public ResponseEntity<ServiceResponse> getExecutive(@Valid @RequestBody ExecutiveDashboardRequestDTO request,
			@RequestParam(required = true) boolean iskanban) {

		log.info(EXECUTIVE_DASHBOARD_REQUEST, iskanban ? "Kanban" : "Scrum", request.getLevel(), request.getLabel());

		try {
			ExecutiveDashboardResponseDTO response = iskanban ? executiveService.getExecutiveDashboardKanban(request)
					: executiveService.getExecutiveDashboardScrum(request);

			log.debug(REQUEST_PROCESSED_SUCCESS);
			return new ResponseEntity<>(new ServiceResponse(true, "Data fetched successfully", response.getData()),
					HttpStatus.OK);

		} catch (ExecutiveDataException e) {
			log.error("{}: {}", REQUEST_PROCESSING_ERROR, e.getMessage(), e);
			throw e; // Let GlobalExceptionHandler handle it
		} catch (Exception e) {
			log.error("{}: {}", REQUEST_PROCESSING_ERROR, e.getMessage(), e);
			throw new ExecutiveDataException("Service unavailable. Please try again later.", e);
		}
	}
}
