/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.apis.kpiintegration.service.impl;

import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.model.KpiRecommendationRequestDTO;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.ProjectWiseKpiRecommendation;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class KpiRecommendationServiceImpl {

	@Autowired
	private RnrEngineRecommendationProviderServiceImpl rnrEngineRecommendationProviderService;

	@Autowired private AiKpiRecommendationProviderServiceImpl aiKpiRecommendationProviderService;

	@Autowired private CustomApiConfig customApiConfig;

	/**
	 * Retrieves project-wise KPI recommendations based on the provided recommendation request.
	 *
	 * @param kpiRecommendationRequestDTO The DTO containing details for the KPI recommendation
	 *     request.
	 * @return A {@link ServiceResponse} object containing the success status, message, and list of
	 *     project-wise KPI recommendations. If AI recommendations are enabled and the recommendation
	 *     persona is null, returns a failure response.
	 */
	public ServiceResponse getProjectWiseKpiRecommendation(
			KpiRecommendationRequestDTO kpiRecommendationRequestDTO) {
		List<ProjectWiseKpiRecommendation> projectWiseKpiRecommendations;
		KpiRequest kpiRequest = new KpiRequest();
		BeanUtils.copyProperties(kpiRecommendationRequestDTO, kpiRequest);
		if (CollectionUtils.isNotEmpty(customApiConfig.getAiRecommendationKpiList())) {
			if (kpiRecommendationRequestDTO.getRecommendationFor() == null) {
				return new ServiceResponse(false, "AiRecommendation", null);
			}
			projectWiseKpiRecommendations =
					aiKpiRecommendationProviderService.getProjectWiseKpiRecommendations(
							kpiRequest, kpiRecommendationRequestDTO.getRecommendationFor());
		} else {
			projectWiseKpiRecommendations =
					rnrEngineRecommendationProviderService.getProjectWiseKpiRecommendations(kpiRequest, null);
		}
		return new ServiceResponse(
				true, "Successfully Fetched Recommendations", projectWiseKpiRecommendations);
	}
}
