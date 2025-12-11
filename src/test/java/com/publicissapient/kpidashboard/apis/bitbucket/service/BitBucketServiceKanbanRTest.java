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

package com.publicissapient.kpidashboard.apis.bitbucket.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.apis.abac.UserAuthorizedProjectsService;
import com.publicissapient.kpidashboard.apis.auth.apikey.ApiKeyAuthenticationService;
import com.publicissapient.kpidashboard.apis.bitbucket.factory.BitBucketKPIServiceFactory;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyKanbanFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.FieldMappingDataFactory;
import com.publicissapient.kpidashboard.apis.data.HierachyLevelFactory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyDataKanban;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;

@RunWith(MockitoJUnitRunner.class)
public class BitBucketServiceKanbanRTest {
	public Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
	public Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();

	@Mock
	private FilterHelperService filterHelperService;
	@Mock
	private KpiHelperService kpiHelperService;
	@Mock
	private CacheService cacheService;
	@Mock
	private CodeCommitKanbanServiceImpl codeCommitKanbanServiceImpl;
	@Mock
	private UserAuthorizedProjectsService authorizedProjectsService;

	@InjectMocks
	private BitBucketServiceKanbanR bitbucketServiceKanbanR;

	private List<AccountHierarchyDataKanban> accountHierarchyDataKanbanList = new ArrayList<>();
    private Map<String, BitBucketKPIService<?, ?, ?>> bitbucketServiceCache = new HashMap<>();

	@Before
	public void setup() throws EntityNotFoundException {
		AccountHierarchyKanbanFilterDataFactory accountHierarchyKanbanFilterDataFactory = AccountHierarchyKanbanFilterDataFactory
				.newInstance();
		accountHierarchyDataKanbanList = accountHierarchyKanbanFilterDataFactory.getAccountHierarchyKanbanDataList();

		ProjectBasicConfig projectConfig = new ProjectBasicConfig();
		projectConfig.setId(new ObjectId("6335368249794a18e8a4479f"));
		projectConfig.setProjectName("Kanban Project");
		projectConfigMap.put(projectConfig.getProjectName(), projectConfig);

		HierachyLevelFactory hierachyLevelFactory = HierachyLevelFactory.newInstance();
		List<HierarchyLevel> hierarchyLevels = hierachyLevelFactory.getHierarchyLevels();
		Map<String, Integer> map = new HashMap<>();
		Map<String, HierarchyLevel> hierarchyMap = hierarchyLevels.stream()
				.collect(Collectors.toMap(HierarchyLevel::getHierarchyLevelId, x -> x));
		hierarchyMap.forEach((key, value) -> map.put(key, value.getLevel()));
		when(filterHelperService.getHierarchyIdLevelMap(anyBoolean())).thenReturn(map);
		when(authorizedProjectsService.filterKanbanProjects(accountHierarchyDataKanbanList))
				.thenReturn(accountHierarchyDataKanbanList);

		FieldMappingDataFactory fieldMappingDataFactory = FieldMappingDataFactory
				.newInstance("/json/kanban/kanban_project_field_mappings.json");
		FieldMapping fieldMapping = fieldMappingDataFactory.getFieldMappings().get(0);
		fieldMappingMap.put(fieldMapping.getBasicProjectConfigId(), fieldMapping);
		when(filterHelperService.getFilteredBuildsKanban(Mockito.any(), Mockito.any()))
				.thenReturn(accountHierarchyDataKanbanList);
	}

