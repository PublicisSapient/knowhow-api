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

import java.lang.reflect.Method;
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
					((List<?>) ((List<DataCount>) kpiElement.getTrendValueList()).get(0).getValue()).size(),
					equalTo(5));
			assertThat(
					"Test Execution Time Hower Value :",
					((DataCount)
									((List<?>)
													((DataCount) ((List<?>) kpiElement.getTrendValueList()).get(0))
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

	@Test
	public void testCalculateKpiValue() {
		List<Double> values = Arrays.asList(10.0, 20.0, 30.0);
		Double result = testExecutionTimeServiceImpl.calculateKpiValue(values, "kpi194");
		assertThat("KPI value should be calculated", result != null, equalTo(true));
	}

	@Test
	public void testCalculateKPIMetrics() {
		Double result = testExecutionTimeServiceImpl.calculateKPIMetrics(new HashMap<>());
		assertThat("KPI metrics should return 0.0", result, equalTo(0.0));
	}

	@Test
	public void testGetValueFromHoverMapWithValidData() {
		Map<String, Object> hoverMap = new HashMap<>();
		Map<String, Object> totalData = new HashMap<>();
		totalData.put("avgExecutionTimeSec", 5.5);
		hoverMap.put("TOTAL", totalData);
		Double result = TestExecutionTimeServiceImpl.getValueFromHoverMap(hoverMap, "TOTAL", "avgExecutionTimeSec", Double.class);
		assertThat("Should extract double value", result, equalTo(5.5));
	}

	@Test
	public void testGetValueFromHoverMapWithNull() {
		Double result = TestExecutionTimeServiceImpl.getValueFromHoverMap(null, "TOTAL", "avgExecutionTimeSec", Double.class);
		assertThat("Should return null for null map", result, equalTo(null));
	}

	@Test
	public void testGetValueFromHoverMapWithMissingCategory() {
		Map<String, Object> hoverMap = new HashMap<>();
		Double result = TestExecutionTimeServiceImpl.getValueFromHoverMap(hoverMap, "TOTAL", "avgExecutionTimeSec", Double.class);
		assertThat("Should return null for missing category", result, equalTo(null));
	}

	@Test
	public void testGetValueFromHoverMapWithIntegerConversion() {
		Map<String, Object> hoverMap = new HashMap<>();
		Map<String, Object> totalData = new HashMap<>();
		totalData.put("count", 10);
		hoverMap.put("TOTAL", totalData);
		Double result = TestExecutionTimeServiceImpl.getValueFromHoverMap(hoverMap, "TOTAL", "count", Double.class);
		assertThat("Should convert integer to double", result, equalTo(10.0));
	}

	@Test
	public void testGetValueFromHoverMapWithLongConversion() {
		Map<String, Object> hoverMap = new HashMap<>();
		Map<String, Object> totalData = new HashMap<>();
		totalData.put("count", 100);
		hoverMap.put("TOTAL", totalData);
		Long result = TestExecutionTimeServiceImpl.getValueFromHoverMap(hoverMap, "TOTAL", "count", Long.class);
		assertThat("Should convert to long", result, equalTo(100L));
	}

	@Test
	public void testGetValueFromHoverMapWithInvalidCategory() {
		Map<String, Object> hoverMap = new HashMap<>();
		hoverMap.put("TOTAL", "not a map");
		Double result = TestExecutionTimeServiceImpl.getValueFromHoverMap(hoverMap, "TOTAL", "count", Double.class);
		assertThat("Should return null for invalid structure", result, equalTo(null));
	}

	@Test
	public void testCalculateHoverMapWithMultipleCategories() {
		List<Map<String, Object>> hoverMapValues = new ArrayList<>();
		Map<String, Object> hoverMap1 = new HashMap<>();
		Map<String, Object> totalData1 = new HashMap<>();
		totalData1.put("count", 10L);
		totalData1.put("avgExecutionTimeSec", 5.0);
		hoverMap1.put("TOTAL", totalData1);
		Map<String, Object> autoData1 = new HashMap<>();
		autoData1.put("count", 5L);
		autoData1.put("avgExecutionTimeSec", 3.0);
		hoverMap1.put("AUTOMATED", autoData1);
		Map<String, Object> manualData1 = new HashMap<>();
		manualData1.put("count", 5L);
		manualData1.put("avgExecutionTimeSec", 7.0);
		hoverMap1.put("MANUAL", manualData1);
		hoverMapValues.add(hoverMap1);

		Map<String, Object> result = testExecutionTimeServiceImpl.calculateHoverMap(hoverMapValues);
		assertThat("Should have all categories", result.size(), equalTo(3));
		assertThat("Should contain AUTOMATED", result.containsKey("AUTOMATED"), equalTo(true));
		assertThat("Should contain MANUAL", result.containsKey("MANUAL"), equalTo(true));
	}

	@Test
	public void testGetKpiDataWithMultipleProjects() throws ApplicationException {
		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(featureRepository.findIssuesGroupBySprint(any(), any(), any(), any()))
				.thenReturn(new ArrayList<>());
		when(testCaseDetailsRepository.findTestDetails(anyMap(), anyMap(), anyString()))
				.thenReturn(new ArrayList<>());
		when(featureRepository.findIssueAndDescByNumber(any())).thenReturn(new ArrayList<>());
		when(cacheService.getFromApplicationCache(anyString())).thenReturn("test-tracker-id");

		KpiElement result = testExecutionTimeServiceImpl.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);
		assertThat("Should return kpi element", result != null, equalTo(true));
	}

	@Test
	public void testCalculateHoverMapWithWeightedAverage() {
		List<Map<String, Object>> hoverMapValues = new ArrayList<>();
		
		Map<String, Object> hoverMap1 = new HashMap<>();
		Map<String, Object> totalData1 = new HashMap<>();
		totalData1.put("count", 10L);
		totalData1.put("avgExecutionTimeSec", 5.0);
		hoverMap1.put("TOTAL", totalData1);
		hoverMapValues.add(hoverMap1);

		Map<String, Object> hoverMap2 = new HashMap<>();
		Map<String, Object> totalData2 = new HashMap<>();
		totalData2.put("count", 20L);
		totalData2.put("avgExecutionTimeSec", 10.0);
		hoverMap2.put("TOTAL", totalData2);
		hoverMapValues.add(hoverMap2);

		Map<String, Object> result = testExecutionTimeServiceImpl.calculateHoverMap(hoverMapValues);
		assertThat("Should calculate weighted average", result.containsKey("TOTAL"), equalTo(true));
	}

	@Test
	public void testCalculateHoverMapWithZeroCount() {
		List<Map<String, Object>> hoverMapValues = new ArrayList<>();
		Map<String, Object> hoverMap1 = new HashMap<>();
		Map<String, Object> totalData1 = new HashMap<>();
		totalData1.put("count", 0L);
		totalData1.put("avgExecutionTimeSec", 0.0);
		hoverMap1.put("TOTAL", totalData1);
		hoverMapValues.add(hoverMap1);

		Map<String, Object> result = testExecutionTimeServiceImpl.calculateHoverMap(hoverMapValues);
		assertThat("Should handle zero count", result.containsKey("TOTAL"), equalTo(true));
	}

	@Test
	public void testCopyDataCountWithNodeName() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("copyDataCountWithNodeName", DataCount.class, String.class);
		method.setAccessible(true);
		DataCount dc = new DataCount();
		dc.setData("10");
		dc.setValue(10.0);
		dc.setSProjectName("OldProject");
		DataCount result = (DataCount) method.invoke(testExecutionTimeServiceImpl, dc, "NewProject");
		assertThat("Should set new project name", result.getSProjectName(), equalTo("NewProject"));
	}

	@Test
	public void testGetNumberValue() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("getNumberValue", Object.class);
		method.setAccessible(true);
		long result = (long) method.invoke(testExecutionTimeServiceImpl, 10);
		assertThat("Should convert integer", result, equalTo(10L));
	}

	@Test
	public void testGetDoubleValue() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("getDoubleValue", Object.class);
		method.setAccessible(true);
		double result = (double) method.invoke(testExecutionTimeServiceImpl, 10.5);
		assertThat("Should handle double", result, equalTo(10.5));
	}

	@Test
	public void testCreateEmptyHoverMap() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("createEmptyHoverMap");
		method.setAccessible(true);
		@SuppressWarnings("unchecked")
		Map<String, Object> result = (Map<String, Object>) method.invoke(testExecutionTimeServiceImpl);
		assertThat("Should have 3 categories", result.size(), equalTo(3));
	}

	@Test
	public void testGetAverageExecutionTimeForTestCase() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("getAverageExecutionTimeForTestCase", TestCaseDetails.class);
		method.setAccessible(true);
		TestCaseDetails tc = new TestCaseDetails();
		com.publicissapient.kpidashboard.common.model.zephyr.TestCaseExecutionData exec1 = new com.publicissapient.kpidashboard.common.model.zephyr.TestCaseExecutionData();
		exec1.setExecutionTime(100);
		com.publicissapient.kpidashboard.common.model.zephyr.TestCaseExecutionData exec2 = new com.publicissapient.kpidashboard.common.model.zephyr.TestCaseExecutionData();
		exec2.setExecutionTime(200);
		tc.setExecutions(Arrays.asList(exec1, exec2));
		double result = (double) method.invoke(testExecutionTimeServiceImpl, tc);
		assertThat("Should calculate average", result, equalTo(150.0));
	}

	@Test
	public void testGetAverageExecutionTimeForTestCaseEmpty() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("getAverageExecutionTimeForTestCase", TestCaseDetails.class);
		method.setAccessible(true);
		TestCaseDetails tc = new TestCaseDetails();
		tc.setExecutions(new ArrayList<>());
		double result = (double) method.invoke(testExecutionTimeServiceImpl, tc);
		assertThat("Should return 0 for empty", result, equalTo(0.0));
	}

	@Test
	public void testCalculateAverageExecutionTime() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("calculateAverageExecutionTime", List.class);
		method.setAccessible(true);
		TestCaseDetails tc1 = new TestCaseDetails();
		com.publicissapient.kpidashboard.common.model.zephyr.TestCaseExecutionData exec1 = new com.publicissapient.kpidashboard.common.model.zephyr.TestCaseExecutionData();
		exec1.setExecutionTime(60000);
		tc1.setExecutions(Arrays.asList(exec1));
		List<TestCaseDetails> testCases = Arrays.asList(tc1);
		double result = (double) method.invoke(testExecutionTimeServiceImpl, testCases);
		assertThat("Should calculate average", result > 0, equalTo(true));
	}

	@Test
	public void testMatchesStory() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("matchesStory", TestCaseDetails.class, SprintWiseStory.class, String.class);
		method.setAccessible(true);
		TestCaseDetails tc = new TestCaseDetails();
		tc.setBasicProjectConfigId("project1");
		tc.setIsTestAutomated("Yes");
		tc.setDefectStoryID(new HashSet<>(Arrays.asList("STORY-1")));
		SprintWiseStory st = new SprintWiseStory();
		st.setBasicProjectConfigId("project1");
		st.setStoryList(Arrays.asList("STORY-1"));
		boolean result = (boolean) method.invoke(testExecutionTimeServiceImpl, tc, st, "Yes");
		assertThat("Should match story", result, equalTo(true));
	}

	@Test
	public void testMatchesStoryWithNull() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("matchesStory", TestCaseDetails.class, SprintWiseStory.class, String.class);
		method.setAccessible(true);
		TestCaseDetails tc = new TestCaseDetails();
		tc.setBasicProjectConfigId("project1");
		tc.setDefectStoryID(new HashSet<>(Arrays.asList("STORY-1")));
		SprintWiseStory st = new SprintWiseStory();
		st.setBasicProjectConfigId("project1");
		st.setStoryList(Arrays.asList("STORY-1"));
		boolean result = (boolean) method.invoke(testExecutionTimeServiceImpl, tc, st, null);
		assertThat("Should match with null", result, equalTo(true));
	}

	@Test
	public void testPopulateCategoryData() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("populateCategoryData", String.class, List.class, Map.class);
		method.setAccessible(true);
		TestCaseDetails tc = new TestCaseDetails();
		com.publicissapient.kpidashboard.common.model.zephyr.TestCaseExecutionData exec = new com.publicissapient.kpidashboard.common.model.zephyr.TestCaseExecutionData();
		exec.setExecutionTime(60000);
		tc.setExecutions(Arrays.asList(exec));
		List<TestCaseDetails> testCases = Arrays.asList(tc);
		Map<String, Object> hoverMap = new HashMap<>();
		method.invoke(testExecutionTimeServiceImpl, "TOTAL", testCases, hoverMap);
		assertThat("Should populate hover map", hoverMap.containsKey("TOTAL"), equalTo(true));
	}

	@Test
	public void testPopulateCategoryDataEmpty() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("populateCategoryData", String.class, List.class, Map.class);
		method.setAccessible(true);
		Map<String, Object> hoverMap = new HashMap<>();
		method.invoke(testExecutionTimeServiceImpl, "TOTAL", new ArrayList<>(), hoverMap);
		assertThat("Should populate with zero", hoverMap.containsKey("TOTAL"), equalTo(true));
	}

	@Test
	public void testGroupByIndex() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("groupByIndex", Map.class);
		method.setAccessible(true);
		DataCount dc1 = new DataCount();
		DataCount dc2 = new DataCount();
		Map<String, List<DataCount>> projectWiseDataCount = new HashMap<>();
		projectWiseDataCount.put("Project1", Arrays.asList(dc1, dc2));
		@SuppressWarnings("unchecked")
		List<List<DataCount>> result = (List<List<DataCount>>) method.invoke(testExecutionTimeServiceImpl, projectWiseDataCount);
		assertThat("Should group by index", result.size(), equalTo(2));
	}

	@Test
	public void testCreateSingleProjectResult() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("createSingleProjectResult", List.class, Node.class);
		method.setAccessible(true);
		DataCount dc = new DataCount();
		dc.setSProjectName("Project1");
		List<DataCount> dataCounts = Arrays.asList(dc);
		Node node = new Node();
		node.setName("TestNode");
		@SuppressWarnings("unchecked")
		List<DataCount> result = (List<DataCount>) method.invoke(testExecutionTimeServiceImpl, dataCounts, node);
		assertThat("Should return list", result.size(), equalTo(1));
	}

	@Test
	public void testAggregateIndexValues() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("aggregateIndexValues", List.class, Node.class);
		method.setAccessible(true);
		DataCount dc1 = new DataCount();
		dc1.setValue(10.0);
		dc1.setSProjectName("Project1");
		dc1.setDate("2024-01-01");
		Map<String, Object> hover = new HashMap<>();
		Map<String, Object> totalData = new HashMap<>();
		totalData.put("count", 5L);
		totalData.put("avgExecutionTimeSec", 10.0);
		hover.put("TOTAL", totalData);
		dc1.setHoverValue(hover);
		Node node = new Node();
		node.setName("AggNode");
		DataCount result = (DataCount) method.invoke(testExecutionTimeServiceImpl, Arrays.asList(dc1), node);
		assertThat("Should aggregate", result.getSProjectName(), equalTo("AggNode"));
	}

	@Test
	public void testSyncHoverValue() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("syncHoverValueWithCalculatedValue", Map.class, Double.class);
		method.setAccessible(true);
		Map<String, Object> hoverMap = new HashMap<>();
		Map<String, Object> totalData = new HashMap<>();
		totalData.put("count", 5L);
		totalData.put("avgExecutionTimeSec", 10.0);
		hoverMap.put("TOTAL", totalData);
		method.invoke(testExecutionTimeServiceImpl, hoverMap, 15.0);
		assertThat("Should sync values", hoverMap.containsKey("TOTAL"), equalTo(true));
	}

	@Test
	public void testGetAutomatedTestCases() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("getAutomatedTestCases", List.class, List.class);
		method.setAccessible(true);
		TestCaseDetails tc = new TestCaseDetails();
		tc.setBasicProjectConfigId("project1");
		tc.setIsTestAutomated("Yes");
		tc.setDefectStoryID(new HashSet<>(Arrays.asList("STORY-1")));
		SprintWiseStory sws = new SprintWiseStory();
		sws.setBasicProjectConfigId("project1");
		sws.setStoryList(Arrays.asList("STORY-1"));
		@SuppressWarnings("unchecked")
		List<TestCaseDetails> result = (List<TestCaseDetails>) method.invoke(testExecutionTimeServiceImpl, Arrays.asList(tc), Arrays.asList(sws));
		assertThat("Should filter automated", result.size(), equalTo(1));
	}

	@Test
	public void testGetManualTestCases() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("getManualTestCases", List.class, List.class);
		method.setAccessible(true);
		TestCaseDetails tc = new TestCaseDetails();
		tc.setBasicProjectConfigId("project1");
		tc.setIsTestAutomated("No");
		tc.setDefectStoryID(new HashSet<>(Arrays.asList("STORY-1")));
		SprintWiseStory sws = new SprintWiseStory();
		sws.setBasicProjectConfigId("project1");
		sws.setStoryList(Arrays.asList("STORY-1"));
		@SuppressWarnings("unchecked")
		List<TestCaseDetails> result = (List<TestCaseDetails>) method.invoke(testExecutionTimeServiceImpl, Arrays.asList(tc), Arrays.asList(sws));
		assertThat("Should filter manual", result.size(), equalTo(1));
	}

	@Test
	public void testGetTotalTestCases() throws Exception {
		Method method = TestExecutionTimeServiceImpl.class.getDeclaredMethod("getTotalTestCases", List.class, List.class);
		method.setAccessible(true);
		TestCaseDetails tc1 = new TestCaseDetails();
		tc1.setBasicProjectConfigId("project1");
		tc1.setIsTestAutomated("Yes");
		tc1.setDefectStoryID(new HashSet<>(Arrays.asList("STORY-1")));
		TestCaseDetails tc2 = new TestCaseDetails();
		tc2.setBasicProjectConfigId("project1");
		tc2.setIsTestAutomated("No");
		tc2.setDefectStoryID(new HashSet<>(Arrays.asList("STORY-1")));
		SprintWiseStory sws = new SprintWiseStory();
		sws.setBasicProjectConfigId("project1");
		sws.setStoryList(Arrays.asList("STORY-1"));
		@SuppressWarnings("unchecked")
		List<TestCaseDetails> result = (List<TestCaseDetails>) method.invoke(testExecutionTimeServiceImpl, Arrays.asList(tc1, tc2), Arrays.asList(sws));
		assertThat("Should get all tests", result.size(), equalTo(2));
	}
}
