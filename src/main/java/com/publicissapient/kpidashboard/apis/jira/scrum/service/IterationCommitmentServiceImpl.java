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

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.iterationdashboard.JiraIterationKPIService;
import com.publicissapient.kpidashboard.apis.model.CategoryData;
import com.publicissapient.kpidashboard.apis.model.Filter;
import com.publicissapient.kpidashboard.apis.model.FilterGroup;
import com.publicissapient.kpidashboard.apis.model.IssueKpiModalValue;
import com.publicissapient.kpidashboard.apis.model.KpiData;
import com.publicissapient.kpidashboard.apis.model.KpiDataCategory;
import com.publicissapient.kpidashboard.apis.model.KpiDataGroup;
import com.publicissapient.kpidashboard.apis.model.KpiDataSummary;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.util.IterationKpiHelper;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class IterationCommitmentServiceImpl extends JiraIterationKPIService {

	public static final String UNCHECKED = "unchecked";
	private static final String PUNTED_ISSUES = "puntedIssues";
	private static final String ADDED_ISSUES = "addedIssues";
	private static final String EXCLUDE_ADDED_ISSUES = "excludeAddedIssues";
	private static final String SCOPE_ADDED = "Sprint scope added";
	private static final String SCOPE_REMOVED = "Sprint scope removed";
	private static final String SCOPE_CHANGE = "Scope Change";
	private static final String INITIAL_COMMITMENT = "Initial Commitment";
	private static final String FILTER_TYPE = "Multi";
	private static final String FILTER_BY_ISSUE_TYPE = "Filter by issue type";
	private static final String FILTER_BY_STATUS = "Filter by status";
	private static final String JIRA_ISSUE_HISTORY = "jiraIssueHistory";
	private static final String SPRINT_DETAILS = "sprintDetails";

	@Autowired private ConfigHelperService configHelperService;

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node sprintNode)
			throws ApplicationException {
		projectWiseLeafNodeValue(sprintNode, kpiElement, kpiRequest);
		return kpiElement;
	}

	@Override
	public String getQualifierType() {
		return KPICode.ITERATION_COMMITMENT.name();
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			Node leafNode, String startDate, String endDate, KpiRequest kpiRequest) {
		Map<String, Object> resultListMap = new HashMap<>();
		if (null != leafNode) {
			log.info("Scope Change -> Requested sprint : {}", leafNode.getName());
			SprintDetails dbSprintDetail = getSprintDetailsFromBaseClass();
			SprintDetails sprintDetails;
			if (null != dbSprintDetail) {
				FieldMapping fieldMapping =
						configHelperService
								.getFieldMappingMap()
								.get(leafNode.getProjectFilter().getBasicProjectConfigId());
				// to modify sprint details on the basis of configuration for the project
				List<JiraIssueCustomHistory> totalHistoryList = getJiraIssuesCustomHistoryFromBaseClass();
				List<JiraIssue> totalJiraIssueList = getJiraIssuesFromBaseClass();
				Set<String> issueList =
						totalJiraIssueList.stream().map(JiraIssue::getNumber).collect(Collectors.toSet());

				sprintDetails =
						IterationKpiHelper.transformIterSprintdetail(
								totalHistoryList,
								issueList,
								dbSprintDetail,
								fieldMapping.getJiraIterationIssuetypeKPI120(),
								fieldMapping.getJiraIterationCompletionStatusKPI120(),
								leafNode.getProjectFilter().getBasicProjectConfigId());

				resultListMap.put(JIRA_ISSUE_HISTORY, totalHistoryList);
				resultListMap.put(SPRINT_DETAILS, sprintDetails);

				List<String> puntedIssues =
						KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(
								sprintDetails, CommonConstant.PUNTED_ISSUES);
				Set<String> addedIssues = sprintDetails.getAddedIssues();
				List<String> completeAndIncompleteIssues =
						Stream.of(
										KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(
												sprintDetails, CommonConstant.COMPLETED_ISSUES),
										KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(
												sprintDetails, CommonConstant.NOT_COMPLETED_ISSUES))
								.flatMap(Collection::stream)
								.collect(Collectors.toList());
				// Adding issues which were added before sprint start and later removed form
				// sprint or dropped.
				completeAndIncompleteIssues.addAll(puntedIssues);
				if (CollectionUtils.isNotEmpty(puntedIssues)) {
					List<JiraIssue> filteredPuntedIssueList =
							IterationKpiHelper.getFilteredJiraIssue(puntedIssues, totalJiraIssueList);
					Set<JiraIssue> filtersIssuesList =
							KpiDataHelper.getFilteredJiraIssuesListBasedOnTypeFromSprintDetails(
									sprintDetails, sprintDetails.getPuntedIssues(), filteredPuntedIssueList);
					resultListMap.put(PUNTED_ISSUES, new ArrayList<>(filtersIssuesList));
				}
				if (CollectionUtils.isNotEmpty(addedIssues)) {
					List<JiraIssue> filterAddedIssueList =
							IterationKpiHelper.getFilteredJiraIssue(
									new ArrayList<>(addedIssues), totalJiraIssueList);
					Set<JiraIssue> filtersIssuesList =
							KpiDataHelper.getFilteredJiraIssuesListBasedOnTypeFromSprintDetails(
									sprintDetails, new HashSet<>(), filterAddedIssueList);
					resultListMap.put(ADDED_ISSUES, new ArrayList<>(filtersIssuesList));
					completeAndIncompleteIssues.removeAll(new ArrayList<>(addedIssues));
				}
				if (CollectionUtils.isNotEmpty(completeAndIncompleteIssues)) {
					List<JiraIssue> filteredJiraIssue =
							IterationKpiHelper.getFilteredJiraIssue(
									new ArrayList<>(completeAndIncompleteIssues), totalJiraIssueList);
					Set<JiraIssue> filtersIssuesList =
							KpiDataHelper.getFilteredJiraIssuesListBasedOnTypeFromSprintDetails(
									sprintDetails, new HashSet<>(), filteredJiraIssue);
					resultListMap.put(EXCLUDE_ADDED_ISSUES, new ArrayList<>(filtersIssuesList));
				}
			}
		}
		return resultListMap;
	}

	/**
	 * Populates KPI value to sprint leaf nodes and gives the trend analysis at sprint level.
	 *
	 * @param sprintLeafNode
	 * @param kpiElement
	 * @param kpiRequest
	 */
	@SuppressWarnings("unchecked")
	private void projectWiseLeafNodeValue(
			Node sprintLeafNode, KpiElement kpiElement, KpiRequest kpiRequest) {
		String requestTrackerId = getRequestTrackerId();

		Object basicProjectConfigId = sprintLeafNode.getProjectFilter().getBasicProjectConfigId();
		FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigId);

		Map<String, Object> resultMap = fetchKPIDataFromDb(sprintLeafNode, null, null, kpiRequest);
		List<JiraIssue> puntedIssues = (List<JiraIssue>) resultMap.get(PUNTED_ISSUES);
		List<JiraIssue> addedIssues = (List<JiraIssue>) resultMap.get(ADDED_ISSUES);
		List<JiraIssue> initialIssues = (List<JiraIssue>) resultMap.get(EXCLUDE_ADDED_ISSUES);
		List<JiraIssueCustomHistory> issueHistory =
				(List<JiraIssueCustomHistory>) resultMap.get(JIRA_ISSUE_HISTORY);
		SprintDetails sprintDetails = (SprintDetails) resultMap.get(SPRINT_DETAILS);
		List<JiraIssue> scopeChangeIssues =
				Stream.of(initialIssues, addedIssues, puntedIssues)
						.filter(Objects::nonNull)
						.flatMap(Collection::stream)
						.filter(
								j ->
										Objects.nonNull(j.getLabels())
												&& Objects.nonNull(fieldMapping.getJiraLabelsKPI120())
												&& j.getLabels().stream()
														.anyMatch(fieldMapping.getJiraLabelsKPI120()::contains))
						.distinct()
						.toList();
		Set<IssueKpiModalValue> issueData = new HashSet<>();

		if (CollectionUtils.isNotEmpty(initialIssues)) {
			log.info(
					"Iteration Commitment - Initial Commitment -> request id : {} jira Issues : {}",
					requestTrackerId,
					initialIssues.size());
			Map<String, IssueKpiModalValue> issueKpiModalObject =
					KpiDataHelper.createMapOfIssueModal(initialIssues);
			initialIssues.forEach(
					issue -> {
						KPIExcelUtility.populateIssueModal(issue, fieldMapping, issueKpiModalObject);
						IssueKpiModalValue data = issueKpiModalObject.get(issue.getNumber());
						setCategoryAndDataValue(issue, data, fieldMapping, INITIAL_COMMITMENT);
					});
			issueData.addAll(new HashSet<>(issueKpiModalObject.values()));
		}
		Map<String, Set<Integer>> scopeDuration = new HashMap<>();
		if (CollectionUtils.isNotEmpty(addedIssues)) {
			log.info(
					"Iteration Commitment - Scope Added -> request id : {} jira Issues : {}",
					requestTrackerId,
					addedIssues.size());
			Map<String, IssueKpiModalValue> issueKpiModalObject =
					KpiDataHelper.createMapOfIssueModal(addedIssues);
			addedIssues.forEach(
					issue -> {
						KPIExcelUtility.populateIssueModal(issue, fieldMapping, issueKpiModalObject);
						IssueKpiModalValue data = issueKpiModalObject.get(issue.getNumber());
						setScopeDuration(
								data,
								issueHistory.stream()
										.filter(
												jiraIssueCustomHistory ->
														jiraIssueCustomHistory.getStoryID().equals(issue.getNumber()))
										.findFirst()
										.orElse(null),
								sprintDetails,
								fieldMapping,
								SCOPE_ADDED,
								scopeDuration);
						setCategoryAndDataValue(issue, data, fieldMapping, SCOPE_ADDED);
					});
			issueData.addAll(new HashSet<>(issueKpiModalObject.values()));
		}
		if (CollectionUtils.isNotEmpty(puntedIssues)) {
			log.info(
					"Iteration Commitment - Scope Removed -> request id : {} jira Issues : {}",
					requestTrackerId,
					puntedIssues.size());
			Map<String, IssueKpiModalValue> issueKpiModalObject =
					KpiDataHelper.createMapOfIssueModal(puntedIssues);
			puntedIssues.forEach(
					issue -> {
						KPIExcelUtility.populateIssueModal(issue, fieldMapping, issueKpiModalObject);
						IssueKpiModalValue data = issueKpiModalObject.get(issue.getNumber());
						setScopeDuration(
								data,
								issueHistory.stream()
										.filter(
												jiraIssueCustomHistory ->
														jiraIssueCustomHistory.getStoryID().equals(issue.getNumber()))
										.findFirst()
										.orElse(null),
								sprintDetails,
								fieldMapping,
								SCOPE_REMOVED,
								scopeDuration);
						setCategoryAndDataValue(issue, data, fieldMapping, SCOPE_REMOVED);
					});
			issueData.addAll(new HashSet<>(issueKpiModalObject.values()));
		}
		if (CollectionUtils.isNotEmpty(scopeChangeIssues)) {
			log.info(
					"Iteration Commitment - Scope Change -> request id : {} jira Issues : {}",
					requestTrackerId,
					scopeChangeIssues.size());
			Map<String, IssueKpiModalValue> issueKpiModalObject =
					KpiDataHelper.createMapOfIssueModal(scopeChangeIssues);
			scopeChangeIssues.forEach(
					issue -> {
						KPIExcelUtility.populateIssueModal(issue, fieldMapping, issueKpiModalObject);
						IssueKpiModalValue data = issueKpiModalObject.get(issue.getNumber());
						setScopeDuration(
								data,
								issueHistory.stream()
										.filter(
												jiraIssueCustomHistory ->
														jiraIssueCustomHistory.getStoryID().equals(issue.getNumber()))
										.findFirst()
										.orElse(null),
								sprintDetails,
								fieldMapping,
								SCOPE_CHANGE,
								scopeDuration);
						setCategoryAndDataValue(issue, data, fieldMapping, SCOPE_CHANGE);
					});
			issueData.addAll(new HashSet<>(issueKpiModalObject.values()));
		}

		if (CollectionUtils.isNotEmpty(issueData)) {

			kpiElement.setSprint(sprintLeafNode.getName());
			kpiElement.setModalHeads(KPIExcelColumn.ITERATION_COMMITMENT.getColumns());
			kpiElement.setIssueData(issueData);
			kpiElement.setFilterGroup(createFilterGroup());
			kpiElement.setDataGroup(createDataGroup(fieldMapping));
			kpiElement.setCategoryData(createCategoryData(scopeDuration));
		}
	}

	/**
	 * Updates the scope duration by calculating milestones
	 *
	 * @param data The `IssueKpiModalValue` object to update with the sorted scope duration.
	 * @param jiraIssueCustomHistory The history of changes for a Jira issue.
	 * @param sprintDetails The details of the sprint, including start and end dates.
	 * @param fieldMapping The field mapping configuration for the project.
	 * @param label The label indicating the type of scope change (e.g., "Scope Added", "Scope
	 *     Removed").
	 * @param overALlScopeDuration A map to store the overall scope duration for different labels.
	 */
	private void setScopeDuration(
			IssueKpiModalValue data,
			JiraIssueCustomHistory jiraIssueCustomHistory,
			SprintDetails sprintDetails,
			FieldMapping fieldMapping,
			String label,
			Map<String, Set<Integer>> overALlScopeDuration) {

		if (jiraIssueCustomHistory == null) {
			return;
		}

		LocalDateTime sprintStartDate =
				DateUtil.stringToLocalDateTime(sprintDetails.getStartDate(), DateUtil.TIME_FORMAT_WITH_SEC);
		LocalDateTime sprintEndDate =
				DateUtil.stringToLocalDateTime(sprintDetails.getEndDate(), DateUtil.TIME_FORMAT_WITH_SEC);
		long sprintDuration = ChronoUnit.DAYS.between(sprintStartDate, sprintEndDate) + 1;

		int quarterSprint = (int) Math.ceil(0.25 * sprintDuration);
		int halfSprint = (int) Math.ceil(0.5 * sprintDuration);
		int threeQuarterSprint = (int) Math.ceil(0.75 * sprintDuration);

		List<JiraHistoryChangeLog> relevantLogs =
				getRelevantLogs(jiraIssueCustomHistory, sprintDetails, fieldMapping, label);
		if (CollectionUtils.isEmpty(relevantLogs)) {
			return;
		}
		Set<Integer> scopeDuration = new LinkedHashSet<>();
		LocalDateTime today = DateUtil.getTodayTime();
		long durationFromSprintStart = ChronoUnit.DAYS.between(sprintStartDate, today) + 1L;

		for (JiraHistoryChangeLog log : relevantLogs) {
			LocalDateTime updateDate = log.getUpdatedOn();

			if (durationFromSprintStart >= quarterSprint
					&& updateDate.isAfter(today.minusDays(quarterSprint + 1L))) {
				scopeDuration.add(quarterSprint);
			}
			if (durationFromSprintStart >= halfSprint
					&& updateDate.isAfter(today.minusDays(halfSprint + 1L))) {
				scopeDuration.add(halfSprint);
			}
			if (durationFromSprintStart >= threeQuarterSprint
					&& updateDate.isAfter(today.minusDays(threeQuarterSprint + 1L))) {
				scopeDuration.add(threeQuarterSprint);
			}
		}

		List<Integer> sortedScopeDuration = new ArrayList<>(scopeDuration);
		Collections.sort(sortedScopeDuration);
		data.setScopeDuration(sortedScopeDuration);

		if (!sortedScopeDuration.isEmpty()) {
			overALlScopeDuration.computeIfAbsent(label, k -> new HashSet<>()).addAll(sortedScopeDuration);
		}
	}

	/**
	 * Retrieves relevant Jira history change logs based on the specified label. The logs are filtered
	 * according to the sprint name or label mapping, depending on the label type.
	 *
	 * @param history The `JiraIssueCustomHistory` object containing the history of changes for a Jira
	 *     issue.
	 * @param sprintDetails The `SprintDetails` object containing details of the sprint, including the
	 *     sprint name.
	 * @param fieldMapping The `FieldMapping` object containing configuration for field mappings.
	 * @param label The label indicating the type of scope change (e.g., "Scope Added", "Scope
	 *     Removed", "Scope Change").
	 * @return A list of `JiraHistoryChangeLog` objects that match the specified label and filtering
	 *     criteria.
	 */
	private List<JiraHistoryChangeLog> getRelevantLogs(
			JiraIssueCustomHistory history,
			SprintDetails sprintDetails,
			FieldMapping fieldMapping,
			String label) {

		String sprintName = sprintDetails.getSprintName();

		return switch (label) {
			case SCOPE_ADDED ->
					history.getSprintUpdationLog().stream()
							.filter(log -> log.getChangedTo().equalsIgnoreCase(sprintName))
							.toList();
			case SCOPE_REMOVED ->
					history.getSprintUpdationLog().stream()
							.filter(log -> log.getChangedFrom().equalsIgnoreCase(sprintName))
							.toList();
			case SCOPE_CHANGE ->
					history.getLabelUpdationLog().stream()
							.filter(log -> fieldMapping.getJiraLabelsKPI120().contains(log.getChangedTo()))
							.toList();
			default -> new ArrayList<>();
		};
	}

	/**
	 * Sets kpi data category and value field.
	 *
	 * @param issue
	 * @param data
	 * @param fieldMapping
	 * @param category
	 */
	private static void setCategoryAndDataValue(
			JiraIssue issue, IssueKpiModalValue data, FieldMapping fieldMapping, String category) {
		if (null == data.getCategory()) {
			data.setCategory(List.of(category));
		} else {
			data.getCategory().add(category);
		}
		data.setValue(0d);
		if (StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria())
				&& fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT)) {
			if (null != issue.getStoryPoints()) {
				data.setValue(issue.getStoryPoints());
			}
		} else if (null != issue.getOriginalEstimateMinutes()) {
			data.setValue(Double.valueOf(issue.getOriginalEstimateMinutes()));
		}
	}

	/**
	 * Creates filter group.
	 *
	 * @return
	 */
	private FilterGroup createFilterGroup() {
		FilterGroup filterGroup = new FilterGroup();
		// for the group by selection
		List<Filter> filterList = new ArrayList<>();
		filterList.add(createFilter(FILTER_TYPE, FILTER_BY_ISSUE_TYPE, "Issue Type", 1));
		filterList.add(createFilter(FILTER_TYPE, FILTER_BY_STATUS, "Issue Status", 2));
		filterGroup.setFilterGroup1(filterList);

		return filterGroup;
	}

	/**
	 * Creates individual filter object.
	 *
	 * @param type
	 * @param name
	 * @param key
	 * @param order
	 * @return
	 */
	private Filter createFilter(String type, String name, String key, Integer order) {
		Filter filter = new Filter();
		filter.setFilterType(type);
		filter.setFilterName(name);
		filter.setFilterKey(key);
		filter.setOrder(order);
		return filter;
	}

	/**
	 * Creates data group that tells what kind of data will be shown on chart.
	 *
	 * @param fieldMapping
	 * @return
	 */
	private KpiDataGroup createDataGroup(FieldMapping fieldMapping) {
		KpiDataGroup dataGroup = new KpiDataGroup();

		KpiDataSummary summary = new KpiDataSummary();
		summary.setName("Overall Commitment");
		summary.setAggregation("sum");

		List<KpiData> dataGroup1 = new ArrayList<>();
		String unit;
		String displayName;
		if (StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria())
				&& fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT)) {
			unit = CommonConstant.SP;
			displayName = CommonConstant.STORY_POINT;
		} else {
			unit = CommonConstant.DAY;
			displayName = CommonConstant.ORIGINAL_ESTIMATE;
		}

		dataGroup1.add(createKpiData("", "Issues", 1, "count", ""));
		dataGroup1.add(createKpiData("value", displayName, 2, "sum", unit));

		dataGroup.setSummary(summary);
		dataGroup.setDataGroup1(dataGroup1);
		return dataGroup;
	}

	/**
	 * Creates kpi data object.
	 *
	 * @param key
	 * @param name
	 * @param order
	 * @param aggregation
	 * @param unit
	 * @return
	 */
	private KpiData createKpiData(
			String key, String name, Integer order, String aggregation, String unit) {
		KpiData data = new KpiData();
		data.setKey(key);
		data.setName(name);
		data.setOrder(order);
		data.setAggregation(aggregation);
		data.setUnit(unit);
		data.setShowAsLegend(false);
		return data;
	}

	/**
	 * Creates object to hold category related info.
	 *
	 * @return
	 */
	private CategoryData createCategoryData(Map<String, Set<Integer>> scopeDuration) {
		CategoryData categoryData = new CategoryData();
		categoryData.setCategoryKey("Category");

		List<KpiDataCategory> categoryGroup = new ArrayList<>();
		categoryGroup.add(createKpiDataCategory(INITIAL_COMMITMENT, "+", 1, new HashSet<>()));
		categoryGroup.add(createKpiDataCategory(SCOPE_ADDED, "+", 2, scopeDuration.get(SCOPE_ADDED)));
		categoryGroup.add(
				createKpiDataCategory(
						SCOPE_CHANGE, Constant.NOT_AVAILABLE, 3, scopeDuration.get(SCOPE_CHANGE)));
		categoryGroup.add(
				createKpiDataCategory(SCOPE_REMOVED, "-", -1, scopeDuration.get(SCOPE_REMOVED)));
		categoryData.setCategoryGroup(categoryGroup);
		return categoryData;
	}

	/**
	 * Creates kpi data category object.
	 *
	 * @param categoryName
	 * @param categoryValue
	 * @param order
	 * @return
	 */
	private KpiDataCategory createKpiDataCategory(
			String categoryName, String categoryValue, Integer order, Set<Integer> scopeduration) {
		KpiDataCategory category = new KpiDataCategory();
		category.setCategoryName(categoryName);
		category.setCategoryValue(categoryValue);
		category.setOrder(order);
		category.setScopeDuration(scopeduration);

		return category;
	}
}
