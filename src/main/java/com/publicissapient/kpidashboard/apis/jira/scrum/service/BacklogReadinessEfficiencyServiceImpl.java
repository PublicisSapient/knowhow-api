/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/
package com.publicissapient.kpidashboard.apis.jira.scrum.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.AtomicDouble;
import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.enums.JiraFeature;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.SprintVelocityServiceHelper;
import com.publicissapient.kpidashboard.apis.jira.service.backlogdashboard.JiraBacklogKPIService;
import com.publicissapient.kpidashboard.apis.model.IterationKpiData;
import com.publicissapient.kpidashboard.apis.model.IterationKpiFilters;
import com.publicissapient.kpidashboard.apis.model.IterationKpiFiltersOptions;
import com.publicissapient.kpidashboard.apis.model.IterationKpiModalValue;
import com.publicissapient.kpidashboard.apis.model.IterationKpiValue;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.IssueDetails;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Jira service class to fetch backlog readiness kpi details
 *
 * @author dhachuda
 */
@Slf4j
@Component
public class BacklogReadinessEfficiencyServiceImpl extends JiraBacklogKPIService<Integer, List<Object>> {

	public static final String UNCHECKED = "unchecked";
	private static final double DEFAULT_BACKLOG_STRENGTH = 0.0;
	private static final String DAYS = "days";
	private static final String SPRINT = "Sprint";
	private static final String SP = "SP";
	private static final String SPRINT_VELOCITY_KEY = "sprintVelocityKey";
	private static final String SPRINT_WISE_SPRINT_DETAIL_MAP = "sprintWiseSprintDetailMap";
	private static final String HISTORY = "history";
	private static final String READINESS_CYCLE_TIME = "Readiness Cycle time";
	private static final String BACKLOG_STRENGTH = "Backlog Strength";
	private static final String READY_BACKLOG = "Ready Backlog";
	private static final String SEARCH_BY_ISSUE_TYPE = "Filter by issue type";
	private static final String SEARCH_BY_PRIORITY = "Filter by priority";
	private static final String ISSUES = "issues";

	private static final String OVERALL = "Overall";

	@Autowired
	private ConfigHelperService configHelperService;

	@Autowired
	private KpiHelperService kpiHelperService;

	@Autowired
	private SprintVelocityServiceHelper velocityServiceHelper;

	@Autowired
	private CustomApiConfig customApiConfig;
	@Autowired
	private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

	@Autowired
	private JiraIssueRepository jiraIssueRepository;

	/** Methods get the data for the KPI */
	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
			throws ApplicationException {
		log.info("Backlog readiness efficiency service {}", kpiRequest.getRequestTrackerId());
		DataCount trendValue = new DataCount();
		projectWiseLeafNodeValue(projectNode, trendValue, kpiElement, kpiRequest);
		return kpiElement;
	}

	@Override
	public String getQualifierType() {
		return KPICode.BACKLOG_READINESS_EFFICIENCY.name();
	}

	/**
	 * Fetches the data from the backlog where the story have completed the grooming
	 * corresponding history and previous sprint data
	 */
	@Override
	public Map<String, Object> fetchKPIDataFromDb(Node leafNode, String startDate, String endDate,
			KpiRequest kpiRequest) {

		return new HashMap<>();
	}

