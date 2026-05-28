package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.publicissapient.kpidashboard.apis.data.JiraIssueHistoryDataFactory;
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

import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.ProjectFilter;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.dto.CycleTimeGroup;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;

@RunWith(MockitoJUnitRunner.class)
public class CycleTimeTrendSlingshotSprintsServiceImplTest {

	private static final ObjectId PROJECT_CONFIG_ID = new ObjectId("6335363749794a18e8a4479b");

	@Mock private CustomApiConfig customApiConfig;
	@Mock private ConfigHelperService configHelperService;

	@InjectMocks private CycleTimeTrendSlingshotSprintsServiceImpl service;

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

		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
	}

	// ── projectWiseLeafNodeValue ──────────────────────────────────────────────

	@Test
	public void testProjectWiseLeafNodeValue_setsNodeValue() {
		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = buildResultMap(issueHistoryList, buildSprintList("Sprint 1"));

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap);

		assertNotNull(leafNode.getValue());
	}

	@Test
	public void testProjectWiseLeafNodeValue_setsFilters() {
		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = buildResultMap(issueHistoryList, buildSprintList("Sprint 1"));

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap);

		assertNotNull(kpiElement.getFilters());
	}

	@Test
	public void testProjectWiseLeafNodeValue_setsLabelXAxis() {
		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = buildResultMap(issueHistoryList, buildSprintList("Sprint 1"));

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap);

		assertEquals("Sprint", kpiElement.getLabelXAxis());
	}

	@Test
	public void testProjectWiseLeafNodeValue_setsExcelColumns() {
		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = buildResultMap(issueHistoryList, buildSprintList("Sprint 1"));

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap);

		assertNotNull(kpiElement.getExcelColumns());
	}

	@Test
	public void testProjectWiseLeafNodeValue_emptySprintList() {
		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = buildResultMap(issueHistoryList, new ArrayList<>());

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap);

		Map<?, ?> value = (Map<?, ?>) leafNode.getValue();
		assertTrue(value.isEmpty());
	}

	@Test
	public void testProjectWiseLeafNodeValue_emptyHistory() {
		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = buildResultMap(new ArrayList<>(), buildSprintList("Sprint 1"));

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap);

		// sprint map is built but no history matches → filterMap is empty
		Map<?, ?> value = (Map<?, ?>) leafNode.getValue();
		assertTrue(value.isEmpty());
	}

	@Test
	public void testProjectWiseLeafNodeValue_multipleSprintsProduceMultipleDataCounts() {
		String storyId1 = "STORY-10";
		String storyId2 = "STORY-11";

		JiraIssueCustomHistory h1 =
				buildHistory(
						storyId1,
						"Story",
						buildLog("Open", "In Development", LocalDateTime.now().minusDays(10)),
						buildLog("In Development", "Done", LocalDateTime.now().minusDays(7)));
		JiraIssueCustomHistory h2 =
				buildHistory(
						storyId2,
						"Story",
						buildLog("Open", "In Development", LocalDateTime.now().minusDays(4)),
						buildLog("In Development", "Done", LocalDateTime.now().minusDays(1)));

		List<SprintDetails> sprints =
				List.of(
						buildSprintWithIssue("Sprint 1", storyId1), buildSprintWithIssue("Sprint 2", storyId2));

		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = buildResultMap(List.of(h1, h2), sprints);

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap);

		@SuppressWarnings("unchecked")
		Map<String, List<DataCount>> datacountMap = (Map<String, List<DataCount>>) leafNode.getValue();
		assertNotNull(datacountMap);
		// Each group should have 2 DataCount entries (one per sprint)
		datacountMap.values().forEach(list -> assertEquals(2, list.size()));
	}


	@Test
	public void testProjectWiseLeafNodeValue_issueNotInHistoryIsSkipped() {
		// Sprint references an issue that has no matching history entry
		SprintDetails sprint = buildSprintWithIssue("Sprint X", "UNKNOWN-999");

		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = buildResultMap(issueHistoryList, List.of(sprint));

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap);

		// null history entries are silently added to the list but produce no cycle time
		assertNotNull(leafNode.getValue());
	}

	@Test
	public void testProjectWiseLeafNodeValue_noMatchingStatusLogs_producesEmptyDatacountMap() {
		String storyId = "STORY-3";
		// Logs with statuses that don't match any CycleTimeGroup
		JiraIssueCustomHistory history =
				buildHistory(
						storyId,
						"Story",
						buildLog("Open", "Backlog", LocalDateTime.now().minusDays(5)),
						buildLog("Backlog", "Closed", LocalDateTime.now().minusDays(1)));

		SprintDetails sprint = buildSprintWithIssue("Sprint C", storyId);

		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = buildResultMap(List.of(history), List.of(sprint));

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap);

		Map<?, ?> value = (Map<?, ?>) leafNode.getValue();
		assertTrue(value.isEmpty());
	}

	@Test
	public void testProjectWiseLeafNodeValue_multipleGroupsMatched() {
		String storyId = "STORY-4";
		JiraIssueCustomHistory history =
				buildHistory(
						storyId,
						"Story",
						buildLog("Open", "In Development", LocalDateTime.now().minusDays(10)),
						buildLog("In Development", "In Testing", LocalDateTime.now().minusDays(7)),
						buildLog("In Testing", "Done", LocalDateTime.now().minusDays(2)));

		SprintDetails sprint = buildSprintWithIssue("Sprint D", storyId);

		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = buildResultMap(List.of(history), List.of(sprint));

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap);

		@SuppressWarnings("unchecked")
		Map<String, List<DataCount>> datacountMap = (Map<String, List<DataCount>>) leafNode.getValue();
		// Expect keys for both Development and Testing groups (plus Overall)
		assertTrue(datacountMap.size() >= 2);
	}

	@Test
	public void testProjectWiseLeafNodeValue_sprintWithNoIssues_producesNoData() {
		SprintDetails emptySprintDetails = new SprintDetails();
		emptySprintDetails.setSprintName("Empty Sprint");
		emptySprintDetails.setTotalIssues(new HashSet<>());

		Node leafNode = buildLeafNode();
		KpiElement kpiElement = new KpiElement();
		Map<String, Object> resultMap = buildResultMap(issueHistoryList, List.of(emptySprintDetails));

		service.projectWiseLeafNodeValue(kpiElement, leafNode, resultMap);

		Map<?, ?> value = (Map<?, ?>) leafNode.getValue();
		assertTrue(value.isEmpty());
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

	private Map<String, Object> buildResultMap(
			List<JiraIssueCustomHistory> history, List<SprintDetails> sprints) {
		Map<String, Object> resultMap = new HashMap<>();
		resultMap.put("history", history);
		resultMap.put("sprints", sprints);
		return resultMap;
	}

	private List<SprintDetails> buildSprintList(String... sprintNames) {
		List<SprintDetails> sprints = new ArrayList<>();
		for (String name : sprintNames) {
			SprintDetails sd = new SprintDetails();
			sd.setSprintName(name);
			sd.setTotalIssues(new HashSet<>());
			sprints.add(sd);
		}
		return sprints;
	}

	private SprintDetails buildSprintWithIssue(String sprintName, String issueNumber) {
		SprintIssue sprintIssue = new SprintIssue();
		sprintIssue.setNumber(issueNumber);

		Set<SprintIssue> issues = new HashSet<>();
		issues.add(sprintIssue);

		SprintDetails sprint = new SprintDetails();
		sprint.setSprintName(sprintName);
		sprint.setTotalIssues(issues);
		return sprint;
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
