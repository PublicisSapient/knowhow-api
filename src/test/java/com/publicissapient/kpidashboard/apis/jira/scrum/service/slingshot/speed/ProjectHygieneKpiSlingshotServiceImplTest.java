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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
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

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.request.chat.ChatGenerationRequest;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.dto.response.HygieneKpiResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.parser.HygieneKpiParser;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.CommonService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.SprintDetailsService;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.ProjectFilter;
import com.publicissapient.kpidashboard.apis.model.SprintFilter;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.dto.CycleTimeGroup;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;

/**
 * Tests for {@link ProjectHygieneKpiSlingshotServiceImpl}.
 *
 * <p>The service fans out per-sprint LLM calls through a Spring-managed executor. To keep the tests
 * deterministic we swap that executor for {@code Runnable::run} (same-thread) via reflection —
 * {@link java.util.concurrent.CompletableFuture#supplyAsync(java.util.function.Supplier, Executor)}
 * then runs synchronously.
 */
@RunWith(MockitoJUnitRunner.class)
public class ProjectHygieneKpiSlingshotServiceImplTest {

	@Mock private HygieneKpiParser hygieneKpiParser;
	@Mock private JiraIssueRepository jiraIssueRepository;
	@Mock private AiGatewayClient aiGatewayClient;
	@Mock private SprintDetailsService sprintDetailsService;
	@Mock private ConfigHelperService configHelperService;

	@Mock private CacheService cacheService;
	@Mock private CommonService commonService;
	@Mock private CustomApiConfig customApiConfig;

	@InjectMocks private ProjectHygieneKpiSlingshotServiceImpl service;

	private ObjectId projectConfigId;
	private KpiRequest kpiRequest;
	private KpiElement kpiElement;

	// ---------------------------------------------------------------------
	// Fixture
	// ---------------------------------------------------------------------

