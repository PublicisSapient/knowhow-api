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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import java.util.*;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.JiraIssueDataFactory;
import com.publicissapient.kpidashboard.apis.data.KpiRequestFactory;
import com.publicissapient.kpidashboard.apis.data.TestCaseDetailsDataFactory;
import com.publicissapient.kpidashboard.apis.enums.Filters;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.*;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.constant.ProcessorConstants;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintWiseStory;
import com.publicissapient.kpidashboard.common.model.zephyr.TestCaseDetails;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.zephyr.TestCaseDetailsRepository;

@RunWith(MockitoJUnitRunner.class)
public class TestExecutionTimeServiceImplTest {

	private static final String TESTCASEKEY = "testCaseData";
	public Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
	public Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();
	@Mock JiraIssueRepository featureRepository;
	@Mock CacheService cacheService;
	@Mock ConfigHelperService configHelperService;
	@InjectMocks TestExecutionTimeServiceImpl testExecutionTimeServiceImpl;
	@Mock TestCaseDetailsRepository testCaseDetailsRepository;
	List<TestCaseDetails> totalTestCaseList = new ArrayList<>();
	List<TestCaseDetails> automatedTestCaseList = new ArrayList<>();
	List<TestCaseDetails> manualTestCaseList = new ArrayList<>();
	List<JiraIssue> issues = new ArrayList<>();

	private List<FieldMapping> fieldMappingList = new ArrayList<>();
	private KpiRequest kpiRequest;
	private KpiElement kpiElement;
	private Map<String, String> kpiWiseAggregation = new HashMap<>();
	private List<AccountHierarchyData> accountHierarchyDataList = new ArrayList<>();
	private List<ProjectBasicConfig> projectConfigList = new ArrayList<>();

	@Before
	public void setup() {
		KpiRequestFactory kpiRequestFactory = KpiRequestFactory.newInstance();
		kpiRequest = kpiRequestFactory.findKpiRequest("kpi194");
		kpiRequest.setLabel("PROJECT");
		kpiElement = kpiRequest.getKpiList().get(0);
		kpiWiseAggregation.put("defectInjectionRate", "average");
		AccountHierarchyFilterDataFactory accountHierarchyFilterDataFactory =
				AccountHierarchyFilterDataFactory.newInstance();
		accountHierarchyDataList = accountHierarchyFilterDataFactory.getAccountHierarchyDataList();
		totalTestCaseList = TestCaseDetailsDataFactory.newInstance().getTestCaseDetailsList();
		automatedTestCaseList = TestCaseDetailsDataFactory.newInstance().findAutomatedTestCases();
		automatedTestCaseList = TestCaseDetailsDataFactory.newInstance().findAutomatedTestCases();
		manualTestCaseList = TestCaseDetailsDataFactory.newInstance().findManualTestCases();
		issues = new ArrayList<>(JiraIssueDataFactory.newInstance().getStories());
		setMockFieldMapping();
		fieldMappingList.forEach(
				fieldMapping -> {
					fieldMappingMap.put(fieldMapping.getBasicProjectConfigId(), fieldMapping);
				});

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
	}

