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
package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.quality;

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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
import com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.quality.RefinementToDefectLinkageServiceImpl.RootCauseDrillDownValue;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

/** Tests for {@link RefinementToDefectLinkageServiceImpl} (kpi226 — Slingshot / Quality). */
@RunWith(MockitoJUnitRunner.class)
public class RefinementToDefectLinkageServiceImplTest {

	private static final ObjectId PROJECT_CONFIG_ID = new ObjectId("6335363749794a18e8a4479b");
	private static final String HIERARCHY_LEVEL_ONE = "hierarchyLevelOne";
	private static final String HOVER_REFINEMENT = "Refinement Root Cause";
	private static final String HOVER_TOTAL = "Total Defects";
	private static final String HOVER_TAGGED = "Root Cause Tagged";
	private static final String HOVER_COVERAGE = "Root Cause Coverage %";

	@Mock private CacheService cacheService;
	@Mock private ConfigHelperService configHelperService;
	@Mock private CommonService commonService;
	@Mock private CustomApiConfig customApiConfig;
	@Mock private JiraIssueRepository jiraIssueRepository;

	private RefinementToDefectLinkageServiceImpl service;

	private KpiRequest kpiRequest;
	private List<AccountHierarchyData> accountHierarchyDataList;
	private final Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();
	private FieldMapping fieldMapping;

	@Before
	public void setUp() {
		service = new RefinementToDefectLinkageServiceImpl();
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
						.findKpiRequest(KPICode.REFINEMENT_TO_DEFECT_LINKAGE.getKpiId());
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
		fieldMappingMap.put(PROJECT_CONFIG_ID, fieldMapping);
	}

	// ------------------------------------------------------------------
	// Wiring
	// ------------------------------------------------------------------

	@Test
	public void testGetQualifierType() {
		assertEquals(KPICode.REFINEMENT_TO_DEFECT_LINKAGE.name(), service.getQualifierType());
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
		mapping.setThresholdValueKPI226("20");
		assertEquals(Double.valueOf(20.0d), service.calculateThresholdValue(mapping));
	}

	@Test
	public void testCalculateKpiValueDelegatesToDoubleAggregation() {
		assertNotNull(
				service.calculateKpiValue(
						new ArrayList<>(Arrays.asList(10.0d, 30.0d)),
						KPICode.REFINEMENT_TO_DEFECT_LINKAGE.getKpiId()));
	}

	// ------------------------------------------------------------------
	// Manual root-cause classification
	// ------------------------------------------------------------------

	@Test
	public void testResolveCategoryUsesProjectConfiguredRefinementValuesFirst() {
		Set<String> configured = Set.of("ac missing", "refinement");
		assertEquals(
				RefinementToDefectLinkageServiceImpl.CATEGORY_REFINEMENT,
				RefinementToDefectLinkageServiceImpl.resolveCategory(List.of("AC Missing"), configured));
		assertEquals(
				RefinementToDefectLinkageServiceImpl.CATEGORY_REFINEMENT,
				RefinementToDefectLinkageServiceImpl.resolveCategory(List.of("Refinement"), configured));
	}

	@Test
	public void testResolveCategoryMapsTheFiveCanonicalBuckets() {
		Set<String> configured = Set.of("refinement");
		assertEquals(
				RefinementToDefectLinkageServiceImpl.CATEGORY_DESIGN,
				RefinementToDefectLinkageServiceImpl.resolveCategory(List.of("Design"), configured));
		assertEquals(
				RefinementToDefectLinkageServiceImpl.CATEGORY_CODE,
				RefinementToDefectLinkageServiceImpl.resolveCategory(List.of("Coding"), configured));
		assertEquals(
				RefinementToDefectLinkageServiceImpl.CATEGORY_INFRA,
				RefinementToDefectLinkageServiceImpl.resolveCategory(List.of("Environment"), configured));
		assertEquals(
				RefinementToDefectLinkageServiceImpl.CATEGORY_EXTERNAL,
				RefinementToDefectLinkageServiceImpl.resolveCategory(List.of("Third Party"), configured));
	}

	@Test
	public void testResolveCategoryNeverGuessesRefinement() {
		Set<String> configured = Set.of("refinement");
		// an unrecognised tag must not silently inflate the numerator
		assertEquals(
				RefinementToDefectLinkageServiceImpl.CATEGORY_OTHER,
				RefinementToDefectLinkageServiceImpl.resolveCategory(
						List.of("Something Bespoke"), configured));
	}