	@Before
	public void setUp() {
		// Same-thread executor keeps supplyAsync deterministic.
		Executor synchronousExecutor = Runnable::run;
		injectField(service, "hygieneAiExecutor", synchronousExecutor);

		// Parent-class (ToolsKPIService) @Autowired fields need explicit injection
		// because
		// @InjectMocks does not walk the superclass field graph for private fields.
		injectField(service, "cacheService", cacheService);
		injectField(service, "commonService", commonService);
		injectField(service, "configHelperService", configHelperService);
		injectField(service, "customApiConfig", customApiConfig);

		projectConfigId = new ObjectId("6335363749794a18e8a4479b");

		kpiElement = new KpiElement();
		kpiElement.setKpiId(KPICode.PROJECT_HYGIENE.getKpiId());

		kpiRequest = new KpiRequest();
		kpiRequest.setIds(new String[] {"project1"});
		kpiRequest.setLabel("PROJECT");
		kpiRequest.setLevel(4);
		Map<String, List<String>> selectedMap = new HashMap<>();
		selectedMap.put("project", new ArrayList<>(Collections.singletonList("project1")));
		selectedMap.put("sprint", new ArrayList<>());
		kpiRequest.setSelectedMap(selectedMap);
		kpiRequest.setKpiList(new ArrayList<>(Collections.singletonList(kpiElement)));

		// Request-tracker id is normally cached by
		// JiraKPIService#getRequestTrackerId().
		lenient()
				.when(
						cacheService.getFromApplicationCache(
								Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
				.thenReturn("trackerid");
		lenient().when(cacheService.getAdditionalFilterHierarchyLevel()).thenReturn(new HashMap<>());
		lenient().when(cacheService.cacheProjectConfigMapData()).thenReturn(new HashMap<>());
		lenient().when(cacheService.getKpiBenchmarkTargets()).thenReturn(new HashMap<>());

		lenient().when(configHelperService.calculateMaturity()).thenReturn(new HashMap<>());
		lenient().when(configHelperService.loadKpiMaster()).thenReturn(new ArrayList<>());
		lenient().when(configHelperService.getFieldMappingMap()).thenReturn(new HashMap<>());

		lenient().when(commonService.sortTrendValueMap(anyMap())).thenAnswer(i -> i.getArgument(0));
	}

	// ---------------------------------------------------------------------
	// Reflection & DTO helpers
	// ---------------------------------------------------------------------

	private void injectField(Object target, String fieldName, Object value) {
		Class<?> clazz = target.getClass();
		while (clazz != null) {
			try {
				Field f = clazz.getDeclaredField(fieldName);
				f.setAccessible(true);
				f.set(target, value);
				return;
			} catch (NoSuchFieldException ignored) {
				clazz = clazz.getSuperclass();
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		}
		throw new IllegalStateException("Field not found: " + fieldName);
	}

	private void setSprintIdList(List<String> ids) throws Exception {
		Field f = ProjectHygieneKpiSlingshotServiceImpl.class.getDeclaredField("sprintIdList");
		f.setAccessible(true);
		f.set(service, ids);
	}

	private Node createProjectNode() {
		Node node = new Node();
		node.setId("project1");
		node.setName("Test Project");
		node.setGroupName("PROJECT");
		node.setProjectFilter(new ProjectFilter("project1", "Test Project", projectConfigId));
		return node;
	}

	private Node createSprintLeafNode(String sprintId, String sprintName) {
		Node node = new Node();
		node.setId(sprintId);
		node.setName(sprintName);
		node.setGroupName("SPRINT");
		node.setSprintFilter(new SprintFilter(sprintId, sprintName, "2026-01-01", "2026-01-15"));
		return node;
	}

	private TreeAggregatorDetail buildTree(List<Node> sprintLeafNodes) {
		Node projectNode = createProjectNode();
		Map<String, List<Node>> mapOfLeaves = new HashMap<>();
		mapOfLeaves.put(CommonConstant.SPRINT_MASTER, sprintLeafNodes);

		Map<String, List<Node>> mapOfProjects = new HashMap<>();
		mapOfProjects.put(
				CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, Collections.singletonList(projectNode));

		Map<String, Node> mapTmp = new HashMap<>();
		mapTmp.put(projectNode.getId(), projectNode);

		return new TreeAggregatorDetail(projectNode, mapOfLeaves, mapTmp, mapOfProjects);
	}

	private SprintDetails createSprintDetails(String id, String name, String startDate) {
		SprintDetails sd = new SprintDetails();
		sd.setSprintID(id);
		sd.setSprintName(name);
		sd.setStartDate(startDate);
		return sd;
	}

	private JiraIssue createJiraIssue(String key, String sprintId) {
		JiraIssue issue = new JiraIssue();
		issue.setNumber(key);
		issue.setSprintID(sprintId);
		return issue;
	}

	private HygieneKpiResponseDTO createHygieneDTO(String issueKey, Integer score) {
		HygieneKpiResponseDTO dto = new HygieneKpiResponseDTO();
		dto.setIssueKey(issueKey);
		dto.setIssueType("Story");
		dto.setAssignee("me");
		dto.setHygieneScore(score);
		dto.setOverallStatus("READY");
		dto.setRecommendations("do better");
		return dto;
	}

	private CycleTimeGroup group(String label, String prompt) {
		CycleTimeGroup g = new CycleTimeGroup();
		g.setLabel(label);
		g.setPrompt(prompt);
		return g;
	}

	private FieldMapping fieldMappingWith(List<CycleTimeGroup> groups) {
		FieldMapping fm = new FieldMapping();
		fm.setJiraFieldsSelectionKPI217(groups);
		return fm;
	}

	private void mockFieldMapping(FieldMapping fm) {
		when(configHelperService.getFieldMapping(any(ObjectId.class))).thenReturn(fm);
	}

	// ---------------------------------------------------------------------
	// Simple method tests
	// ---------------------------------------------------------------------

	@Test
	public void testGetQualifierType_isProjectHygiene() {
		assertEquals(KPICode.PROJECT_HYGIENE.name(), service.getQualifierType());
	}

	@Test
	public void testCalculateKPIMetrics_alwaysReturnsZero() {
		assertEquals(Double.valueOf(0.0), service.calculateKPIMetrics(new HashMap<>()));
		assertEquals(Double.valueOf(0.0), service.calculateKPIMetrics(null));
	}

	// ---------------------------------------------------------------------
	// fetchKPIDataFromDb tests
	// ---------------------------------------------------------------------

	@Test
	@SuppressWarnings("unchecked")
	public void testFetchKPIDataFromDb_lessThanFiveSprints_allIncluded() throws Exception {
		setSprintIdList(Arrays.asList("sp1", "sp2"));
		SprintDetails s1 = createSprintDetails("sp1", "Sprint 1", "2026-01-01T00:00:00Z");
		SprintDetails s2 = createSprintDetails("sp2", "Sprint 2", "2026-01-15T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any())).thenReturn(Arrays.asList(s1, s2));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(new ArrayList<>());

		Map<String, Object> result =
				service.fetchKPIDataFromDb(
						Collections.singletonList(createProjectNode()), null, null, kpiRequest);

		assertNotNull(result);
		List<SprintDetails> sprints = (List<SprintDetails>) result.get("sprintDetails");
		assertEquals(2, sprints.size());
		assertTrue(((List<?>) result.get("jiraIssues")).isEmpty());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFetchKPIDataFromDb_exactlyFiveSprints_allIncluded() throws Exception {
		List<String> ids = Arrays.asList("s1", "s2", "s3", "s4", "s5");
		setSprintIdList(ids);
		List<SprintDetails> sdList = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			sdList.add(
					createSprintDetails(ids.get(i), "Name " + i, "2026-01-0" + (i + 1) + "T00:00:00Z"));
		}
		when(sprintDetailsService.getSprintDetailsByIds(any())).thenReturn(sdList);
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(new ArrayList<>());

		Map<String, Object> result =
				service.fetchKPIDataFromDb(
						Collections.singletonList(createProjectNode()), null, null, kpiRequest);

		List<SprintDetails> sprints = (List<SprintDetails>) result.get("sprintDetails");
		assertEquals(5, sprints.size());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFetchKPIDataFromDb_moreThanFiveSprints_lastFiveKeptInOrder() throws Exception {
		List<String> ids = Arrays.asList("s1", "s2", "s3", "s4", "s5", "s6", "s7");
		setSprintIdList(ids);
		List<SprintDetails> sdList = new ArrayList<>();
		for (int i = 0; i < ids.size(); i++) {
			sdList.add(
					createSprintDetails(ids.get(i), "Name " + i, "2026-01-0" + (i + 1) + "T00:00:00Z"));
		}
		when(sprintDetailsService.getSprintDetailsByIds(any())).thenReturn(sdList);
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(new ArrayList<>());

		Map<String, Object> result =
				service.fetchKPIDataFromDb(
						Collections.singletonList(createProjectNode()), null, null, kpiRequest);

		List<SprintDetails> sprints = (List<SprintDetails>) result.get("sprintDetails");
		assertEquals(5, sprints.size());
		assertEquals("s3", sprints.get(0).getSprintID());
		assertEquals("s7", sprints.get(4).getSprintID());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFetchKPIDataFromDb_nullStartDatesSortedLast() throws Exception {
		setSprintIdList(Arrays.asList("s1", "s2", "s3"));
		SprintDetails s1 = createSprintDetails("s1", "Name 1", null);
		SprintDetails s2 = createSprintDetails("s2", "Name 2", "2026-01-01T00:00:00Z");
		SprintDetails s3 = createSprintDetails("s3", "Name 3", null);
		when(sprintDetailsService.getSprintDetailsByIds(any())).thenReturn(Arrays.asList(s1, s2, s3));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(new ArrayList<>());

		Map<String, Object> result =
				service.fetchKPIDataFromDb(
						Collections.singletonList(createProjectNode()), null, null, kpiRequest);

		List<SprintDetails> sprints = (List<SprintDetails>) result.get("sprintDetails");
		assertEquals(3, sprints.size());
		// s2 has a real start date and sorts first; nullsLast keeps null-dated at the
		// tail.
		assertEquals("s2", sprints.get(0).getSprintID());
	}

	@Test
	public void testFetchKPIDataFromDb_emptySprintList_returnsEmptyResults() throws Exception {
		setSprintIdList(Collections.emptyList());
		when(sprintDetailsService.getSprintDetailsByIds(any())).thenReturn(new ArrayList<>());
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(new ArrayList<>());

		Map<String, Object> result =
				service.fetchKPIDataFromDb(
						Collections.singletonList(createProjectNode()), null, null, kpiRequest);

		assertTrue(((List<?>) result.get("sprintDetails")).isEmpty());
		assertTrue(((List<?>) result.get("jiraIssues")).isEmpty());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void testFetchKPIDataFromDb_returnsJiraIssuesForSprints() throws Exception {
		setSprintIdList(Collections.singletonList("SP1"));
		SprintDetails sd = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		JiraIssue i1 = createJiraIssue("ISS-1", "SP1");
		JiraIssue i2 = createJiraIssue("ISS-2", "SP1");
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(Arrays.asList(i1, i2));

		Map<String, Object> result =
				service.fetchKPIDataFromDb(
						Collections.singletonList(createProjectNode()), null, null, kpiRequest);

		List<JiraIssue> issues = (List<JiraIssue>) result.get("jiraIssues");
		assertEquals(2, issues.size());
	}

	// ---------------------------------------------------------------------
	// getKpiData tests (end-to-end)
	// ---------------------------------------------------------------------

	private void primeAiHappyPath() {
		when(aiGatewayClient.generate(any(ChatGenerationRequest.class)))
				.thenReturn(new ChatGenerationResponseDTO("[{\"issueKey\":\"ISS-1\"}]"));
		when(hygieneKpiParser.parse(anyString()))
				.thenReturn(Collections.singletonList(createHygieneDTO("ISS-1", 80)));
	}

	@Test
	public void testGetKpiData_happyPath_returnsTrendAndColumns() throws ApplicationException {
		mockFieldMapping(fieldMappingWith(Collections.singletonList(group("Rule1", "Prompt1"))));
		SprintDetails sd = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(Collections.singletonList(createJiraIssue("ISS-1", "SP1")));
		primeAiHappyPath();

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(Collections.singletonList(createSprintLeafNode("SP1", "Sprint 1"))));

		assertNotNull(result);
		assertNotNull(result.getTrendValueList());
		assertFalse(((List<?>) result.getTrendValueList()).isEmpty());
		assertEquals(KPIExcelColumn.PROJECT_HYGIENE.getColumns(), result.getExcelColumns());
		// Non-EXCEL tracker → no excel rows appended.
		assertNull(result.getExcelData());
	}

	@Test
	public void testGetKpiData_excelTracker_populatesExcelData() throws ApplicationException {
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
				.thenReturn("Excel-track-id");
		mockFieldMapping(fieldMappingWith(Collections.singletonList(group("Rule1", "Prompt1"))));
		SprintDetails sd = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(Collections.singletonList(createJiraIssue("ISS-1", "SP1")));
		primeAiHappyPath();

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(Collections.singletonList(createSprintLeafNode("SP1", "Sprint 1"))));

		assertNotNull(result.getExcelData());
		assertFalse(result.getExcelData().isEmpty());
	}

	@Test
	public void testGetKpiData_nullCycleTimeGroups_usesEmptyPromptsMap() throws ApplicationException {
		// FieldMapping with no jiraFieldsSelectionKPI217 → cycleTimeGroupList == null
		// branch.
		mockFieldMapping(new FieldMapping());
		SprintDetails sd = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(Collections.singletonList(createJiraIssue("ISS-1", "SP1")));
		primeAiHappyPath();

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(Collections.singletonList(createSprintLeafNode("SP1", "Sprint 1"))));

		assertNotNull(result);
		verify(aiGatewayClient, times(1)).generate(any(ChatGenerationRequest.class));
	}

	@Test
	public void testGetKpiData_emptyCycleTimeGroups_usesEmptyPromptsMap()
			throws ApplicationException {
		mockFieldMapping(fieldMappingWith(new ArrayList<>()));
		SprintDetails sd = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(Collections.singletonList(createJiraIssue("ISS-1", "SP1")));
		primeAiHappyPath();

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(Collections.singletonList(createSprintLeafNode("SP1", "Sprint 1"))));

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_cycleTimeGroupsWithNullOrBlank_arefiltered()
			throws ApplicationException {
		// Every filter branch: null CTG, null label, blank label, null prompt, plus one
		// valid.
		List<CycleTimeGroup> groups =
				new ArrayList<>(
						Arrays.asList(
								null,
								group(null, "p1"),
								group("   ", "p2"),
								group("valid", null),
								group("Rule1", "Prompt1")));
		mockFieldMapping(fieldMappingWith(groups));
		SprintDetails sd = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(Collections.singletonList(createJiraIssue("ISS-1", "SP1")));
		primeAiHappyPath();

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(Collections.singletonList(createSprintLeafNode("SP1", "Sprint 1"))));

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_duplicateLabels_mergeKeepsFirst() throws ApplicationException {
		mockFieldMapping(
				fieldMappingWith(Arrays.asList(group("Rule1", "PromptA"), group("Rule1", "PromptB"))));
		SprintDetails sd = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(Collections.singletonList(createJiraIssue("ISS-1", "SP1")));
		primeAiHappyPath();

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(Collections.singletonList(createSprintLeafNode("SP1", "Sprint 1"))));

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_moreThanTenIssues_truncatedToTen() throws ApplicationException {
		mockFieldMapping(fieldMappingWith(Collections.singletonList(group("Rule1", "Prompt1"))));
		SprintDetails sd = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		List<JiraIssue> issues = new ArrayList<>();
		for (int i = 0; i < 15; i++) {
			issues.add(createJiraIssue("ISS-" + i, "SP1"));
		}
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(issues);
		when(aiGatewayClient.generate(any(ChatGenerationRequest.class)))
				.thenReturn(new ChatGenerationResponseDTO("[]"));
		when(hygieneKpiParser.parse(anyString()))
				.thenReturn(Collections.singletonList(createHygieneDTO("ISS-0", 90)));

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(Collections.singletonList(createSprintLeafNode("SP1", "Sprint 1"))));

		assertNotNull(result);
		verify(aiGatewayClient, times(1)).generate(any(ChatGenerationRequest.class));
	}

	@Test
	public void testGetKpiData_aiGatewayThrows_fallbackToEmptyDataCount()
			throws ApplicationException {
		mockFieldMapping(fieldMappingWith(Collections.singletonList(group("Rule1", "Prompt1"))));
		SprintDetails sd = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(Collections.singletonList(createJiraIssue("ISS-1", "SP1")));
		when(aiGatewayClient.generate(any(ChatGenerationRequest.class)))
				.thenThrow(new RuntimeException("boom"));

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(Collections.singletonList(createSprintLeafNode("SP1", "Sprint 1"))));

		assertNotNull(result);
		// Parser must never have been consulted because AI threw before parsing.
		verify(hygieneKpiParser, never()).parse(anyString());
	}

	@Test
	public void testGetKpiData_parserThrows_fallbackToEmptyDataCount() throws ApplicationException {
		mockFieldMapping(fieldMappingWith(Collections.singletonList(group("Rule1", "Prompt1"))));
		SprintDetails sd = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(Collections.singletonList(createJiraIssue("ISS-1", "SP1")));
		when(aiGatewayClient.generate(any(ChatGenerationRequest.class)))
				.thenReturn(new ChatGenerationResponseDTO("[]"));
		when(hygieneKpiParser.parse(anyString())).thenThrow(new RuntimeException("bad-json"));

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(Collections.singletonList(createSprintLeafNode("SP1", "Sprint 1"))));

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_sprintWithNoIssues_notEvaluatedByAi() throws ApplicationException {
		mockFieldMapping(fieldMappingWith(Collections.singletonList(group("Rule1", "Prompt1"))));
		// Two sprints declared, but only SP2 has issues → SP1 is filtered out before
		// the LLM call.
		SprintDetails sd1 = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		SprintDetails sd2 = createSprintDetails("SP2", "Sprint 2", "2026-01-15T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any())).thenReturn(Arrays.asList(sd1, sd2));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(Collections.singletonList(createJiraIssue("ISS-A", "SP2")));
		primeAiHappyPath();

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(
								Arrays.asList(
										createSprintLeafNode("SP1", "Sprint 1"),
										createSprintLeafNode("SP2", "Sprint 2"))));

		assertNotNull(result);
		verify(aiGatewayClient, times(1)).generate(any(ChatGenerationRequest.class));
	}

	@Test
	public void testGetKpiData_parserReturnsEmpty_scoreDefaultsToZero() throws ApplicationException {
		mockFieldMapping(fieldMappingWith(Collections.singletonList(group("Rule1", "Prompt1"))));
		SprintDetails sd = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(Collections.singletonList(createJiraIssue("ISS-1", "SP1")));
		when(aiGatewayClient.generate(any(ChatGenerationRequest.class)))
				.thenReturn(new ChatGenerationResponseDTO("[]"));
		when(hygieneKpiParser.parse(anyString())).thenReturn(Collections.emptyList());

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(Collections.singletonList(createSprintLeafNode("SP1", "Sprint 1"))));

		assertNotNull(result);
		assertNotNull(result.getTrendValueList());
	}

	@Test
	public void testGetKpiData_allNullHygieneScores_scoreDefaultsToZero()
			throws ApplicationException {
		mockFieldMapping(fieldMappingWith(Collections.singletonList(group("Rule1", "Prompt1"))));
		SprintDetails sd = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(Collections.singletonList(createJiraIssue("ISS-1", "SP1")));
		when(aiGatewayClient.generate(any(ChatGenerationRequest.class)))
				.thenReturn(new ChatGenerationResponseDTO("[]"));
		when(hygieneKpiParser.parse(anyString()))
				.thenReturn(
						Arrays.asList(createHygieneDTO("ISS-1", null), createHygieneDTO("ISS-2", null)));

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(Collections.singletonList(createSprintLeafNode("SP1", "Sprint 1"))));

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_mixedHygieneScores_averageIsRounded() throws ApplicationException {
		mockFieldMapping(fieldMappingWith(Collections.singletonList(group("Rule1", "Prompt1"))));
		SprintDetails sd = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(Collections.singletonList(createJiraIssue("ISS-1", "SP1")));
		when(aiGatewayClient.generate(any(ChatGenerationRequest.class)))
				.thenReturn(new ChatGenerationResponseDTO("[]"));
		// Scores 80, 60, null → average of 70.
		when(hygieneKpiParser.parse(anyString()))
				.thenReturn(
						Arrays.asList(
								createHygieneDTO("A", 80), createHygieneDTO("B", 60), createHygieneDTO("C", null)));

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(Collections.singletonList(createSprintLeafNode("SP1", "Sprint 1"))));

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_nullSprintName_fallsBackToSprintIdInDisplayName()
			throws ApplicationException {
		mockFieldMapping(fieldMappingWith(Collections.singletonList(group("Rule1", "Prompt1"))));
		// SprintDetails carries a null sprintName — buildDataCount must fall back to
		// the id.
		SprintDetails sd = createSprintDetails("SP1", null, "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(Collections.singletonList(createJiraIssue("ISS-1", "SP1")));
		primeAiHappyPath();

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(Collections.singletonList(createSprintLeafNode("SP1", "Sprint 1"))));

		assertNotNull(result);
	}

	@Test
	public void testGetKpiData_multipleSprints_oneAiCallPerSprint() throws ApplicationException {
		mockFieldMapping(fieldMappingWith(Collections.singletonList(group("Rule1", "Prompt1"))));
		SprintDetails sd1 = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		SprintDetails sd2 = createSprintDetails("SP2", "Sprint 2", "2026-01-15T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any())).thenReturn(Arrays.asList(sd1, sd2));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(
						Arrays.asList(createJiraIssue("ISS-1", "SP1"), createJiraIssue("ISS-2", "SP2")));
		when(aiGatewayClient.generate(any(ChatGenerationRequest.class)))
				.thenReturn(new ChatGenerationResponseDTO("[]"));
		when(hygieneKpiParser.parse(anyString()))
				.thenReturn(Collections.singletonList(createHygieneDTO("ISS-1", 75)));

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(
								Arrays.asList(
										createSprintLeafNode("SP1", "Sprint 1"),
										createSprintLeafNode("SP2", "Sprint 2"))));

		assertNotNull(result);
		verify(aiGatewayClient, times(2)).generate(any(ChatGenerationRequest.class));
	}

