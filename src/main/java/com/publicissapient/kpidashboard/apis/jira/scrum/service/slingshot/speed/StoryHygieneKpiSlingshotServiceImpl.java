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
import com.publicissapient.kpidashboard.common.util.HygienePromptBuilder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class StoryHygieneKpiSlingshotServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String JIRA_ISSUES = "jiraIssues";
	private static final String SPRINT_DETAILS = "sprintDetails";
	private static final String JIRA_METADATA = "jiraMetadata";

	/**
	 * Fallback response used when the AI Gateway is unavailable. Shown to the user but never
	 * persisted.
	 */
	static final String MOCK_HYGIENE_RESPONSE_JSON =
			"""
			[
				{
					"issueKey": "DTS-48971",
					"issueType": "Story",
					"sprintId": "55076_a4fbe170-8667-4878-a877-a1b1300d8b16",
					"assignee": "Raja Kurru",
					"results": [
						{
							"rule": "Assignee Set",
							"field": "assigneeName",
							"observed": "Raja Kurru",
							"status": "Passed",
							"reason": "assigneeName is populated with a valid team member name"
						},
						{
							"rule": "Story Points Estimated",
							"field": "estimate",
							"observed": "3.0",
							"status": "Passed",
							"reason": "estimate is set to 3.0, greater than 0"
						},
						{
							"rule": "Priority Set",
							"field": "priority",
							"observed": "P3 - Major",
							"status": "Passed",
							"reason": "priority is set to P3 - Major, a recognised priority value"
						},
						{
							"rule": "Sprint Assigned",
							"field": "sprintID",
							"observed": "55076_a4fbe170-8667-4878-a877-a1b1300d8b16",
							"status": "Passed",
							"reason": "sprintID is populated, issue is tagged to KnowHOW | PI_23| ITR_6"
						},
						{
							"rule": "Issue Type Valid",
							"field": "typeName",
							"observed": "Story",
							"status": "Passed",
							"reason": "typeName is Story, a recognised issue type"
						},
						{
							"rule": "Summary Meaningful",
							"field": "summary",
							"observed": "FE | Role based access to KH Resources | Move access control from FE to BE",
							"status": "Passed",
							"reason": "summary clearly describes the feature scope and the architectural direction of the change"
						},
						{
							"rule": "Acceptance Criteria Defined",
							"field": "description",
							"observed": "Given a user with role X / When they navigate to KH Resources / Then access is enforced by BE role rules and the FE reflects the result accordingly",
							"status": "Passed",
							"reason": "description contains structured Given-When-Then AC with testable role-based outcomes"
						}
					],
					"totalApplicableRules": 7,
					"passedRules": 7,
					"failedRules": 0,
					"partialRules": 0,
					"hygieneScore": 100,
					"hygieneGrade": "GOOD",
					"overallStatus": "READY",
					"topFailures": [],
					"recommendations": "Story is well-defined | Add edge-case AC for users with multiple conflicting roles | Document expected API contract between FE and BE | Add negative scenario for unauthorized access attempt | Consider adding rollback plan for the access migration"
				},
				{
					"issueKey": "DTS-47979",
					"issueType": "Story",
					"sprintId": "55076_a4fbe170-8667-4878-a877-a1b1300d8b16",
					"assignee": "Andrada Mihai",
					"results": [
						{
							"rule": "Assignee Set",
							"field": "assigneeName",
							"observed": "Andrada Mihai",
							"status": "Passed",
							"reason": "assigneeName is populated with a valid team member name"
						},
						{
							"rule": "Story Points Estimated",
							"field": "estimate",
							"observed": "3.0",
							"status": "Passed",
							"reason": "estimate is set to 3.0, greater than 0"
						},
						{
							"rule": "Priority Set",
							"field": "priority",
							"observed": "P3 - Major",
							"status": "Passed",
							"reason": "priority is set to P3 - Major, a recognised priority value"
						},
						{
							"rule": "Sprint Assigned",
							"field": "sprintID",
							"observed": "55076_a4fbe170-8667-4878-a877-a1b1300d8b16",
							"status": "Passed",
							"reason": "sprintID is populated, issue is tagged to KnowHOW | PI_23| ITR_6"
						},
						{
							"rule": "Issue Type Valid",
							"field": "typeName",
							"observed": "Story",
							"status": "Passed",
							"reason": "typeName is Story, a recognised issue type"
						},
						{
							"rule": "Summary Meaningful",
							"field": "summary",
							"observed": "FE | Enhance Retro UI for supporting soft delete/pause",
							"status": "Passed",
							"reason": "summary clearly identifies the UI component and the feature being enhanced"
						},
						{
							"rule": "Acceptance Criteria Defined",
							"field": "description",
							"observed": "UI should support soft delete and pause. States should be reflected in the list view.",
							"status": "Partial",
							"reason": "description outlines expected behaviour but lacks structured format, does not define visual treatment of soft-deleted items, and omits undo/restore scenarios"
						}
					],
					"totalApplicableRules": 7,
					"passedRules": 6,
					"failedRules": 0,
					"partialRules": 1,
					"hygieneScore": 85,
					"hygieneGrade": "GOOD",
					"overallStatus": "NOT READY",
					"topFailures": ["Acceptance Criteria Defined"],
					"recommendations": "Rewrite AC in Given-When-Then or checklist format | Add scenario for restoring a soft-deleted item | Define visual treatment of paused vs deleted state | Specify keyboard and screen-reader behaviour for state toggles | Link to Figma designs if available"
				},
				{
					"issueKey": "DTS-50876",
					"issueType": "Story",
					"sprintId": "55076_a4fbe170-8667-4878-a877-a1b1300d8b16",
					"assignee": "Theodor Constantin",
					"results": [
						{
							"rule": "Assignee Set",
							"field": "assigneeName",
							"observed": "Theodor Constantin",
							"status": "Passed",
							"reason": "assigneeName is populated with a valid team member name"
						},
						{
							"rule": "Story Points Estimated",
							"field": "estimate",
							"observed": "5.0",
							"status": "Passed",
							"reason": "estimate is set to 5.0, greater than 0"
						},
						{
							"rule": "Priority Set",
							"field": "priority",
							"observed": "P3 - Major",
							"status": "Passed",
							"reason": "priority is set to P3 - Major, a recognised priority value"
						},
						{
							"rule": "Sprint Assigned",
							"field": "sprintID",
							"observed": "55076_a4fbe170-8667-4878-a877-a1b1300d8b16",
							"status": "Passed",
							"reason": "sprintID is populated, issue is tagged to KnowHOW | PI_23| ITR_6"
						},
						{
							"rule": "Issue Type Valid",
							"field": "typeName",
							"observed": "Story",
							"status": "Passed",
							"reason": "typeName is Story, a recognised issue type"
						},
						{
							"rule": "Summary Meaningful",
							"field": "summary",
							"observed": "BE | Developer KPIs - As a user, I want the system to predict the next KPI value for Quality KPIs so that I can anticipate performance trends and plan corrective actions.",
							"status": "Passed",
							"reason": "summary follows user-story format and clearly states the user goal, context, and benefit"
						},
						{
							"rule": "Acceptance Criteria Defined",
							"field": "description",
							"observed": "System should predict next sprint KPI value using historical data. Prediction should be visible on the KPI tile.",
							"status": "Partial",
							"reason": "description states the high-level expectation but does not define the prediction algorithm, confidence threshold, fallback when history is insufficient, or how the predicted value is visually differentiated from actual"
						}
					],
					"totalApplicableRules": 7,
					"passedRules": 6,
					"failedRules": 0,
					"partialRules": 1,
					"hygieneScore": 85,
					"hygieneGrade": "GOOD",
					"overallStatus": "NOT READY",
					"topFailures": ["Acceptance Criteria Defined"],
					"recommendations": "Define minimum sprint history required before prediction activates | Specify visual treatment of predicted vs actual value on the KPI tile | Add AC for fallback when insufficient data is available | Clarify which Quality KPIs are in scope for this iteration | Add non-functional requirement for prediction latency"
				},
				{
					"issueKey": "DTS-50715",
					"issueType": "Bug",
					"sprintId": "55076_a4fbe170-8667-4878-a877-a1b1300d8b16",
					"assignee": "Baldev Krishna",
					"results": [
						{
							"rule": "Assignee Set",
							"field": "assigneeName",
							"observed": "Baldev Krishna",
							"status": "Passed",
							"reason": "assigneeName is populated with a valid team member name"
						},
						{
							"rule": "Story Points Estimated",
							"field": "estimate",
							"observed": "0",
							"status": "Failed",
							"reason": "estimate is 0; bug has not been sized by the team"
						},
						{
							"rule": "Priority Set",
							"field": "priority",
							"observed": "P3 - Major",
							"status": "Passed",
							"reason": "priority is set to P3 - Major, a recognised priority value"
						},
						{
							"rule": "Sprint Assigned",
							"field": "sprintID",
							"observed": "55076_a4fbe170-8667-4878-a877-a1b1300d8b16",
							"status": "Passed",
							"reason": "sprintID is populated, issue is tagged to KnowHOW | PI_23| ITR_6"
						},
						{
							"rule": "Issue Type Valid",
							"field": "typeName",
							"observed": "Bug",
							"status": "Passed",
							"reason": "typeName is Bug, a recognised issue type"
						},
						{
							"rule": "Summary Meaningful",
							"field": "summary",
							"observed": "Regression issue - Jira Configuration Type & Template dropdown showing empty values instead of expected configuration options",
							"status": "Passed",
							"reason": "summary clearly describes the regression symptom and the affected UI component"
						},
						{
							"rule": "Acceptance Criteria Defined",
							"field": "description",
							"observed": "null",
							"status": "Failed",
							"reason": "description field is empty; no reproduction steps, expected behaviour, or fix-verification criteria documented"
						}
					],
					"totalApplicableRules": 7,
					"passedRules": 5,
					"failedRules": 2,
					"partialRules": 0,
					"hygieneScore": 71,
					"hygieneGrade": "AVERAGE",
					"overallStatus": "NOT READY",
					"topFailures": ["Story Points Estimated", "Acceptance Criteria Defined"],
					"recommendations": "Add story point estimate to size the fix effort | Document reproduction steps: steps to reproduce, expected vs actual behaviour | Specify affected Jira configuration types and templates | Attach screenshot showing the empty dropdown | Link to related regression test case"
				},
				{
					"issueKey": "DTS-51662",
					"issueType": "Story",
					"sprintId": "55076_a4fbe170-8667-4878-a877-a1b1300d8b16",
					"assignee": "Akshat Shrivastav",
					"results": [
						{
							"rule": "Assignee Set",
							"field": "assigneeName",
							"observed": "Akshat Shrivastav",
							"status": "Passed",
							"reason": "assigneeName is populated with a valid team member name"
						},
						{
							"rule": "Story Points Estimated",
							"field": "estimate",
							"observed": "0",
							"status": "Failed",
							"reason": "estimate is 0; story has not been sized by the team"
						},
						{
							"rule": "Priority Set",
							"field": "priority",
							"observed": "P3 - Major",
							"status": "Passed",
							"reason": "priority is set to P3 - Major, a recognised priority value"
						},
						{
							"rule": "Sprint Assigned",
							"field": "sprintID",
							"observed": "55076_a4fbe170-8667-4878-a877-a1b1300d8b16",
							"status": "Passed",
							"reason": "sprintID is populated, issue is tagged to KnowHOW | PI_23| ITR_6"
						},
						{
							"rule": "Issue Type Valid",
							"field": "typeName",
							"observed": "Story",
							"status": "Passed",
							"reason": "typeName is Story, a recognised issue type"
						},
						{
							"rule": "Summary Meaningful",
							"field": "summary",
							"observed": "L3 Rollout Support ITR6",
							"status": "Failed",
							"reason": "summary is 4 words relying on unexplained acronyms (L3, ITR6); it functions as a label rather than a description of user value or scope"
						},
						{
							"rule": "Acceptance Criteria Defined",
							"field": "description",
							"observed": "null",
							"status": "Failed",
							"reason": "description field is empty; no acceptance criteria, scope, or deliverables documented"
						}
					],
					"totalApplicableRules": 7,
					"passedRules": 4,
					"failedRules": 3,
					"partialRules": 0,
					"hygieneScore": 57,
					"hygieneGrade": "AVERAGE",
					"overallStatus": "NOT READY",
					"topFailures": ["Story Points Estimated", "Summary Meaningful", "Acceptance Criteria Defined"],
					"recommendations": "Replace summary with a meaningful description of the rollout scope and user value | Add story point estimate | Document acceptance criteria: what constitutes a successful L3 rollout for ITR6 | List affected modules and environments | Define exit criteria for rollout completion"
				}
			]
			""";

	@Autowired private HygieneKpiParser hygieneKpiParser;
	@Autowired private JiraIssueRepository jiraIssueRepository;
	@Autowired private AiGatewayClient aiGatewayClient;
	@Autowired private SprintDetailsService sprintDetailsService;
	@Autowired private ConfigHelperService configHelperService;
	@Autowired private CustomApiConfig customApiConfig;
	@Autowired private ObjectMapper objectMapper;
	@Autowired private StoryHygieneSprintResultRepository hygieneResultRepository;

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
		List<String> anchorFieldNames = customApiConfig.getSlingshotHygieneAnchorFields();
		if (CollectionUtils.isNotEmpty(anchorFieldNames)) {
			jiraFields.addAll(anchorFieldNames);
		}
		jiraFields.addAll(List.of("sprintID", "priority", "changeDate"));

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
		String basicProjectConfigId = node.getProjectFilter().getBasicProjectConfigId().toString();

		FieldMapping fieldMapping =
				configHelperService.getFieldMapping(node.getProjectFilter().getBasicProjectConfigId());
		List<CycleTimeGroup> cycleTimeGroupList = fieldMapping.getJiraFieldsSelectionKPI311();

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

		String ruleSetHash = HygienePromptBuilder.computeRuleSetHash(cycleTimeGroupList, objectMapper);

		long time = System.currentTimeMillis();
		Map<String, Object> resultMap = fetchKPIDataFromDb(List.of(node), null, null, kpiRequest);
		log.info(
				"Story Hygiene (kpi311): fetchKPIDataFromDb took {} ms", System.currentTimeMillis() - time);

		List<JiraIssue> jiraIssueList = (List<JiraIssue>) resultMap.get(JIRA_ISSUES);
		List<SprintDetails> sprintDetailsList = (List<SprintDetails>) resultMap.get(SPRINT_DETAILS);
		@SuppressWarnings("unchecked")
		Map<String, String> metaData = (Map<String, String>) resultMap.get(JIRA_METADATA);
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
																			ji,
																			anchorFieldNames,
																			cycleTimeGroupList,
																			metaData,
																			objectMapper))
													.toList();
									String prompt =
											HygienePromptBuilder.buildPrompt(prompts, issueNodes, metaData, objectMapper);
									if (prompt == null) {
										return CompletableFuture.completedFuture(
												new SprintHygieneOutcome(
														emptyDataCount(sprintId, sprintName, projectName),
														Collections.emptyList(),
														0));
									}

									return CompletableFuture.supplyAsync(
													() ->
															computeSprintHygiene(
																	sprintId,
																	sprintName,
																	projectName,
																	prompt,
																	sampledCount,
																	totalIssueCount,
																	ruleSetHash,
																	basicProjectConfigId,
																	cached),
													hygieneAiExecutor)
											.exceptionally(
													ex -> {
														Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
														if (cause instanceof MockHygieneResponseException mockEx) {
															return mockEx.outcome();
														}
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

		kpiElement.setScoreFactor(jiraIssuesBySprint.values().stream().mapToInt(List::size).sum());
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

	/**
	 * Calls the LLM for a single sprint, persists the result to MongoDB, and returns the outcome. The
	 * {@code existingResult} is used to carry over the document {@code _id} for an in-place upsert
	 * (avoids insert + delete on hash change).
	 */
	private SprintHygieneOutcome computeSprintHygiene(
			String sprintId,
			String sprintName,
			String projectName,
			String prompt,
			int sampledCount,
			int totalIssueCount,
			String ruleSetHash,
			String basicProjectConfigId,
			StoryHygieneSprintResult existingResult) {

		String responseContent;
		try {
			ChatGenerationResponseDTO chatGenerationResponseDTO =
					aiGatewayClient.generate(ChatGenerationRequest.builder().prompt(prompt).build());
			responseContent =
					chatGenerationResponseDTO == null ? null : chatGenerationResponseDTO.content();
		} catch (Exception ex) {
			log.error(
					"AI Gateway call failed for sprint '{}' ({}): {} — returning mock data (not persisted)",
					sprintName,
					sprintId,
					ex.getMessage(),
					ex);
			responseContent = null;
		}

		if (responseContent == null || responseContent.isBlank()) {
			log.warn(
					"AI Gateway returned blank content for sprint '{}' ({}) — returning mock data (not persisted)",
					sprintName,
					sprintId);
			List<HygieneKpiResponseDTO> mockVerdicts = hygieneKpiParser.parse(MOCK_HYGIENE_RESPONSE_JSON);
			// Throw so the exceptionally handler returns an outcome built from mock — no DB
			// write
			throw new MockHygieneResponseException(
					buildOutcomeFromVerdicts(
							sprintId, sprintName, projectName, mockVerdicts, sampledCount, totalIssueCount));
		}

		List<HygieneKpiResponseDTO> issueVerdicts = hygieneKpiParser.parse(responseContent);

		// Persist only on a real LLM success — upsert in place if a doc already existed
		// (stale hash)
		StoryHygieneSprintResult toSave =
				existingResult != null
						? existingResult
						: StoryHygieneSprintResult.builder()
								.basicProjectConfigId(basicProjectConfigId)
								.sprintId(sprintId)
								.build();
		toSave.setSprintName(sprintName);
		toSave.setRuleSetHash(ruleSetHash);
		toSave.setSampledIssueCount(sampledCount);
		toSave.setTotalIssueCount(totalIssueCount);
		toSave.setIssueVerdicts(issueVerdicts);
		toSave.setComputedAt(Instant.now());
		hygieneResultRepository.save(toSave);

		return buildOutcomeFromVerdicts(
				sprintId, sprintName, projectName, issueVerdicts, sampledCount, totalIssueCount);
	}

	/**
	 * Derives {@link SprintHygieneOutcome} from a list of issue verdicts. Called for both cache-hit
	 * (from stored {@code issueVerdicts}) and post-LLM paths so the aggregation logic lives in one
	 * place.
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
					emptyDataCount(sprintId, sprintName, projectName), List.of(), 0);
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
				issueVerdicts.stream().mapToInt(HygieneKpiResponseDTO::getHygieneScore).average();
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

		if (sampledCount < totalIssueCount) {
			dataCount
					.getHoverValue()
					.put("Issue Count", sampledCount + " of " + totalIssueCount + " (Capped)");
		}

		List<KPIExcelData> excelRows = new ArrayList<>();
		KPIExcelUtility.populateStoryHygieneExcelData(
				excelRows, sprintName != null ? sprintName : sprintId, issueVerdicts);

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

	/** Bundle returned by the per-sprint pipeline — trend point + excel rows + passed count. */
	private record SprintHygieneOutcome(
			DataCount dataCount, List<KPIExcelData> excelRows, int totalPassedIssues) {}

	/**
	 * Thrown by {@link #computeSprintHygiene} when the AI Gateway is unavailable and the mock
	 * response is used. Carries the pre-built outcome so the {@code exceptionally} handler can return
	 * it — bypassing the DB persist while still showing data to the user.
	 */
	private static final class MockHygieneResponseException extends RuntimeException {
		private final SprintHygieneOutcome outcome;

		MockHygieneResponseException(SprintHygieneOutcome outcome) {
			super("AI Gateway unavailable — serving mock hygiene data");
			this.outcome = outcome;
		}

		SprintHygieneOutcome outcome() {
			return outcome;
		}
	}
}
