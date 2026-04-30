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

package com.publicissapient.kpidashboard.apis.jira.kanban.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyKanbanFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.HierachyLevelFactory;
import com.publicissapient.kpidashboard.apis.data.KanbanIssueCustomHistoryDataFactory;
import com.publicissapient.kpidashboard.apis.data.KanbanJiraIssueDataFactory;
import com.publicissapient.kpidashboard.apis.data.KpiRequestFactory;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyDataKanban;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.jira.KanbanIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.KanbanJiraIssue;
import com.publicissapient.kpidashboard.common.repository.application.FieldMappingRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.jira.KanbanJiraIssueHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.KanbanJiraIssueRepository;

@SuppressWarnings({"javadoc", "deprecation"})
@RunWith(MockitoJUnitRunner.Silent.class)
public class CostOfDelayKanbanServiceImplTest {

	public Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
	public Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();
	@Mock KanbanJiraIssueRepository jiraKanbanIssueRepository;
	@Mock KanbanJiraIssueHistoryRepository kanbanJiraIssueHistoryRepository;
	@Mock CacheService cacheService;
	@Mock ConfigHelperService configHelperService;
	@Mock KpiHelperService kpiHelperService;
	@InjectMocks CostOfDelayKanbanServiceImpl costOfDelayKanbanServiceImpl;
	@Mock ProjectBasicConfigRepository projectConfigRepository;
	@Mock FieldMappingRepository fieldMappingRepository;
	@Mock CustomApiConfig customApiSetting;

	private List<AccountHierarchyDataKanban> accountHierarchyKanbanDataList = new ArrayList<>();
	private List<KanbanJiraIssue> kanbanJiraIssueDataList = new ArrayList<>();
	private List<KanbanIssueCustomHistory> kanbanIssueCustomHistoryList = new ArrayList<>();
	private KpiRequest kpiRequest;

	@Before
	public void setup() {
		KpiRequestFactory kpiRequestFactory =
				KpiRequestFactory.newInstance("/json/default/kanban_kpi_request.json");
		kpiRequest = kpiRequestFactory.findKpiRequest("kpi114");
		kpiRequest.setLabel("PROJECT");

		AccountHierarchyKanbanFilterDataFactory accountHierarchyKanbanFilterDataFactory =
				AccountHierarchyKanbanFilterDataFactory.newInstance();
		accountHierarchyKanbanDataList =
				accountHierarchyKanbanFilterDataFactory.getAccountHierarchyKanbanDataList();

		KanbanJiraIssueDataFactory kanbanJiraIssueDataFactory =
				KanbanJiraIssueDataFactory.newInstance();
		kanbanJiraIssueDataList =
				kanbanJiraIssueDataFactory.getKanbanJiraIssueDataListByTypeName(Arrays.asList("Story"));
		jiraKanbanIssueRepository.saveAll(kanbanJiraIssueDataList);

		KanbanIssueCustomHistoryDataFactory historyDataFactory =
				KanbanIssueCustomHistoryDataFactory.newInstance();
		kanbanIssueCustomHistoryList = historyDataFactory.getKanbanIssueCustomHistoryDataList();

		// Setup FieldMapping with closed statuses
		ObjectId projectConfigId = new ObjectId("6335368249794a18e8a4479f");
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setBasicProjectConfigId(projectConfigId);
		fieldMapping.setClosedIssueStatusToConsiderKpi114(Arrays.asList("Closed"));
		fieldMappingMap.put(projectConfigId, fieldMapping);

		Map<String, ProjectBasicConfig> mapOfProjectDetails = new HashMap<>();
		ProjectBasicConfig p1 = new ProjectBasicConfig();
		p1.setId(projectConfigId);
		p1.setProjectName("Test");
		p1.setProjectNodeId("Kanban Project_6335368249794a18e8a4479f");
		mapOfProjectDetails.put(p1.getId().toString(), p1);
		Mockito.when(cacheService.cacheProjectConfigMapData()).thenReturn(mapOfProjectDetails);
	}

	@After
	public void cleanup() {
		jiraKanbanIssueRepository.deleteAll();
	}

