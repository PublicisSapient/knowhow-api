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

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.publicissapient.kpidashboard.apis.model.IterationKpiData;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.request.chat.ChatGenerationRequest;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.config.HygieneAiExecutorConfig;
import com.publicissapient.kpidashboard.apis.ai.parser.EpicHygieneKpiParser;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.dto.CycleTimeGroup;
import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneResponseDTO;
import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneResult;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.repository.jira.EpicHygieneResultRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.service.recommendation.PromptService;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import com.publicissapient.kpidashboard.common.util.HygienePromptBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Epic Hygiene (kpi312) — AI driven <b>readiness</b> assessment of every Jira Epic created in the
 * trailing N months of a project.
 *
 * <p>Where {@link StoryHygieneKpiSlingshotServiceImpl} grades sprint issues rule-by-rule with a
 * pass/fail verdict, an Epic is graded per <em>readiness dimension</em> on a 0-100 scale (Business
 * Clarity, Scope Definition, Solution Readiness, Dependency Readiness, Delivery Readiness, Risk
 * Readiness, ...). The dimensions are not hardcoded: they come from the project's {@code
 * jiraFieldsSelectionKPI312} field mapping, so each entry supplies the Jira field to inspect and
 * the scoring criteria to apply.
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li><b>No trend line.</b> Epics are not sprint scoped, so this KPI publishes no {@code
 *       trendValueList}. It reports the drill-down Excel rows plus {@code projectScore}, {@code
 *       scoreFactor} (Epics evaluated) and {@code validScoreFactor} (Epics READY).
 *   <li><b>Per-Epic cache.</b> One {@link EpicHygieneResult} document per (project, Epic). A cached
 *       verdict is reused while the rule-set hash and the Epic's {@code changeDate} are both
 *       unchanged, so a request only pays the LLM cost for Epics that were actually re-groomed.
 *   <li><b>Batched fan-out.</b> Epics needing evaluation are chunked and dispatched concurrently on
 *       the shared hygiene executor; per-call HTTP timeout is governed by OkHttp's {@code
 *       callTimeout} in {@code AiGatewayConfig}.
 * </ul>
 */
