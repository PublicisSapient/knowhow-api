package com.publicissapient.kpidashboard.apis.kpiintegration.service.impl;

import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.request.chat.ChatGenerationRequest;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.service.PromptGenerator;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.kpiintegration.service.KpiIntegrationServiceImpl;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.ProjectWiseKpiRecommendation;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class AiKpiRecommendationProviderServiceImplTest {

	@InjectMocks
	private AiKpiRecommendationProviderServiceImpl aiKpiRecommendationProviderService;

	@Mock
	private CustomApiConfig customApiConfig;

	@Mock
	private PromptGenerator promptGenerator;

	@Mock
	private AiGatewayClient aiGatewayClient;

	@Mock
	private KpiIntegrationServiceImpl kpiIntegrationService;

	private KpiElement kpiElement1;

	private KpiElement kpiElement2;

	@BeforeEach
	void setUp() throws IOException, EntityNotFoundException {
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
		Mockito.when(customApiConfig.getAiRecommendationKpiList()).thenReturn(Arrays.asList("KPI1", "KPI2"));
		Mockito.when(kpiIntegrationService.getKpiResponses(any())).thenReturn(Collections.singletonList(kpiElement1));
		Mockito.when(promptGenerator.getKpiRecommendationPrompt(any(), any())).thenReturn("Generated Prompt");
	}

	@Test
	void getProjectWiseKpiRecommendationsReturnsRecommendationsForValidInput() {
		KpiRequest kpiRequest = new KpiRequest();
		kpiRequest.setIds(new String[] { "project1" });
		kpiRequest.setSelectedMap(new HashMap<>());

		Mockito.when(aiGatewayClient.generate(any(ChatGenerationRequest.class))).thenReturn(new ChatGenerationResponseDTO(
				"{\"project_health_value\": 85.0, \"project_recommendations\": [{\"recommendation\": \"Improve code quality\", \"severity\": \"High\"}]}"));

		List<ProjectWiseKpiRecommendation> recommendations = aiKpiRecommendationProviderService
				.getProjectWiseKpiRecommendations(kpiRequest, "persona");

		assertNotNull(recommendations);
		assertEquals(1, recommendations.size());
		assertEquals("project1", recommendations.get(0).getProjectId());
		assertEquals(85.0, recommendations.get(0).getProjectScore());
		assertEquals(1, recommendations.get(0).getRecommendations().size());
		assertEquals("\"Improve code quality\"",
				recommendations.get(0).getRecommendations().get(0).getRecommendationDetails());
		assertEquals("\"High\"", recommendations.get(0).getRecommendations().get(0).getRecommendationType());
	}

	@Test
	void getProjectWiseKpiRecommendationsHandlesEmptyKpiData() {
		KpiRequest kpiRequest = new KpiRequest();
		kpiRequest.setIds(new String[] { "project1" });
		kpiRequest.setSelectedMap(new HashMap<>());

		Mockito.when(aiGatewayClient.generate(any())).thenReturn(
				new ChatGenerationResponseDTO("{\"project_health_value\": 0.0, \"project_recommendations\": []}"));

		List<ProjectWiseKpiRecommendation> recommendations = aiKpiRecommendationProviderService
				.getProjectWiseKpiRecommendations(kpiRequest, "persona");

		assertNotNull(recommendations);
		assertEquals(1, recommendations.size());
		assertEquals("project1", recommendations.get(0).getProjectId());
		assertEquals(0.0, recommendations.get(0).getProjectScore());
		assertTrue(recommendations.get(0).getRecommendations().isEmpty());
	}
}