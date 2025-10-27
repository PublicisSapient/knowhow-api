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

import java.net.URI;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.kpiintegration.service.KpiRecommendationProviderService;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.ProjectWiseKpiRecommendation;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;

@Service
public class RnrEngineRecommendationProviderServiceImpl
		implements KpiRecommendationProviderService {

	@Autowired CustomApiConfig customApiConfig;

	@Autowired private RestTemplate restTemplate;

	private static final String RNR_API_HEADER = "X-Custom-Authentication";

	/**
	 * Retrieves project-wise KPI recommendations based on the provided KPI request and persona.
	 *
	 * @param kpiRequest The request object containing details such as selected map and KPI IDs.
	 * @param promptPersona The persona to be used for generating recommendations.
	 * @return A list of {@link ProjectWiseKpiRecommendation} objects containing recommendations for
	 *     each project.
	 */
	@Override
	public List<ProjectWiseKpiRecommendation> getProjectWiseKpiRecommendations(
			KpiRequest kpiRequest, String promptPersona) {
		Optional<String> sprintId =
				kpiRequest.getSelectedMap().get(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT).stream()
						.findFirst();
		Optional<String> projectId =
				kpiRequest.getSelectedMap().get(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT).stream()
						.findFirst();

		String recommendationUrl =
				String.format(
						customApiConfig.getRnrRecommendationUrl(),
						projectId.orElse(""),
						sprintId.orElse(""),
						String.join(",", kpiRequest.getKpiIdList()));

		HttpHeaders headers = new HttpHeaders();
		headers.set(RNR_API_HEADER, customApiConfig.getRnrRecommendationApiKey());
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<List<ProjectWiseKpiRecommendation>> response =
				restTemplate.exchange(
						URI.create(recommendationUrl),
						HttpMethod.GET,
						new HttpEntity<>(headers),
						new ParameterizedTypeReference<>() {});

		return response.getBody();
	}
}
