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

package com.publicissapient.kpidashboard.apis.jira.service;

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
import java.util.Objects;

import org.bson.types.ObjectId;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockedStatic.Verification;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.apis.abac.UserAuthorizedProjectsService;
import com.publicissapient.kpidashboard.apis.auth.apikey.ApiKeyAuthenticationService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.data.KpiRequestFactory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.jira.factory.JiraKPIServiceFactory;
import com.publicissapient.kpidashboard.apis.jira.kanban.service.NetOpenTicketCountByRCAServiceImpl;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyDataKanban;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;

/**
 * @author pkum34
 */
@RunWith(MockitoJUnitRunner.class)
public class JiraServiceKanbanRTest {

	public Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
	public Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();

	private static final String TEST_JIRA = "TEST_JIRA";

	private final Map<String, JiraKPIService<?, ?, ?>> jiraServiceCache = new HashMap<>();

	@Mock private FilterHelperService filterHelperService;
	@Mock private KpiHelperService kpiHelperService;
	@Mock private UserAuthorizedProjectsService authorizedProjectsService;
	@Mock private NetOpenTicketCountByRCAServiceImpl rcaServiceImpl;
	@Mock private CacheService cacheService;
	@Mock private TestService service;

	@InjectMocks private JiraServiceKanbanR jiraServiceKanbanR;

	private List<AccountHierarchyDataKanban> accountHierarchyDataList = new ArrayList<>();

	private KpiRequestFactory kpiRequestFactory;