	@Test
	public void getQualifierTypeTest() {
		String qualifierType = testExecutionTimeServiceImpl.getQualifierType();
		assertThat("Qualifier type :", qualifierType, equalTo(KPICode.TEST_EXECUTION_TIME.name()));
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testFetchKPIDataFromDbData() throws ApplicationException {
		List<Node> leafNodeList = new ArrayList<>();
		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		treeAggregatorDetail
				.getMapOfListOfLeafNodes()
				.forEach(
						(k, v) -> {
							if (Filters.getFilter(k) == Filters.SPRINT) {
								leafNodeList.addAll(v);
							}
						});
		List<SprintWiseStory> sprintWiseStories =
				JiraIssueDataFactory.newInstance().getSprintWiseStories();
		sprintWiseStories.forEach(
				sprintWiseStory -> sprintWiseStory.setBasicProjectConfigId("6335363749794a18e8a4479b"));
		when(featureRepository.findIssuesGroupBySprint(any(), any(), any(), any()))
				.thenReturn(sprintWiseStories);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		Map<ObjectId, Map<String, List<ProjectToolConfig>>> toolMap = new HashMap<>();
		Map<String, List<ProjectToolConfig>> projectTool = new HashMap<>();

		ProjectToolConfig zephyConfig = new ProjectToolConfig();
		zephyConfig.setInSprintAutomationFolderPath(Arrays.asList("test1"));
		projectTool.put(ProcessorConstants.ZEPHYR, Arrays.asList(zephyConfig));
		ProjectToolConfig jiraTest = new ProjectToolConfig();

		jiraTest.setTestCaseStatus(Arrays.asList("test1"));
		projectTool.put(ProcessorConstants.ZEPHYR, Arrays.asList(zephyConfig));
		projectTool.put(ProcessorConstants.JIRA_TEST, Arrays.asList(jiraTest));

		projectTool.put(ProcessorConstants.ZEPHYR, Arrays.asList(zephyConfig));
		projectTool.put(ProcessorConstants.JIRA_TEST, Arrays.asList(jiraTest));
		toolMap.put(new ObjectId("6335363749794a18e8a4479b"), projectTool);
		when(cacheService.cacheProjectToolConfigMapData()).thenReturn(toolMap);

		Map<String, Object> defectDataListMap =
				testExecutionTimeServiceImpl.fetchKPIDataFromDb(leafNodeList, null, null, kpiRequest);
		assertThat(
				"Total Test Case value :",
				((List<JiraIssue>) (defectDataListMap.get(TESTCASEKEY))).size(),
				equalTo(0));
	}

	@Test
	public void getKpiDataTest() throws ApplicationException {
		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		Map<String, List<String>> maturityRangeMap = new HashMap<>();
		when(configHelperService.calculateMaturity()).thenReturn(maturityRangeMap);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(featureRepository.findIssueAndDescByNumber(any())).thenReturn(issues);
		when(cacheService.getFromApplicationCache(Mockito.anyString()))
				.thenReturn(Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.ZEPHYR.name());

		testFetchKPIDataFromDbData();
		when(testCaseDetailsRepository.findTestDetails(anyMap(), anyMap(), anyString()))
				.thenReturn(totalTestCaseList);
		try {
			kpiElement =
					testExecutionTimeServiceImpl.getKpiData(
							kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);
			assertThat(
					"Test Execution Data Trend Value :",
					((ArrayList) ((List<DataCount>) kpiElement.getTrendValueList()).get(0).getValue()).size(),
					equalTo(5));
			assertThat(
					"Test Execution Time Hower Value :",
					((DataCount)
									((ArrayList)
													((DataCount) ((ArrayList) kpiElement.getTrendValueList()).get(0))
															.getValue())
											.get(0))
							.getHoverValue()
							.size(),
					equalTo(3));
		} catch (ApplicationException ignored) {
			ignored.printStackTrace();
		}
	}

	private void setMockFieldMapping() {

		FieldMapping projectOne = new FieldMapping();
		projectOne.setBasicProjectConfigId(new ObjectId("63284960fdd20276d60e4df5"));
		projectOne.setJiraDefectInjectionIssueTypeKPI14(Arrays.asList("Story", "Tech Story"));
		projectOne.setJiradefecttype(Arrays.asList("Bug"));
		projectOne.setJiraDodKPI171(Arrays.asList("Done"));
		projectOne.setJiraDefectCreatedStatusKPI14("Open");
		projectOne.setUploadDataKPI16(false);
		projectOne.setJiraTestAutomationIssueType(Arrays.asList("Yes", "No"));

		FieldMapping projectTwo = new FieldMapping();
		projectTwo.setBasicProjectConfigId(new ObjectId("6335363749794a18e8a4479b"));
		projectTwo.setJiraDefectInjectionIssueTypeKPI14(Arrays.asList("Story", "Tech Story"));
		projectOne.setJiradefecttype(Arrays.asList("Bug"));
		projectTwo.setJiraDodKPI171(Arrays.asList("Done"));
		projectTwo.setJiraDefectCreatedStatusKPI14("Open");
		projectTwo.setUploadDataKPI16(false);
		projectTwo.setJiraTestAutomationIssueType(Arrays.asList("Yes", "No"));

		FieldMapping projectThree = new FieldMapping();
		projectThree.setBasicProjectConfigId(new ObjectId("63284960fdd20276d60e4df5"));
		projectThree.setJiraDefectInjectionIssueTypeKPI14(Arrays.asList("Story", "Tech Story"));
		projectOne.setJiradefecttype(Arrays.asList("Bug"));
		projectOne.setUploadDataKPI16(true);
		projectThree.setJiraDodKPI171(Arrays.asList("Done"));
		projectThree.setJiraDefectCreatedStatusKPI14("Open");
		projectThree.setJiraTestAutomationIssueType(Arrays.asList("Yes", "No"));

		fieldMappingList.add(projectOne);
		fieldMappingList.add(projectTwo);
		fieldMappingList.add(projectThree);
	}

	@Test
	public void testCalculateHoverMap() {
		List<Map<String, Object>> hoverMapValues = new ArrayList<>();
		Map<String, Object> hoverMap1 = new HashMap<>();
		Map<String, Object> totalData1 = new HashMap<>();
		totalData1.put("count", 10L);
		totalData1.put("avgExecutionTimeSec", 5.0);
		hoverMap1.put("TOTAL", totalData1);
		hoverMapValues.add(hoverMap1);

		Map<String, Object> result = testExecutionTimeServiceImpl.calculateHoverMap(hoverMapValues);
		assertThat("Hover map should contain TOTAL", result.containsKey("TOTAL"), equalTo(true));
	}

	@Test
	public void testCalculateHoverMapEmpty() {
		Map<String, Object> result = testExecutionTimeServiceImpl.calculateHoverMap(new ArrayList<>());
		assertThat("Empty hover map should have 3 categories", result.size(), equalTo(3));
	}
}