	@Test
	public void testProcess() throws Exception {
		KpiRequest kpiRequest = createKpiRequest(2, "Bitbucket");
		bitbucketServiceCache.put(KPICode.NUMBER_OF_CHECK_INS.name(), codeCommitKanbanServiceImpl);

		List<KpiElement> resultList;
		when(kpiHelperService.isToolConfigured(any(), any(), any())).thenReturn(true);
		try (MockedStatic<BitBucketKPIServiceFactory> mockedStatic = mockStatic(BitBucketKPIServiceFactory.class)) {
			CodeCommitKanbanServiceImpl mockService = mock(CodeCommitKanbanServiceImpl.class);
			mockedStatic.when(
					() -> BitBucketKPIServiceFactory.getBitBucketKPIService(eq(KPICode.NUMBER_OF_CHECK_INS.name())))
					.thenReturn(mockService);
			when(mockService.getKpiData(any(), any(), any())).thenReturn(kpiRequest.getKpiList().get(0));
			resultList = bitbucketServiceKanbanR.process(kpiRequest);
			mockedStatic.verify(
					() -> BitBucketKPIServiceFactory.getBitBucketKPIService(eq(KPICode.NUMBER_OF_CHECK_INS.name())));
		}
		assertThat("Kpi Name :", resultList.get(0).getResponseCode(), equalTo(CommonConstant.KPI_PASSED));
	}

	@Test
	public void when_RequestIsMadeWithApiKey_Expect_NoCachedDataWillBeUsed() throws EntityNotFoundException, ApplicationException {
		KpiRequest kpiRequest = createKpiRequest(2, "Bitbucket");
		bitbucketServiceCache.put(KPICode.NUMBER_OF_CHECK_INS.name(), codeCommitKanbanServiceImpl);

		when(kpiHelperService.isToolConfigured(any(), any(), any())).thenReturn(true);
		try (MockedStatic<BitBucketKPIServiceFactory> mockedStatic = mockStatic(BitBucketKPIServiceFactory.class);
			 MockedStatic<ApiKeyAuthenticationService> apiKeyAuthenticationServiceMockedStatic = mockStatic(
					 ApiKeyAuthenticationService.class)) {
			CodeCommitKanbanServiceImpl mockService = mock(CodeCommitKanbanServiceImpl.class);
			mockedStatic.when(
							() -> BitBucketKPIServiceFactory.getBitBucketKPIService(eq(KPICode.NUMBER_OF_CHECK_INS.name())))
					.thenReturn(mockService);
			apiKeyAuthenticationServiceMockedStatic.when(ApiKeyAuthenticationService::isApiKeyRequest)
					.thenReturn(true);
			when(mockService.getKpiData(any(), any(), any())).thenReturn(kpiRequest.getKpiList().get(0));
			bitbucketServiceKanbanR.process(kpiRequest);
			mockedStatic.verify(
					() -> BitBucketKPIServiceFactory.getBitBucketKPIService(eq(KPICode.NUMBER_OF_CHECK_INS.name())));
		}
		verifyNoInteractions(cacheService);
	}

	@Test
	public void testProcess_NullPointer() throws Exception {
		KpiRequest kpiRequest = createKpiRequest(2, "Bitbucket");
		bitbucketServiceCache.put(KPICode.NUMBER_OF_CHECK_INS.name(), codeCommitKanbanServiceImpl);

		List<KpiElement> resultList;
		when(kpiHelperService.isToolConfigured(any(), any(), any())).thenReturn(true);
		try (MockedStatic<BitBucketKPIServiceFactory> mockedStatic = mockStatic(BitBucketKPIServiceFactory.class)) {
			CodeCommitKanbanServiceImpl mockService = mock(CodeCommitKanbanServiceImpl.class);
			mockedStatic.when(
					() -> BitBucketKPIServiceFactory.getBitBucketKPIService(eq(KPICode.NUMBER_OF_CHECK_INS.name())))
					.thenReturn(mockService);
			when(mockService.getKpiData(any(), any(), any())).thenThrow(NullPointerException.class);
			resultList = bitbucketServiceKanbanR.process(kpiRequest);
			mockedStatic.verify(
					() -> BitBucketKPIServiceFactory.getBitBucketKPIService(eq(KPICode.NUMBER_OF_CHECK_INS.name())));
		}
		assertThat("Kpi Name :", resultList.get(0).getResponseCode(), equalTo(CommonConstant.KPI_FAILED));
	}

