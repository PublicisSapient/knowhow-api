package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.FieldMappingDataFactory;
import com.publicissapient.kpidashboard.apis.data.JiraIssueDataFactory;
import com.publicissapient.kpidashboard.apis.data.KpiRequestFactory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;

@RunWith(MockitoJUnitRunner.class)
public class SprintVelocitySlingshotServiceImplTest {

	@Mock private CustomApiConfig customApiConfig;

	@Mock private ConfigHelperService configHelperService;

	@Mock private JiraIssueRepository jiraIssueRepository;

	@Mock private CacheService cacheService;

	@InjectMocks private SprintVelocitySlingshotServiceImpl sprintVelocityService;

	private KpiRequest kpiRequest;
	private TreeAggregatorDetail treeAggregatorDetail;
	private List<Node> leafNodeList = new ArrayList<>();
	private Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();
	private Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
	private List<JiraIssue> jiraIssueList = new ArrayList<>();

	@Before
	public void setUp() throws ApplicationException {
		KpiRequestFactory kpiRequestFactory = KpiRequestFactory.newInstance();
		kpiRequest = kpiRequestFactory.findKpiRequest("kpi205");
		kpiRequest.setLabel("PROJECT");

		AccountHierarchyFilterDataFactory accountHierarchyFilterDataFactory =
				AccountHierarchyFilterDataFactory.newInstance();
		List<AccountHierarchyData> accountHierarchyDataList =
				accountHierarchyFilterDataFactory.getAccountHierarchyDataList();

		FieldMappingDataFactory fieldMappingDataFactory =
				FieldMappingDataFactory.newInstance("/json/default/scrum_project_field_mappings.json");
		FieldMapping fieldMapping = fieldMappingDataFactory.getFieldMappings().get(0);
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479b");
		fieldMappingMap.put(projectId, fieldMapping);

		// Create project basic config
		ProjectBasicConfig projectBasicConfig = new ProjectBasicConfig();
		projectBasicConfig.setId(projectId);
		projectBasicConfig.setProjectName("Test Project");
		projectBasicConfig.setProjectNodeId("Test Project_" + projectId.toString());
		projectConfigMap.put(projectId.toString(), projectBasicConfig);

		leafNodeList = new ArrayList<>();
		treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 4);
		treeAggregatorDetail
				.getMapOfListOfProjectNodes()
				.forEach(
						(k, v) -> {
							leafNodeList.addAll(v);
						});

		JiraIssueDataFactory jiraIssueDataFactory = JiraIssueDataFactory.newInstance();
		jiraIssueList = jiraIssueDataFactory.getJiraIssues();

		// Mock sprint velocity limit
		when(customApiConfig.getSprintVelocityLimit()).thenReturn(5);

