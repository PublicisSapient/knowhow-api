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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.service.ExecutiveServiceImpl;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;

import lombok.RequiredArgsConstructor;

/**
 * Controller for handling executive dashboard requests.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/executive")
public class ExecutiveController {

	private final ExecutiveServiceImpl executiveService;

	/**
	 * Retrieves executive dashboard data based on the provided KPI request.
	 *
	 * @param kpiRequest
	 *            The KPI request containing filter criteria
	 * @return Executive dashboard response with project metrics
	 */
	@GetMapping
	public ResponseEntity<ExecutiveDashboardResponseDTO> getExecutive(@RequestBody KpiRequest kpiRequest) {
		ExecutiveDashboardResponseDTO response = kpiRequest.isKanban()
				? executiveService.getExecutiveDashboardKanban(kpiRequest)
				: executiveService.getExecutiveDashboardScrum(kpiRequest);

		return ResponseEntity.ok(response);
	}
}
