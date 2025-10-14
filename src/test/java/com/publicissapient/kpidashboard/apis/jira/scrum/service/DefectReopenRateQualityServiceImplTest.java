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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import com.publicissapient.kpidashboard.apis.common.service.CommonService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.FieldMappingDataFactory;
import com.publicissapient.kpidashboard.apis.data.JiraIssueDataFactory;
import com.publicissapient.kpidashboard.apis.data.JiraIssueHistoryDataFactory;
import com.publicissapient.kpidashboard.apis.data.KpiRequestFactory;
import com.publicissapient.kpidashboard.apis.data.SprintDetailsDataFactory;
import com.publicissapient.kpidashboard.apis.data.SprintWiseStoryDataFactory;
import com.publicissapient.kpidashboard.apis.enums.Filters;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintWiseStory;
import com.publicissapient.kpidashboard.common.repository.application.FieldMappingRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;

@RunWith(MockitoJUnitRunner.class)
public class DefectReopenRateQualityServiceImplTest {

	public Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
	public Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();
	List<JiraIssue> totalBugList = new ArrayList<>();
	List<JiraIssue> jiraIssueList = new ArrayList<>();
	String P1 = "p1,p1-blocker,blocker, 1, 0, p0";
	String P2 = "p2, critical, p2-critical, 2";
	String P3 = "p3, p3-major, major, 3";
	String P4 = "p4, p4-minor, minor, 4, p5-trivial, 5,trivial";
	@Mock JiraIssueRepository jiraIssueRepository;
	@Mock JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;
	@Mock CacheService cacheService;
	@Mock ConfigHelperService configHelperService;
	@Mock KpiHelperService kpiHelperService;
	@InjectMocks DefectReopenRateQualityServiceImpl defectReopenRateQualityService;
	@Mock ProjectBasicConfigRepository projectConfigRepository;
	@Mock FieldMappingRepository fieldMappingRepository;
	@Mock SprintRepository sprintRepository;
	@Mock CustomApiConfig customApiConfig;
	private List<SprintWiseStory> sprintWiseStoryList = new ArrayList<>();
	private List<AccountHierarchyData> accountHierarchyDataList = new ArrayList<>();
	private Map<String, Object> filterLevelMap;
	private KpiRequest kpiRequest;
	private Map<String, String> kpiWiseAggregation = new HashMap<>();
	private List<DataCount> trendValues = new ArrayList<>();
	private List<SprintDetails> sprintDetailsList = new ArrayList<>();
	private List<ProjectBasicConfig> projectConfigList = new ArrayList<>();
	private List<JiraIssueCustomHistory> jiraIssueCustomHistoryList = new ArrayList<>();
	@Mock private FilterHelperService filterHelperService;
	@Mock private CommonService commonService;

	@Before
	public void setup() {

		KpiRequestFactory kpiRequestFactory = KpiRequestFactory.newInstance("");
		kpiRequest = kpiRequestFactory.findKpiRequest(KPICode.DEFECT_COUNT_BY_PRIORITY.getKpiId());
		kpiRequest.setLabel("PROJECT");

		ProjectBasicConfig projectBasicConfig = new ProjectBasicConfig();
		projectBasicConfig.setId(new ObjectId("6335363749794a18e8a4479b"));
		projectBasicConfig.setIsKanban(true);
		projectBasicConfig.setProjectName("Scrum Project");
		projectBasicConfig.setProjectNodeId("Scrum Project_6335363749794a18e8a4479b");
		projectConfigList.add(projectBasicConfig);

		projectConfigList.forEach(
				projectConfig -> {
					projectConfigMap.put(projectConfig.getProjectName(), projectConfig);
				});
		Mockito.when(cacheService.cacheProjectConfigMapData()).thenReturn(projectConfigMap);

		AccountHierarchyFilterDataFactory accountHierarchyFilterDataFactory =
				AccountHierarchyFilterDataFactory.newInstance();
		accountHierarchyDataList = accountHierarchyFilterDataFactory.getAccountHierarchyDataList();
		SprintDetailsDataFactory sprintDetailsDataFactory = SprintDetailsDataFactory.newInstance();
		sprintDetailsList = sprintDetailsDataFactory.getSprintDetails();
		filterLevelMap = new LinkedHashMap<>();
		filterLevelMap.put("PROJECT", Filters.PROJECT);
		filterLevelMap.put("SPRINT", Filters.SPRINT);

		FieldMappingDataFactory fieldMappingDataFactory =
				FieldMappingDataFactory.newInstance("/json/default/scrum_project_field_mappings.json");
		FieldMapping fieldMapping = fieldMappingDataFactory.getFieldMappings().get(0);
		fieldMappingMap.put(fieldMapping.getBasicProjectConfigId(), fieldMapping);
		configHelperService.setProjectConfigMap(projectConfigMap);
		configHelperService.setFieldMappingMap(fieldMappingMap);

		JiraIssueDataFactory jiraIssueDataFactory = JiraIssueDataFactory.newInstance();
		totalBugList = jiraIssueDataFactory.getBugs();
		jiraIssueList = jiraIssueDataFactory.getJiraIssues();

		SprintWiseStoryDataFactory sprintWiseStoryDataFactory =
				SprintWiseStoryDataFactory.newInstance();
		sprintWiseStoryList = sprintWiseStoryDataFactory.getSprintWiseStories();

		JiraIssueHistoryDataFactory jiraIssueHistoryDataFactory =
				JiraIssueHistoryDataFactory.newInstance();
		jiraIssueCustomHistoryList = jiraIssueHistoryDataFactory.getJiraIssueCustomHistory();

		// setTreadValuesDataCount();
	}