	/**
	 * Populates KPI value to sprint leaf nodes and gives the trend analysis at
	 * sprint level.
	 *
	 * @param projectNode
	 * @param kpiElement
	 * @param kpiRequest
	 */
	@SuppressWarnings("unchecked")
	private void projectWiseLeafNodeValue(Node projectNode, DataCount trendValue, KpiElement kpiElement,
			KpiRequest kpiRequest) {
		String requestTrackerId = getRequestTrackerId();
		FieldMapping fieldMapping = projectNode != null
				? configHelperService.getFieldMappingMap().get(projectNode.getProjectFilter().getBasicProjectConfigId())
				: new FieldMapping();

		Map<String, Object> resultMap = new HashMap<>();

		if (projectNode != null && projectNode.getProjectFilter() != null) {
			List<JiraIssue> jiraIssues = getBackLogStory(projectNode.getProjectFilter().getBasicProjectConfigId());
			resultMap.put(ISSUES, jiraIssues);

			List<String> issueNumbers = jiraIssues.stream().map(JiraIssue::getNumber).collect(Collectors.toList());
			List<JiraIssueCustomHistory> jiraIssueCustomHistories = getJiraIssuesCustomHistoryFromBaseClass();
			List<JiraIssueCustomHistory> customHistoryForIssues = jiraIssueCustomHistories.stream()
					.filter(history -> issueNumbers.contains(history.getStoryID())).collect(Collectors.toList());

			resultMap.put(HISTORY, customHistoryForIssues);

			Map<String, Object> sprintVelocityStoryMap = kpiHelperService.fetchBackLogReadinessFromdb(kpiRequest,
					projectNode);

			resultMap.putAll(sprintVelocityStoryMap);

			Double avgVelocity = getAverageSprintCapacity(projectNode,
					(List<SprintDetails>) resultMap.get(SPRINT_WISE_SPRINT_DETAIL_MAP),
					(List<JiraIssue>) resultMap.get(SPRINT_VELOCITY_KEY), fieldMapping);

			List<JiraIssue> allIssues = (List<JiraIssue>) resultMap.get(ISSUES);
			if (CollectionUtils.isNotEmpty(allIssues)) {
				log.info("Backlog items ready for development -> request id : {} total jira Issues : {}", requestTrackerId,
						allIssues.size());
				List<JiraIssueCustomHistory> historyForIssues = (List<JiraIssueCustomHistory>) resultMap.get(HISTORY);
				Map<String, JiraIssueCustomHistory> jiraIssueCustomHistoryMap = historyForIssues.stream()
						.collect(Collectors.toMap(JiraIssueCustomHistory::getStoryID,
								jiraIssueCustomHistory -> jiraIssueCustomHistory, (e1, e2) -> e1));
				Map<String, Map<String, List<JiraIssue>>> typeAndPriorityWiseIssues = allIssues.stream()
						.collect(Collectors.groupingBy(JiraIssue::getTypeName, Collectors.groupingBy(JiraIssue::getPriority)));

				Set<String> issueTypes = new HashSet<>();
				Set<String> priorities = new HashSet<>();
				List<IterationKpiValue> iterationKpiValues = new ArrayList<>();
				List<Integer> overAllIssueCount = Arrays.asList(0);
				List<Double> overAllStoryPoints = Arrays.asList(0.0D);
				AtomicLong overAllCycleTime = new AtomicLong(0);
				List<IterationKpiModalValue> overAllmodalValues = new ArrayList<>();

				typeAndPriorityWiseIssues
						.forEach((issueType, priorityWiseIssue) -> priorityWiseIssue.forEach((priority, issues) -> {
							issueTypes.add(issueType);
							priorities.add(priority);
							int issueCount = 0;
							Double storyPoint = 0.0D;
							long cycleTime = 0;
							List<IterationKpiModalValue> modalValues = new ArrayList<>();
							for (JiraIssue jiraIssue : issues) {
								KPIExcelUtility.populateBackLogData(overAllmodalValues, modalValues, jiraIssue,
										jiraIssueCustomHistoryMap.getOrDefault(jiraIssue.getNumber(), new JiraIssueCustomHistory()),
										fieldMapping.getReadyForDevelopmentStatusKPI138());
								issueCount = issueCount + 1;
								overAllIssueCount.set(0, overAllIssueCount.get(0) + 1);
								AtomicLong difference = getActivityCycleTimeForAnIssue(
										fieldMapping.getReadyForDevelopmentStatusKPI138(), historyForIssues, jiraIssue);
								cycleTime = cycleTime + difference.get();
								overAllCycleTime.set(overAllCycleTime.get() + difference.get());
								if (null != jiraIssue.getStoryPoints()) {
									storyPoint = storyPoint + jiraIssue.getStoryPoints();
									overAllStoryPoints.set(0, overAllStoryPoints.get(0) + jiraIssue.getStoryPoints());
								}
							}
							List<IterationKpiData> data = new ArrayList<>();
							IterationKpiData issuesForDevelopment = new IterationKpiData(READY_BACKLOG, Double.valueOf(issueCount),
									storyPoint, null, null, SP, modalValues);
							IterationKpiData backLogStrength = new IterationKpiData(BACKLOG_STRENGTH, DEFAULT_BACKLOG_STRENGTH, null,
									null, SPRINT, null);
							log.debug("Issue type: {} priority: {} Cycle time: {}", issueType, priority, cycleTime);
							IterationKpiData averageCycleTime = new IterationKpiData(READINESS_CYCLE_TIME,
									(double) Math.round(cycleTime / (double) issueCount), null, null, DAYS, null);
							data.add(issuesForDevelopment);
							data.add(backLogStrength);
							data.add(averageCycleTime);
							IterationKpiValue iterationKpiValue = new IterationKpiValue(issueType, priority, data);
							iterationKpiValues.add(iterationKpiValue);
						}));
				List<IterationKpiData> data = new ArrayList<>();

				IterationKpiData overAllIssues = new IterationKpiData(READY_BACKLOG, Double.valueOf(overAllIssueCount.get(0)),
						overAllStoryPoints.get(0), null, null, SP, overAllmodalValues);
				log.debug("Overall  the avg velocity of the previous sprint: {}", avgVelocity);
				double strength = (avgVelocity == 0D)
						? DEFAULT_BACKLOG_STRENGTH
						: (double) Math.round(overAllStoryPoints.get(0) / avgVelocity * 100) / 100;
				IterationKpiData backLogStrength = new IterationKpiData(BACKLOG_STRENGTH, strength, null, null, SPRINT, null);
				log.debug("Overall  the cycle time is : {} ", overAllCycleTime.get());
				IterationKpiData averageOverAllCycleTime = new IterationKpiData(READINESS_CYCLE_TIME,
						(double) Math.round(overAllCycleTime.get() / Double.valueOf(overAllIssueCount.get(0))), null, null, DAYS,
						null);
				data.add(overAllIssues);
				data.add(backLogStrength);
				data.add(averageOverAllCycleTime);
				IterationKpiValue overAllIterationKpiValue = new IterationKpiValue(OVERALL, OVERALL, data);
				iterationKpiValues.add(overAllIterationKpiValue);

				// Create kpi level filters
				IterationKpiFiltersOptions filter1 = new IterationKpiFiltersOptions(SEARCH_BY_ISSUE_TYPE, issueTypes);
				IterationKpiFiltersOptions filter2 = new IterationKpiFiltersOptions(SEARCH_BY_PRIORITY, priorities);
				IterationKpiFilters iterationKpiFilters = new IterationKpiFilters(filter1, filter2);
				// Modal Heads Options
				trendValue.setValue(iterationKpiValues);
				kpiElement.setFilters(iterationKpiFilters);
				kpiElement.setModalHeads(KPIExcelColumn.BACKLOG_READINESS_EFFICIENCY.getColumns());
				kpiElement.setTrendValueList(trendValue);
			}
		}
	}

