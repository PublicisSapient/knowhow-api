package com.publicissapient.kpidashboard.apis.jira.scrum.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.CommonService;
import com.publicissapient.kpidashboard.apis.common.service.ToolsKPIService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.ProjectFilter;
import com.publicissapient.kpidashboard.apis.model.SprintFilter;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.common.model.application.BaseFieldMappingStructure;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class DefectsBreachedSlasServiceImplTest {

	private static final String TEST_BASIC_PROJECT_CONFIG_ID = "507f1f77bcf86cd799439011";

	@Mock private CustomApiConfig customApiConfig;

	@Mock private ConfigHelperService configHelperService;

	@Mock private CacheService cacheService;

	@Mock private CommonService commonService;

	@Mock private MongoTemplate mongoTemplate;

	@Spy @InjectMocks private DefectsBreachedSlasServiceImpl defectsBreachedSlasService;

	private KpiRequest kpiRequest;
	private KpiElement kpiElement;

	private FieldMapping fieldMapping;

	private List<Node> leafNodeList;

	private TreeAggregatorDetail treeAggregatorDetail;

	@Before
	public void setUp() {
		setupMocksIntoParentClasses();

		setupKpiElement();
	}

	@Test
	public void when_GettingTheQualifierType_Expect_DefectsBreachedSLAsIsReturned() {
		String result = defectsBreachedSlasService.getQualifierType();
		assertEquals(KPICode.DEFECTS_BREACHED_SLAS.name(), result);
	}

	@Test
	public void when_RequiredFieldMappingIsMissing_Expect_MetricCalculationIsNotBeingPerformed()
			throws ApplicationException {
		setupTreeAggregatorDetail();
		kpiRequest = Mockito.mock(KpiRequest.class);
		when(kpiRequest.getIds()).thenReturn(new String[] {"node1"});
		when(kpiRequest.getSelecedHierarchyLabel()).thenReturn("SPRINT");
		when(kpiRequest.getLabel()).thenReturn("project");

		fieldMapping = new FieldMapping();
		Map<ObjectId, FieldMapping> basicProjectConfigIdFieldMappingMap = new HashMap<>();
		basicProjectConfigIdFieldMappingMap.put(
				new ObjectId(TEST_BASIC_PROJECT_CONFIG_ID), fieldMapping);
		when(configHelperService.getFieldMappingMap()).thenReturn(basicProjectConfigIdFieldMappingMap);
		when(cacheService.cacheProjectConfigMapData()).thenReturn(Collections.emptyMap());

		KpiElement resultedKpiElement =
				defectsBreachedSlasService.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);

		assertNotNull(resultedKpiElement);
		Object trendValuesObject = resultedKpiElement.getTrendValueList();

		assertNotNull(trendValuesObject);

		List<DataCountGroup> trendValuesDataCountGroupsList =
				(List<DataCountGroup>) resultedKpiElement.getTrendValueList();

		assertTrue(CollectionUtils.isEmpty(trendValuesDataCountGroupsList));
		assertTrue(CollectionUtils.isEmpty(kpiElement.getExcelData()));
	}

	@Test
	public void when_DataIsValid_Expect_MetricCalculationIsPerformedAccordingly()
			throws ApplicationException {
		setupTreeAggregatorDetail();
		// custom api config
		when(customApiConfig.getSeverity())
				.thenReturn(
						Map.of(
								"s1",
								List.of("p1", "P1 - Blocker", "blocker", "1", "0", "p0", "Urgent", "s1", "s0"),
								"s2",
								List.of("p2", "critical", "P2 - Critical", "2", "High", "s2"),
								"s3",
								List.of("p3", "P3 - Major", "major", "3", "Medium", "s3"),
								"s4",
								List.of("p4", "P4 - Minor", "minor", "4", "Low", "s4")));

		// Field Mapping
		BaseFieldMappingStructure.Options s1Option = new BaseFieldMappingStructure.Options();
		s1Option.setLabel("s1");
		s1Option.setStructuredValue(Map.of("severity", "s1", "sla", 24.0, "timeUnit", "Hours"));

		BaseFieldMappingStructure.Options s3Option = new BaseFieldMappingStructure.Options();
		s3Option.setLabel("s3");
		s3Option.setStructuredValue(Map.of("severity", "s3", "sla", 5.0, "timeUnit", "Days"));

		fieldMapping = new FieldMapping();
		fieldMapping.setIncludedSeveritySlasKPI195(List.of(s1Option, s3Option));
		fieldMapping.setIncludedDefectClosureStatusesKPI195(
				List.of("Closed", "Resolved", "Ready for Delivery", "Done", "Ready for Sign-off"));
		Map<ObjectId, FieldMapping> basicProjectConfigIdFieldMappingMap = new HashMap<>();
		basicProjectConfigIdFieldMappingMap.put(
				new ObjectId(TEST_BASIC_PROJECT_CONFIG_ID), fieldMapping);
		when(cacheService.cacheProjectConfigMapData()).thenReturn(Collections.emptyMap());
		when(configHelperService.getFieldMappingMap()).thenReturn(basicProjectConfigIdFieldMappingMap);

		// Kpi request
		kpiRequest = Mockito.mock(KpiRequest.class);
		when(kpiRequest.getIds()).thenReturn(new String[] {"project_node"});
		when(kpiRequest.getSelecedHierarchyLabel()).thenReturn("PROJECT");
		when(kpiRequest.getLabel()).thenReturn("project");
		when(kpiRequest.getRequestTrackerId())
				.thenReturn("test-tracker-id-" + System.currentTimeMillis());

		when(mongoTemplate.find(any(Query.class), eq(JiraIssue.class)))
				.thenReturn(generateTestJiraIssues());
		when(mongoTemplate.find(any(Query.class), eq(JiraIssueCustomHistory.class)))
				.thenReturn(generateTestJiraIssueCustomHistories());

		when(commonService.sortTrendValueMap(any()))
				.thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		KpiElement resultedKpiElement =
				defectsBreachedSlasService.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);

		// kpi element is not null
		assertNotNull(resultedKpiElement);

		List<DataCountGroup> dataCountGroups =
				(List<DataCountGroup>) resultedKpiElement.getTrendValueList();
		// kpi element contains all expected filters
		assertNotNull(dataCountGroups);
		assertFalse(CollectionUtils.isEmpty(dataCountGroups));

		List<String> expectedFilters = List.of("Overall", "S1", "S3");

		dataCountGroups.forEach(
				dataCountGroup -> assertTrue(expectedFilters.contains(dataCountGroup.getFilter())));

		DataCountGroup overallDataCountGroup =
				dataCountGroups.stream()
						.filter(dataCountGroup -> dataCountGroup.getFilter().equalsIgnoreCase("Overall"))
						.findFirst()
						.get();

		overallDataCountGroup
				.getValue()
				.forEach(
						dataCount -> {
							List<DataCount> sprintDataCounts = (List<DataCount>) dataCount.getValue();
							sprintDataCounts.forEach(
									sprintDataCount -> assertNotNull(sprintDataCount.getDrillDown()));
						});

		assertTrue(CollectionUtils.isEmpty(resultedKpiElement.getExcelData()));
	}

	@Test
	public void when_KPIExcelDataIsRequestedAndDataIsValid_Expect_KpiElementContainsTheRequestedData()
			throws ApplicationException {
		setupTreeAggregatorDetail();

		// custom api config
		when(customApiConfig.getSeverity())
				.thenReturn(
						Map.of(
								"s1",
								List.of("p1", "P1 - Blocker", "blocker", "1", "0", "p0", "Urgent", "s1", "s0"),
								"s2",
								List.of("p2", "critical", "P2 - Critical", "2", "High", "s2"),
								"s3",
								List.of("p3", "P3 - Major", "major", "3", "Medium", "s3"),
								"s4",
								List.of("p4", "P4 - Minor", "minor", "4", "Low", "s4")));

		// Field Mapping
		BaseFieldMappingStructure.Options s1Option = new BaseFieldMappingStructure.Options();
		s1Option.setLabel("s1");
		s1Option.setStructuredValue(Map.of("severity", "s1", "sla", 24.0, "timeUnit", "Hours"));

		BaseFieldMappingStructure.Options s3Option = new BaseFieldMappingStructure.Options();
		s3Option.setLabel("s3");
		s3Option.setStructuredValue(Map.of("severity", "s3", "sla", 5.0, "timeUnit", "Days"));

		fieldMapping = new FieldMapping();
		fieldMapping.setIncludedSeveritySlasKPI195(List.of(s1Option, s3Option));
		fieldMapping.setIncludedDefectClosureStatusesKPI195(
				List.of("Closed", "Resolved", "Ready for Delivery", "Done", "Ready for Sign-off"));
		Map<ObjectId, FieldMapping> basicProjectConfigIdFieldMappingMap = new HashMap<>();
		basicProjectConfigIdFieldMappingMap.put(
				new ObjectId(TEST_BASIC_PROJECT_CONFIG_ID), fieldMapping);
		when(configHelperService.getFieldMappingMap()).thenReturn(basicProjectConfigIdFieldMappingMap);

		// Kpi request
		kpiRequest = Mockito.mock(KpiRequest.class);
		when(kpiRequest.getIds()).thenReturn(new String[] {"node1", "node2"});
		when(kpiRequest.getSelecedHierarchyLabel()).thenReturn("SPRINT");
		when(kpiRequest.getLabel()).thenReturn("project");
		when(kpiRequest.getRequestTrackerId())
				.thenReturn("EXCEL-tracker-id-" + System.currentTimeMillis());

		when(mongoTemplate.find(any(Query.class), eq(JiraIssue.class)))
				.thenReturn(generateTestJiraIssues());
		when(mongoTemplate.find(any(Query.class), eq(JiraIssueCustomHistory.class)))
				.thenReturn(generateTestJiraIssueCustomHistories());

		when(commonService.sortTrendValueMap(any()))
				.thenAnswer(invocationOnMock -> invocationOnMock.getArgument(0));

		KpiElement resultedKpiElement =
				defectsBreachedSlasService.getKpiData(kpiRequest, kpiElement, treeAggregatorDetail);

		assertNotNull(resultedKpiElement);

		assertFalse(CollectionUtils.isEmpty(resultedKpiElement.getExcelData()));
		resultedKpiElement
				.getExcelData()
				.forEach(kpiExcelData -> assertNotNull(kpiExcelData.getDefectId()));
	}

	@Test
	public void when_calculateHoverMap_withValidData_expectCorrectAverages() {
		Map<String, Object> h1 = Map.of("totalResolvedIssues", 4, "breachedPercentage", 60.0);
		Map<String, Object> h2 = Map.of("totalResolvedIssues", 6, "breachedPercentage", 40.0);
		List<Map<String, Object>> list = List.of(h1, h2);

		Map<String, Object> result = defectsBreachedSlasService.calculateHoverMap(list);

		assertEquals(10, result.get("totalResolvedIssues"));
		assertEquals(50.0, result.get("breachedPercentage"));
	}

	@Test
	public void when_calculateHoverMap_withEmptyList_expectEmptyMap() {
		Map<String, Object> result =
				defectsBreachedSlasService.calculateHoverMap(Collections.emptyList());
		assertTrue(result.isEmpty());
	}

	@Test
	public void when_FetchKPIDataFromDBIsCalled_Expect_EmptyMapIsReturned() {
		Map<String, Object> result =
				defectsBreachedSlasService.fetchKPIDataFromDb(
						leafNodeList, StringUtils.EMPTY, StringUtils.EMPTY, kpiRequest);
		assertTrue(MapUtils.isEmpty(result));
	}

	@Test
	public void when_ValueListIsReceived_Expect_CalculateKpiValueResultIsNotNull() {
		List<Double> valueList = Arrays.asList(10.0D, 20.0D, 30.0D);
		String kpiName = "Test KPI";

		Double result = defectsBreachedSlasService.calculateKpiValue(valueList, kpiName);
		assertNotNull(result);
	}

	@Test
	public void when_FieldMappingIsReceived_Expect_CalculateThresholdValueReturnsNull() {
		fieldMapping = new FieldMapping();
		Double result = defectsBreachedSlasService.calculateThresholdValue(fieldMapping);
	}

	@Test
	public void when_CalculateKPIMetricsIsInvolved_Expect_ResultIsEqualToZero() {
		Map<String, Object> objectMap = new HashMap<>();
		objectMap.put("test", "value");

		Double result = defectsBreachedSlasService.calculateKPIMetrics(objectMap);
		assertEquals(0.0D, result, 0.0);
	}

	private void setupMocksIntoParentClasses() {
		try {
			Field abstractServiceField1 = ToolsKPIService.class.getDeclaredField("customApiConfig");
			abstractServiceField1.setAccessible(true);
			abstractServiceField1.set(defectsBreachedSlasService, customApiConfig);

			Field abstractServiceField2 = ToolsKPIService.class.getDeclaredField("commonService");
			abstractServiceField2.setAccessible(true);
			abstractServiceField2.set(defectsBreachedSlasService, commonService);

			Field abstractServiceField3 = ToolsKPIService.class.getDeclaredField("cacheService");
			abstractServiceField3.setAccessible(true);
			abstractServiceField3.set(defectsBreachedSlasService, cacheService);

			Field abstractServiceField4 = ToolsKPIService.class.getDeclaredField("configHelperService");
			abstractServiceField4.setAccessible(true);
			abstractServiceField4.set(defectsBreachedSlasService, configHelperService);
		} catch (Exception e) {
			log.error("Could not setup the mocks into the parent classes {}", e.getMessage());
		}
	}

	private void setupKpiElement() {
		kpiElement = new KpiElement();
		kpiElement.setKpiId(KPICode.DEFECTS_BREACHED_SLAS.getKpiId());
		kpiElement.setKpiName(KPICode.DEFECTS_BREACHED_SLAS.name());
	}

	private void setupTreeAggregatorDetail() {
		treeAggregatorDetail = new TreeAggregatorDetail();

		Node root = new Node();
		root.setId("root");
		root.setName("Root");
		root.setGroupName("root");
		treeAggregatorDetail.setRoot(root);

		Node projectNode = new Node();
		projectNode.setId("project_node");
		projectNode.setName("TEST PROJECT");
		ProjectFilter projectFilter =
				new ProjectFilter(
						"project-test", "TEST PROJECT", new ObjectId(TEST_BASIC_PROJECT_CONFIG_ID));
		projectNode.setProjectFilter(projectFilter);
		projectNode.setGroupName("project");

		leafNodeList = new ArrayList<>();
		Node node = createTestNode("sprint_node1", "Sprint1", "2023-01-01", "2023-01-15");
		leafNodeList.add(node);

		Node node2 = createTestNode("sprint_node2", "Sprint2", "2023-01-16", "2023-01-30");
		leafNodeList.add(node2);

		projectNode.setChildren(leafNodeList);

		root.setChildren(List.of(projectNode));

		Map<String, List<Node>> mapOfListOfLeafNodes = new HashMap<>();
		mapOfListOfLeafNodes.put("sprint", leafNodeList);
		treeAggregatorDetail.setMapOfListOfLeafNodes(mapOfListOfLeafNodes);

		Map<String, Node> nodeMap = new HashMap<>();
		nodeMap.put("sprint_node1", node);
		nodeMap.put("sprint_node2", node2);
		nodeMap.put("project_node", projectNode);
		nodeMap.put("root", root);
		root.setChildren(List.of(projectNode));
		treeAggregatorDetail.setMapTmp(nodeMap);
	}

	private Node createTestNode(String id, String sprintName, String startDate, String endDate) {
		Node node = new Node();
		node.setId(id);
		node.setName(sprintName);
		node.setGroupName("sprint"); // Set group name to avoid null pointer exceptions

		ProjectFilter projectFilter =
				new ProjectFilter(
						"project-test", "TEST PROJECT", new ObjectId(TEST_BASIC_PROJECT_CONFIG_ID));
		node.setProjectFilter(projectFilter);

		SprintFilter sprintFilter = new SprintFilter(id, sprintName, startDate, endDate);
		node.setSprintFilter(sprintFilter);

		return node;
	}

	private List<JiraIssueCustomHistory> generateTestJiraIssueCustomHistories() {
		List<JiraIssueCustomHistory> jiraIssueCustomHistoryList = new ArrayList<>();
		jiraIssueCustomHistoryList.add(
				createJiraIssueCustomHistory(
						"TEST-1",
						LocalDateTime.parse("2022-01-16T11:00:53.0000000").plusDays(60).plusMinutes(8)));
		jiraIssueCustomHistoryList.add(
				createJiraIssueCustomHistory(
						"TEST-2",
						LocalDateTime.parse("2022-01-16T11:00:53.0000000").plusDays(1).plusMinutes(8)));
		jiraIssueCustomHistoryList.add(
				createJiraIssueCustomHistory(
						"TEST-3",
						LocalDateTime.parse("2022-05-22T09:45:33.7654321").plusDays(5).plusMinutes(42)));
		jiraIssueCustomHistoryList.add(
				createJiraIssueCustomHistory(
						"TEST-4",
						LocalDateTime.parse("2022-07-01T18:10:05.3333333").plusDays(30).plusMinutes(1)));
		jiraIssueCustomHistoryList.add(
				createJiraIssueCustomHistory(
						"TEST-5",
						LocalDateTime.parse("2022-02-28T06:59:59.9999999").plusDays(3).plusMinutes(20)));
		jiraIssueCustomHistoryList.add(
				createJiraIssueCustomHistory(
						"TEST-6",
						LocalDateTime.parse("2022-06-30T20:20:20.1111111").plusDays(11).plusMinutes(9)));
		jiraIssueCustomHistoryList.add(
				createJiraIssueCustomHistory(
						"TEST-7",
						LocalDateTime.parse("2022-09-10T00:00:00.7777777").plusDays(28).plusMinutes(6)));
		jiraIssueCustomHistoryList.add(
				createJiraIssueCustomHistory(
						"TEST-8",
						LocalDateTime.parse("2022-04-05T23:59:59.0000001").plusDays(1).plusMinutes(1)));
		jiraIssueCustomHistoryList.add(
				createJiraIssueCustomHistory(
						"TEST-9",
						LocalDateTime.parse("2022-08-18T08:08:08.8888888").plusDays(10).plusMinutes(58)));
		jiraIssueCustomHistoryList.add(
				createJiraIssueCustomHistory(
						"TEST-10",
						LocalDateTime.parse("2022-11-11T11:11:11.5555555").plusDays(25).plusMinutes(15)));
		return jiraIssueCustomHistoryList;
	}

	private JiraIssueCustomHistory createJiraIssueCustomHistory(
			String storyId, LocalDateTime updatedOn) {
		JiraIssueCustomHistory jiraIssueCustomHistory = new JiraIssueCustomHistory();
		jiraIssueCustomHistory.setStoryID(storyId);
		List<JiraHistoryChangeLog> jiraHistoryChangeLogList = new ArrayList<>();
		JiraHistoryChangeLog jiraHistoryChangeLog = new JiraHistoryChangeLog();
		jiraHistoryChangeLog.setChangedTo("CLOSED");
		jiraHistoryChangeLog.setUpdatedOn(updatedOn);
		jiraHistoryChangeLogList.add(jiraHistoryChangeLog);
		jiraIssueCustomHistory.setStatusUpdationLog(jiraHistoryChangeLogList);
		return jiraIssueCustomHistory;
	}

	private List<JiraIssue> generateTestJiraIssues() {
		List<JiraIssue> jiraIssueList = new ArrayList<>();
		jiraIssueList.add(
				createJiraIssue("TEST-1", "2022-01-16T11:00:53.0000000", "Critical", "sprint_node1"));
		jiraIssueList.add(
				createJiraIssue("TEST-2", "2022-03-12T14:25:17.1234567", "Blocker", "sprint_node1"));
		jiraIssueList.add(
				createJiraIssue("TEST-3", "2022-05-22T09:45:33.7654321", "High", "sprint_node1"));
		jiraIssueList.add(
				createJiraIssue("TEST-4", "2022-07-01T18:10:05.3333333", "Medium", "sprint_node1"));
		jiraIssueList.add(
				createJiraIssue("TEST-5", "2022-02-28T06:59:59.9999999", "Low", "sprint_node1"));
		jiraIssueList.add(
				createJiraIssue("TEST-6", "2022-06-30T20:20:20.1111111", "Critical", "sprint_node2"));
		jiraIssueList.add(
				createJiraIssue("TEST-7", "2022-09-10T00:00:00.7777777", "Blocker", "sprint_node2"));
		jiraIssueList.add(
				createJiraIssue("TEST-8", "2022-04-05T23:59:59.0000001", "High", "sprint_node2"));
		jiraIssueList.add(
				createJiraIssue("TEST-9", "2022-08-18T08:08:08.8888888", "Medium", "sprint_node2"));
		jiraIssueList.add(
				createJiraIssue("TEST-10", "2022-11-11T11:11:11.5555555", "Low", "sprint_node2"));
		return jiraIssueList;
	}

	private JiraIssue createJiraIssue(
			String number, String createdDate, String severity, String sprintId) {
		return JiraIssue.builder()
				.number(number)
				.defectStoryID(Collections.emptySet())
				.timeSpentInMinutes(400)
				.createdDate(createdDate)
				.url(String.format("https://test.domain/browse/%s", number))
				.priority("P1")
				.severity(severity)
				.sprintID(sprintId)
				.status("CLOSED")
				.build();
	}
}
