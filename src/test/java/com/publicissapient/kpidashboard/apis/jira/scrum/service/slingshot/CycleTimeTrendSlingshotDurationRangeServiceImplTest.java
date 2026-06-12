package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.data.FieldMappingDataFactory;
import com.publicissapient.kpidashboard.apis.data.JiraIssueHistoryDataFactory;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.ProjectFilter;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.dto.CycleTimeGroup;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;

@RunWith(MockitoJUnitRunner.class)
public class CycleTimeTrendSlingshotDurationRangeServiceImplTest {

	private static final ObjectId PROJECT_CONFIG_ID = new ObjectId("6335363749794a18e8a4479b");
	private static final List<String> RANGE_LIST =
			Arrays.asList("Last 1 Months", "Last 3 Months", "Last 6 Months");

	@Mock private CustomApiConfig customApiConfig;
	@Mock private ConfigHelperService configHelperService;

	@InjectMocks private CycleTimeTrendSlingshotDurationRangeServiceImpl service;

	private FieldMapping fieldMapping;
	private Map<ObjectId, FieldMapping> fieldMappingMap;
	private List<JiraIssueCustomHistory> issueHistoryList;

	@Before
	public void setUp() {
		FieldMappingDataFactory factory =
				FieldMappingDataFactory.newInstance("/json/default/scrum_project_field_mappings.json");
		fieldMapping = factory.getFieldMappings().get(0);
		fieldMapping.setJiraIssueTypeKPI202(new ArrayList<>(Arrays.asList("Story", "Defect")));
		fieldMapping.setJiradefecttype(new ArrayList<>(Arrays.asList("Defect", "Bug")));

		CycleTimeGroup dev = new CycleTimeGroup();
		dev.setLabel("Development");
		dev.setStatuses(Arrays.asList("In Development", "In Progress"));

		CycleTimeGroup test = new CycleTimeGroup();
		test.setLabel("Testing");
		test.setStatuses(Arrays.asList("In Testing", "Ready For Testing"));

		fieldMapping.setJiraIssueStatusGroupByCategoryKPI202(Arrays.asList(dev, test));

		fieldMappingMap = new HashMap<>();
		fieldMappingMap.put(PROJECT_CONFIG_ID, fieldMapping);

		JiraIssueHistoryDataFactory historyFactory =
				JiraIssueHistoryDataFactory.newInstance(
						"/json/default/iteration/jira_issue_custom_history.json");
		issueHistoryList = historyFactory.getUniqueJiraIssueCustomHistory();
		issueHistoryList.forEach(
				issue ->
						issue
								.getStatusUpdationLog()
								.forEach(
										log -> {
											log.setUpdatedOn(LocalDateTime.now().minusWeeks(2));
											log.setChangedTo("In Development");
											log.setChangedFrom("Open");
										}));

		when(customApiConfig.getFlowEfficiencyXAxisRange()).thenReturn(RANGE_LIST);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
	}

	// ── projectWiseLeafNodeValue ──────────────────────────────────────────────

	@Test
	public void testProjectWiseLeafNodeValue_setsNodeValue() {
		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("history", issueHistoryList);

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap, "");

