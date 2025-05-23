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

package com.publicissapient.kpidashboard.apis.common.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.abac.UserAuthorizedProjectsService;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyKanbanFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.CapacityKpiDataDataFactory;
import com.publicissapient.kpidashboard.apis.data.FieldMappingDataFactory;
import com.publicissapient.kpidashboard.apis.data.JiraIssueDataFactory;
import com.publicissapient.kpidashboard.apis.data.JiraIssueHistoryDataFactory;
import com.publicissapient.kpidashboard.apis.data.KanbanIssueCustomHistoryDataFactory;
import com.publicissapient.kpidashboard.apis.data.KpiMasterDataFactory;
import com.publicissapient.kpidashboard.apis.data.KpiRequestFactory;
import com.publicissapient.kpidashboard.apis.data.SprintDetailsDataFactory;
import com.publicissapient.kpidashboard.apis.data.SprintWiseStoryDataFactory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyDataKanban;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.MasterResponse;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.FieldMappingStructure;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.excel.CapacityKpiData;
import com.publicissapient.kpidashboard.common.model.generic.ProcessorItem;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.KanbanIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintWiseStory;
import com.publicissapient.kpidashboard.common.repository.excel.CapacityKpiDataRepository;
import com.publicissapient.kpidashboard.common.repository.excel.KanbanCapacityRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.jira.KanbanJiraIssueHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.common.repository.kpivideolink.KPIVideoLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class KpiHelperServiceTest {

	private List<AccountHierarchyData> ahdList = new ArrayList<>();
	private List<AccountHierarchyDataKanban> ahdKanbanList = new ArrayList<>();
	private Map<ObjectId, Map<String, List<Tool>>> toolMap = new HashMap<>();
	@Mock
	private JiraIssueRepository jiraIssueRepository;

	@Mock
	private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

	@Mock
	private KanbanJiraIssueHistoryRepository kanbanJiraIssueHistoryRepository;

	@Mock
	private KanbanCapacityRepository kanbanCapacityRepository;
	@Mock
	private ConfigHelperService configHelperService;

	@Mock
	private CapacityKpiDataRepository capacityKpiDataRepository;
	@InjectMocks
	private KpiHelperService kpiHelperService;

	private List<SprintDetails> sprintDetailsList = new ArrayList<>();

	private List<JiraIssueCustomHistory> issueCustomHistoryList = new ArrayList<>();

	private List<SprintWiseStory> sprintWiseStoryList = new ArrayList<>();

	private List<JiraIssue> bugList = new ArrayList<>();

	private List<JiraIssue> issueList = new ArrayList<>();

	private List<CapacityKpiData> capacityKpiDataList = new ArrayList<>();

	private KpiRequestFactory kpiRequestFactory;

	private KpiRequestFactory kanbanKpiRequestFactory;

	@Mock
	private FieldMappingStructure fieldMappingStructure = new FieldMappingStructure();

	private List<FieldMappingStructure> fieldMappingStructureList = new ArrayList<>();

	@Mock
	private FilterHelperService flterHelperService;

	@Mock
	private SprintRepository sprintRepository;

	@Mock
	private KPIVideoLinkRepository kpiVideoLinkRepository;

	@Mock
	private CustomApiConfig customApiConfig;

	@Mock
	private CacheService cacheService;
	@Mock
	private UserAuthorizedProjectsService authorizedProjectsService;

	Map<String, List<String>> priority = new HashMap<>();

	private Map<ObjectId, Map<String, List<ProjectToolConfig>>> projectConfigMap = new HashMap<>();

	List<AccountHierarchyData> accountHierarchyDataList = new ArrayList<>();
	private static final String AZURE_REPO = "AzureRepository";
	private static final String BITBUCKET = "Bitbucket";
	private static final String GITLAB = "GitLab";
	private static final String GITHUB = "GitHub";
	private static Tool tool3;
	public Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();
	Map<String, List<Tool>> toolGroup = new HashMap<>();

	List<Tool> toolList1;
	List<Tool> toolList2;
	List<Tool> toolList3;

	@Before
	public void setup() {
		AccountHierarchyFilterDataFactory factory = AccountHierarchyFilterDataFactory.newInstance();
		ahdList = factory.getAccountHierarchyDataList();

		AccountHierarchyKanbanFilterDataFactory kanbanFactory = AccountHierarchyKanbanFilterDataFactory.newInstance();
		ahdKanbanList = kanbanFactory.getAccountHierarchyKanbanDataList();

		kpiRequestFactory = KpiRequestFactory.newInstance();
		kanbanKpiRequestFactory = KpiRequestFactory.newInstance();

		ProjectToolConfig projectConfig = new ProjectToolConfig();
		projectConfig.setId(new ObjectId("6335363749794a18e8a4479b"));
		projectConfig.setBasicProjectConfigId(new ObjectId("6335363749794a18e8a4479c"));
		Map<String, List<ProjectToolConfig>> stringListMap = new HashMap<>();
		stringListMap.put("Jira", Arrays.asList());
		projectConfigMap.put(projectConfig.getBasicProjectConfigId(), stringListMap);
		when(configHelperService.getProjectToolConfigMap()).thenReturn(projectConfigMap);

		FieldMappingDataFactory fieldMappingDataFactory = FieldMappingDataFactory
				.newInstance("/json/default/scrum_project_field_mappings.json");
		FieldMapping fieldMapping = fieldMappingDataFactory.getFieldMappings().get(0);
		Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();
		fieldMappingMap.put(fieldMapping.getBasicProjectConfigId(), fieldMapping);
		FieldMappingDataFactory fieldMappingDataFactoryKanban = FieldMappingDataFactory
				.newInstance("/json/kanban/kanban_project_field_mappings.json");
		FieldMapping fieldMappingKanban = fieldMappingDataFactoryKanban.getFieldMappings().get(0);
		fieldMappingMap.put(fieldMappingKanban.getBasicProjectConfigId(), fieldMappingKanban);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		configHelperService.setFieldMappingMap(fieldMappingMap);

		sprintDetailsList = SprintDetailsDataFactory.newInstance().getSprintDetails();

		JiraIssueHistoryDataFactory issueHistoryFactory = JiraIssueHistoryDataFactory.newInstance();
		issueCustomHistoryList = issueHistoryFactory.getJiraIssueCustomHistory();

		SprintWiseStoryDataFactory storyFactory = SprintWiseStoryDataFactory.newInstance();
		sprintWiseStoryList = storyFactory.getSprintWiseStories();

		JiraIssueDataFactory jiraIssueDataFactory = JiraIssueDataFactory.newInstance();
		bugList = jiraIssueDataFactory.getBugs();

		issueList = jiraIssueDataFactory.getJiraIssues();

		CapacityKpiDataDataFactory capacityKpiDataDataFactory = CapacityKpiDataDataFactory.newInstance();
		capacityKpiDataList = capacityKpiDataDataFactory.getCapacityKpiDataList();
		fieldMappingStructureList.add(fieldMappingStructure);

		priority.put("P1", Arrays.asList("p1"));

		AccountHierarchyFilterDataFactory accountHierarchyFilterDataFactory = AccountHierarchyFilterDataFactory
				.newInstance("/json/default/account_hierarchy_filter_data.json");
		accountHierarchyDataList = accountHierarchyFilterDataFactory.getAccountHierarchyDataList();
	}

	@After
	public void cleanup() {
	}

	@Test
	public void testFetchDIRDataFromDb() throws ApplicationException {
		KpiRequest kpiRequest = kpiRequestFactory.findKpiRequest(KPICode.DEFECT_INJECTION_RATE.getKpiId());
		when(jiraIssueRepository.findIssuesGroupBySprint(any(), any(), any(), any())).thenReturn(sprintWiseStoryList);

		when(jiraIssueCustomHistoryRepository.findFeatureCustomHistoryStoryProjectWise(any(), any(), any()))
				.thenReturn(issueCustomHistoryList);
		when(jiraIssueRepository.findIssuesByType(any())).thenReturn(bugList);

		TreeAggregatorDetail treeAggregatorDetail = KPIHelperUtil.getTreeLeafNodesGroupedByFilter(kpiRequest, ahdList,
				new ArrayList<>(), "hierarchyLevelOne", 5);
		List<Node> leafNodeList = new ArrayList<>();
		leafNodeList = KPIHelperUtil.getLeafNodes(treeAggregatorDetail.getRoot(), leafNodeList, false);

		when(customApiConfig.getPriority()).thenReturn(priority);
		List<String> sprintList = List.of("sprint1", "sprint2");
		ObjectId basicProjectConfigId = new ObjectId("6335363749794a18e8a4479b");
		Map<String, Object> resultMap = kpiHelperService.fetchDIRDataFromDb(basicProjectConfigId, kpiRequest,
				sprintList);
		assertEquals(3, resultMap.size());
	}

	@Test
	public void testFetchQADDFromDb() throws ApplicationException {

		KpiRequest kpiRequest = kpiRequestFactory.findKpiRequest(KPICode.DEFECT_DENSITY.getKpiId());
		when(jiraIssueRepository.findIssuesGroupBySprint(any(), any(), any(), any())).thenReturn(sprintWiseStoryList);
		when(jiraIssueCustomHistoryRepository.findFeatureCustomHistoryStoryProjectWise(any(), any(), any()))
				.thenReturn(issueCustomHistoryList);
		when(jiraIssueRepository.findIssuesBySprintAndType(any(), any())).thenReturn(bugList);

		TreeAggregatorDetail treeAggregatorDetail = KPIHelperUtil.getTreeLeafNodesGroupedByFilter(kpiRequest, ahdList,
				new ArrayList<>(), "hierarchyLevelOne", 5);
		when(customApiConfig.getPriority()).thenReturn(priority);
		List<String> sprintList = List.of("sprint1", "sprint2");
		ObjectId basicProjectConfigId = new ObjectId("6335363749794a18e8a4479b");
		Map<String, Object> resultMap = kpiHelperService.fetchQADDFromDb(basicProjectConfigId, kpiRequest, sprintList);
		assertEquals(3, resultMap.size());
	}

	@Test
	public void testFetchSprintVelocityDataFromDb() throws ApplicationException {

		KpiRequest kpiRequest = kpiRequestFactory.findKpiRequest(KPICode.SPRINT_VELOCITY.getKpiId());
		TreeAggregatorDetail treeAggregatorDetail = KPIHelperUtil.getTreeLeafNodesGroupedByFilter(kpiRequest, ahdList,
				new ArrayList<>(), "hierarchyLevelOne", 5);
		List<Node> leafNodeList = new ArrayList<>();
		leafNodeList = KPIHelperUtil.getLeafNodes(treeAggregatorDetail.getRoot(), leafNodeList, false);

		Map<String, Object> resultMap = kpiHelperService.fetchSprintVelocityDataFromDb(kpiRequest, new ArrayList<>(),
				sprintDetailsList);
		assertEquals(2, resultMap.size());
	}

	@Test
	public void testFetchSprintCapacityDataFromDb() throws ApplicationException {

		KpiRequest kpiRequest = kpiRequestFactory.findKpiRequest(KPICode.SPRINT_CAPACITY_UTILIZATION.getKpiId());
		when(sprintRepository.findBySprintIDIn(any())).thenReturn(sprintDetailsList);

		TreeAggregatorDetail treeAggregatorDetail = KPIHelperUtil.getTreeLeafNodesGroupedByFilter(kpiRequest, ahdList,
				new ArrayList<>(), "hierarchyLevelOne", 5);
		List<Node> leafNodeList = new ArrayList<>();
		leafNodeList = KPIHelperUtil.getLeafNodes(treeAggregatorDetail.getRoot(), leafNodeList, false);

		kpiHelperService.fetchSprintCapacityDataFromDb(kpiRequest, leafNodeList);
		assertEquals(5, leafNodeList.size());
	}

	@Test
	public void testFetchCapacityDataFromDB() throws ApplicationException {

		KpiRequest kpiRequest = kpiRequestFactory.findKpiRequest(KPICode.CAPACITY.getKpiId());

		when(capacityKpiDataRepository.findByFilters(any(), any())).thenReturn(capacityKpiDataList);

		TreeAggregatorDetail treeAggregatorDetail = KPIHelperUtil.getTreeLeafNodesGroupedByFilter(kpiRequest, ahdList,
				new ArrayList<>(), "hierarchyLevelOne", 5);
		List<Node> leafNodeList = new ArrayList<>();
		leafNodeList = KPIHelperUtil.getLeafNodes(treeAggregatorDetail.getRoot(), leafNodeList, false);

		List<CapacityKpiData> resultList = kpiHelperService.fetchCapacityDataFromDB(kpiRequest, leafNodeList);
		assertEquals(4, resultList.size());
	}

	@Test
	public void testFetchTicketVelocityDataFromDb() throws ApplicationException {

		KpiRequest kpiRequest = kanbanKpiRequestFactory.findKpiRequest(KPICode.TICKET_VELOCITY.getKpiId());

		KanbanIssueCustomHistoryDataFactory issueHistoryFactory = KanbanIssueCustomHistoryDataFactory.newInstance();
		List<KanbanIssueCustomHistory> issueHistory = issueHistoryFactory.getKanbanIssueCustomHistoryDataList();
		when(kanbanJiraIssueHistoryRepository.findIssuesByStatusAndDate(any(), any(), any(), any(), any()))
				.thenReturn(issueHistory);

		TreeAggregatorDetail treeAggregatorDetail = KPIHelperUtil.getTreeLeafNodesGroupedByFilter(kpiRequest,
				new ArrayList<>(), ahdKanbanList, "hierarchyLevelOne", 5);
		List<Node> leafNodeList = new ArrayList<>();
		leafNodeList = KPIHelperUtil.getLeafNodes(treeAggregatorDetail.getRoot(), leafNodeList, false);

		Map<String, Object> resultMap = kpiHelperService.fetchTicketVelocityDataFromDb(leafNodeList, "", "");
		assertEquals(2, resultMap.size());
	}

	@Test
	public void testFetchTeamCapacityDataFromDb() throws ApplicationException {

		KpiRequest kpiRequest = kanbanKpiRequestFactory.findKpiRequest(KPICode.TEAM_CAPACITY.getKpiId());

		when(kanbanCapacityRepository.findIssuesByType(any(), any(), any())).thenReturn(new ArrayList<>());

		TreeAggregatorDetail treeAggregatorDetail = KPIHelperUtil.getTreeLeafNodesGroupedByFilter(kpiRequest,
				new ArrayList<>(), ahdKanbanList, "hierarchyLevelOne", 5);
		List<Node> leafNodeList = new ArrayList<>();
		leafNodeList = KPIHelperUtil.getLeafNodes(treeAggregatorDetail.getRoot(), leafNodeList, false);

		Map<String, Object> resultMap = kpiHelperService.fetchTeamCapacityDataFromDb(leafNodeList, "", "", kpiRequest,
				"");
		assertEquals(2, resultMap.size());
	}

	@Test
	public void testProcessStoryData() {
		List<JiraHistoryChangeLog> statusChangeLogs = new ArrayList<>();
		JiraHistoryChangeLog jiraHistoryChangeLog1 = new JiraHistoryChangeLog();
		jiraHistoryChangeLog1.setChangedFrom("success");
		jiraHistoryChangeLog1.setChangedTo("fromStatus");
		jiraHistoryChangeLog1.setUpdatedOn(LocalDateTime.now());
		JiraHistoryChangeLog jiraHistoryChangeLog2 = new JiraHistoryChangeLog();
		jiraHistoryChangeLog2.setChangedFrom("success");
		jiraHistoryChangeLog2.setChangedTo("fromStatus");
		jiraHistoryChangeLog2.setUpdatedOn(LocalDateTime.now());
		statusChangeLogs.add(jiraHistoryChangeLog1);
		statusChangeLogs.add(jiraHistoryChangeLog2);
		JiraIssueCustomHistory jiraIssueCustomHistory = new JiraIssueCustomHistory();
		jiraIssueCustomHistory.setStatusUpdationLog(statusChangeLogs);
		double result = kpiHelperService.processStoryData(jiraIssueCustomHistory, "fromStatus", "fromStatus");
		assertEquals(0.0, result, 0);
	}

	@Test
	public void testProcessStoryDataElseCondition() {
		List<JiraHistoryChangeLog> statusChangeLogs = new ArrayList<>();
		JiraHistoryChangeLog jiraHistoryChangeLog1 = new JiraHistoryChangeLog();
		jiraHistoryChangeLog1.setChangedFrom("success");
		jiraHistoryChangeLog1.setChangedTo("fromStatus");
		jiraHistoryChangeLog1.setUpdatedOn(LocalDateTime.now());
		statusChangeLogs.add(jiraHistoryChangeLog1);
		JiraIssueCustomHistory jiraIssueCustomHistory = new JiraIssueCustomHistory();
		jiraIssueCustomHistory.setStatusUpdationLog(statusChangeLogs);
		jiraIssueCustomHistory.setCreatedDate(DateTime.now());
		double result = kpiHelperService.processStoryData(jiraIssueCustomHistory, "fromStatus", "fromStatus");
		assertEquals(0.0, result, 0);
	}

	@Test
	public void fetchKpiMasterList() {

		when(configHelperService.loadKpiMaster()).thenReturn(Arrays.asList(createKpiMaster()));
		MasterResponse masterResponse = kpiHelperService.fetchKpiMasterList();

		KpiMaster kpiMaster = masterResponse.getKpiList().get(0);
		assertEquals("kpi14", kpiMaster.getKpiId());
	}

	private KpiMaster createKpiMaster() {
		KpiMasterDataFactory kpiMasterDataFactory = KpiMasterDataFactory.newInstance();
		KpiMaster kpiMaster = kpiMasterDataFactory.getKpiList().get(0);
		return kpiMaster;
	}

	@Test
	public void testKpiResolution() {

		KpiRequest kpiRequest = kpiRequestFactory.findKpiRequest(KPICode.CREATED_VS_RESOLVED_DEFECTS.getKpiId());
		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId("kpi126");
		kpiMaster.setKpiName("abc");
		kpiMaster.setKpiSource("abc");
		kpiMaster.setKpiUnit("123");
		kpiMaster.setKpiCategory("abc");
		kpiMaster.setMaxValue("abbc");
		List<KpiMaster> kpiMasters = new ArrayList<>();
		kpiMasters.add(kpiMaster);
		when(configHelperService.loadKpiMaster()).thenReturn(kpiMasters);
		kpiHelperService.kpiResolution(kpiRequest.getKpiList());
	}

	@Test
	public void fetchFieldMappingStructureByKpiFieldMappingData() {
		when(configHelperService.loadFieldMappingStructure()).thenReturn(fieldMappingStructureList);
		assertNotNull(
				kpiHelperService.fetchFieldMappingStructureByKpiId("6335363749794a18e8a4479c", "kpi0"));
	}

	@Test
	public void updateKPISource() {
		Map<ObjectId, Map<String, List<ProjectToolConfig>>> toolMap = new HashMap<>();
		Map<String, List<ProjectToolConfig>> projectTool = new HashMap<>();
		ProjectToolConfig jira = new ProjectToolConfig();
		jira.setId(new ObjectId("6335363749794a18e8a4479c"));
		jira.setTestCaseStatus(Arrays.asList("test1"));
		List<ProjectToolConfig> projectToolConfigs = new ArrayList<>();
		projectToolConfigs.add(jira);
		projectTool.put("Jira", projectToolConfigs);
		toolMap.put(new ObjectId("6335363749794a18e8a4479b"), projectTool);

		when(cacheService.cacheProjectToolConfigMapData()).thenReturn(toolMap);
		kpiHelperService.updateKPISource(new ObjectId("6335363749794a18e8a4479b"),
				new ObjectId("6335363749794a18e8a4479c"));
	}

	@Test
	public void updateKpiSourceNull() {
		Map<ObjectId, Map<String, List<ProjectToolConfig>>> toolMap = new HashMap<>();
		Map<String, List<ProjectToolConfig>> projectTool = new HashMap<>();
		ProjectToolConfig jira = new ProjectToolConfig();
		jira.setId(new ObjectId("6335363749794a18e8a4479c"));
		jira.setTestCaseStatus(Arrays.asList("test1"));
		List<ProjectToolConfig> projectToolConfigs = new ArrayList<>();
		projectToolConfigs.add(jira);
		projectTool.put("Jira", projectToolConfigs);
		toolMap.put(new ObjectId("6335363749794a18e8a4479b"), projectTool);

		when(cacheService.cacheProjectToolConfigMapData()).thenReturn(null);
		kpiHelperService.updateKPISource(new ObjectId("6335363749794a18e8a4479b"),
				new ObjectId("6335363749794a18e8a4479c"));
	}

	@Test
	public void updateKpiSourceNoProject() {
		Map<ObjectId, Map<String, List<ProjectToolConfig>>> toolMap = new HashMap<>();
		Map<String, List<ProjectToolConfig>> projectTool = new HashMap<>();
		ProjectToolConfig jira = new ProjectToolConfig();
		jira.setId(new ObjectId("6335363749794a18e8a4479c"));
		jira.setTestCaseStatus(Arrays.asList("test1"));
		List<ProjectToolConfig> projectToolConfigs = new ArrayList<>();
		projectToolConfigs.add(jira);
		projectTool.put("Jira", projectToolConfigs);
		toolMap.put(new ObjectId("6335363749794a18e8a4479b"), projectTool);

		when(cacheService.cacheProjectToolConfigMapData()).thenReturn(null);
		kpiHelperService.updateKPISource(new ObjectId("6335363749794a18e8a4479c"),
				new ObjectId("6335363749794a18e8a4479c"));
	}

	@Test
	public void testFetchJiraCustomHistoryDataFromDbForKanban() throws ApplicationException {

		KpiRequest kpiRequest = kanbanKpiRequestFactory.findKpiRequest(KPICode.TEAM_CAPACITY.getKpiId());

		TreeAggregatorDetail treeAggregatorDetail = KPIHelperUtil.getTreeLeafNodesGroupedByFilter(kpiRequest,
				new ArrayList<>(), ahdKanbanList, "hierarchyLevelOne", 5);
		List<Node> leafNodeList = new ArrayList<>();
		leafNodeList = KPIHelperUtil.getLeafNodes(treeAggregatorDetail.getRoot(), leafNodeList, true);
		Map<ObjectId, Map<String, Object>> projectMap = new HashMap<>();
		Map<String, Object> fieldMappingMap = new HashMap<>();
		fieldMappingMap.put("ClosedStatus", Arrays.asList("Closed", "Dropped"));
		fieldMappingMap.put("LiveStatus", "Live");
		fieldMappingMap.put("RejectedStatus", Arrays.asList("Dropped"));
		fieldMappingMap.put("RCA_Count_IssueType", Arrays.asList("Defect"));
		leafNodeList
				.forEach(node -> projectMap.put(node.getProjectFilter().getBasicProjectConfigId(), fieldMappingMap));
		Map<String, Object> resultMap = kpiHelperService.fetchJiraCustomHistoryDataFromDbForKanban(leafNodeList, "", "",
				kpiRequest, "rca", projectMap);
		assertEquals(5, resultMap.size());
	}

	@Test
	public void testComputeProjectWiseJiraHistoryByStatusAndDate() {

		KanbanIssueCustomHistoryDataFactory issueHistoryFactory = KanbanIssueCustomHistoryDataFactory.newInstance();
		List<KanbanIssueCustomHistory> issueHistory = issueHistoryFactory.getKanbanIssueCustomHistoryDataList();
		Map<String, List<KanbanIssueCustomHistory>> projectWiseNonClosedTickets = new HashMap<>();
		issueHistory.forEach(issue -> projectWiseNonClosedTickets.put(issue.getBasicProjectConfigId(), issueHistory));
		Map<String, Object> historyDataResultMap = new HashMap<>();
		Map<String, String> projectWiseOpenStatus = new HashMap<>();
		projectWiseNonClosedTickets.keySet().forEach(key -> projectWiseOpenStatus.put(key, "Open"));
		historyDataResultMap.put("projectWiseOpenStatus", projectWiseOpenStatus);

		Map<String, Map<String, Map<String, Set<String>>>> result = kpiHelperService
				.computeProjectWiseJiraHistoryByStatusAndDate(projectWiseNonClosedTickets, "2022-07-01",
						historyDataResultMap);
		assertNotNull(result);
	}

	@Test
	public void testComputeProjectWiseJiraHistoryByFieldAndDate() {

		KanbanIssueCustomHistoryDataFactory issueHistoryFactory = KanbanIssueCustomHistoryDataFactory.newInstance();
		List<KanbanIssueCustomHistory> issueHistory = issueHistoryFactory.getKanbanIssueCustomHistoryDataList();
		Map<String, List<KanbanIssueCustomHistory>> projectWiseNonClosedTickets = new HashMap<>();
		issueHistory.forEach(issue -> projectWiseNonClosedTickets.put(issue.getBasicProjectConfigId(), issueHistory));
		Map<String, Object> historyDataResultMap = new HashMap<>();
		Map<String, String> projectWiseOpenStatus = new HashMap<>();
		projectWiseNonClosedTickets.keySet().forEach(key -> projectWiseOpenStatus.put(key, "Open"));
		historyDataResultMap.put("projectWiseOpenStatus", projectWiseOpenStatus);

		Map<String, List<String>> projectWiseClosedStatus = new HashMap<>();
		projectWiseNonClosedTickets.keySet()
				.forEach(key -> projectWiseClosedStatus.put(key, Arrays.asList("Closed", "Dropped")));
		historyDataResultMap.put("projectWiseClosedStoryStatus", projectWiseClosedStatus);

		Map<String, Map<String, Map<String, Set<String>>>> result = kpiHelperService
				.computeProjectWiseJiraHistoryByFieldAndDate(projectWiseNonClosedTickets, "2022-07-01",
						historyDataResultMap, "rca");
		assertNotNull(result);
	}

	@Test
	public void getProjectKeyCache() {
		KpiRequest kpiRequest = kpiRequestFactory.findKpiRequest(KPICode.DEFECT_INJECTION_RATE.getKpiId());
		when(authorizedProjectsService.ifSuperAdminUser()).thenReturn(true);
		kpiHelperService.getProjectKeyCache(kpiRequest, accountHierarchyDataList, true);
	}

	@Test
	public void getProjectKeyCache2() {
		KpiRequest kpiRequest = kpiRequestFactory.findKpiRequest(KPICode.DEFECT_INJECTION_RATE.getKpiId());
		when(authorizedProjectsService.ifSuperAdminUser()).thenReturn(false);
		String[] projectKey = new String[0];
		when(authorizedProjectsService.getProjectKey(accountHierarchyDataList, kpiRequest)).thenReturn(projectKey);
		kpiHelperService.getProjectKeyCache(kpiRequest, accountHierarchyDataList, true);
	}

	@Test
	public void getAuthorizedFilteredList() {
		KpiRequest kpiRequest = kpiRequestFactory.findKpiRequest(KPICode.DEFECT_INJECTION_RATE.getKpiId());
		when(authorizedProjectsService.ifSuperAdminUser()).thenReturn(false);
		when(authorizedProjectsService.filterProjects(any())).thenReturn(accountHierarchyDataList);
		kpiHelperService.getAuthorizedFilteredList(kpiRequest, accountHierarchyDataList, true);
	}

	@Test
	public void setIntoApplicationCache() {
		KpiRequest kpiRequest = createKpiRequest(5);
		Map<String, Integer> map = new HashMap<>();
		map.put("sprint", 5);
		when(flterHelperService.getHierarchyIdLevelMap(anyBoolean())).thenReturn(map);
		kpiHelperService.setIntoApplicationCache(kpiRequest, new ArrayList<>(), 1, new String[0]);
	}

	private KpiRequest createKpiRequest(int level) {
		KpiRequest kpiRequest = new KpiRequest();
		List<KpiElement> kpiList = new ArrayList<>();

		addKpiElement(kpiList, KPICode.ITERATION_BURNUP.getKpiId(), KPICode.ITERATION_BURNUP.name(), "Iteration", "");
		kpiRequest.setLevel(level);
		kpiRequest.setIds(new String[] { "38296_Scrum Project_6335363749794a18e8a4479b" });
		kpiRequest.setKpiList(kpiList);
		kpiRequest.setRequestTrackerId();
		kpiRequest.setLabel("sprint");
		Map<String, List<String>> s = new HashMap<>();
		s.put("sprint", Arrays.asList("38296_Scrum Project_6335363749794a18e8a4479b"));
		kpiRequest.setSelectedMap(s);
		kpiRequest.setSprintIncluded(Arrays.asList("CLOSED", "ACTIVE"));
		return kpiRequest;
	}

	private void addKpiElement(List<KpiElement> kpiList, String kpiId, String kpiName, String category,
			String kpiUnit) {
		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId(kpiId);
		kpiElement.setKpiName(kpiName);
		kpiElement.setKpiCategory(category);
		kpiElement.setKpiUnit(kpiUnit);
		kpiElement.setKpiSource("Jira");
		kpiElement.setGroupId(1);
		kpiElement.setMaxValue("500");
		kpiElement.setChartType("gaugeChart");
		kpiList.add(kpiElement);
	}

	@Test
	public void getScmToolJobsReturnsAllTools() {
		setToolMap();
		Node node = new Node();
		List<Tool> result = kpiHelperService.getScmToolJobs(toolMap.get(new ObjectId("6335363749794a18e8a4479b")),
				node);
		assertEquals(1, result.size());
	}

	@Test
	public void getScmToolJobsReturnsEmptyListWhenNoTools() {
		Map<String, List<Tool>> toolListMap = new HashMap<>();
		Node node = new Node();
		List<Tool> result = kpiHelperService.getScmToolJobs(toolListMap, node);
		assertTrue(result.isEmpty());
	}

	@Test
	public void populateSCMToolsRepoListReturnsAllTools() {
		setToolMap();
		List<Tool> result = kpiHelperService
				.populateSCMToolsRepoList(toolMap.get(new ObjectId("6335363749794a18e8a4479b")));
		assertEquals(1, result.size());
	}

	@Test
	public void populateSCMToolsRepoListReturnsEmptyListWhenNoTools() {
		Map<String, List<Tool>> mapOfListOfTools = new HashMap<>();
		List<Tool> result = kpiHelperService.populateSCMToolsRepoList(mapOfListOfTools);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testIsRequiredTestToolConfigured_JiraConfigured() {
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479c");
		Map<String, List<ProjectToolConfig>> stringListMap = new HashMap<>();
		stringListMap.put("Jira", Arrays.asList());
		projectConfigMap.put(projectId, stringListMap);
		when(configHelperService.getProjectToolConfigMap()).thenReturn(projectConfigMap);
		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(configHelperService.getFieldMappingMap()).thenReturn(Map.of(projectId, fieldMapping));
		when(fieldMapping.isUploadDataKPI16()).thenReturn(true);

		KpiElement kpiElement = new KpiElement();
		assertTrue(kpiHelperService.isRequiredTestToolConfigured(KPICode.INSPRINT_AUTOMATION_COVERAGE, kpiElement,
				projectId));
	}

	@Test
	public void testIsRequiredTestToolConfigured_JiraConfiguredKanbanRegression() {
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479c");
		Map<String, List<ProjectToolConfig>> stringListMap = new HashMap<>();
		stringListMap.put("Jira", Arrays.asList());
		projectConfigMap.put(projectId, stringListMap);
		when(configHelperService.getProjectToolConfigMap()).thenReturn(projectConfigMap);
		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(configHelperService.getFieldMappingMap()).thenReturn(Map.of(projectId, fieldMapping));
		when(fieldMapping.isUploadDataKPI42()).thenReturn(true);

		KpiElement kpiElement = new KpiElement();
		assertTrue(kpiHelperService.isRequiredTestToolConfigured(KPICode.REGRESSION_AUTOMATION_COVERAGE, kpiElement,
				projectId));
	}

	@Test
	public void testIsRequiredTestToolConfigured_JiraNotConfigured() {
		// Mock data
		when(configHelperService.getProjectToolConfigMap()).thenReturn(new HashMap<>());

		KpiElement kpiElement = new KpiElement();
		assertFalse(
				kpiHelperService.isRequiredTestToolConfigured(
						KPICode.REGRESSION_AUTOMATION_COVERAGE,
						kpiElement,
						new ObjectId("6335363749794a18e8a4479c")));
	}

	@Test
	public void testIsZephyrRequiredToolConfigured_JiraConfigured() {
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479c");
		Map<String, List<ProjectToolConfig>> stringListMap = new HashMap<>();
		stringListMap.put("ZEPHYR", Arrays.asList());
		stringListMap.put("JIRA", Arrays.asList());
		projectConfigMap.put(projectId, stringListMap);
		when(configHelperService.getProjectToolConfigMap()).thenReturn(projectConfigMap);
		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(configHelperService.getFieldMappingMap()).thenReturn(Map.of(projectId, fieldMapping));
		KpiElement kpiElement = new KpiElement();
		assertTrue(kpiHelperService.isRequiredTestToolConfigured(KPICode.KANBAN_REGRESSION_PASS_PERCENTAGE, kpiElement,
				projectId));
	}

	@Test
	public void testIsRequiredTestToolConfigured_JiraTestExecutionConfigured() {
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479c");
		Map<String, List<ProjectToolConfig>> stringListMap = new HashMap<>();
		stringListMap.put("ZEPHYR", Arrays.asList());
		stringListMap.put("JIRA", Arrays.asList());
		projectConfigMap.put(projectId, stringListMap);
		when(configHelperService.getProjectToolConfigMap()).thenReturn(projectConfigMap);
		KpiElement kpiElement = new KpiElement();
		assertTrue(kpiHelperService.isRequiredTestToolConfigured(KPICode.TEST_EXECUTION_AND_PASS_PERCENTAGE, kpiElement,
				projectId));
	}

	@Test
	public void testIsZephyrRequiredToolConfigured_AzureConfigured() {
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479c");
		Map<String, List<ProjectToolConfig>> stringListMap = new HashMap<>();
		stringListMap.put("ZEPHYR", Arrays.asList());
		stringListMap.put("Azure", Arrays.asList());
		projectConfigMap.put(projectId, stringListMap);
		when(configHelperService.getProjectToolConfigMap()).thenReturn(projectConfigMap);
		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(configHelperService.getFieldMappingMap()).thenReturn(Map.of(projectId, fieldMapping));
		KpiElement kpiElement = new KpiElement();
		assertTrue(kpiHelperService.isRequiredTestToolConfigured(KPICode.INSPRINT_AUTOMATION_COVERAGE, kpiElement,
				projectId));
	}

	private void setToolMap() {
		toolList3 = new ArrayList<>();

		ProcessorItem processorItem = new ProcessorItem();
		processorItem.setProcessorId(new ObjectId("63282180160f5b4eb2ac380b"));
		processorItem.setId(new ObjectId("633ab3fb26878c56f03ebd36"));

		ProcessorItem processorItem1 = new ProcessorItem();
		processorItem1.setProcessorId(new ObjectId("63378301e7d2665a7944f675"));
		processorItem1.setId(new ObjectId("633abcd1e7d2665a7944f678"));

		List<ProcessorItem> collectorItemList = new ArrayList<>();
		collectorItemList.add(processorItem);
		List<ProcessorItem> collectorItemList1 = new ArrayList<>();
		collectorItemList1.add(processorItem1);

		tool3 = createTool("url3", "Bitbucket", collectorItemList1);

		toolList3.add(tool3);

		toolGroup.put(Constant.TOOL_BITBUCKET, toolList3);
		toolGroup.put(Constant.TOOL_AZUREREPO, toolList1);
		toolGroup.put(Constant.REPO_TOOLS, toolList2);
		toolMap.put(new ObjectId("6335363749794a18e8a4479b"), toolGroup);
	}

	private Tool createTool(String url, String toolType, List<ProcessorItem> collectorItemList) {
		Tool tool = new Tool();
		tool.setTool(toolType);
		tool.setUrl(url);
		tool.setBranch("master");
		tool.setRepositoryName("PSknowHOW");

		tool.setProcessorItemList(collectorItemList);
		return tool;
	}
}
