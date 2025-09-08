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

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.publicissapient.kpidashboard.apis.data.FieldMappingDataFactory;
import org.bson.types.ObjectId;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.abac.UserAuthorizedProjectsService;
import com.publicissapient.kpidashboard.apis.bitbucket.factory.BitBucketKPIServiceFactory;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.HierachyLevelFactory;
import com.publicissapient.kpidashboard.apis.enums.Filters;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;

@RunWith(MockitoJUnitRunner.Silent.class)
public class BitBucketServiceRTest {

	public Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
	public Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();
	@Mock
	KpiHelperService kpiHelperService;
	@Mock
	FilterHelperService filterHelperService;
	@Mock
	ScmKpiHelperService scmKpiHelperService;
	@InjectMocks
	private BitBucketServiceR bitBucketServiceR;
	@Mock
	private CacheService cacheService;
	@Mock
	private BitBucketKPIService<?, ?, ?> bitBucketKPIService;

	@SuppressWarnings("rawtypes")
	@Mock
	private List<BitBucketKPIService> services;

	private List<AccountHierarchyData> accountHierarchyDataList = new ArrayList<>();
	private Map<String, Object> filterLevelMap;
	private String[] projectKey;
	private List<HierarchyLevel> hierarchyLevels = new ArrayList<>();
	private Map<String, BitBucketKPIService> bitBucketServiceCache = new HashMap<>();
	@Mock
	private UserAuthorizedProjectsService authorizedProjectsService;

	private KpiRequest kpiRequest;

	@Before
	public void setup() throws ApplicationException {
		MockitoAnnotations.openMocks(this);

		AccountHierarchyFilterDataFactory accountHierarchyFilterDataFactory = AccountHierarchyFilterDataFactory
				.newInstance("/json/default/project_hierarchy_filter_data.json");
		accountHierarchyDataList = accountHierarchyFilterDataFactory.getAccountHierarchyDataList();
		HierachyLevelFactory hierachyLevelFactory = HierachyLevelFactory.newInstance();
		hierarchyLevels = hierachyLevelFactory.getHierarchyLevels();

		filterLevelMap = new LinkedHashMap<>();
		filterLevelMap.put("PROJECT", Filters.PROJECT);

		kpiRequest = createKpiRequest(5);

		ProjectBasicConfig projectConfig = new ProjectBasicConfig();
		projectConfig.setId(new ObjectId("6335363749794a18e8a4479b"));
		projectConfig.setProjectName("Scrum Project");
		projectConfigMap.put(projectConfig.getProjectName(), projectConfig);

		FieldMappingDataFactory fieldMappingDataFactory = FieldMappingDataFactory
				.newInstance("/json/default/scrum_project_field_mappings.json");
		FieldMapping fieldMapping = fieldMappingDataFactory.getFieldMappings().get(0);
		fieldMappingMap.put(fieldMapping.getBasicProjectConfigId(), fieldMapping);

		when(filterHelperService.getHierarachyLevelId(5, "project", false)).thenReturn("project");
		when(filterHelperService.getFilteredBuilds(any(), anyString())).thenReturn(accountHierarchyDataList);
		when(kpiHelperService.getAuthorizedFilteredList(any(), any(), anyBoolean()))
				.thenReturn(accountHierarchyDataList);
		when(authorizedProjectsService.getProjectKey(any(), any())).thenReturn(kpiRequest.getIds());
		when(authorizedProjectsService.ifSuperAdminUser()).thenReturn(true);

		Map<String, Integer> hierarchyIdLevelMap = new HashMap<>();
		hierarchyIdLevelMap.put(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, 5);
		when(filterHelperService.getHierarchyIdLevelMap(anyBoolean())).thenReturn(hierarchyIdLevelMap);
	}

	@Test
	public void testKPI() throws Exception {
		KpiRequest kpiRequest = createKpiRequest(5);
		when(kpiHelperService.isToolConfigured(any(), any(), any())).thenReturn(true);

		// Setup SCM data
		when(scmKpiHelperService.getCommitDetails(any(ObjectId.class), any(CustomDateRange.class)))
				.thenReturn(List.of(new ScmCommits()));
		when(scmKpiHelperService.getMergeRequests(any(ObjectId.class), any(CustomDateRange.class)))
				.thenReturn(List.of(new ScmMergeRequests()));
		when(scmKpiHelperService.getScmUsers(any(ObjectId.class))).thenReturn(Arrays.asList(new Assignee()));

		try (MockedStatic<BitBucketKPIServiceFactory> factoryMock = Mockito
				.mockStatic(BitBucketKPIServiceFactory.class);
				MockedStatic<DeveloperKpiHelper> helperMock = Mockito.mockStatic(DeveloperKpiHelper.class)) {

			factoryMock.when(() -> BitBucketKPIServiceFactory.getBitBucketKPIService(anyString()))
					.thenReturn(bitBucketKPIService);
			helperMock.when(() -> DeveloperKpiHelper.getStartAndEndDate(any(KpiRequest.class)))
					.thenReturn(new CustomDateRange());

			KpiElement responseKpi = new KpiElement();
			responseKpi.setResponseCode(CommonConstant.KPI_PASSED);
			when(bitBucketKPIService.getKpiData(any(), any(), any())).thenReturn(responseKpi);

			List<KpiElement> resultList = bitBucketServiceR.process(kpiRequest);

			MatcherAssert.assertThat("Kpi Name :", resultList.get(0).getResponseCode(),
					equalTo(CommonConstant.KPI_FAILED));
		}
	}