	/**
	 * Calculates the activity cycle time based on the history data for an issue
	 *
	 * @param status
	 *          status
	 * @param historyForIssues
	 *          historyForIssues
	 * @param jiraIssue
	 *          jiraIssue
	 * @return cycletime
	 */
	private AtomicLong getActivityCycleTimeForAnIssue(List<String> status, List<JiraIssueCustomHistory> historyForIssues,
			JiraIssue jiraIssue) {
		Optional<JiraIssueCustomHistory> jiraCustomHistory = historyForIssues.stream()
				.filter(history -> history.getStoryID().equals(jiraIssue.getNumber())).findAny();
		AtomicLong difference = new AtomicLong(0);
		if (jiraCustomHistory.isPresent()) {

			Optional<JiraHistoryChangeLog> sprint = jiraCustomHistory.get().getStatusUpdationLog().stream()
					.filter(sprintDetails -> CollectionUtils.isNotEmpty(status) && status.contains(sprintDetails.getChangedTo()))
					.max(Comparator.comparing(JiraHistoryChangeLog::getUpdatedOn));
			if (sprint.isPresent()) {
				DateTime createdDate = new DateTime(jiraCustomHistory.get().getCreatedDate(), DateTimeZone.UTC);
				DateTime changedDate = new DateTime(sprint.get().getUpdatedOn().toString(), DateTimeZone.UTC);
				difference.set(difference.get() + new Duration(createdDate, changedDate).getStandardDays());
			}
		}
		log.debug("cycle time for the issue {} is {}", jiraIssue.getNumber(), difference.get());
		return difference;
	}

