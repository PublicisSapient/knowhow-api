/*
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.executive.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.bitbucket.service.BitBucketServiceKanbanR;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.jenkins.service.JenkinsServiceKanbanR;
import com.publicissapient.kpidashboard.apis.jira.service.JiraServiceKanbanR;
import com.publicissapient.kpidashboard.apis.kpiintegration.service.KpiIntegrationServiceImpl;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.sonar.service.SonarServiceKanbanR;
import com.publicissapient.kpidashboard.apis.zephyr.service.ZephyrServiceKanban;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;

@RunWith(MockitoJUnitRunner.class)
public class KanbanKpiMaturityTest {

	@Mock
	private KpiIntegrationServiceImpl kpiIntegrationServiceImpl;

	@Mock
	private JiraServiceKanbanR jiraService;

	@Mock
	private SonarServiceKanbanR sonarService;

	@Mock
	private ZephyrServiceKanban zephyrService;

	@Mock
	private JenkinsServiceKanbanR jenkinsServiceR;

	@Mock
	private BitBucketServiceKanbanR bitBucketServiceR;

	@InjectMocks
	private KanbanKpiMaturity kanbanKpiMaturity;

	private KpiRequest kpiRequest;
	private KpiMaster dummyKpiMaster;
	private KpiElement dummyKpiElement;

	@Before
	public void setup() {
		kpiRequest = new KpiRequest();
		dummyKpiMaster = new KpiMaster();
		dummyKpiElement = new KpiElement();
	}

	@Test
	public void testGetKpiElements_AllSources() throws EntityNotFoundException {
		when(kpiIntegrationServiceImpl.mapKpiMasterToKpiElement(any())).thenReturn(dummyKpiElement);

		when(jiraService.process(any())).thenReturn(List.of(dummyKpiElement));
		when(sonarService.process(any())).thenReturn(List.of(dummyKpiElement));
		when(zephyrService.process(any())).thenReturn(List.of(dummyKpiElement));
		when(jenkinsServiceR.process(any())).thenReturn(List.of(dummyKpiElement));
		when(bitBucketServiceR.process(any())).thenReturn(List.of(dummyKpiElement));

		Map<String, List<KpiMaster>> sourceWiseKpiList = Map.of("Jira", List.of(dummyKpiMaster), "Sonar",
				List.of(dummyKpiMaster), "Zypher", List.of(dummyKpiMaster), "Jenkins", List.of(dummyKpiMaster),
				"BitBucket", List.of(dummyKpiMaster));

		List<KpiElement> result = kanbanKpiMaturity.getKpiElements(kpiRequest, sourceWiseKpiList);

		assertEquals(5, result.size());
		verify(jiraService).process(any());
		verify(sonarService).process(any());
		verify(zephyrService).process(any());
		verify(jenkinsServiceR).process(any());
		verify(bitBucketServiceR).process(any());
		verify(kpiIntegrationServiceImpl).calculateOverallMaturity(anyList());
	}

	@Test
	public void testGetKpiElements_DefaultSource() {
		Map<String, List<KpiMaster>> sourceWiseKpiList = Map.of("UnknownSource", List.of(dummyKpiMaster));

		List<KpiElement> result = kanbanKpiMaturity.getKpiElements(kpiRequest, sourceWiseKpiList);

		assertTrue(result.isEmpty());
		verifyNoInteractions(jiraService, sonarService, zephyrService, jenkinsServiceR, bitBucketServiceR);
		verify(kpiIntegrationServiceImpl).calculateOverallMaturity(anyList());
	}

	@Test
	public void testGetKpiElements_ExceptionHandling() throws EntityNotFoundException {
		when(kpiIntegrationServiceImpl.mapKpiMasterToKpiElement(any())).thenReturn(dummyKpiElement);
		when(jiraService.process(any())).thenThrow(new RuntimeException("Test exception"));

		Map<String, List<KpiMaster>> sourceWiseKpiList = Map.of("Jira", List.of(dummyKpiMaster));

		List<KpiElement> result = kanbanKpiMaturity.getKpiElements(kpiRequest, sourceWiseKpiList);

		assertTrue(result.isEmpty());
		verify(jiraService).process(any());
		verify(kpiIntegrationServiceImpl).calculateOverallMaturity(anyList());
	}

	@Test
	public void testGetKpiElements_MixedSourcesWithUnknownAndException() throws EntityNotFoundException {
		when(kpiIntegrationServiceImpl.mapKpiMasterToKpiElement(any())).thenReturn(dummyKpiElement);
		when(jiraService.process(any())).thenReturn(List.of(dummyKpiElement));
		when(sonarService.process(any())).thenThrow(new RuntimeException("Sonar failed"));

		Map<String, List<KpiMaster>> sourceWiseKpiList = Map.of("Jira", List.of(dummyKpiMaster), "Sonar",
				List.of(dummyKpiMaster), "Unknown", List.of(dummyKpiMaster));

		List<KpiElement> result = kanbanKpiMaturity.getKpiElements(kpiRequest, sourceWiseKpiList);

		// Jira processed successfully, others fail or unknown
		assertEquals(1, result.size());
		verify(jiraService).process(any());
		verify(sonarService).process(any());
		verifyNoInteractions(zephyrService, jenkinsServiceR, bitBucketServiceR);
		verify(kpiIntegrationServiceImpl).calculateOverallMaturity(anyList());
	}
}