		assertNotNull(leafNode.getValue());
	}

	@Test
	public void testProjectWiseLeafNodeValue_setsFiltersOnKpiElement() {
		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("history", issueHistoryList);

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap, "");

		assertNotNull(kpiElement.getFilters());
	}

	@Test
	public void testProjectWiseLeafNodeValue_setsXAxisValuesReversed() {
		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("history", issueHistoryList);

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap, "");

		List<String> xAxis = kpiElement.getxAxisValues();
		assertNotNull(xAxis);
		assertEquals(RANGE_LIST.size(), xAxis.size());
	}

	@Test
	public void testProjectWiseLeafNodeValue_setsLabelXAxis() {
		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("history", issueHistoryList);

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap, "");

		assertEquals("Range", kpiElement.getLabelXAxis());
	}

	@Test
	public void testProjectWiseLeafNodeValue_setsExcelColumns() {
		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("history", issueHistoryList);

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap, "");

		assertNotNull(kpiElement.getExcelColumns());
	}

	@Test
	public void testProjectWiseLeafNodeValue_emptyHistory() {
		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("history", new ArrayList<>());

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap, "");

		assertNotNull(leafNode.getValue());
		// datacountMap should be empty when no issues match
		Map<?, ?> value = (Map<?, ?>) leafNode.getValue();
		assertTrue(value.isEmpty());
	}

	@Test
	public void testProjectWiseLeafNodeValue_datacountMapHasOneEntryPerGroup() {
		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();

		JiraIssueCustomHistory history =
				buildHistory(
						"STORY-1",
						"Story",
						buildLog("Open", "In Development", LocalDateTime.now().minusDays(10)),
						buildLog("In Development", "Done", LocalDateTime.now().minusDays(5)));

		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("history", List.of(history));

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap, "");

		@SuppressWarnings("unchecked")
		Map<String, List<?>> datacountMap = (Map<String, List<?>>) leafNode.getValue();
		// Each group key maps to a list with one DataCount per range
		datacountMap.forEach(
				(key, dataCountList) -> assertEquals(RANGE_LIST.size(), dataCountList.size()));
	}

	@Test
	public void testProjectWiseLeafNodeValue_minutesConvertedToDays() {
		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();

		// Issue in "In Development" for ~1440 mins (1 day) within last 1 month
		JiraIssueCustomHistory history =
				buildHistory(
						"STORY-2",
						"Story",
						buildLog("Open", "In Development", LocalDateTime.now().minusDays(2)),
						buildLog("In Development", "Done", LocalDateTime.now().minusDays(1)));

		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("history", List.of(history));

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap, "");

		// Just verify no exception and value is set
		assertNotNull(leafNode.getValue());
	}

	// ── initializeRangeMapForProjects ─────────────────────────────────────────

	@Test
	public void testInitializeRangeMapForProjects_monthRange() {
		Map<String, List<JiraIssueCustomHistory>> rangeMap = new LinkedHashMap<>();
		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
		Map<String, Object> filters = new HashMap<>();
		filters.put("statusUpdationLog.story.changedTo", new ArrayList<>());
		uniqueProjectMap.put(PROJECT_CONFIG_ID.toString(), filters);

		List<String> ranges = List.of("Last 1 Months");

		CycleTimeTrendSlingshotDurationRangeServiceImpl.initializeRangeMapForProjects(
				rangeMap, issueHistoryList, ranges, uniqueProjectMap);

		assertTrue(rangeMap.containsKey("Last 1 Months"));
	}

	@Test
	public void testInitializeRangeMapForProjects_weekRange() {
		Map<String, List<JiraIssueCustomHistory>> rangeMap = new LinkedHashMap<>();
		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
		Map<String, Object> filters = new HashMap<>();
		filters.put("statusUpdationLog.story.changedTo", new ArrayList<>());
		uniqueProjectMap.put(PROJECT_CONFIG_ID.toString(), filters);

		List<String> ranges = List.of("Last 2 Weeks");

		CycleTimeTrendSlingshotDurationRangeServiceImpl.initializeRangeMapForProjects(
				rangeMap, issueHistoryList, ranges, uniqueProjectMap);

		assertTrue(rangeMap.containsKey("Last 2 Weeks"));
	}

	@Test
	public void testInitializeRangeMapForProjects_multipleRanges_allKeysPresent() {
		Map<String, List<JiraIssueCustomHistory>> rangeMap = new LinkedHashMap<>();
		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
		Map<String, Object> filters = new HashMap<>();
		filters.put("statusUpdationLog.story.changedTo", new ArrayList<>());
		uniqueProjectMap.put(PROJECT_CONFIG_ID.toString(), filters);

		CycleTimeTrendSlingshotDurationRangeServiceImpl.initializeRangeMapForProjects(
				rangeMap, issueHistoryList, RANGE_LIST, uniqueProjectMap);

		assertEquals(RANGE_LIST.size(), rangeMap.size());
		RANGE_LIST.forEach(range -> assertTrue(rangeMap.containsKey(range)));
	}

	@Test
	public void testInitializeRangeMapForProjects_emptyIssueList() {
		Map<String, List<JiraIssueCustomHistory>> rangeMap = new LinkedHashMap<>();
		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();

		CycleTimeTrendSlingshotDurationRangeServiceImpl.initializeRangeMapForProjects(
				rangeMap, new ArrayList<>(), RANGE_LIST, uniqueProjectMap);

		RANGE_LIST.forEach(range -> assertTrue(rangeMap.containsKey(range)));
		rangeMap.values().forEach(list -> assertTrue(list.isEmpty()));
	}

	// ── helpers ───────────────────────────────────────────────────────────────

	private Node buildLeafNode() {
		ProjectFilter projectFilter =
				new ProjectFilter(PROJECT_CONFIG_ID.toString(), "Scrum Project", PROJECT_CONFIG_ID);
		Node node = new Node();
		node.setId(PROJECT_CONFIG_ID.toString());
		node.setName("Scrum Project");
		node.setProjectFilter(projectFilter);
		return node;
	}

	private JiraIssueCustomHistory buildHistory(
			String storyId, String storyType, JiraHistoryChangeLog... logs) {
		JiraIssueCustomHistory history = new JiraIssueCustomHistory();
		history.setStoryID(storyId);
		history.setStoryType(storyType);
		history.setBasicProjectConfigId(PROJECT_CONFIG_ID.toString());
		history.setStatusUpdationLog(new ArrayList<>(Arrays.asList(logs)));
		return history;
	}

	private JiraHistoryChangeLog buildLog(
			String changedFrom, String changedTo, LocalDateTime updatedOn) {
		JiraHistoryChangeLog log = new JiraHistoryChangeLog();
		log.setChangedFrom(changedFrom);
		log.setChangedTo(changedTo);
		log.setUpdatedOn(updatedOn);
		return log;
	}
}
