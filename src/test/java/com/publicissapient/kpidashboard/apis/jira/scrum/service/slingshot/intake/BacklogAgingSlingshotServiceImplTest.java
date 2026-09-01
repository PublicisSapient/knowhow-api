/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 */
package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.intake;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.CommonService;
import com.publicissapient.kpidashboard.apis.common.service.ToolsKPIService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.KpiRequestFactory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.intake.BacklogAgingSlingshotServiceImpl.BacklogAgingDrillDownValue;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

/** Tests for {@link BacklogAgingSlingshotServiceImpl} (kpi224 — Slingshot / Intake). */
@RunWith(MockitoJUnitRunner.class)
public class BacklogAgingSlingshotServiceImplTest {

	private static final ObjectId PROJECT_CONFIG_ID = new ObjectId("6335363749794a18e8a4479b");
	private static final String HIERARCHY_LEVEL_ONE = "hierarchyLevelOne";
	private static final String BUCKET_0_30 = "0-30 Days";
	private static final String BUCKET_30_90 = "30-90 Days";
	private static final String BUCKET_90_180 = "90-180 Days";
	private static final String BUCKET_180_PLUS = "180+ Days";

	@Mock private CacheService cacheService;
	@Mock private ConfigHelperService configHelperService;
	@Mock private CommonService commonService;
	@Mock private CustomApiConfig customApiConfig;
	@Mock private JiraIssueRepository jiraIssueRepository;

	private BacklogAgingSlingshotServiceImpl service;

	private KpiRequest kpiRequest;
	private List<AccountHierarchyData> accountHierarchyDataList;
	private final Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();
	private FieldMapping fieldMapping;

