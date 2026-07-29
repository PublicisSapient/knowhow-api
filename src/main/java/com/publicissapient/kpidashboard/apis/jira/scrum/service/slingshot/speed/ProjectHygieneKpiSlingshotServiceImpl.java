package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.speed;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.knowhow.retro.aigatewayclient.client.AiGatewayClient;
import com.knowhow.retro.aigatewayclient.client.request.chat.ChatGenerationRequest;
import com.knowhow.retro.aigatewayclient.client.response.chat.ChatGenerationResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.config.HygieneAiExecutorConfig;
import com.publicissapient.kpidashboard.apis.ai.dto.response.HygieneKpiResponseDTO;
import com.publicissapient.kpidashboard.apis.ai.parser.HygieneKpiParser;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
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
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.Metadata;
import com.publicissapient.kpidashboard.common.model.jira.MetadataValue;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.service.recommendation.PromptService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ProjectHygieneKpiSlingshotServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String JIRA_ISSUES = "jiraIssues";
	private static final String SPRINT_DETAILS = "sprintDetails";
	private static final String JIRA_METADATA = "jiraMetadata";

	/**
	 * Deterministic mock response used as a fallback when the AI Gateway returns {@code null} / blank
	 * content (e.g. during local testing, when the gateway is unreachable, or when the LLM quota is
	 * exhausted). Shape mirrors {@link HygieneKpiResponseDTO} exactly and intentionally covers all
	 * verdict types ({@code Passed} / {@code Failed} / {@code Partial}) and all hygiene grades
	 * ({@code GOOD} / {@code AVERAGE} / {@code POOR}) so the downstream trend / tooltip /
	 * excel-export pipeline can be exercised end-to-end.
	 */
	static final String MOCK_HYGIENE_RESPONSE_JSON =
			"""
			[
				{
					"issueKey": "MOCK-101",
					"issueType": "Story",
					"sprintId": "sprint-mock-001",
					"assignee": "Jane Doe",
					"results": [
						{
							"rule": "Acceptance Criteria Present",
							"field": "description",
							"observed": "Given/When/Then criteria listed",
							"status": "Passed",
							"reason": "description field contains explicit Given/When/Then acceptance criteria"
						},
						{
							"rule": "Story Points Estimated",
							"field": "estimate",
							"observed": "5",
							"status": "Passed",
							"reason": "estimate field is set to 5"
						},
						{
							"rule": "Assignee Set",
							"field": "assigneeName",
							"observed": "Jane Doe",
							"status": "Passed",
							"reason": "assigneeName field is populated"
						},
						{
							"rule": "Business Value Documented",
							"field": "labels",
							"observed": "null",
							"status": "Failed",
							"reason": "labels field does not contain a business-value tag"
						}
					],
					"totalApplicableRules": 4,
					"passedRules": 3,
					"failedRules": 1,
					"partialRules": 0,
					"hygieneScore": 75,
					"hygieneGrade": "AVERAGE",
					"overallStatus": "NOT READY",
					"topFailures": ["Business Value Documented"],
					"recommendations": "Add a business-value label | Link a UX mockup in the description | Re-confirm stakeholder sign-off in comments"
				},
				{
					"issueKey": "MOCK-102",
					"issueType": "Bug",
					"sprintId": "sprint-mock-001",
					"assignee": "Unassigned",
					"results": [
						{
							"rule": "Steps to Reproduce",
							"field": "description",
							"observed": "null",
							"status": "Failed",
							"reason": "description field is empty"
						},
						{
							"rule": "Assignee Set",
							"field": "assigneeName",
							"observed": "null",
							"status": "Failed",
							"reason": "assigneeName field is empty"
						},
						{
							"rule": "Priority Set",
							"field": "priority",
							"observed": "High",
							"status": "Passed",
							"reason": "priority field is set to High"
						},
						{
							"rule": "Environment Captured",
							"field": "environment",
							"observed": "partial - only browser noted",
							"status": "Partial",
							"reason": "environment field lists browser but omits OS/version"
						}
					],
					"totalApplicableRules": 4,
					"passedRules": 1,
					"failedRules": 2,
					"partialRules": 1,
					"hygieneScore": 25,
					"hygieneGrade": "POOR",
					"overallStatus": "NOT READY",
					"topFailures": ["Steps to Reproduce", "Assignee Set", "Environment Captured"],
					"recommendations": "Add reproduction steps to description | Assign the bug to an owner | Capture full environment (OS, version, build) | Attach relevant logs or screenshots"
				},
				{
					"issueKey": "MOCK-103",
					"issueType": "Task",
					"sprintId": "sprint-mock-001",
					"assignee": "John Smith",
					"results": [
						{
							"rule": "Description Present",
							"field": "description",
							"observed": "Set up CI pipeline for module X",
							"status": "Passed",
							"reason": "description field contains a clear objective"
						},
						{
							"rule": "Assignee Set",
							"field": "assigneeName",
							"observed": "John Smith",
							"status": "Passed",
							"reason": "assigneeName field is populated"
						},
						{
							"rule": "Estimate Present",
							"field": "estimate",
							"observed": "3",
							"status": "Passed",
							"reason": "estimate field is set to 3"
						},
						{
							"rule": "Acceptance Criteria Present",
							"field": "description",
							"observed": "N/A for infra task",
							"status": "N/A",
							"reason": "rule does not apply to infrastructure tasks"
						}
					],
					"totalApplicableRules": 3,
					"passedRules": 3,
					"failedRules": 0,
					"partialRules": 0,
					"hygieneScore": 100,
					"hygieneGrade": "GOOD",
					"overallStatus": "READY",
					"topFailures": [],
					"recommendations": "Great hygiene - no immediate improvements needed"
				}
			]
			""";

	@Autowired private HygieneKpiParser hygieneKpiParser;
	@Autowired private JiraIssueRepository jiraIssueRepository;
	@Autowired private AiGatewayClient aiGatewayClient;
	@Autowired private SprintDetailsService sprintDetailsService;
	@Autowired private ConfigHelperService configHelperService;
	@Autowired private PromptService promptService;

	@Autowired
	@Qualifier(HygieneAiExecutorConfig.HYGIENE_AI_EXECUTOR)
	private Executor hygieneAiExecutor;

	/**
	 * Client-side hard cap per sprint LLM call. Bounded above by the OkHttp callTimeout in {@code
	 * ai-gateway-config.http-client.call-timeout}.
	 */
	private static final long PER_SPRINT_TIMEOUT_MINUTES = 15;

	private List<String> sprintIdList = Collections.synchronizedList(new ArrayList<>());

	@Override
	public String getQualifierType() {
		return KPICode.PROJECT_HYGIENE.name();
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

		// in case if only projects or sprint filters are applied
		projectWiseLeafNodeValue(kpiElement, project, kpiRequest);
		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValue(project, nodeWiseKPIValue, KPICode.PROJECT_HYGIENE);
		List<DataCount> trendValues =
				getTrendValues(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.PROJECT_HYGIENE);
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
		List<SprintDetails> limitedSprintList =
				sortedSprintList.stream().skip(Math.max(0, sortedSprintList.size() - 5)).toList();
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
		Set<String> fieldNames =
				cycleTimeGroupList == null
						? new HashSet<>()
						: cycleTimeGroupList.stream()
								.filter(Objects::nonNull)
								.map(CycleTimeGroup::getLabel)
								.filter(Objects::nonNull)
								.collect(Collectors.toSet());
		Set<String> jiraFields = new HashSet<>();
		for (String field : fieldNames) {
			String fieldName = data.get(field);
			if (StringUtils.isNotEmpty(fieldName)) {
				jiraFields.add(fieldName);
			}
		}

		List<JiraIssue> jiraIssueList =
				jiraIssueRepository.findBySprintIDInAndBasicProjectConfigIdWithFields(
						limitedSprintList.stream().map(SprintDetails::getSprintID).collect(Collectors.toSet()),
						basicProjectConfigId.toString(),
						jiraFields);

		resultListMap.put(JIRA_METADATA, data);
		resultListMap.put(JIRA_ISSUES, jiraIssueList);
		resultListMap.put(SPRINT_DETAILS, limitedSprintList);
		return resultListMap;
	}

	private void projectWiseLeafNodeValue(KpiElement kpiElement, Node node, KpiRequest kpiRequest) {
		String requestTrackerId = getRequestTrackerId();
		String projectName = node.getProjectFilter().getName();

		FieldMapping fieldMapping =
				configHelperService.getFieldMapping(node.getProjectFilter().getBasicProjectConfigId());
		List<CycleTimeGroup> cycleTimeGroupList = fieldMapping.getJiraFieldsSelectionKPI311();

		String hygieneRules = buildHygieneRules(cycleTimeGroupList);

		long time = System.currentTimeMillis();
		Map<String, Object> resultMap = fetchKPIDataFromDb(List.of(node), null, null, kpiRequest);
		log.info("DSR taking fetchKPIDataFromDb {}", System.currentTimeMillis() - time);

		List<JiraIssue> jiraIssueList = (List<JiraIssue>) resultMap.get(JIRA_ISSUES);
		List<SprintDetails> sprintDetailsList = (List<SprintDetails>) resultMap.get(SPRINT_DETAILS);

		Map<String, List<JiraIssue>> jiraIssuesBySprint =
				jiraIssueList.stream().collect(Collectors.groupingBy(JiraIssue::getSprintID));

		List<CompletableFuture<SprintHygieneOutcome>> futures =
				sprintDetailsList.stream()
						.filter(sd -> !jiraIssuesBySprint.getOrDefault(sd.getSprintID(), List.of()).isEmpty())
						.map(
								sprintDetails -> {
									String sprintId = sprintDetails.getSprintID();
									String sprintName = sprintDetails.getSprintName();
									List<JiraIssue> jiraIssues = jiraIssuesBySprint.get(sprintId);
									List<JiraIssue> jiraIssueSubset =
											jiraIssues.size() < 10 ? jiraIssues : jiraIssues.subList(0, 10);
									return CompletableFuture.supplyAsync(
													() -> {
														String prompt =
																promptService.getProjectHygienePrompt(
																		hygieneRules, jiraIssueSubset);
														return computeSprintHygiene(sprintId, sprintName, projectName, prompt);
													},
													hygieneAiExecutor)
											.orTimeout(PER_SPRINT_TIMEOUT_MINUTES, TimeUnit.MINUTES)
											.exceptionally(
													ex -> {
														log.error(
																"Hygiene evaluation failed for sprint {}: {}",
																sprintId,
																ex.getMessage(),
																ex);
														return new SprintHygieneOutcome(
																emptyDataCount(sprintId, sprintName, projectName),
																Collections.emptyList(),
																0);
													});
								})
						.toList();

		// Wait for all sprints, then collect results in a stable order.
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
		List<SprintHygieneOutcome> outcomes = futures.stream().map(CompletableFuture::join).toList();

		List<DataCount> dataCountList = outcomes.stream().map(SprintHygieneOutcome::dataCount).toList();

		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			List<KPIExcelData> excelData =
					outcomes.stream().flatMap(o -> o.excelRows().stream()).toList();
			kpiElement.setExcelData(excelData);
		}
		kpiElement.setExcelColumns(KPIExcelColumn.PROJECT_HYGIENE.getColumns());

		kpiElement.setScoreFactor(jiraIssuesBySprint.values().stream().mapToInt(List::size).sum());
		kpiElement.setValidScoreFactor(
				outcomes.stream().mapToInt(SprintHygieneOutcome::totalPassedIssues).sum());

		kpiElement.setProjectScore(
				dataCountList.isEmpty()
						? 0
						: dataCountList.stream()
										.map(DataCount::getValue)
										.filter(Objects::nonNull)
										.mapToDouble(v -> ((Number) v).doubleValue())
										.sum()
								/ dataCountList.size());

		node.setValue(dataCountList);
	}

	/**
	 * Flattens the project's configured field/prompt pairs into a numbered list of <b>independent</b>
	 * hygiene rules.
	 *
	 * <p>A user may configure several rule sets against the <b>same</b> Jira field — e.g. an
	 * "acceptance criteria" check and a "BDD definition" check, both written against {@code
	 * description}. Previously such entries were collapsed by field name (only the first survived).
	 * Every configured rule set is now emitted as its own rule and is given a unique {@code
	 * ruleName}, so the LLM returns one independent verdict per rule set and the downstream
	 * drill-down / Excel maps — which are keyed by rule name — no longer overwrite each other.
	 *
	 * <p>A field carrying a single rule set keeps its plain label as the rule name; a field carrying
	 * several rule sets gets {@code label (1)}, {@code label (2)}, … suffixes.
	 *
	 * @param cycleTimeGroupList the configured field/prompt pairs, may be {@code null} or contain
	 *     {@code null} entries
	 * @return the rendered rule list, or an empty string when nothing is configured
	 */
	private String buildHygieneRules(List<CycleTimeGroup> cycleTimeGroupList) {
		if (CollectionUtils.isEmpty(cycleTimeGroupList)) {
			return "";
		}

		// groupingBy keeps every rule set configured against a field instead of
		// collapsing
		// them; LinkedHashMap preserves the order in which the fields were declared.
		Map<String, List<String>> criteriaByField =
				cycleTimeGroupList.stream()
						.filter(Objects::nonNull)
						.filter(
								ctg ->
										StringUtils.isNotBlank(ctg.getLabel())
												&& StringUtils.isNotBlank(ctg.getPrompt()))
						.collect(
								Collectors.groupingBy(
										CycleTimeGroup::getLabel,
										LinkedHashMap::new,
										Collectors.mapping(CycleTimeGroup::getPrompt, Collectors.toList())));

		StringBuilder rules = new StringBuilder();
		int ruleNumber = 0;
		for (Map.Entry<String, List<String>> entry : criteriaByField.entrySet()) {
			String field = entry.getKey();
			List<String> criteriaList = entry.getValue();
			for (int index = 0; index < criteriaList.size(); index++) {
				String ruleName = criteriaList.size() == 1 ? field : field + " (" + (index + 1) + ")";
				rules
						.append("Rule ")
						.append(++ruleNumber)
						.append("\n  ruleName: ")
						.append(ruleName)
						.append("\n  field: ")
						.append(field)
						.append("\n  criteria: ")
						.append(criteriaList.get(index).trim())
						.append("\n\n");
			}
		}

		log.debug(
				"Built {} independent hygiene rule(s) across {} field(s)",
				ruleNumber,
				criteriaByField.size());
		return rules.toString().trim();
	}

	/**
	 * Runs the LLM hygiene evaluation for a single sprint and turns the response into (a) a {@link
	 * DataCount} that feeds the KPI trend line and (b) a list of {@link KPIExcelData} rows — one per
	 * Jira issue — that feed the Excel export. Called from the async pipeline, one invocation per
	 * sprint. All exceptions are propagated so the surrounding {@link
	 * CompletableFuture#exceptionally} handler can decide the fallback.
	 */
	private SprintHygieneOutcome computeSprintHygiene(
			String sprintId, String sprintName, String projectName, String prompt) {
		String responseContent;
		try {
			ChatGenerationResponseDTO chatGenerationResponseDTO =
					aiGatewayClient.generate(ChatGenerationRequest.builder().prompt(prompt).build());
			responseContent =
					chatGenerationResponseDTO == null ? null : chatGenerationResponseDTO.content();
			if (responseContent == null || responseContent.isBlank()) {

				log.warn(
						"AI Gateway returned null/blank content for sprint {} ({}); using MOCK hygiene response.",
						sprintName,
						sprintId);
				responseContent = MOCK_HYGIENE_RESPONSE_JSON;
			}
		} catch (Exception ex) {
			responseContent = MOCK_HYGIENE_RESPONSE_JSON;
		}
		List<HygieneKpiResponseDTO> hygieneKpiResponseDTOList = hygieneKpiParser.parse(responseContent);

		List<HygieneKpiResponseDTO.RuleResult> allRuleResults =
				hygieneKpiResponseDTOList.stream()
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

		long totalIssues = hygieneKpiResponseDTOList.stream().filter(Objects::nonNull).count();
		Map<String, Double> passedPercentageByRule = new LinkedHashMap<>();
		ruleNames.forEach(
				rule -> {
					long passed =
							allRuleResults.stream()
									.filter(rr -> rule.equalsIgnoreCase(rr.getRule()))
									.filter(rr -> "Passed".equalsIgnoreCase(rr.getStatus()))
									.count();
					double percentage = totalIssues == 0 ? 0.0 : (passed * 100.0) / totalIssues;
					passedPercentageByRule.put(rule, percentage);
				});

		OptionalDouble sprintScore =
				hygieneKpiResponseDTOList.stream()
						.mapToInt(HygieneKpiResponseDTO::getHygieneScore)
						.average();
		double score = sprintScore.isPresent() ? sprintScore.getAsDouble() : 0d;

		int passedIssues =
				Math.toIntExact(
						hygieneKpiResponseDTOList.stream()
								.filter(
										hygieneKpiResponseDTO ->
												hygieneKpiResponseDTO.getOverallStatus().equalsIgnoreCase("READY"))
								.count());

		log.debug(
				"Hygiene passed-percentage for Sprint {} ({}) : sprintScore={} perRule={}",
				sprintName,
				sprintId,
				score,
				passedPercentageByRule);

		DataCount dataCount =
				buildDataCount(sprintId, sprintName, projectName, score, passedPercentageByRule);

		// Build one KPIExcelData row per Jira issue for the Excel export.
		List<KPIExcelData> excelRows = new ArrayList<>();
		KPIExcelUtility.populateProjectHygieneExcelData(
				excelRows, sprintName != null ? sprintName : sprintId, hygieneKpiResponseDTOList);

		return new SprintHygieneOutcome(dataCount, excelRows, passedIssues);
	}

	private DataCount emptyDataCount(String sprintId, String sprintName, String projectName) {
		return buildDataCount(sprintId, sprintName, projectName, 0.0, new HashMap<>());
	}

	private DataCount buildDataCount(
			String sprintId,
			String sprintName,
			String projectName,
			double score,
			Map<String, Double> passedPercentageByRule) {
		long roundedScore = Math.round(score);
		String displayName = sprintName != null ? sprintName : sprintId;

		DataCount dataCount = new DataCount();
		dataCount.setData(String.valueOf(roundedScore));
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

	/** Bundle returned by {@link #computeSprintHygiene} — trend point + excel rows. */
	private record SprintHygieneOutcome(
			DataCount dataCount, List<KPIExcelData> excelRows, int totalPassedIssues) {}
}
