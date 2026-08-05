/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 ******************************************************************************/
package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.speed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.request.chat.ChatGenerationRequest;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.parser.EpicHygieneKpiParser;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.ProjectFilter;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.dto.CycleTimeGroup;
import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneResult;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.repository.jira.EpicHygieneResultRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.service.recommendation.PromptService;
import com.publicissapient.kpidashboard.common.util.HygienePromptBuilder;

/**
 * Tests for {@link EpicHygieneKpiSlingshotServiceImpl}.
 *
 * <p>The service fans batches of Epics out to the LLM through a Spring-managed executor. To keep
 * the tests deterministic that executor is swapped for {@code Runnable::run} (same-thread) via
 * reflection, so {@link java.util.concurrent.CompletableFuture#supplyAsync(java.util.function.Supplier,
 * Executor)} runs synchronously.
 *
 * <p>The real {@link EpicHygieneKpiParser} is used rather than a mock: the parser is pure and its
 * arithmetic is exactly what the score factors are asserted against.
 */
@RunWith(MockitoJUnitRunner.class)
public class EpicHygieneKpiSlingshotServiceImplTest {

	private static final String PROJECT_CONFIG_ID = "6335363749794a18e8a4479b";

	/** Tracker id shape used by the Excel endpoint — this is what unlocks excelData. */
	private static final String EXCEL_TRACKER_ID = "Excel-4b224f13-9a7a-49c5-8e01-9012ad92bfcb";

	/** Tracker id shape used by a normal dashboard call. */
	private static final String DASHBOARD_TRACKER_ID = "Jira-4b224f13-9a7a-49c5-8e01-9012ad92bfcb";

	@Mock private JiraIssueRepository jiraIssueRepository;
	@Mock private EpicHygieneResultRepository epicHygieneResultRepository;
	@Mock private AiGatewayClient aiGatewayClient;
	@Mock private ConfigHelperService configHelperService;
	@Mock private CustomApiConfig customApiConfig;
	@Mock private PromptService promptService;
	@Mock private CacheService cacheService;

	@InjectMocks private EpicHygieneKpiSlingshotServiceImpl service;

	private ObjectId projectConfigId;
	private KpiRequest kpiRequest;
	private KpiElement kpiElement;
	private String ruleSetHash;

	// ---------------------------------------------------------------------
	// Fixture
	// ---------------------------------------------------------------------

	@Before
	public void setUp() {
		Executor synchronousExecutor = Runnable::run;
		injectField(service, "hygieneAiExecutor", synchronousExecutor);
		injectField(service, "objectMapper", new ObjectMapper());
		injectField(service, "epicHygieneKpiParser", new EpicHygieneKpiParser());
		injectField(service, "configHelperService", configHelperService);
		injectField(service, "customApiConfig", customApiConfig);
		injectField(service, "cacheService", cacheService);

		// Most assertions below are about the drill-down rows, which only the Excel path
		// produces — so default the request tracker to an Excel one. See
		// getKpiData_dashboardRequest_doesNotBuildExcelRows for the other branch.
		useTracker(EXCEL_TRACKER_ID);

		projectConfigId = new ObjectId(PROJECT_CONFIG_ID);

		kpiElement = new KpiElement();
		kpiElement.setKpiId(KPICode.EPIC_HYGIENE.getKpiId());

		kpiRequest = new KpiRequest();
		kpiRequest.setIds(new String[] {"project1"});
		kpiRequest.setLabel("PROJECT");
		kpiRequest.setLevel(4);
		kpiRequest.setKpiList(new ArrayList<>(Collections.singletonList(kpiElement)));

		lenient().when(customApiConfig.getSlingshotEpicHygieneMonths()).thenReturn(6);
		lenient().when(customApiConfig.getSlingshotEpicHygieneEpicCount()).thenReturn(50);
		lenient().when(customApiConfig.getSlingshotEpicHygieneBatchSize()).thenReturn(10);
		lenient().when(customApiConfig.getSlingshotEpicHygieneIssueTypes()).thenReturn(List.of("Epic"));
		lenient()
				.when(customApiConfig.getSlingshotEpicHygieneAnchorFields())
				.thenReturn(Arrays.asList("number", "name", "typeName", "status", "assigneeName"));

		lenient()
				.when(configHelperService.getFieldMapping(any(ObjectId.class)))
				.thenReturn(fieldMappingWith(defaultDimensions()));
		lenient().when(promptService.getEpicHygienePrompt(any(), any())).thenReturn("epic-hygiene-prompt");
		lenient()
				.when(epicHygieneResultRepository.findByBasicProjectConfigIdAndEpicKeyIn(anyString(), anyList()))
				.thenReturn(Collections.emptyList());

		ruleSetHash = HygienePromptBuilder.computeRuleSetHash(defaultDimensions(), new ObjectMapper());
	}