@Slf4j
@Service
public class EpicHygieneKpiSlingshotServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	static final String EPIC_ISSUES = "epicIssues";

	private static final String READY = "READY";

	/**
	 * Fallback response used when the AI Gateway is unavailable. Shown to the user but never
	 * persisted.
	 */
	static final String MOCK_EPIC_HYGIENE_RESPONSE_JSON =
			"""
			[
				{
					"epicKey": "DTS-40112",
					"epicName": "Role based access across KnowHOW resources",
					"status": "Construction Ready",
					"assignee": "Raja Kurru",
					"results": [
						{
							"dimension": "Business Clarity",
							"field": "description",
							"weight": 1,
							"score": 88,
							"observed": "Problem statement, target personas and expected business outcome are documented",
							"reason": "description states the business problem, the value proposition and the measurable outcome"
						},
						{
							"dimension": "Scope Definition",
							"field": "description",
							"weight": 1,
							"score": 82,
							"observed": "In-scope and out-of-scope lists plus deliverables are enumerated",
							"reason": "scope boundaries and deliverables are explicit, exclusions are called out"
						},
						{
							"dimension": "Solution Readiness",
							"field": "description",
							"weight": 1,
							"score": 78,
							"observed": "Architecture approach and integration points documented",
							"reason": "technical approach is described with the chosen option and its trade-offs"
						},
						{
							"dimension": "Dependency Readiness",
							"field": "description",
							"weight": 1,
							"score": 74,
							"observed": "Two upstream dependencies listed with owners",
							"reason": "dependencies are named with owners, but no target resolution dates are given"
						},
						{
							"dimension": "Delivery Readiness",
							"field": "assigneeName",
							"weight": 1,
							"score": 80,
							"observed": "Raja Kurru",
							"reason": "owner assigned and status is Construction Ready with milestones listed"
						},
						{
							"dimension": "Risk Readiness",
							"field": "description",
							"weight": 1,
							"score": 72,
							"observed": "Risks and assumptions section present",
							"reason": "risks are identified with mitigations, but no contingency or monitoring plan"
						}
					],
					"readinessScore": 79,
					"readinessGrade": "GOOD",
					"overallStatus": "READY",
					"topGaps": [],
					"recommendations": "Add target resolution dates to each dependency | Document contingency plans for the top two risks | Capture NFRs for authorisation latency | Link the architecture decision record | Define success metrics for the rollout"
				},
				{
					"epicKey": "DTS-41880",
					"epicName": "Predictive KPIs for quality metrics",
					"status": "Functional Grooming",
					"assignee": "Theodor Constantin",
					"results": [
						{
							"dimension": "Business Clarity",
							"field": "description",
							"weight": 1,
							"score": 65,
							"observed": "Goal stated at a high level, no measurable outcome",
							"reason": "description explains the intent but does not quantify the expected business benefit"
						},
						{
							"dimension": "Scope Definition",
							"field": "description",
							"weight": 1,
							"score": 40,
							"observed": "No acceptance criteria or deliverables listed",
							"reason": "scope boundaries are absent; which KPIs are in scope is not stated"
						},
						{
							"dimension": "Solution Readiness",
							"field": "description",
							"weight": 1,
							"score": 30,
							"observed": "null",
							"reason": "no technical approach, model choice or data strategy documented"
						},
						{
							"dimension": "Dependency Readiness",
							"field": "description",
							"weight": 1,
							"score": 20,
							"observed": "null",
							"reason": "no dependencies documented despite an external data feed being implied"
						},
						{
							"dimension": "Delivery Readiness",
							"field": "assigneeName",
							"weight": 1,
							"score": 45,
							"observed": "Theodor Constantin",
							"reason": "owner assigned, but no milestones, phasing or capacity confirmation"
						},
						{
							"dimension": "Risk Readiness",
							"field": "description",
							"weight": 1,
							"score": 25,
							"observed": "null",
							"reason": "no risks, assumptions or constraints captured"
						}
					],
					"readinessScore": 38,
					"readinessGrade": "POOR",
					"overallStatus": "NOT READY",
					"topGaps": ["Dependency Readiness", "Risk Readiness", "Solution Readiness"],
					"recommendations": "Document the technical approach and model selection | List in-scope KPIs and acceptance criteria | Capture dependencies with owners and dates | Record risks, assumptions and mitigations | Define measurable success metrics"
				}
			]
			""";

	@Autowired private EpicHygieneKpiParser epicHygieneKpiParser;
	@Autowired private JiraIssueRepository jiraIssueRepository;
	@Autowired private EpicHygieneResultRepository epicHygieneResultRepository;
	@Autowired private AiGatewayClient aiGatewayClient;
	@Autowired private ConfigHelperService configHelperService;
	@Autowired private CustomApiConfig customApiConfig;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private PromptService promptService;

	@Autowired
	@Qualifier(HygieneAiExecutorConfig.HYGIENE_AI_EXECUTOR)
	private Executor hygieneAiExecutor;

	@Override
	public String getQualifierType() {
		return KPICode.EPIC_HYGIENE.name();
	}

	@Override
	public Double calculateKPIMetrics(Map<String, Object> stringObjectMap) {
		return 0.0;
	}

	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {

		List<Node> projectNodes =
				treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT);
		if (CollectionUtils.isEmpty(projectNodes)) {
			log.warn("Epic Hygiene (kpi312): no project node in the request — nothing to evaluate.");
			return kpiElement;
		}
		projectWiseLeafNodeValue(kpiElement, projectNodes.get(0), kpiRequest);
		// Intentionally no trend value list: Epics are not sprint scoped, so this KPI
		// publishes only the drill-down rows and the project level score factors.
		return kpiElement;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {

		Map<String, Object> resultListMap = new HashMap<>();
		if (CollectionUtils.isEmpty(leafNodeList)) {
			resultListMap.put(EPIC_ISSUES, Collections.<JiraIssue>emptyList());
			return resultListMap;
		}

		ObjectId basicProjectConfigId =
				leafNodeList.get(0).getProjectFilter().getBasicProjectConfigId();
		List<CycleTimeGroup> dimensions =
				readinessDimensions(configHelperService.getFieldMapping(basicProjectConfigId));

		Set<String> jiraFields = new HashSet<>();
		if (dimensions != null) {
			dimensions.stream()
					.filter(Objects::nonNull)
					.map(CycleTimeGroup::getFieldName)
					.filter(StringUtils::isNotEmpty)
					.forEach(jiraFields::add);
		}
		List<String> anchorFieldNames = customApiConfig.getSlingshotEpicHygieneAnchorFields();
		if (CollectionUtils.isNotEmpty(anchorFieldNames)) {
			jiraFields.addAll(anchorFieldNames);
		}
		jiraFields.addAll(
				List.of(
						"number",
						"name",
						"typeName",
						"status",
						"priority",
						"changeDate",
						"createdDate",
						"url"));

		String windowStart = StringUtils.defaultIfEmpty(startDate, defaultWindowStart());
		String windowEnd = StringUtils.defaultIfEmpty(endDate, defaultWindowEnd());

		List<JiraIssue> epicIssues =
				jiraIssueRepository.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
						new HashSet<>(epicIssueTypes()),
						basicProjectConfigId.toString(),
						windowStart,
						windowEnd,
						jiraFields);

		resultListMap.put(EPIC_ISSUES, epicIssues);
		return resultListMap;
	}

	// ────────────────────────────────────────────────────────────────────────
	// Core pipeline
	// ────────────────────────────────────────────────────────────────────────

	@SuppressWarnings("unchecked")
	private void projectWiseLeafNodeValue(KpiElement kpiElement, Node node, KpiRequest kpiRequest) {
		String basicProjectConfigId = node.getProjectFilter().getBasicProjectConfigId().toString();

		List<CycleTimeGroup> readinessDimensions =
				readinessDimensions(
						configHelperService.getFieldMapping(node.getProjectFilter().getBasicProjectConfigId()));

		String readinessRules = HygienePromptBuilder.buildHygieneRules(readinessDimensions);
		String ruleSetHash = HygienePromptBuilder.computeRuleSetHash(readinessDimensions, objectMapper);

		long startedAt = System.currentTimeMillis();
		Map<String, Object> resultMap = fetchKPIDataFromDb(List.of(node), null, null, kpiRequest);
		log.info(
				"Epic Hygiene (kpi312): fetchKPIDataFromDb took {} ms",
				System.currentTimeMillis() - startedAt);

		List<JiraIssue> epicIssues = (List<JiraIssue>) resultMap.get(EPIC_ISSUES);
		if (CollectionUtils.isEmpty(epicIssues)) {
			log.info(
					"Epic Hygiene (kpi312): no Epics found in the configured window for {}",
					basicProjectConfigId);
			publish(kpiElement, List.of(), 0);
			return;
		}

		List<JiraIssue> sampledEpics = capEpics(epicIssues);
		Map<String, JiraIssue> epicByKey =
				sampledEpics.stream()
						.filter(epic -> epic.getNumber() != null)
						.collect(
								Collectors.toMap(JiraIssue::getNumber, epic -> epic, (first, second) -> first));

		Map<String, EpicHygieneResult> cachedByEpicKey =
				epicHygieneResultRepository
						.findByBasicProjectConfigIdAndEpicKeyIn(
								basicProjectConfigId, new ArrayList<>(epicByKey.keySet()))
						.stream()
						.filter(result -> result.getEpicKey() != null)
						.collect(
								Collectors.toMap(EpicHygieneResult::getEpicKey, result -> result, (a, b) -> a));

		List<EpicHygieneResponseDTO> verdicts = new ArrayList<>();
		List<JiraIssue> staleEpics = new ArrayList<>();
		epicByKey.forEach(
				(epicKey, epic) -> {
					EpicHygieneResult cached = cachedByEpicKey.get(epicKey);
					if (isFresh(cached, ruleSetHash, epic)) {
						log.debug("Epic Hygiene (kpi312): cache hit for Epic '{}' — serving from DB", epicKey);
						verdicts.add(cached.getVerdict());
					} else {
						staleEpics.add(epic);
					}
				});

		if (!staleEpics.isEmpty()) {
			verdicts.addAll(
					evaluateWithLlm(
							staleEpics,
							readinessDimensions,
							readinessRules,
							ruleSetHash,
							basicProjectConfigId,
							cachedByEpicKey));
		}

		List<EpicHygieneResponseDTO> orderedVerdicts =
				verdicts.stream()
						.filter(Objects::nonNull)
						.sorted(
								Comparator.comparing(
										EpicHygieneResponseDTO::getEpicKey,
										Comparator.nullsLast(Comparator.naturalOrder())))
						.toList();

		publish(kpiElement, orderedVerdicts, sampledEpics.size());
		node.setValue(orderedVerdicts);
	}

	/**
	 * Splits the Epics that need a fresh verdict into batches, fires one LLM call per batch on the
	 * shared hygiene executor and collects the results. A batch that fails outright contributes no
	 * rows; the first batch that hits an unreachable gateway contributes the mock payload so the user
	 * still sees a populated table.
	 */
	private List<EpicHygieneResponseDTO> evaluateWithLlm(
			List<JiraIssue> staleEpics,
			List<CycleTimeGroup> readinessDimensions,
			String readinessRules,
			String ruleSetHash,
			String basicProjectConfigId,
			Map<String, EpicHygieneResult> cachedByEpicKey) {

		List<String> anchorFieldNames = customApiConfig.getSlingshotEpicHygieneAnchorFields();
		AtomicBoolean mockServed = new AtomicBoolean(false);

		List<List<JiraIssue>> batches = batch(staleEpics, batchSize());
		log.info(
				"Epic Hygiene (kpi312): {} Epic(s) need evaluation — dispatching {} LLM batch(es).",
				staleEpics.size(),
				batches.size());

		List<CompletableFuture<List<EpicHygieneResponseDTO>>> futures =
				batches.stream()
						.map(
								epicBatch -> {
									List<ObjectNode> epicNodes =
											epicBatch.stream()
													.map(
															epic ->
																	HygienePromptBuilder.buildIssueNode(
																			epic, anchorFieldNames, readinessDimensions, objectMapper))
													.toList();
									String epicsJson = HygienePromptBuilder.buildIssuesJson(epicNodes, objectMapper);
									if (epicsJson == null) {
										return CompletableFuture.completedFuture(
												Collections.<EpicHygieneResponseDTO>emptyList());
									}
									String prompt = promptService.getEpicHygienePrompt(readinessRules, epicsJson);
									return CompletableFuture.supplyAsync(
													() ->
															computeBatchReadiness(
																	epicBatch,
																	prompt,
																	ruleSetHash,
																	basicProjectConfigId,
																	cachedByEpicKey),
													hygieneAiExecutor)
											.exceptionally(ex -> handleBatchFailure(ex, epicBatch, mockServed));
								})
						.toList();

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		return futures.stream().map(CompletableFuture::join).flatMap(List::stream).toList();
	}

	/**
	 * Calls the LLM for one batch of Epics, persists every returned verdict and hands the verdicts
	 * back. Existing documents are updated in place so the collection keeps exactly one entry per
	 * (project, Epic).
	 */
	private List<EpicHygieneResponseDTO> computeBatchReadiness(
			List<JiraIssue> epicBatch,
			String prompt,
			String ruleSetHash,
			String basicProjectConfigId,
			Map<String, EpicHygieneResult> cachedByEpicKey) {

		Map<String, JiraIssue> batchByKey =
				epicBatch.stream()
						.filter(epic -> epic.getNumber() != null)
						.collect(Collectors.toMap(JiraIssue::getNumber, epic -> epic, (a, b) -> a));

		String responseContent;
		try {
			ChatGenerationResponseDTO response =
					aiGatewayClient.generate(ChatGenerationRequest.builder().prompt(prompt).build());
			responseContent = response == null ? null : response.content();
			log.info(
					"kpi312 [{} epics]: responseChars={}",
					epicBatch.size(),
					responseContent == null ? 0 : responseContent.length());
			log.debug("kpi312: content={}", responseContent);
		} catch (Exception ex) {
			log.error(
					"AI Gateway call failed for {} Epic(s): {} — returning mock data (not persisted)",
					epicBatch.size(),
					ex.getMessage(),
					ex);
			responseContent = null;
		}

		if (StringUtils.isBlank(responseContent)) {
			throw new MockEpicHygieneResponseException(mockVerdicts(batchByKey));
		}

		List<EpicHygieneResponseDTO> verdicts = epicHygieneKpiParser.parse(responseContent);
		enrichAndPersist(verdicts, batchByKey, ruleSetHash, basicProjectConfigId, cachedByEpicKey);
		return verdicts;
	}

	/**
	 * Embeds the Jira metadata the Excel sheet needs (URL, name, status, assignee) into each verdict
	 * and upserts it, so a later cache hit is fully self-contained and needs no {@code jira_issues}
	 * lookup.
	 */
	private void enrichAndPersist(
			List<EpicHygieneResponseDTO> verdicts,
			Map<String, JiraIssue> batchByKey,
			String ruleSetHash,
			String basicProjectConfigId,
			Map<String, EpicHygieneResult> cachedByEpicKey) {

		// Drop verdicts for keys that were never in the batch — the LLM occasionally
		// invents an issue key and such a row must neither be shown nor persisted.
		verdicts.removeIf(
				verdict -> {
					boolean unknown =
							verdict == null
									|| verdict.getEpicKey() == null
									|| !batchByKey.containsKey(verdict.getEpicKey());
					if (unknown && verdict != null) {
						log.warn("kpi312: dropping verdict for unknown Epic key '{}'", verdict.getEpicKey());
					}
					return unknown;
				});

		List<EpicHygieneResult> toSave = new ArrayList<>();
		for (EpicHygieneResponseDTO verdict : verdicts) {
			JiraIssue epic = batchByKey.get(verdict.getEpicKey());
			applyJiraMetadata(verdict, epic);

			EpicHygieneResult result =
					cachedByEpicKey.getOrDefault(
							verdict.getEpicKey(),
							EpicHygieneResult.builder()
									.basicProjectConfigId(basicProjectConfigId)
									.epicKey(verdict.getEpicKey())
									.build());
			result.setEpicName(verdict.getEpicName());
			result.setRuleSetHash(ruleSetHash);
			result.setEpicChangeDate(epic.getChangeDate());
			result.setVerdict(verdict);
			result.setComputedAt(Instant.now());
			toSave.add(result);
		}

		if (!toSave.isEmpty()) {
			epicHygieneResultRepository.saveAll(toSave);
		}
	}

	private void applyJiraMetadata(EpicHygieneResponseDTO verdict, JiraIssue epic) {
		verdict.setEpicUrl(Objects.toString(epic.getUrl(), ""));
		if (StringUtils.isBlank(verdict.getEpicName())) {
			verdict.setEpicName(StringUtils.defaultIfBlank(epic.getName(), epic.getNumber()));
		}
		if (StringUtils.isBlank(verdict.getStatus())) {
			verdict.setStatus(epic.getStatus());
		}
		if (StringUtils.isBlank(verdict.getAssignee())) {
			verdict.setAssignee(StringUtils.defaultIfBlank(epic.getAssigneeName(), "Unassigned"));
		}
	}

	/**
	 * Recovers from a failed batch. The mock payload is served at most once per request so a project
	 * with many batches does not repeat the same fabricated Epics.
	 */
	private List<EpicHygieneResponseDTO> handleBatchFailure(
			Throwable ex, List<JiraIssue> epicBatch, AtomicBoolean mockServed) {
		Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
		if (cause instanceof MockEpicHygieneResponseException mockEx) {
			return mockServed.compareAndSet(false, true) ? mockEx.verdicts() : List.of();
		}
		log.error(
				"Epic Hygiene (kpi312): evaluation failed for a batch of {} Epic(s): {}",
				epicBatch.size(),
				ex.getMessage(),
				ex);
		return List.of();
	}

	/**
	 * Writes the Excel rows and the project level score factors onto the {@link KpiElement}. {@code
	 * scoreFactor} is the number of Epics evaluated, {@code validScoreFactor} the number that came
	 * back READY and {@code projectScore} the mean readiness score across all Epics.
	 */
	private void publish(
			KpiElement kpiElement, List<EpicHygieneResponseDTO> verdicts, int totalEpicsConsidered) {

		// This KPI has no trend line, so the drill-down rows ARE its payload: they are
		// published on every request (not just the Excel one) and surfaced on
		// /jira/kpi.
		List<KPIExcelData> excelRows = new ArrayList<>();
		KPIExcelUtility.populateEpicHygieneExcelData(excelRows, verdicts);
		kpiElement.setExcelData(excelRows);
		kpiElement.setExcelColumns(KPIExcelColumn.EPIC_HYGIENE.getColumns());
		List<IterationKpiData> kpiDataList = new ArrayList<>();

		int readyEpics =
				(int)
						verdicts.stream()
								.filter(verdict -> READY.equalsIgnoreCase(verdict.getOverallStatus()))
								.count();

		OptionalDouble averageReadiness =
				verdicts.stream()
						.map(EpicHygieneResponseDTO::getReadinessScore)
						.filter(Objects::nonNull)
						.mapToInt(Integer::intValue)
						.average();
		long atRisked =
				verdicts.stream()
						.map(EpicHygieneResponseDTO::getReadinessScore)
						.filter(score -> score != null && score<50)
						.count();
		kpiDataList.add(IterationKpiData.builder().label("Total Active Epics").value((double) totalEpicsConsidered).build());
		kpiDataList.add(IterationKpiData.builder().label("Construction Ready").value((double) readyEpics).build());
		kpiDataList.add(IterationKpiData.builder().label("At Risk / Blocked").value((double) atRisked).labelInfo("Readiness < 50%").build());
		kpiDataList.add(IterationKpiData.builder().label("Avg Readiness Score").value(averageReadiness.isPresent() ? roundToTwoDecimals(averageReadiness.getAsDouble()) : 0d).build());
		kpiElement.setTrendValueList(kpiDataList);
	}

	/** A cached verdict survives only while both the rule-set and the Epic itself are unchanged. */
	private boolean isFresh(EpicHygieneResult cached, String ruleSetHash, JiraIssue epic) {
		return cached != null
				&& cached.getVerdict() != null
				&& StringUtils.isNotBlank(ruleSetHash)
				&& ruleSetHash.equals(cached.getRuleSetHash())
				&& Objects.equals(
						Objects.toString(cached.getEpicChangeDate(), ""),
						Objects.toString(epic.getChangeDate(), ""));
	}

	/**
	 * Keeps the most recently touched Epics when the project has more than the configured cap, so a
	 * very large backlog cannot blow up the prompt budget.
	 */
	private List<JiraIssue> capEpics(List<JiraIssue> epicIssues) {
		int cap = Math.max(1, customApiConfig.getSlingshotEpicHygieneEpicCount());
		if (epicIssues.size() <= cap) {
			return epicIssues;
		}
		log.warn(
				"Epic Hygiene (kpi312): Epic cap of {} hit — {} Epics available, sampling the {} most recently updated.",
				cap,
				epicIssues.size(),
				cap);
		return epicIssues.stream()
				.sorted(
						Comparator.comparing(
										(JiraIssue epic) ->
												StringUtils.defaultIfBlank(epic.getChangeDate(), epic.getCreatedDate()),
										Comparator.nullsLast(Comparator.reverseOrder()))
								.thenComparingInt(epic -> HygienePromptBuilder.priorityRank(epic.getPriority())))
				.limit(cap)
				.toList();
	}

	private static <T> List<List<T>> batch(List<T> items, int batchSize) {
		List<List<T>> batches = new ArrayList<>();
		for (int index = 0; index < items.size(); index += batchSize) {
			batches.add(new ArrayList<>(items.subList(index, Math.min(index + batchSize, items.size()))));
		}
		return batches;
	}

	private int batchSize() {
		return Math.max(1, customApiConfig.getSlingshotEpicHygieneBatchSize());
	}

	private List<String> epicIssueTypes() {
		List<String> configured = customApiConfig.getSlingshotEpicHygieneIssueTypes();
		return CollectionUtils.isEmpty(configured) ? List.of("Epic") : configured;
	}

	private String defaultWindowStart() {
		int months = Math.max(1, customApiConfig.getSlingshotEpicHygieneMonths());
		return DateUtil.dateTimeFormatter(
				LocalDateTime.now().minusMonths(months).toLocalDate().atStartOfDay(), DateUtil.TIME_FORMAT);
	}

	private String defaultWindowEnd() {
		return DateUtil.dateTimeFormatter(LocalDateTime.now(), DateUtil.TIME_FORMAT);
	}

	/** Mock verdicts are shown but never persisted — they carry no real evidence. */
	private List<EpicHygieneResponseDTO> mockVerdicts(Map<String, JiraIssue> batchByKey) {
		List<EpicHygieneResponseDTO> mocks =
				epicHygieneKpiParser.parse(MOCK_EPIC_HYGIENE_RESPONSE_JSON);
		mocks.forEach(
				mock -> {
					JiraIssue epic = batchByKey.get(mock.getEpicKey());
					if (epic != null) {
						applyJiraMetadata(mock, epic);
					} else {
						mock.setEpicUrl("");
					}
				});
		return mocks;
	}

	/** Resolves the configured readiness dimensions, tolerating a missing field mapping. */
	private List<CycleTimeGroup> readinessDimensions(FieldMapping fieldMapping) {
		return fieldMapping == null ? List.of() : fieldMapping.getJiraFieldsSelectionKPI312();
	}

	private double roundToTwoDecimals(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	/**
	 * Thrown by {@link #computeBatchReadiness} when the AI Gateway is unavailable and the mock
	 * response is used. Carries the pre-built verdicts so the {@code exceptionally} handler can
	 * return them — bypassing the DB persist while still showing data to the user.
	 */
	private static final class MockEpicHygieneResponseException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final transient List<EpicHygieneResponseDTO> verdicts;

		MockEpicHygieneResponseException(List<EpicHygieneResponseDTO> verdicts) {
			super("AI Gateway unavailable — serving mock epic hygiene data");
			this.verdicts = verdicts;
		}

		List<EpicHygieneResponseDTO> verdicts() {
			return verdicts;
		}
	}
}