	@Test
	public void when_KpiRequestIsValid_Expect_RequestIsProcessedAsExpected() throws Exception {
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
			KpiRequest kpiRequest = createKPIRequest();
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

			List<KpiElement> resultList = jiraServiceKanbanR.process(kpiRequest);
			assertThat(
					"Kpi Name :", resultList.get(0).getResponseCode(), equalTo(CommonConstant.KPI_PASSED));
		}
	}

	@Test
	public void TestProcess1() throws Exception {
		initiateRequiredServicesAndFactories();

		KpiRequest kpiRequest = kpiRequestFactory.findKpiRequest("kpi14");
		kpiRequest.setLabel("PROJECT");

		@SuppressWarnings("rawtypes")
		JiraKPIService mcokAbstract = rcaServiceImpl;
		jiraServiceCache.put(KPICode.NET_OPEN_TICKET_COUNT_BY_STATUS.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.TICKET_VELOCITY.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.TICKET_COUNT_BY_PRIORITY.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.NET_OPEN_TICKET_COUNT_BY_RCA.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.TICKET_OPEN_VS_CLOSE_BY_PRIORITY.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.LEAD_TIME_KANBAN.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.TICKET_OPEN_VS_CLOSED_RATE_BY_TYPE.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.KANBAN_JIRA_TECH_DEBT.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.TEAM_CAPACITY.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.TICKET_THROUGHPUT.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.WIP_VS_CLOSED.name(), mcokAbstract);

		try (MockedStatic<JiraKPIServiceFactory> utilities =
				Mockito.mockStatic(JiraKPIServiceFactory.class)) {
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(
											KPICode.NET_OPEN_TICKET_COUNT_BY_STATUS.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(KPICode.TICKET_VELOCITY.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(KPICode.TICKET_COUNT_BY_PRIORITY.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(
											KPICode.NET_OPEN_TICKET_COUNT_BY_RCA.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(
											KPICode.TICKET_OPEN_VS_CLOSE_BY_PRIORITY.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(KPICode.LEAD_TIME_KANBAN.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(
											KPICode.TICKET_OPEN_VS_CLOSED_RATE_BY_TYPE.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(KPICode.KANBAN_JIRA_TECH_DEBT.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification) JiraKPIServiceFactory.getJiraKPIService(KPICode.TEAM_CAPACITY.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(KPICode.TICKET_THROUGHPUT.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification) JiraKPIServiceFactory.getJiraKPIService(KPICode.WIP_VS_CLOSED.name()))
					.thenReturn(mcokAbstract);
		}

		List<KpiElement> resultList = jiraServiceKanbanR.process(kpiRequest);

		resultList.forEach(
				k -> {
					KPICode kpi = KPICode.getKPI(k.getKpiId());

					if (Objects.requireNonNull(kpi) == KPICode.NET_OPEN_TICKET_COUNT_BY_RCA) {
						assertThat("Kpi Name :", k.getKpiName(), equalTo("TICKET_RCA"));
					}
				});
	}

	@Test
	public void TestProcessKpiException() throws Exception {

		initiateRequiredServicesAndFactories();

		KpiRequest kpiRequest = kpiRequestFactory.findKpiRequest("kpi14");
		kpiRequest.setLabel("PROJECT");

		@SuppressWarnings("rawtypes")
		JiraKPIService mcokAbstract = rcaServiceImpl;
		jiraServiceCache.put(KPICode.NET_OPEN_TICKET_COUNT_BY_STATUS.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.TICKET_VELOCITY.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.TICKET_COUNT_BY_PRIORITY.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.NET_OPEN_TICKET_COUNT_BY_RCA.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.TICKET_OPEN_VS_CLOSE_BY_PRIORITY.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.LEAD_TIME_KANBAN.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.TICKET_OPEN_VS_CLOSED_RATE_BY_TYPE.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.KANBAN_JIRA_TECH_DEBT.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.TEAM_CAPACITY.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.TICKET_THROUGHPUT.name(), mcokAbstract);
		jiraServiceCache.put(KPICode.WIP_VS_CLOSED.name(), mcokAbstract);

		try (MockedStatic<JiraKPIServiceFactory> utilities =
				Mockito.mockStatic(JiraKPIServiceFactory.class)) {
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(
											KPICode.NET_OPEN_TICKET_COUNT_BY_STATUS.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(KPICode.TICKET_VELOCITY.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(KPICode.TICKET_COUNT_BY_PRIORITY.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(
											KPICode.NET_OPEN_TICKET_COUNT_BY_RCA.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(
											KPICode.TICKET_OPEN_VS_CLOSE_BY_PRIORITY.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(KPICode.LEAD_TIME_KANBAN.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(
											KPICode.TICKET_OPEN_VS_CLOSED_RATE_BY_TYPE.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(KPICode.KANBAN_JIRA_TECH_DEBT.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification) JiraKPIServiceFactory.getJiraKPIService(KPICode.TEAM_CAPACITY.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification)
									JiraKPIServiceFactory.getJiraKPIService(KPICode.TICKET_THROUGHPUT.name()))
					.thenReturn(mcokAbstract);
			utilities
					.when(
							(Verification) JiraKPIServiceFactory.getJiraKPIService(KPICode.WIP_VS_CLOSED.name()))
					.thenReturn(mcokAbstract);
		}

		when(filterHelperService.getFilteredBuildsKanban(
						ArgumentMatchers.any(), ArgumentMatchers.any()))
				.thenReturn(accountHierarchyDataList);
		when(authorizedProjectsService.getKanbanProjectKey(accountHierarchyDataList, kpiRequest))
				.thenReturn(new String[] {"test-key"});

		List<KpiElement> resultList = jiraServiceKanbanR.process(kpiRequest);

		resultList.forEach(
				k -> {
					KPICode kpi = KPICode.getKPI(k.getKpiId());

					if (Objects.requireNonNull(kpi) == KPICode.NET_OPEN_TICKET_COUNT_BY_RCA) {
						assertThat("Kpi Name :", k.getKpiName(), equalTo("TICKET_RCA"));
					}
				});
	}

	@Test
	public void when_KanbanKpiRequestIsReceived_Expect_RequestIsPopulatedAccordingly() {
		KpiRequest kpiRequest = new KpiRequest();
		kpiRequest.setIds(new String[] {"10"});
		kpiRequest.setSelectedMap(Map.of("date", List.of("5")));

		ReflectionTestUtils.invokeMethod(jiraServiceKanbanR, "populateKanbanKpiRequest", kpiRequest);

		assertEquals(10, kpiRequest.getKanbanXaxisDataPoints());
		assertEquals(CommonConstant.DAYS, kpiRequest.getDuration());

		kpiRequest.setSelectedMap(Map.of("date", List.of("Random duration")));

		ReflectionTestUtils.invokeMethod(jiraServiceKanbanR, "populateKanbanKpiRequest", kpiRequest);

		assertEquals("RANDOM DURATION", kpiRequest.getDuration());
	}

	@Test
	public void
			when_RequesterShouldHaveFullAccessOnRequestedResource_Expect_GetAuthorizedFilteredListReturnsRequiredResource() {
		KpiRequest kpiRequest = createKPIRequest();
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
							jiraServiceKanbanR,
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
							jiraServiceKanbanR,
							"getAuthorizedFilteredList",
							kpiRequest,
							expectedAccountHierarchyDataKanbanList);

			assertEquals(expectedAccountHierarchyDataKanbanList, resultedAccountHierarchyDataKanbanList);
		}
	}

	@Test
	public void
			when_RequesterIsNotSuperAdminOrRequestIsNotMadeWithApiKey_Expect_GetAuthorizedFilteredListPerformsResourceAccessFiltering() {
		KpiRequest kpiRequest = createKPIRequest();
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
							jiraServiceKanbanR,
							"getAuthorizedFilteredList",
							kpiRequest,
							testAccountHierarchyDataKanbanList);

			assertEquals(expectedAccountHierarchyDataKanbanList, resultedAccountHierarchyDataKanbanList);
		}
	}

	@Test
	public void when_RequestIsMadeWithApiKey_Expect_NoCachedDataWillBeUsed()
			throws EntityNotFoundException {
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
			KpiRequest kpiRequest = createKPIRequest();
			kpiRequest.setLabel("PROJECT");
			AccountHierarchyDataKanban accountHierarchyDataKanban = new AccountHierarchyDataKanban();
			Node testNode = new Node();
			testNode.setGroupName("group name");
			accountHierarchyDataKanban.setNode(List.of(testNode));
			when(filterHelperService.getHierarchyLevelId(anyInt(), anyString(), eq(true)))
					.thenReturn("test level id");
			when(filterHelperService.getFilteredBuildsKanban(any(KpiRequest.class), anyString()))
					.thenReturn(List.of(accountHierarchyDataKanban));

			jiraServiceKanbanR.process(kpiRequest);
			verifyNoInteractions(cacheService);
		}
	}

	private KpiRequest createKPIRequest() {
		KpiRequest kpiRequest = new KpiRequest();
		List<KpiElement> kpiList = new ArrayList<>();

		addKpiElement(
				kpiList, KPICode.TEST_JIRA.getKpiId(), KPICode.TEST_JIRA.name(), "Category One", "");
		kpiRequest.setLevel(4);
		kpiRequest.setIds(new String[] {"Kanban Project_6335368249794a18e8a4479f"});
		kpiRequest.setKpiList(kpiList);
		kpiRequest.setRequestTrackerId();
		Map<String, List<String>> selectedMap = new HashMap<>();
		selectedMap.put("Project", List.of("Kanban Project_6335368249794a18e8a4479f"));
		selectedMap.put(CommonConstant.DATE, List.of("10"));
		kpiRequest.setSelectedMap(selectedMap);
		kpiRequest.setLabel("PROJECT");
		return kpiRequest;
	}

	private TreeAggregatorDetail createTreeAggregatorDetail() {
		TreeAggregatorDetail treeAggregatorDetail = new TreeAggregatorDetail();
		treeAggregatorDetail.setMapOfListOfProjectNodes(
				Map.of("project", List.of(new Node(), new Node())));
		return treeAggregatorDetail;
	}

	private void addKpiElement(
			List<KpiElement> kpiList, String kpiId, String kpiName, String category, String kpiUnit) {
		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId(kpiId);
		kpiElement.setKpiName(kpiName);
		kpiElement.setKpiCategory(category);
		kpiElement.setKpiUnit(kpiUnit);
		kpiElement.setKpiSource("Jira");
		kpiElement.setMaxValue("500");
		kpiElement.setGroupId(1);
		kpiElement.setChartType("gaugeChart");
		kpiList.add(kpiElement);
	}

	private void initiateRequiredServicesAndFactories() {
		List<JiraKPIService<?, ?, ?>> mockServices = List.of(service);
		JiraKPIServiceFactory serviceFactory =
				JiraKPIServiceFactory.builder().services(mockServices).build();
		doReturn(TEST_JIRA).when(service).getQualifierType();
		serviceFactory.initMyServiceCache();

		kpiRequestFactory = KpiRequestFactory.newInstance();
	}
}