	/**
	 * Gets the sprint velocity of n number of previous sprint
	 *
	 * @param leafNode
	 *          leafNode
	 * @param sprintDetails
	 *          sprintDetails
	 * @param allJiraIssue
	 *          allJiraIssue
	 * @param fieldMapping
	 *          fieldMapping
	 * @return averageVelocity
	 */
	private Double getAverageSprintCapacity(Node leafNode, List<SprintDetails> sprintDetails,
			List<JiraIssue> allJiraIssue, FieldMapping fieldMapping) {
		int sprintCountForBackLogStrength = customApiConfig.getSprintCountForBackLogStrength();
		AtomicDouble storyPoint = new AtomicDouble();
		if (CollectionUtils.isNotEmpty(sprintDetails)) {
			List<SprintDetails> sprintForStregthCalculation = sprintDetails.stream()
					.filter(sprintDetail -> null != sprintDetail.getEndDate() &&
							DateTime.parse(sprintDetail.getEndDate()).isBefore(DateTime.now()))
					.limit(sprintCountForBackLogStrength).toList();

			if (CollectionUtils.isNotEmpty(sprintForStregthCalculation)) {
				Map<Pair<String, String>, List<JiraIssue>> sprintWiseIssues = new HashMap<>();
				Map<Pair<String, String>, Set<IssueDetails>> currentSprintLeafVelocityMap = new HashMap<>();
				getSprintForProject(allJiraIssue, sprintDetails, currentSprintLeafVelocityMap);
				sprintForStregthCalculation.forEach(sprintDetail -> {
					Pair<String, String> currentNodeIdentifier = Pair
							.of(leafNode.getProjectFilter().getBasicProjectConfigId().toString(), sprintDetail.getSprintID());
					double sprintVelocityForCurrentLeaf = calculateSprintVelocityValue(currentSprintLeafVelocityMap,
							currentNodeIdentifier, sprintWiseIssues, fieldMapping);
					storyPoint.set(storyPoint.doubleValue() + sprintVelocityForCurrentLeaf);
				});
				log.debug("Velocity for {} sprints is {}", sprintCountForBackLogStrength, storyPoint.get());
				return storyPoint.get() / sprintForStregthCalculation.size();
			}
		}
		return 0D;
	}

