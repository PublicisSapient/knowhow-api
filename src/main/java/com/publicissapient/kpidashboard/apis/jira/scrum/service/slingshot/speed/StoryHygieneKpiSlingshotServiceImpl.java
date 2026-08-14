package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.speed;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.request.chat.ChatGenerationRequest;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.config.HygieneAiExecutorConfig;
import com.publicissapient.kpidashboard.apis.ai.parser.HygieneKpiParser;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.jira.service.SprintDetailsService;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.dto.CycleTimeGroup;
import com.publicissapient.kpidashboard.common.model.jira.BoardMetadata;
import com.publicissapient.kpidashboard.common.model.jira.HygieneKpiResponseDTO;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.Metadata;
import com.publicissapient.kpidashboard.common.model.jira.MetadataValue;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.StoryHygieneSprintResult;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.jira.StoryHygieneSprintResultRepository;
import com.publicissapient.kpidashboard.common.service.recommendation.PromptService;
import com.publicissapient.kpidashboard.common.util.HygienePromptBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class StoryHygieneKpiSlingshotServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String JIRA_ISSUES = "jiraIssues";
	private static final String SPRINT_DETAILS = "sprintDetails";

	@Autowired private HygieneKpiParser hygieneKpiParser;
	@Autowired private JiraIssueRepository jiraIssueRepository;
	@Autowired private AiGatewayClient aiGatewayClient;
	@Autowired private SprintDetailsService sprintDetailsService;
	@Autowired private ConfigHelperService configHelperService;
	@Autowired private CustomApiConfig customApiConfig;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private StoryHygieneSprintResultRepository hygieneResultRepository;
	@Autowired private MongoTemplate mongoTemplate;
	@Autowired private PromptService promptService;

	@Autowired
	@Qualifier(HygieneAiExecutorConfig.HYGIENE_AI_EXECUTOR)
	private Executor hygieneAiExecutor;

	// Per-sprint HTTP timeout is governed by OkHttp's callTimeout in
	// AiGatewayConfig
	// (currently 150 s). No additional CompletableFuture timeout is needed here.

	private List<String> sprintIdList = Collections.synchronizedList(new ArrayList<>());

	@Override
	public String getQualifierType() {
		return KPICode.STORY_HYGIENE.name();
	}

	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {

		sprintIdList =
				treeAggregatorDetail.getMapOfListOfLeafNodes().get(CommonConstant.SPRINT_MASTER).stream()
						.map(node -> node.getSprintFilter().getId())
						.toList();
		Node project =
				treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT).get(0);

		projectWiseLeafNodeValue(kpiElement, project, kpiRequest);
		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValue(project, nodeWiseKPIValue, KPICode.STORY_HYGIENE);
		List<DataCount> trendValues =
				getTrendValues(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.STORY_HYGIENE);
		kpiElement.setTrendValueList(trendValues);

		return kpiElement;
	}

	@Override
	public Double calculateKPIMetrics(Map<String, Object> stringObjectMap) {
		return 0.0;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {

		Map<String, Object> resultListMap = new HashMap<>();
		Node leafNode = leafNodeList.get(0);

		ObjectId basicProjectConfigId = leafNode.getProjectFilter().getBasicProjectConfigId();
		List<SprintDetails> sprintDetailsList =
				sprintDetailsService.getSprintDetailsByIds(sprintIdList);
		List<SprintDetails> sortedSprintList =
				sprintDetailsList.stream()
						.sorted(
								Comparator.comparing(
										SprintDetails::getStartDate, Comparator.nullsLast(Comparator.naturalOrder())))
						.toList();
		int hygieneSprintCount = customApiConfig.getSlingshotHygieneSprintCount();
		if (sortedSprintList.size() > hygieneSprintCount) {
			log.warn(
					"Story Hygiene (kpi311): sprint cap of {} hit — {} sprints available, evaluating only the most recent {}.",
					hygieneSprintCount,
					sortedSprintList.size(),
					hygieneSprintCount);
		}
		List<SprintDetails> limitedSprintList =
				sortedSprintList.stream()
						.skip(Math.max(0, sortedSprintList.size() - hygieneSprintCount))
						.toList();
		Map<String, String> data = new HashMap<>();
		BoardMetadata boardmetadata = configHelperService.getBoardMetaData(basicProjectConfigId);
		if (boardmetadata != null && CollectionUtils.isNotEmpty(boardmetadata.getMetadata())) {
			data =
					boardmetadata.getMetadata().stream()
							.filter(Objects::nonNull)
							.filter(metadata -> "fields".equalsIgnoreCase(metadata.getType()))
							.map(Metadata::getValue)
							.filter(Objects::nonNull)
							.flatMap(List::stream)
							.filter(mv -> mv != null && mv.getKey() != null)
							.collect(
									Collectors.toMap(
											MetadataValue::getKey,
											MetadataValue::getData,
											(first, second) -> first,
											LinkedHashMap::new));
		}

		FieldMapping fieldMapping = configHelperService.getFieldMapping(basicProjectConfigId);
		List<CycleTimeGroup> cycleTimeGroupList = fieldMapping.getJiraFieldsSelectionKPI311();
		Set<String> jiraFields = new HashSet<>();
		if (cycleTimeGroupList != null) {
			cycleTimeGroupList.stream()
					.filter(ctg -> ctg != null && ctg.getFieldName() != null)
					.map(CycleTimeGroup::getFieldName)
					.filter(StringUtils::isNotEmpty)
					.forEach(jiraFields::add);
		}
		List<String> anchorFieldNames = customApiConfig.getSlingshotHygieneAnchorFields();
		if (CollectionUtils.isNotEmpty(anchorFieldNames)) {
			jiraFields.addAll(anchorFieldNames);
		}
		jiraFields.addAll(List.of("sprintID", "priority", "changeDate", "url", "number"));

		List<JiraIssue> jiraIssueList =
				jiraIssueRepository.findBySprintIDInAndBasicProjectConfigIdWithFields(
						limitedSprintList.stream().map(SprintDetails::getSprintID).collect(Collectors.toSet()),
						basicProjectConfigId.toString(),
						jiraFields);

		resultListMap.put(JIRA_ISSUES, jiraIssueList);
		resultListMap.put(SPRINT_DETAILS, limitedSprintList);
		return resultListMap;
	}

	private void projectWiseLeafNodeValue(KpiElement kpiElement, Node node, KpiRequest kpiRequest) {
		String requestTrackerId = getRequestTrackerId();
		String projectName = node.getProjectFilter().getName();
		String basicProjectConfigId = node.getProjectFilter().getBasicProjectConfigId().toString();

		FieldMapping fieldMapping =
				configHelperService.getFieldMapping(node.getProjectFilter().getBasicProjectConfigId());
		List<CycleTimeGroup> cycleTimeGroupList = fieldMapping.getJiraFieldsSelectionKPI311();

		String hygieneRules = HygienePromptBuilder.buildHygieneRules(cycleTimeGroupList);

		String ruleSetHash = HygienePromptBuilder.computeRuleSetHash(cycleTimeGroupList, objectMapper);

		long time = System.currentTimeMillis();
		Map<String, Object> resultMap = fetchKPIDataFromDb(List.of(node), null, null, kpiRequest);
		log.info(
				"Story Hygiene (kpi311): fetchKPIDataFromDb took {} ms", System.currentTimeMillis() - time);

		List<JiraIssue> jiraIssueList = (List<JiraIssue>) resultMap.get(JIRA_ISSUES);
		List<SprintDetails> sprintDetailsList = (List<SprintDetails>) resultMap.get(SPRINT_DETAILS);
		List<String> anchorFieldNames = customApiConfig.getSlingshotHygieneAnchorFields();

		// Pre-load all cached results for the sprints in this request (single DB
		// round-trip)
		List<String> sprintIds = sprintDetailsList.stream().map(SprintDetails::getSprintID).toList();
		Map<String, StoryHygieneSprintResult> cachedBySprintId =
				hygieneResultRepository
						.findByBasicProjectConfigIdAndSprintIdIn(basicProjectConfigId, sprintIds)
						.stream()
						.collect(Collectors.toMap(StoryHygieneSprintResult::getSprintId, r -> r));

		Map<String, List<JiraIssue>> jiraIssuesBySprint =
				jiraIssueList.stream()
						.filter(ji -> ji.getSprintID() != null)
						.collect(Collectors.groupingBy(JiraIssue::getSprintID));

		List<CompletableFuture<SprintHygieneOutcome>> futures =
				sprintDetailsList.stream()
						.filter(sd -> !jiraIssuesBySprint.getOrDefault(sd.getSprintID(), List.of()).isEmpty())
						.map(
								sprintDetails -> {
									String sprintId = sprintDetails.getSprintID();
									String sprintName = sprintDetails.getSprintName();
									List<JiraIssue> jiraIssues = jiraIssuesBySprint.get(sprintId);
									int totalIssueCount = jiraIssues.size();

									// ── Cache hit: valid stored result for the current rule-set ──
									StoryHygieneSprintResult cached = cachedBySprintId.get(sprintId);
									Map<String, String> issueUrlMap =
											jiraIssues.stream()
													.collect(
															Collectors.toMap(
																	JiraIssue::getNumber,
																	ji -> StringUtils.defaultString(ji.getUrl(), ""),
																	(a, b) -> a));

									if (cached != null && ruleSetHash.equals(cached.getRuleSetHash())) {
										log.debug(
												"Story Hygiene (kpi311): cache hit for sprint '{}' — serving from DB",
												sprintName);
										return CompletableFuture.completedFuture(
												buildOutcomeFromVerdicts(
														sprintId,
														sprintName,
														projectName,
														cached.getIssueVerdicts(),
														cached.getSampledIssueCount(),
														cached.getTotalIssueCount()));
									}

									// ── Cache miss or stale: run LLM ──
									int issueCountCap = customApiConfig.getSlingshotHygieneIssueCountPerSprint();
									if (totalIssueCount > issueCountCap) {
										log.warn(
												"Story Hygiene (kpi311): issue cap of {} hit for sprint '{}' — {} issues available, sampling top {}.",
												issueCountCap,
												sprintName,
												totalIssueCount,
												issueCountCap);
									}
									List<JiraIssue> jiraIssueSubset =
											totalIssueCount <= issueCountCap
													? jiraIssues
													: jiraIssues.stream()
															.sorted(
																	Comparator.comparingInt(
																					(JiraIssue ji) ->
																							HygienePromptBuilder.priorityRank(ji.getPriority()))
																			.thenComparing(
																					ji ->
																							ji.getChangeDate() != null ? ji.getChangeDate() : "",
																					Comparator.reverseOrder()))
															.limit(issueCountCap)
															.toList();
									int sampledCount = jiraIssueSubset.size();
									List<ObjectNode> issueNodes =
											jiraIssueSubset.stream()
													.map(
															ji ->
																	HygienePromptBuilder.buildIssueNode(
																			ji, anchorFieldNames, cycleTimeGroupList, objectMapper))
													.toList();
									String issuesJson =
											HygienePromptBuilder.buildIssuesJson(issueNodes, objectMapper);
									if (issuesJson == null) {
										return CompletableFuture.completedFuture(
												new SprintHygieneOutcome(
														emptyDataCount(sprintId, sprintName, projectName),
														Collections.emptyList(),
														0,
														0));
									}

									return CompletableFuture.supplyAsync(
													() ->
															computeSprintHygiene(
																	sprintId,
																	sprintName,
																	projectName,
																	promptService.getProjectHygienePrompt(hygieneRules, issuesJson),
																	sampledCount,
																	totalIssueCount,
																	ruleSetHash,
																	basicProjectConfigId,
																	issueUrlMap),
													hygieneAiExecutor)
											.exceptionally(
													ex -> {
														log.error(
																"Hygiene evaluation failed for sprint '{}'({}): {}",
																sprintName,
																sprintId,
																ex.getMessage(),
																ex);
														return new SprintHygieneOutcome(
																emptyDataCount(sprintId, sprintName, projectName),
																Collections.emptyList(),
																0,
																0);
													});
								})
						.toList();

		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		List<SprintHygieneOutcome> outcomes = futures.stream().map(CompletableFuture::join).toList();

		Map<String, String> sprintStartDateMap =
				sprintDetailsList.stream()
						.filter(sd -> sd.getSprintID() != null)
						.collect(
								Collectors.toMap(
										SprintDetails::getSprintID,
										sd -> sd.getStartDate() != null ? sd.getStartDate() : "",
										(a, b) -> a));

		List<DataCount> dataCountList =
				outcomes.stream()
						.map(SprintHygieneOutcome::dataCount)
						.filter(Objects::nonNull)
						.sorted(
								Comparator.comparing(
										dc -> sprintStartDateMap.getOrDefault(dc.getsSprintID(), ""),
										Comparator.nullsLast(Comparator.naturalOrder())))
						.collect(Collectors.toList());

		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			List<KPIExcelData> excelData =
					outcomes.stream().flatMap(o -> o.excelRows().stream()).toList();
			kpiElement.setExcelData(excelData);
		}
		kpiElement.setExcelColumns(KPIExcelColumn.STORY_HYGIENE.getColumns());

		kpiElement.setScoreFactor(
				outcomes.stream().mapToInt(SprintHygieneOutcome::evaluatedIssueCount).sum());
		kpiElement.setValidScoreFactor(
				outcomes.stream().mapToInt(SprintHygieneOutcome::totalPassedIssues).sum());

		if (!dataCountList.isEmpty()) {
			double rawProjectScore =
					dataCountList.stream()
									.map(DataCount::getValue)
									.filter(Objects::nonNull)
									.mapToDouble(v -> ((Number) v).doubleValue())
									.sum()
							/ dataCountList.size();
			kpiElement.setProjectScore(Math.round(rawProjectScore * 100.0) / 100.0);
		}

		node.setValue(dataCountList);
	}

	/** Calls the LLM for a single sprint, persists the result to MongoDB, and returns the outcome. */
	private SprintHygieneOutcome computeSprintHygiene(
			String sprintId,
			String sprintName,
			String projectName,
			String prompt,
			int sampledCount,
			int totalIssueCount,
			String ruleSetHash,
			String basicProjectConfigId,
			Map<String, String> issueUrlMap) {

		String responseContent;
		try {
			ChatGenerationResponseDTO chatGenerationResponseDTO =
					aiGatewayClient.generate(ChatGenerationRequest.builder().prompt(prompt).build());
			responseContent =
					chatGenerationResponseDTO == null ? null : chatGenerationResponseDTO.content();
			log.info(
					"kpi311 [{}]: responseChars={}",
					sprintName,
					responseContent != null ? responseContent.length() : 0);
			log.debug("kpi311 [{}]: content={}", sprintName, responseContent);
		} catch (Exception ex) {
			log.error(
					"AI Gateway call failed for sprint '{}' ({}): {}",
					sprintName,
					sprintId,
					ex.getMessage(),
					ex);
			responseContent = null;
		}

		if (responseContent == null || responseContent.isBlank()) {
			log.warn(
					"AI Gateway returned blank content for sprint '{}' ({}) — skipping sprint (not persisted)",
					sprintName,
					sprintId);
			return new SprintHygieneOutcome(
					emptyDataCount(sprintId, sprintName, projectName), Collections.emptyList(), 0, 0);
		}

		List<HygieneKpiResponseDTO> issueVerdicts = hygieneKpiParser.parse(responseContent);

		// Embed the issue URL into each verdict so the stored document is
		// self-contained
		// for the Excel path — no jira_issues query needed on cache hits.
		issueVerdicts.forEach(v -> v.setIssueUrl(issueUrlMap.getOrDefault(v.getIssueKey(), "")));

		// Atomic upsert — avoids DuplicateKeyException when two concurrent requests
		// both find a cache miss for the same sprint and race to insert.
		Query query =
				new Query(
						Criteria.where("basicProjectConfigId")
								.is(basicProjectConfigId)
								.and("sprintId")
								.is(sprintId));
		Update update =
				new Update()
						.set("sprintName", sprintName)
						.set("ruleSetHash", ruleSetHash)
						.set("sampledIssueCount", sampledCount)
						.set("totalIssueCount", totalIssueCount)
						.set("issueVerdicts", issueVerdicts)
						.set("computedAt", Instant.now());
		mongoTemplate.upsert(query, update, StoryHygieneSprintResult.class);

		return buildOutcomeFromVerdicts(
				sprintId, sprintName, projectName, issueVerdicts, sampledCount, totalIssueCount);
	}

	/**
	 * Derives {@link SprintHygieneOutcome} from a list of issue verdicts. Called for both cache-hit
	 * (from stored {@code issueVerdicts}) and post-LLM paths so the aggregation logic lives in one
	 * place. Issue URLs are read from {@link HygieneKpiResponseDTO#getIssueUrl()} — they are embedded
	 * at persist time so no extra {@code jira_issues} query is needed.
	 */
	private SprintHygieneOutcome buildOutcomeFromVerdicts(
			String sprintId,
			String sprintName,
			String projectName,
			List<HygieneKpiResponseDTO> issueVerdicts,
			int sampledCount,
			int totalIssueCount) {

		if (CollectionUtils.isEmpty(issueVerdicts)) {
			return new SprintHygieneOutcome(
					emptyDataCount(sprintId, sprintName, projectName), List.of(), 0, 0);
		}

		List<HygieneKpiResponseDTO.RuleResult> allRuleResults =
				issueVerdicts.stream()
						.filter(Objects::nonNull)
						.map(HygieneKpiResponseDTO::getResults)
						.filter(Objects::nonNull)
						.flatMap(List::stream)
						.filter(Objects::nonNull)
						.toList();

		Set<String> ruleNames =
				allRuleResults.stream()
						.map(HygieneKpiResponseDTO.RuleResult::getRule)
						.collect(Collectors.toSet());

		long totalIssues = issueVerdicts.stream().filter(Objects::nonNull).count();

		Map<String, Double> rulePassRates = new LinkedHashMap<>();
		ruleNames.forEach(
				rule -> {
					long passed =
							allRuleResults.stream()
									.filter(rr -> rule.equalsIgnoreCase(rr.getRule()))
									.filter(rr -> "Passed".equalsIgnoreCase(rr.getStatus()))
									.count();
					double percentage = totalIssues == 0 ? 0.0 : (passed * 100.0) / totalIssues;
					rulePassRates.put(rule, Math.round(percentage * 100.0) / 100.0);
				});

		Map<String, Double> passedPercentageByRule =
				rulePassRates.entrySet().stream()
						.sorted(Map.Entry.comparingByKey())
						.collect(
								Collectors.toMap(
										Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

		OptionalDouble sprintScore =
				issueVerdicts.stream()
						.filter(Objects::nonNull)
						.map(HygieneKpiResponseDTO::getHygieneScore)
						.filter(Objects::nonNull)
						.mapToInt(Integer::intValue)
						.average();
		double score = sprintScore.isPresent() ? sprintScore.getAsDouble() : 0d;

		int passedIssues =
				Math.toIntExact(
						issueVerdicts.stream()
								.filter(
										dto ->
												dto.getOverallStatus() != null
														&& dto.getOverallStatus().equalsIgnoreCase("READY"))
								.count());

		DataCount dataCount =
				buildDataCount(sprintId, sprintName, projectName, score, passedPercentageByRule);

		dataCount.getHoverValue().put("Sampled Issue Count", sampledCount);
		dataCount.getHoverValue().put("Passed Issue Count", passedIssues);
		if (sampledCount < totalIssueCount) {
			dataCount
					.getHoverValue()
					.put("Issue Count", sampledCount + " of " + totalIssueCount + " (Capped)");
		}

		List<KPIExcelData> excelRows = new ArrayList<>();
		KPIExcelUtility.populateStoryHygieneExcelData(
				excelRows, sprintName != null ? sprintName : sprintId, issueVerdicts);

		return new SprintHygieneOutcome(dataCount, excelRows, passedIssues, (int) totalIssues);
	}

	private DataCount emptyDataCount(String sprintId, String sprintName, String projectName) {
		DataCount dc = buildDataCount(sprintId, sprintName, projectName, 0.0, new HashMap<>());
		dc.getHoverValue().put("Evaluation Status", "Failed");
		return dc;
	}

	private DataCount buildDataCount(
			String sprintId,
			String sprintName,
			String projectName,
			double score,
			Map<String, Double> passedPercentageByRule) {
		double roundedScore = Math.round(score * 100.0) / 100.0;
		String displayName = sprintName != null ? sprintName : sprintId;

		DataCount dataCount = new DataCount();
		dataCount.setData(String.format("%.2f", roundedScore));
		dataCount.setValue(roundedScore);
		dataCount.setSProjectName(projectName);
		dataCount.setSSprintID(sprintId);
		dataCount.setSSprintName(displayName);
		Map<String, Object> hoverValue = new HashMap<>();
		hoverValue.put("Hygiene Score", roundedScore);
		dataCount.setHoverValue(hoverValue);
		dataCount.setDrillDown(passedPercentageByRule);
		return dataCount;
	}

	/**
	 * Bundle returned by the per-sprint pipeline — trend point + excel rows + passed/evaluated
	 * counts.
	 */
	private record SprintHygieneOutcome(
			DataCount dataCount,
			List<KPIExcelData> excelRows,
			int totalPassedIssues,
			int evaluatedIssueCount) {}
}
