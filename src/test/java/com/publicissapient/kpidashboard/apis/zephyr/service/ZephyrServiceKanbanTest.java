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

package com.publicissapient.kpidashboard.apis.zephyr.service;

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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.jupiter.api.Assertions;
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
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyDataKanban;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.apis.zephyr.factory.ZephyrKPIServiceFactory;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;

/**
 * @author pkum34
 */
@RunWith(MockitoJUnitRunner.class)
public class ZephyrServiceKanbanTest {
	public Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
	public Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();

	private static final String TEST_ZEPHYR = "TEST_ZEPHYR";

	@Mock private FilterHelperService filterHelperService;
	@Mock private KpiHelperService kpiHelperService;
	@Mock private CacheService cacheService;
	@Mock private UserAuthorizedProjectsService authorizedProjectsService;
	@Mock private TestService service;

	@InjectMocks private ZephyrServiceKanban zephyrService;

	private final KpiRequest kpiRequest = new KpiRequest();

	private List<HierarchyLevel> hierarchyLevels = new ArrayList<>();
	private List<AccountHierarchyDataKanban> accountHierarchyKanbanDataList = new ArrayList<>();

	@Test
	public void sonarViolationsTestProcess() throws Exception {
		try (MockedStatic<ApiKeyAuthenticationService> apiKeyAuthenticationServiceMockedStatic =
						mockStatic(ApiKeyAuthenticationService.class);
				MockedStatic<KPIHelperUtil> kpiHelperUtilMockedStatic = mockStatic(KPIHelperUtil.class)) {
			initiateRequiredServicesAndFactories();

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
			kpiRequest.setLabel("PROJECT");
			AccountHierarchyDataKanban accountHierarchyDataKanban = new AccountHierarchyDataKanban();
			Node testNode = new Node();
			testNode.setGroupName("group name");
			accountHierarchyDataKanban.setNode(List.of(testNode));

			when(filterHelperService.getHierarchyIdLevelMap(true)).thenReturn(Map.of("project", 1));
			when(filterHelperService.getFirstHierarchyLevel()).thenReturn("firstHierarchyLevel");
			when(filterHelperService.getHierarchyLevelId(anyInt(), anyString(), eq(true)))
					.thenReturn("test level id");
			when(authorizedProjectsService.filterKanbanProjects(anyList()))
					.thenReturn(List.of(accountHierarchyDataKanban));
			when(filterHelperService.getFilteredBuildsKanban(any(KpiRequest.class), anyString()))
					.thenReturn(List.of(accountHierarchyDataKanban));
			when(service.getKpiData(any(), any(), any())).thenReturn(kpiRequest.getKpiList().get(0));

			List<KpiElement> resultList = zephyrService.process(kpiRequest);
			assertThat(
					"Kpi Name :", resultList.get(0).getResponseCode(), equalTo(CommonConstant.KPI_PASSED));
		}
	}

	@Test
	public void sonarViolationsTestProcessThrowApplication() throws Exception {
		try (MockedStatic<ApiKeyAuthenticationService> apiKeyAuthenticationServiceMockedStatic =
						mockStatic(ApiKeyAuthenticationService.class);
				MockedStatic<KPIHelperUtil> kpiHelperUtilMockedStatic = mockStatic(KPIHelperUtil.class)) {
			initiateRequiredServicesAndFactories();

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
			kpiRequest.setLabel("PROJECT");
			AccountHierarchyDataKanban accountHierarchyDataKanban = new AccountHierarchyDataKanban();
			Node testNode = new Node();
			testNode.setGroupName("group name");
			accountHierarchyDataKanban.setNode(List.of(testNode));

			when(filterHelperService.getHierarchyIdLevelMap(true)).thenReturn(Map.of("project", 1));
			when(filterHelperService.getFirstHierarchyLevel()).thenReturn("firstHierarchyLevel");
			when(filterHelperService.getHierarchyLevelId(anyInt(), anyString(), eq(true)))
					.thenReturn("test level id");
			when(authorizedProjectsService.filterKanbanProjects(anyList()))
					.thenReturn(List.of(accountHierarchyDataKanban));
			when(filterHelperService.getFilteredBuildsKanban(any(KpiRequest.class), anyString()))
					.thenReturn(List.of(accountHierarchyDataKanban));
			when(service.getKpiData(any(), any(), any())).thenThrow(ApplicationException.class);

			List<KpiElement> resultList = zephyrService.process(kpiRequest);
			assertThat(
					"Kpi Name :", resultList.get(0).getResponseCode(), equalTo(CommonConstant.KPI_FAILED));
		}
	}

