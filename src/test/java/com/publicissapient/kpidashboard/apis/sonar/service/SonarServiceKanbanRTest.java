/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.sonar.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.apis.abac.UserAuthorizedProjectsService;
import com.publicissapient.kpidashboard.apis.auth.apikey.ApiKeyAuthenticationService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyDataKanban;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.sonar.factory.SonarKPIServiceFactory;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;

@RunWith(MockitoJUnitRunner.class)
public class SonarServiceKanbanRTest {
	public Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
	public Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();

	private static final String TEST_SONAR = "TEST_SONAR";

	@Mock private FilterHelperService filterHelperService;
	@Mock private KpiHelperService kpiHelperService;
	@Mock private CacheService cacheService;
	@Mock private TestService service;
	@Mock private UserAuthorizedProjectsService authorizedProjectsService;

	@InjectMocks private SonarServiceKanbanR sonarService;

	private final KpiRequest kpiRequest = new KpiRequest();

	private final List<HierarchyLevel> hierarchyLevels = new ArrayList<>();
	private final List<AccountHierarchyDataKanban> accountHierarchyKanbanDataList = new ArrayList<>();

	@Test
	public void sonarViolationsTestProcess() throws Exception {
		try (MockedStatic<ApiKeyAuthenticationService> apiKeyAuthenticationServiceMockedStatic =
						mockStatic(ApiKeyAuthenticationService.class);
				MockedStatic<KPIHelperUtil> kpiHelperUtilMockedStatic = mockStatic(KPIHelperUtil.class)) {
			initiateRequiredServicesAndFactories();
			initialization();

			kpiHelperUtilMockedStatic
					.when(
							() ->
									KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
											any(), eq(null), anyList(), anyString(), anyInt()))
					.thenReturn(createTreeAggregatorDetail());
			apiKeyAuthenticationServiceMockedStatic
					.when(ApiKeyAuthenticationService::isApiKeyRequest)
					.thenReturn(false);
			updateKpiRequest("Excel-Sonar", 3);
			AccountHierarchyDataKanban accountHierarchyDataKanban = new AccountHierarchyDataKanban();
			Node testNode = new Node();
			testNode.setGroupName("group name");
			accountHierarchyDataKanban.setNode(List.of(testNode));
			when(filterHelperService.getHierarchyIdLevelMap(true)).thenReturn(Map.of("project", 1));
			when(filterHelperService.getFirstHierarchyLevel()).thenReturn("firstHierarchyLevel");
			when(authorizedProjectsService.filterKanbanProjects(anyList()))
					.thenReturn(List.of(accountHierarchyDataKanban));
			when(service.getKpiData(any(), any(), any())).thenReturn(kpiRequest.getKpiList().get(0));

			List<KpiElement> resultList = sonarService.process(kpiRequest);
			assertThat(
					"Kpi Name :", resultList.get(0).getResponseCode(), equalTo(CommonConstant.KPI_PASSED));
		}
	}

	@Test
	public void sonarViolationsTestProcess_ApplicationException() throws Exception {
		try (MockedStatic<ApiKeyAuthenticationService> apiKeyAuthenticationServiceMockedStatic =
						mockStatic(ApiKeyAuthenticationService.class);
				MockedStatic<KPIHelperUtil> kpiHelperUtilMockedStatic = mockStatic(KPIHelperUtil.class)) {
			initiateRequiredServicesAndFactories();
			initialization();

			kpiHelperUtilMockedStatic
					.when(
							() ->
									KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
											any(), eq(null), anyList(), anyString(), anyInt()))
					.thenReturn(createTreeAggregatorDetail());
			apiKeyAuthenticationServiceMockedStatic
					.when(ApiKeyAuthenticationService::isApiKeyRequest)
					.thenReturn(false);
			updateKpiRequest("Excel-Sonar", 3);
			AccountHierarchyDataKanban accountHierarchyDataKanban = new AccountHierarchyDataKanban();
			Node testNode = new Node();
			testNode.setGroupName("group name");
			accountHierarchyDataKanban.setNode(List.of(testNode));
			when(filterHelperService.getHierarchyIdLevelMap(true)).thenReturn(Map.of("project", 1));
			when(filterHelperService.getFirstHierarchyLevel()).thenReturn("firstHierarchyLevel");
			when(authorizedProjectsService.filterKanbanProjects(anyList()))
					.thenReturn(List.of(accountHierarchyDataKanban));
			when(service.getKpiData(any(), any(), any())).thenThrow(ApplicationException.class);

			List<KpiElement> resultList = sonarService.process(kpiRequest);
			assertThat(
					"Kpi Name :", resultList.get(0).getResponseCode(), equalTo(CommonConstant.KPI_FAILED));
		}
	}

	@Test
	public void sonarViolationsTestProcess_NullPointer() throws Exception {
		try (MockedStatic<ApiKeyAuthenticationService> apiKeyAuthenticationServiceMockedStatic =
						mockStatic(ApiKeyAuthenticationService.class);
				MockedStatic<KPIHelperUtil> kpiHelperUtilMockedStatic = mockStatic(KPIHelperUtil.class)) {
			initiateRequiredServicesAndFactories();
			initialization();

			kpiHelperUtilMockedStatic
					.when(
							() ->
									KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
											any(), eq(null), anyList(), anyString(), anyInt()))
					.thenReturn(createTreeAggregatorDetail());
			apiKeyAuthenticationServiceMockedStatic
					.when(ApiKeyAuthenticationService::isApiKeyRequest)
					.thenReturn(false);
			updateKpiRequest("Excel-Sonar", 3);
			AccountHierarchyDataKanban accountHierarchyDataKanban = new AccountHierarchyDataKanban();
			Node testNode = new Node();
			testNode.setGroupName("group name");
			accountHierarchyDataKanban.setNode(List.of(testNode));
			when(filterHelperService.getHierarchyIdLevelMap(true)).thenReturn(Map.of("project", 1));
			when(filterHelperService.getFirstHierarchyLevel()).thenReturn("firstHierarchyLevel");
			when(authorizedProjectsService.filterKanbanProjects(anyList()))
					.thenReturn(List.of(accountHierarchyDataKanban));
			when(service.getKpiData(any(), any(), any())).thenThrow(NullPointerException.class);

			List<KpiElement> resultList = sonarService.process(kpiRequest);
			assertThat(
					"Kpi Name :", resultList.get(0).getResponseCode(), equalTo(CommonConstant.KPI_FAILED));
		}
	}

	@Test
	public void sonarViolationsTestProcessCachedData() {
		updateKpiRequest("Excel-Sonar", 3);
		List<KpiElement> resultList = sonarService.process(kpiRequest);
		assertThat("Kpi list :", resultList.size(), equalTo(0));
	}

	@Test
	public void when_KanbanKpiRequestIsReceived_Expect_RequestIsPopulatedAccordingly() {
		KpiRequest kpiRequest1 = new KpiRequest();
		kpiRequest1.setIds(new String[] {"10"});
		kpiRequest1.setSelectedMap(Map.of("date", List.of("5")));

		ReflectionTestUtils.invokeMethod(sonarService, "populateKanbanKpiRequest", kpiRequest1);

		assertEquals(10, kpiRequest1.getKanbanXaxisDataPoints());
		assertEquals(CommonConstant.DAYS, kpiRequest1.getDuration());

		kpiRequest1.setSelectedMap(Map.of("date", List.of("Random duration")));

		ReflectionTestUtils.invokeMethod(sonarService, "populateKanbanKpiRequest", kpiRequest1);

		assertEquals("RANDOM DURATION", kpiRequest1.getDuration());
	}

	@Test
	public void when_RequestIsMadeWithApiKey_Expect_NoCachedDataWillBeUsed() throws Exception {
		try (MockedStatic<ApiKeyAuthenticationService> apiKeyAuthenticationServiceMockedStatic =
						mockStatic(ApiKeyAuthenticationService.class);
				MockedStatic<KPIHelperUtil> kpiHelperUtilMockedStatic = mockStatic(KPIHelperUtil.class)) {
			initiateRequiredServicesAndFactories();
			initialization();

			kpiHelperUtilMockedStatic
					.when(
							() ->
									KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
											any(), eq(null), anyList(), anyString(), anyInt()))
					.thenReturn(createTreeAggregatorDetail());
			apiKeyAuthenticationServiceMockedStatic
					.when(ApiKeyAuthenticationService::isApiKeyRequest)
					.thenReturn(true);
			updateKpiRequest("Excel-Sonar", 3);
			AccountHierarchyDataKanban accountHierarchyDataKanban = new AccountHierarchyDataKanban();
			Node testNode = new Node();
			testNode.setGroupName("group name");
			accountHierarchyDataKanban.setNode(List.of(testNode));
			when(filterHelperService.getFirstHierarchyLevel()).thenReturn("firstHierarchyLevel");
			when(service.getKpiData(any(), any(), any())).thenReturn(kpiRequest.getKpiList().get(0));

			sonarService.process(kpiRequest);
			verifyNoInteractions(cacheService);
		}
	}

	private void updateKpiRequest(String source, int level) {
		List<KpiElement> kpiList = new ArrayList<>();
		addKpiElement(kpiList, TEST_SONAR, TEST_SONAR, "TechDebt", "", source);
		kpiRequest.setLevel(level);
		kpiRequest.setIds(new String[] {"Kanban Project_6335368249794a18e8a4479f"});
		kpiRequest.setKpiList(kpiList);
		kpiRequest.setRequestTrackerId();
		Map<String, List<String>> selectedMap = new HashMap<>();
		selectedMap.put("Project", List.of("Kanban Project_6335368249794a18e8a4479f"));
		selectedMap.put(CommonConstant.DATE, List.of("10"));
		kpiRequest.setSelectedMap(selectedMap);
	}

	private void addKpiElement(
			List<KpiElement> kpiList,
			String kpiId,
			String kpiName,
			String category,
			String kpiUnit,
			String source) {
		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId(kpiId);
		kpiElement.setKpiName(kpiName);
		kpiElement.setKpiCategory(category);
		kpiElement.setKpiUnit(kpiUnit);
		kpiElement.setKpiSource(source);
		kpiElement.setGroupId(1);

		kpiElement.setMaxValue("500");
		kpiElement.setChartType("gaugeChart");
		kpiList.add(kpiElement);
	}

	private void initialization() {
		String[] exampleStringList = {"exampleElement", "exampleElement"};
		when(authorizedProjectsService.getKanbanProjectKey(accountHierarchyKanbanDataList, kpiRequest))
				.thenReturn(exampleStringList);
		when(filterHelperService.getFirstHierarchyLevel()).thenReturn("hierarchyLevelOne");
		Map<String, Integer> map = new HashMap<>();
		Map<String, HierarchyLevel> hierarchyMap =
				hierarchyLevels.stream()
						.collect(Collectors.toMap(HierarchyLevel::getHierarchyLevelId, x -> x));
		hierarchyMap.forEach((key, value) -> map.put(key, value.getLevel()));
		when(filterHelperService.getHierarchyIdLevelMap(false)).thenReturn(map);
		when(authorizedProjectsService.filterKanbanProjects(accountHierarchyKanbanDataList))
				.thenReturn(accountHierarchyKanbanDataList);
	}

	private TreeAggregatorDetail createTreeAggregatorDetail() {
		TreeAggregatorDetail treeAggregatorDetail = new TreeAggregatorDetail();
		treeAggregatorDetail.setMapOfListOfProjectNodes(
				Map.of("project", List.of(new Node(), new Node())));
		return treeAggregatorDetail;
	}

	private void initiateRequiredServicesAndFactories() throws ApplicationException {
		List<SonarKPIService<?, ?, ?>> mockServices = List.of(service);
		SonarKPIServiceFactory sonarKPIServiceFactory =
				SonarKPIServiceFactory.builder().services(mockServices).build();
		doReturn(TEST_SONAR).when(service).getQualifierType();
		doReturn(new KpiElement()).when(service).getKpiData(any(), any(), any());

		sonarKPIServiceFactory.initMyServiceCache();
	}
}