	@Test
	public void testResolveCategoryFlagsTheTaggingGap() {
		Set<String> configured = Set.of("refinement");
		assertEquals(
				RefinementToDefectLinkageServiceImpl.CATEGORY_UNTAGGED,
				RefinementToDefectLinkageServiceImpl.resolveCategory(null, configured));
		assertEquals(
				RefinementToDefectLinkageServiceImpl.CATEGORY_UNTAGGED,
				RefinementToDefectLinkageServiceImpl.resolveCategory(new ArrayList<>(), configured));
	}

	@Test
	public void testParseCreatedDateHandlesTheKnownJiraShapes() {
		assertNotNull(
				RefinementToDefectLinkageServiceImpl.parseCreatedDate("2026-01-05T10:15:30.0000000"));
		assertNotNull(RefinementToDefectLinkageServiceImpl.parseCreatedDate("2026-01-05T10:15:30"));
		assertNull(RefinementToDefectLinkageServiceImpl.parseCreatedDate(null));
		assertNull(RefinementToDefectLinkageServiceImpl.parseCreatedDate("  "));
		assertNull(RefinementToDefectLinkageServiceImpl.parseCreatedDate("not-a-date"));
	}

	// ------------------------------------------------------------------
	// Fetch window alignment
	// ------------------------------------------------------------------

	@Test
	public void testWeeklyWindowStartsOnTheMondayOfTheOldestPlottedWeek() {
		LocalDateTime start = RefinementToDefectLinkageServiceImpl.windowStart(CommonConstant.WEEK, 12);
		assertEquals(DayOfWeek.MONDAY, start.getDayOfWeek());
		assertEquals(LocalTime.MIDNIGHT, start.toLocalTime());
		// the oldest plotted week is the one containing today-11w, never earlier
		assertFalse(start.toLocalDate().isAfter(LocalDate.now().minusWeeks(11)));
		assertTrue(start.toLocalDate().isAfter(LocalDate.now().minusWeeks(13)));
	}

	@Test
	public void testMonthlyWindowStartsOnTheFirstOfTheOldestPlottedMonth() {
		LocalDateTime start = RefinementToDefectLinkageServiceImpl.windowStart(CommonConstant.MONTH, 6);
		assertEquals(1, start.getDayOfMonth());
		assertEquals(LocalTime.MIDNIGHT, start.toLocalTime());
		assertEquals(LocalDate.now().minusMonths(5).getMonthValue(), start.getMonthValue());
	}

	// ------------------------------------------------------------------
	// fetchKPIDataFromDb — production defect identification
	// ------------------------------------------------------------------

