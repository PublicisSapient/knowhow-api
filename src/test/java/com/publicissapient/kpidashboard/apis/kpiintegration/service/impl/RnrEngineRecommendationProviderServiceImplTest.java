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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.ProjectWiseKpiRecommendation;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;

@ExtendWith(MockitoExtension.class)
class RnrEngineRecommendationProviderServiceImplTest {

	@Mock private CustomApiConfig customApiConfig;

	@Mock private RestTemplate restTemplate;

	@InjectMocks
	private RnrEngineRecommendationProviderServiceImpl rnrEngineRecommendationProviderService;

	private KpiRequest kpiRequest;

	@BeforeEach
	public void setup() {
		kpiRequest = new KpiRequest();
		kpiRequest.setIds(new String[] {"id1"});
		Map<String, List<String>> selectedMap = new HashMap<>();
		selectedMap.put(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, Arrays.asList("project1"));
		selectedMap.put(CommonConstant.HIERARCHY_LEVEL_ID_SPRINT, Arrays.asList("sprint1"));
		kpiRequest.setSelectedMap(selectedMap);
		kpiRequest.setKpiIdList(Arrays.asList("kpi1", "kpi2"));
		kpiRequest.setKpiIdList(List.of("kpi1", "kpi2"));
		Mockito.when(customApiConfig.getRnrRecommendationUrl()).thenReturn("http://mock-url/%s");
		Mockito.when(customApiConfig.getRnrRecommendationApiKey()).thenReturn("mock-api-key");
	}

	@Test
	void getProjectWiseKpiRecommendations_returnsRecommendations_whenValidRequestProvided() {

		List<ProjectWiseKpiRecommendation> recommendations =
				List.of(new ProjectWiseKpiRecommendation());
		Mockito.when(
						restTemplate.exchange(
								any(),
								Mockito.eq(HttpMethod.GET),
								any(HttpEntity.class),
								Mockito.<ParameterizedTypeReference<List<ProjectWiseKpiRecommendation>>>any()))
				.thenReturn(ResponseEntity.ok(recommendations));

		List<ProjectWiseKpiRecommendation> result =
				rnrEngineRecommendationProviderService.getProjectWiseKpiRecommendations(kpiRequest, null);

		assertEquals(recommendations, result);
	}

	@Test
	void getProjectWiseKpiRecommendations_throwsException_whenRestTemplateFails() {

		String recommendationUrl = "http://mock-url";
		Mockito.when(
						restTemplate.exchange(
								Mockito.eq(URI.create(recommendationUrl)),
								Mockito.eq(HttpMethod.GET),
								any(HttpEntity.class),
								Mockito.<ParameterizedTypeReference<List<ProjectWiseKpiRecommendation>>>any()))
				.thenThrow(new RuntimeException("RestTemplate error"));

		assertThrows(
				RuntimeException.class,
				() ->
						rnrEngineRecommendationProviderService.getProjectWiseKpiRecommendations(
								kpiRequest, null));
	}
}