	@Before
	public void setUp() {
		service = new BacklogAgingSlingshotServiceImpl();
		ReflectionTestUtils.setField(service, "configHelperService", configHelperService);
		ReflectionTestUtils.setField(service, "jiraIssueRepository", jiraIssueRepository);

		Class<?> parent = ToolsKPIService.class;
		ReflectionTestUtils.setField(service, parent, "cacheService", cacheService, CacheService.class);
		ReflectionTestUtils.setField(
				service, parent, "commonService", commonService, CommonService.class);
		ReflectionTestUtils.setField(
				service, parent, "customApiConfig", customApiConfig, CustomApiConfig.class);
		ReflectionTestUtils.setField(
				service, parent, "configHelperService", configHelperService, ConfigHelperService.class);

		kpiRequest =
				KpiRequestFactory.newInstance("")
						.findKpiRequest(KPICode.BACKLOG_AGING_SLINGSHOT.getKpiId());
		kpiRequest.setLabel("PROJECT");

		Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
		ProjectBasicConfig projectConfig = new ProjectBasicConfig();
		projectConfig.setId(PROJECT_CONFIG_ID);
		projectConfig.setProjectName("Scrum Project");
		projectConfig.setProjectNodeId("Scrum Project_6335363749794a18e8a4479b");
		projectConfigMap.put(projectConfig.getProjectName(), projectConfig);

		lenient().when(cacheService.cacheProjectConfigMapData()).thenReturn(projectConfigMap);
		lenient().when(cacheService.getAdditionalFilterHierarchyLevel()).thenReturn(new HashMap<>());
		lenient().when(cacheService.getKpiBenchmarkTargets()).thenReturn(new HashMap<>());
		lenient()
				.when(
						cacheService.getFromApplicationCache(
								Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
				.thenReturn("Jira-tracker-id");

		lenient().when(configHelperService.calculateMaturity()).thenReturn(new HashMap<>());
		lenient().when(configHelperService.loadKpiMaster()).thenReturn(new ArrayList<>());

		accountHierarchyDataList =
				AccountHierarchyFilterDataFactory.newInstance().getAccountHierarchyDataList();

		fieldMapping = new FieldMapping();
		fieldMapping.setBasicProjectConfigId(PROJECT_CONFIG_ID);
		fieldMapping.setJiraBacklogStatusKPI224(new ArrayList<>(Arrays.asList("Backlog", "To Do")));
		fieldMappingMap.put(PROJECT_CONFIG_ID, fieldMapping);
	}

	// ------------------------------------------------------------------
	// Wiring
	// ------------------------------------------------------------------

	@Test
	public void testGetQualifierType() {
		assertEquals(KPICode.BACKLOG_AGING_SLINGSHOT.name(), service.getQualifierType());
	}

	@Test
	public void testCalculateKPIMetricsIsNotUsed() {
		assertNull(service.calculateKPIMetrics(new HashMap<>()));
	}

	@Test
	public void testCalculateThresholdValueFallsBackToKpiMasterWhenNotConfigured() {
		assertEquals(Double.valueOf(0.0d), service.calculateThresholdValue(new FieldMapping()));
	}

	@Test
	public void testCalculateThresholdValueUsesProjectOverride() {
		FieldMapping mapping = new FieldMapping();
		mapping.setThresholdValueKPI224("25");
		assertEquals(Double.valueOf(25.0d), service.calculateThresholdValue(mapping));
	}

	@Test
	public void testCalculateKpiValueDelegatesToDoubleAggregation() {
		assertNotNull(
				service.calculateKpiValue(
						new ArrayList<>(Arrays.asList(1.0d, 2.0d)),
						KPICode.BACKLOG_AGING_SLINGSHOT.getKpiId()));
	}

	// ------------------------------------------------------------------
	// Bucketing / age maths
	// ------------------------------------------------------------------

	@Test
	public void testResolveBucketBoundaries() {
		assertEquals(BUCKET_0_30, BacklogAgingSlingshotServiceImpl.resolveBucket(0d));
		assertEquals(BUCKET_0_30, BacklogAgingSlingshotServiceImpl.resolveBucket(29.99d));
		assertEquals(BUCKET_30_90, BacklogAgingSlingshotServiceImpl.resolveBucket(30d));
		assertEquals(BUCKET_30_90, BacklogAgingSlingshotServiceImpl.resolveBucket(89.99d));
		assertEquals(BUCKET_90_180, BacklogAgingSlingshotServiceImpl.resolveBucket(90d));
		assertEquals(BUCKET_90_180, BacklogAgingSlingshotServiceImpl.resolveBucket(179.99d));
		assertEquals(BUCKET_180_PLUS, BacklogAgingSlingshotServiceImpl.resolveBucket(180d));
		assertEquals(BUCKET_180_PLUS, BacklogAgingSlingshotServiceImpl.resolveBucket(3650d));
	}

	@Test
	public void testAgeInDaysIsNeverNegativeAndRounded() {
		LocalDateTime now = LocalDateTime.of(2026, 6, 1, 12, 0);
		assertEquals(10.0d, BacklogAgingSlingshotServiceImpl.ageInDays(now.minusDays(10), now), 0.001d);
		assertEquals(0.5d, BacklogAgingSlingshotServiceImpl.ageInDays(now.minusHours(12), now), 0.001d);
		// created in the future → clamped to zero
		assertEquals(0.0d, BacklogAgingSlingshotServiceImpl.ageInDays(now.plusDays(5), now), 0.001d);
	}

	@Test
	public void testParseCreatedDateHandlesTheKnownJiraShapes() {
		assertNotNull(BacklogAgingSlingshotServiceImpl.parseCreatedDate("2026-01-05T10:15:30.0000000"));
		assertNotNull(BacklogAgingSlingshotServiceImpl.parseCreatedDate("2026-01-05T10:15:30"));
		assertNull(BacklogAgingSlingshotServiceImpl.parseCreatedDate(null));
		assertNull(BacklogAgingSlingshotServiceImpl.parseCreatedDate("  "));
		assertNull(BacklogAgingSlingshotServiceImpl.parseCreatedDate("not-a-date"));
	}

	// ------------------------------------------------------------------
	// fetchKPIDataFromDb
	// ------------------------------------------------------------------

	@Test
	@SuppressWarnings("unchecked")
	public void testFetchKPIDataFromDbUsesConfiguredStatusesAndTypes() throws ApplicationException {
		fieldMapping.setJiraIssueTypeKPI224(new ArrayList<>(Arrays.asList("Story", "Bug")));
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findBacklogIssuesByStatusAndType(anyString(), any(), any()))
				.thenReturn(List.of(issue("PROJ-1", "Story", "Backlog", daysAgo(10))));

		Map<String, Object> result =
				service.fetchKPIDataFromDb(List.of(projectNode()), null, null, kpiRequest);

		ArgumentCaptor<Set<String>> statusCaptor = ArgumentCaptor.forClass(Set.class);
		ArgumentCaptor<Set<String>> typeCaptor = ArgumentCaptor.forClass(Set.class);
		verify(jiraIssueRepository)
				.findBacklogIssuesByStatusAndType(
						eq(PROJECT_CONFIG_ID.toString()), statusCaptor.capture(), typeCaptor.capture());

		assertEquals(Set.of("Backlog", "To Do"), statusCaptor.getValue());
		assertEquals(Set.of("Story", "Bug"), typeCaptor.getValue());

		Map<String, List<JiraIssue>> byProject =
				(Map<String, List<JiraIssue>>) result.get("backlogIssueData");
		assertEquals(1, byProject.get(PROJECT_CONFIG_ID.toString()).size());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFetchKPIDataFromDbFallsBackToDefaultBacklogStatuses()
			throws ApplicationException {
		fieldMapping.setJiraBacklogStatusKPI224(new ArrayList<>());
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findBacklogIssuesByStatusAndType(anyString(), any(), any()))
				.thenReturn(new ArrayList<>());

		service.fetchKPIDataFromDb(List.of(projectNode()), null, null, kpiRequest);

		ArgumentCaptor<Set<String>> statusCaptor = ArgumentCaptor.forClass(Set.class);
		verify(jiraIssueRepository)
				.findBacklogIssuesByStatusAndType(anyString(), statusCaptor.capture(), any());
		assertTrue(statusCaptor.getValue().contains("Backlog"));
		assertTrue(statusCaptor.getValue().contains("To Do"));
		assertTrue(statusCaptor.getValue().contains("Open"));
		assertTrue(statusCaptor.getValue().contains("New"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFetchKPIDataFromDbDropsAlreadyRefinedIssues() throws ApplicationException {
		fieldMapping.setJiraBacklogStatusKPI224(
				new ArrayList<>(Arrays.asList("Backlog", "To Do", "Ready")));
		fieldMapping.setJiraStatusForRefinedKPI224(new ArrayList<>(Collections.singletonList("Ready")));
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findBacklogIssuesByStatusAndType(anyString(), any(), any()))
				.thenReturn(
						new ArrayList<>(
								Arrays.asList(
										issue("PROJ-1", "Story", "Backlog", daysAgo(10)),
										issue("PROJ-2", "Story", "ready", daysAgo(20)))));

		Map<String, Object> result =
				service.fetchKPIDataFromDb(List.of(projectNode()), null, null, kpiRequest);

		Map<String, List<JiraIssue>> byProject =
				(Map<String, List<JiraIssue>>) result.get("backlogIssueData");
		List<JiraIssue> retained = byProject.get(PROJECT_CONFIG_ID.toString());
		assertEquals(1, retained.size());
		assertEquals("PROJ-1", retained.get(0).getNumber());
	}

	@Test
	public void testFetchKPIDataFromDbHandlesEmptyLeafNodeList() {
		Map<String, Object> result =
				service.fetchKPIDataFromDb(new ArrayList<>(), null, null, kpiRequest);
		assertNotNull(result.get("backlogIssueData"));
		verify(jiraIssueRepository, never())
				.findBacklogIssuesByStatusAndType(anyString(), any(), any());
	}

	// ------------------------------------------------------------------
	// getKpiData — the histogram itself
	// ------------------------------------------------------------------

	@Test
	@SuppressWarnings("unchecked")
	public void testGetKpiDataBuildsFourBucketHistogram() throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findBacklogIssuesByStatusAndType(anyString(), any(), any()))
				.thenReturn(
						new ArrayList<>(
								Arrays.asList(
										issue("PROJ-1", "Story", "Backlog", daysAgo(5)),
										issue("PROJ-2", "Story", "Backlog", daysAgo(15)),
										issue("PROJ-3", "Bug", "To Do", daysAgo(45)),
										issue("PROJ-4", "Story", "To Do", daysAgo(120)),
										issue("PROJ-5", "Story", "Backlog", daysAgo(400)))));

		KpiElement kpiElement = kpiRequest.getKpiList().get(0);
		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTree());

		assertNotNull(result.getTrendValueList());
		assertEquals(
				List.of(BUCKET_0_30, BUCKET_30_90, BUCKET_90_180, BUCKET_180_PLUS),
				result.getxAxisValues());
		assertEquals("Age (Days)", result.getLabelXAxis());

		List<DataCount> trendValues = (List<DataCount>) result.getTrendValueList();
		Map<String, Double> bucketWiseCount = flattenBuckets(trendValues);
		assertEquals(Double.valueOf(2d), bucketWiseCount.get(BUCKET_0_30));
		assertEquals(Double.valueOf(1d), bucketWiseCount.get(BUCKET_30_90));
		assertEquals(Double.valueOf(1d), bucketWiseCount.get(BUCKET_90_180));
		assertEquals(Double.valueOf(1d), bucketWiseCount.get(BUCKET_180_PLUS));
	}

