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

package com.publicissapient.kpidashboard.apis.kpis;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.model.MasterResponse;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

/**
 * Rest Controller for all kpi master requests.
 *
 * @author prigupta8
 */
@RestController
@RequestMapping("/masterData")
@RequiredArgsConstructor
@Tag(name = "KPI Master Controller", description = "APIs for KPI Master Data Management")
public class KPIMasterController {

	private final KpiHelperService kPIHelperService;

	/**
	 * Fetch master data master response.
	 *
	 * @return the master response
	 */
	@GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
	public MasterResponse fetchMasterData() {
		return kPIHelperService.fetchKpiMasterList();
	}
}
