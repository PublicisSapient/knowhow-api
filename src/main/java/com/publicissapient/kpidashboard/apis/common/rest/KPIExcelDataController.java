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

import java.util.Arrays;
import javax.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.publicissapient.kpidashboard.apis.appsetting.service.KPIExcelDataService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.model.KPIExcelValidationDataResponse;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * This class fetches KPI data for received filter. This API is used by Application Dashboard to
 * fetch Excel Data.
 *
 * @author tauakram
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "KPI Excel Data API", description = "APIs for fetching KPI Excel Data")
public class KPIExcelDataController {

	private final KPIExcelDataService kpiExcelDataService;
	private final CustomApiConfig customApiConfig;

	/**
	 * Fetches KPI validation data (story keys, defect keys) for a specific KPI id.
	 *
	 * @param kpiRequest the kpi request
	 * @param kpiID the kpi id
	 * @return validation kpi data
	 */
	@PostMapping(value = "/v1/kpi/{kpiID}", produces = APPLICATION_JSON_VALUE)
	public ResponseEntity<KPIExcelValidationDataResponse> getValidationKPIData(
			HttpServletRequest request,
			@NotNull @RequestBody KpiRequest kpiRequest,
			@NotNull @PathVariable("kpiID") String kpiID) {
		String apiKey = customApiConfig.getxApiKey();
		Boolean isApiAuth =
				StringUtils.isNotEmpty(apiKey)
						&& apiKey.equalsIgnoreCase(request.getHeader(Constant.TOKEN_KEY));
		String kpiRequestStr = kpiRequest.toString();
		kpiID = CommonUtils.handleCrossScriptingTaintedValue(kpiID);
		kpiRequestStr = CommonUtils.handleCrossScriptingTaintedValue(kpiRequestStr);
		log.info(
				"[KPI-EXCEL-DATA][]. Received Specific Excel KPI Data request for {} with kpiRequest {}",
				kpiID,
				kpiRequestStr);

		KPIExcelValidationDataResponse responseList =
				(KPIExcelValidationDataResponse)
						kpiExcelDataService.process(
								kpiID,
								kpiRequest.getLevel(),
								Arrays.asList(kpiRequest.getIds()),
								null,
								kpiRequest,
								null,
								isApiAuth);
		return ResponseEntity.ok().body(responseList);
	}
}