	@Test
	@SuppressWarnings("unchecked")
	public void testFetchKPIDataFromDbUsesConfiguredDefectIssueTypes() throws ApplicationException {
		fieldMapping.setJiraIssueTypeKPI226(new ArrayList<>(Arrays.asList("Bug", "Incident")));
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						any(), anyString(), anyString(), anyString(), any()))
				.thenReturn(new ArrayList<>(Collections.singletonList(productionDefect("PROJ-1"))));

		Map<String, Object> result =
				service.fetchKPIDataFromDb(List.of(projectNode()), "2026-01-01", "2026-06-01", kpiRequest);

		ArgumentCaptor<Set<String>> typeCaptor = ArgumentCaptor.forClass(Set.class);
		verify(jiraIssueRepository)
				.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						typeCaptor.capture(),
						eq(PROJECT_CONFIG_ID.toString()),
						eq("2026-01-01"),
						eq("2026-06-01"),
						any());
		assertEquals(Set.of("Bug", "Incident"), typeCaptor.getValue());

		Map<String, List<JiraIssue>> byProject =
				(Map<String, List<JiraIssue>>) result.get("productionDefectData");
		assertEquals(1, byProject.get(PROJECT_CONFIG_ID.toString()).size());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFetchKPIDataFromDbFallsBackToDefaultDefectIssueTypes()
			throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						any(), anyString(), anyString(), anyString(), any()))
				.thenReturn(new ArrayList<>());

		service.fetchKPIDataFromDb(List.of(projectNode()), "2026-01-01", "2026-06-01", kpiRequest);

		ArgumentCaptor<Set<String>> typeCaptor = ArgumentCaptor.forClass(Set.class);
		verify(jiraIssueRepository)
				.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						typeCaptor.capture(), anyString(), anyString(), anyString(), any());
		assertEquals(Set.of("Bug", "Defect"), typeCaptor.getValue());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFetchKPIDataFromDbKeepsOnlyProcessorStampedProductionDefectsByDefault()
			throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		JiraIssue escaped = productionDefect("PROJ-1");
		JiraIssue internal = defect("PROJ-2");
		when(jiraIssueRepository.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						any(), anyString(), anyString(), anyString(), any()))
				.thenReturn(new ArrayList<>(Arrays.asList(escaped, internal)));

		Map<String, Object> result =
				service.fetchKPIDataFromDb(List.of(projectNode()), "2026-01-01", "2026-06-01", kpiRequest);

		List<JiraIssue> retained =
				((Map<String, List<JiraIssue>>) result.get("productionDefectData"))
						.get(PROJECT_CONFIG_ID.toString());
		assertEquals(1, retained.size());
		assertEquals("PROJ-1", retained.get(0).getNumber());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFetchKPIDataFromDbSupportsLabelBasedProductionDefects()
			throws ApplicationException {
		fieldMapping.setJiraProductionDefectIdentificationKPI226("Labels");
		fieldMapping.setJiraProductionDefectValueKPI226(
				new ArrayList<>(Collections.singletonList("PROD_DEFECT")));
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);

		JiraIssue labelled = defect("PROJ-1");
		labelled.setLabels(new ArrayList<>(Collections.singletonList("prod_defect")));
		JiraIssue unlabelled = defect("PROJ-2");
		// stamped by the processor but not carrying the label — labels mode must win
		JiraIssue stampedOnly = productionDefect("PROJ-3");

		when(jiraIssueRepository.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						any(), anyString(), anyString(), anyString(), any()))
				.thenReturn(new ArrayList<>(Arrays.asList(labelled, unlabelled, stampedOnly)));

		Map<String, Object> result =
				service.fetchKPIDataFromDb(List.of(projectNode()), "2026-01-01", "2026-06-01", kpiRequest);

		List<JiraIssue> retained =
				((Map<String, List<JiraIssue>>) result.get("productionDefectData"))
						.get(PROJECT_CONFIG_ID.toString());
		assertEquals(1, retained.size());
		assertEquals("PROJ-1", retained.get(0).getNumber());
	}

	@Test
	public void testFetchKPIDataFromDbHandlesEmptyLeafNodeList() {
		Map<String, Object> result =
				service.fetchKPIDataFromDb(new ArrayList<>(), "2026-01-01", "2026-06-01", kpiRequest);
		assertNotNull(result.get("productionDefectData"));
		verify(jiraIssueRepository, never())
				.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						any(), anyString(), anyString(), anyString(), any());
	}

	// ------------------------------------------------------------------
	// getKpiData — the linkage percentage itself
	// ------------------------------------------------------------------

	@Test
	@SuppressWarnings("unchecked")
	public void testGetKpiDataComputesRefinementShareOfProductionDefects()
			throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						any(), anyString(), anyString(), anyString(), any()))
				.thenReturn(
						new ArrayList<>(
								Arrays.asList(
										taggedDefect("PROJ-1", "Refinement"),
										taggedDefect("PROJ-2", "Coding"),
										taggedDefect("PROJ-3", "Design"),
										taggedDefect("PROJ-4", "Environment"))));

		KpiElement result = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), buildTree());

		DataCount current = currentPeriod((List<DataCount>) result.getTrendValueList());
		// 1 of 4 production defects traces back to refinement
		assertEquals(25.0d, ((Number) current.getValue()).doubleValue(), 0.001d);
		assertEquals(1L, current.getHoverValue().get(HOVER_REFINEMENT));
		assertEquals(4L, current.getHoverValue().get(HOVER_TOTAL));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testGetKpiDataReportsRootCauseCoverageAlongsideTheScore()
			throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						any(), anyString(), anyString(), anyString(), any()))
				.thenReturn(
						new ArrayList<>(
								Arrays.asList(
										taggedDefect("PROJ-1", "Refinement"),
										productionDefect("PROJ-2"),
										productionDefect("PROJ-3"),
										productionDefect("PROJ-4"))));

		KpiElement result = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), buildTree());

		DataCount current = currentPeriod((List<DataCount>) result.getTrendValueList());
		// only one of four defects was actually classified at incident review
		assertEquals(1L, current.getHoverValue().get(HOVER_TAGGED));
		assertEquals(
				25.0d, ((Number) current.getHoverValue().get(HOVER_COVERAGE)).doubleValue(), 0.001d);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testGetKpiDataHonoursProjectConfiguredRefinementRootCauses()
			throws ApplicationException {
		fieldMapping.setJiraRefinementRootCauseValuesKPI226(
				new ArrayList<>(Arrays.asList("Missing Acceptance Criteria", "Ambiguous AC")));
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						any(), anyString(), anyString(), anyString(), any()))
				.thenReturn(
						new ArrayList<>(
								Arrays.asList(
										taggedDefect("PROJ-1", "Missing Acceptance Criteria"),
										taggedDefect("PROJ-2", "Ambiguous AC"),
										taggedDefect("PROJ-3", "Coding"),
										taggedDefect("PROJ-4", "Coding"))));

		KpiElement result = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), buildTree());

		DataCount current = currentPeriod((List<DataCount>) result.getTrendValueList());
		assertEquals(50.0d, ((Number) current.getValue()).doubleValue(), 0.001d);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testGetKpiDataWithNoProductionDefectsStillEmitsAContinuousTrend()
			throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						any(), anyString(), anyString(), anyString(), any()))
				.thenReturn(new ArrayList<>());

		KpiElement result = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), buildTree());

		List<DataCount> periods = periodsOfFirstProject((List<DataCount>) result.getTrendValueList());
		assertEquals(12, periods.size());
		assertTrue(periods.stream().allMatch(dc -> ((Number) dc.getValue()).doubleValue() == 0d));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testGetKpiDataAttachesRootCauseDrillDownToEveryPeriod() throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						any(), anyString(), anyString(), anyString(), any()))
				.thenReturn(
						new ArrayList<>(
								Arrays.asList(
										taggedDefect("PROJ-1", "Refinement"),
										taggedDefect("PROJ-2", "Coding"),
										productionDefect("PROJ-3"))));

		KpiElement result = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), buildTree());

		List<DataCount> periods = periodsOfFirstProject((List<DataCount>) result.getTrendValueList());
		periods.forEach(period -> assertNotNull(period.getDrillDown()));

		Map<String, Long> breakdown =
				drillDownAsMap(currentPeriod((List<DataCount>) result.getTrendValueList()));
		assertEquals(Long.valueOf(1L), breakdown.get("Refinement"));
		assertEquals(Long.valueOf(1L), breakdown.get("Code"));
		assertEquals(Long.valueOf(1L), breakdown.get("Untagged"));
		assertEquals(Long.valueOf(0L), breakdown.get("Design"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testDrillDownCountsAlwaysSumUpToTheTotalDefectsOfThePeriod()
			throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						any(), anyString(), anyString(), anyString(), any()))
				.thenReturn(
						new ArrayList<>(
								Arrays.asList(
										taggedDefect("PROJ-1", "Refinement"),
										taggedDefect("PROJ-2", "Design"),
										taggedDefect("PROJ-3", "Upstream"),
										productionDefect("PROJ-4"))));

		KpiElement result = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), buildTree());

		periodsOfFirstProject((List<DataCount>) result.getTrendValueList())
				.forEach(
						period -> {
							long drillDownTotal =
									((List<RootCauseDrillDownValue>) period.getDrillDown())
											.stream().mapToLong(RootCauseDrillDownValue::count).sum();
							assertEquals(
									((Number) period.getHoverValue().get(HOVER_TOTAL)).longValue(), drillDownTotal);
						});
	}

	@Test
	public void testGetKpiDataSkipsDefectsWithUnparseableCreatedDate() throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		JiraIssue broken = productionDefect("PROJ-9");
		broken.setCreatedDate("garbage");
		when(jiraIssueRepository.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						any(), anyString(), anyString(), anyString(), any()))
				.thenReturn(new ArrayList<>(Collections.singletonList(broken)));

		KpiElement result = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), buildTree());

		assertNotNull(result.getTrendValueList());
	}

	// ------------------------------------------------------------------
	// Drill-down building / aggregation
	// ------------------------------------------------------------------

	@Test
	public void testBuildCategoryDrillDownKeepsAllCanonicalBucketsWithZeroCounts() {
		List<RootCauseDrillDownValue> drillDown =
				RefinementToDefectLinkageServiceImpl.buildCategoryDrillDown(new ArrayList<>());

		assertEquals(7, drillDown.size());
		assertTrue(drillDown.stream().allMatch(entry -> entry.count() == 0L));
		assertEquals("Refinement", drillDown.get(0).category());
	}

	@Test
	public void testBuildCategoryDrillDownHandlesNullInput() {
		assertFalse(RefinementToDefectLinkageServiceImpl.buildCategoryDrillDown(null).isEmpty());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testCalculateDrillDownValueSumsCountsAcrossProjects() {
		Object aggregated =
				service.calculateDrillDownValue(
						new ArrayList<>(
								Arrays.asList(
										List.of(
												new RootCauseDrillDownValue("Refinement", 3L),
												new RootCauseDrillDownValue("Code", 1L)),
										List.of(
												new RootCauseDrillDownValue("Refinement", 2L),
												new RootCauseDrillDownValue("Infra", 4L)))));

		Map<String, Long> merged = asMap((List<RootCauseDrillDownValue>) aggregated);
		assertEquals(Long.valueOf(5L), merged.get("Refinement"));
		assertEquals(Long.valueOf(1L), merged.get("Code"));
		assertEquals(Long.valueOf(4L), merged.get("Infra"));
		assertEquals(Long.valueOf(0L), merged.get("Design"));
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
										List.of("foreign", new RootCauseDrillDownValue("Refinement", 7L)))));

		Map<String, Long> merged = asMap((List<RootCauseDrillDownValue>) aggregated);
		assertEquals(Long.valueOf(7L), merged.get("Refinement"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testCalculateDrillDownValueWithNoInputReturnsAllZeroBuckets() {
		Map<String, Long> merged =
				asMap((List<RootCauseDrillDownValue>) service.calculateDrillDownValue(null));
		assertEquals(7, merged.size());
		assertTrue(merged.values().stream().allMatch(count -> count == 0L));
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
		when(jiraIssueRepository.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						any(), anyString(), anyString(), anyString(), any()))
				.thenReturn(
						new ArrayList<>(
								Arrays.asList(
										taggedDefect("PROJ-1", "Refinement"), taggedDefect("PROJ-2", "Coding"))));

		KpiElement result = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), buildTree());

		List<KPIExcelData> excelData = result.getExcelData();
		assertNotNull(excelData);
		assertEquals(2, excelData.size());
		assertEquals("Refinement", excelData.get(0).getRootCauseCategory());
		assertEquals("Code", excelData.get(1).getRootCauseCategory());
		assertEquals(List.of("Refinement"), excelData.get(0).getRootCause());
		assertNotNull(excelData.get(0).getDaysWeeks());
		assertEquals(
				KPIExcelColumn.REFINEMENT_TO_DEFECT_LINKAGE.getColumns(), result.getExcelColumns());
	}

	@Test
	public void testExcelDataIsEmptyForNonExcelRequests() throws ApplicationException {
		when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
		when(jiraIssueRepository.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						any(), anyString(), anyString(), anyString(), any()))
				.thenReturn(
						new ArrayList<>(Collections.singletonList(taggedDefect("PROJ-1", "Refinement"))));

		KpiElement result = service.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0), buildTree());

		assertNotNull(result.getExcelData());
		assertTrue(result.getExcelData().isEmpty());
		assertFalse(KPIExcelColumn.REFINEMENT_TO_DEFECT_LINKAGE.getColumns().isEmpty());
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
	private List<DataCount> periodsOfFirstProject(List<DataCount> trendValues) {
		return (List<DataCount>) trendValues.get(0).getValue();
	}

	/** The seeded trend runs oldest → newest, so the current period is the last entry. */
	private DataCount currentPeriod(List<DataCount> trendValues) {
		List<DataCount> periods = periodsOfFirstProject(trendValues);
		return periods.get(periods.size() - 1);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Long> drillDownAsMap(DataCount period) {
		return asMap((List<RootCauseDrillDownValue>) period.getDrillDown());
	}

	private Map<String, Long> asMap(List<RootCauseDrillDownValue> drillDown) {
		return drillDown.stream()
				.collect(
						Collectors.toMap(RootCauseDrillDownValue::category, RootCauseDrillDownValue::count));
	}

	private JiraIssue defect(String number) {
		JiraIssue issue = new JiraIssue();
		issue.setNumber(number);
		issue.setName(number + " summary");
		issue.setTypeName("Bug");
		issue.setStatus("Closed");
		issue.setPriority("P1");
		issue.setUrl("http://jira/" + number);
		issue.setCreatedDate(
				DateUtil.getTodayTime()
						.format(java.time.format.DateTimeFormatter.ofPattern(DateUtil.TIME_FORMAT)));
		issue.setBasicProjectConfigId(PROJECT_CONFIG_ID.toString());
		return issue;
	}

	private JiraIssue productionDefect(String number) {
		JiraIssue issue = defect(number);
		issue.setProductionDefect(true);
		return issue;
	}

	private JiraIssue taggedDefect(String number, String rootCause) {
		JiraIssue issue = productionDefect(number);
		issue.setRootCauseList(new ArrayList<>(Collections.singletonList(rootCause)));
		return issue;
	}
}