		// Mock field mapping
		when(configHelperService.getFieldMapping(any(ObjectId.class))).thenReturn(fieldMapping);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);

		// Mock cache service
		String kpiRequestTrackerId = "Jira-Excel-QADD-track001";
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
				.thenReturn(kpiRequestTrackerId);
		when(cacheService.cacheProjectConfigMapData()).thenReturn(projectConfigMap);
	}

	@Test
	public void testGetQualifierType() {
		assertThat(
				sprintVelocityService.getQualifierType(),
				equalTo(KPICode.SPRINT_VELOCITY_SLINGSHOT.name()));
	}

	@Test
	public void testGetKpiData() throws ApplicationException {
		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("kpi205");

		when(jiraIssueRepository.findByBasicProjectConfigId(anyString()))
				.thenReturn(createMockJiraIssues());

		KpiElement result =
				sprintVelocityService.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);

		assertNotNull(result);
		assertNotNull(result.getTrendValueList());
	}

	@Test
	public void testFetchKPIDataFromDb() {
		List<JiraIssue> mockJiraIssues = createMockJiraIssues();
		when(jiraIssueRepository.findByBasicProjectConfigId(anyString())).thenReturn(mockJiraIssues);

		Map<String, Object> result =
				sprintVelocityService.fetchKPIDataFromDb(
						leafNodeList, "2023-01-01", "2023-12-31", kpiRequest);

		assertNotNull(result);
		assertNotNull(result.get("JIRAISSUES"));
	}

	@Test
	public void testCalculateKPIMetrics() {
		Map<String, Object> testData = new HashMap<>();
		testData.put("JIRAISSUES", createMockJiraIssues());

		Double result = sprintVelocityService.calculateKPIMetrics(testData);

		assertNotNull(result);
		assertEquals(Double.valueOf(2.0), result);
	}

	@Test
	public void testCalculateKPIMetricsWithEmptyList() {
		Map<String, Object> testData = new HashMap<>();
		testData.put("JIRAISSUES", new ArrayList<>());

		Double result = sprintVelocityService.calculateKPIMetrics(testData);

		assertEquals(Double.valueOf(0.0), result);
	}

	@Test
	public void testCalculateKpiValue() {
		List<Double> valueList = new ArrayList<>();
		valueList.add(10.0);
		valueList.add(20.0);
		valueList.add(30.0);

		Double result = sprintVelocityService.calculateKpiValue(valueList, "testKpi");

		assertNotNull(result);
	}

	@Test
	public void testCalculateThresholdValue() {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setThresholdValueKPI205("15.0");

		Double result = sprintVelocityService.calculateThresholdValue(fieldMapping);

		assertNotNull(result);
	}

	@Test
	public void testFetchKPIDataFromDbWithNullFieldMapping() {
		// Create a field mapping with empty values for null scenario
		FieldMapping emptyFieldMapping = new FieldMapping();
		emptyFieldMapping.setJiraTicketClosedStatus(new ArrayList<>());
		emptyFieldMapping.setJiraIterationCompletionStatusKPI205(new ArrayList<>());

		when(configHelperService.getFieldMapping(any(ObjectId.class))).thenReturn(emptyFieldMapping);
		when(jiraIssueRepository.findByBasicProjectConfigId(anyString())).thenReturn(new ArrayList<>());

		Map<String, Object> result =
				sprintVelocityService.fetchKPIDataFromDb(
						leafNodeList, "2023-01-01", "2023-12-31", kpiRequest);

		assertNotNull(result);
	}

	@Test
	public void testFetchKPIDataFromDbWithEmptyStatuses() {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setJiraTicketClosedStatus(new ArrayList<>());
		fieldMapping.setJiraIterationCompletionStatusKPI205(new ArrayList<>());

		when(configHelperService.getFieldMapping(any(ObjectId.class))).thenReturn(fieldMapping);
		when(jiraIssueRepository.findByBasicProjectConfigId(anyString()))
				.thenReturn(createMockJiraIssues());

		Map<String, Object> result =
				sprintVelocityService.fetchKPIDataFromDb(
						leafNodeList, "2023-01-01", "2023-12-31", kpiRequest);

		assertNotNull(result);
	}

	@Test
	public void testSprintWiseLeafNodeValueWithStoryPointCriteria() throws ApplicationException {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setEstimationCriteria(CommonConstant.STORY_POINT);
		fieldMapping.setJiraTicketClosedStatus(Arrays.asList("Done", "Closed"));
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479b");
		fieldMappingMap.put(projectId, fieldMapping);

		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByBasicProjectConfigId(anyString()))
				.thenReturn(createMockJiraIssuesWithStoryPoints());

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("kpi205");

		KpiElement result =
				sprintVelocityService.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);

		assertNotNull(result);
		assertNotNull(result.getTrendValueList());
	}

	@Test
	public void testSprintWiseLeafNodeValueWithTimeCriteria() throws ApplicationException {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setEstimationCriteria("TIME");
		fieldMapping.setStoryPointToHourMapping(8.0);
		fieldMapping.setJiraTicketClosedStatus(Arrays.asList("Done", "Closed"));
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479b");
		fieldMappingMap.put(projectId, fieldMapping);

		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByBasicProjectConfigId(anyString()))
				.thenReturn(createMockJiraIssuesWithTimeEstimates());

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("kpi205");

		KpiElement result =
				sprintVelocityService.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);

		assertNotNull(result);
		assertNotNull(result.getTrendValueList());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testGetKpiDataContainsStoryPointSubfilterValues() throws ApplicationException {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setJiraTicketClosedStatus(Arrays.asList("Done", "Closed"));
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479b");
		fieldMappingMap.put(projectId, fieldMapping);

		when(configHelperService.getFieldMapping(any(ObjectId.class))).thenReturn(fieldMapping);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByBasicProjectConfigId(anyString()))
				.thenReturn(createMockJiraIssuesWithStoryPoints());

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("kpi205");

		KpiElement result =
				sprintVelocityService.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);

		assertNotNull(result);
		assertNotNull(result.getTrendValueList());

		List<DataCount> trendValues;
		Object trendValueList = result.getTrendValueList();
		if (trendValueList instanceof List<?> list
				&& !list.isEmpty()
				&& list.get(0) instanceof DataCountGroup) {
			trendValues = ((DataCountGroup) list.get(0)).getValue();
		} else {
			trendValues = (List<DataCount>) trendValueList;
		}

		assertNotNull(trendValues);
		trendValues.stream()
				.filter(dc -> dc.getSubfilterValues() != null)
				.forEach(
						dc -> {
							assertTrue(dc.getSubfilterValues().containsKey("storyPoints"));
							assertTrue(dc.getSubfilterValues().containsKey("storyPointsLineValue"));
							assertTrue(dc.getSubfilterValues().containsKey("storyPointsAggregationValue"));
						});
	}

	@Test
	public void testGetKpiDataWithPastReferenceDate() throws ApplicationException {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setJiraTicketClosedStatus(Arrays.asList("Done", "Closed"));
		fieldMapping.setWeeklyDataStartDateKPI205(
				java.time.LocalDate.now()
						.minusDays(10)
						.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")));
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479b");
		fieldMappingMap.put(projectId, fieldMapping);

		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByBasicProjectConfigId(anyString()))
				.thenReturn(createMockJiraIssues());

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("kpi205");

		KpiElement result =
				sprintVelocityService.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);

		assertNotNull(result);
		assertNotNull(result.getTrendValueList());
	}

	@Test
	public void testGetKpiDataWithFutureReferenceDate() throws ApplicationException {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setJiraTicketClosedStatus(Arrays.asList("Done", "Closed"));
		fieldMapping.setWeeklyDataStartDateKPI205(
				java.time.LocalDate.now()
						.plusDays(5)
						.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")));
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479b");
		fieldMappingMap.put(projectId, fieldMapping);

		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByBasicProjectConfigId(anyString()))
				.thenReturn(createMockJiraIssues());

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("kpi205");

		KpiElement result =
				sprintVelocityService.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);

		assertNotNull(result);
		assertNotNull(result.getTrendValueList());
	}

	@Test
	public void testGetKpiDataWithInvalidReferenceDate() throws ApplicationException {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setJiraTicketClosedStatus(Arrays.asList("Done", "Closed"));
		fieldMapping.setWeeklyDataStartDateKPI205("not-a-date");
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479b");
		fieldMappingMap.put(projectId, fieldMapping);

		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByBasicProjectConfigId(anyString()))
				.thenReturn(createMockJiraIssues());

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("kpi205");

		KpiElement result =
				sprintVelocityService.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);

		assertNotNull(result);
		assertNotNull(result.getTrendValueList());
	}

	private List<JiraIssue> createMockJiraIssues() {
		List<JiraIssue> issues = new ArrayList<>();

		JiraIssue issue1 = new JiraIssue();
		issue1.setNumber("ISSUE-1");
		issue1.setEstimate("5.0");
		issue1.setStatus("Done");
		issue1.setChangeDate("2023-11-01T10:00:00");
		issues.add(issue1);

		JiraIssue issue2 = new JiraIssue();
		issue2.setNumber("ISSUE-2");
		issue2.setEstimate("10.0");
		issue2.setStatus("Closed");
		issue2.setChangeDate("2023-11-02T10:00:00");
		issues.add(issue2);

		return issues;
	}

	private List<JiraIssue> createMockJiraIssuesWithStoryPoints() {
		List<JiraIssue> issues = new ArrayList<>();

		JiraIssue issue1 = new JiraIssue();
		issue1.setNumber("ISSUE-1");
		issue1.setStoryPoints(5.0);
		issue1.setStatus("Done");
		issue1.setChangeDate("2023-11-01T10:00:00");
		issues.add(issue1);

		JiraIssue issue2 = new JiraIssue();
		issue2.setNumber("ISSUE-2");
		issue2.setStoryPoints(8.0);
		issue2.setStatus("Closed");
		issue2.setChangeDate("2023-11-02T10:00:00");
		issues.add(issue2);

		return issues;
	}

	private List<JiraIssue> createMockJiraIssuesWithTimeEstimates() {
		List<JiraIssue> issues = new ArrayList<>();

		JiraIssue issue1 = new JiraIssue();
		issue1.setNumber("ISSUE-1");
		issue1.setAggregateTimeOriginalEstimateMinutes(480); // 8 hours
		issue1.setStatus("Done");
		issue1.setChangeDate("2023-11-01T10:00:00");
		issues.add(issue1);

		JiraIssue issue2 = new JiraIssue();
		issue2.setNumber("ISSUE-2");
		issue2.setAggregateTimeOriginalEstimateMinutes(240); // 4 hours
		issue2.setStatus("Closed");
		issue2.setChangeDate("2023-11-02T10:00:00");
		issues.add(issue2);

		return issues;
	}

	@Test
	public void testGetKpiDataWithMultiGranularityAllFiltersPresent() throws ApplicationException {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setJiraTicketClosedStatus(Arrays.asList("Done", "Closed"));
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479b");
		fieldMappingMap.put(projectId, fieldMapping);

		when(customApiConfig.isSlingshotSprintVelocityMultiGranularity()).thenReturn(true);
		when(configHelperService.getFieldMapping(any(ObjectId.class))).thenReturn(fieldMapping);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByBasicProjectConfigId(anyString()))
				.thenReturn(createRecentMockJiraIssues());

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("kpi205");

		KpiElement result =
				sprintVelocityService.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);

		assertNotNull(result);
		assertNotNull(result.getTrendValueList());
		assertTrue(result.getTrendValueList() instanceof List<?>);
		List<?> groups = (List<?>) result.getTrendValueList();
		assertEquals(3, groups.size());
		List<String> filterNames =
				groups.stream()
						.map(g -> ((DataCountGroup) g).getFilter())
						.collect(java.util.stream.Collectors.toList());
		assertTrue(filterNames.contains("Weekly"));
		assertTrue(filterNames.contains("Bi-Weekly"));
		assertTrue(filterNames.contains("Monthly"));
	}

	@Test
	public void testGetKpiDataMultiGranularityWeeklyLimitedTo12() throws ApplicationException {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setJiraTicketClosedStatus(Arrays.asList("Done", "Closed"));
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479b");
		fieldMappingMap.put(projectId, fieldMapping);

		when(customApiConfig.isSlingshotSprintVelocityMultiGranularity()).thenReturn(true);
		when(configHelperService.getFieldMapping(any(ObjectId.class))).thenReturn(fieldMapping);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByBasicProjectConfigId(anyString()))
				.thenReturn(createRecentMockJiraIssues());

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("kpi205");

		KpiElement result =
				sprintVelocityService.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);

		List<?> groups = (List<?>) result.getTrendValueList();
		DataCountGroup weeklyGroup =
				groups.stream()
						.filter(g -> "Weekly".equals(((DataCountGroup) g).getFilter()))
						.map(g -> (DataCountGroup) g)
						.findFirst()
						.orElseThrow();
		List<?> weeklyValues = weeklyGroup.getValue();
		assertNotNull(weeklyValues);
		if (!weeklyValues.isEmpty() && weeklyValues.get(0) instanceof DataCount) {
			Object innerVal = ((DataCount) weeklyValues.get(0)).getValue();
			if (innerVal instanceof List<?> inner) {
				assertTrue("Weekly filter should show at most 12 periods", inner.size() <= 12);
			}
		}
	}

	@Test
	public void testGetKpiDataMultiGranularityBiWeeklyMaxPeriods() throws ApplicationException {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setJiraTicketClosedStatus(Arrays.asList("Done", "Closed"));
		fieldMapping.setWeeklyDataStartDateKPI205(
				LocalDate.now().minusDays(30).format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479b");
		fieldMappingMap.put(projectId, fieldMapping);

		when(customApiConfig.isSlingshotSprintVelocityMultiGranularity()).thenReturn(true);
		when(configHelperService.getFieldMapping(any(ObjectId.class))).thenReturn(fieldMapping);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByBasicProjectConfigId(anyString()))
				.thenReturn(createRecentMockJiraIssues());

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("kpi205");

		KpiElement result =
				sprintVelocityService.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);

		assertNotNull(result);
		List<?> groups = (List<?>) result.getTrendValueList();
		DataCountGroup biWeeklyGroup =
				groups.stream()
						.filter(g -> "Bi-Weekly".equals(((DataCountGroup) g).getFilter()))
						.map(g -> (DataCountGroup) g)
						.findFirst()
						.orElseThrow();
		assertNotNull(biWeeklyGroup.getValue());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testReferenceDateAlignmentNoFutureDatesAndOldestToNewestOrder()
			throws ApplicationException {
		LocalDate refDate = LocalDate.now().minusDays(3);
		String refDateStr = refDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setJiraTicketClosedStatus(Arrays.asList("Done", "Closed"));
		fieldMapping.setWeeklyDataStartDateKPI205(refDateStr);
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479b");
		fieldMappingMap.put(projectId, fieldMapping);

		when(customApiConfig.isSlingshotSprintVelocityMultiGranularity()).thenReturn(true);
		when(configHelperService.getFieldMapping(any(ObjectId.class))).thenReturn(fieldMapping);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByBasicProjectConfigId(anyString()))
				.thenReturn(createRecentMockJiraIssues());

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("kpi205");

		KpiElement result =
				sprintVelocityService.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);

		List<?> groups = (List<?>) result.getTrendValueList();
		DateTimeFormatter labelFmt =
				DateTimeFormatter.ofPattern("dd-MMM-yyyy", java.util.Locale.ENGLISH);
		String expectedStart = refDate.format(labelFmt);
		LocalDate today = LocalDate.now();

		for (Object g : groups) {
			DataCountGroup group = (DataCountGroup) g;
			List<DataCount> projectWrappers = (List<DataCount>) group.getValue();
			if (projectWrappers.isEmpty()) continue;
			List<DataCount> periods = (List<DataCount>) projectWrappers.get(0).getValue();
			if (periods == null || periods.isEmpty()) continue;

			DataCount mostRecent = periods.get(periods.size() - 1);
			String sprintId = mostRecent.getsSprintID();
			assertNotNull("SprintId should not be null for " + group.getFilter(), sprintId);

			if ("Weekly".equals(group.getFilter())) {
				assertTrue(
						"Weekly most-recent period should start on reference date. Got: " + sprintId,
						sprintId.startsWith(expectedStart));
			}

			if (!"Monthly".equals(group.getFilter())) {
				String endPart =
						sprintId.contains(" to ") ? sprintId.split(" to ")[1].trim() : sprintId.trim();
				try {
					LocalDate endDate = LocalDate.parse(endPart, labelFmt);
					assertFalse(
							"Period end date should not be in the future for "
									+ group.getFilter()
									+ ": "
									+ sprintId,
							endDate.isAfter(today));
				} catch (Exception ignored) {
				}
			}

			for (int i = 0; i < periods.size() - 1; i++) {
				String id1 = periods.get(i).getsSprintID();
				String id2 = periods.get(i + 1).getsSprintID();
				if (id1 == null || id2 == null) continue;
				try {
					String s1 = id1.contains(" to ") ? id1.split(" to ")[0].trim() : id1.trim();
					String s2 = id2.contains(" to ") ? id2.split(" to ")[0].trim() : id2.trim();
					LocalDate d1 = LocalDate.parse(s1, labelFmt);
					LocalDate d2 = LocalDate.parse(s2, labelFmt);
					assertTrue(
							"Periods should be in oldest-to-newest order for "
									+ group.getFilter()
									+ ": "
									+ id1
									+ " then "
									+ id2,
							!d1.isAfter(d2));
				} catch (Exception ignored) {
				}
			}
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testBiWeeklyStartsOnReferenceBoundary() throws ApplicationException {
		LocalDate refDate = LocalDate.now().minusDays(10);
		String refDateStr = refDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setJiraTicketClosedStatus(Arrays.asList("Done", "Closed"));
		fieldMapping.setWeeklyDataStartDateKPI205(refDateStr);
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479b");
		fieldMappingMap.put(projectId, fieldMapping);

		when(customApiConfig.isSlingshotSprintVelocityMultiGranularity()).thenReturn(true);
		when(configHelperService.getFieldMapping(any(ObjectId.class))).thenReturn(fieldMapping);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByBasicProjectConfigId(anyString()))
				.thenReturn(createRecentMockJiraIssues());

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("kpi205");

		KpiElement result =
				sprintVelocityService.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);

		List<?> groups = (List<?>) result.getTrendValueList();
		DataCountGroup biWeeklyGroup =
				groups.stream()
						.filter(g -> "Bi-Weekly".equals(((DataCountGroup) g).getFilter()))
						.map(g -> (DataCountGroup) g)
						.findFirst()
						.orElseThrow();

		List<DataCount> projectWrappers = (List<DataCount>) biWeeklyGroup.getValue();
		assertNotNull(projectWrappers);
		if (!projectWrappers.isEmpty()) {
			List<DataCount> biWeeklyPeriods = (List<DataCount>) projectWrappers.get(0).getValue();
			assertNotNull(biWeeklyPeriods);
			DateTimeFormatter labelFmt =
					DateTimeFormatter.ofPattern("dd-MMM-yyyy", java.util.Locale.ENGLISH);
			for (DataCount period : biWeeklyPeriods) {
				String sprintId = period.getsSprintID();
				assertNotNull(sprintId);
				String startPart =
						sprintId.contains(" to ") ? sprintId.split(" to ")[0].trim() : sprintId.trim();
				LocalDate startDate = LocalDate.parse(startPart, labelFmt);
				long diff = java.time.temporal.ChronoUnit.DAYS.between(refDate, startDate);
				assertEquals(
						"Bi-weekly period start date should be a multiple of 14 days from reference date, but got diff="
								+ diff
								+ " for period "
								+ sprintId,
						0L,
						diff % 14);
			}
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testMonthlyStartsOnReferenceDate() throws ApplicationException {
		LocalDate refDate = LocalDate.now().minusDays(10);
		String refDateStr = refDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setJiraTicketClosedStatus(Arrays.asList("Done", "Closed"));
		fieldMapping.setWeeklyDataStartDateKPI205(refDateStr);
		ObjectId projectId = new ObjectId("6335363749794a18e8a4479b");
		fieldMappingMap.put(projectId, fieldMapping);

		when(customApiConfig.isSlingshotSprintVelocityMultiGranularity()).thenReturn(true);
		when(configHelperService.getFieldMapping(any(ObjectId.class))).thenReturn(fieldMapping);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByBasicProjectConfigId(anyString()))
				.thenReturn(createRecentMockJiraIssues());

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("kpi205");

		KpiElement result =
				sprintVelocityService.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);

		List<?> groups = (List<?>) result.getTrendValueList();
		DataCountGroup monthlyGroup =
				groups.stream()
						.filter(g -> "Monthly".equals(((DataCountGroup) g).getFilter()))
						.map(g -> (DataCountGroup) g)
						.findFirst()
						.orElseThrow();

		List<DataCount> projectWrappers = (List<DataCount>) monthlyGroup.getValue();
		assertNotNull(projectWrappers);
		if (!projectWrappers.isEmpty()) {
			List<DataCount> monthlyPeriods = (List<DataCount>) projectWrappers.get(0).getValue();
			assertNotNull(monthlyPeriods);
			assertTrue("Should have at least one monthly period", !monthlyPeriods.isEmpty());
			DataCount refPeriod = null;
			DateTimeFormatter dayFmt =
					DateTimeFormatter.ofPattern("dd-MMM-yyyy", java.util.Locale.ENGLISH);
			String expectedRefLabel =
					refDate.format(dayFmt) + " - " + refDate.plusDays(27).format(dayFmt);
			for (DataCount period : monthlyPeriods) {
				if (expectedRefLabel.equals(period.getsSprintID())) {
					refPeriod = period;
					break;
				}
			}
			assertNotNull(
					"There should be a monthly period labeled '"
							+ expectedRefLabel
							+ "' (the 28-day bucket starting on the reference date)",
					refPeriod);
		}
	}

	private List<JiraIssue> createRecentMockJiraIssues() {
		List<JiraIssue> issues = new ArrayList<>();
		LocalDate base = LocalDate.now().minusWeeks(2);
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
		String[] statuses = {"Done", "Closed"};
		for (int i = 0; i < 10; i++) {
			JiraIssue issue = new JiraIssue();
			issue.setNumber("RECENT-" + i);
			issue.setStatus(statuses[i % 2]);
			issue.setStoryPoints(3.0);
			issue.setChangeDate(base.minusWeeks(i).atStartOfDay().format(dtf));
			issue.setCreatedDate(base.minusWeeks(i + 1).atStartOfDay().format(dtf));
			issues.add(issue);
		}
		return issues;
	}
}