	@Test
	public void testGetKpiDataExposesNoFilters() throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findBacklogIssuesByStatusAndType(anyString(), any(), any()))
				.thenReturn(
						new ArrayList<>(
								Collections.singletonList(issue("PROJ-1", "Story", "Backlog", daysAgo(5)))));

		KpiElement result = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), buildTree());

		assertNull(result.getFilters());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testGetKpiDataAttachesIssueTypeDrillDownToEveryBucket() throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findBacklogIssuesByStatusAndType(anyString(), any(), any()))
				.thenReturn(
						new ArrayList<>(
								Arrays.asList(
										issue("PROJ-1", "Story", "Backlog", daysAgo(5)),
										issue("PROJ-2", "Story", "Backlog", daysAgo(10)),
										issue("PROJ-3", "Bug", "Backlog", daysAgo(200)))));

		KpiElement result = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), buildTree());

		List<DataCount> buckets = bucketsOfFirstProject((List<DataCount>) result.getTrendValueList());
		assertEquals(4, buckets.size());

		// every bucket exposes the same, stable set of issue type keys
		buckets.forEach(
				bucket -> {
					List<BacklogAgingDrillDownValue> drillDown =
							(List<BacklogAgingDrillDownValue>) bucket.getDrillDown();
					assertNotNull(drillDown);
					assertEquals(
							List.of("Bug", "Story"),
							drillDown.stream().map(BacklogAgingDrillDownValue::issueType).toList());
				});

		Map<String, Long> firstBucket = drillDownAsMap(buckets.get(0));
		assertEquals(Long.valueOf(2L), firstBucket.get("Story"));
		assertEquals(Long.valueOf(0L), firstBucket.get("Bug"));

		Map<String, Long> oldestBucket = drillDownAsMap(buckets.get(3));
		assertEquals(Long.valueOf(0L), oldestBucket.get("Story"));
		assertEquals(Long.valueOf(1L), oldestBucket.get("Bug"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testDrillDownCountsAlwaysSumUpToTheBucketValue() throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findBacklogIssuesByStatusAndType(anyString(), any(), any()))
				.thenReturn(
						new ArrayList<>(
								Arrays.asList(
										issue("PROJ-1", "Story", "Backlog", daysAgo(5)),
										issue("PROJ-2", "Bug", "Backlog", daysAgo(6)),
										issue("PROJ-3", "Task", "To Do", daysAgo(7)),
										issue("PROJ-4", "Story", "To Do", daysAgo(120)))));

		KpiElement result = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), buildTree());

		bucketsOfFirstProject((List<DataCount>) result.getTrendValueList())
				.forEach(
						bucket -> {
							long drillDownTotal =
									((List<BacklogAgingDrillDownValue>) bucket.getDrillDown())
											.stream().mapToLong(BacklogAgingDrillDownValue::count).sum();
							assertEquals(((Number) bucket.getValue()).longValue(), drillDownTotal);
						});
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testGetKpiDataWithEmptyBacklogStillEmitsAllBuckets() throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findBacklogIssuesByStatusAndType(anyString(), any(), any()))
				.thenReturn(new ArrayList<>());

		KpiElement result = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), buildTree());

		List<DataCount> trendValues = (List<DataCount>) result.getTrendValueList();
		Map<String, Double> bucketWiseCount = flattenBuckets(trendValues);
		assertEquals(4, bucketWiseCount.size());
		assertTrue(bucketWiseCount.values().stream().allMatch(v -> v == 0d));

		// no issue types in the backlog -> empty (but never null) drill-down
		bucketsOfFirstProject(trendValues)
				.forEach(
						bucket -> {
							assertNotNull(bucket.getDrillDown());
							assertTrue(((List<BacklogAgingDrillDownValue>) bucket.getDrillDown()).isEmpty());
						});
	}

	// ------------------------------------------------------------------
	// Drill-down building / aggregation
	// ------------------------------------------------------------------

	@Test
	public void testBuildIssueTypeDrillDownKeepsZeroCountKeys() {
		List<BacklogAgingDrillDownValue> drillDown =
				BacklogAgingSlingshotServiceImpl.buildIssueTypeDrillDown(
						new ArrayList<>(), List.of("Story", "Bug"));

		assertEquals(2, drillDown.size());
		assertTrue(drillDown.stream().allMatch(entry -> entry.count() == 0L));
	}

	@Test
	public void testBuildIssueTypeDrillDownHandlesNullInputs() {
		assertTrue(BacklogAgingSlingshotServiceImpl.buildIssueTypeDrillDown(null, null).isEmpty());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testCalculateDrillDownValueSumsCountsAcrossProjects() {
		Object aggregated =
				service.calculateDrillDownValue(
						new ArrayList<>(
								Arrays.asList(
										List.of(
												new BacklogAgingDrillDownValue("Story", 3L),
												new BacklogAgingDrillDownValue("Bug", 1L)),
										List.of(
												new BacklogAgingDrillDownValue("Story", 2L),
												new BacklogAgingDrillDownValue("Task", 4L)))));

		Map<String, Long> merged =
				((List<BacklogAgingDrillDownValue>) aggregated)
						.stream()
								.collect(
										Collectors.toMap(
												BacklogAgingDrillDownValue::issueType, BacklogAgingDrillDownValue::count));

		assertEquals(Long.valueOf(5L), merged.get("Story"));
		assertEquals(Long.valueOf(1L), merged.get("Bug"));
		assertEquals(Long.valueOf(4L), merged.get("Task"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testCalculateDrillDownValueIgnoresNullAndForeignEntries() {
		Object aggregated =
				service.calculateDrillDownValue(
						new ArrayList<>(
								Arrays.asList(
										null,
										"not-a-drilldown",
										List.of("foreign", new BacklogAgingDrillDownValue("Story", 7L)))));

		List<BacklogAgingDrillDownValue> result = (List<BacklogAgingDrillDownValue>) aggregated;
		assertEquals(1, result.size());
		assertEquals("Story", result.get(0).issueType());
		assertEquals(7L, result.get(0).count());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testCalculateDrillDownValueWithNoInputReturnsEmptyList() {
		assertTrue(
				((List<BacklogAgingDrillDownValue>) service.calculateDrillDownValue(null)).isEmpty());
	}

	@Test
	public void testGetKpiDataSkipsIssuesWithUnparseableCreatedDate() throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		JiraIssue broken = issue("PROJ-9", "Story", "Backlog", "garbage");
		when(jiraIssueRepository.findBacklogIssuesByStatusAndType(anyString(), any(), any()))
				.thenReturn(new ArrayList<>(Collections.singletonList(broken)));

		KpiElement result = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), buildTree());

		assertNotNull(result.getTrendValueList());
	}

	// ------------------------------------------------------------------
	// Excel export
	// ------------------------------------------------------------------

	@Test
	public void testExcelDataIsPopulatedForExcelRequests() throws ApplicationException {
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
				.thenReturn("Excel-Jira-tracker");
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findBacklogIssuesByStatusAndType(anyString(), any(), any()))
				.thenReturn(
						new ArrayList<>(
								Arrays.asList(
										issue("PROJ-1", "Story", "Backlog", daysAgo(5)),
										issue("PROJ-2", "Story", "Backlog", daysAgo(365)))));

		KpiElement result = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), buildTree());

		List<KPIExcelData> excelData = result.getExcelData();
		assertNotNull(excelData);
		assertEquals(2, excelData.size());
		// oldest first
		assertEquals(BUCKET_180_PLUS, excelData.get(0).getAgingBucket());
		assertEquals(BUCKET_0_30, excelData.get(1).getAgingBucket());
		assertNotNull(excelData.get(0).getAgeInDays());
		assertEquals("Backlog", excelData.get(0).getStatus());
		assertEquals(KPIExcelColumn.BACKLOG_AGING_SLINGSHOT.getColumns(), result.getExcelColumns());
	}

	@Test
	public void testExcelDataIsEmptyForNonExcelRequests() throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findBacklogIssuesByStatusAndType(anyString(), any(), any()))
				.thenReturn(
						new ArrayList<>(
								Collections.singletonList(issue("PROJ-1", "Story", "Backlog", daysAgo(5)))));

		KpiElement result = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), buildTree());

		assertNotNull(result.getExcelData());
		assertTrue(result.getExcelData().isEmpty());
		assertFalse(KPIExcelColumn.BACKLOG_AGING_SLINGSHOT.getColumns().isEmpty());
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private TreeAggregatorDetail buildTree() throws ApplicationException {
		return KPIHelperUtil.getTreeLeafNodesGroupedByFilter(
				kpiRequest, accountHierarchyDataList, new ArrayList<>(), HIERARCHY_LEVEL_ONE, 5);
	}

	private Node projectNode() throws ApplicationException {
		TreeAggregatorDetail tree = buildTree();
		return tree.getMapOfListOfProjectNodes().get("project").get(0);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Double> flattenBuckets(List<DataCount> trendValues) {
		Map<String, Double> bucketWiseValue = new LinkedHashMap<>();
		trendValues.forEach(
				parent ->
						((List<DataCount>) parent.getValue())
								.forEach(
										dc ->
												bucketWiseValue.merge(
														dc.getDate(), ((Number) dc.getValue()).doubleValue(), Double::sum)));
		return bucketWiseValue;
	}

	@SuppressWarnings("unchecked")
	private List<DataCount> bucketsOfFirstProject(List<DataCount> trendValues) {
		return (List<DataCount>) trendValues.get(0).getValue();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Long> drillDownAsMap(DataCount bucket) {
		return ((List<BacklogAgingDrillDownValue>) bucket.getDrillDown())
				.stream()
						.collect(
								Collectors.toMap(
										BacklogAgingDrillDownValue::issueType, BacklogAgingDrillDownValue::count));
	}

	private String daysAgo(int days) {
		return DateUtil.getTodayTime()
				.minusDays(days)
				.format(java.time.format.DateTimeFormatter.ofPattern(DateUtil.TIME_FORMAT));
	}

	private JiraIssue issue(String number, String type, String status, String createdDate) {
		JiraIssue issue = new JiraIssue();
		issue.setNumber(number);
		issue.setName(number + " summary");
		issue.setTypeName(type);
		issue.setStatus(status);
		issue.setPriority("P2");
		issue.setUrl("http://jira/" + number);
		issue.setCreatedDate(createdDate);
		issue.setBasicProjectConfigId(PROJECT_CONFIG_ID.toString());
		return issue;
	}
}