	@Test
	public void sonarViolationsTestProcessThrowNullPointer() throws Exception {
		try (MockedStatic<ApiKeyAuthenticationService> apiKeyAuthenticationServiceMockedStatic =
						mockStatic(ApiKeyAuthenticationService.class);
				MockedStatic<KPIHelperUtil> kpiHelperUtilMockedStatic = mockStatic(KPIHelperUtil.class)) {
			initiateRequiredServicesAndFactories();

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
			kpiRequest.setLabel("PROJECT");
			AccountHierarchyDataKanban accountHierarchyDataKanban = new AccountHierarchyDataKanban();
			Node testNode = new Node();
			testNode.setGroupName("group name");
			accountHierarchyDataKanban.setNode(List.of(testNode));

			when(filterHelperService.getHierarchyIdLevelMap(true)).thenReturn(Map.of("project", 1));
			when(filterHelperService.getFirstHierarchyLevel()).thenReturn("firstHierarchyLevel");
			when(filterHelperService.getHierarchyLevelId(anyInt(), anyString(), eq(true)))
					.thenReturn("test level id");
			when(authorizedProjectsService.filterKanbanProjects(anyList()))
					.thenReturn(List.of(accountHierarchyDataKanban));
			when(filterHelperService.getFilteredBuildsKanban(any(KpiRequest.class), anyString()))
					.thenReturn(List.of(accountHierarchyDataKanban));
			when(service.getKpiData(any(), any(), any())).thenThrow(NullPointerException.class);

			List<KpiElement> resultList = zephyrService.process(kpiRequest);
			assertThat(
					"Kpi Name :", resultList.get(0).getResponseCode(), equalTo(CommonConstant.KPI_FAILED));
		}
	}

	@Test
	public void when_KanbanKpiRequestIsReceived_Expect_RequestIsPopulatedAccordingly() {
		KpiRequest kpiRequest1 = new KpiRequest();
		kpiRequest1.setIds(new String[] {"10"});
		kpiRequest1.setSelectedMap(Map.of("date", List.of("5")));

		ReflectionTestUtils.invokeMethod(zephyrService, "populateKanbanKpiRequest", kpiRequest1);

		Assertions.assertEquals(10, kpiRequest1.getKanbanXaxisDataPoints());
		Assertions.assertEquals(CommonConstant.DAYS, kpiRequest1.getDuration());

		kpiRequest1.setSelectedMap(Map.of("date", List.of("Random duration")));

		ReflectionTestUtils.invokeMethod(zephyrService, "populateKanbanKpiRequest", kpiRequest1);

		Assertions.assertEquals("RANDOM DURATION", kpiRequest1.getDuration());
	}

	@Test
	public void
			when_RequesterShouldHaveFullAccessOnRequestedResource_Expect_GetAuthorizedFilteredListReturnsRequiredResource() {
		updateKpiRequest("Excel-Sonar", 3);
		List<AccountHierarchyDataKanban> expectedAccountHierarchyDataKanbanList = new ArrayList<>();
		AccountHierarchyDataKanban accountHierarchyDataKanban = new AccountHierarchyDataKanban();
		accountHierarchyDataKanban.setLabelName("testLabel");
		expectedAccountHierarchyDataKanbanList.add(accountHierarchyDataKanban);

		try (MockedStatic<ApiKeyAuthenticationService> apiKeyAuthenticationServiceMockedStatic =
				mockStatic(ApiKeyAuthenticationService.class)) {
			// Case 1 -> user is super admin and request is not through api key
			when(authorizedProjectsService.ifSuperAdminUser()).thenReturn(true);
			apiKeyAuthenticationServiceMockedStatic
					.when(ApiKeyAuthenticationService::isApiKeyRequest)
					.thenReturn(false);

			List<AccountHierarchyDataKanban> resultedAccountHierarchyDataKanbanList =
					ReflectionTestUtils.invokeMethod(
							zephyrService,
							"getAuthorizedFilteredList",
							kpiRequest,
							expectedAccountHierarchyDataKanbanList);

			assertEquals(expectedAccountHierarchyDataKanbanList, resultedAccountHierarchyDataKanbanList);

			// Case 2 -> user is not super admin and request is through api key
			when(authorizedProjectsService.ifSuperAdminUser()).thenReturn(false);
			apiKeyAuthenticationServiceMockedStatic
					.when(ApiKeyAuthenticationService::isApiKeyRequest)
					.thenReturn(true);

			resultedAccountHierarchyDataKanbanList =
					ReflectionTestUtils.invokeMethod(
							zephyrService,
							"getAuthorizedFilteredList",
							kpiRequest,
							expectedAccountHierarchyDataKanbanList);

			assertEquals(expectedAccountHierarchyDataKanbanList, resultedAccountHierarchyDataKanbanList);
		}
	}

