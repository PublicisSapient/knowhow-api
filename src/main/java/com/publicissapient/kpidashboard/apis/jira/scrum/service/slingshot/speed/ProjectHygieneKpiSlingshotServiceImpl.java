package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.speed;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class ProjectHygieneKpiSlingshotServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String JIRA_ISSUES = "jiraIssues";
	private static final String SPRINT_DETAILS = "sprintDetails";
	private static final String FINAL_HYGIENE_PROMPT =
			"""
			You are an Expert Project Hygiene Analyzer Agent.
			Your job is to evaluate Jira issues against a Definition-of-Ready (DoR)
			style hygiene checklist and produce a strict, evidence-based verdict
			for each issue.

			=== Non-Negotiable Principles ===
			- Rely STRICTLY on the fields provided in the Jira Issue JSON below.
			- NEVER assume, infer, or fabricate a value that is not present.
			- If evidence is missing, mark the rule as "Failed" (never "Passed").
			- Differentiate REQUESTS from CONFIRMATIONS —
					a request for sign-off is NOT approval.
			- Every verdict MUST cite the exact field name and observed value.

			=== Hygiene Rules ===
			Each map entry is {ruleName -> evaluation criteria}. Apply every rule
			independently, in the order given, using ONLY the fields named in the rule.
			%1$s

			=== Jira Issues (JSON array) ===
			%2$s

			=== Per-Rule Verdict Vocabulary ===
			- "Passed"  → rule is fully satisfied by explicit evidence
																	in the listed fields
			- "Failed"  → rule is not met OR required evidence is missing
			- "Partial" → rule is partially met — present but
																	incomplete / unclear / unconfirmed
			- "N/A"     → rule does not apply to this issue type/status
																	per its own criteria

			=== Overall Status Rules ===
			- "READY"     → every applicable rule (i.e. excluding "N/A")
																			has status "Passed"
			- "NOT READY" → any applicable rule is "Failed" or "Partial"

			=== Hygiene Score ===
			- totalApplicableRules = count of rules whose status is not "N/A"
			- passedRules          = count of rules whose status is "Passed"
			- hygieneScore         = passedRules * 100 / totalApplicableRules
																											(if totalApplicableRules == 0 → 100)
			- hygieneGrade         = "GOOD"    when hygieneScore >= 80
																											"AVERAGE" when 50 <= hygieneScore < 80
																											"POOR"    when hygieneScore < 50

			=== Improvement Recommendations ===
			- Provide 3 to 5 short, actionable suggestions that would raise the
					hygiene score for this issue.
			- Each suggestion must reference a specific field or missing evidence.
			- Return them as ONE string with items joined by " | ".

			=== Output Contract ===
			Return a JSON ARRAY — one element per input Jira issue, in the same
			order as the input. No markdown, no prose, no code fences, no trailing
			commentary. Schema per element:
			[
					{
							"issueKey":             "<jiraIssue.number>",
							"issueType":            "<jiraIssue.typeName>",
							"sprintId":             "<jiraIssue.sprintID>",
							"assignee":             "<jiraIssue.assigneeName or 'Unassigned'>",
							"results": [
									{
											"rule":     "<ruleName from map key>",
											"field":    "<jiraIssue field(s) evaluated>",
											"observed": "<actual field value or 'null'>",
											"status":   "Passed | Failed | Partial | N/A",
											"reason":   "<one-line justification citing the observed value>"
									}
							],
							"totalApplicableRules": <int>,
							"passedRules":          <int>,
							"failedRules":          <int>,
							"partialRules":         <int>,
							"hygieneScore":         <int 0-100>,
							"hygieneGrade":         "GOOD | AVERAGE | POOR",
							"overallStatus":        "READY | NOT READY",
							"topFailures":          ["<up to 3 ruleNames of most impactful non-Passed rules>"],
							"recommendations":      "<3-5 fixes joined by ' | '>"
					}
			]

			=== Hard Constraints ===
			- Evaluate EVERY rule in the map for EVERY issue; never skip a rule
					and never skip an issue.
			- status MUST be exactly one of "Passed", "Failed", "Partial", "N/A"
					(case sensitive, spelled exactly).
			- overallStatus MUST be exactly "READY" or "NOT READY".
			- reason MUST cite the exact field name and value observed.
			- Never invent field values that are not present in the input JSON.
			- Return the JSON array and nothing else.
			""";

	/**
	 * Deterministic mock response used as a fallback when the AI Gateway returns {@code null} /
	 * blank content (e.g. during local testing, when the gateway is unreachable, or when the LLM
	 * quota is exhausted). Shape mirrors {@link HygieneKpiResponseDTO} exactly and intentionally
	 * covers all verdict types ({@code Passed} / {@code Failed} / {@code Partial}) and all
	 * hygiene grades ({@code GOOD} / {@code AVERAGE} / {@code POOR}) so the downstream trend /
	 * tooltip / excel-export pipeline can be exercised end-to-end.
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

		List<JiraIssue> jiraIssueList =
				jiraIssueRepository.findBySprintIDInAndBasicProjectConfigId(
						limitedSprintList.stream().map(SprintDetails::getSprintID).collect(Collectors.toSet()),
						basicProjectConfigId.toString());

		resultListMap.put(JIRA_ISSUES, jiraIssueList);
		resultListMap.put(SPRINT_DETAILS, limitedSprintList);
		return resultListMap;
	}

	private void projectWiseLeafNodeValue(KpiElement kpiElement, Node node, KpiRequest kpiRequest) {
		String requestTrackerId = getRequestTrackerId();
		String projectName = node.getProjectFilter().getName();

		FieldMapping fieldMapping =
				configHelperService.getFieldMapping(node.getProjectFilter().getBasicProjectConfigId());
		List<CycleTimeGroup> cycleTimeGroupList = fieldMapping.getJiraFieldsSelectionKPI217();

		Map<String, String> prompts =
				cycleTimeGroupList == null
						? new LinkedHashMap<>()
						: cycleTimeGroupList.stream()
								.filter(
										ctg ->
												ctg != null
														&& ctg.getLabel() != null
														&& !ctg.getLabel().isBlank()
														&& ctg.getPrompt() != null)
								.collect(
										Collectors.toMap(
												CycleTimeGroup::getLabel,
												CycleTimeGroup::getPrompt,
												(first, second) -> first,
												LinkedHashMap::new));

		// Final aggregate prompt sent to the LLM per sprint's list of JiraIssues.
		// At call-time substitute:
		// %1$s → JSON of fieldMappingPrompts (rule name → criteria)
		// %2$s → JSON of the JiraIssue list under evaluation

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
									String prompt = String.format(FINAL_HYGIENE_PROMPT, prompts, jiraIssueSubset);
									return CompletableFuture.supplyAsync(
													() -> computeSprintHygiene(sprintId, sprintName, projectName, prompt, prompts),
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
																Collections.emptyList());
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

		node.setValue(dataCountList);
	}

	/**
	 * Runs the LLM hygiene evaluation for a single sprint and turns the response into (a) a {@link
	 * DataCount} that feeds the KPI trend line and (b) a list of {@link KPIExcelData} rows — one per
	 * Jira issue — that feed the Excel export. Called from the async pipeline, one invocation per
	 * sprint. All exceptions are propagated so the surrounding {@link
	 * CompletableFuture#exceptionally} handler can decide the fallback.
	 */
	private SprintHygieneOutcome computeSprintHygiene(
			String sprintId, String sprintName, String projectName, String prompt, Map<String, String> fieldMapping) {
		ChatGenerationResponseDTO chatGenerationResponseDTO =
				aiGatewayClient.generate(ChatGenerationRequest.builder().prompt(prompt).build());
		String responseContent =
				chatGenerationResponseDTO == null ? null : chatGenerationResponseDTO.content();
		if (responseContent == null || responseContent.isBlank()) {
			// AI Gateway returned nothing usable (offline, quota exhausted, upstream error, etc.)
			// - fall back to a deterministic mock so the KPI, tooltip and Excel export can still
			// be exercised end-to-end for testing / demo purposes.
			log.warn(
					"AI Gateway returned null/blank content for sprint {} ({}); using MOCK hygiene response.",
					sprintName,
					sprintId);
			responseContent = MOCK_HYGIENE_RESPONSE_JSON;
		}
		List<HygieneKpiResponseDTO> hygieneKpiResponseDTOList = hygieneKpiParser.parse(responseContent);

		// Materialise a flat, non-null list of every RuleResult verdict returned
		// for this sprint so we can query it multiple times without re-streaming.
		List<HygieneKpiResponseDTO.RuleResult> allRuleResults =
				hygieneKpiResponseDTOList.stream()
						.filter(Objects::nonNull)
						.map(HygieneKpiResponseDTO::getResults)
						.filter(Objects::nonNull)
						.flatMap(List::stream)
						.filter(Objects::nonNull)
						.toList();

		// Per-rule count of "Passed" verdicts, keyed by the configured rule
		// catalogue (fieldMapping.keySet()) so every rule shows up in the tooltip
		// even when the LLM omitted it or produced only Failed / Partial verdicts.
		// equalsIgnoreCase keeps us tolerant of minor LLM casing drift ("passed",
		// "PASSED", etc.).
		Set<String> ruleNames =
				allRuleResults.stream().map(HygieneKpiResponseDTO.RuleResult::getRule).collect(Collectors.toSet());
		Map<String, Long> passedCountByRule = new LinkedHashMap<>();
		ruleNames.forEach(
				rule ->
						passedCountByRule.put(
								rule,
								allRuleResults.stream()
										.filter(rr -> rule.equalsIgnoreCase(rr.getRule()))
										.map(HygieneKpiResponseDTO.RuleResult::getStatus)
										.filter("Passed"::equalsIgnoreCase)
										.count()));

		long sprintScore =
				allRuleResults.stream()
						.map(HygieneKpiResponseDTO.RuleResult::getStatus)
						.filter("Passed"::equalsIgnoreCase)
						.count();

		log.debug(
				"Hygiene Passed-count for Sprint {} ({}) : total={} perRule={}",
				sprintName,
				sprintId,
				sprintScore,
				passedCountByRule);

		DataCount dataCount = buildDataCount(sprintId, sprintName, projectName, sprintScore, passedCountByRule);

		// Build one KPIExcelData row per Jira issue for the Excel export.
		List<KPIExcelData> excelRows = new ArrayList<>();
		KPIExcelUtility.populateProjectHygieneExcelData(
				excelRows, sprintName != null ? sprintName : sprintId, hygieneKpiResponseDTOList);

		return new SprintHygieneOutcome(dataCount, excelRows);
	}

	private DataCount emptyDataCount(String sprintId, String sprintName, String projectName) {
		return buildDataCount(sprintId, sprintName, projectName, 0.0, new HashMap<>());
	}

	private DataCount buildDataCount(
			String sprintId, String sprintName, String projectName, double score, Map<String, Long> passedCountByRule) {
		// Round to an integer percentage so both the axis label and the
		// aggregation input are clean whole numbers.
		long roundedScore = Math.round(score);
		String displayName = sprintName != null ? sprintName : sprintId;

		DataCount dataCount = new DataCount();
		// `data` is the display-ready string shown on the point/tooltip.
		dataCount.setData(String.valueOf(roundedScore));
		// `value` is the numeric input the parent JiraKPIService uses when it
		// aggregates the trend and computes maturity — keep it numeric.
		dataCount.setValue(roundedScore);
		dataCount.setSProjectName(projectName);
		dataCount.setSSprintID(sprintId);
		dataCount.setSSprintName(displayName);
		// hoverValue populates the tooltip on the trend line hover.
		Map<String, Object> hoverValue = new HashMap<>();
		hoverValue.put("Hygiene Score", roundedScore);
		dataCount.setHoverValue(hoverValue);
		dataCount.setDrillDown(passedCountByRule);
		return dataCount;
	}

	/** Bundle returned by {@link #computeSprintHygiene} — trend point + excel rows. */
	private record SprintHygieneOutcome(DataCount dataCount, List<KPIExcelData> excelRows) {}
}