	// ---------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------

	private void injectField(Object target, String fieldName, Object value) {
		Class<?> clazz = target.getClass();
		while (clazz != null) {
			try {
				Field field = clazz.getDeclaredField(fieldName);
				field.setAccessible(true);
				field.set(target, value);
				return;
			} catch (NoSuchFieldException ignored) {
				clazz = clazz.getSuperclass();
			} catch (IllegalAccessException e) {
				throw new IllegalStateException(e);
			}
		}
		throw new IllegalStateException("Field not found: " + fieldName);
	}

	/** Points {@code getRequestTrackerId()} at the supplied tracker id. */
	private void useTracker(String trackerId) {
		lenient()
				.when(
						cacheService.getFromApplicationCache(
								Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
				.thenReturn(trackerId);
	}

	private CycleTimeGroup dimension(String label, String fieldName, String prompt) {
		CycleTimeGroup group = new CycleTimeGroup();
		group.setLabel(label);
		group.setFieldName(fieldName);
		group.setPrompt(prompt);
		return group;
	}

	private List<CycleTimeGroup> defaultDimensions() {
		return List.of(
				dimension("Business Clarity", "description", "Score the business problem and value"),
				dimension("Delivery Readiness", "assigneeName", "[2]: Score owner and milestones"));
	}

	private FieldMapping fieldMappingWith(List<CycleTimeGroup> dimensions) {
		FieldMapping fieldMapping = new FieldMapping();
		fieldMapping.setJiraFieldsSelectionKPI312(dimensions);
		return fieldMapping;
	}

	private Node createProjectNode() {
		Node node = new Node();
		node.setId("project1");
		node.setName("Test Project");
		node.setGroupName("PROJECT");
		node.setProjectFilter(new ProjectFilter("project1", "Test Project", projectConfigId));
		return node;
	}

	private TreeAggregatorDetail buildTree() {
		Node projectNode = createProjectNode();
		Map<String, List<Node>> mapOfProjects = new HashMap<>();
		mapOfProjects.put(
				CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, Collections.singletonList(projectNode));

		Map<String, Node> mapTmp = new HashMap<>();
		mapTmp.put(projectNode.getId(), projectNode);

		return new TreeAggregatorDetail(projectNode, new HashMap<>(), mapTmp, mapOfProjects);
	}

	private TreeAggregatorDetail buildTreeWithoutProject() {
		Node projectNode = createProjectNode();
		Map<String, Node> mapTmp = new HashMap<>();
		mapTmp.put(projectNode.getId(), projectNode);
		return new TreeAggregatorDetail(projectNode, new HashMap<>(), mapTmp, new HashMap<>());
	}

	private JiraIssue epic(String key, String name, String changeDate) {
		JiraIssue issue = new JiraIssue();
		issue.setNumber(key);
		issue.setName(name);
		issue.setTypeName("Epic");
		issue.setStatus("Functional Grooming");
		issue.setAssigneeName("Ada");
		issue.setUrl("https://jira/browse/" + key);
		issue.setChangeDate(changeDate);
		issue.setCreatedDate(changeDate);
		return issue;
	}

	private void mockEpics(List<JiraIssue> epics) {
		when(jiraIssueRepository.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						anySet(), anyString(), anyString(), anyString(), anySet()))
				.thenReturn(epics);
	}

	private void mockLlmResponse(String content) {
		ChatGenerationResponseDTO response = new ChatGenerationResponseDTO(content);
		when(aiGatewayClient.generate(any(ChatGenerationRequest.class))).thenReturn(response);
	}

	/** Builds an LLM payload scoring every supplied Epic key at {@code score} on both dimensions. */
	private String llmPayload(Map<String, Integer> scoreByEpicKey) {
		String elements =
				scoreByEpicKey.entrySet().stream()
						.map(
								entry ->
										("{\"epicKey\":\"%s\",\"epicName\":\"%s name\",\"status\":\"Functional Grooming\","
														+ "\"assignee\":\"Ada\",\"results\":["
														+ "{\"dimension\":\"Business Clarity\",\"field\":\"description\",\"weight\":1,\"score\":%d},"
														+ "{\"dimension\":\"Delivery Readiness\",\"field\":\"assigneeName\",\"weight\":2,\"score\":%d}],"
														+ "\"recommendations\":\"fix a | fix b | fix c\"}")
												.formatted(
														entry.getKey(), entry.getKey(), entry.getValue(), entry.getValue()))
						.collect(Collectors.joining(","));
		return "[" + elements + "]";
	}

	private EpicHygieneResult cachedResult(String epicKey, String hash, String changeDate, int score) {
		EpicHygieneResult result =
				EpicHygieneResult.builder()
						.basicProjectConfigId(PROJECT_CONFIG_ID)
						.epicKey(epicKey)
						.epicName(epicKey + " name")
						.ruleSetHash(hash)
						.epicChangeDate(changeDate)
						.build();
		result.setVerdict(new EpicHygieneKpiParser().parse(llmPayload(Map.of(epicKey, score))).get(0));
		return result;
	}

	// ---------------------------------------------------------------------
	// Simple contract
	// ---------------------------------------------------------------------

	@Test
	public void getQualifierType_isEpicHygiene() {
		assertEquals(KPICode.EPIC_HYGIENE.name(), service.getQualifierType());
	}

	@Test
	public void calculateKPIMetrics_alwaysReturnsZero() {
		assertEquals(Double.valueOf(0.0), service.calculateKPIMetrics(new HashMap<>()));
	}

	// ---------------------------------------------------------------------
	// fetchKPIDataFromDb
	// ---------------------------------------------------------------------

	@Test
	@SuppressWarnings("unchecked")
	public void fetchKPIDataFromDb_projectsConfiguredAnchorAndDimensionFields() {
		mockEpics(List.of(epic("EPIC-1", "One", "2026-07-01T00:00:00.0000000")));

		Map<String, Object> result =
				service.fetchKPIDataFromDb(
						Collections.singletonList(createProjectNode()), null, null, kpiRequest);

		List<JiraIssue> epics =
				(List<JiraIssue>) result.get(EpicHygieneKpiSlingshotServiceImpl.EPIC_ISSUES);
		assertEquals(1, epics.size());

		ArgumentCaptor<java.util.Set<String>> typesCaptor = ArgumentCaptor.forClass(java.util.Set.class);
		ArgumentCaptor<java.util.Set<String>> fieldsCaptor = ArgumentCaptor.forClass(java.util.Set.class);
		verify(jiraIssueRepository)
				.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						typesCaptor.capture(),
						anyString(),
						anyString(),
						anyString(),
						fieldsCaptor.capture());

		assertTrue(typesCaptor.getValue().contains("Epic"));
		// Anchor fields, the fields the dimensions reference and the always-on columns
		assertTrue(fieldsCaptor.getValue().containsAll(List.of("number", "name", "typeName", "status")));
		assertTrue(fieldsCaptor.getValue().contains("description"));
		assertTrue(fieldsCaptor.getValue().contains("assigneeName"));
		assertTrue(fieldsCaptor.getValue().containsAll(List.of("url", "changeDate", "createdDate")));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void fetchKPIDataFromDb_emptyLeafNodes_returnsEmptyPayload() {
		Map<String, Object> result =
				service.fetchKPIDataFromDb(Collections.emptyList(), null, null, kpiRequest);

		assertTrue(
				((List<JiraIssue>) result.get(EpicHygieneKpiSlingshotServiceImpl.EPIC_ISSUES)).isEmpty());
		verify(jiraIssueRepository, never())
				.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						anySet(), anyString(), anyString(), anyString(), anySet());
	}

