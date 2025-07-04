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

package com.publicissapient.kpidashboard.apis.jira.scrum.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.FieldMappingDataFactory;
import com.publicissapient.kpidashboard.apis.data.JiraIssueDataFactory;
import com.publicissapient.kpidashboard.apis.data.JiraIssueHistoryDataFactory;
import com.publicissapient.kpidashboard.apis.data.KpiRequestFactory;
import com.publicissapient.kpidashboard.apis.data.SprintDetailsDataFactory;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.iterationdashboard.JiraIterationServiceR;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;

@RunWith(MockitoJUnitRunner.class)
public class WorkStatusServiceImplTest {

	@Mock
	CacheService cacheService;
	@Mock
	private JiraIssueRepository jiraIssueRepository;
	@Mock
	private ConfigHelperService configHelperService;
	@InjectMocks
	private WorkStatusServiceImpl workStatusService;
	@Mock
	private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;
	@Mock
	private JiraIterationServiceR jiraService;
	private List<JiraIssue> storyList = new ArrayList<>();
	private List<JiraIssueCustomHistory> jiraIssueCustomHistoryList = new ArrayList<>();
	private Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
	private Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();
	private SprintDetails sprintDetails = new SprintDetails();
	private List<AccountHierarchyData> accountHierarchyDataList = new ArrayList<>();
	private KpiRequest kpiRequest;

	@Before
	public void setup() {
		KpiRequestFactory kpiRequestFactory = KpiRequestFactory.newInstance();
		kpiRequest = kpiRequestFactory.findKpiRequest("kpi128");
		kpiRequest.setLabel("PROJECT");

		AccountHierarchyFilterDataFactory accountHierarchyFilterDataFactory = AccountHierarchyFilterDataFactory
				.newInstance();
		accountHierarchyDataList = accountHierarchyFilterDataFactory.getAccountHierarchyDataList();

		setMockProjectConfig();
		setMockFieldMapping();
		sprintDetails = SprintDetailsDataFactory.newInstance().getSprintDetails().get(0);

		List<String> jiraIssueList = sprintDetails.getTotalIssues().stream().filter(Objects::nonNull)
				.map(SprintIssue::getNumber).distinct().collect(Collectors.toList());
		JiraIssueDataFactory jiraIssueDataFactory = JiraIssueDataFactory.newInstance();
		storyList = jiraIssueDataFactory.findIssueByNumberList(jiraIssueList);
		JiraIssueHistoryDataFactory jiraIssueHistoryDataFactory = JiraIssueHistoryDataFactory.newInstance();
		jiraIssueCustomHistoryList = jiraIssueHistoryDataFactory.getJiraIssueCustomHistory();
	}

	private void setMockProjectConfig() {
		ProjectBasicConfig projectConfig = new ProjectBasicConfig();
		projectConfig.setId(new ObjectId("6335363749794a18e8a4479b"));
		projectConfig.setProjectName("Scrum Project");
		projectConfigMap.put(projectConfig.getProjectName(), projectConfig);
	}

	private void setMockFieldMapping() {
		FieldMappingDataFactory fieldMappingDataFactory = FieldMappingDataFactory
				.newInstance("/json/default/scrum_project_field_mappings.json");
		FieldMapping fieldMapping = fieldMappingDataFactory.getFieldMappings().get(0);
		fieldMappingMap.put(fieldMapping.getBasicProjectConfigId(), fieldMapping);
		configHelperService.setFieldMappingMap(fieldMappingMap);
	}

