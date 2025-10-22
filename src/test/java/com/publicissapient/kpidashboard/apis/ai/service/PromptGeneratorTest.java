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

package com.publicissapient.kpidashboard.apis.ai.service;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.ai.constants.PromptKeys;
import com.publicissapient.kpidashboard.apis.ai.model.PromptDetails;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;

@RunWith(MockitoJUnitRunner.class)
public class PromptGeneratorTest {

	@Mock private CacheService cacheService;

	private PromptGenerator promptGenerator;

	@Before
	public void setUp() {
		MockitoAnnotations.openMocks(this);

		PromptDetails kpiRecommendationPrompt =
				new PromptDetails(
						PromptKeys.KPI_RECOMMENDATION_PROMPT,
						"Prompt for KPI recommendation",
						"Recommendation format",
						Collections.singletonList("DummyField1"),
						"DummyField2",
						"DummyField3",
						Collections.singletonList("DummyField4"));

		PromptDetails kpiCorrelationAnalysisReport =
				new PromptDetails(
						PromptKeys.KPI_CORRELATION_ANALYSIS_REPORT,
						"Prompt for KPI correlation analysis",
						"Analysis format",
						Collections.singletonList("DummyField1"),
						"DummyField2",
						"DummyField3",
						Collections.singletonList("DummyField4"));

		PromptDetails kpidata =
				new PromptDetails(
						PromptKeys.KPI_DATA,
						"Prompt for kpi details",
						null,
						Arrays.asList(
								"kpi14:Percentage of Defect created and linked to stories in a sprint against the number of stories in the same sprint",
								"kpi82:percentage of tickets that passed QA with no return transition or any tagging to a specific configured status and no linkage of a defect"),
						null,
						null,
						null);

		PromptDetails kpiSearch =
				new PromptDetails(
						PromptKeys.KPI_SEARCH,
						"You are a helpful assistant with a system role",
						"Search for relevant kpis and provide the kpiid in ",
						Arrays.asList(
								"1. Given a list of KPIs in the format <kpiId>:<definition> : KPI_DATA and a user query"),
						"User Query: USER_QUERY",
						" comma-seperated string\"",
						Collections.singletonList("KPI_DATA"));

		when(cacheService.getPromptDetails())
				.thenReturn(
						Map.of(
								PromptKeys.KPI_RECOMMENDATION_PROMPT,
								kpiRecommendationPrompt,
								PromptKeys.KPI_CORRELATION_ANALYSIS_REPORT,
								kpiCorrelationAnalysisReport,
								PromptKeys.KPI_DATA,
								kpidata,
								PromptKeys.KPI_SEARCH,
								kpiSearch));
		promptGenerator = new PromptGenerator(cacheService);
	}

	@Test
	public void testGetKpiRecommendationPromptValid() throws Exception {

		// Inputs
		Map<String, Object> kpiDataByProject = Map.of("key1", "value1");
		String userRole = "Admin";

		// Execution
		String result = promptGenerator.getKpiRecommendationPrompt(kpiDataByProject, userRole);

		// Verification
		assertNotNull(result);
	}

	@Test
	public void testGetKpiSearchPrompt() throws Exception {
		// Execution
		String result = promptGenerator.getKpiSearchPrompt("test");

		// Verification
		assertNotNull(result);
	}
}