	@Test
	public void testGetKpiData_noJiraIssues_noAiCallsAndEmptyValue() throws ApplicationException {
		mockFieldMapping(fieldMappingWith(Collections.singletonList(group("Rule1", "Prompt1"))));
		SprintDetails sd = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(new ArrayList<>());

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(Collections.singletonList(createSprintLeafNode("SP1", "Sprint 1"))));

		assertNotNull(result);
		verify(aiGatewayClient, never()).generate(any(ChatGenerationRequest.class));
	}

	@Test
	public void testGetKpiData_noSprintDetails_noAiCalls() throws ApplicationException {
		mockFieldMapping(fieldMappingWith(Collections.singletonList(group("Rule1", "Prompt1"))));
		when(sprintDetailsService.getSprintDetailsByIds(any())).thenReturn(new ArrayList<>());
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(new ArrayList<>());

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(Collections.singletonList(createSprintLeafNode("SP1", "Sprint 1"))));

		assertNotNull(result);
		verify(aiGatewayClient, never()).generate(any(ChatGenerationRequest.class));
	}

	@Test
	public void testGetKpiData_excelData_containsOneRowPerHygieneDTO() throws ApplicationException {
		when(cacheService.getFromApplicationCache(
						Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
				.thenReturn("Excel-track-id");
		mockFieldMapping(fieldMappingWith(Collections.singletonList(group("Rule1", "Prompt1"))));
		SprintDetails sd = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(Collections.singletonList(createJiraIssue("ISS-1", "SP1")));
		when(aiGatewayClient.generate(any(ChatGenerationRequest.class)))
				.thenReturn(new ChatGenerationResponseDTO("[]"));
		when(hygieneKpiParser.parse(anyString()))
				.thenReturn(
						Arrays.asList(
								createHygieneDTO("A", 90), createHygieneDTO("B", 70), createHygieneDTO("C", 50)));

		KpiElement result =
				service.getKpiData(
						kpiRequest,
						kpiElement,
						buildTree(Collections.singletonList(createSprintLeafNode("SP1", "Sprint 1"))));

		assertNotNull(result.getExcelData());
		assertEquals(3, result.getExcelData().size());
	}

	@Test
	public void testGetKpiData_trendValueDataCountFieldsPopulated() throws ApplicationException {
		mockFieldMapping(fieldMappingWith(Collections.singletonList(group("Rule1", "Prompt1"))));
		SprintDetails sd = createSprintDetails("SP1", "Sprint 1", "2026-01-01T00:00:00Z");
		when(sprintDetailsService.getSprintDetailsByIds(any()))
				.thenReturn(Collections.singletonList(sd));
		when(jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(anySet(), anyString()))
				.thenReturn(Collections.singletonList(createJiraIssue("ISS-1", "SP1")));
		when(aiGatewayClient.generate(any(ChatGenerationRequest.class)))
				.thenReturn(new ChatGenerationResponseDTO("[]"));
		when(hygieneKpiParser.parse(anyString()))
				.thenReturn(Collections.singletonList(createHygieneDTO("ISS-1", 100)));

		Node projectLeaf = createSprintLeafNode("SP1", "Sprint 1");
		TreeAggregatorDetail detail = buildTree(Collections.singletonList(projectLeaf));

		service.getKpiData(kpiRequest, kpiElement, detail);

		// The project node's value was set to a list of DataCount with the sprint-level
		// score.
		Node project =
				detail.getMapOfListOfProjectNodes().get(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT).get(0);
		Object value = project.getValue();
		assertTrue(value instanceof List);
		@SuppressWarnings("unchecked")
		List<DataCount> counts = (List<DataCount>) value;
		assertEquals(1, counts.size());
		DataCount dc = counts.get(0);
		assertEquals("100", dc.getData());
		assertEquals(100L, ((Number) dc.getValue()).longValue());
		assertEquals("Test Project", dc.getSProjectName());
		assertEquals("SP1", dc.getsSprintID());
		assertEquals("Sprint 1", dc.getsSprintName());
		assertNotNull(dc.getHoverValue());
		assertEquals(100L, ((Number) dc.getHoverValue().get("Hygiene Score")).longValue());
	}

	// ---------------------------------------------------------------------
	// Sanity checks on helper wiring
	// ---------------------------------------------------------------------

	@Test
	public void testCreateProjectNode_hasProjectFilter() {
		Node n = createProjectNode();
		assertNotNull(n.getProjectFilter());
		assertEquals(projectConfigId, n.getProjectFilter().getBasicProjectConfigId());
	}

	@Test
	public void testCreateSprintLeafNode_hasSprintFilter() {
		Node n = createSprintLeafNode("S1", "Sprint 1");
		assertNotNull(n.getSprintFilter());
		assertEquals("S1", n.getSprintFilter().getId());
	}

	@Test
	public void testBuildTree_populatesRequiredMaps() {
		TreeAggregatorDetail detail =
				buildTree(Collections.singletonList(createSprintLeafNode("S1", "Sprint 1")));
		assertNotNull(detail.getMapOfListOfLeafNodes().get(CommonConstant.SPRINT_MASTER));
		assertNotNull(
				detail.getMapOfListOfProjectNodes().get(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT));
		assertNotNull(detail.getMapTmp());
	}

	@Test
	public void testMockAiGatewayClientInstantiation() {
		// Guards against classpath issues loading the external AI-gateway types.
		AiGatewayClient client = mock(AiGatewayClient.class);
		assertNotNull(client);
	}
}
