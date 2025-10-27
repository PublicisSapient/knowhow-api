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

package com.publicissapient.kpidashboard.apis.kpiintegration.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.kpiintegration.service.impl.AiKpiRecommendationProviderServiceImpl;
import com.publicissapient.kpidashboard.apis.kpiintegration.service.impl.KpiRecommendationServiceImpl;
import com.publicissapient.kpidashboard.apis.kpiintegration.service.impl.RnrEngineRecommendationProviderServiceImpl;
import com.publicissapient.kpidashboard.apis.model.KpiRecommendationRequestDTO;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.ProjectWiseKpiRecommendation;
import com.publicissapient.kpidashboard.apis.model.ServiceResponse;

@ExtendWith(MockitoExtension.class)
class KpiRecommendationServiceImplTest {

	@Mock private RnrEngineRecommendationProviderServiceImpl rnrEngineRecommendationProviderService;

	@Mock private AiKpiRecommendationProviderServiceImpl aiKpiRecommendationProviderService;

	@Mock private CustomApiConfig customApiConfig;

	@InjectMocks private KpiRecommendationServiceImpl kpiRecommendationService;

	@Test
	void
			getProjectWiseKpiRecommendation_returnsAiRecommendations_whenAiRecommendationListIsNotEmptyAndRecommendationForIsProvided() {
		KpiRecommendationRequestDTO requestDTO = new KpiRecommendationRequestDTO();
		requestDTO.setRecommendationFor("someRecommendation");
		List<ProjectWiseKpiRecommendation> recommendations =
				List.of(new ProjectWiseKpiRecommendation());

		Mockito.when(customApiConfig.getAiRecommendationKpiList()).thenReturn(List.of("kpi1", "kpi2"));
		Mockito.when(
						aiKpiRecommendationProviderService.getProjectWiseKpiRecommendations(
								any(KpiRequest.class), Mockito.eq("someRecommendation")))
				.thenReturn(recommendations);

		ServiceResponse response = kpiRecommendationService.getProjectWiseKpiRecommendation(requestDTO);

		assertTrue(response.getSuccess());
		assertEquals("Successfully Fetched Recommendations", response.getMessage());
		assertEquals(recommendations, response.getData());
	}

	@Test
	void
			getProjectWiseKpiRecommendation_returnsError_whenAiRecommendationListIsNotEmptyAndRecommendationForIsNull() {
		KpiRecommendationRequestDTO requestDTO = new KpiRecommendationRequestDTO();

		Mockito.when(customApiConfig.getAiRecommendationKpiList()).thenReturn(List.of("kpi1", "kpi2"));

		ServiceResponse response = kpiRecommendationService.getProjectWiseKpiRecommendation(requestDTO);

		assertFalse(response.getSuccess());
		assertEquals("AiRecommendation", response.getMessage());
		assertNull(response.getData());
	}

	@Test
	void getProjectWiseKpiRecommendation_returnsRnrRecommendations_whenAiRecommendationListIsEmpty() {
		KpiRecommendationRequestDTO requestDTO = new KpiRecommendationRequestDTO();
		List<ProjectWiseKpiRecommendation> recommendations =
				List.of(new ProjectWiseKpiRecommendation());

		Mockito.when(customApiConfig.getAiRecommendationKpiList()).thenReturn(List.of());
		Mockito.when(
						rnrEngineRecommendationProviderService.getProjectWiseKpiRecommendations(
								any(KpiRequest.class), any()))
				.thenReturn(recommendations);

		ServiceResponse response = kpiRecommendationService.getProjectWiseKpiRecommendation(requestDTO);

		assertTrue(response.getSuccess());
		assertEquals("Successfully Fetched Recommendations", response.getMessage());
		assertEquals(recommendations, response.getData());
	}
}
