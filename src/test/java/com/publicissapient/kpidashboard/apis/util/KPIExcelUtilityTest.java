/*
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.util;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.testng.Assert;

import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.data.JiraIssueDataFactory;
import com.publicissapient.kpidashboard.apis.data.TestCaseDetailsDataFactory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.model.*;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.*;
import com.publicissapient.kpidashboard.common.model.jira.*;
import com.publicissapient.kpidashboard.common.model.zephyr.TestCaseDetails;
import com.publicissapient.kpidashboard.common.model.zephyr.TestCaseExecutionData;

@RunWith(MockitoJUnitRunner.class)
public class KPIExcelUtilityTest {

	@InjectMocks KPIExcelUtility excelUtility;
	private List<KPIExcelData> kpiExcelData;
	@Mock CustomApiConfig customApiConfig;
	private List<JiraIssue> jiraIssues;
	private List<TestCaseDetails> testCaseDetailsList;
	List<JiraIssue> storyList = new ArrayList<>();
	private DeploymentFrequencyInfo deploymentFrequencyInfo;

	@Before
	public void setup() {
		deploymentFrequencyInfo = Mockito.mock(DeploymentFrequencyInfo.class);

		// Setup CustomApiConfig mocks for priority methods
		when(customApiConfig.getpriorityP1()).thenReturn("P1");
		when(customApiConfig.getpriorityP2()).thenReturn("P2");
		when(customApiConfig.getpriorityP3()).thenReturn("P3");
		when(customApiConfig.getpriorityP4()).thenReturn("P4");
		JiraIssueDataFactory jiraIssueDataFactory = JiraIssueDataFactory.newInstance();
		jiraIssues = jiraIssueDataFactory.getJiraIssues();
		testCaseDetailsList = TestCaseDetailsDataFactory.newInstance().getTestCaseDetailsList();
		storyList =
				jiraIssues.stream()
						.filter(issue -> issue.getTypeName().equalsIgnoreCase("Story"))
						.collect(Collectors.toList());
		kpiExcelData = new ArrayList<>();
	}

	@Test
	public void populateFTPRExcelData_ValidData_PopulatesKPIExcelData() {

		List<String> storyIds = Arrays.asList("STORY1", "STORY2");
		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		Map<String, JiraIssue> issueData =
				jiraIssues.stream().collect(Collectors.toMap(JiraIssue::getNumber, x -> x));
		List<JiraIssue> defects =
				jiraIssues.stream()
						.filter(i -> i.getTypeName().equalsIgnoreCase("Bug"))
						.collect(Collectors.toList());

		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.STORY_POINT);
		when(customApiConfig.getpriorityP1()).thenReturn(Constant.P1);
		when(customApiConfig.getpriorityP2()).thenReturn(Constant.P2);
		when(customApiConfig.getpriorityP3()).thenReturn(Constant.P3);
		when(customApiConfig.getpriorityP4()).thenReturn("p4-minor");
		// Act
		Node node = new Node();
		node.setSprintFilter(
				new SprintFilter(
						"sprint-id",
						"TEST| KnowHOW|PI_10|Opensource_Scrum Project",
						LocalDateTime.now().toString(),
						LocalDateTime.now().toString()));

		excelUtility.populateFTPRExcelData(
				storyIds,
				jiraIssues,
				kpiExcelData,
				issueData,
				defects,
				customApiConfig,
				fieldMapping,
				node);

		// Assert
		assertEquals(2, kpiExcelData.size());
		assertEquals(
				"TEST| KnowHOW|PI_10|Opensource_Scrum Project", kpiExcelData.get(0).getSprintName());
		assertEquals(
				"TEST| KnowHOW|PI_10|Opensource_Scrum Project", kpiExcelData.get(1).getSprintName());
	}

	@Test
	public void populateLeadTimeForChangeExcelData_ValidData_PopulatesKPIExcelData() {
		// Arrange
		String projectName = "Project1";
		Map<String, List<LeadTimeChangeData>> leadTimeMapTimeWise = new HashMap<>();
		List<LeadTimeChangeData> leadTimeList = Arrays.asList(createLeadTime(), createLeadTime());

		leadTimeMapTimeWise.put("Week1", leadTimeList);
		List<KPIExcelData> kpiExcelData = new ArrayList<>();
		String leadTimeConfigRepoTool = CommonConstant.REPO;

		// Act
		excelUtility.populateLeadTimeForChangeExcelData(
				projectName, leadTimeMapTimeWise, kpiExcelData, leadTimeConfigRepoTool);

		// Assert
		assertEquals(2, kpiExcelData.size());
	}

	public static void populatePickupTimeExcelData(
			String projectName,
			List<Map<String, Double>> repoWiseMRList,
			List<String> repoList,
			List<String> branchList,
			List<KPIExcelData> kpiExcelData) {

		if (CollectionUtils.isNotEmpty(repoWiseMRList)) {
			for (int i = 0; i < repoWiseMRList.size(); i++) {
				Map<String, Double> repoWiseMap = repoWiseMRList.get(i);
				for (Map.Entry<String, Double> m : repoWiseMap.entrySet()) {
					KPIExcelData excelData = new KPIExcelData();
					excelData.setProject(projectName);
					excelData.setRepo(repoList.get(i));
					excelData.setBranch(branchList.get(i));
					excelData.setDaysWeeks(m.getKey());
					excelData.setPickupTime(m.getValue().toString());
					kpiExcelData.add(excelData);
				}
			}
		}
	}

	@Test
	public void populatePickupTimeExcelData_ValidData_PopulatesKPIExcelData() {
		// Arrange
		RepoToolValidationData repoToolValidationData = new RepoToolValidationData();
		repoToolValidationData.setProjectName("Project1");
		repoToolValidationData.setPickupTime(10.0d);
		repoToolValidationData.setDate("Week");
		repoToolValidationData.setRepoUrl("repoUrl");
		repoToolValidationData.setBranchName("master");
		repoToolValidationData.setDeveloperName("developer");
		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		// Act
		excelUtility.populatePickupTimeExcelData(Arrays.asList(repoToolValidationData), kpiExcelData);

		// Assert
		assertEquals(1, kpiExcelData.size());
	}

	@Test
	public void populatePRSizeExcelData_ValidData_PopulatesKPIExcelData() {
		// Arrange
		RepoToolValidationData repoToolValidationData = new RepoToolValidationData();
		repoToolValidationData.setProjectName("Project1");
		repoToolValidationData.setPrSize(10L);
		repoToolValidationData.setDate("Week");
		repoToolValidationData.setRepoUrl("repoUrl");
		repoToolValidationData.setBranchName("master");
		repoToolValidationData.setDeveloperName("developer");
		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		// Act
		excelUtility.populatePRSizeExcelData(Arrays.asList(repoToolValidationData), kpiExcelData);

		// Assert
		assertEquals(1, kpiExcelData.size());
	}

	@Test
	public void populateCodeCommit_ValidData_PopulatesKPIExcelData() {
		// Arrange
		RepoToolValidationData repoToolValidationData = new RepoToolValidationData();
		repoToolValidationData.setProjectName("Project1");
		repoToolValidationData.setCommitCount(10L);
		repoToolValidationData.setMrCount(2L);
		repoToolValidationData.setDate("Week");
		repoToolValidationData.setRepoUrl("repoUrl");
		repoToolValidationData.setBranchName("master");
		repoToolValidationData.setDeveloperName("developer");
		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		// Act
		excelUtility.populateCodeCommit(Arrays.asList(repoToolValidationData), kpiExcelData);

		// Assert
		assertEquals(1, kpiExcelData.size());
	}

	@Test
	public void populateCodeBuildTimeExcelData_ValidData_PopulatesKPIExcelData() {
		// Arrange
		CodeBuildTimeInfo codeBuildTimeInfo = new CodeBuildTimeInfo();
		codeBuildTimeInfo.setBuildJobList(Arrays.asList("Job1", "Job2"));
		codeBuildTimeInfo.setBuildStartTimeList(
				Arrays.asList("2022-01-01T10:00:00", "2022-01-02T11:00:00"));
		codeBuildTimeInfo.setBuildEndTimeList(
				Arrays.asList("2022-01-01T11:00:00", "2022-01-02T12:00:00"));
		codeBuildTimeInfo.setDurationList(Arrays.asList("1 hour", "1 hour"));
		codeBuildTimeInfo.setBuildUrlList(Arrays.asList("url1", "url2"));
		codeBuildTimeInfo.setBuildStatusList(Arrays.asList("SUCCESS", "FAILURE"));

		String projectName = "Project1";
		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		// Act
		excelUtility.populateCodeBuildTimeExcelData(codeBuildTimeInfo, projectName, kpiExcelData);

		// Assert
		assertEquals(2, kpiExcelData.size());
	}

	@Test
	public void populateRefinementRejectionExcelData_ValidData_PopulatesExcelDataList() {
		// Arrange
		List<KPIExcelData> excelDataList = new ArrayList<>();

		Map<String, Map<String, List<JiraIssue>>> weekAndTypeMap = new HashMap<>();
		Map<String, List<JiraIssue>> map = new HashMap<>();
		map.put("Type1", Arrays.asList(jiraIssues.get(0)));

		weekAndTypeMap.put("Week1", map);
		map.clear();
		map.put("Type2", Arrays.asList(jiraIssues.get(1)));
		weekAndTypeMap.put("Week2", map);

		Map<String, LocalDateTime> jiraDateMap =
				jiraIssues.stream()
						.collect(Collectors.toMap(JiraIssue::getNumber, x -> LocalDateTime.now()));

		// Act
		excelUtility.populateRefinementRejectionExcelData(
				excelDataList, jiraIssues, weekAndTypeMap, jiraDateMap);

		// Assert
		assertEquals(48, excelDataList.size());
	}

	@Test
	public void testPopulateDefectRelatedExcelData_DRE() {
		// Mock input parameters
		Set<String> set = new HashSet<>();
		set.add("A");
		set.add("B");

		jiraIssues.forEach(
				jira -> {
					AdditionalFilterConfig additionalFilterConfig = new AdditionalFilterConfig();
					additionalFilterConfig.setFilterId("sqd");
					additionalFilterConfig.setValues(set);
					List<AdditionalFilterConfig> additionalFilterConfigList = new ArrayList<>();
					additionalFilterConfigList.add(additionalFilterConfig);

					List<AdditionalFilterValue> additionalFilterValueList = new ArrayList<>();
					AdditionalFilterValue additionalFilterValue = new AdditionalFilterValue();
					additionalFilterValue.setValue("abc");
					additionalFilterValue.setValueId("abc12");
					additionalFilterValueList.add(additionalFilterValue);

					List<AdditionalFilter> additionalFilterConfigsList = new ArrayList<>();
					AdditionalFilter additionalFilter = new AdditionalFilter();
					additionalFilter.setFilterId("sqd");
					additionalFilter.setFilterValues(additionalFilterValueList);
					additionalFilterConfigsList.add(additionalFilter);

					jira.setAdditionalFilters(additionalFilterConfigsList);
				});
		Map<String, JiraIssue> bug =
				jiraIssues.stream()
						.filter(issue -> issue.getTypeName().equalsIgnoreCase("Bug"))
						.collect(Collectors.toMap(JiraIssue::getNumber, x -> x));
		List<KPIExcelData> kpiExcelData = new ArrayList<>();
		String kpiId = KPICode.DEFECT_REMOVAL_EFFICIENCY.getKpiId();
		when(customApiConfig.getpriorityP1()).thenReturn(Constant.P1);
		when(customApiConfig.getpriorityP2()).thenReturn(Constant.P2);
		when(customApiConfig.getpriorityP3()).thenReturn(Constant.P3);
		when(customApiConfig.getpriorityP4()).thenReturn("p4-minor");
		// Call the method to populate data
		KPIExcelUtility.populateDefectRelatedExcelData(
				"abc", bug, jiraIssues, kpiExcelData, kpiId, customApiConfig, storyList);

		// Assert the result based on your logic
		assertEquals(20, kpiExcelData.size());
		KPIExcelData excelData = kpiExcelData.get(0);

		Map<String, String> defectIdDetails = excelData.getDefectId();
		assertEquals(1, defectIdDetails.size());
		// Depending on your kpiId logic, assert the corresponding fields
		assertEquals(Constant.EXCEL_YES, excelData.getRemovedDefect());
	}

	@Test
	public void testPopulateDefectRelatedExcelData_DSR() {
		// Mock input parameters
		Map<String, JiraIssue> bug =
				jiraIssues.stream()
						.filter(issue -> issue.getTypeName().equalsIgnoreCase("Bug"))
						.collect(Collectors.toMap(JiraIssue::getNumber, x -> x));
		List<KPIExcelData> kpiExcelData = new ArrayList<>();
		String kpiId = KPICode.DEFECT_SEEPAGE_RATE.getKpiId();
		when(customApiConfig.getpriorityP1()).thenReturn(Constant.P1);
		when(customApiConfig.getpriorityP2()).thenReturn(Constant.P2);
		when(customApiConfig.getpriorityP3()).thenReturn(Constant.P3);
		when(customApiConfig.getpriorityP4()).thenReturn("p4-minor");
		// Call the method to populate data
		KPIExcelUtility.populateDefectRelatedExcelData(
				"abc", bug, jiraIssues, kpiExcelData, kpiId, customApiConfig, storyList);

		// Assert the result based on your logic
		assertEquals(20, kpiExcelData.size());
		KPIExcelData excelData = kpiExcelData.get(0);

		Map<String, String> defectIdDetails = excelData.getDefectId();
		assertEquals(1, defectIdDetails.size());
		// Depending on your kpiId logic, assert the corresponding fields
		// assertEquals(Constant.EXCEL_YES, excelData.getEscapedDefect());
	}

	@Test
	public void testPopulateDefectRelatedExcelData_DRR() {
		// Mock input parameters
		Map<String, JiraIssue> bug =
				jiraIssues.stream()
						.filter(issue -> issue.getTypeName().equalsIgnoreCase("Bug"))
						.collect(Collectors.toMap(JiraIssue::getNumber, x -> x));
		List<KPIExcelData> kpiExcelData = new ArrayList<>();
		String kpiId = KPICode.DEFECT_REJECTION_RATE.getKpiId();
		when(customApiConfig.getpriorityP1()).thenReturn(Constant.P1);
		when(customApiConfig.getpriorityP2()).thenReturn(Constant.P2);
		when(customApiConfig.getpriorityP3()).thenReturn(Constant.P3);
		when(customApiConfig.getpriorityP4()).thenReturn("p4-minor");
		// Call the method to populate data
		KPIExcelUtility.populateDefectRelatedExcelData(
				"abc", bug, jiraIssues, kpiExcelData, kpiId, customApiConfig, storyList);

		// Assert the result based on your logic
		assertEquals(20, kpiExcelData.size());
		KPIExcelData excelData = kpiExcelData.get(0);

		Map<String, String> defectIdDetails = excelData.getDefectId();
		assertEquals(1, defectIdDetails.size());
		// Depending on your kpiId logic, assert the corresponding fields
		assertEquals(Constant.EXCEL_YES, excelData.getRejectedDefect());
	}

	@Test
	public void testPopulateDefectRelatedExcelData_Negative() {
		// Mock input parameters
		Map<String, JiraIssue> bug =
				jiraIssues.stream()
						.filter(issue -> issue.getTypeName().equalsIgnoreCase("Bug"))
						.collect(Collectors.toMap(JiraIssue::getNumber, x -> x));
		List<KPIExcelData> kpiExcelData = new ArrayList<>();
		String kpiId = KPICode.CYCLE_TIME.getKpiId();
		when(customApiConfig.getpriorityP1()).thenReturn(Constant.P1);
		when(customApiConfig.getpriorityP2()).thenReturn(Constant.P2);
		when(customApiConfig.getpriorityP3()).thenReturn(Constant.P3);
		when(customApiConfig.getpriorityP4()).thenReturn("p4-minor");
		// Call the method to populate data
		KPIExcelUtility.populateDefectRelatedExcelData(
				"abc", bug, jiraIssues, kpiExcelData, kpiId, customApiConfig, storyList);

		// Assert the result based on your logic
		assertEquals(20, kpiExcelData.size());
		KPIExcelData excelData = kpiExcelData.get(0);

		Map<String, String> defectIdDetails = excelData.getDefectId();
		assertEquals(1, defectIdDetails.size());
		// Depending on your kpiId logic, assert the corresponding fields
		assertNull(excelData.getRemovedDefect());
	}

	@Test
	public void testPopulateInSprintAutomationExcelData() {
		// Mock input parameters
		String sprint = "YourSprint";
		List<TestCaseDetails> allTestList = new ArrayList<>();
		List<TestCaseDetails> automatedList = new ArrayList<>();
		Set<JiraIssue> linkedStories = new HashSet<>();
		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		// Mock a TestCaseDetails
		TestCaseDetails testCase1 = mock(TestCaseDetails.class);
		when(testCase1.getNumber()).thenReturn("TC123");
		when(testCase1.getDefectStoryID()).thenReturn(new HashSet<>(Arrays.asList("Story123")));

		// Mock another TestCaseDetails
		TestCaseDetails testCase2 = mock(TestCaseDetails.class);
		when(testCase2.getNumber()).thenReturn("TC456");
		when(testCase2.getDefectStoryID()).thenReturn(new HashSet<>(Arrays.asList("Story456")));

		// Add the mock TestCaseDetails to the allTestList and automatedList
		allTestList.add(testCase1);
		allTestList.add(testCase2);
		automatedList.add(testCase1);

		// Mock a JiraIssue (linked story)
		JiraIssue linkedStory = mock(JiraIssue.class);
		when(linkedStory.getNumber()).thenReturn("Story123");
		when(linkedStory.getUrl()).thenReturn("http://example.com/story123");

		// Add the mock JiraIssue to the linkedStories
		linkedStories.add(linkedStory);

		// Call the method to populate data
		KPIExcelUtility.populateInSprintAutomationExcelData(
				sprint, allTestList, automatedList, linkedStories, kpiExcelData);

		// Assert the result based on your logic
		assertEquals(2, kpiExcelData.size());

		KPIExcelData excelData = kpiExcelData.get(0);

		assertEquals(sprint, excelData.getSprintName());
		assertEquals("TC123", excelData.getTestCaseId());
		assertEquals(Constant.EXCEL_YES, excelData.getAutomated());

		Map<String, String> linkedStoriesMap = excelData.getLinkedStory();
		assertEquals(1, linkedStoriesMap.size());
		assertEquals("http://example.com/story123", linkedStoriesMap.get("Story123"));
	}

	@Test
	public void testPopulateOpenVsClosedExcelData() {
		// Create a mock of KanbanJiraIssue
		KanbanJiraIssue openIssue = mock(KanbanJiraIssue.class);
		when(openIssue.getNumber()).thenReturn("OPEN-1");
		// when(openIssue.getTypeName()).thenReturn("Bug");
		// when(openIssue.getPriority()).thenReturn("High");

		KanbanIssueCustomHistory closedIssue = mock(KanbanIssueCustomHistory.class);
		when(closedIssue.getStoryID()).thenReturn("CLOSED-1");
		// when(closedIssue.getStoryType()).thenReturn("Story");
		// when(closedIssue.getPriority()).thenReturn("Low");

		// Mock data
		List<KanbanJiraIssue> openIssues = Arrays.asList(openIssue);
		List<KanbanIssueCustomHistory> closedIssues = Arrays.asList(closedIssue);

		// Create an empty list to hold the KPIExcelData objects
		List<KPIExcelData> excelDataList = new ArrayList<>();

		// Call the method to be tested
		KPIExcelUtility.populateOpenVsClosedExcelData(
				"2022-01-01", "ProjectX", openIssues, closedIssues, excelDataList, "KPI_ID");

		// Verify the results
		assertEquals(2, excelDataList.size());

		// Verify the first KPIExcelData object for open issue
		KPIExcelData openKPIExcelData = excelDataList.get(0);
		assertEquals("ProjectX", openKPIExcelData.getProject());
		assertEquals("2022-01-01", openKPIExcelData.getDayWeekMonth());
		assertEquals("OPEN-1", openKPIExcelData.getTicketIssue().keySet().iterator().next());

		// Verify the second KPIExcelData object for closed issue
		KPIExcelData closedKPIExcelData = excelDataList.get(1);
		assertEquals("ProjectX", closedKPIExcelData.getProject());
		assertEquals("2022-01-01", closedKPIExcelData.getDayWeekMonth());
		assertEquals("CLOSED-1", closedKPIExcelData.getTicketIssue().keySet().iterator().next());
	}

	@Test
	public void testPrepareExcelForKanbanCumulativeDataMap() {
		// Mock data
		String projectName = "ProjectX";
		String date = "2022-01-01";
		String kpiId = "NET_OPEN_TICKET_COUNT_BY_STATUS";

		Map<String, Map<String, Set<String>>> jiraHistoryFieldAndDateWiseIssueMap = new HashMap<>();
		Map<String, Set<String>> internalMap = new HashMap<>();
		internalMap.put(LocalDate.now().toString(), new HashSet<>(Arrays.asList("Issue1", "Issue2")));
		jiraHistoryFieldAndDateWiseIssueMap.put("FieldA", internalMap);

		Set<String> fieldValues = new HashSet<>(Arrays.asList("FieldA"));
		Set<KanbanIssueCustomHistory> kanbanJiraIssues =
				new HashSet<>(
						Arrays.asList(
								createKanbanIssue("Issue1", "FieldA", "2022-01-01"),
								createKanbanIssue("Issue2", "FieldB", "2022-01-01"),
								createKanbanIssue("Issue3", "FieldA", "2022-01-02")));
		List<KPIExcelData> excelDataList = new ArrayList<>();

		// Create a mock of YourClass and use it to call the method
		// when(KPIExcelUtility.checkEmptyURL(any(KanbanJiraIssue.class))).thenReturn("MockedURL");

		// Call the method to be tested
		KPIExcelUtility.prepareExcelForKanbanCumulativeDataMap(
				projectName,
				jiraHistoryFieldAndDateWiseIssueMap,
				fieldValues,
				kanbanJiraIssues,
				excelDataList,
				date,
				kpiId);

		// Verify the results
		assertEquals(2, excelDataList.size());
	}

	private KanbanIssueCustomHistory createKanbanIssue(
			String storyId, String field, String createdDate) {
		KanbanIssueCustomHistory issue = new KanbanIssueCustomHistory();
		issue.setStoryID(storyId);
		issue.setCreatedDate(createdDate);
		return issue;
	}

	private LeadTimeChangeData createLeadTime() {
		LeadTimeChangeData leadTimeChangeData = new LeadTimeChangeData();
		leadTimeChangeData.setLeadTime(2.0);
		leadTimeChangeData.setLeadTimeInDays("2");
		leadTimeChangeData.setDate(LocalDate.now().toString());
		leadTimeChangeData.setClosedDate(LocalDate.now().minusDays(1).toString());
		leadTimeChangeData.setReleaseDate(LocalDate.now().toString());
		leadTimeChangeData.setMergeID("123");
		leadTimeChangeData.setUrl("www.fhewjdh.com");
		return leadTimeChangeData;
	}

	@Test
	public void populateReleaseDefectRelatedExcelData_ValidData_PopulatesExcelDataList() {
		// Arrange
		List<KPIExcelData> excelDataList = new ArrayList<>();
		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.STORY_POINT);
		jiraIssues.get(0).setAggregateTimeOriginalEstimateMinutes(10);

		// Act
		KPIExcelUtility.populateReleaseDefectRelatedExcelData(jiraIssues, excelDataList, fieldMapping);

		// Assert
		assertEquals(48, excelDataList.size());
	}

	@Test
	public void
			populateReleaseDefectRelatedExcelData_WhenEstimationCriteriaIsNotStoryPoint_PopulatesExcelDataList() {
		// Arrange
		List<KPIExcelData> excelDataList = new ArrayList<>();
		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.JIRA_IN_PROGRESS_STATUS);
		jiraIssues.get(0).setAggregateTimeOriginalEstimateMinutes(10);

		// Act
		KPIExcelUtility.populateReleaseDefectRelatedExcelData(jiraIssues, excelDataList, fieldMapping);

		// Assert
		assertEquals(48, excelDataList.size());
	}

	@Test
	public void populateBacklogCountExcelData_ValidData_PopulatesExcelDataList() {
		// Arrange
		List<KPIExcelData> excelDataList = new ArrayList<>();

		jiraIssues.get(0).setCreatedDate("2022-01-01T23:08:15.6740000");
		jiraIssues.get(0).setUpdateDate("2022-04-01T23:08:15.6740000");

		// Act
		KPIExcelUtility.populateBacklogCountExcelData(jiraIssues, excelDataList);

		// Assert
		assertEquals(48, excelDataList.size());
	}

	@Test
	public void populateIterationKPI_ValidData() {

		IterationKpiModalValue jiraIssueModalObject = new IterationKpiModalValue();
		IterationKpiModalValue modelValue = new IterationKpiModalValue();
		IterationKpiModalValue iterationKpiModalValue = new IterationKpiModalValue();
		List<IterationKpiModalValue> overAllModalValues = new ArrayList<>();
		overAllModalValues.add(iterationKpiModalValue);
		List<IterationKpiModalValue> modalValues = new ArrayList<>();
		modalValues.add(modelValue);

		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.STORY_POINT);
		Map<String, IterationKpiModalValue> modalObjectMap = mock(Map.class);
		when(modalObjectMap.get(jiraIssues.get(0).getNumber())).thenReturn(jiraIssueModalObject);

		// Act
		KPIExcelUtility.populateIterationKPI(
				overAllModalValues, modalValues, jiraIssues.get(0), fieldMapping, modalObjectMap);
		assertNotNull(modalValues);
		assertEquals(2, modalValues.size());
		assertNotNull(overAllModalValues);
		assertEquals(2, overAllModalValues.size());
	}

	@Test
	public void populateIterationKPI_ValidData1() {

		IterationKpiModalValue jiraIssueModalObject = new IterationKpiModalValue();
		IterationKpiModalValue modelValue = new IterationKpiModalValue();
		IterationKpiModalValue iterationKpiModalValue = new IterationKpiModalValue();
		List<IterationKpiModalValue> overAllModalValues = new ArrayList<>();
		overAllModalValues.add(iterationKpiModalValue);
		List<IterationKpiModalValue> modalValues = new ArrayList<>();
		modalValues.add(modelValue);

		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.STORY_POINT);
		Map<String, IterationKpiModalValue> modalObjectMap = mock(Map.class);
		when(modalObjectMap.get(jiraIssues.get(0).getNumber())).thenReturn(jiraIssueModalObject);
		jiraIssues.get(0).setSprintName("");

		// Act
		KPIExcelUtility.populateIterationKPI(
				overAllModalValues, modalValues, jiraIssues.get(0), fieldMapping, modalObjectMap);
		assertNotNull(modalValues);
		assertEquals(2, modalValues.size());
		assertNotNull(overAllModalValues);
		assertEquals(2, overAllModalValues.size());
	}

	@Test
	public void populateIterationKPI_When_Actual_Estimation_ValidData() {

		IterationKpiModalValue jiraIssueModalObject = new IterationKpiModalValue();
		IterationKpiModalValue modelValue = new IterationKpiModalValue();
		IterationKpiModalValue iterationKpiModalValue = new IterationKpiModalValue();
		List<IterationKpiModalValue> overAllModalValues = new ArrayList<>();
		overAllModalValues.add(iterationKpiModalValue);
		List<IterationKpiModalValue> modalValues = new ArrayList<>();
		modalValues.add(modelValue);

		jiraIssues.get(0).setOriginalEstimateMinutes(480);
		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.ACTUAL_ESTIMATION);
		when(fieldMapping.getAdditionalFilterConfig())
				.thenReturn(List.of(new AdditionalFilterConfig()));
		Map modalObjectMap = mock(Map.class);
		when(modalObjectMap.get(jiraIssues.get(0).getNumber())).thenReturn(jiraIssueModalObject);

		// Act
		KPIExcelUtility.populateIterationKPI(
				overAllModalValues, modalValues, jiraIssues.get(0), fieldMapping, modalObjectMap);
		assertNotNull(modalValues);
		assertEquals(2, modalValues.size());
		assertNotNull(overAllModalValues);
		assertEquals(2, overAllModalValues.size());
	}

	@Test
	public void populateFlowKPI_ValidData_PopulatesExcelDataList() {
		Map<String, Integer> typeCountMap = new HashMap<>();
		typeCountMap.put("A", 1);
		Map<String, Map<String, Integer>> dateTypeCountMap = new HashMap<>();
		dateTypeCountMap.put("2022-01-01", typeCountMap);
		// Arrange
		List<KPIExcelData> excelDataList = new ArrayList<>();

		// Act
		KPIExcelUtility.populateFlowKPI(dateTypeCountMap, excelDataList);

		// Assert
		assertNotNull(excelDataList);
		assertEquals(1, excelDataList.size());
		assertEquals(1, excelDataList.get(0).getCount().size());
	}

	@Test
	public void populateDirExcelData_ValidData_PopulatesKPIExcelData() {
		List<JiraIssue> defects = new ArrayList<>();
		Set<String> set = new HashSet<String>();
		set.add("STORY1");
		jiraIssues.get(0).setDefectStoryID(set);
		jiraIssues.get(0).setNumber("STORY1");
		defects.add(jiraIssues.get(0));
		jiraIssues.get(1).setDefectStoryID(set);
		jiraIssues.get(1).setNumber("STORY2");
		jiraIssues.get(1).setAggregateTimeOriginalEstimateMinutes(15);
		defects.add(jiraIssues.get(1));
		List<String> storyIds = Arrays.asList("STORY1", "STORY2");
		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		Map<String, JiraIssue> issueData =
				defects.stream().collect(Collectors.toMap(JiraIssue::getNumber, x -> x));

		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.STORY_POINT);
		when(customApiConfig.getpriorityP1()).thenReturn(Constant.P1);
		when(customApiConfig.getpriorityP2()).thenReturn(Constant.P2);
		when(customApiConfig.getpriorityP3()).thenReturn(Constant.P3);
		when(customApiConfig.getpriorityP4()).thenReturn("p4-minor");
		// Act
		Node node = new Node();
		node.setSprintFilter(
				new SprintFilter(
						"sprint-id",
						"TEST| KnowHOW|PI_10|Opensource_Scrum Project",
						LocalDateTime.now().toString(),
						LocalDateTime.now().toString()));

		KPIExcelUtility.populateDirExcelData(
				storyIds, defects, kpiExcelData, issueData, fieldMapping, customApiConfig, node);

		// Assert
		assertEquals(3, kpiExcelData.size());
		assertEquals(
				"TEST| KnowHOW|PI_10|Opensource_Scrum Project", kpiExcelData.get(0).getSprintName());
		assertEquals(
				"TEST| KnowHOW|PI_10|Opensource_Scrum Project", kpiExcelData.get(1).getSprintName());
	}

	@Test
	public void populateDefectDensityExcelData_ValidData_Actual_Estimation() {
		List<JiraIssue> defects = new ArrayList<>();
		Set<String> set = new HashSet<String>();
		set.add("STORY1");
		jiraIssues.get(0).setDefectStoryID(set);
		jiraIssues.get(0).setNumber("STORY1");
		defects.add(jiraIssues.get(0));
		// Arrange
		jiraIssues.get(1).setDefectStoryID(set);
		jiraIssues.get(1).setNumber("STORY2");
		jiraIssues.get(1).setAggregateTimeOriginalEstimateMinutes(15);
		defects.add(jiraIssues.get(1));
		List<String> storyIds = Arrays.asList("STORY1", "STORY2");
		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		Map<String, JiraIssue> issueData =
				defects.stream().collect(Collectors.toMap(JiraIssue::getNumber, x -> x));

		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.STORY_POINT);
		when(customApiConfig.getpriorityP1()).thenReturn(Constant.P1);
		when(customApiConfig.getpriorityP2()).thenReturn(Constant.P2);
		when(customApiConfig.getpriorityP3()).thenReturn(Constant.P3);
		when(customApiConfig.getpriorityP4()).thenReturn("p4-minor");
		// Act
		Node node = new Node();
		node.setSprintFilter(
				new SprintFilter(
						"sprint-id",
						"TEST| KnowHOW|PI_10|Opensource_Scrum Project",
						LocalDateTime.now().toString(),
						LocalDateTime.now().toString()));

		KPIExcelUtility.populateDefectDensityExcelData(
				storyIds, defects, kpiExcelData, issueData, fieldMapping, customApiConfig, node);

		// Assert
		assertEquals(3, kpiExcelData.size());
		assertEquals(
				"TEST| KnowHOW|PI_10|Opensource_Scrum Project", kpiExcelData.get(0).getSprintName());
		assertEquals(
				"TEST| KnowHOW|PI_10|Opensource_Scrum Project", kpiExcelData.get(1).getSprintName());
	}

	@Test
	public void populateDefectDensityExcelData_ValidData() {
		List<JiraIssue> defects = new ArrayList<>();
		Set<String> set = new HashSet<String>();
		set.add("STORY1");
		set.add("STORY2");
		jiraIssues.get(0).setDefectStoryID(set);
		jiraIssues.get(0).setNumber("STORY1");
		jiraIssues.get(0).setAggregateTimeOriginalEstimateMinutes(5);
		defects.add(jiraIssues.get(0));
		jiraIssues.get(1).setDefectStoryID(set);
		jiraIssues.get(1).setNumber("STORY2");
		jiraIssues.get(1).setAggregateTimeOriginalEstimateMinutes(15);
		defects.add(jiraIssues.get(1));

		List<String> storyIds = Arrays.asList("STORY1", "STORY2");
		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		Map<String, JiraIssue> issueData =
				defects.stream().collect(Collectors.toMap(JiraIssue::getNumber, x -> x));

		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.ACTUAL_ESTIMATION);
		when(customApiConfig.getpriorityP1()).thenReturn(Constant.P1);
		when(customApiConfig.getpriorityP2()).thenReturn(Constant.P2);
		when(customApiConfig.getpriorityP3()).thenReturn(Constant.P3);
		when(customApiConfig.getpriorityP4()).thenReturn("p4-minor");
		// Act
		Node node = new Node();
		node.setSprintFilter(
				new SprintFilter(
						"sprint-id",
						"TEST| KnowHOW|PI_10|Opensource_Scrum Project",
						LocalDateTime.now().toString(),
						LocalDateTime.now().toString()));

		KPIExcelUtility.populateDefectDensityExcelData(
				storyIds, defects, kpiExcelData, issueData, fieldMapping, customApiConfig, node);

		// Assert
		assertEquals(4, kpiExcelData.size());
		assertEquals(
				"TEST| KnowHOW|PI_10|Opensource_Scrum Project", kpiExcelData.get(0).getSprintName());
		assertEquals(
				"TEST| KnowHOW|PI_10|Opensource_Scrum Project", kpiExcelData.get(1).getSprintName());
	}

	@Test
	public void populateFTPRExcelData_NonNullJiraIssue() {
		List<JiraIssue> defects = new ArrayList<>();
		Set<String> set = new HashSet<>();
		set.add("STORY1");
		jiraIssues.get(0).setDefectStoryID(set);
		jiraIssues.get(0).setNumber("STORY1");
		defects.add(jiraIssues.get(0));
		jiraIssues.get(1).setDefectStoryID(set);
		jiraIssues.get(1).setNumber("STORY2");
		jiraIssues.get(1).setAggregateTimeOriginalEstimateMinutes(15);
		defects.add(jiraIssues.get(1));
		// Arrange
		String sprint = "Sprint1";
		List<String> storyIds = Arrays.asList("STORY1", "STORY2");
		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		Map<String, JiraIssue> issueData =
				defects.stream().collect(Collectors.toMap(JiraIssue::getNumber, x -> x));

		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.STORY_POINT);
		when(customApiConfig.getpriorityP1()).thenReturn(Constant.P1);
		when(customApiConfig.getpriorityP2()).thenReturn(Constant.P2);
		when(customApiConfig.getpriorityP3()).thenReturn(Constant.P3);
		when(customApiConfig.getpriorityP4()).thenReturn("p4-minor");
		// Act
		Node node = new Node();
		node.setSprintFilter(
				new SprintFilter(
						"sprint-id",
						"TEST| KnowHOW|PI_10|Opensource_Scrum Project",
						LocalDateTime.now().toString(),
						LocalDateTime.now().toString()));

		excelUtility.populateFTPRExcelData(
				storyIds,
				jiraIssues,
				kpiExcelData,
				issueData,
				defects,
				customApiConfig,
				fieldMapping,
				node);
		// Assert
		assertEquals(3, kpiExcelData.size());
		assertEquals(
				"TEST| KnowHOW|PI_10|Opensource_Scrum Project", kpiExcelData.get(0).getSprintName());
		assertEquals(
				"TEST| KnowHOW|PI_10|Opensource_Scrum Project", kpiExcelData.get(1).getSprintName());
	}

	@Test
	public void testPopulateDefectSeepageRateExcelData() {
		Map<String, JiraIssue> totalBugList = new HashMap<>();
		totalBugList.put("STORY1", jiraIssues.get(0));

		List<DSRValidationData> dsrValidationDataList = new ArrayList<>();
		DSRValidationData dsrValidationData = new DSRValidationData();
		dsrValidationData.setIssueNumber("STORY1");
		dsrValidationData.setLabel("test");
		dsrValidationDataList.add(dsrValidationData);

		// Arrange
		String sprint = "Sprint1";
		List<KPIExcelData> kpiExcelData = new ArrayList<>();
		when(customApiConfig.getpriorityP1()).thenReturn(Constant.P1);
		when(customApiConfig.getpriorityP2()).thenReturn(Constant.P2);
		when(customApiConfig.getpriorityP3()).thenReturn(Constant.P3);
		when(customApiConfig.getpriorityP4()).thenReturn("p4-minor");

		// Act
		KPIExcelUtility.populateDefectSeepageRateExcelData(
				sprint, totalBugList, dsrValidationDataList, kpiExcelData, customApiConfig, storyList);
		// Assert
		assertEquals(1, kpiExcelData.size());
		assertEquals("Sprint1", kpiExcelData.get(0).getSprintName());
	}

	@Test
	public void populateDefectRelatedExcelData_ValidData_PopulatesKPIExcelData() {
		List<JiraIssue> defects = new ArrayList<>();
		Set<String> set = new HashSet<>();
		set.add("STORY1");
		jiraIssues.get(0).setDefectStoryID(set);
		jiraIssues.get(0).setNumber("STORY1");
		defects.add(jiraIssues.get(0));
		// Arrange
		String sprint = "Sprint1";
		List<String> storyIds = Arrays.asList("STORY1", "STORY2");
		List<KPIExcelData> kpiExcelData = new ArrayList<>();
		List<String> priority = new ArrayList<>();
		Map<String, List<String>> pr = new HashMap<>();
		priority.add("p4-minor");
		priority.add("4");
		priority.add("p4");
		priority.add("minor");
		priority.add("Low");
		pr.put("p4-minor", priority);
		customApiConfig.setPriority(pr);
		Map<String, JiraIssue> issueData =
				defects.stream().collect(Collectors.toMap(JiraIssue::getNumber, x -> x));
		when(customApiConfig.getpriorityP1()).thenReturn(Constant.P1);
		when(customApiConfig.getpriorityP2()).thenReturn(Constant.P2);
		when(customApiConfig.getpriorityP3()).thenReturn(Constant.P3);
		when(customApiConfig.getpriorityP4()).thenReturn("p4-minor");
		// Act
		KPIExcelUtility.populateDefectRelatedExcelData(
				sprint, defects, kpiExcelData, customApiConfig, storyList);

		// Assert
		assertEquals(1, kpiExcelData.size());
		assertEquals("Sprint1", kpiExcelData.get(0).getSprintName());
	}

	@Test
	public void testPopulateDefectRCAandStatusRelatedExcelData_ValidData() {
		List<JiraIssue> jiraIssue = new ArrayList<>();

		List<JiraIssue> createDuringIteration = new ArrayList<>();
		Set<String> set = new HashSet<String>();
		set.add("STORY1");
		jiraIssues.get(0).setDefectStoryID(set);
		jiraIssues.get(0).setNumber("STORY1");
		jiraIssues.get(0).setAggregateTimeOriginalEstimateMinutes(5);
		jiraIssue.add(jiraIssues.get(0));
		// Arrange
		String sprint = "Sprint1";
		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.ACTUAL_ESTIMATION);

		// Act
		KPIExcelUtility.populateDefectRCAandStatusRelatedExcelData(
				sprint, jiraIssue, createDuringIteration, kpiExcelData, fieldMapping);

		// Assert
		assertEquals(1, kpiExcelData.size());
		assertEquals("Sprint1", kpiExcelData.get(0).getSprintName());
	}

	@Test
	public void testPopulateCreatedVsResolvedExcelData_ValidData() {
		List<JiraIssue> jiraIssue = new ArrayList<>();
		Set<String> set = new HashSet<>();
		set.add("STORY1");
		jiraIssues.get(0).setDefectStoryID(set);
		jiraIssues.get(0).setNumber("STORY1");
		jiraIssues.get(0).setAggregateTimeOriginalEstimateMinutes(5);
		jiraIssue.add(jiraIssues.get(0));
		when(customApiConfig.getpriorityP1()).thenReturn(Constant.P1);
		when(customApiConfig.getpriorityP2()).thenReturn(Constant.P2);
		when(customApiConfig.getpriorityP3()).thenReturn(Constant.P3);
		when(customApiConfig.getpriorityP4()).thenReturn("p4-minor");
		Map<String, String> map = new HashMap<>();
		map.put(jiraIssues.get(0).getNumber(), jiraIssues.get(0).getStatus());
		List<JiraIssue> createdConditionStories = new ArrayList<>();
		createdConditionStories.add(jiraIssues.get(0));
		// Arrange
		String sprint = "Sprint1";
		List<KPIExcelData> kpiExcelData = new ArrayList<>();
		Map<String, JiraIssue> issueData =
				jiraIssue.stream().collect(Collectors.toMap(JiraIssue::getNumber, x -> x));
		// Act
		KPIExcelUtility.populateCreatedVsResolvedExcelData(
				sprint, issueData, createdConditionStories, map, kpiExcelData, customApiConfig, storyList);
		// Assert
		assertEquals(1, kpiExcelData.size());
		assertEquals("Sprint1", kpiExcelData.get(0).getSprintName());
	}

	@Test
	public void testPopulateBackLogData() {
		JiraIssue jiraIssue = new JiraIssue();
		jiraIssue.setTypeName("bug");
		jiraIssue.setUrl("abc");
		jiraIssue.setNumber("1");
		jiraIssue.setPriority("5");
		jiraIssue.setName("Testing");
		List<String> status = new ArrayList<>();
		status.add("In Development");
		List<IterationKpiModalValue> overAllmodalValues = new ArrayList<>();
		List<IterationKpiModalValue> modalValues = new ArrayList<>();
		JiraIssueCustomHistory issueCustomHistory = new JiraIssueCustomHistory();
		issueCustomHistory.setStoryID("1");
		issueCustomHistory.setCreatedDate(DateTime.now().now());
		List<JiraHistoryChangeLog> statusUpdationLog = new ArrayList<>();
		JiraHistoryChangeLog jiraHistoryChangeLog = new JiraHistoryChangeLog();
		jiraHistoryChangeLog.setChangedTo("In Development");
		jiraHistoryChangeLog.setUpdatedOn(LocalDateTime.now());
		statusUpdationLog.add(jiraHistoryChangeLog);
		issueCustomHistory.setStatusUpdationLog(statusUpdationLog);
		KPIExcelUtility.populateBackLogData(
				overAllmodalValues, modalValues, jiraIssue, issueCustomHistory, status);
		Assert.assertNotNull(modalValues);
		Assert.assertNotNull(overAllmodalValues);
	}

	@Test
	public void testPopulateIssueModal() {

		IssueKpiModalValue jiraIssueModalObject = new IssueKpiModalValue();
		AdditionalFilterConfig config = new AdditionalFilterConfig();

		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.STORY_POINT);
		when(fieldMapping.getAdditionalFilterConfig()).thenReturn(List.of(config));
		Map<String, IssueKpiModalValue> modalObjectMap = mock(Map.class);
		when(modalObjectMap.get(jiraIssues.get(0).getNumber())).thenReturn(jiraIssueModalObject);

		// Act
		KPIExcelUtility.populateIssueModal(jiraIssues.get(0), fieldMapping, modalObjectMap);
		assertNotNull(modalObjectMap);
	}

	@Test
	public void testPopulateIssueModalOriginalEstimate() {

		IssueKpiModalValue jiraIssueModalObject = new IssueKpiModalValue();
		AdditionalFilterConfig config = new AdditionalFilterConfig();
		jiraIssues.get(0).setOriginalEstimateMinutes(480);
		jiraIssues.get(0).setRemainingEstimateMinutes(null);

		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.ACTUAL_ESTIMATION);
		when(fieldMapping.getAdditionalFilterConfig()).thenReturn(List.of(config));
		Map<String, IssueKpiModalValue> modalObjectMap = mock(Map.class);
		when(modalObjectMap.get(jiraIssues.get(0).getNumber())).thenReturn(jiraIssueModalObject);

		// Act
		KPIExcelUtility.populateIssueModal(jiraIssues.get(0), fieldMapping, modalObjectMap);
		assertNotNull(modalObjectMap);
	}

	@Test
	public void testPopulateReleasePlanExcelData() {

		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.STORY_POINT);

		excelUtility.populateReleasePlanExcelData(jiraIssues, kpiExcelData, fieldMapping);

		// Assert
		assertEquals(48, kpiExcelData.size());
	}

	@Test
	public void testPopulateReleasePlanExcelData2() {

		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.ACTUAL_ESTIMATION);

		jiraIssues.get(0).setAggregateTimeOriginalEstimateMinutes(480);

		excelUtility.populateReleasePlanExcelData(jiraIssues, kpiExcelData, fieldMapping);

		// Assert
		assertEquals(48, kpiExcelData.size());
	}

	@Test
	public void testPopulateIterationReadinessExcelData() {

		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.STORY_POINT);

		excelUtility.populateIterationReadinessExcelData(jiraIssues, kpiExcelData, fieldMapping);

		// Assert
		assertEquals(48, kpiExcelData.size());
	}

	@Test
	public void testPopulateIterationReadinessExcelData2() {

		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.ACTUAL_ESTIMATION);

		jiraIssues.get(0).setAggregateTimeOriginalEstimateMinutes(480);

		excelUtility.populateIterationReadinessExcelData(jiraIssues, kpiExcelData, fieldMapping);

		// Assert
		assertEquals(48, kpiExcelData.size());
	}

	@Test
	public void testPopulateReleaseDefectWithTestPhasesRelatedExcelData() {

		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		excelUtility.populateReleaseDefectWithTestPhasesRelatedExcelData(jiraIssues, kpiExcelData);

		// Assert
		assertEquals(48, kpiExcelData.size());
	}

	@Test
	public void testPopulateBacklogDefectCountExcelData() {

		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		excelUtility.populateBacklogDefectCountExcelData(jiraIssues, kpiExcelData);

		// Assert
		assertEquals(48, kpiExcelData.size());
	}

	@Test
	public void testPopulateReleaseBurnUpExcelData() {

		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.STORY_POINT);

		Map<String, LocalDateTime> issueWiseReleaseTagDateMap = new HashMap<>();
		Map<String, LocalDateTime> completeDateIssueMap = new HashMap<>();
		Map<String, LocalDateTime> devCompleteDateIssueMap = new HashMap<>();

		excelUtility.populateReleaseBurnUpExcelData(
				jiraIssues,
				issueWiseReleaseTagDateMap,
				completeDateIssueMap,
				devCompleteDateIssueMap,
				kpiExcelData,
				fieldMapping);

		// Assert
		assertEquals(48, kpiExcelData.size());
	}

	@Test
	public void testPopulateReleaseBurnUpExcelData2() {

		List<KPIExcelData> kpiExcelData = new ArrayList<>();

		FieldMapping fieldMapping = mock(FieldMapping.class);
		when(fieldMapping.getEstimationCriteria()).thenReturn(CommonConstant.ACTUAL_ESTIMATION);

		jiraIssues.get(0).setAggregateTimeOriginalEstimateMinutes(480);
		Map<String, LocalDateTime> issueWiseReleaseTagDateMap = new HashMap<>();
		Map<String, LocalDateTime> completeDateIssueMap = new HashMap<>();
		Map<String, LocalDateTime> devCompleteDateIssueMap = new HashMap<>();

		excelUtility.populateReleaseBurnUpExcelData(
				jiraIssues,
				issueWiseReleaseTagDateMap,
				completeDateIssueMap,
				devCompleteDateIssueMap,
				kpiExcelData,
				fieldMapping);

		// Assert
		assertEquals(48, kpiExcelData.size());
	}

	@Test
	public void testPopulateDeploymentFrequencyExcelData() {
		// Setup mock data
		List<String> jobNameList = List.of("Job1", "Job2");
		List<String> monthList = List.of("Week1", "Week2");
		List<String> environmentList = List.of("Env1", "Env2");
		List<String> deploymentDateList = List.of("2022-01-01", "2022-01-02");
		String projectName = "projectName";
		Map<String, String> deploymentMapPipelineNameWise = new HashMap<>();
		deploymentMapPipelineNameWise.put("pipeline1", "ddd");
		when(deploymentFrequencyInfo.getJobNameList()).thenReturn(jobNameList);
		when(deploymentFrequencyInfo.getMonthList()).thenReturn(monthList);
		when(deploymentFrequencyInfo.getEnvironmentList()).thenReturn(environmentList);
		when(deploymentFrequencyInfo.getDeploymentDateList()).thenReturn(deploymentDateList);

		// Call the method
		KPIExcelUtility.populateDeploymentFrequencyExcelData(
				projectName, deploymentFrequencyInfo, kpiExcelData, deploymentMapPipelineNameWise);

		// Verify the results
		assertEquals(2, kpiExcelData.size());
		assertEquals("Job1", kpiExcelData.get(0).getJobName());
		assertEquals("Week1", kpiExcelData.get(0).getWeeks());
		assertEquals("Env1", kpiExcelData.get(0).getDeploymentEnvironment());
		assertEquals("Job2", kpiExcelData.get(1).getJobName());
		assertEquals("Week2", kpiExcelData.get(1).getWeeks());
		assertEquals("Env2", kpiExcelData.get(1).getDeploymentEnvironment());
	}

	@Test
	public void testPopulateDeploymentFrequencyExcelData_ValidData() {
		// Setup mock data
		List<String> jobNameList = Arrays.asList("Job1", "Job2");
		List<String> monthList = Arrays.asList("Week1", "Week2");
		List<String> environmentList = Arrays.asList("Env1", "Env2");
		List<String> deploymentDateList = List.of("2022-01-01", "2022-01-02");
		String projectName = "projectName";
		Map<String, String> deploymentMapPipelineNameWise = new HashMap<>();
		deploymentMapPipelineNameWise.put("pipeline1", "ddd");

		when(deploymentFrequencyInfo.getJobNameList()).thenReturn(jobNameList);
		when(deploymentFrequencyInfo.getMonthList()).thenReturn(monthList);
		when(deploymentFrequencyInfo.getEnvironmentList()).thenReturn(environmentList);
		when(deploymentFrequencyInfo.getDeploymentDateList()).thenReturn(deploymentDateList);

		// Call the method
		KPIExcelUtility.populateDeploymentFrequencyExcelData(
				projectName, deploymentFrequencyInfo, kpiExcelData, deploymentMapPipelineNameWise);

		// Verify the results
		assertEquals(2, kpiExcelData.size());
		assertEquals("Job1", kpiExcelData.get(0).getJobName());
		assertEquals("Week1", kpiExcelData.get(0).getWeeks());
		assertEquals("Env1", kpiExcelData.get(0).getDeploymentEnvironment());
		assertEquals("Job2", kpiExcelData.get(1).getJobName());
		assertEquals("Week2", kpiExcelData.get(1).getWeeks());
		assertEquals("Env2", kpiExcelData.get(1).getDeploymentEnvironment());
	}

	@Test
	public void testPopulateDeploymentFrequencyExcelData_EmptyData() {
		// Setup mock data
		List<String> jobNameList = Arrays.asList();
		List<String> monthList = Arrays.asList();
		List<String> environmentList = Arrays.asList();
		List<String> deploymentDateList = List.of();
		String projectName = "projectName";
		Map<String, String> deploymentMapPipelineNameWise = new HashMap<>();
		// Call the method
		KPIExcelUtility.populateDeploymentFrequencyExcelData(
				projectName, deploymentFrequencyInfo, kpiExcelData, deploymentMapPipelineNameWise);

		// Verify the results
		assertEquals(0, kpiExcelData.size());
	}

	@Test
	public void testPopulateDirExcelData_ValidData() {
		// Setup test data
		List<String> storyIds = Arrays.asList("STORY-001", "STORY-002");
		List<JiraIssue> defects = new ArrayList<>();
		JiraIssue defect1 = createTestJiraIssue("DEF-001", "Bug", "P1");
		defects.add(defect1);

		Map<String, JiraIssue> issueData = new HashMap<>();
		JiraIssue story1 = createTestJiraIssue("STORY-001", "Story", "P2");
		issueData.put("STORY-001", story1);

		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setEstimationCriteria("Story Point");
		fieldMapping.setStoryPointToHourMapping(8.0);

		Node node = createTestNode();

		// Call the method
		KPIExcelUtility.populateDirExcelData(
				storyIds, defects, kpiExcelData, issueData, fieldMapping, customApiConfig, node);

		// Verify results
		assertNotNull(kpiExcelData);
		assertEquals(2, kpiExcelData.size());
		assertEquals("Sprint 1", kpiExcelData.get(0).getSprintName());
	}

	@Test
	public void testPopulateDirExcelData_EmptyStoryIds() {
		// Setup test data with empty story IDs
		List<String> storyIds = new ArrayList<>();
		List<JiraIssue> defects = new ArrayList<>();
		Map<String, JiraIssue> issueData = new HashMap<>();
		FieldMapping fieldMapping = new FieldMapping();
		Node node = createTestNode();

		// Call the method
		KPIExcelUtility.populateDirExcelData(
				storyIds, defects, kpiExcelData, issueData, fieldMapping, customApiConfig, node);

		// Verify results - should not add any data
		assertEquals(0, kpiExcelData.size());
	}

	@Test
	public void testPopulateDefectDensityExcelData_ValidData() {
		// Setup test data
		List<String> storyIds = Arrays.asList("STORY-001", "STORY-002");
		List<JiraIssue> defects = new ArrayList<>();
		JiraIssue defect1 = createTestJiraIssue("DEF-001", "Bug", "P1");
		defects.add(defect1);

		Map<String, JiraIssue> issueData = new HashMap<>();
		JiraIssue story1 = createTestJiraIssue("STORY-001", "Story", "P2");
		issueData.put("STORY-001", story1);

		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setEstimationCriteria("Story Point");

		Node node = createTestNode();

		// Call the method
		KPIExcelUtility.populateDefectDensityExcelData(
				storyIds, defects, kpiExcelData, issueData, fieldMapping, customApiConfig, node);

		// Verify results
		assertNotNull(kpiExcelData);
		assertEquals(2, kpiExcelData.size());
		assertEquals("Sprint 1", kpiExcelData.get(0).getSprintName());
	}

	@Test
	public void testPopulateDefectDensityExcelData_EmptyStoryIds() {
		// Setup test data with empty story IDs
		List<String> storyIds = new ArrayList<>();
		List<JiraIssue> defects = new ArrayList<>();
		Map<String, JiraIssue> issueData = new HashMap<>();
		FieldMapping fieldMapping = new FieldMapping();
		Node node = createTestNode();

		// Call the method
		KPIExcelUtility.populateDefectDensityExcelData(
				storyIds, defects, kpiExcelData, issueData, fieldMapping, customApiConfig, node);

		// Verify results - should not add any data
		assertEquals(0, kpiExcelData.size());
	}

	@Test
	public void testPopulateReleasePlanExcelData_ValidData() {
		// Setup test data
		List<JiraIssue> jiraIssues = new ArrayList<>();
		JiraIssue issue1 = createTestJiraIssue("STORY-001", "Story", "P1");
		issue1.setStoryPoints(5.0);
		issue1.setStatus("In Progress");
		issue1.setAssigneeId("user1");
		issue1.setAssigneeName("John Doe");
		issue1.setDueDate("2023-12-31");
		jiraIssues.add(issue1);

		JiraIssue issue2 = createTestJiraIssue("STORY-002", "Story", "P2");
		issue2.setAggregateTimeOriginalEstimateMinutes(480); // 8 hours
		issue2.setStatus("Done");
		jiraIssues.add(issue2);

		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setEstimationCriteria("Story Point");
		fieldMapping.setStoryPointToHourMapping(8.0);

		// Call the method
		KPIExcelUtility.populateReleasePlanExcelData(jiraIssues, kpiExcelData, fieldMapping);

		// Verify results
		assertNotNull(kpiExcelData);
		assertEquals(2, kpiExcelData.size());

		// Verify first issue
		KPIExcelData excelData1 = kpiExcelData.get(0);
		assertEquals("Test Story 001", excelData1.getIssueDesc());
		assertEquals("In Progress", excelData1.getIssueStatus());
		assertEquals("Story", excelData1.getIssueType());
		assertEquals("P1", excelData1.getPriority());
		assertEquals("5.0", excelData1.getStoryPoint());

		// Verify second issue
		KPIExcelData excelData2 = kpiExcelData.get(1);
		assertEquals("Test Story 002", excelData2.getIssueDesc());
		assertEquals("Done", excelData2.getIssueStatus());
	}

	@Test
	public void testPopulateReleasePlanExcelData_EmptyJiraIssues() {
		// Setup test data with empty issues
		List<JiraIssue> jiraIssues = new ArrayList<>();
		FieldMapping fieldMapping = new FieldMapping();

		// Call the method
		KPIExcelUtility.populateReleasePlanExcelData(jiraIssues, kpiExcelData, fieldMapping);

		// Verify results - should not add any data
		assertEquals(0, kpiExcelData.size());
	}

	@Test
	public void testPopulateReleasePlanExcelData_NullJiraIssues() {
		// Setup test data with null issues
		List<JiraIssue> jiraIssues = null;
		FieldMapping fieldMapping = new FieldMapping();

		// Call the method
		KPIExcelUtility.populateReleasePlanExcelData(jiraIssues, kpiExcelData, fieldMapping);

		// Verify results - should not add any data
		assertEquals(0, kpiExcelData.size());
	}

	// Helper methods for test data creation
	private JiraIssue createTestJiraIssue(String number, String typeName, String priority) {
		JiraIssue issue = new JiraIssue();
		issue.setNumber(number);
		issue.setTypeName(typeName);
		issue.setPriority(priority);
		String[] parts = number.split("-");
		String suffix = parts.length > 1 ? parts[1] : "001";
		issue.setName("Test " + typeName + " " + suffix);
		issue.setUrl("https://test.jira.com/browse/" + number);
		issue.setStatus("Open");
		issue.setTimeSpentInMinutes(120); // 2 hours
		issue.setStoryPoints(5.0); // Set story points to avoid NullPointerException
		issue.setRootCauseList(Arrays.asList("Code Issue", "Design Issue"));
		issue.setLabels(Arrays.asList("bug", "critical"));

		// Set additional filters for squad testing
		AdditionalFilter additionalFilter = new AdditionalFilter();
		AdditionalFilterValue filterValue = new AdditionalFilterValue();
		filterValue.setValue("Squad A");
		additionalFilter.setFilterValues(Arrays.asList(filterValue));
		issue.setAdditionalFilters(Arrays.asList(additionalFilter));

		// Set defectStoryID to avoid NullPointerException
		Set<String> defectStoryIds = new HashSet<>();
		defectStoryIds.add("STORY-001");
		issue.setDefectStoryID(defectStoryIds);

		return issue;
	}

	private Node createTestNode() {
		Node node = new Node();
		SprintFilter sprintFilter = new SprintFilter("sprint1", "Sprint 1", "2023-01-01", "2023-01-15");
		node.setSprintFilter(sprintFilter);
		return node;
	}

	@Test
	public void testPopulateDefectWithReopenInfoExcelData_ValidData() {
		// Setup test data
		String sprint = "Sprint 1";
		List<JiraIssue> storyList = new ArrayList<>();
		JiraIssue story = createTestJiraIssue("STORY-001", "Story", "P2");
		storyList.add(story);

		Map<String, List<com.publicissapient.kpidashboard.apis.model.DefectTransitionInfo>>
				reopenedDefectInfoMap = new HashMap<>();
		List<com.publicissapient.kpidashboard.apis.model.DefectTransitionInfo> transitionInfoList =
				new ArrayList<>();

		// Create DefectTransitionInfo mock
		com.publicissapient.kpidashboard.apis.model.DefectTransitionInfo defectTransitionInfo =
				Mockito.mock(com.publicissapient.kpidashboard.apis.model.DefectTransitionInfo.class);
		JiraIssue defectIssue = createTestJiraIssue("DEF-001", "Bug", "P1");

		when(defectTransitionInfo.getDefectJiraIssue()).thenReturn(defectIssue);
		when(defectTransitionInfo.getReopenDate()).thenReturn(new DateTime("2023-01-15T10:00:00.000Z"));
		when(defectTransitionInfo.getClosedDate()).thenReturn(new DateTime("2023-01-20T15:00:00.000Z"));
		when(defectTransitionInfo.getReopenDuration()).thenReturn(120.0);

		transitionInfoList.add(defectTransitionInfo);
		reopenedDefectInfoMap.put("DEF-001", transitionInfoList);

		// Call the method
		KPIExcelUtility.populateDefectWithReopenInfoExcelData(
				sprint, kpiExcelData, customApiConfig, storyList, reopenedDefectInfoMap);

		// Verify results
		assertNotNull(kpiExcelData);
		assertEquals(1, kpiExcelData.size());

		KPIExcelData excelData = kpiExcelData.get(0);
		assertEquals("Sprint 1", excelData.getSprintName());
		assertEquals("Test Bug 001", excelData.getDefectDesc());
		assertNotNull(excelData.getDefectId());
		assertEquals("120.0Hrs", excelData.getDurationToReopen());
	}

	@Test
	public void testPopulateDefectWithReopenInfoExcelData_EmptyMap() {
		// Setup test data with empty map
		String sprint = "Sprint 1";
		List<JiraIssue> storyList = new ArrayList<>();
		Map<String, List<com.publicissapient.kpidashboard.apis.model.DefectTransitionInfo>>
				reopenedDefectInfoMap = new HashMap<>();

		// Call the method
		KPIExcelUtility.populateDefectWithReopenInfoExcelData(
				sprint, kpiExcelData, customApiConfig, storyList, reopenedDefectInfoMap);

		// Verify results - should not add any data
		assertEquals(0, kpiExcelData.size());
	}

	@Test
	public void testPopulateDefectWithReopenInfoExcelData_NullMap() {
		// Setup test data with null map
		String sprint = "Sprint 1";
		List<JiraIssue> storyList = new ArrayList<>();
		Map<String, List<com.publicissapient.kpidashboard.apis.model.DefectTransitionInfo>>
				reopenedDefectInfoMap = null;

		// Call the method
		KPIExcelUtility.populateDefectWithReopenInfoExcelData(
				sprint, kpiExcelData, customApiConfig, storyList, reopenedDefectInfoMap);

		// Verify results - should not add any data
		assertEquals(0, kpiExcelData.size());
	}

	@Test
	public void testPopulateReleasePlanExcelData_WithTimeBasedEstimation() {
		// Setup test data with time-based estimation
		List<JiraIssue> jiraIssues = new ArrayList<>();
		JiraIssue issue1 = createTestJiraIssue("STORY-001", "Story", "P1");
		issue1.setAggregateTimeOriginalEstimateMinutes(480); // 8 hours
		issue1.setStatus("In Progress");
		jiraIssues.add(issue1);

		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setEstimationCriteria("Hours"); // Not Story Point
		fieldMapping.setStoryPointToHourMapping(8.0);

		// Call the method
		KPIExcelUtility.populateReleasePlanExcelData(jiraIssues, kpiExcelData, fieldMapping);

		// Verify results
		assertNotNull(kpiExcelData);
		assertEquals(1, kpiExcelData.size());

		KPIExcelData excelData = kpiExcelData.get(0);
		assertEquals("Test Story 001", excelData.getIssueDesc());
		assertEquals("In Progress", excelData.getIssueStatus());
		// Should contain time-based story point calculation
		assertNotNull(excelData.getStoryPoint());
		assertTrue(excelData.getStoryPoint().contains("hrs"));
	}

	@Test
	public void testPopulateReleasePlanExcelData_WithNullEstimationCriteria() {
		// Setup test data with null estimation criteria
		List<JiraIssue> jiraIssues = new ArrayList<>();
		JiraIssue issue1 = createTestJiraIssue("STORY-001", "Story", "P1");
		issue1.setStoryPoints(5.0);
		issue1.setAggregateTimeOriginalEstimateMinutes(null);
		jiraIssues.add(issue1);

		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setEstimationCriteria(null);
		fieldMapping.setStoryPointToHourMapping(8.0);

		// Call the method
		KPIExcelUtility.populateReleasePlanExcelData(jiraIssues, kpiExcelData, fieldMapping);

		// Verify results
		assertNotNull(kpiExcelData);
		assertEquals(1, kpiExcelData.size());

		KPIExcelData excelData = kpiExcelData.get(0);
		assertEquals("Test Story 001", excelData.getIssueDesc());
		// Story point should be null when estimation criteria is null and no time
		// estimate
		assertNull(excelData.getStoryPoint());
	}

	@Test
	public void testCreateTestJiraIssue_WithAdditionalFilters() {
		// Test the helper method to ensure it creates proper test data
		JiraIssue issue = createTestJiraIssue("TEST-001", "Bug", "P1");

		// Verify basic properties
		assertEquals("TEST-001", issue.getNumber());
		assertEquals("Bug", issue.getTypeName());
		assertEquals("P1", issue.getPriority());
		assertEquals("Test Bug 001", issue.getName());
		assertEquals("https://test.jira.com/browse/TEST-001", issue.getUrl());
		assertEquals("Open", issue.getStatus());
		assertEquals(Integer.valueOf(120), issue.getTimeSpentInMinutes());

		// Verify additional filters
		assertNotNull(issue.getAdditionalFilters());
		assertEquals(1, issue.getAdditionalFilters().size());
		assertEquals(
				"Squad A", issue.getAdditionalFilters().get(0).getFilterValues().get(0).getValue());

		// Verify root cause and labels
		assertNotNull(issue.getRootCauseList());
		assertEquals(2, issue.getRootCauseList().size());
		assertTrue(issue.getRootCauseList().contains("Code Issue"));
		assertTrue(issue.getRootCauseList().contains("Design Issue"));

		assertNotNull(issue.getLabels());
		assertEquals(2, issue.getLabels().size());
		assertTrue(issue.getLabels().contains("bug"));
		assertTrue(issue.getLabels().contains("critical"));
	}

	@Test
	public void testCreateTestNode_Properties() {
		// Test the helper method to ensure it creates proper test data
		Node node = createTestNode();

		// Verify node properties
		assertNotNull(node);
		assertNotNull(node.getSprintFilter());
		assertEquals("Sprint 1", node.getSprintFilter().getName());
	}

	@Test
	public void testPopulateDirExcelData_WithNullDefects() {
		// Setup test data with empty defects
		List<String> storyIds = Arrays.asList("STORY-001");
		List<JiraIssue> defects = new ArrayList<>();

		Map<String, JiraIssue> issueData = new HashMap<>();
		JiraIssue story1 = createTestJiraIssue("STORY-001", "Story", "P2");
		issueData.put("STORY-001", story1);

		FieldMapping fieldMapping = new FieldMapping();
		Node node = createTestNode();

		// Call the method
		KPIExcelUtility.populateDirExcelData(
				storyIds, defects, kpiExcelData, issueData, fieldMapping, customApiConfig, node);

		// Verify results - should handle null defects gracefully
		assertNotNull(kpiExcelData);
		// The method should still process stories even with null defects
		assertEquals(1, kpiExcelData.size());
	}

	@Test
	public void testPopulateDefectDensityExcelData_WithNullDefects() {
		// Setup test data with empty defects
		List<String> storyIds = Arrays.asList("STORY-001");
		List<JiraIssue> defects = new ArrayList<>();

		Map<String, JiraIssue> issueData = new HashMap<>();
		JiraIssue story1 = createTestJiraIssue("STORY-001", "Story", "P2");
		issueData.put("STORY-001", story1);

		FieldMapping fieldMapping = new FieldMapping();
		Node node = createTestNode();

		// Call the method
		KPIExcelUtility.populateDefectDensityExcelData(
				storyIds, defects, kpiExcelData, issueData, fieldMapping, customApiConfig, node);

		// Verify results - should handle null defects gracefully
		assertNotNull(kpiExcelData);
		// The method should still process stories even with null defects
		assertEquals(1, kpiExcelData.size());
	}

	@Test
	public void when_ValidAutomatedAndManualCases_expect_ExcelRowsWithTypesAndAvgSeconds() {
		String sprint = "Sprint-1";
		List<TestCaseDetails> allTests = new ArrayList<>();
		List<TestCaseDetails> automatedTests = new ArrayList<>();
		List<TestCaseDetails> manualTests = new ArrayList<>();
		List<KPIExcelData> kpiExcelDataLocal = new ArrayList<>();

		TestCaseDetails tcAutomated = new TestCaseDetails();
		tcAutomated.setNumber("TC-1");
		tcAutomated.setTestCaseStatus("Pass");
		tcAutomated.setDefectStoryID(new HashSet<>(Arrays.asList("STORY-1")));

		TestCaseExecutionData a1 = new TestCaseExecutionData();
		a1.setExecutionTime(2000);
		a1.setAutomated(true);
		TestCaseExecutionData a2 = new TestCaseExecutionData();
		a2.setExecutionTime(4000);
		a2.setAutomated(true);
		tcAutomated.setExecutions(Arrays.asList(a1, a2));

		TestCaseDetails tcManual = new TestCaseDetails();
		tcManual.setNumber("TC-2");
		tcManual.setTestCaseStatus("Fail");
		tcManual.setDefectStoryID(new HashSet<>(Arrays.asList("STORY-1")));

		TestCaseExecutionData m1 = new TestCaseExecutionData();
		m1.setExecutionTime(3000); // ms
		m1.setAutomated(false);
		tcManual.setExecutions(Arrays.asList(m1));

		allTests.add(tcAutomated);
		allTests.add(tcManual);
		automatedTests.add(tcAutomated);
		manualTests.add(tcManual);

		KPIExcelUtility.populateTestExecutionTimeExcelData(
				sprint,
				allTests,
				automatedTests,
				manualTests,
				kpiExcelDataLocal,
				KPICode.TEST_EXECUTION_TIME.getKpiId(),
				"");

		assertEquals(2, kpiExcelDataLocal.size());

		KPIExcelData rowTC1 =
				kpiExcelDataLocal.stream()
						.filter(r -> "TC-1".equals(r.getTestCaseId()))
						.findFirst()
						.orElseThrow(AssertionError::new);

		assertEquals("Sprint-1", rowTC1.getSprintName());
		assertEquals("Automated", rowTC1.getTestCaseType());
		assertEquals("3.0", rowTC1.getExecutionTime()); // (2000+4000)/2 ms => 3.0 sec

		KPIExcelData rowTC2 =
				kpiExcelDataLocal.stream()
						.filter(r -> "TC-2".equals(r.getTestCaseId()))
						.findFirst()
						.orElseThrow(AssertionError::new);

		assertEquals("Sprint-1", rowTC2.getSprintName());
		assertEquals("Manual", rowTC2.getTestCaseType());
		assertEquals("3.0", rowTC2.getExecutionTime()); // 3000 ms => 3.0 sec
	}

	@Test
	public void when_TestCaseNotInAutomatedOrManual_expect_EmptyTypeAndAvgSecondsComputed() {
		String sprint = "Sprint-X";
		List<TestCaseDetails> allTests = new ArrayList<>();
		List<TestCaseDetails> automatedTests = new ArrayList<>();
		List<TestCaseDetails> manualTests = new ArrayList<>();
		List<KPIExcelData> kpiExcelDataLocal = new ArrayList<>();

		TestCaseDetails tcOther = new TestCaseDetails();
		tcOther.setNumber("TC-3");
		tcOther.setTestCaseStatus("Blocked");
		tcOther.setDefectStoryID(new HashSet<>(Arrays.asList("S-10")));

		TestCaseExecutionData e = new TestCaseExecutionData();
		e.setExecutionTime(1500); // 1.5 sec
		e.setAutomated(false);
		tcOther.setExecutions(Arrays.asList(e));

		allTests.add(tcOther);

		KPIExcelUtility.populateTestExecutionTimeExcelData(
				sprint,
				allTests,
				automatedTests,
				manualTests,
				kpiExcelDataLocal,
				KPICode.TEST_EXECUTION_TIME.getKpiId(),
				"");

		assertEquals(1, kpiExcelDataLocal.size());
		KPIExcelData row = kpiExcelDataLocal.get(0);
		assertEquals("TC-3", row.getTestCaseId());
		assertEquals("", row.getTestCaseType()); // not in either list
		assertEquals("1.5", row.getExecutionTime()); // 1500 ms => 1.5 sec
	}

	@Test
	public void when_AllTestsEmpty_expect_NoRows() {
		List<KPIExcelData> kpiExcelDataLocal = new ArrayList<>();

		KPIExcelUtility.populateTestExecutionTimeExcelData(
				"Sprint-Empty",
				new ArrayList<>(), // allTestList
				new ArrayList<>(), // automatedList
				new ArrayList<>(), // manualList
				kpiExcelDataLocal,
				KPICode.TEST_EXECUTION_TIME.getKpiId(),
				"");

		assertTrue(kpiExcelDataLocal.isEmpty());
	}

	// Test cases for populateCodeQualityMetricsExcelData and helper methods

	@Test
	public void testPopulateCodeQualityMetricsExcelData_ValidData() {

		List<KPIExcelData> kpiExcelDataList = new ArrayList<>();
		String dateLabel = "2023-01-01 to 2023-01-31";

		DataCountGroup dataCountGroup = createDataCountGroup();

		KPIExcelUtility.populateCodeQualityMetricsExcelData(List.of(dataCountGroup), kpiExcelDataList, dateLabel);

		assertEquals(1, kpiExcelDataList.size());
		KPIExcelData excelData = kpiExcelDataList.get(0);
		assertEquals("project1", excelData.getProject());
		assertEquals("repo1", excelData.getRepo());
		assertEquals("main", excelData.getBranch());
		assertEquals("developer1", excelData.getDeveloper());
		assertEquals(dateLabel, excelData.getDaysWeeks());
		assertEquals("15.50", excelData.getReworkRate());
		assertEquals(25.75, excelData.getRevertRate(), 0.01);
	}

	@Test
	public void testPopulateCodeQualityMetricsExcelData_EmptyGroupMap() {
		List<KPIExcelData> kpiExcelDataList = new ArrayList<>();
		String dateLabel = "2023-01-01 to 2023-01-31";

		KPIExcelUtility.populateCodeQualityMetricsExcelData(List.of(), kpiExcelDataList, dateLabel);

		assertEquals(0, kpiExcelDataList.size());
	}

	@Test
	public void testExtractFilters_ValidKey() {
		String key = "main -> repo1 -> project1#developer1";
		String[] result = KPIExcelUtility.extractFilters(key);

		assertEquals(2, result.length);
		assertEquals("main -> repo1 -> project1", result[0]);
		assertEquals("developer1", result[1]);
	}

	@Test
	public void testExtractFilters_KeyWithoutDelimiter() {
		String key = "main -> repo1 -> project1";
		String[] result = KPIExcelUtility.extractFilters(key);

		assertEquals(2, result.length);
		assertEquals("main -> repo1 -> project1", result[0]);
		assertEquals("Unknown", result[1]);
	}

	@Test
	public void testExtractFilters_EmptyKey() {
		String key = "";
		String[] result = KPIExcelUtility.extractFilters(key);

		assertEquals(2, result.length);
		assertEquals("", result[0]);
		assertEquals("Unknown", result[1]);
	}

	@Test
	public void testExtractProjectName_ValidFilter() {
		String filter = "main -> repo1 -> project1";
		String result = KPIExcelUtility.extractProjectName(filter);

		assertEquals("project1", result);
	}

	@Test
	public void testExtractProjectName_IncompleteFilter() {
		String filter = "main -> repo1";
		String result = KPIExcelUtility.extractProjectName(filter);

		assertEquals("", result);
	}

	@Test
	public void testExtractProjectName_EmptyFilter() {
		String filter = "";
		String result = KPIExcelUtility.extractProjectName(filter);

		assertEquals("", result);
	}

	@Test
	public void testExtractRepositoryName_ValidFilter() {
		String filter = "main -> repo1 -> project1";
		String result = KPIExcelUtility.extractRepositoryName(filter);

		assertEquals("repo1", result);
	}

	@Test
	public void testExtractRepositoryName_IncompleteFilter() {
		String filter = "main";
		String result = KPIExcelUtility.extractRepositoryName(filter);

		assertEquals("", result);
	}

	@Test
	public void testExtractBranchName_ValidFilter() {
		String filter = "main -> repo1 -> project1";
		String result = KPIExcelUtility.extractBranchName(filter);

		assertEquals("main", result);
	}

	@Test
	public void testExtractBranchName_EmptyFilter() {
		String filter = "";
		String result = KPIExcelUtility.extractBranchName(filter);

		assertEquals("", result);
	}

	@Test
	public void testExtractValueRate_ValidDataType() {
		DataCountGroup dataCountGroup = createDataCountGroup();
		double result = KPIExcelUtility.extractValueRate(dataCountGroup, "Rework Rate");

		assertEquals(15.5, result, 0.01);
	}

	@Test
	public void testExtractValueRate_InvalidDataType() {
		DataCountGroup dataCountGroup = createDataCountGroup();
		double result = KPIExcelUtility.extractValueRate(dataCountGroup, "Invalid Type");

		assertEquals(0.0, result, 0.01);
	}

	@Test
	public void testExtractValueRate_NullDataCountGroup() {
		double result = KPIExcelUtility.extractValueRate(null, "Rework Rate");

		assertEquals(0.0, result, 0.01);
	}

	@Test
	public void testExtractValueRate_EmptyValueList() {
		DataCountGroup dataCountGroup = new DataCountGroup();
		dataCountGroup.setValue(new ArrayList<>());
		double result = KPIExcelUtility.extractValueRate(dataCountGroup, "Rework Rate");

		assertEquals(0.0, result, 0.01);
	}

	@Test
	public void testExtractValueRate_NullValueList() {
		DataCountGroup dataCountGroup = new DataCountGroup();
		dataCountGroup.setValue(null);
		double result = KPIExcelUtility.extractValueRate(dataCountGroup, "Rework Rate");

		assertEquals(0.0, result, 0.01);
	}

	private DataCountGroup createDataCountGroup() {
		DataCountGroup dataCountGroup = new DataCountGroup();
		List<DataCount> dataCountList = new ArrayList<>();
		DataCount reworkRate = new DataCount();
		reworkRate.setData("Rework Rate");
		reworkRate.setValue(15.5);
		dataCountList.add(reworkRate);

		DataCount revertRate = new DataCount();
		revertRate.setData("Revert Rate");
		revertRate.setValue(25.75);
		dataCountList.add(revertRate);

        dataCountGroup.setFilter1("main -> repo1 -> project1");
        dataCountGroup.setFilter2("developer1");
		dataCountGroup.setValue(dataCountList);
		return dataCountGroup;
	}
}