	public double calculateSprintVelocityValue(Map<Pair<String, String>, Set<IssueDetails>> currentSprintLeafVelocityMap,
			Pair<String, String> currentNodeIdentifier, Map<Pair<String, String>, List<JiraIssue>> sprintIssues,
			FieldMapping fieldMapping) {
		double sprintVelocityForCurrentLeaf = 0.0d;
		if (CollectionUtils.isNotEmpty(sprintIssues.get(currentNodeIdentifier))) {
			log.debug("Current Node identifier is present in sprintjirsissues map {} ", currentNodeIdentifier);
			List<JiraIssue> issueBacklogList = sprintIssues.get(currentNodeIdentifier);
			if (StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria()) &&
					fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT)) {
				sprintVelocityForCurrentLeaf = issueBacklogList.stream().mapToDouble(ji -> Double.parseDouble(ji.getEstimate()))
						.sum();
			} else {
				double totalOriginalEstimate = issueBacklogList.stream()
						.filter(issueBacklog -> Objects.nonNull(issueBacklog.getAggregateTimeOriginalEstimateMinutes()))
						.mapToDouble(JiraIssue::getAggregateTimeOriginalEstimateMinutes).sum();
				double totalOriginalEstimateInHours = totalOriginalEstimate / 60;
				sprintVelocityForCurrentLeaf = totalOriginalEstimateInHours / 60;
			}
		} else {
			if (Objects.nonNull(currentSprintLeafVelocityMap.get(currentNodeIdentifier))) {
				log.debug("Current Node identifier is present in currentSprintLeafVelocityMap map {} ", currentNodeIdentifier);
				Set<IssueDetails> issueDetailsSet = currentSprintLeafVelocityMap.get(currentNodeIdentifier);
				if (StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria()) &&
						fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT)) {
					sprintVelocityForCurrentLeaf = issueDetailsSet.stream()
							.filter(issueDetails -> Objects.nonNull(issueDetails.getSprintIssue().getStoryPoints()))
							.mapToDouble(issueDetails -> issueDetails.getSprintIssue().getStoryPoints()).sum();
				} else {
					double totalOriginalEstimate = issueDetailsSet.stream()
							.filter(issueDetails -> Objects.nonNull(issueDetails.getSprintIssue().getOriginalEstimate()))
							.mapToDouble(issueDetails -> issueDetails.getSprintIssue().getOriginalEstimate()).sum();
					double totalOriginalEstimateInHours = totalOriginalEstimate / 60;
					sprintVelocityForCurrentLeaf = totalOriginalEstimateInHours / 60;
				}
			}
		}
		log.debug("Sprint velocity for the sprint {} is {}", currentNodeIdentifier.getValue(),
				sprintVelocityForCurrentLeaf);
		return sprintVelocityForCurrentLeaf;
	}

	public void getSprintForProject(List<JiraIssue> allJiraIssue, List<SprintDetails> sprintDetails,
			Map<Pair<String, String>, Set<IssueDetails>> currentSprintLeafVelocityMap) {
		if (CollectionUtils.isNotEmpty(sprintDetails)) {
			sprintDetails.forEach(sd -> {
				Set<IssueDetails> filterIssueDetailsSet = new HashSet<>();
				if (CollectionUtils.isNotEmpty(sd.getCompletedIssues())) {
					sd.getCompletedIssues().forEach(sprintIssue -> {
						allJiraIssue.forEach(jiraIssue -> {
							if (sprintIssue.getNumber().equals(jiraIssue.getNumber())) {
								IssueDetails issueDetails = new IssueDetails();
								issueDetails.setSprintIssue(sprintIssue);
								issueDetails.setUrl(jiraIssue.getUrl());
								issueDetails.setDesc(jiraIssue.getName());
								filterIssueDetailsSet.add(issueDetails);
							}
						});
						Pair<String, String> currentNodeIdentifier = Pair.of(sd.getBasicProjectConfigId().toString(),
								sd.getSprintID());
						log.debug("Issue count for the sprint {} is {}", sd.getSprintID(), filterIssueDetailsSet.size());
						currentSprintLeafVelocityMap.put(currentNodeIdentifier, filterIssueDetailsSet);
					});
				}
			});
		}
	}

	public List<JiraIssue> getBackLogStory(ObjectId basicProjectId) {
		Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();
		Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();
		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
		List<String> doneStatus = new ArrayList<>();
		Map<Long, String> doneStatusMap = getJiraIssueReleaseStatus().getClosedList();
		if (doneStatusMap != null) {
			doneStatus = doneStatusMap.values().stream().map(String::toLowerCase).collect(Collectors.toList());
		}

		List<String> basicProjectConfigIds = new ArrayList<>();
		basicProjectConfigIds.add(basicProjectId.toString());

		FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectId);

		List<String> statusList = new ArrayList<>();
		if (Optional.ofNullable(fieldMapping.getReadyForDevelopmentStatusKPI138()).isPresent()) {
			statusList.addAll(fieldMapping.getReadyForDevelopmentStatusKPI138());
		}
		mapOfProjectFilters.put(JiraFeature.JIRA_ISSUE_STATUS.getFieldValueInFeature(),
				CommonUtils.convertToPatternList(statusList));
		mapOfFilters.put(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				basicProjectConfigIds.stream().distinct().collect(Collectors.toList()));

		mapOfFilters.put(JiraFeature.SPRINT_STATUS.getFieldValueInFeature(),
				Lists.newArrayList("", null, CommonConstant.FUTURE, CommonConstant.FUTURE.toLowerCase(),
						CommonConstant.CLOSED.toUpperCase(), CommonConstant.CLOSED.toLowerCase()));

		uniqueProjectMap.put(basicProjectId.toString(), mapOfProjectFilters);
		List<JiraIssue> allIssues = jiraIssueRepository.findIssuesBySprintAndType(mapOfFilters, uniqueProjectMap);
		List<String> finalDoneStatus = doneStatus;
		allIssues = allIssues.stream()
				.filter(issue -> issue.getSprintAssetState() == null ||
						!issue.getSprintAssetState().equalsIgnoreCase(CommonConstant.CLOSED) ||
						!finalDoneStatus.contains(issue.getStatus().toLowerCase()))
				.collect(Collectors.toList());
		return allIssues;
	}
}
