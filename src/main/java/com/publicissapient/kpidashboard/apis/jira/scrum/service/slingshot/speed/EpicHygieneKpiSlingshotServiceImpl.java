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
import java.util.stream.Collectors;

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
import com.publicissapient.kpidashboard.apis.model.IterationKpiData;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.dto.CycleTimeGroup;
import com.publicissapient.kpidashboard.common.model.jira.EpicHygieneResponseDTO;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.service.recommendation.PromptService;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import com.publicissapient.kpidashboard.common.util.EpicReadinessDimension;
import com.publicissapient.kpidashboard.common.util.HygienePromptBuilder;

import lombok.extern.slf4j.Slf4j;

/**
 * Epic Hygiene (kpi312) — AI driven <b>readiness</b> assessment of every Jira Epic created in the
 * trailing N months of a project.
 *
 * <p>Where {@link StoryHygieneKpiSlingshotServiceImpl} grades sprint issues rule-by-rule with a
 * pass/fail verdict, an Epic is graded on the FIXED readiness dimensions of {@link
 * EpicReadinessDimension} — Business Clarity, Scope Definition, Solution Readiness, Dependency
 * Readiness and Risk Readiness — each on a 0-100 scale, plus the derived Readiness Score. The
 * dimensions are fixed so every project downloads the same comparable sheet; what a project
 * configures in {@code jiraFieldsSelectionKPI312} is <em>how</em> a dimension is scored: the Jira
 * field carrying the evidence and the rule to check. A dimension no configured rule matches is
 * graded with the default criteria carried by the {@code epic-hygiene} prompt.
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li><b>No trend line.</b> Epics are not sprint scoped, so this KPI publishes no {@code
 *       trendValueList}. It reports the drill-down Excel rows plus {@code projectScore}, {@code
 *       scoreFactor} (Epics evaluated) and {@code validScoreFactor} (Epics READY).
 *   <li><b>No stored verdicts.</b> Every Epic is scored by the LLM on every request; nothing is
 *       read from or written to a results collection, so the report always reflects the current
 *       state of the Epic and the current prompt.
 *   <li><b>Batched fan-out.</b> Epics are chunked and dispatched concurrently on the shared hygiene
 *       executor; per-call HTTP timeout is governed by OkHttp's {@code callTimeout} in {@code
 *       AiGatewayConfig}.
 * </ul>
 */