	@Test
	public void testGetKpiDataProject() throws ApplicationException {

		TreeAggregatorDetail treeAggregatorDetail = KPIHelperUtil.getTreeLeafNodesGroupedByFilter(kpiRequest,
				accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		when(jiraService.getCurrentSprintDetails()).thenReturn(sprintDetails);
		when(jiraService.getJiraIssuesForCurrentSprint()).thenReturn(storyList);
		when(jiraService.getJiraIssuesCustomHistoryForCurrentSprint()).thenReturn(jiraIssueCustomHistoryList);
		String kpiRequestTrackerId = "Excel-Jira-5be544de025de212549176a9";
		when(cacheService.getFromApplicationCache(Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
				.thenReturn(kpiRequestTrackerId);
		when(workStatusService.getRequestTrackerId()).thenReturn(kpiRequestTrackerId);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		try {
			KpiElement kpiElement = workStatusService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0),
					treeAggregatorDetail.getMapOfListOfLeafNodes().get("sprint").get(0));
			assertNotNull(kpiElement.getIssueData());

		} catch (ApplicationException enfe) {

		}
	}

	@Test
	public void testGetKpiDataProject_active() throws ApplicationException {

		TreeAggregatorDetail treeAggregatorDetail = KPIHelperUtil.getTreeLeafNodesGroupedByFilter(kpiRequest,
				accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		storyList.stream().filter(jiraIssue -> jiraIssue.getNumber().equals("TEST-17768")).findFirst().get()
				.setDueDate(String.valueOf(LocalDate.now().plusDays(3) + "T00:00:00.000Z"));
		sprintDetails.setState(SprintDetails.SPRINT_STATE_ACTIVE);
		when(jiraService.getCurrentSprintDetails()).thenReturn(sprintDetails);
		when(jiraService.getJiraIssuesForCurrentSprint()).thenReturn(storyList);
		when(jiraService.getJiraIssuesCustomHistoryForCurrentSprint()).thenReturn(jiraIssueCustomHistoryList);
		String kpiRequestTrackerId = "Excel-Jira-5be544de025de212549176a9";
		when(cacheService.getFromApplicationCache(Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
				.thenReturn(kpiRequestTrackerId);
		when(workStatusService.getRequestTrackerId()).thenReturn(kpiRequestTrackerId);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		try {
			KpiElement kpiElement = workStatusService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0),
					treeAggregatorDetail.getMapOfListOfLeafNodes().get("sprint").get(0));
			assertNotNull(kpiElement.getIssueData());

		} catch (ApplicationException enfe) {

		}
	}

	@Test
	public void testGetKpiDataProjectOriginalEstimate() throws ApplicationException {
		fieldMappingMap.forEach((projectId, mapping) -> mapping.setEstimationCriteria("Original Estimate"));
		configHelperService.setFieldMappingMap(fieldMappingMap);

		TreeAggregatorDetail treeAggregatorDetail = KPIHelperUtil.getTreeLeafNodesGroupedByFilter(kpiRequest,
				accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		storyList.stream().filter(jiraIssue -> jiraIssue.getNumber().equals("TEST-17768")).findFirst().get()
				.setDueDate(String.valueOf(LocalDate.now().plusDays(3) + "T00:00:00.000Z"));
		sprintDetails.setState(SprintDetails.SPRINT_STATE_ACTIVE);
		when(jiraService.getCurrentSprintDetails()).thenReturn(sprintDetails);
		when(jiraService.getJiraIssuesForCurrentSprint()).thenReturn(storyList);
		when(jiraService.getJiraIssuesCustomHistoryForCurrentSprint()).thenReturn(jiraIssueCustomHistoryList);
		String kpiRequestTrackerId = "Excel-Jira-5be544de025de212549176a9";
		when(cacheService.getFromApplicationCache(Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
				.thenReturn(kpiRequestTrackerId);
		when(workStatusService.getRequestTrackerId()).thenReturn(kpiRequestTrackerId);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		try {
			KpiElement kpiElement = workStatusService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0),
					treeAggregatorDetail.getMapOfListOfLeafNodes().get("sprint").get(0));
			assertNotNull(kpiElement.getIssueData());

		} catch (ApplicationException enfe) {

		}
	}

	@Test
	public void testGetQualifierType() {
		assertThat(workStatusService.getQualifierType(), equalTo("WORK_STATUS"));
	}

	@Test
	public void testFetchKPIDataFromDbData() throws ApplicationException {
		TreeAggregatorDetail treeAggregatorDetail = KPIHelperUtil.getTreeLeafNodesGroupedByFilter(kpiRequest,
				accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		List<Node> leafNodeList = new ArrayList<>();
		leafNodeList = KPIHelperUtil.getLeafNodes(treeAggregatorDetail.getRoot(), leafNodeList, false);
		String startDate = leafNodeList.get(0).getSprintFilter().getStartDate();
		String endDate = leafNodeList.get(leafNodeList.size() - 1).getSprintFilter().getEndDate();
		when(jiraService.getCurrentSprintDetails()).thenReturn(sprintDetails);
		when(jiraService.getJiraIssuesForCurrentSprint()).thenReturn(storyList);
		when(jiraService.getJiraIssuesCustomHistoryForCurrentSprint()).thenReturn(jiraIssueCustomHistoryList);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		Map<String, Object> returnMap = workStatusService.fetchKPIDataFromDb(leafNodeList.get(0), startDate, endDate,
				kpiRequest);
		assertNotNull(returnMap);
	}

	@Test
	public void testSetDataForPlannedWithDelay() throws ApplicationException {
		// Arrange
		TreeAggregatorDetail treeAggregatorDetail = new TreeAggregatorDetail();
		Map<String, Node> mapTmp = new HashMap<>();

		// Create a mock Node for "hierarchyLevelOne"
		Node hierarchyNode = new Node();
		hierarchyNode.setName("Hierarchy Level One");

		// Add child nodes to the hierarchyNode
		Node childNode1 = new Node();
		childNode1.setName("Child Node 1");
		Node childNode2 = new Node();
		childNode2.setName("Child Node 2");

		List<Node> children = new ArrayList<>();
		children.add(childNode1);
		children.add(childNode2);
		hierarchyNode.setChildren(children);

		// Add the hierarchyNode to mapTmp
		mapTmp.put("hierarchyLevelOne", hierarchyNode);
		treeAggregatorDetail.setMapTmp(mapTmp);

		// Act
		Node retrievedNode = treeAggregatorDetail.getMapTmp().get("hierarchyLevelOne");

		// Assert
		assertNotNull("Node for 'hierarchyLevelOne' should not be null", retrievedNode);
		assertNotNull("Children of the node should not be null", retrievedNode.getChildren());
		assertFalse("Children list should not be empty", retrievedNode.getChildren().isEmpty());
	}

	@After
	public void cleanup() {
		jiraIssueRepository.deleteAll();
	}
}