	@Test
	public void testProcess_Application() throws Exception {
		KpiRequest kpiRequest = createKpiRequest(2, "Bitbucket");
		bitbucketServiceCache.put(KPICode.NUMBER_OF_CHECK_INS.name(), codeCommitKanbanServiceImpl);

		List<KpiElement> resultList;
		when(kpiHelperService.isToolConfigured(any(), any(), any())).thenReturn(true);
		try (MockedStatic<BitBucketKPIServiceFactory> mockedStatic = mockStatic(BitBucketKPIServiceFactory.class)) {
			CodeCommitKanbanServiceImpl mockService = mock(CodeCommitKanbanServiceImpl.class);
			mockedStatic.when(
					() -> BitBucketKPIServiceFactory.getBitBucketKPIService(eq(KPICode.NUMBER_OF_CHECK_INS.name())))
					.thenReturn(mockService);
			when(mockService.getKpiData(any(), any(), any())).thenThrow(ApplicationException.class);
			resultList = bitbucketServiceKanbanR.process(kpiRequest);
			mockedStatic.verify(
					() -> BitBucketKPIServiceFactory.getBitBucketKPIService(eq(KPICode.NUMBER_OF_CHECK_INS.name())));
		}
		assertThat("Kpi Name :", resultList.get(0).getResponseCode(), equalTo(CommonConstant.KPI_FAILED));
	}

	@Test
	public void testProcessCachedData() throws Exception {
		KpiRequest kpiRequest = createKpiRequest(2, "Bitbucket");

		List<KpiElement> resultList = bitbucketServiceKanbanR.process(kpiRequest);
		assertThat("Kpi list :", resultList.size(), equalTo(1));
	}

	@Test
	public void when_RequesterShouldHaveFullAccessOnRequestedResource_Expect_GetAuthorizedFilteredListReturnsRequiredResource() {
		KpiRequest kpiRequest = createKpiRequest(2, "Bitbucket");
		List<AccountHierarchyDataKanban> expectedAccountHierarchyDataKanbanList = new ArrayList<>();
		AccountHierarchyDataKanban accountHierarchyDataKanban = new AccountHierarchyDataKanban();
		accountHierarchyDataKanban.setLabelName("testLabel");
		expectedAccountHierarchyDataKanbanList.add(accountHierarchyDataKanban);

		try(MockedStatic<ApiKeyAuthenticationService> apiKeyAuthenticationServiceMockedStatic =
					mockStatic(ApiKeyAuthenticationService.class)) {
			//Case 1 -> user is super admin and request is not through api key
			when(authorizedProjectsService.ifSuperAdminUser()).thenReturn(true);
			apiKeyAuthenticationServiceMockedStatic.when(ApiKeyAuthenticationService::isApiKeyRequest).thenReturn(false);

			List<AccountHierarchyDataKanban> resultedAccountHierarchyDataKanbanList =
					ReflectionTestUtils.invokeMethod(bitbucketServiceKanbanR, "getAuthorizedFilteredList", kpiRequest,
							expectedAccountHierarchyDataKanbanList);

			assertEquals(expectedAccountHierarchyDataKanbanList, resultedAccountHierarchyDataKanbanList);

			//Case 2 -> user is not super admin and request is through api key
			when(authorizedProjectsService.ifSuperAdminUser()).thenReturn(false);
			apiKeyAuthenticationServiceMockedStatic.when(ApiKeyAuthenticationService::isApiKeyRequest).thenReturn(true);

			resultedAccountHierarchyDataKanbanList =
					ReflectionTestUtils.invokeMethod(bitbucketServiceKanbanR, "getAuthorizedFilteredList", kpiRequest,
							expectedAccountHierarchyDataKanbanList);

			assertEquals(expectedAccountHierarchyDataKanbanList, resultedAccountHierarchyDataKanbanList);
		}
	}