	@Test
	public void
			when_RequesterIsNotSuperAdminOrRequestIsNotMadeWithApiKey_Expect_GetAuthorizedFilteredListPerformsResourceAccessFiltering() {
		updateKpiRequest("Excel-Sonar", 3);
		List<AccountHierarchyDataKanban> testAccountHierarchyDataKanbanList = new ArrayList<>();
		AccountHierarchyDataKanban accountHierarchyDataKanban = new AccountHierarchyDataKanban();
		accountHierarchyDataKanban.setLabelName("testLabel");
		testAccountHierarchyDataKanbanList.add(accountHierarchyDataKanban);

		List<AccountHierarchyDataKanban> expectedAccountHierarchyDataKanbanList = new ArrayList<>();
		AccountHierarchyDataKanban accountHierarchyDataKanban1 = new AccountHierarchyDataKanban();
		accountHierarchyDataKanban1.setLabelName("expectedLabel");
		expectedAccountHierarchyDataKanbanList.add(accountHierarchyDataKanban);

		try (MockedStatic<ApiKeyAuthenticationService> apiKeyAuthenticationServiceMockedStatic =
				mockStatic(ApiKeyAuthenticationService.class)) {
			when(authorizedProjectsService.ifSuperAdminUser()).thenReturn(false);
			when(authorizedProjectsService.filterKanbanProjects(anyList()))
					.thenReturn(expectedAccountHierarchyDataKanbanList);
			apiKeyAuthenticationServiceMockedStatic
					.when(ApiKeyAuthenticationService::isApiKeyRequest)
					.thenReturn(false);

			List<AccountHierarchyDataKanban> resultedAccountHierarchyDataKanbanList =
					ReflectionTestUtils.invokeMethod(
							zephyrService,
							"getAuthorizedFilteredList",
							kpiRequest,
							testAccountHierarchyDataKanbanList);

			assertEquals(expectedAccountHierarchyDataKanbanList, resultedAccountHierarchyDataKanbanList);
		}
	}

	@Test
	public void when_RequestIsMadeWithApiKey_Expect_NoCachedDataWillBeUsed()
			throws EntityNotFoundException, ApplicationException {
		try (MockedStatic<ApiKeyAuthenticationService> apiKeyAuthenticationServiceMockedStatic =
						mockStatic(ApiKeyAuthenticationService.class);
				MockedStatic<KPIHelperUtil> kpiHelperUtilMockedStatic = mockStatic(KPIHelperUtil.class)) {
			initiateRequiredServicesAndFactories();

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
			kpiRequest.setLabel("PROJECT");
			AccountHierarchyDataKanban accountHierarchyDataKanban = new AccountHierarchyDataKanban();
			Node testNode = new Node();
			testNode.setGroupName("group name");
			accountHierarchyDataKanban.setNode(List.of(testNode));
			when(filterHelperService.getHierarchyLevelId(anyInt(), anyString(), eq(true)))
					.thenReturn("test level id");
			when(filterHelperService.getFilteredBuildsKanban(any(KpiRequest.class), anyString()))
					.thenReturn(List.of(accountHierarchyDataKanban));

			zephyrService.process(kpiRequest);
			verifyNoInteractions(cacheService);
		}
	}

	private void updateKpiRequest(String source, int level) {
		List<KpiElement> kpiList = new ArrayList<>();
		addKpiElement(kpiList, TEST_ZEPHYR, TEST_ZEPHYR, "TechDebt", "", source);
		kpiRequest.setLevel(level);
		kpiRequest.setIds(new String[] {"Scrum Project_6335363749794a18e8a4479b"});
		kpiRequest.setKpiList(kpiList);
		kpiRequest.setRequestTrackerId();
		Map<String, List<String>> selectedMap = new HashMap<>();
		selectedMap.put("Project", Arrays.asList("Kanban Project_6335368249794a18e8a4479f"));
		selectedMap.put(CommonConstant.DATE, Arrays.asList("10"));
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

	private TreeAggregatorDetail createTreeAggregatorDetail() {
		TreeAggregatorDetail treeAggregatorDetail = new TreeAggregatorDetail();
		treeAggregatorDetail.setMapOfListOfProjectNodes(
				Map.of("project", List.of(new Node(), new Node())));
		return treeAggregatorDetail;
	}

	private void initiateRequiredServicesAndFactories() throws ApplicationException {
		List<ZephyrKPIService<?, ?, ?>> mockServices = List.of(service);
		ZephyrKPIServiceFactory zephyrKPIServiceFactory =
				ZephyrKPIServiceFactory.builder().services(mockServices).build();
		doReturn(TEST_ZEPHYR).when(service).getQualifierType();
		doReturn(new KpiElement()).when(service).getKpiData(any(), any(), any());
		zephyrKPIServiceFactory.initMyServiceCache();
	}
}