	@Test
	public void testFetchKPIDataFromDb() throws ApplicationException {
		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, new ArrayList<>(), accountHierarchyKanbanDataList, "hierarchyLevelOne", 4);
		List<Node> projectList =
				treeAggregatorDetail
						.getMapOfListOfProjectNodes()
						.get(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);

		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraKanbanIssueRepository.findCostOfDelayByType(Mockito.any()))
				.thenReturn(kanbanJiraIssueDataList);
		when(kanbanJiraIssueHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(
						Mockito.any(), Mockito.any()))
				.thenReturn(kanbanIssueCustomHistoryList);

		Map<String, Object> dataList =
				costOfDelayKanbanServiceImpl.fetchKPIDataFromDb(projectList, null, null, kpiRequest);
		assertThat("Result map should not be null", dataList, notNullValue());
		assertThat("Result map should contain 3 keys", dataList.size(), equalTo(3));
	}

	@Test
	public void testGetKpiData() throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraKanbanIssueRepository.findCostOfDelayByType(Mockito.any()))
				.thenReturn(kanbanJiraIssueDataList);
		when(kanbanJiraIssueHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(
						Mockito.any(), Mockito.any()))
				.thenReturn(kanbanIssueCustomHistoryList);
		when(customApiSetting.getJiraXaxisMonthCount()).thenReturn(5);

		String kpiRequestTrackerId = "Jira-5be544de025de212549176a9";
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRAKANBAN.name()))
				.thenReturn(kpiRequestTrackerId);

		HierachyLevelFactory hierachyLevelFactory = HierachyLevelFactory.newInstance();
		when(cacheService.getFullKanbanHierarchyLevel())
				.thenReturn(hierachyLevelFactory.getHierarchyLevels());

		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, new ArrayList<>(), accountHierarchyKanbanDataList, "hierarchyLevelOne", 4);

		KpiElement kpiElement =
				costOfDelayKanbanServiceImpl.getKpiData(
						kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);
		assertThat("KpiElement should not be null", kpiElement, notNullValue());
	}

	@Test
	public void testGetKpiDataWithExcelTracker() throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraKanbanIssueRepository.findCostOfDelayByType(Mockito.any()))
				.thenReturn(kanbanJiraIssueDataList);
		when(kanbanJiraIssueHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(
						Mockito.any(), Mockito.any()))
				.thenReturn(kanbanIssueCustomHistoryList);
		when(customApiSetting.getJiraXaxisMonthCount()).thenReturn(5);

		String kpiRequestTrackerId = "Excel-Jira-5be544de025de212549176a9";
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRAKANBAN.name()))
				.thenReturn(kpiRequestTrackerId);

		HierachyLevelFactory hierachyLevelFactory = HierachyLevelFactory.newInstance();
		when(cacheService.getFullKanbanHierarchyLevel())
				.thenReturn(hierachyLevelFactory.getHierarchyLevels());

		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, new ArrayList<>(), accountHierarchyKanbanDataList, "hierarchyLevelOne", 4);

		KpiElement kpiElement =
				costOfDelayKanbanServiceImpl.getKpiData(
						kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);
		assertThat("KpiElement should not be null", kpiElement, notNullValue());
	}

	@Test
	public void testGetKpiDataWithEmptyHistory() throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraKanbanIssueRepository.findCostOfDelayByType(Mockito.any()))
				.thenReturn(kanbanJiraIssueDataList);
		when(kanbanJiraIssueHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(
						Mockito.any(), Mockito.any()))
				.thenReturn(new ArrayList<>());
		when(customApiSetting.getJiraXaxisMonthCount()).thenReturn(5);

		String kpiRequestTrackerId = "Jira-5be544de025de212549176a9";
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRAKANBAN.name()))
				.thenReturn(kpiRequestTrackerId);

		HierachyLevelFactory hierachyLevelFactory = HierachyLevelFactory.newInstance();
		when(cacheService.getFullKanbanHierarchyLevel())
				.thenReturn(hierachyLevelFactory.getHierarchyLevels());

		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, new ArrayList<>(), accountHierarchyKanbanDataList, "hierarchyLevelOne", 4);

		KpiElement kpiElement =
				costOfDelayKanbanServiceImpl.getKpiData(
						kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);
		assertThat("KpiElement should not be null", kpiElement, notNullValue());
	}

	@Test
	public void testGetKpiDataWithEmptyCloseStatuses() throws ApplicationException {
		ObjectId projectConfigId = new ObjectId("6335368249794a18e8a4479f");
		FieldMapping fieldMappingNoStatuses = new FieldMapping();
		fieldMappingNoStatuses.setBasicProjectConfigId(projectConfigId);
		fieldMappingNoStatuses.setClosedIssueStatusToConsiderKpi114(new ArrayList<>());
		Map<ObjectId, FieldMapping> emptyStatusFieldMappingMap = new HashMap<>();
		emptyStatusFieldMappingMap.put(projectConfigId, fieldMappingNoStatuses);

		when(configHelperService.getFieldMappingMap()).thenReturn(emptyStatusFieldMappingMap);
		when(jiraKanbanIssueRepository.findCostOfDelayByType(Mockito.any()))
				.thenReturn(kanbanJiraIssueDataList);
		when(kanbanJiraIssueHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(
						Mockito.any(), Mockito.any()))
				.thenReturn(kanbanIssueCustomHistoryList);
		when(customApiSetting.getJiraXaxisMonthCount()).thenReturn(5);

		String kpiRequestTrackerId = "Jira-5be544de025de212549176a9";
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRAKANBAN.name()))
				.thenReturn(kpiRequestTrackerId);

		HierachyLevelFactory hierachyLevelFactory = HierachyLevelFactory.newInstance();
		when(cacheService.getFullKanbanHierarchyLevel())
				.thenReturn(hierachyLevelFactory.getHierarchyLevels());

		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, new ArrayList<>(), accountHierarchyKanbanDataList, "hierarchyLevelOne", 4);

		KpiElement kpiElement =
				costOfDelayKanbanServiceImpl.getKpiData(
						kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);
		assertThat("KpiElement should not be null", kpiElement, notNullValue());
	}

	@Test
	public void testCalculateKPIMetrics() {
		assertThat(costOfDelayKanbanServiceImpl.calculateKPIMetrics(new HashMap<>()), equalTo(null));
	}

	@Test
	public void testGetQualifierType() {
		assertThat(costOfDelayKanbanServiceImpl.getQualifierType(), equalTo("COST_OF_DELAY_KANBAN"));
	}
}
