package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
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
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.CommonService;
import com.publicissapient.kpidashboard.apis.common.service.ToolsKPIService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.FieldMappingDataFactory;
import com.publicissapient.kpidashboard.apis.data.JiraIssueHistoryDataFactory;
import com.publicissapient.kpidashboard.apis.data.KpiRequestFactory;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.dto.CycleTimeGroup;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;

@RunWith(MockitoJUnitRunner.class)
public class CycleTimeTrendSlingshotServiceImplTest {

	@Mock CacheService cacheService;
	@Mock ConfigHelperService configHelperService;
	@Mock JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;
	@Mock CommonService commonService;
	@Mock CustomApiConfig customApiConfig;

	@InjectMocks CycleTimeTrendSlingshotServiceImpl service;

	private KpiRequest kpiRequest;
	private List<AccountHierarchyData> accountHierarchyDataList;
	private final Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();
	private List<JiraIssueCustomHistory> jiraIssueCustomHistoryList;
	private FieldMapping fieldMapping;
	private static final ObjectId PROJECT_CONFIG_ID = new ObjectId("6335363749794a18e8a4479b");

	@Before
	public void setUp() {
		KpiRequestFactory kpiRequestFactory = KpiRequestFactory.newInstance("");
		kpiRequest = kpiRequestFactory.findKpiRequest("kpi202");
		kpiRequest.setLabel("PROJECT");

		Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
		ProjectBasicConfig projectConfig = new ProjectBasicConfig();
		projectConfig.setId(PROJECT_CONFIG_ID);
		projectConfig.setProjectName("Scrum Project");
		projectConfig.setProjectNodeId("Scrum Project_6335363749794a18e8a4479b");
		projectConfigMap.put(projectConfig.getProjectName(), projectConfig);
		Mockito.when(cacheService.cacheProjectConfigMapData()).thenReturn(projectConfigMap);
		Mockito.when(cacheService.getAdditionalFilterHierarchyLevel()).thenReturn(new HashMap<>());
		Class<?> parentClass = ToolsKPIService.class;
		ReflectionTestUtils.setField(
				service, parentClass, "cacheService", cacheService, CacheService.class);
		ReflectionTestUtils.setField(
				service, parentClass, "commonService", commonService, CommonService.class);
		ReflectionTestUtils.setField(
				service, parentClass, "customApiConfig", customApiConfig, CustomApiConfig.class);
		ReflectionTestUtils.setField(
				service,
				parentClass,
				"configHelperService",
				configHelperService,
				ConfigHelperService.class);

		AccountHierarchyFilterDataFactory accountHierarchyFilterDataFactory =
				AccountHierarchyFilterDataFactory.newInstance();
		accountHierarchyDataList = accountHierarchyFilterDataFactory.getAccountHierarchyDataList();

		FieldMappingDataFactory fieldMappingDataFactory =
				FieldMappingDataFactory.newInstance("/json/default/scrum_project_field_mappings.json");
		fieldMapping = fieldMappingDataFactory.getFieldMappings().get(0);
		fieldMapping.setJiraIssueTypeKPI202(new ArrayList<>(Arrays.asList("Story", "Defect")));
		fieldMapping.setJiradefecttype(new ArrayList<>(Arrays.asList("Defect", "Bug")));

		CycleTimeGroup group1 = new CycleTimeGroup();
		group1.setLabel("Development");
		group1.setStatuses(Arrays.asList("In Development", "In Progress"));

		CycleTimeGroup group2 = new CycleTimeGroup();
		group2.setLabel("Testing");
		group2.setStatuses(Arrays.asList("In Testing", "Ready For Testing"));

		fieldMapping.setJiraIssueStatusGroupByCategoryKPI202(Arrays.asList(group1, group2));
		fieldMappingMap.put(fieldMapping.getBasicProjectConfigId(), fieldMapping);

		JiraIssueHistoryDataFactory jiraIssueHistoryDataFactory =
				JiraIssueHistoryDataFactory.newInstance(
						"/json/default/iteration/jira_issue_custom_history.json");
		jiraIssueCustomHistoryList = jiraIssueHistoryDataFactory.getUniqueJiraIssueCustomHistory();
		jiraIssueCustomHistoryList.forEach(
				issue ->
						issue
								.getStatusUpdationLog()
								.forEach(
										log -> {
											log.setUpdatedOn(LocalDateTime.now().minusWeeks(2));
											log.setChangedTo("In Development");
											log.setChangedFrom("Open");
										}));

		when(jiraIssueCustomHistoryRepository.findByBasicProjectConfigIdIn(anyString()))
				.thenReturn(jiraIssueCustomHistoryList);
	}

	@Test
	public void testGetQualifierType() {
		assertEquals("CYCLE_TIME_TREND_SLINGSHOT", service.getQualifierType());
	}

	@Test
	public void testCalculateKPIMetrics() {
		assertEquals(Long.valueOf(0L), service.calculateKPIMetrics(new HashMap<>()));
	}

	@Test
	public void testFetchKPIDataFromDb() throws ApplicationException {
		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		List<Node> leafNodeList =
				KPIHelperUtil.getLeafNodes(treeAggregatorDetail.getRoot(), new ArrayList<>(), false);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);