	@After
	public void cleanup() {
		jiraIssueRepository.deleteAll();
	}

	@Test
	public void testGetKPIData() throws ApplicationException {

		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

		Map<String, List<String>> maturityRangeMap = new HashMap<>();
		maturityRangeMap.put(
				"defectCountByPriority", Arrays.asList("-390", "390-309", "309-221", "221-140", "140-"));
		maturityRangeMap.put("defectPriorityWeight", Arrays.asList("10", "7", "5", "3"));

		when(jiraIssueRepository.findIssuesByFilterAndProjectMapFilter(Mockito.any(), Mockito.any()))
				.thenReturn(jiraIssueList);

		fieldMappingMap.forEach(
				(k, v) -> {
					v.setIncludeRCAForKPI35(Arrays.asList("code issue"));
					v.setDefectPriorityKPI35(Arrays.asList("P3"));
				});
		when(sprintRepository.findBySprintIDIn(any())).thenReturn(sprintDetailsList);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(configHelperService.getFieldMapping(any()))
				.thenReturn(fieldMappingMap.get(new ObjectId("6335363749794a18e8a4479b")));
		when(jiraIssueCustomHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(
						Mockito.any(), Mockito.any()))
				.thenReturn(jiraIssueCustomHistoryList);
		String kpiRequestTrackerId = "Excel-Jira-5be544de025de212549176a9";
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
				.thenReturn(kpiRequestTrackerId);
		when(defectReopenRateQualityService.getRequestTrackerId()).thenReturn(kpiRequestTrackerId);
		when(customApiConfig.getpriorityP1()).thenReturn(P1);
		when(customApiConfig.getpriorityP2()).thenReturn(P2);
		when(customApiConfig.getpriorityP3()).thenReturn(P3);
		when(customApiConfig.getpriorityP4()).thenReturn(P4);

		try {
			KpiElement kpiElement =
					defectReopenRateQualityService.getKpiData(
							kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

			((List<DataCount>) kpiElement.getTrendValueList())
					.forEach(
							dc -> {
								String priority = dc.getData();
								switch (priority) {
									case "High":
										assertThat("DC Value :", dc.getCount(), equalTo(1));
										break;
									case "Low":
										assertThat("DC Value :", dc.getCount(), equalTo(1));
										break;
									case "Medium":
										assertThat("DC Value :", dc.getCount(), equalTo(1));
										break;
									case "Critical":
										assertThat("DC Value :", dc.getCount(), equalTo(1));
										break;

									default:
										break;
								}
							});

		} catch (ApplicationException enfe) {

		}
	}

	@Test
	public void testGetQualifierType() {
		assertThat(
				defectReopenRateQualityService.getQualifierType(),
				equalTo(KPICode.DEFECT_REOPEN_RATE_QUALITY.name()));
	}

	@Test
	public void testFetchKPIDataFromDbData() throws ApplicationException {
		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		List<Node> leafNodeList = new ArrayList<>();
		leafNodeList = KPIHelperUtil.getLeafNodes(treeAggregatorDetail.getRoot(), leafNodeList, false);
		String startDate = leafNodeList.get(0).getSprintFilter().getStartDate();
		String endDate = leafNodeList.get(leafNodeList.size() - 1).getSprintFilter().getEndDate();
		fieldMappingMap.forEach(
				(k, v) -> {
					v.setIncludeRCAForKPI35(Arrays.asList("code issue"));
					v.setDefectPriorityKPI35(Arrays.asList("P3"));

					v.setJiraDefectRejectionStatusKPI190("rejected");
					v.setResolutionTypeForRejectionKPI190(
							Arrays.asList("Invalid", "Duplicate", "Unrequired"));
				});
		when(sprintRepository.findBySprintIDIn(any())).thenReturn(sprintDetailsList);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(configHelperService.getFieldMapping(any()))
				.thenReturn(fieldMappingMap.get(new ObjectId("6335363749794a18e8a4479b")));
		when(jiraIssueCustomHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(
						Mockito.any(), Mockito.any()))
				.thenReturn(jiraIssueCustomHistoryList);
		String kpiRequestTrackerId = "Excel-Jira-5be544de025de212549176a9";
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
				.thenReturn(kpiRequestTrackerId);
		Map<String, Object> defectDataListMap =
				defectReopenRateQualityService.fetchKPIDataFromDb(
						leafNodeList, startDate, endDate, kpiRequest);
		assertThat(
				"Total Defects value :",
				((List<JiraIssue>) (defectDataListMap.get("totalDefectWithoutDrop"))).size(),
				equalTo(0));
	}
}
