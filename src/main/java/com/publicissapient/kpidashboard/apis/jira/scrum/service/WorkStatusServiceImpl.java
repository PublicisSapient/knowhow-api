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

import static com.publicissapient.kpidashboard.apis.util.KpiDataHelper.sprintWiseDelayCalculation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.CalculatePCDHelper;
import com.publicissapient.kpidashboard.apis.jira.service.iterationdashboard.JiraIterationKPIService;
import com.publicissapient.kpidashboard.apis.model.*;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.IterationKpiHelper;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.*;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class WorkStatusServiceImpl extends JiraIterationKPIService {

	public static final String UNCHECKED = "unchecked";
	public static final String ISSUE_CUSTOM_HISTORY = "issues custom history";
	public static final String ACTUAL_COMPLETE_DATE = "actualCompleteDate";
	public static final String COMPLETED = "Completed";
	public static final String PLANNED_COMPLETION = "Planned Completion";
	public static final String ACTUAL_COMPLETION = "Actual Completion";
	public static final String ACTUAL_START_DATE = "actualStartDate";
	public static final String ACTUAL_COMPLETION_DATA = "actualCompletionData";
	public static final String ISSUE_DELAY = "issueDelay";
	public static final String DEV_COMPLETION_DATE = "devCompletionDate";
	private static final String FILTER_BY_ISSUE_TYPE = "Filter by issue type";
	private static final String FILTER_BY_PRIORITY = "Filter by priority";
	private static final String ISSUES = "issues";
	private static final String DELAY = "Delay";
	private static final String SPRINT_DETAILS = "sprintDetails";
	private static final String FILTER_TYPE = "Multi";
	public static final String PLANNED = "Planned";
	public static final String UNPLANNED = "Unplanned";
	public static final String DEV_STATUS = "Dev Status";
	public static final String DELAY_COUNT = "Delayed Workitems";

	@Autowired
	private ConfigHelperService configHelperService;

	// CHANGE: Reduced DelayCalculationContext constructor parameters from 8 to 6 by grouping related data
	public static class DelayCalculationContext {
		private final JiraIssue issue;
		private final Map<String, Object> jiraIssueData;
		private final Map<JiraIssue, String> devCompletedIssues;
		private final IssueKpiModalValue data;
		private final CategoryDelayData categoryDelayData; // CHANGE: Combined category2, issueWiseDelay, and actualCompletionData
		private final int currentDelay;

		public DelayCalculationContext(JiraIssue issue, Map<String, Object> jiraIssueData,
									   Map<JiraIssue, String> devCompletedIssues, IssueKpiModalValue data,
									   CategoryDelayData categoryDelayData, int currentDelay) {
			this.issue = issue;
			this.jiraIssueData = jiraIssueData;
			this.devCompletedIssues = devCompletedIssues;
			this.data = data;
			this.categoryDelayData = categoryDelayData;
			this.currentDelay = currentDelay;
		}

		public JiraIssue getIssue() { return issue; }
		public Map<String, Object> getJiraIssueData() { return jiraIssueData; }
		public Map<JiraIssue, String> getDevCompletedIssues() { return devCompletedIssues; }
		public IssueKpiModalValue getData() { return data; }
		public CategoryDelayData getCategoryDelayData() { return categoryDelayData; }
		public int getCurrentDelay() { return currentDelay; }
	}

	// CHANGE: Helper class to group category and delay related data
	public static class CategoryDelayData {
		private final Map<String, List<String>> category2;
		private final Map<String, IterationPotentialDelay> issueWiseDelay;
		private final Map<String, Object> actualCompletionData;

		public CategoryDelayData(Map<String, List<String>> category2,
								 Map<String, IterationPotentialDelay> issueWiseDelay,
								 Map<String, Object> actualCompletionData) {
			this.category2 = category2;
			this.issueWiseDelay = issueWiseDelay;
			this.actualCompletionData = actualCompletionData;
		}

		public Map<String, List<String>> getCategory2() { return category2; }
		public Map<String, IterationPotentialDelay> getIssueWiseDelay() { return issueWiseDelay; }
		public Map<String, Object> getActualCompletionData() { return actualCompletionData; }
	}

	// CHANGE: Reduced PlannedCompletedIssueContext constructor parameters from 9 to 6 by grouping related data
	public static class PlannedCompletedIssueContext {
		private final JiraIssue issue;
		private final Map<String, Object> jiraIssueData;
		private final List<String> allCompletedIssuesList;
		private final IssueKpiModalValue data;
		private final CategoryInfo categoryInfo; // CHANGE: Combined category, category2, delay, and actualCompletionData
		private final Map<String, IterationPotentialDelay> issueWiseDelay;

		public PlannedCompletedIssueContext(JiraIssue issue, Map<String, Object> jiraIssueData,
											List<String> allCompletedIssuesList, IssueKpiModalValue data,
											CategoryInfo categoryInfo, Map<String, IterationPotentialDelay> issueWiseDelay) {
			this.issue = issue;
			this.jiraIssueData = jiraIssueData;
			this.allCompletedIssuesList = allCompletedIssuesList;
			this.data = data;
			this.categoryInfo = categoryInfo;
			this.issueWiseDelay = issueWiseDelay;
		}

		public JiraIssue getIssue() { return issue; }
		public Map<String, Object> getJiraIssueData() { return jiraIssueData; }
		public List<String> getAllCompletedIssuesList() { return allCompletedIssuesList; }
		public IssueKpiModalValue getData() { return data; }
		public CategoryInfo getCategoryInfo() { return categoryInfo; }
		public Map<String, IterationPotentialDelay> getIssueWiseDelay() { return issueWiseDelay; }
	}

	// CHANGE: Helper class to group category-related information
	public static class CategoryInfo {
		private final Set<String> category;
		private final Map<String, List<String>> category2;
		private final int delay;
		private final Map<String, Object> actualCompletionData;

		public CategoryInfo(Set<String> category, Map<String, List<String>> category2,
							int delay, Map<String, Object> actualCompletionData) {
			this.category = category;
			this.category2 = category2;
			this.delay = delay;
			this.actualCompletionData = actualCompletionData;
		}

		public Set<String> getCategory() { return category; }
		public Map<String, List<String>> getCategory2() { return category2; }
		public int getDelay() { return delay; }
		public Map<String, Object> getActualCompletionData() { return actualCompletionData; }
	}

	// CHANGE: Helper class to group dev completion related data
	public static class DevCompletionData {
		private final SprintDetails sprintDetails;
		private final Map<String, Object> jiraIssueData;
		private final Map<JiraIssue, String> devCompletedIssues;

		public DevCompletionData(SprintDetails sprintDetails, Map<String, Object> jiraIssueData,
								 Map<JiraIssue, String> devCompletedIssues) {
			this.sprintDetails = sprintDetails;
			this.jiraIssueData = jiraIssueData;
			this.devCompletedIssues = devCompletedIssues;
		}

		public SprintDetails getSprintDetails() { return sprintDetails; }
		public Map<String, Object> getJiraIssueData() { return jiraIssueData; }
		public Map<JiraIssue, String> getDevCompletedIssues() { return devCompletedIssues; }
	}

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node sprintNode)
			throws ApplicationException {
		projectWiseLeafNodeValue(sprintNode, kpiElement, kpiRequest);
		return kpiElement;
	}

	@Override
	public String getQualifierType() {
		return KPICode.WORK_STATUS.name();
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(Node leafNode, String startDate, String endDate,
												  KpiRequest kpiRequest) {
		Map<String, Object> resultListMap = new HashMap<>();
		if (null != leafNode) {
			log.info("Work Status -> Requested sprint : {}", leafNode.getName());
			SprintDetails dbSprintDetail = getSprintDetailsFromBaseClass();
			SprintDetails sprintDetails;
			if (null != dbSprintDetail) {
				FieldMapping fieldMapping = configHelperService.getFieldMappingMap()
															   .get(leafNode.getProjectFilter().getBasicProjectConfigId());
				// to modify sprint details on the basis of configuration for the project
				List<JiraIssueCustomHistory> totalHistoryList = getJiraIssuesCustomHistoryFromBaseClass();
				List<JiraIssue> totalJiraIssueList = getJiraIssuesFromBaseClass();
				Set<String> issueList = totalJiraIssueList.stream().map(JiraIssue::getNumber).collect(Collectors.toSet());

				sprintDetails = IterationKpiHelper.transformIterSprintdetail(totalHistoryList, issueList, dbSprintDetail,
																			 fieldMapping.getJiraIterationIssuetypeKPI128(), fieldMapping.getJiraIterationCompletionStatusKPI128(),
																			 leafNode.getProjectFilter().getBasicProjectConfigId());

				List<String> totalIssues = KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(sprintDetails,
																									 CommonConstant.TOTAL_ISSUES);
				List<String> completedIssues = KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(sprintDetails,
																										 CommonConstant.COMPLETED_ISSUES);
				if (CollectionUtils.isNotEmpty(totalIssues)) {
					List<JiraIssue> jiraIssueList = IterationKpiHelper.getFilteredJiraIssue(totalIssues, totalJiraIssueList);
					List<JiraIssueCustomHistory> issueHistoryList = IterationKpiHelper.getFilteredJiraIssueHistory(totalIssues,
																												   totalHistoryList);
					Set<JiraIssue> filtersIssuesList = KpiDataHelper.getFilteredJiraIssuesListBasedOnTypeFromSprintDetails(
							sprintDetails, sprintDetails.getTotalIssues(), jiraIssueList);
					resultListMap.put(ISSUES, new ArrayList<>(filtersIssuesList));
					resultListMap.put(ISSUE_CUSTOM_HISTORY, new ArrayList<>(issueHistoryList));
					resultListMap.put(SPRINT_DETAILS, sprintDetails);
				}
				resultListMap.put(COMPLETED, new ArrayList<>(completedIssues));
			}
		}
		return resultListMap;
	}

	/**
	 * Populates KPI value to sprint leaf nodes and gives the trend analysis at
	 * sprint level.
	 *
	 * @param sprintLeafNode
	 * @param kpiElement
	 * @param kpiRequest
	 */
	@SuppressWarnings("unchecked")
	private void projectWiseLeafNodeValue(Node sprintLeafNode, KpiElement kpiElement, KpiRequest kpiRequest) {
		String requestTrackerId = getRequestTrackerId();
		Object basicProjectConfigId = Objects.requireNonNull(sprintLeafNode).getProjectFilter().getBasicProjectConfigId();
		FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigId);

		Map<String, Object> resultMap = fetchKPIDataFromDb(sprintLeafNode, null, null, kpiRequest);

		SprintDetails sprintDetails = (SprintDetails) resultMap.get(SPRINT_DETAILS);
		List<JiraIssue> allIssues = (List<JiraIssue>) resultMap.get(ISSUES);
		List<String> allCompletedIssuesList = (List<String>) resultMap.get(COMPLETED);
		List<JiraIssueCustomHistory> allIssueHistories = (List<JiraIssueCustomHistory>) resultMap.get(ISSUE_CUSTOM_HISTORY);

		if (CollectionUtils.isNotEmpty(allIssues)) {
			log.info("Work Status -> request id : {} total jira Issues : {}", requestTrackerId, allIssues.size());
			// issues with due date.
			List<JiraIssue> allIssuesWithDueDate = allIssues.stream()
															.filter(jiraIssue -> StringUtils.isNotBlank(jiraIssue.getDueDate())).toList();
			// issues with dev due date.
			List<JiraIssue> allIssuesWithDevDueDate = allIssues.stream()
															   .filter(jiraIssue -> StringUtils.isNotBlank(jiraIssue.getDevDueDate())).toList();
			// issues without due date.
			List<JiraIssue> allIssuesWithoutDueDate = allIssues.stream()
															   .filter(jiraIssue -> StringUtils.isBlank(jiraIssue.getDueDate())).toList();

			List<JiraIssue> notCompletedIssuesWithDueDate = allIssuesWithDueDate.stream()
																				.filter(jiraIssue -> !allCompletedIssuesList.contains(jiraIssue.getNumber())).toList();
			List<IterationPotentialDelay> iterationPotentialDelayList = calculatePotentialDelay(sprintDetails,
																								notCompletedIssuesWithDueDate, fieldMapping);
			Map<String, IterationPotentialDelay> issueWiseDelay = checkMaxDelayAssigneeWise(notCompletedIssuesWithDueDate,
																							iterationPotentialDelayList, sprintDetails);
			Map<String, IssueKpiModalValue> issueKpiModalObject = KpiDataHelper.createMapOfIssueModal(allIssues);

			Map<JiraIssue, String> devCompletedIssues = getDevCompletedIssues(allIssuesWithDevDueDate, allIssueHistories,
																			  fieldMapping);
			Set<IssueKpiModalValue> issueData = new HashSet<>();
			allIssues.forEach(issue -> {
				KPIExcelUtility.populateIssueModal(issue, fieldMapping, issueKpiModalObject);
				IssueKpiModalValue data = issueKpiModalObject.get(issue.getNumber());
				setCommonData(issue, data, fieldMapping);
				Set<String> category = new HashSet<>();
				Map<String, List<String>> category2 = new HashMap<>();
				if (allIssuesWithDueDate.contains(issue)) {
					Map<String, Object> jiraIssueData = jiraIssueCalculation(fieldMapping, sprintDetails, allIssueHistories,
																			 allCompletedIssuesList, issue);
					category2 = setDataForPlanned(issue, jiraIssueData, sprintDetails, allCompletedIssuesList, issueWiseDelay,
												  data, category);
				}
				if (allIssuesWithDevDueDate.contains(issue)) {
					Map<String, Object> jiraIssueData = jiraIssueCalculationDev(fieldMapping, sprintDetails, allIssueHistories,
																				issue, devCompletedIssues, allCompletedIssuesList);
					// CHANGE: Using parameter objects to reduce method parameters
					DevCompletionData devData = new DevCompletionData(sprintDetails, jiraIssueData, devCompletedIssues);
					setDataForDevCompletion(issue, category, data, category2, issueWiseDelay, devData);
				}
				setCategoryForUnplanned(issue, allIssuesWithoutDueDate, category, allCompletedIssuesList, category2);
				if (CollectionUtils.isNotEmpty(category)) {
					data.setCategory(category.stream().toList());
					data.setCategory2(category2);
					issueData.add(data);
				}
			});

			kpiElement.setSprint(sprintLeafNode.getName());
			kpiElement.setExcelColumnInfo(KPIExcelColumn.WORK_STATUS.getKpiExcelColumnInfo());
			kpiElement.setIssueData(issueData);
			kpiElement.setFilterGroup(createFilterGroup());
			kpiElement.setDataGroup(createDataGroup(fieldMapping));
			kpiElement.setCategoryData(createCategoryData());
		}
	}

	private static void setCategoryForUnplanned(JiraIssue issue, List<JiraIssue> allIssuesWithoutDueDate,
												Set<String> category, List<String> allCompletedIssuesList, Map<String, List<String>> category2) {
		if (allIssuesWithoutDueDate.contains(issue)) {
			category.add(UNPLANNED);
			category2.putIfAbsent(UNPLANNED, new ArrayList<>());
			category2.get(UNPLANNED).add(PLANNED_COMPLETION);
			if (allCompletedIssuesList.contains(issue.getNumber())) {
				category2.get(UNPLANNED).add(ACTUAL_COMPLETION);
			}
		}
	}

	private static void setCommonData(JiraIssue issue, IssueKpiModalValue data, FieldMapping fieldMapping) {
		data.setValue(0d);
		if (StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria()) &&
			fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT)) {
			data.setValue(issue.getStoryPoints());
		} else if (null != issue.getOriginalEstimateMinutes()) {
			data.setValue(Double.valueOf(issue.getOriginalEstimateMinutes()));
		}
		data.setCategoryWiseDelay(new HashMap<>());
		data.setMarker(Constant.BLANK);
		data.setDevDelayInDays(Constant.BLANK);
		data.setPotentialDelay(Constant.BLANK);
		data.setPredictedCompletionDate(Constant.BLANK);
	}

	/**
	 * Creates data group that tells what kind of data will be shown on chart.
	 *
	 * @param fieldMapping
	 * @return
	 */
	private KpiDataGroup createDataGroup(FieldMapping fieldMapping) {
		KpiDataGroup dataGroup = new KpiDataGroup();

		// For markerInfo
		Map<String, String> markerInfo = new HashMap<>();
		markerInfo.put(Constant.GREEN, "Issue finished earlier than planned are marked in Green");

		List<KpiData> dataGroup1 = new ArrayList<>();
		String unit;
		String name;
		if (StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria()) &&
			fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT)) {
			unit = CommonConstant.SP;
			name = CommonConstant.STORY_POINT;
		} else {
			unit = CommonConstant.DAY;
			name = CommonConstant.ORIGINAL_ESTIMATE;
		}

		dataGroup1.add(createKpiData("", "Issue count", 1, "count", "", false));
		dataGroup1.add(createKpiData("value", name, 2, "sum", unit, false));

		List<KpiData> dataGroup2 = new ArrayList<>();
		dataGroup2.add(createKpiData("categoryWiseDelay", DELAY, 1, "sum", CommonConstant.DAY, true));

		dataGroup.setMarkerInfo(markerInfo);
		dataGroup.setDataGroup1(dataGroup1);
		dataGroup.setDataGroup2(dataGroup2);
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
	private KpiData createKpiData(String key, String name, Integer order, String aggregation, String unit,
								  boolean isMultipleValue) {
		KpiData data = new KpiData();
		data.setKey(key);
		data.setName(name);
		data.setOrder(order);
		data.setAggregation(aggregation);
		data.setUnit(unit);
		data.setShowAsLegend(false);
		data.setMultipleValue(isMultipleValue);
		return data;
	}

	/**
	 * Creates object to hold category related info.
	 *
	 * @return
	 */
	private com.publicissapient.kpidashboard.apis.model.CategoryData createCategoryData() {
		com.publicissapient.kpidashboard.apis.model.CategoryData categoryData = new com.publicissapient.kpidashboard.apis.model.CategoryData();
		categoryData.setCategoryKey("Category");
		categoryData.setCategoryKey2("category2");

		List<KpiDataCategory> categoryGroup = new ArrayList<>();
		categoryGroup.add(createKpiDataCategory(PLANNED, 1));
		categoryGroup.add(createKpiDataCategory(DEV_STATUS, 2));
		categoryGroup.add(createKpiDataCategory(UNPLANNED, 3));
		categoryData.setCategoryGroup(categoryGroup);

		List<KpiDataCategory> categoryGroup2 = new ArrayList<>();
		categoryGroup2.add(createKpiDataCategory(PLANNED_COMPLETION, 1));
		categoryGroup2.add(createKpiDataCategory(ACTUAL_COMPLETION, 2));
		categoryGroup2.add(createKpiDataCategory(DELAY_COUNT, 3));
		categoryData.setCategoryGroup2(categoryGroup2);

		return categoryData;
	}

	/**
	 * Creates kpi data category object.
	 *
	 * @param categoryName
	 * @param order
	 * @return
	 */
	private KpiDataCategory createKpiDataCategory(String categoryName, Integer order) {
		KpiDataCategory category = new KpiDataCategory();
		category.setCategoryName(categoryName);
		category.setOrder(order);
		return category;
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
		filterList.add(createFilter(FILTER_TYPE, FILTER_BY_PRIORITY, "Priority", 2));
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
	 * CHANGE: Refactored method to use parameter objects, reducing from 8 to 6 parameters
	 * @param issue
	 * @param category
	 * @param data
	 * @param category2
	 * @param issueWiseDelay
	 * @param devData
	 */
	void setDataForDevCompletion(JiraIssue issue, Set<String> category, IssueKpiModalValue data,
								 Map<String, List<String>> category2, Map<String, IterationPotentialDelay> issueWiseDelay,
								 DevCompletionData devData) {
		int delay = 0;
		category2.putIfAbsent(DEV_STATUS, new ArrayList<>());
		Map<String, Object> actualCompletionData = (Map<String, Object>) devData.getJiraIssueData().get(ACTUAL_COMPLETION_DATA);

		if (SprintDetails.SPRINT_STATE_ACTIVE.equalsIgnoreCase(devData.getSprintDetails().getState())) {
			// Checking if dev due Date is < today date for active sprint
			if (DateUtil.stringToLocalDate(issue.getDevDueDate(), DateUtil.TIME_FORMAT_WITH_SEC).isBefore(LocalDate.now())) {
				category.add(DEV_STATUS);
				category2.get(DEV_STATUS).add(PLANNED_COMPLETION);
				if (!devData.getJiraIssueData().get(ISSUE_DELAY).equals(Constant.DASH) &&
					devData.getJiraIssueData().get(ISSUE_DELAY) instanceof Integer jiraIssueDataDelay &&
					jiraIssueDataDelay >= 0) {
					int jiraIssueDelay = (int) devData.getJiraIssueData().get(ISSUE_DELAY);
					delay = KpiDataHelper.getDelayInMinutes(jiraIssueDelay);
					populateDelay(delay, category2, DEV_STATUS);
				}
				setKpiSpecificData(data, issueWiseDelay, issue, devData.getJiraIssueData(), actualCompletionData, false);
			}
		} else {
			// Checking if dev due Date is <= sprint End Date for closed sprint
			if (DateUtil.stringToLocalDate(issue.getDevDueDate(), DateUtil.TIME_FORMAT_WITH_SEC).isBefore(
					DateUtil.stringToLocalDate(devData.getSprintDetails().getEndDate(), DateUtil.TIME_FORMAT_WITH_SEC).plusDays(1))) {
				category.add(DEV_STATUS);
				category2.get(DEV_STATUS).add(PLANNED_COMPLETION);
				if (!devData.getJiraIssueData().get(ISSUE_DELAY).equals(Constant.DASH) &&
					devData.getJiraIssueData().get(ISSUE_DELAY) instanceof Integer jiraIssueDataDelay &&
					jiraIssueDataDelay >= 0) {
					int jiraIssueDelay = (int) devData.getJiraIssueData().get(ISSUE_DELAY);
					delay = KpiDataHelper.getDelayInMinutes(jiraIssueDelay);
					populateDelay(delay, category2, DEV_STATUS);
				}
				setKpiSpecificData(data, issueWiseDelay, issue, devData.getJiraIssueData(), actualCompletionData, false);
			}
		}
		// CHANGE: Using parameter object instead of individual parameters
		CategoryDelayData categoryDelayData = new CategoryDelayData(category2, issueWiseDelay, actualCompletionData);
		DelayCalculationContext delayContext = new DelayCalculationContext(issue, devData.getJiraIssueData(),
																		   devData.getDevCompletedIssues(), data, categoryDelayData, delay);
		delay = getDelay(delayContext);
		data.getCategoryWiseDelay().put(DEV_STATUS, delay);
	}

	/**
	 * CHANGE: Refactored method to use parameter object instead of 8 individual parameters
	 * @param context DelayCalculationContext containing all required data
	 */
	private int getDelay(DelayCalculationContext context) {
		int delay = context.getCurrentDelay();
		// Calculating actual work status for only completed issues
		if (context.getDevCompletedIssues().containsKey(context.getIssue())) {
			context.getCategoryDelayData().getCategory2().get(DEV_STATUS).add(ACTUAL_COMPLETION);
			if (DateUtil.stringToLocalDate(context.getIssue().getDevDueDate(), DateUtil.TIME_FORMAT_WITH_SEC)
						.isAfter(LocalDate.now().minusDays(1)) && !context.getJiraIssueData().get(ISSUE_DELAY).equals(Constant.DASH)) {
				int jiraIssueDelay = (int) context.getJiraIssueData().get(ISSUE_DELAY);
				delay = KpiDataHelper.getDelayInMinutes(jiraIssueDelay);
			}
			setKpiSpecificData(context.getData(), context.getCategoryDelayData().getIssueWiseDelay(), context.getIssue(),
							   context.getJiraIssueData(), context.getCategoryDelayData().getActualCompletionData(), false);
		}
		return delay;
	}

	/**
	 * @param issue
	 * @param jiraIssueData
	 * @param sprintDetails
	 * @param allCompletedIssuesList
	 * @param issueWiseDelay
	 * @param data
	 */
	private Map<String, List<String>> setDataForPlanned(JiraIssue issue, Map<String, Object> jiraIssueData,
														SprintDetails sprintDetails, List<String> allCompletedIssuesList,
														Map<String, IterationPotentialDelay> issueWiseDelay, IssueKpiModalValue data, Set<String> category) {
		Map<String, List<String>> category2 = new HashMap<>();
		int delay = 0;
		if (!jiraIssueData.get(ISSUE_DELAY).equals(Constant.DASH)) {
			int jiraIssueDelay = (int) jiraIssueData.get(ISSUE_DELAY);
			delay = KpiDataHelper.getDelayInMinutes(jiraIssueDelay);
		} else if (issueWiseDelay.containsKey(issue.getNumber()) && issueWiseDelay.get(issue.getNumber()).isMaxMarker()) {
			IterationPotentialDelay iterationPotentialDelay = issueWiseDelay.get(issue.getNumber());
			delay = KpiDataHelper.getDelayInMinutes(iterationPotentialDelay.getPotentialDelay());
		}
		category2.putIfAbsent(PLANNED, new ArrayList<>());
		Map<String, Object> actualCompletionData = (Map<String, Object>) jiraIssueData.get(ACTUAL_COMPLETION_DATA);
		if (SprintDetails.SPRINT_STATE_ACTIVE.equalsIgnoreCase(sprintDetails.getState())) {
			// Checking if dueDate is < today date for active sprint
			if (DateUtil.stringToLocalDate(issue.getDueDate(), DateUtil.TIME_FORMAT_WITH_SEC).isBefore(LocalDate.now())) {
				category.add(PLANNED);
				category2.get(PLANNED).add(PLANNED_COMPLETION);
				if (delay >= 0) {
					populateDelay(delay, category2, PLANNED);
					data.getCategoryWiseDelay().put(PLANNED, delay);
				}
				setKpiSpecificData(data, issueWiseDelay, issue, jiraIssueData, actualCompletionData, true);
			}
		} else {
			// Checking if dueDate is <= sprint End Date for closed sprint
			if (DateUtil.stringToLocalDate(issue.getDueDate(), DateUtil.TIME_FORMAT_WITH_SEC).isBefore(
					DateUtil.stringToLocalDate(sprintDetails.getEndDate(), DateUtil.TIME_FORMAT_WITH_SEC).plusDays(1))) {
				category.add(PLANNED);
				category2.get(PLANNED).add(PLANNED_COMPLETION);
				if (delay >= 0) {
					populateDelay(delay, category2, PLANNED);
					data.getCategoryWiseDelay().put(PLANNED, delay);
				}
				setKpiSpecificData(data, issueWiseDelay, issue, jiraIssueData, actualCompletionData, true);
			}
		}
		// CHANGE: Create CategoryInfo object to group related parameters before calling constructor
		CategoryInfo categoryInfo = new CategoryInfo(category, category2, delay, actualCompletionData);
		// CHANGE: Fixed constructor call with correct parameter order and count (6 parameters)
		PlannedCompletedIssueContext context = new PlannedCompletedIssueContext(issue, jiraIssueData,
																				allCompletedIssuesList, data, categoryInfo, issueWiseDelay);
		calculateForPlannedAndCompletedIssues(context);
		return category2;
	}

	/**
	 * CHANGE: Updated method to use CategoryInfo accessor pattern
	 * @param context PlannedCompletedIssueContext containing all required data
	 */
	private void calculateForPlannedAndCompletedIssues(PlannedCompletedIssueContext context) {
		// Calculating actual work status for only completed issues
		if (context.getAllCompletedIssuesList().contains(context.getIssue().getNumber())) {
			// CHANGE: Access category through CategoryInfo wrapper
			context.getCategoryInfo().getCategory().add(PLANNED);
			context.getCategoryInfo().getCategory2().get(PLANNED).add(ACTUAL_COMPLETION);
			if (DateUtil.stringToLocalDate(context.getIssue().getDueDate(), DateUtil.TIME_FORMAT_WITH_SEC)
						.isAfter(LocalDate.now().minusDays(1)) && context.getCategoryInfo().getDelay() >= 0) {
				context.getData().getCategoryWiseDelay().put(PLANNED, context.getCategoryInfo().getDelay());
			}
			// CHANGE: Access actualCompletionData through CategoryInfo wrapper
			setKpiSpecificData(context.getData(), context.getIssueWiseDelay(), context.getIssue(),
							   context.getJiraIssueData(), context.getCategoryInfo().getActualCompletionData(), true);
		}
	}


	private static void populateDelay(int delay, Map<String, List<String>> category2, String categoryKey) {
		if (delay != 0) {
			category2.get(categoryKey).add(DELAY_COUNT);
		}
	}

	/**
	 * @param allIssuesWithDevDueDate
	 * @param allIssueHistories
	 * @param fieldMapping
	 */
	private static Map<JiraIssue, String> getDevCompletedIssues(List<JiraIssue> allIssuesWithDevDueDate,
			List<JiraIssueCustomHistory> allIssueHistories, FieldMapping fieldMapping) {
		Map<JiraIssue, String> devCompletedIssues = new HashMap<>();
		if (CollectionUtils.isNotEmpty(allIssuesWithDevDueDate)) {
			Map<JiraIssue, String> completedIssues = new HashMap<>();
			if (CollectionUtils.isNotEmpty(allIssueHistories)) {
				allIssuesWithDevDueDate.forEach(jiraIssue -> {
					JiraIssueCustomHistory issueCustomHistory = allIssueHistories.stream()
							.filter(jiraIssueCustomHistory -> jiraIssueCustomHistory.getStoryID().equals(jiraIssue.getNumber()))
							.findFirst().orElse(new JiraIssueCustomHistory());
					String devCompletionDate = IterationKpiHelper.getDevCompletionDate(issueCustomHistory,
							fieldMapping.getJiraDevDoneStatusKPI128());
					completedIssues.putIfAbsent(jiraIssue, devCompletionDate);
				});
			}
			devCompletedIssues = completedIssues.entrySet().stream()
					.filter(entry -> !entry.getValue().equalsIgnoreCase(Constant.DASH))
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		}
		return devCompletedIssues;
	}

	/**
	 * Method to calculate story start and completed date
	 *
	 * @param issueCustomHistory
	 * @param allCompletedIssuesList
	 * @param sprintDetail
	 * @param fieldMapping
	 * @return
	 */
	private Map<String, Object> calStartAndEndDate(JiraIssueCustomHistory issueCustomHistory,
			List<String> allCompletedIssuesList, SprintDetails sprintDetail, FieldMapping fieldMapping) {
		List<String> inProgressStatuses = new ArrayList<>();
		List<JiraHistoryChangeLog> filterStatusUpdationLogs = new ArrayList<>();

		LocalDateTime sprintStartDate = DateUtil.stringToLocalDateTime(sprintDetail.getStartDate(),DateUtil.TIME_FORMAT_WITH_SEC);
		LocalDateTime sprintEndDate = DateUtil.stringToLocalDateTime(sprintDetail.getEndDate(),DateUtil.TIME_FORMAT_WITH_SEC);
		Map<String, Object> resultList = new HashMap<>();

		// filtering statusUpdationLogs lies in between sprintStart and sprintEnd
		filterStatusUpdationLogs = getFilterStatusUpdationLogs(issueCustomHistory, filterStatusUpdationLogs,
				sprintStartDate, sprintEndDate);

		// Creating the set of completed status
		Set<String> closedStatus = sprintDetail.getCompletedIssues().stream().map(SprintIssue::getStatus)
				.collect(Collectors.toSet());

		// sorting the story history on basis of UpdatedOn
		filterStatusUpdationLogs.sort(Comparator.comparing(JiraHistoryChangeLog::getUpdatedOn));

		// Getting inProgress Status
		if (null != fieldMapping && CollectionUtils.isNotEmpty(fieldMapping.getJiraStatusForInProgressKPI128())) {
			inProgressStatuses = fieldMapping.getJiraStatusForInProgressKPI128();
		}
		LocalDateTime startDate = null;
		LocalDateTime endDate;
		boolean isStartDateFound = false;

		Map<String, LocalDateTime> closedStatusDateMap = new HashMap<>();
		for (JiraHistoryChangeLog statusUpdationLog : filterStatusUpdationLogs) {
			LocalDateTime activityLocalDate = statusUpdationLog.getUpdatedOn();

			if (inProgressStatuses.contains(statusUpdationLog.getChangedTo()) && !isStartDateFound) {
				startDate = activityLocalDate;
				isStartDateFound = true;
			}

			if (CollectionUtils.isNotEmpty(allCompletedIssuesList) &&
					allCompletedIssuesList.contains(issueCustomHistory.getStoryID()) &&
					closedStatus.contains(statusUpdationLog.getChangedTo())) {
				if (closedStatusDateMap.containsKey(statusUpdationLog.getChangedTo())) {
					closedStatusDateMap.clear();
				}
				closedStatusDateMap.put(statusUpdationLog.getChangedTo(), activityLocalDate);
			}
		}
		// Getting the min date of closed status.
		endDate = closedStatusDateMap.values().stream().filter(Objects::nonNull).min(LocalDateTime::compareTo).orElse(null);
		resultList.put(ACTUAL_START_DATE, startDate);
		resultList.put(ACTUAL_COMPLETE_DATE, endDate);
		return resultList;
	}

	// Filtering the history which happened inside the sprint on basis of activity
	// date
	private List<JiraHistoryChangeLog> getFilterStatusUpdationLogs(JiraIssueCustomHistory issueCustomHistory,
			List<JiraHistoryChangeLog> filterStatusUpdationLogs, LocalDateTime sprintStartDate,
			LocalDateTime sprintEndDate) {
		if (CollectionUtils.isNotEmpty(issueCustomHistory.getStatusUpdationLog())) {
			filterStatusUpdationLogs = issueCustomHistory
					.getStatusUpdationLog().stream().filter(jiraIssueSprint -> DateUtil
							.isWithinDateTimeRange(jiraIssueSprint.getUpdatedOn(), sprintStartDate, sprintEndDate))
					.toList();
		}
		return filterStatusUpdationLogs;
	}

	/**
	 * To calculate delay, devCompletion date
	 *
	 * @param fieldMapping
	 * @param sprintDetails
	 * @param allIssueHistories
	 * @param allCompletedIssuesList
	 * @param jiraIssue
	 * @return
	 */
	private Map<String, Object> jiraIssueCalculation(FieldMapping fieldMapping, SprintDetails sprintDetails,
			List<JiraIssueCustomHistory> allIssueHistories, List<String> allCompletedIssuesList, JiraIssue jiraIssue) {
		Map<String, Object> resultList = new HashMap<>();

		JiraIssueCustomHistory issueCustomHistory = allIssueHistories.stream()
				.filter(jiraIssueCustomHistory -> jiraIssueCustomHistory.getStoryID().equals(jiraIssue.getNumber())).findFirst()
				.orElse(new JiraIssueCustomHistory());

		String devCompletionDate = IterationKpiHelper.getDevCompletionDate(issueCustomHistory,
				fieldMapping.getJiraDevDoneStatusKPI128());
		// calling function for cal actual completion days
		Map<String, Object> actualCompletionData = calStartAndEndDate(issueCustomHistory, allCompletedIssuesList,
				sprintDetails, fieldMapping);
		if (actualCompletionData.get(ACTUAL_COMPLETE_DATE) != null && jiraIssue.getDueDate() != null) {
			LocalDateTime actualCompletedDate = (LocalDateTime) actualCompletionData.get(ACTUAL_COMPLETE_DATE);
			int jiraIssueDelay = getDelay(DateUtil.stringToLocalDate(jiraIssue.getDueDate(), DateUtil.TIME_FORMAT_WITH_SEC),
					actualCompletedDate.toLocalDate());
			resultList.put(ISSUE_DELAY, jiraIssueDelay);
		} else {
			resultList.put(ISSUE_DELAY, Constant.DASH);
		}
		resultList.put(DEV_COMPLETION_DATE, devCompletionDate);
		resultList.put(ACTUAL_COMPLETION_DATA, actualCompletionData);
		return resultList;
	}

	/**
	 * To calculate delay, devCompletion date
	 *
	 * @param fieldMapping
	 * @param sprintDetails
	 * @param allIssueHistories
	 * @param jiraIssue
	 * @param completedIssueMap
	 * @return
	 */
	private Map<String, Object> jiraIssueCalculationDev(FieldMapping fieldMapping, SprintDetails sprintDetails,
			List<JiraIssueCustomHistory> allIssueHistories, JiraIssue jiraIssue, Map<JiraIssue, String> completedIssueMap,
			List<String> allCompletedIssuesList) {
		Map<String, Object> resultList = new HashMap<>();

		JiraIssueCustomHistory issueCustomHistory = allIssueHistories.stream()
				.filter(jiraIssueCustomHistory -> jiraIssueCustomHistory.getStoryID().equals(jiraIssue.getNumber())).findFirst()
				.orElse(new JiraIssueCustomHistory());
		String devCompletionDate = completedIssueMap.getOrDefault(jiraIssue, "-");
		// calling function for call actual completion days
		Map<String, Object> actualCompletionData = calStartAndEndDate(issueCustomHistory, allCompletedIssuesList,
				sprintDetails, fieldMapping);

		if (StringUtils.isNotEmpty(devCompletionDate) && !devCompletionDate.equalsIgnoreCase("-") &&
				jiraIssue.getDevDueDate() != null) {
			LocalDateTime devCompletedDate = LocalDateTime.parse(devCompletionDate);
			int jiraIssueDelay = getDelay(
					DateUtil.stringToLocalDate(jiraIssue.getDevDueDate(), DateUtil.TIME_FORMAT_WITH_SEC), devCompletedDate.toLocalDate());
			resultList.put(ISSUE_DELAY, jiraIssueDelay);
		} else {
			resultList.put(ISSUE_DELAY, Constant.DASH);
		}
		resultList.put(DEV_COMPLETION_DATE, devCompletionDate);
		resultList.put(ACTUAL_COMPLETION_DATA, actualCompletionData);
		return resultList;
	}

	private int getDelay(LocalDate dueDate, LocalDate completedDate) {
		int potentialDelays = CommonUtils.getWorkingDays(dueDate, completedDate);
		return (dueDate.isAfter(completedDate)) ? potentialDelays * (-1) : potentialDelays;
	}

	private LinkedHashMap<String, IterationPotentialDelay> checkMaxDelayAssigneeWise(List<JiraIssue> jiraIssueList,
			List<IterationPotentialDelay> issueWiseDelay, SprintDetails sprintDetails) {
		if (SprintDetails.SPRINT_STATE_ACTIVE.equalsIgnoreCase(sprintDetails.getState())) {
			jiraIssueList = jiraIssueList.stream()
					.filter(jiraIssue -> jiraIssue.getDueDate() != null && DateUtil
							.stringToLocalDate(jiraIssue.getDueDate(), DateUtil.TIME_FORMAT_WITH_SEC).isBefore(LocalDate.now()))
					.collect(Collectors.toList());
		} else {
			jiraIssueList = jiraIssueList.stream()
					.filter(jiraIssue -> jiraIssue.getDueDate() != null &&
							DateUtil.stringToLocalDate(jiraIssue.getDueDate(), DateUtil.TIME_FORMAT_WITH_SEC).isBefore(
									DateUtil.stringToLocalDate(sprintDetails.getEndDate(), DateUtil.TIME_FORMAT_WITH_SEC).plusDays(1)))
					.collect(Collectors.toList());
		}
		Map<String, List<JiraIssue>> assigneeWiseJiraIssue = assigneeWiseJiraIssue(jiraIssueList);
		List<IterationPotentialDelay> maxDelayList = new ArrayList<>();
		if (MapUtils.isNotEmpty(assigneeWiseJiraIssue)) {
			for (List<JiraIssue> jiraIssues : assigneeWiseJiraIssue.values()) {
				List<IterationPotentialDelay> delayList = new ArrayList<>();
				for (JiraIssue jiraIssue : jiraIssues) {
					issueWiseDelay.stream().filter(
							iterationPotentialDelay -> iterationPotentialDelay.getIssueId().equalsIgnoreCase(jiraIssue.getNumber()))
							.forEach(delayList::add);
				}

				if (CollectionUtils.isNotEmpty(delayList)) {
					// fetch the maximum delayed story of each assignee
					// and set the marker in the original IterationPotentialDelay list
					maxDelayList.add(delayList.stream().max(Comparator.comparing(IterationPotentialDelay::getPotentialDelay))
							.orElse(new IterationPotentialDelay()));
				}
			}
			if (CollectionUtils.isNotEmpty(maxDelayList)) {
				maxDelayList.forEach(iterationPotentialDelay -> issueWiseDelay.stream()
						.filter(issue -> issue.equals(iterationPotentialDelay)).forEach(issue -> issue.setMaxMarker(true)));
			}
		}
		return issueWiseDelay.stream().collect(
				Collectors.toMap(IterationPotentialDelay::getIssueId, Function.identity(), (e1, e2) -> e2, LinkedHashMap::new));
	}

	/**
	 * with assignees criteria calculating potential delay for in progress and open
	 * issues and without assignees calculating potential delay for in progress
	 * stories
	 *
	 * @param sprintDetails
	 * @param allIssues
	 * @param fieldMapping
	 * @return
	 */
	private List<IterationPotentialDelay> calculatePotentialDelay(SprintDetails sprintDetails, List<JiraIssue> allIssues,
			FieldMapping fieldMapping) {
		List<IterationPotentialDelay> iterationPotentialDelayList = new ArrayList<>();
		Map<String, List<JiraIssue>> assigneeWiseJiraIssue = assigneeWiseJiraIssue(allIssues);

		if (MapUtils.isNotEmpty(assigneeWiseJiraIssue)) {
			assigneeWiseJiraIssue.forEach((assignee, jiraIssues) -> {
				List<JiraIssue> inProgressIssues = new ArrayList<>();
				List<JiraIssue> openIssues = new ArrayList<>();
				CalculatePCDHelper.arrangeJiraIssueList(fieldMapping.getJiraStatusForInProgressKPI128(), jiraIssues,
						inProgressIssues, openIssues);
				iterationPotentialDelayList.addAll(sprintWiseDelayCalculation(inProgressIssues, openIssues, sprintDetails));
			});
		}

		if (CollectionUtils.isNotEmpty(fieldMapping.getJiraStatusForInProgressKPI128())) {
			List<JiraIssue> inProgressIssues = allIssues.stream()
					.filter(jiraIssue -> (jiraIssue.getAssigneeId() == null) && StringUtils.isNotEmpty(jiraIssue.getDueDate()) &&
							(fieldMapping.getJiraStatusForInProgressKPI128().contains(jiraIssue.getStatus())))
					.collect(Collectors.toList());

			List<JiraIssue> openIssues = new ArrayList<>();
			iterationPotentialDelayList.addAll(sprintWiseDelayCalculation(inProgressIssues, openIssues, sprintDetails));
		}
		return iterationPotentialDelayList;
	}

	private Map<String, List<JiraIssue>> assigneeWiseJiraIssue(List<JiraIssue> allIssues) {
		return allIssues.stream().filter(jiraIssue -> jiraIssue.getAssigneeId() != null)
				.collect(Collectors.groupingBy(JiraIssue::getAssigneeName));
	}

	private void setKpiSpecificData(IssueKpiModalValue jiraIssueModalObject,
			Map<String, IterationPotentialDelay> issueWiseDelay, JiraIssue jiraIssue, Map<String, Object> jiraIssueData,
			Map<String, Object> actualCompletionData, boolean isPlanned) {
		String devCompletion=(String)jiraIssueData.get(DEV_COMPLETION_DATE);
		jiraIssueModalObject.setDevCompletionDate(devCompletion);
		if(StringUtils.isNotEmpty(devCompletion) && !devCompletion.equalsIgnoreCase(Constant.DASH)) {
			jiraIssueModalObject.setDevCompletionDate(DateUtil.localDateTimeToUTC(jiraIssueData.get(DEV_COMPLETION_DATE).toString()));
		}
		if (actualCompletionData.get(ACTUAL_COMPLETE_DATE) != null)
			jiraIssueModalObject
					.setActualCompletionDate(DateUtil.localDateTimeToUTC(actualCompletionData.get(ACTUAL_COMPLETE_DATE).toString()));
		else
			jiraIssueModalObject.setActualCompletionDate(Constant.BLANK);
		if (actualCompletionData.get(ACTUAL_START_DATE) != null) {
			jiraIssueModalObject.setActualStartDate(DateUtil.localDateTimeToUTC(actualCompletionData.get(ACTUAL_START_DATE).toString()));
		} else
			jiraIssueModalObject.setActualStartDate(Constant.BLANK);

		if (isPlanned) {
			calculateKpiSpecificDataForPlanned(jiraIssueModalObject, jiraIssue, jiraIssueData);
		} else {
			calculateKpiSpecificDataForNonPlanned(jiraIssueModalObject, jiraIssue, jiraIssueData);
		}
		if (StringUtils.isNotEmpty(jiraIssue.getDueDate()) && issueWiseDelay.containsKey(jiraIssue.getNumber()) &&
				issueWiseDelay.get(jiraIssue.getNumber()).isMaxMarker()) {
			IterationPotentialDelay iterationPotentialDelay = issueWiseDelay.get(jiraIssue.getNumber());
			jiraIssueModalObject.setPotentialDelay(iterationPotentialDelay.getPotentialDelay() + "d");
			jiraIssueModalObject.setPredictedCompletionDate(iterationPotentialDelay.getPredictedCompletedDate());
		}
	}

	private void calculateKpiSpecificDataForPlanned(
			IssueKpiModalValue jiraIssueModalObject, JiraIssue jiraIssue, Map<String, Object> jiraIssueData
	) {
		if (DateUtil.stringToLocalDateTime(jiraIssue.getDueDate(), DateUtil.TIME_FORMAT_WITH_SEC)
				.isAfter(DateUtil.getTodayTime().minusDays(1))) {
			jiraIssueModalObject.setMarker(Constant.GREEN);
		}
		if (!jiraIssueData.get(ISSUE_DELAY).equals(Constant.DASH)) {
			jiraIssueModalObject.setDelayInDays(jiraIssueData.get(ISSUE_DELAY) + "d");
		} else {
			jiraIssueModalObject.setDelayInDays(Constant.BLANK);
		}
	}

	private void calculateKpiSpecificDataForNonPlanned(
			IssueKpiModalValue jiraIssueModalObject, JiraIssue jiraIssue, Map<String, Object> jiraIssueData
	) {
		if (DateUtil.stringToLocalDateTime(jiraIssue.getDevDueDate(), DateUtil.TIME_FORMAT_WITH_SEC)
				.isAfter(DateUtil.getTodayTime().minusDays(1))) {
			jiraIssueModalObject.setMarker(Constant.GREEN);
		}
		if (!jiraIssueData.get(ISSUE_DELAY).equals(Constant.DASH)) {
			jiraIssueModalObject.setDevDelayInDays(jiraIssueData.get(ISSUE_DELAY) + "d");
		} else {
			jiraIssueModalObject.setDevDelayInDays(Constant.BLANK);
		}
	}
}
