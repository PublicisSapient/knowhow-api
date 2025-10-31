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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Qualifier;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.request.chat.ChatGenerationRequest;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.parser.ParserStategy;
import com.publicissapient.kpidashboard.apis.ai.service.PromptGenerator;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.kpiintegration.service.KpiIntegrationServiceImpl;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.ProjectWiseKpiRecommendation;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;

@ExtendWith(MockitoExtension.class)
class AiKpiRecommendationProviderServiceImplTest {

	@InjectMocks private AiKpiRecommendationProviderServiceImpl aiKpiRecommendationProviderService;

	@Mock private CustomApiConfig customApiConfig;

	@Mock private PromptGenerator promptGenerator;

	@Mock private AiGatewayClient aiGatewayClient;

	@Mock private KpiIntegrationServiceImpl kpiIntegrationService;

	@Mock
	@Qualifier("AiRecommendation")
	private ParserStategy<Object> parserStategy;

	private KpiElement kpiElement1;

	private KpiElement kpiElement2;
	private String content;

	@BeforeEach
	void setUp() throws EntityNotFoundException {
		DataCount dataCount = new DataCount();
		dataCount.setMaturity("1");
		dataCount.setMaturityValue("35");
		kpiElement1 = new KpiElement();
		kpiElement1.setTrendValueList(Arrays.asList(dataCount));
		DataCountGroup dataCountGroup = new DataCountGroup();
		dataCountGroup.setFilter("Overall");
		dataCountGroup.setValue(Arrays.asList(dataCount));
		kpiElement2 = new KpiElement();
		kpiElement2.setTrendValueList(Arrays.asList(dataCountGroup));
		when(customApiConfig.getAiRecommendationKpiList()).thenReturn(Arrays.asList("KPI1", "KPI2"));
		when(kpiIntegrationService.getKpiResponses(any()))
				.thenReturn(Collections.singletonList(kpiElement1));
		when(promptGenerator.getKpiRecommendationPrompt(any(), any())).thenReturn("Generated Prompt");
	}

	@Test
	void getProjectWiseKpiRecommendationsReturnsRecommendationsForValidInput()
			throws JsonProcessingException {
		KpiRequest kpiRequest = new KpiRequest();
		kpiRequest.setIds(new String[] {"project1"});
		kpiRequest.setSelectedMap(new HashMap<>());

		content =
				"{\"project_health_value\": 85.0, \"project_recommendations\": [{\"kpi\": \"kpi\",\"recommendation\": \"Improve code quality\",\"observation\": \"Observation\",\"correlated_kpis\": [\"Recommendation\"], \"severity\": \"High\"}]}";
		when(aiGatewayClient.generate(any(ChatGenerationRequest.class)))
				.thenReturn(new ChatGenerationResponseDTO(content));
		when(parserStategy.parse(any())).thenReturn(new ObjectMapper().readTree(content));

		List<ProjectWiseKpiRecommendation> recommendations =
				aiKpiRecommendationProviderService.getProjectWiseKpiRecommendations(kpiRequest, "persona");

		assertNotNull(recommendations);
		assertEquals(1, recommendations.size());
		assertEquals("project1", recommendations.get(0).getProjectId());
		assertEquals(85.0, recommendations.get(0).getProjectScore());
		assertEquals(1, recommendations.get(0).getRecommendations().size());
		assertEquals(
				"Improve code quality",
				recommendations.get(0).getRecommendations().get(0).getRecommendationDetails());
		assertEquals(
				"High", recommendations.get(0).getRecommendations().get(0).getRecommendationType());
	}

	@Test
	void getProjectWiseKpiRecommendationsHandlesEmptyKpiData() throws JsonProcessingException {
		KpiRequest kpiRequest = new KpiRequest();
		kpiRequest.setIds(new String[] {"project1"});
		kpiRequest.setSelectedMap(new HashMap<>());

		content = "{\"project_health_value\": 0.0, \"project_recommendations\": []}";
		when(aiGatewayClient.generate(any())).thenReturn(new ChatGenerationResponseDTO(content));
		when(parserStategy.parse(any())).thenReturn(new ObjectMapper().readTree(content));

		List<ProjectWiseKpiRecommendation> recommendations =
				aiKpiRecommendationProviderService.getProjectWiseKpiRecommendations(kpiRequest, "persona");

		assertNotNull(recommendations);
		assertEquals(1, recommendations.size());
		assertEquals("project1", recommendations.get(0).getProjectId());
		assertEquals(0.0, recommendations.get(0).getProjectScore());
		assertTrue(recommendations.get(0).getRecommendations().isEmpty());
	}
}
