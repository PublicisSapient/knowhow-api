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
		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();

		// in case if only projects or sprint filters are applied
		projectWiseLeafNodeValue(kpiElement, mapTmp, project, kpiRequest);
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

	private void projectWiseLeafNodeValue(
			KpiElement kpiElement, Map<String, Node> mapTmp, Node node, KpiRequest kpiRequest) {
		String requestTrackerId = getRequestTrackerId();
		String projectName = node.getProjectFilter().getName();
		// LinkedHashMap keeps the rule order deterministic when the map is serialized
		// into the LLM prompt, which improves reproducibility of hygiene scoring.
		Map<String, String> fieldMappingPrompts = new LinkedHashMap<>();
		FieldMapping fieldMapping =
				configHelperService.getFieldMapping(node.getProjectFilter().getBasicProjectConfigId());
		List<CycleTimeGroup> cycleTimeGroupList = fieldMapping.getJiraFieldsSelectionKPI217();

		fieldMappingPrompts.put(
				"storyPoints",
				"""
				The story must be estimated by the team (story points must be present). \s
				- Use field: originalEstimateMinutes (Story Points) \s
				- Passed  → originalEstimateMinutes exists AND value > 0 \s
				- Failed  → field missing OR value is 0 OR null \s
				- Partial → (not applicable — estimation is binary) \s
				- N/A     → (not applicable — every story must be estimated) \s
				- Do not assume estimation from description or other fields.\s
				""");

		fieldMappingPrompts.put(
				"summary",
				"""
				The issue must have a meaningful, non-generic summary/title. \s
				- Use field: name (Summary) \s
				- Passed  → name is not null, length >= 10 chars, has at least 2 words,
										and is NOT a placeholder ("TBD", "test", "temp", "xxx",
										"todo", "wip") \s
				- Partial → name is present but only 1 word OR length between 4 and 9 chars
										(readable but not descriptive enough) \s
				- Failed  → name is null/blank, shorter than 4 chars, or matches a
										placeholder pattern \s
				- N/A     → (not applicable — every issue must have a summary) \s
				- Do not infer meaningfulness from description or labels.\s
				""");

		fieldMappingPrompts.put(
				"priority",
				"""
				The issue must have an explicit priority set by the team. \s
				- Use field: priority \s
				- Passed  → priority is not null/blank AND is one of
										[Blocker, Critical, Highest, High, Major, Medium, Low,
										Minor, Lowest, Trivial] \s
				- Partial → priority is present but set to "Undefined" / "None" /
										"Unassigned" (present but not decided) \s
				- Failed  → priority is null or empty \s
				- N/A     → (not applicable — every issue must have a priority) \s
				- Do not infer priority from severity or labels.\s
				""");

		fieldMappingPrompts.put(
				"assignee",
				"""
				An in-flight or completed issue must have an owner. \s
				- Use fields: assigneeId, assigneeName, jiraStatus \s
				- Passed  → assigneeId is not null AND assigneeName is not blank
										when jiraStatus is NOT in ["Open", "To Do", "Backlog", "Funnel"] \s
				- Partial → assigneeId or assigneeName present but the other is blank
										(owner information is incomplete) \s
				- Failed  → assigneeId null/blank while jiraStatus indicates work has
										started (e.g., "In Progress", "In Development", "In Review",
										"Done", "Closed") \s
				- N/A     → jiraStatus is "Open"/"To Do"/"Backlog"/"Funnel" with no
										assignee (pre-refinement backlog item).\s
				""");

		fieldMappingPrompts.put(
				"labels",
				"""
				The issue must be categorized with at least one label. \s
				- Use field: labels (List<String>) \s
				- Passed  → labels is a non-empty list with at least one non-blank entry \s
				- Partial → labels list is non-empty but every entry is blank/whitespace
										(present but meaningless) \s
				- Failed  → labels is null OR empty \s
				- N/A     → (not applicable — every issue must carry categorisation) \s
				- Do not treat components, epicLinked, or affectedVersions as
					substitutes for labels.\s
				""");

		fieldMappingPrompts.put(
				"dueDate",
				"""
				Sprint-committed or in-progress issues must have a due date. \s
				- Use fields: dueDate, sprintID, jiraStatus \s
				- Passed  → dueDate is a valid non-null date when sprintID is not null
										OR when jiraStatus is "In Progress"/"In Review" \s
				- Partial → dueDate is present but already in the past while jiraStatus
										is still "In Progress"/"In Review" (stale commitment) \s
				- Failed  → dueDate is null/blank while the issue is committed to a
										sprint or actively worked on \s
				- N/A     → pure backlog items (no sprintID AND status is
										"Backlog"/"To Do").\s
				""");

		fieldMappingPrompts.put(
				"sprint",
				"""
				Committed work must be attached to a sprint. \s
				- Use fields: sprintID, sprintName, jiraStatus, typeName \s
				- Passed  → sprintID is not null AND sprintName is not blank
										when jiraStatus is NOT "Backlog"/"Funnel"/"To Do" \s
				- Partial → sprintID is present but sprintName is blank
										(linked to sprint but sprint metadata incomplete) \s
				- Failed  → sprintID is null while jiraStatus is
										"In Progress"/"In Review"/"Done"/"Closed" \s
				- N/A     → typeName is "Epic" OR the issue is a pure backlog story
										(jiraStatus in "Backlog"/"Funnel"/"To Do" AND sprintID null).\s
				""");

		fieldMappingPrompts.put(
				"epicLink",
				"""
				Stories must be linked to an epic for traceability. \s
				- Use fields: epicLinked, epicID, typeName \s
				- Passed  → typeName is "Story" AND (epicLinked not blank OR epicID
										is not null) \s
				- Partial → typeName is "Story" AND epicLinked is present as text but
										epicID is null (link recorded but not resolvable) \s
				- Failed  → typeName is "Story" AND both epicLinked and epicID are
										null/blank \s
				- N/A     → typeName in ["Epic", "Bug", "Defect", "Task", "Sub-task"].\s
				""");

		fieldMappingPrompts.put(
				"issueType",
				"""
				The issue must have a valid issue type. \s
				- Use field: typeName \s
				- Passed  → typeName is not null/blank AND is one of
										[Story, Bug, Defect, Task, Sub-task, Epic, Spike, Enhancement] \s
				- Partial → typeName is a non-blank custom value not in the list above
										(present but non-standard) \s
				- Failed  → typeName is null or blank \s
				- N/A     → (not applicable — every issue must have a type) \s
				- Do not infer type from labels or summary.\s
				""");

		fieldMappingPrompts.put(
				"status",
				"""
				The issue must have a valid workflow status. \s
				- Use fields: jiraStatus, status \s
				- Passed  → jiraStatus is not null/blank AND matches one of the
										standard workflow states [Open, To Do, In Progress, In Review,
										In Testing, Done, Closed, Resolved, Blocked, Backlog] \s
				- Partial → jiraStatus is present but is a non-standard/custom value
										(workflow is valid but not aligned to standard vocabulary) \s
				- Failed  → jiraStatus is null or blank \s
				- N/A     → (not applicable — every issue must have a status) \s
				- Do not use status text from description or comments.\s
				""");

		fieldMappingPrompts.put(
				"resolution",
				"""
				Closed issues must record how they were resolved. \s
				- Use fields: resolution, jiraStatus \s
				- Passed  → resolution is not null/blank when jiraStatus is
										"Done"/"Closed"/"Resolved" \s
				- Partial → resolution is present but set to "Unresolved" while
										jiraStatus is "Done"/"Closed"/"Resolved" \s
				- Failed  → resolution is null/blank while jiraStatus indicates
										completion \s
				- N/A     → jiraStatus is not one of "Done"/"Closed"/"Resolved".\s
				""");

		fieldMappingPrompts.put(
				"worklog",
				"""
				Work must be logged for issues that have been worked on. \s
				- Use fields: timeSpentInMinutes, jiraStatus \s
				- Passed  → timeSpentInMinutes is not null AND value > 0 when jiraStatus
										is "In Progress"/"In Review"/"Done"/"Closed" \s
				- Partial → timeSpentInMinutes is > 0 but < 15 minutes for an issue
										that is "Done"/"Closed" (implausibly small) \s
				- Failed  → timeSpentInMinutes is null or 0 while jiraStatus indicates
										work has been done \s
				- N/A     → jiraStatus is "Open"/"To Do"/"Backlog"/"Funnel" \s
				- Do not derive worklog from originalEstimateMinutes or storyPoints.\s
				""");

		fieldMappingPrompts.put(
				"rootCause",
				"""
				Defects/bugs must capture a root cause once resolved. \s
				- Use fields: rootCauseList, typeName, jiraStatus \s
				- Passed  → typeName in ["Bug", "Defect"] AND rootCauseList is a
										non-empty list when jiraStatus is "Done"/"Closed"/"Resolved" \s
				- Partial → typeName in ["Bug", "Defect"] AND rootCauseList is present
										but every entry is blank/"Unknown"/"TBD" \s
				- Failed  → typeName is a defect type, jiraStatus is closed, and
										rootCauseList is null/empty \s
				- N/A     → typeName is not a defect type OR defect is still open.\s
				""");

		fieldMappingPrompts.put(
				"severity",
				"""
				Defects/bugs must have severity classified. \s
				- Use fields: severity, typeName \s
				- Passed  → typeName in ["Bug", "Defect"] AND severity is not null/blank
										AND severity is one of
										[S1, S2, S3, S4, Critical, Major, Minor, Trivial] \s
				- Partial → typeName in ["Bug", "Defect"] AND severity is present but
										is a non-standard value \s
				- Failed  → typeName is a defect type AND severity is null/blank \s
				- N/A     → typeName is not a defect type.\s
				""");

		fieldMappingPrompts.put(
				"environmentImpacted",
				"""
				Defects/bugs must record the environment where they were found. \s
				- Use fields: envImpacted, typeName \s
				- Passed  → typeName in ["Bug", "Defect"] AND envImpacted is not null/blank
										AND is one of
										[Dev, QA, UAT, Staging, Pre-Prod, Prod, Production] \s
				- Partial → typeName in ["Bug", "Defect"] AND envImpacted is present
										but is a non-standard value \s
				- Failed  → typeName is a defect type AND envImpacted is null/blank \s
				- N/A     → typeName is not a defect type.\s
				""");

		fieldMappingPrompts.put(
				"defectRaisedBy",
				"""
				Every defect must record who raised it. \s
				- Use fields: defectRaisedBy, typeName \s
				- Passed  → typeName in ["Bug", "Defect"] AND defectRaisedBy is
										not null/blank \s
				- Partial → typeName in ["Bug", "Defect"] AND defectRaisedBy is present
										but reads as a generic placeholder
										(e.g. "Unknown", "System", "N/A") \s
				- Failed  → typeName is a defect type AND defectRaisedBy is null/blank \s
				- N/A     → typeName is not a defect type.\s
				""");

		fieldMappingPrompts.put(
				"devDueDate",
				"""
				Stories under active development must have a dev due date. \s
				- Use fields: devDueDate, jiraStatus, typeName \s
				- Passed  → typeName is "Story" AND devDueDate is not null when
										jiraStatus is "In Progress"/"In Development" \s
				- Partial → devDueDate is set but already in the past while jiraStatus
										is still "In Progress"/"In Development" \s
				- Failed  → devDueDate is null while a story is in active development \s
				- N/A     → backlog stories or non-stories.\s
				""");

		fieldMappingPrompts.put(
				"remainingEstimate",
				"""
				In-flight issues must have a realistic remaining estimate. \s
				- Use fields: remainingEstimateMinutes, originalEstimateMinutes, jiraStatus \s
				- Passed  → remainingEstimateMinutes is not null AND >= 0 when jiraStatus
										is "In Progress"/"In Review" \s
				- Partial → remainingEstimateMinutes is present but > originalEstimateMinutes
										(scope has grown but not yet doubled) \s
				- Failed  → remainingEstimateMinutes is null while issue is in-flight
										OR remainingEstimateMinutes > (originalEstimateMinutes * 2),
										which signals unrealistic re-estimation \s
				- N/A     → jiraStatus is "Done"/"Closed"/"Backlog".\s
				""");

		fieldMappingPrompts.put(
				"staleness",
				"""
				Active issues must be recently updated (not abandoned). \s
				- Use fields: updateDate, jiraStatus \s
				- Passed  → updateDate is within the last 14 days when jiraStatus
										is "In Progress"/"In Review" \s
				- Partial → updateDate is between 15 and 30 days old while jiraStatus
										is "In Progress"/"In Review" (going stale) \s
				- Failed  → updateDate is older than 30 days while jiraStatus is
										"In Progress"/"In Review" (stale WIP) \s
				- N/A     → jiraStatus is "Done"/"Closed"/"Backlog" \s
				- Compute age from updateDate to the current sprint end date.\s
				""");

		// Final aggregate prompt sent to the LLM per sprint's list of JiraIssues.
		// At call-time substitute:
		// %1$s → JSON of fieldMappingPrompts (rule name → criteria)
		// %2$s → JSON of the JiraIssue list under evaluation
		String finalHygienePrompt =
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

		long time = System.currentTimeMillis();
		Map<String, Object> resultMap = fetchKPIDataFromDb(List.of(node), null, null, kpiRequest);
		log.info("DSR taking fetchKPIDataFromDb {}", System.currentTimeMillis() - time);

		List<JiraIssue> jiraIssueList = (List<JiraIssue>) resultMap.get(JIRA_ISSUES);
		List<SprintDetails> sprintDetailsList = (List<SprintDetails>) resultMap.get(SPRINT_DETAILS);

		Map<String, List<JiraIssue>> jiraIssuesBySprint =
				jiraIssueList.stream().collect(Collectors.groupingBy(JiraIssue::getSprintID));

		// Drive the futures loop from the already-sorted sprintDetailsList
		// (sorted by startDate in fetchKPIDataFromDb). This guarantees the
		// resulting dataCountList is in chronological order — no post-sort
		// needed — so the trend line renders left-to-right by sprint date.
		// Sprints with zero issues are skipped to avoid wasted LLM calls.
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
									String prompt =
											String.format(finalHygienePrompt, fieldMappingPrompts, jiraIssueSubset);
									return CompletableFuture.supplyAsync(
													() -> computeSprintHygiene(sprintId, sprintName, projectName, prompt),
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

		List<KPIExcelData> excelData = outcomes.stream().flatMap(o -> o.excelRows().stream()).toList();
		kpiElement.setExcelData(excelData);
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
			String sprintId, String sprintName, String projectName, String prompt) {
		ChatGenerationResponseDTO chatGenerationResponseDTO =
				aiGatewayClient.generate(ChatGenerationRequest.builder().prompt(prompt).build());
		List<HygieneKpiResponseDTO> hygieneKpiResponseDTOList =
				hygieneKpiParser.parse(chatGenerationResponseDTO.content());

		// Average the per-issue hygiene scores → sprint-level percentage.
		// (Previous impl summed them, which produced values > 100.)
		double sprintScore =
				hygieneKpiResponseDTOList.stream()
						.map(HygieneKpiResponseDTO::getHygieneScore)
						.filter(Objects::nonNull)
						.mapToInt(Integer::intValue)
						.average()
						.orElse(0.0);
		log.debug("Hygiene Score for Sprint {} ({}) : {}", sprintName, sprintId, sprintScore);

		DataCount dataCount = buildDataCount(sprintId, sprintName, projectName, sprintScore);

		// Build one KPIExcelData row per Jira issue for the Excel export.
		List<KPIExcelData> excelRows = new ArrayList<>();
		KPIExcelUtility.populateProjectHygieneExcelData(
				excelRows, sprintName != null ? sprintName : sprintId, hygieneKpiResponseDTOList);

		return new SprintHygieneOutcome(dataCount, excelRows);
	}

	private DataCount emptyDataCount(String sprintId, String sprintName, String projectName) {
		return buildDataCount(sprintId, sprintName, projectName, 0.0);
	}

	private DataCount buildDataCount(
			String sprintId, String sprintName, String projectName, double score) {
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
		return dataCount;
	}

	/** Bundle returned by {@link #computeSprintHygiene} — trend point + excel rows. */
	private record SprintHygieneOutcome(DataCount dataCount, List<KPIExcelData> excelRows) {}
}