@Slf4j
@Service
public class EpicHygieneKpiSlingshotServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	static final String EPIC_ISSUES = "epicIssues";

	private static final String READY = "READY";

	/** Overall status of an Epic the LLM could not grade in this request. */
	static final String NOT_EVALUATED = "NOT EVALUATED";

	private static final String NOT_EVALUATED_REASON =
			"The AI evaluation did not return a verdict for this Epic — it will be re-evaluated on the next refresh.";

	@Autowired private EpicHygieneKpiParser epicHygieneKpiParser;
	@Autowired private JiraIssueRepository jiraIssueRepository;
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
		// The fallback evidence of every fixed readiness dimension is always fetched:
		// a dimension the project did not configure is graded on its default criteria
		// and would otherwise have no field to read.
		jiraFields.addAll(EpicReadinessDimension.allDefaultEvidenceFields());
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

		String readinessRules = HygienePromptBuilder.buildEpicReadinessRules(readinessDimensions);

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

		// Every Epic is graded by the LLM on every request: no verdict is ever read
		// from or written to the database.
		List<EpicHygieneResponseDTO> verdicts =
				evaluateWithLlm(new ArrayList<>(epicByKey.values()), readinessDimensions, readinessRules);

		// The drill-down must list EVERY Epic that was counted, so any Epic the LLM
		// could not grade — gateway down, unparseable answer, omitted or renamed key —
		// is reported as NOT EVALUATED instead of silently vanishing from the sheet.
		List<EpicHygieneResponseDTO> completeVerdicts = withNotEvaluatedEpics(verdicts, epicByKey);

		List<EpicHygieneResponseDTO> orderedVerdicts =
				completeVerdicts.stream()
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
	 * Adds a NOT EVALUATED placeholder for every sampled Epic that came back without a verdict, so
	 * the number of drill-down rows always matches the "Total Active Epics" card.
	 *
	 * <p>Placeholders carry the real Jira metadata with every dimension score left {@code null}, so
	 * they render as N/A, never count as READY and never move the average readiness score.
	 */
	private List<EpicHygieneResponseDTO> withNotEvaluatedEpics(
			List<EpicHygieneResponseDTO> verdicts, Map<String, JiraIssue> epicByKey) {

		Set<String> gradedKeys =
				verdicts.stream()
						.filter(Objects::nonNull)
						.map(EpicHygieneResponseDTO::getEpicKey)
						.filter(Objects::nonNull)
						.collect(Collectors.toSet());

		List<EpicHygieneResponseDTO> completed = new ArrayList<>(verdicts);
		epicByKey.forEach(
				(epicKey, epic) -> {
					if (!gradedKeys.contains(epicKey)) {
						completed.add(notEvaluatedVerdict(epic));
					}
				});

		int notEvaluated = completed.size() - verdicts.size();
		if (notEvaluated > 0) {
			log.warn(
					"Epic Hygiene (kpi312): {} of {} Epic(s) could not be evaluated — reported as NOT EVALUATED.",
					notEvaluated,
					epicByKey.size());
		}
		return completed;
	}

	/** Builds the NOT EVALUATED row for one Epic: real metadata, no scores, no verdict. */
	private EpicHygieneResponseDTO notEvaluatedVerdict(JiraIssue epic) {
		List<EpicHygieneResponseDTO.DimensionResult> dimensions =
				EpicReadinessDimension.displayNames().stream()
						.map(
								dimension ->
										EpicHygieneResponseDTO.DimensionResult.builder()
												.dimension(dimension)
												.score(null)
												.reason(NOT_EVALUATED_REASON)
												.build())
						.toList();

		EpicHygieneResponseDTO verdict =
				EpicHygieneResponseDTO.builder()
						.epicKey(epic.getNumber())
						.results(new ArrayList<>(dimensions))
						.readinessScore(null)
						.overallStatus(NOT_EVALUATED)
						.topGaps(List.of())
						.recommendations(NOT_EVALUATED_REASON)
						.build();
		applyJiraMetadata(verdict, epic);
		return verdict;
	}

	/**
	 * Splits the Epics into batches, fires one LLM call per batch on the shared hygiene executor and
	 * collects the results. A batch that fails contributes no rows; its Epics are back-filled as NOT
	 * EVALUATED by {@link #withNotEvaluatedEpics}.
	 */
	private List<EpicHygieneResponseDTO> evaluateWithLlm(
			List<JiraIssue> epics, List<CycleTimeGroup> readinessDimensions, String readinessRules) {

		List<String> anchorFieldNames = customApiConfig.getSlingshotEpicHygieneAnchorFields();

		List<List<JiraIssue>> batches = batch(epics, batchSize());
		log.info(
				"Epic Hygiene (kpi312): {} Epic(s) to evaluate — dispatching {} LLM batch(es).",
				epics.size(),
				batches.size());

		List<CompletableFuture<List<EpicHygieneResponseDTO>>> futures =
				batches.stream()
						.map(
								epicBatch -> {
									List<ObjectNode> epicNodes =
											epicBatch.stream()
													.map(
															epic ->
																	HygienePromptBuilder.buildEpicIssueNode(
																			epic, anchorFieldNames, readinessDimensions, objectMapper))
													.toList();
									String epicsJson = HygienePromptBuilder.buildIssuesJson(epicNodes, objectMapper);
									if (epicsJson == null) {
										return CompletableFuture.completedFuture(
												Collections.<EpicHygieneResponseDTO>emptyList());
									}
									String prompt = promptService.getEpicHygienePrompt(readinessRules, epicsJson);
									return CompletableFuture.supplyAsync(
													() -> computeBatchReadiness(epicBatch, prompt), hygieneAiExecutor)
											.exceptionally(ex -> handleBatchFailure(ex, epicBatch));
								})
						.toList();

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		return futures.stream().map(CompletableFuture::join).flatMap(List::stream).toList();
	}

	/** Calls the LLM for one batch of Epics and hands the parsed verdicts back. */
	private List<EpicHygieneResponseDTO> computeBatchReadiness(
			List<JiraIssue> epicBatch, String prompt) {

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
					"AI Gateway call failed for {} Epic(s): {} — those Epics are reported as {}",
					epicBatch.size(),
					ex.getMessage(),
					NOT_EVALUATED,
					ex);
			responseContent = null;
		}

		if (StringUtils.isBlank(responseContent)) {
			// No fabricated data: the Epics of this batch are back-filled as NOT
			// EVALUATED so the sheet still lists them with their real keys.
			log.warn("Epic Hygiene (kpi312): empty AI response for {} Epic(s).", epicBatch.size());
			return List.of();
		}

		List<EpicHygieneResponseDTO> verdicts = epicHygieneKpiParser.parse(responseContent);
		enrichWithJiraMetadata(verdicts, batchByKey);
		return verdicts;
	}

	/**
	 * Keeps only the verdicts that belong to the Epics of this batch and embeds the Jira metadata the
	 * Excel sheet needs (URL, name, status, assignee).
	 */
	private void enrichWithJiraMetadata(
			List<EpicHygieneResponseDTO> verdicts, Map<String, JiraIssue> batchByKey) {

		// Drop verdicts for keys that were never in the batch — the LLM occasionally
		// invents an issue key and such a row must not be shown.
		Set<String> seenKeys = new HashSet<>();
		verdicts.removeIf(
				verdict -> {
					if (verdict == null
							|| verdict.getEpicKey() == null
							|| !batchByKey.containsKey(verdict.getEpicKey())) {
						log.warn(
								"kpi312: dropping verdict for unknown Epic key '{}'",
								verdict == null ? null : verdict.getEpicKey());
						return true;
					}
					if (!seenKeys.add(verdict.getEpicKey())) {
						log.warn(
								"kpi312: duplicate verdict for Epic '{}' — keeping the first one",
								verdict.getEpicKey());
						return true;
					}
					return false;
				});

		verdicts.forEach(verdict -> applyJiraMetadata(verdict, batchByKey.get(verdict.getEpicKey())));
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
	 * Recovers from a failed batch. The Epics of that batch are not lost: they are back-filled as NOT
	 * EVALUATED once every batch has returned, so the drill-down still lists them with their real
	 * keys instead of showing nothing (or fabricated data).
	 */
	private List<EpicHygieneResponseDTO> handleBatchFailure(Throwable ex, List<JiraIssue> epicBatch) {
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
						.filter(score -> score != null && score < 50)
						.count();
		kpiDataList.add(
				IterationKpiData.builder()
						.label("Total Active Epics")
						.value((double) totalEpicsConsidered)
						.build());
		kpiDataList.add(
				IterationKpiData.builder().label("Construction Ready").value((double) readyEpics).build());
		kpiDataList.add(
				IterationKpiData.builder()
						.label("At Risk / Blocked")
						.value((double) atRisked)
						.labelInfo("Readiness < 50%")
						.build());
		kpiDataList.add(
				IterationKpiData.builder()
						.label("Avg Readiness Score")
						.value(
								averageReadiness.isPresent()
										? roundToTwoDecimals(averageReadiness.getAsDouble())
										: 0d)
						.build());
		kpiElement.setTrendValueList(kpiDataList);
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

	/** Resolves the configured readiness dimensions, tolerating a missing field mapping. */
	private List<CycleTimeGroup> readinessDimensions(FieldMapping fieldMapping) {
		return fieldMapping == null ? List.of() : fieldMapping.getJiraFieldsSelectionKPI312();
	}

	private double roundToTwoDecimals(double value) {
		return Math.round(value * 100.0) / 100.0;
	}
}