	@Test
	public void TestProcess() throws Exception {
		when(authorizedProjectsService.getProjectKey(any(), any())).thenReturn(kpiRequest.getIds());
		bitBucketServiceCache.put(KPICode.REPO_TOOL_CODE_COMMIT.name(), bitBucketKPIService);
		when(kpiHelperService.isToolConfigured(any(), any(), any())).thenReturn(true);

		// Setup SCM data
		when(scmKpiHelperService.getCommitDetails(any(ObjectId.class), any(CustomDateRange.class)))
				.thenReturn(List.of(new ScmCommits()));
		when(scmKpiHelperService.getMergeRequests(any(ObjectId.class), any(CustomDateRange.class)))
				.thenReturn(List.of(new ScmMergeRequests()));
		when(scmKpiHelperService.getScmUsers(any(ObjectId.class))).thenReturn(Arrays.asList(new Assignee()));

		try (MockedStatic<BitBucketKPIServiceFactory> factoryMock = Mockito
				.mockStatic(BitBucketKPIServiceFactory.class);
				MockedStatic<DeveloperKpiHelper> helperMock = Mockito.mockStatic(DeveloperKpiHelper.class)) {

			factoryMock.when(() -> BitBucketKPIServiceFactory.getBitBucketKPIService(anyString()))
					.thenReturn(bitBucketKPIService);
			helperMock.when(() -> DeveloperKpiHelper.getStartAndEndDate(any(KpiRequest.class)))
					.thenReturn(new CustomDateRange());

			KpiElement responseKpi = new KpiElement();
			responseKpi.setResponseCode(CommonConstant.KPI_PASSED);
			when(bitBucketKPIService.getKpiData(any(), any(), any())).thenReturn(responseKpi);

			List<KpiElement> resultList = bitBucketServiceR.process(kpiRequest);
			MatcherAssert.assertThat("Kpi Name :", resultList.get(0).getResponseCode(),
					equalTo(CommonConstant.KPI_FAILED));
		}
	}

	@After
	public void cleanup() {
	}

	@Test
	public void TestProcess_pickFromCache() throws Exception {

		KpiRequest kpiRequest = createKpiRequest(5);

		when(cacheService.getFromApplicationCache(any(), Mockito.anyString(), any(), ArgumentMatchers.anyList()))
				.thenReturn(new ArrayList<KpiElement>());

		List<KpiElement> resultList = bitBucketServiceR.process(kpiRequest);

		assertEquals(0, resultList.size());
	}

	@Test
	public void processWithExposedApiToken() throws EntityNotFoundException {
		KpiRequest kpiRequest = createKpiRequest(5);
		when(cacheService.getFromApplicationCache(any(), Mockito.anyString(), any(), ArgumentMatchers.anyList()))
				.thenReturn(new ArrayList<KpiElement>());
		List<KpiElement> resultList = bitBucketServiceR.processWithExposedApiToken(kpiRequest, true);
		assertEquals(0, resultList.size());
	}

	private KpiRequest createKpiRequest(int level) {
		KpiRequest kpiRequest = new KpiRequest();
		List<KpiElement> kpiList = new ArrayList<>();

		addKpiElement(kpiList, KPICode.REPO_TOOL_CODE_COMMIT.getKpiId(), KPICode.REPO_TOOL_CODE_COMMIT.name());
		kpiRequest.setLevel(level);
		kpiRequest.setIds(new String[] { "7" });
		kpiRequest.setKpiList(kpiList);
		kpiRequest.setRequestTrackerId();
		kpiRequest.setLabel("project");
		Map<String, List<String>> s = new HashMap<>();
		s.put(CommonConstant.DATE, List.of("WEEKS"));
		kpiRequest.setSelectedMap(s);
		kpiRequest.setSprintIncluded(Arrays.asList("CLOSED", "ACTIVE"));
		return kpiRequest;
	}

	private void addKpiElement(List<KpiElement> kpiList, String kpiId, String kpiName) {
		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId(kpiId);
		kpiElement.setKpiName(kpiName);
		kpiElement.setKpiCategory("Developer");
		kpiElement.setKpiUnit("");
		kpiElement.setKpiSource("BitBucket");
		kpiElement.setGroupId(1);
		kpiElement.setMaxValue("500");
		kpiElement.setChartType("gaugeChart");
		kpiList.add(kpiElement);
	}
}