	@Test
	public void fetchKPIDataFromDb_missingFieldMapping_stillQueriesTheAnchorFields() {
		when(configHelperService.getFieldMapping(any(ObjectId.class))).thenReturn(null);
		mockEpics(Collections.emptyList());

		service.fetchKPIDataFromDb(
				Collections.singletonList(createProjectNode()), null, null, kpiRequest);

		verify(jiraIssueRepository)
				.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						anySet(), anyString(), anyString(), anyString(), anySet());
	}

	// ---------------------------------------------------------------------
	// getKpiData — happy paths
	// ---------------------------------------------------------------------

	@Test
	public void getKpiData_noProjectNode_returnsElementUntouched() throws Exception {
		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTreeWithoutProject());

		assertNull(result.getExcelData());
		assertNull(result.getScoreFactor());
		verify(jiraIssueRepository, never())
				.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						anySet(), anyString(), anyString(), anyString(), anySet());
	}

	@Test
	public void getKpiData_noEpicsInWindow_publishesEmptyResultWithZeroScores() throws Exception {
		mockEpics(Collections.emptyList());

		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTree());

		assertTrue(result.getExcelData().isEmpty());
		assertEquals(KPIExcelColumn.EPIC_HYGIENE.getColumns(), result.getExcelColumns());
		assertEquals(Integer.valueOf(0), result.getScoreFactor());
		assertEquals(Integer.valueOf(0), result.getValidScoreFactor());
		assertEquals(Double.valueOf(0d), result.getProjectScore());
		verify(aiGatewayClient, never()).generate(any(ChatGenerationRequest.class));
	}

	@Test
	public void getKpiData_neverPublishesATrendLine() throws Exception {
		mockEpics(List.of(epic("EPIC-1", "One", "2026-07-01T00:00:00.0000000")));
		mockLlmResponse(llmPayload(Map.of("EPIC-1", 90)));

		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTree());

		assertNull(result.getTrendValueList());
	}

	/**
	 * This KPI has no trend line, so its drill-down rows are the payload and must be published on a
	 * plain dashboard call too — not only when the Excel endpoint re-runs it.
	 */
	@Test
	public void getKpiData_dashboardRequest_stillPublishesExcelRows() throws Exception {
		useTracker(DASHBOARD_TRACKER_ID);
		mockEpics(List.of(epic("EPIC-1", "One", "2026-07-01T00:00:00.0000000")));
		mockLlmResponse(llmPayload(Map.of("EPIC-1", 90)));

		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTree());

		assertNotNull(result.getExcelData());
		assertEquals(1, result.getExcelData().size());
		assertEquals(KPIExcelColumn.EPIC_HYGIENE.getColumns(), result.getExcelColumns());
		assertEquals(Integer.valueOf(1), result.getScoreFactor());
		assertEquals(Integer.valueOf(1), result.getValidScoreFactor());
		assertEquals(Double.valueOf(90d), result.getProjectScore());
	}

	@Test
	public void getKpiData_excelRequest_buildsExcelRows() throws Exception {
		useTracker(EXCEL_TRACKER_ID);
		mockEpics(List.of(epic("EPIC-1", "One", "2026-07-01T00:00:00.0000000")));
		mockLlmResponse(llmPayload(Map.of("EPIC-1", 90)));

		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTree());

		assertNotNull(result.getExcelData());
		assertEquals(1, result.getExcelData().size());
	}

	@Test
	public void getKpiData_cacheMiss_callsLlmPersistsAndPublishesScoreFactors() throws Exception {
		mockEpics(
				List.of(
						epic("EPIC-1", "One", "2026-07-01T00:00:00.0000000"),
						epic("EPIC-2", "Two", "2026-07-02T00:00:00.0000000")));
		// EPIC-1 scores 90 on both dimensions -> READY; EPIC-2 scores 40 -> NOT READY
		Map<String, Integer> scores = new java.util.LinkedHashMap<>();
		scores.put("EPIC-1", 90);
		scores.put("EPIC-2", 40);
		mockLlmResponse(llmPayload(scores));

		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTree());

		assertEquals(2, result.getExcelData().size());
		assertEquals(Integer.valueOf(2), result.getScoreFactor());
		assertEquals(Integer.valueOf(1), result.getValidScoreFactor());
		assertEquals(Double.valueOf(65d), result.getProjectScore());
		assertEquals(Double.valueOf(65d), result.getValue());

		verify(aiGatewayClient, times(1)).generate(any(ChatGenerationRequest.class));

		ArgumentCaptor<List<EpicHygieneResult>> savedCaptor = ArgumentCaptor.forClass(List.class);
		verify(epicHygieneResultRepository).saveAll(savedCaptor.capture());
		List<EpicHygieneResult> saved = savedCaptor.getValue();
		assertEquals(2, saved.size());
		saved.forEach(
				stored -> {
					assertEquals(PROJECT_CONFIG_ID, stored.getBasicProjectConfigId());
					assertEquals(ruleSetHash, stored.getRuleSetHash());
					assertNotNull(stored.getComputedAt());
					assertNotNull(stored.getVerdict());
				});
	}

	@Test
	public void getKpiData_excelRowsCarryEpicMetadataAndPerDimensionScores() throws Exception {
		mockEpics(List.of(epic("EPIC-1", "One", "2026-07-01T00:00:00.0000000")));
		mockLlmResponse(llmPayload(Map.of("EPIC-1", 90)));

		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTree());

		KPIExcelData row = result.getExcelData().get(0);
		assertEquals("https://jira/browse/EPIC-1", row.getEpicID().get("EPIC-1"));
		assertEquals("EPIC-1 name", row.getEpicName());
		assertEquals("Functional Grooming", row.getStatus());
		assertEquals("Ada", row.getAssignee());
		assertEquals(Integer.valueOf(90), row.getHygieneScore());
		assertEquals("READY", row.getOverallStatus());
		assertEquals("fix a | fix b | fix c", row.getRecommendations());
		assertEquals("90", row.getGroupMap().get("Business Clarity"));
		assertEquals("90", row.getGroupMap().get("Delivery Readiness"));
	}

	// ---------------------------------------------------------------------
	// Caching
	// ---------------------------------------------------------------------

	@Test
	public void getKpiData_freshCache_servesFromDbWithoutCallingTheLlm() throws Exception {
		String changeDate = "2026-07-01T00:00:00.0000000";
		mockEpics(List.of(epic("EPIC-1", "One", changeDate)));
		when(epicHygieneResultRepository.findByBasicProjectConfigIdAndEpicKeyIn(anyString(), anyList()))
				.thenReturn(List.of(cachedResult("EPIC-1", ruleSetHash, changeDate, 90)));

		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTree());

		assertEquals(1, result.getExcelData().size());
		assertEquals(Integer.valueOf(1), result.getValidScoreFactor());
		verify(aiGatewayClient, never()).generate(any(ChatGenerationRequest.class));
		verify(epicHygieneResultRepository, never()).saveAll(anyList());
	}

	@Test
	public void getKpiData_staleRuleSetHash_reEvaluatesWithTheLlm() throws Exception {
		String changeDate = "2026-07-01T00:00:00.0000000";
		mockEpics(List.of(epic("EPIC-1", "One", changeDate)));
		when(epicHygieneResultRepository.findByBasicProjectConfigIdAndEpicKeyIn(anyString(), anyList()))
				.thenReturn(List.of(cachedResult("EPIC-1", "an-outdated-hash", changeDate, 90)));
		mockLlmResponse(llmPayload(Map.of("EPIC-1", 30)));

		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTree());

		verify(aiGatewayClient, times(1)).generate(any(ChatGenerationRequest.class));
		assertEquals(Integer.valueOf(0), result.getValidScoreFactor());
		assertEquals(Double.valueOf(30d), result.getProjectScore());
	}

	@Test
	public void getKpiData_epicEditedSinceLastRun_reEvaluatesWithTheLlm() throws Exception {
		mockEpics(List.of(epic("EPIC-1", "One", "2026-07-09T00:00:00.0000000")));
		when(epicHygieneResultRepository.findByBasicProjectConfigIdAndEpicKeyIn(anyString(), anyList()))
				.thenReturn(
						List.of(cachedResult("EPIC-1", ruleSetHash, "2026-07-01T00:00:00.0000000", 90)));
		mockLlmResponse(llmPayload(Map.of("EPIC-1", 95)));

		service.getKpiData(kpiRequest, kpiElement, buildTree());

		verify(aiGatewayClient, times(1)).generate(any(ChatGenerationRequest.class));
	}

	@Test
	public void getKpiData_mixedCacheState_onlyStaleEpicsGoToTheLlm() throws Exception {
		String changeDate = "2026-07-01T00:00:00.0000000";
		mockEpics(List.of(epic("EPIC-1", "One", changeDate), epic("EPIC-2", "Two", changeDate)));
		when(epicHygieneResultRepository.findByBasicProjectConfigIdAndEpicKeyIn(anyString(), anyList()))
				.thenReturn(List.of(cachedResult("EPIC-1", ruleSetHash, changeDate, 90)));
		mockLlmResponse(llmPayload(Map.of("EPIC-2", 60)));

		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTree());

		verify(aiGatewayClient, times(1)).generate(any(ChatGenerationRequest.class));
		assertEquals(2, result.getExcelData().size());
		assertEquals(Integer.valueOf(2), result.getScoreFactor());
		assertEquals(Integer.valueOf(1), result.getValidScoreFactor());
		assertEquals(Double.valueOf(75d), result.getProjectScore());
	}

	// ---------------------------------------------------------------------
	// Batching & capping
	// ---------------------------------------------------------------------

	@Test
	public void getKpiData_moreEpicsThanBatchSize_dispatchesMultipleLlmCalls() throws Exception {
		when(customApiConfig.getSlingshotEpicHygieneBatchSize()).thenReturn(2);
		List<JiraIssue> epics = new ArrayList<>();
		Map<String, Integer> scores = new java.util.LinkedHashMap<>();
		for (int i = 1; i <= 5; i++) {
			epics.add(epic("EPIC-" + i, "Epic " + i, "2026-07-0" + i + "T00:00:00.0000000"));
			scores.put("EPIC-" + i, 80);
		}
		mockEpics(epics);
		mockLlmResponse(llmPayload(scores));

		service.getKpiData(kpiRequest, kpiElement, buildTree());

		// 5 epics / batch of 2 => 3 batches
		verify(aiGatewayClient, times(3)).generate(any(ChatGenerationRequest.class));
	}

	@Test
	public void getKpiData_moreEpicsThanCap_evaluatesOnlyTheMostRecentlyUpdated() throws Exception {
		when(customApiConfig.getSlingshotEpicHygieneEpicCount()).thenReturn(2);
		mockEpics(
				List.of(
						epic("EPIC-OLD", "Old", "2026-01-01T00:00:00.0000000"),
						epic("EPIC-MID", "Mid", "2026-05-01T00:00:00.0000000"),
						epic("EPIC-NEW", "New", "2026-07-01T00:00:00.0000000")));
		Map<String, Integer> scores = new java.util.LinkedHashMap<>();
		scores.put("EPIC-NEW", 80);
		scores.put("EPIC-MID", 80);
		mockLlmResponse(llmPayload(scores));

		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTree());

		assertEquals(Integer.valueOf(2), result.getScoreFactor());
		List<String> reportedEpics =
				result.getExcelData().stream()
						.flatMap(row -> row.getEpicID().keySet().stream())
						.sorted()
						.toList();
		assertEquals(List.of("EPIC-MID", "EPIC-NEW"), reportedEpics);
	}

	// ---------------------------------------------------------------------
	// Failure handling
	// ---------------------------------------------------------------------

	@Test
	public void getKpiData_blankLlmResponse_servesMockDataAndPersistsNothing() throws Exception {
		mockEpics(List.of(epic("EPIC-1", "One", "2026-07-01T00:00:00.0000000")));
		mockLlmResponse("   ");

		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTree());

		assertTrue(result.getExcelData().size() > 0);
		verify(epicHygieneResultRepository, never()).saveAll(anyList());
	}

	@Test
	public void getKpiData_gatewayThrows_servesMockDataAndPersistsNothing() throws Exception {
		mockEpics(List.of(epic("EPIC-1", "One", "2026-07-01T00:00:00.0000000")));
		when(aiGatewayClient.generate(any(ChatGenerationRequest.class)))
				.thenThrow(new IllegalStateException("gateway down"));

		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTree());

		assertTrue(result.getExcelData().size() > 0);
		verify(epicHygieneResultRepository, never()).saveAll(anyList());
	}

	@Test
	public void getKpiData_mockIsServedOnlyOnceAcrossBatches() throws Exception {
		when(customApiConfig.getSlingshotEpicHygieneBatchSize()).thenReturn(1);
		mockEpics(
				List.of(
						epic("EPIC-1", "One", "2026-07-01T00:00:00.0000000"),
						epic("EPIC-2", "Two", "2026-07-02T00:00:00.0000000")));
		when(aiGatewayClient.generate(any(ChatGenerationRequest.class)))
				.thenThrow(new IllegalStateException("gateway down"));

		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTree());

		int mockRowCount =
				new EpicHygieneKpiParser()
						.parse(EpicHygieneKpiSlingshotServiceImpl.MOCK_EPIC_HYGIENE_RESPONSE_JSON)
						.size();
		assertEquals(mockRowCount, result.getExcelData().size());
	}

	@Test
	public void getKpiData_llmInventsAnEpicKey_dropsItFromTheReportAndTheDb() throws Exception {
		mockEpics(List.of(epic("EPIC-1", "One", "2026-07-01T00:00:00.0000000")));
		Map<String, Integer> scores = new java.util.LinkedHashMap<>();
		scores.put("EPIC-1", 90);
		scores.put("EPIC-DOES-NOT-EXIST", 10);
		mockLlmResponse(llmPayload(scores));

		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTree());

		assertEquals(1, result.getExcelData().size());
		assertTrue(result.getExcelData().get(0).getEpicID().containsKey("EPIC-1"));

		ArgumentCaptor<List<EpicHygieneResult>> savedCaptor = ArgumentCaptor.forClass(List.class);
		verify(epicHygieneResultRepository).saveAll(savedCaptor.capture());
		assertEquals(1, savedCaptor.getValue().size());
		assertEquals("EPIC-1", savedCaptor.getValue().get(0).getEpicKey());
	}

	@Test
	public void getKpiData_unparseableLlmResponse_yieldsNoRowsButDoesNotFail() throws Exception {
		mockEpics(List.of(epic("EPIC-1", "One", "2026-07-01T00:00:00.0000000")));
		mockLlmResponse("I am afraid I cannot help with that.");

		KpiElement result = service.getKpiData(kpiRequest, kpiElement, buildTree());

		assertTrue(result.getExcelData().isEmpty());
		assertEquals(Integer.valueOf(1), result.getScoreFactor());
		assertEquals(Integer.valueOf(0), result.getValidScoreFactor());
		assertEquals(Double.valueOf(0d), result.getProjectScore());
		verify(epicHygieneResultRepository, never()).saveAll(anyList());
	}
}