		Map<String, Object> result =
				service.fetchKPIDataFromDb(
						leafNodeList,
						LocalDate.now().minusMonths(6).toString(),
						LocalDate.now().toString(),
						kpiRequest);

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_withFilterDuration_week() throws ApplicationException {
		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);

		// Set filterDuration to week
		LinkedHashMap<String, Object> filterDuration = new LinkedHashMap<>();
		filterDuration.put("value", 1);
		filterDuration.put("duration", "week");
		kpiRequest.getKpiList().forEach(k -> k.setFilterDuration(filterDuration));

		KpiElement result =
				service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_withFilterDuration_month() throws ApplicationException {
		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);

		LinkedHashMap<String, Object> filterDuration = new LinkedHashMap<>();
		filterDuration.put("value", 3);
		filterDuration.put("duration", "month");
		kpiRequest.getKpiList().forEach(k -> k.setFilterDuration(filterDuration));

		KpiElement result =
				service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_withNullFilterDuration() throws ApplicationException {
		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);

		// filterDuration is null (default path)
		kpiRequest.getKpiList().forEach(k -> k.setFilterDuration(null));

		KpiElement result =
				service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_withExcelRequestTracker() throws ApplicationException {
		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		ReflectionTestUtils.setField(kpiRequest, "requestTrackerId", "Excel-tracker-123");

		KpiElement result =
				service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

		assertNotNull(result);
		assertNotNull(result.getExcelData());
	}

	@Test
	public void testGetKpiData_withEmptyHistoryList() throws ApplicationException {
		when(jiraIssueCustomHistoryRepository.findByBasicProjectConfigIdIn(anyString()))
				.thenReturn(new ArrayList<>());
		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);

		KpiElement result =
				service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_statusWindowClosedByNextLog() throws ApplicationException {
		// Issue enters "In Development", then exits — window should be calculated
		JiraIssueCustomHistory history =
				buildHistory(
						"STORY-1",
						"Story",
						buildLog("Open", "In Development", LocalDateTime.now().minusDays(5)),
						buildLog("In Development", "Done", LocalDateTime.now().minusDays(2)));

		when(jiraIssueCustomHistoryRepository.findByBasicProjectConfigIdIn(anyString()))
				.thenReturn(List.of(history));

		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);

		KpiElement result =
				service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_statusWindowStillOpen() throws ApplicationException {
		// Issue enters "In Development" but never exits — open window, iterator has
		// next
		JiraIssueCustomHistory history =
				buildHistory(
						"STORY-2",
						"Story",
						buildLog("Open", "In Development", LocalDateTime.now().minusDays(3)));

		when(jiraIssueCustomHistoryRepository.findByBasicProjectConfigIdIn(anyString()))
				.thenReturn(List.of(history));

		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);

		KpiElement result =
				service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_noMatchingStatusInChangeLogs() throws ApplicationException {
		// Change logs have statuses not in any group — no DataValues should be produced
		JiraIssueCustomHistory history =
				buildHistory(
						"STORY-3",
						"Story",
						buildLog("Open", "Backlog", LocalDateTime.now().minusDays(10)),
						buildLog("Backlog", "Closed", LocalDateTime.now().minusDays(1)));

		when(jiraIssueCustomHistoryRepository.findByBasicProjectConfigIdIn(anyString()))
				.thenReturn(List.of(history));

		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);

		KpiElement result =
				service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_multipleGroupsMatched() throws ApplicationException {
		// Issue passes through both Development and Testing groups
		JiraIssueCustomHistory history =
				buildHistory(
						"STORY-4",
						"Story",
						buildLog("Open", "In Development", LocalDateTime.now().minusDays(10)),
						buildLog("In Development", "In Testing", LocalDateTime.now().minusDays(7)),
						buildLog("In Testing", "Done", LocalDateTime.now().minusDays(2)));

		when(jiraIssueCustomHistoryRepository.findByBasicProjectConfigIdIn(anyString()))
				.thenReturn(List.of(history));

		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);

		KpiElement result =
				service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

		assertNotNull(result);
		assertNotNull(result.getFilters());
	}

	@Test
	public void testGetKpiData_filterDuration_pastWeek_label() throws ApplicationException {
		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);

		LinkedHashMap<String, Object> filterDuration = new LinkedHashMap<>();
		filterDuration.put("value", 2);
		filterDuration.put("duration", "week");
		kpiRequest.getKpiList().forEach(k -> k.setFilterDuration(filterDuration));

		KpiElement result =
				service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_filterDuration_unknownLabel() throws ApplicationException {
		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);

		// value=99 has no label mapping → "Unknown"
		LinkedHashMap<String, Object> filterDuration = new LinkedHashMap<>();
		filterDuration.put("value", 99);
		filterDuration.put("duration", "month");
		kpiRequest.getKpiList().forEach(k -> k.setFilterDuration(filterDuration));

		KpiElement result =
				service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_kpi202NotInKpiList() throws ApplicationException {
		// kpiList does not contain kpi202 — should fall back to new KpiElement (null
		// filterDuration)
		kpiRequest.getKpiList().forEach(k -> k.setKpiId("kpi999"));
		TreeAggregatorDetail treeAggregatorDetail =
				KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
						kpiRequest, accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);

		KpiElement result =
				service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), treeAggregatorDetail);

		assertNotNull(result);
	}

	// ── helpers ──────────────────────────────────────────────────────────────

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