	@Test
	public void when_RequesterIsNotSuperAdminOrRequestIsNotMadeWithApiKey_Expect_GetAuthorizedFilteredListPerformsResourceAccessFiltering() {
		KpiRequest kpiRequest = createKpiRequest(2, "Bitbucket");
		List<AccountHierarchyDataKanban> testAccountHierarchyDataKanbanList = new ArrayList<>();
		AccountHierarchyDataKanban accountHierarchyDataKanban = new AccountHierarchyDataKanban();
		accountHierarchyDataKanban.setLabelName("testLabel");
		testAccountHierarchyDataKanbanList.add(accountHierarchyDataKanban);

		List<AccountHierarchyDataKanban> expectedAccountHierarchyDataKanbanList = new ArrayList<>();
		AccountHierarchyDataKanban accountHierarchyDataKanban1 = new AccountHierarchyDataKanban();
		accountHierarchyDataKanban1.setLabelName("expectedLabel");
		expectedAccountHierarchyDataKanbanList.add(accountHierarchyDataKanban);

		try(MockedStatic<ApiKeyAuthenticationService> apiKeyAuthenticationServiceMockedStatic =
					mockStatic(ApiKeyAuthenticationService.class)) {
			when(authorizedProjectsService.ifSuperAdminUser()).thenReturn(false);
			when(authorizedProjectsService.filterKanbanProjects(anyList())).thenReturn(expectedAccountHierarchyDataKanbanList);
			apiKeyAuthenticationServiceMockedStatic.when(ApiKeyAuthenticationService::isApiKeyRequest).thenReturn(false);

			List<AccountHierarchyDataKanban> resultedAccountHierarchyDataKanbanList =
					ReflectionTestUtils.invokeMethod(bitbucketServiceKanbanR, "getAuthorizedFilteredList", kpiRequest,
							testAccountHierarchyDataKanbanList);

			assertEquals(expectedAccountHierarchyDataKanbanList, resultedAccountHierarchyDataKanbanList);
		}
	}

	@Test
	public void when_KanbanKpiRequestIsReceived_Expect_RequestIsPopulatedAccordingly() {
		KpiRequest kpiRequest1 = new KpiRequest();
		kpiRequest1.setIds(new String[] { "10" });
		kpiRequest1.setSelectedMap(Map.of("date", List.of("5")));

		ReflectionTestUtils.invokeMethod(bitbucketServiceKanbanR, "populateKanbanKpiRequest", kpiRequest1);

		assertEquals(10, kpiRequest1.getKanbanXaxisDataPoints());
		assertEquals(CommonConstant.DAYS, kpiRequest1.getDuration());

		kpiRequest1.setSelectedMap(Map.of("date", List.of("Random duration")));

		ReflectionTestUtils.invokeMethod(bitbucketServiceKanbanR, "populateKanbanKpiRequest", kpiRequest1);

		assertEquals("RANDOM DURATION", kpiRequest1.getDuration());
	}

	private KpiRequest createKpiRequest(int level, String source) {
		KpiRequest kpiRequest = new KpiRequest();
		List<KpiElement> kpiList = new ArrayList<>();

		addKpiElement(kpiList, KPICode.NUMBER_OF_CHECK_INS.getKpiId(), KPICode.NUMBER_OF_CHECK_INS.name(),
				"Productivity", "", source);
		kpiRequest.setLevel(level);
		kpiRequest.setIds(new String[] { "5" });
		kpiRequest.setKpiList(kpiList);
		Map<String, List<String>> selectedMap = new HashMap<>();
		selectedMap.put("date", List.of("DATE"));
		selectedMap.put("Project", List.of("Kanban Project_6335368249794a18e8a4479f"));

		kpiRequest.setSelectedMap(selectedMap);
		kpiRequest.setRequestTrackerId();
		return kpiRequest;
	}

	private void addKpiElement(List<KpiElement> kpiList, String kpiId, String kpiName, String category, String kpiUnit,
			String source) {
		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId(kpiId);
		kpiElement.setKpiName(kpiName);
		kpiElement.setKpiCategory(category);
		kpiElement.setKpiUnit(kpiUnit);
		kpiElement.setKpiSource(source);
		kpiElement.setMaxValue("500");
		kpiElement.setChartType("gaugeChart");
		kpiList.add(kpiElement);
	}

	private KpiElement setKpiElement(String kpiId, String kpiName) {

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId(kpiId);
		kpiElement.setKpiName(kpiName);

		return kpiElement;
	}
}
