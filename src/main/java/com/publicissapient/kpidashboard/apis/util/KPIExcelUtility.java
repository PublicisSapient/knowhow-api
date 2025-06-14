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

package com.publicissapient.kpidashboard.apis.util;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.publicissapient.kpidashboard.apis.model.DefectTransitionInfo;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.jira.scrum.service.CommittmentReliabilityServiceImpl;
import com.publicissapient.kpidashboard.apis.model.BuildFrequencyInfo;
import com.publicissapient.kpidashboard.apis.model.ChangeFailureRateInfo;
import com.publicissapient.kpidashboard.apis.model.CodeBuildTimeInfo;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.DSRValidationData;
import com.publicissapient.kpidashboard.apis.model.DeploymentFrequencyInfo;
import com.publicissapient.kpidashboard.apis.model.IssueKpiModalValue;
import com.publicissapient.kpidashboard.apis.model.IterationKpiModalValue;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.LeadTimeChangeData;
import com.publicissapient.kpidashboard.apis.model.MeanTimeRecoverData;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.repotools.model.RepoToolValidationData;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.AdditionalFilterValue;
import com.publicissapient.kpidashboard.common.model.application.CycleTimeValidationData;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.LeadTimeData;
import com.publicissapient.kpidashboard.common.model.application.ProjectVersion;
import com.publicissapient.kpidashboard.common.model.application.ResolutionTimeValidation;
import com.publicissapient.kpidashboard.common.model.jira.HappinessKpiData;
import com.publicissapient.kpidashboard.common.model.jira.IssueDetails;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueReleaseStatus;
import com.publicissapient.kpidashboard.common.model.jira.KanbanIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.KanbanJiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.ReleaseVersion;
import com.publicissapient.kpidashboard.common.model.jira.UserRatingData;
import com.publicissapient.kpidashboard.common.model.testexecution.KanbanTestExecution;
import com.publicissapient.kpidashboard.common.model.testexecution.TestExecution;
import com.publicissapient.kpidashboard.common.model.zephyr.TestCaseDetails;
import com.publicissapient.kpidashboard.common.util.DateUtil;

/**
 * The class contains mapping of kpi and Excel columns.
 *
 * @author pkum34
 */
public class KPIExcelUtility {

	public static final String TIME = "0d ";
	private static final String MONTH_YEAR_FORMAT = "MMM yyyy";
	private static final String DATE_YEAR_MONTH_FORMAT = "dd-MMM-yy";
	private static final String ITERATION_DATE_FORMAT = "yyyy-MM-dd";
	private static final DecimalFormat df2 = new DecimalFormat(".##");
	private static final String STATUS = "Status";
	private static final String WEEK = "Week";
	private static final String UNDEFINED = "Undefined";

	private KPIExcelUtility() {
	}

	/**
	 * This method populate the excel data for DIR KPI
	 *
	 * @param storyIds
	 *          storyIds
	 * @param defects
	 *          defects
	 * @param kpiExcelData
	 *          kpiExcelData
	 * @param issueData
	 *          issueData
	 * @param fieldMapping
	 * @param customApiConfig
	 */
	public static void populateDirExcelData(List<String> storyIds, List<JiraIssue> defects,
			List<KPIExcelData> kpiExcelData, Map<String, JiraIssue> issueData, FieldMapping fieldMapping,
			CustomApiConfig customApiConfig, Node node) {
		if (CollectionUtils.isNotEmpty(storyIds)) {
			setQualityKPIExcelData(storyIds, defects, kpiExcelData, issueData, fieldMapping, customApiConfig, node);
		}
	}

	private static void setQualityCommonExcelData(JiraIssue jiraIssue, String story,
			Map<String, JiraIssue> defectIssueMap, KPIExcelData excelData, String defectNumber,
			CustomApiConfig customApiConfig, Node node) {

		JiraIssue defect = defectIssueMap.get(defectNumber);
		if (jiraIssue != null) {
			String sprintName = node.getSprintFilter().getName();
			excelData.setSprintName(sprintName);
			excelData.setStoryDesc(checkEmptyName(jiraIssue));
			excelData.setStoryId(Collections.singletonMap(story, checkEmptyURL(jiraIssue)));
			setSquads(excelData, jiraIssue);
			excelData.setPriority(setPriority(customApiConfig, jiraIssue));
			Integer totalTimeSpent = Objects.requireNonNullElse(jiraIssue.getTimeSpentInMinutes(),0);
			if (defect != null) {
				excelData.setDefectId(Collections.singletonMap(defectNumber, checkEmptyURL(defect)));
				excelData.setDefectDesc(checkEmptyName(defect));
				excelData.setRootCause(defect.getRootCauseList());
				excelData.setDefectPriority(setPriority(customApiConfig, defect));
				excelData.setDefectStatus(defect.getStatus());
				totalTimeSpent = totalTimeSpent + Objects.requireNonNullElse(defect.getTimeSpentInMinutes(), 0);
			} else {
				excelData.setDefectId(Collections.emptyMap());
				excelData.setDefectDesc(Constant.BLANK);
				excelData.setRootCause(Collections.emptyList());
				excelData.setDefectStatus(Constant.BLANK);
			}
			excelData.setTotalTimeSpent(totalTimeSpent != null ? String.valueOf((totalTimeSpent / 60)) : "0");
		}
	}

	public static void populateDefectDensityExcelData(List<String> storyIds, List<JiraIssue> defects,
			List<KPIExcelData> kpiExcelData, Map<String, JiraIssue> issueData, FieldMapping fieldMapping,
			CustomApiConfig customApiConfig, Node node) {
		if (CollectionUtils.isNotEmpty(storyIds)) {
			setQualityKPIExcelData(storyIds, defects, kpiExcelData, issueData, fieldMapping, customApiConfig, node);
		}
	}

	private static void setQualityKPIExcelData(List<String> storyIds, List<JiraIssue> defects,
			List<KPIExcelData> kpiExcelData, Map<String, JiraIssue> issueData, FieldMapping fieldMapping,
			CustomApiConfig customApiConfig, Node node) {
		storyIds.forEach(story -> {
			List<JiraIssue> linkedDefects = defects.stream().filter(defect -> defect.getDefectStoryID().contains(story))
					.toList();

			if (!linkedDefects.isEmpty()) {
				linkedDefects.forEach(defect -> {
					KPIExcelData excelData = new KPIExcelData();
					Map<String, JiraIssue> defectIssueMap = Collections.singletonMap(defect.getNumber(), defect);
					JiraIssue jiraIssue = issueData.get(story);
					setQualityCommonExcelData(jiraIssue, story, defectIssueMap, excelData, defect.getNumber(), customApiConfig,
							node);
					setStoryPoint(fieldMapping, excelData, jiraIssue);
					kpiExcelData.add(excelData);
				});
			} else {
				KPIExcelData excelData = new KPIExcelData();
				JiraIssue jiraIssue = issueData.get(story);
				setQualityCommonExcelData(jiraIssue, story, Collections.emptyMap(), excelData, Constant.BLANK, customApiConfig,
						node);
				setStoryPoint(fieldMapping, excelData, jiraIssue);
				kpiExcelData.add(excelData);
			}
		});
	}

	private static void setStoryPoint(FieldMapping fieldMapping, KPIExcelData excelData, JiraIssue jiraIssue) {

		if (StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria()) &&
				fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT) && (jiraIssue != null)) {
			excelData.setStoryPoint(String.valueOf(roundingOff(jiraIssue.getStoryPoints())));
		} else if ((jiraIssue != null) && (null != jiraIssue.getAggregateTimeOriginalEstimateMinutes())) {
			Double originalEstimateInHours = Double.valueOf(jiraIssue.getAggregateTimeOriginalEstimateMinutes()) / 60;
			excelData.setStoryPoint(roundingOff(originalEstimateInHours / fieldMapping.getStoryPointToHourMapping()) + "/" +
					roundingOff(originalEstimateInHours) + " hrs");
		}
	}

	public static List<KPIExcelData> populateFTPRExcelData(List<String> storyIds, List<JiraIssue> ftprStories,
			List<KPIExcelData> kpiExcelData, Map<String, JiraIssue> issueData, List<JiraIssue> defects,
			CustomApiConfig customApiConfig, FieldMapping fieldMapping, Node node) {
		List<String> collectFTPIds = ftprStories.stream().map(JiraIssue::getNumber).collect(Collectors.toList());
		setQualityKPIExcelData(storyIds, defects, kpiExcelData, issueData, fieldMapping, customApiConfig, node);
		setFTPRSpecificData(kpiExcelData, collectFTPIds);
		return kpiExcelData;
	}

	private static void setFTPRSpecificData(List<KPIExcelData> kpiExcelData, List<String> collectFTPIds) {
		kpiExcelData.forEach(story -> {
			boolean isFirstTimePass = true;
			if(MapUtils.isNotEmpty(story.getStoryId())) {
				isFirstTimePass = story.getStoryId().keySet().stream().anyMatch(collectFTPIds::contains);
			}
			story.setFirstTimePass(isFirstTimePass ? Constant.EXCEL_YES : Constant.EMPTY_STRING);
		});
	}

	/**
	 * TO GET Constant.EXCEL_YES/"N" from complete list of defects if defect is
	 * present in conditional list then Constant.EXCEL_YES else "N" kpi specific
	 *
	 * @param sprint
	 *          sprint
	 * @param totalBugList
	 *          Map of total bug
	 * @param conditionDefects
	 *          conditionDefects
	 * @param kpiExcelData
	 *          kpiExcelData
	 * @param kpiId
	 *          kpiId
	 */
	public static void populateDefectRelatedExcelData(String sprint, Map<String, JiraIssue> totalBugList,
			List<JiraIssue> conditionDefects, List<KPIExcelData> kpiExcelData, String kpiId, CustomApiConfig customApiConfig,
			List<JiraIssue> storyList) {

		if (MapUtils.isNotEmpty(totalBugList)) {
			List<String> conditionalList = conditionDefects.stream().map(JiraIssue::getNumber).collect(Collectors.toList());

			totalBugList.forEach((defectId, jiraIssue) -> {
				String present = conditionalList.contains(defectId) ? Constant.EXCEL_YES : Constant.EMPTY_STRING;
				KPIExcelData excelData = new KPIExcelData();
				excelData.setSprintName(sprint);
				excelData.setDefectDesc(checkEmptyName(jiraIssue));
				Map<String, String> defectIdDetails = new HashMap<>();
				defectIdDetails.put(defectId, checkEmptyURL(jiraIssue));
				excelData.setDefectId(defectIdDetails);
				setSquads(excelData, jiraIssue);
				excelData.setDefectPriority(setPriority(customApiConfig, jiraIssue));
				excelData.setRootCause(jiraIssue.getRootCauseList());
				excelData.setLabels(jiraIssue.getLabels());
				Integer totalTimeSpentInMinutes = Objects.requireNonNullElse(jiraIssue.getTimeSpentInMinutes(), 0);
				setStoryExcelData(storyList, jiraIssue, excelData, totalTimeSpentInMinutes, customApiConfig);

				if (kpiId.equalsIgnoreCase(KPICode.DEFECT_REMOVAL_EFFICIENCY.getKpiId())) {
					excelData.setDefectStatus(jiraIssue.getStatus());
					excelData.setRemovedDefect(present);
				}
				if (kpiId.equalsIgnoreCase(KPICode.DEFECT_REJECTION_RATE.getKpiId())) {
					excelData.setDefectStatus(jiraIssue.getStatus());
					excelData.setRejectedDefect(present);
				}
				if (kpiId.equalsIgnoreCase(KPICode.OPEN_DEFECT_RATE.getKpiId())) {
					excelData.setOpenDefect(present);
				}

				kpiExcelData.add(excelData);
			});
		}
	}

	/**
	 * @param sprint
	 *          sprint
	 * @param totalBugList
	 *          totalBugList
	 * @param dsrValidationDataList
	 *          dsrValidationDataList
	 * @param excelDataList
	 *          excelDataList
	 * @param customApiConfig
	 * @param totalStoryWoDrop
	 */
	public static void populateDefectSeepageRateExcelData(String sprint, Map<String, JiraIssue> totalBugList,
			List<DSRValidationData> dsrValidationDataList, List<KPIExcelData> excelDataList, CustomApiConfig customApiConfig,
			List<JiraIssue> totalStoryWoDrop) {

		Map<String, String> labelWiseValidationData = dsrValidationDataList.stream().collect(Collectors
				.toMap(DSRValidationData::getIssueNumber, DSRValidationData::getLabel, (x, y) -> x + CommonConstant.COMMA + y));
		totalBugList.forEach((defectId, jiraIssue) -> {
			String label = Constant.EMPTY_STRING;
			String present = Constant.EMPTY_STRING;
			if (labelWiseValidationData.containsKey(defectId)) {
				present = Constant.EXCEL_YES;
				label = StringUtils.capitalize(labelWiseValidationData.get(defectId));
			}
			KPIExcelData excelData = new KPIExcelData();
			excelData.setSprintName(sprint);
			excelData.setDefectDesc(checkEmptyName(jiraIssue));
			Map<String, String> defectIdDetails = new HashMap<>();
			defectIdDetails.put(defectId, checkEmptyURL(jiraIssue));
			setSquads(excelData, jiraIssue);
			excelData.setDefectId(defectIdDetails);
			excelData.setEscapedDefect(present);
			excelData.setEscapedIdentifier(label);
			excelData.setDefectPriority(setPriority(customApiConfig, jiraIssue));
			excelData.setRootCause(jiraIssue.getRootCauseList());
			excelData.setDefectStatus(jiraIssue.getStatus());
			Integer totalTimeSpentInMinutes = Objects.requireNonNullElse(jiraIssue.getTimeSpentInMinutes(),0);
			setStoryExcelData(totalStoryWoDrop, jiraIssue, excelData, totalTimeSpentInMinutes, customApiConfig);
			excelDataList.add(excelData);
		});
	}

	private static int setStoryExcelData(List<JiraIssue> totalStoryWoDrop, JiraIssue jiraIssue, KPIExcelData excelData,
			int totalTimeSpentInMinutes, CustomApiConfig customApiConfig) {
		AtomicInteger totalTimeSpent = new AtomicInteger(totalTimeSpentInMinutes);

		if (CollectionUtils.isNotEmpty(totalStoryWoDrop) && jiraIssue.getDefectStoryID() != null) {
			List<JiraIssue> jiraIssueList = totalStoryWoDrop.stream()
					.filter(issue -> jiraIssue.getDefectStoryID().contains(issue.getNumber())).collect(Collectors.toList());

			jiraIssueList.forEach(story -> {
				excelData.setStoryDesc(checkEmptyName(story));
				Map<String, String> storyIdDetails = new HashMap<>();
				storyIdDetails.put(story.getNumber(), checkEmptyURL(story));
				excelData.setStoryId(storyIdDetails);
				excelData.setPriority(setPriority(customApiConfig, story));

				if (story.getTimeSpentInMinutes() != null) {
					totalTimeSpent.addAndGet(story.getTimeSpentInMinutes());
				}
			});
		}
		excelData.setTotalTimeSpent(String.valueOf(totalTimeSpent.get() / 60));
		return totalTimeSpent.get();
	}

	/**
	 * to get direct related values of a jira issue like priority/RCA from total
	 * list
	 *
	 * @param sprint
	 * @param jiraIssues
	 * @param kpiExcelData
	 * @param storyList
	 */
	public static void populateDefectRelatedExcelData(String sprint, List<JiraIssue> jiraIssues,
			List<KPIExcelData> kpiExcelData, CustomApiConfig customApiConfig, List<JiraIssue> storyList) {
		if (CollectionUtils.isNotEmpty(jiraIssues)) {
			jiraIssues.stream().forEach(jiraIssue -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setSprintName(sprint);
				excelData.setDefectDesc(checkEmptyName(jiraIssue));
				setSquads(excelData, jiraIssue);
				Map<String, String> defectIdDetails = new HashMap<>();
				defectIdDetails.put(jiraIssue.getNumber(), checkEmptyURL(jiraIssue));
				excelData.setDefectId(defectIdDetails);
				excelData.setDefectPriority(setPriority(customApiConfig, jiraIssue));
				excelData.setRootCause(jiraIssue.getRootCauseList());
				excelData.setDefectStatus(jiraIssue.getStatus());
				Integer totalTimeSpentInMinutes = Objects.requireNonNullElse(jiraIssue.getTimeSpentInMinutes(), 0);
				setStoryExcelData(storyList, jiraIssue, excelData, totalTimeSpentInMinutes, customApiConfig);
				kpiExcelData.add(excelData);
			});
		}
	}

	/**
	 * Use to set priority of issue
	 *
	 * @param customApiConfig
	 *          customApiConfig
	 * @param jiraIssue
	 *          jiraIssue
	 */
	private static String setPriority(CustomApiConfig customApiConfig, JiraIssue jiraIssue) {
		List<String> priorities;
		String priority;
		String issuePriority = " ";
		if (StringUtils.isNotEmpty(jiraIssue.getPriority())) {
			if (StringUtils.containsIgnoreCase(customApiConfig.getpriorityP1().replaceAll(Constant.WHITESPACE, "").trim(),
					jiraIssue.getPriority().replaceAll(Constant.WHITESPACE, "").toLowerCase().trim())) {
				priorities = Arrays.asList("P1 - Blocker", "p1");
				priority = Constant.P1;
				issuePriority = getIssuePriority(jiraIssue, priorities, priority);
			} else if (StringUtils.containsIgnoreCase(
					customApiConfig.getpriorityP2().replaceAll(Constant.WHITESPACE, "").trim(),
					jiraIssue.getPriority().replaceAll(Constant.WHITESPACE, "").toLowerCase().trim())) {
				priorities = Arrays.asList("P2 - Critical", "p2");
				priority = Constant.P2;
				issuePriority = getIssuePriority(jiraIssue, priorities, priority);
			} else if (StringUtils.containsIgnoreCase(
					customApiConfig.getpriorityP3().replaceAll(Constant.WHITESPACE, "").trim(),
					jiraIssue.getPriority().replaceAll(Constant.WHITESPACE, "").toLowerCase().trim())) {
				priorities = Arrays.asList("P3 - Major", "p3");
				priority = Constant.P3;
				issuePriority = getIssuePriority(jiraIssue, priorities, priority);
			} else if (StringUtils.containsIgnoreCase(
					customApiConfig.getpriorityP4().replaceAll(Constant.WHITESPACE, "").trim(),
					jiraIssue.getPriority().replaceAll(Constant.WHITESPACE, "").toLowerCase().trim())) {
				priorities = Arrays.asList("P4 - Minor", "p4");
				priority = Constant.P4;
				issuePriority = getIssuePriority(jiraIssue, priorities, priority);
			} else {
				issuePriority = Constant.MISC + "- " + jiraIssue.getPriority();
			}
		}
		return issuePriority;
	}

	private static String getIssuePriority(JiraIssue jiraIssue, List<String> priorities, String priority) {
		if (priorities.contains(jiraIssue.getPriority())) {
			return jiraIssue.getPriority();
		} else {
			return priority + "- " + jiraIssue.getPriority();
		}
	}

	public static void populateDefectRCAandStatusRelatedExcelData(String sprint, List<JiraIssue> jiraIssues,
			List<JiraIssue> createDuringIteration, List<KPIExcelData> kpiExcelData, FieldMapping fieldMapping) {
		if (CollectionUtils.isNotEmpty(jiraIssues)) {
			jiraIssues.stream().forEach(jiraIssue -> {
				KPIExcelData excelData = new KPIExcelData();
				String present = createDuringIteration.contains(jiraIssue) ? Constant.EXCEL_YES : Constant.EMPTY_STRING;
				excelData.setSprintName(sprint);
				Map<String, String> defectIdDetails = new HashMap<>();
				defectIdDetails.put(jiraIssue.getNumber(), checkEmptyURL(jiraIssue));
				excelData.setDefectId(defectIdDetails);
				excelData.setIssueDesc(checkEmptyName(jiraIssue));
				excelData.setIssueStatus(jiraIssue.getStatus());
				excelData.setIssueType(jiraIssue.getTypeName());
				populateAssignee(jiraIssue, excelData);
				setSquads(excelData, jiraIssue);

				if (null != jiraIssue.getStoryPoints() && StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria()) &&
						fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT)) {
					excelData.setStoryPoint(String.valueOf(roundingOff(jiraIssue.getStoryPoints())));
				}
				if (null != jiraIssue.getAggregateTimeOriginalEstimateMinutes() &&
						StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria()) &&
						fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.ACTUAL_ESTIMATION)) {
					excelData
							.setStoryPoint((roundingOff((double) jiraIssue.getAggregateTimeOriginalEstimateMinutes() / 60) + " hrs"));
				}
				excelData.setRootCause(jiraIssue.getRootCauseList());
				excelData.setPriority(jiraIssue.getPriority());
				excelData.setCreatedDuringIteration(present);
				kpiExcelData.add(excelData);
			});
		}
	}

	/**
	 * TO GET Constant.EXCEL_YES/"N" from complete list of defects if defect is
	 * present in conditional list then Constant.EXCEL_YES else
	 * Constant.EMPTY_STRING kpi specific
	 *
	 * @param sprint
	 * @param totalStoriesMap
	 * @param createdConditionStories
	 * @param closedIssuesWithStatus
	 * @param kpiExcelData
	 * @param customApiConfig
	 * @param storyList
	 */
	public static void populateCreatedVsResolvedExcelData(String sprint, Map<String, JiraIssue> totalStoriesMap,
			List<JiraIssue> createdConditionStories, Map<String, String> closedIssuesWithStatus,
			List<KPIExcelData> kpiExcelData, CustomApiConfig customApiConfig, List<JiraIssue> storyList) {
		if (MapUtils.isNotEmpty(totalStoriesMap)) {
			List<String> createdConditionalList = createdConditionStories.stream().map(JiraIssue::getNumber)
					.collect(Collectors.toList());
			totalStoriesMap.forEach((storyId, jiraIssue) -> {
				String resolvedStatus = closedIssuesWithStatus.containsKey(storyId)
						? closedIssuesWithStatus.get(storyId)
						: Constant.EMPTY_STRING;
				String createdAfterSprint = createdConditionalList.contains(storyId)
						? Constant.EXCEL_YES
						: Constant.EMPTY_STRING;
				KPIExcelData excelData = new KPIExcelData();
				excelData.setSprintName(sprint);
				setSquads(excelData, jiraIssue);
				excelData.setDefectDesc(checkEmptyName(jiraIssue));
				Map<String, String> storyDetails = new HashMap<>();
				storyDetails.put(storyId, checkEmptyURL(jiraIssue));
				excelData.setCreatedDefectId(storyDetails);
				excelData.setResolvedStatus(resolvedStatus);
				excelData.setDefectAddedAfterSprintStart(createdAfterSprint);
				excelData.setDefectPriority(setPriority(customApiConfig, jiraIssue));
				excelData.setRootCause(jiraIssue.getRootCauseList());
				excelData.setDefectStatus(jiraIssue.getStatus());
				Integer totalTimeSpentInMinutes = Objects.requireNonNullElse(jiraIssue.getTimeSpentInMinutes(),0);
				setStoryExcelData(storyList, jiraIssue, excelData, totalTimeSpentInMinutes, customApiConfig);
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateRegressionAutomationExcelData(String sprintProject,
			Map<String, TestCaseDetails> totalStoriesMap, List<TestCaseDetails> conditionStories,
			List<KPIExcelData> kpiExcelData, String kpiId, String date) {
		if (MapUtils.isNotEmpty(totalStoriesMap)) {
			List<String> conditionalList = conditionStories.stream().map(TestCaseDetails::getNumber)
					.collect(Collectors.toList());
			totalStoriesMap.forEach((storyId, jiraIssue) -> {
				String present = conditionalList.contains(storyId) ? Constant.EXCEL_YES : Constant.EMPTY_STRING;
				KPIExcelData excelData = new KPIExcelData();
				if (kpiId.equalsIgnoreCase(KPICode.REGRESSION_AUTOMATION_COVERAGE.getKpiId())) {
					excelData.setSprintName(sprintProject);
				} else {
					excelData.setProject(sprintProject);
					excelData.setDayWeekMonth(date);
				}
				excelData.setTestCaseId(storyId);
				excelData.setAutomated(present);
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateSonarKpisExcelData(String projectName, List<String> jobList,
			List<String> kpiSpecificDataList, List<String> versionDate, List<KPIExcelData> kpiExcelData, String kpiId) {
		if (CollectionUtils.isNotEmpty(jobList)) {
			for (int i = 0; i < jobList.size(); i++) {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProject(projectName);
				excelData.setJobName(jobList.get(i));

				if (kpiId.equalsIgnoreCase(KPICode.UNIT_TEST_COVERAGE.getKpiId()) ||
						kpiId.equalsIgnoreCase(KPICode.UNIT_TEST_COVERAGE_KANBAN.getKpiId())) {
					excelData.setUnitCoverage(kpiSpecificDataList.get(i) + " %");
				} else if (kpiId.equalsIgnoreCase(KPICode.SONAR_TECH_DEBT.getKpiId()) ||
						kpiId.equalsIgnoreCase(KPICode.SONAR_TECH_DEBT_KANBAN.getKpiId())) {
					excelData.setTechDebt(kpiSpecificDataList.get(i));
				} else if (kpiId.equalsIgnoreCase(KPICode.SONAR_CODE_QUALITY.getKpiId())) {
					excelData.setCodeQuality(kpiSpecificDataList.get(i) + " unit");
				}
				setSonarKpiWeekDayMonthColumn(versionDate.get(i), excelData, kpiId);
				kpiExcelData.add(excelData);
			}
		}
	}

	public static void populateSonarViolationsExcelData(String projectName, List<String> jobList,
			List<List<String>> kpiSpecificDataList, List<String> versionDate, List<KPIExcelData> kpiExcelData, String kpiId) {
		if (CollectionUtils.isNotEmpty(jobList)) {
			for (int i = 0; i < jobList.size(); i++) {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProject(projectName);
				excelData.setJobName(jobList.get(i));
				excelData.setSonarViolationSeverity(kpiSpecificDataList.get(i).get(0));
				excelData.setSonarViolationType(kpiSpecificDataList.get(i).get(1));
				setSonarKpiWeekDayMonthColumn(versionDate.get(i), excelData, kpiId);
				kpiExcelData.add(excelData);
			}
		}
	}

	private static void setSonarKpiWeekDayMonthColumn(String versionDate, KPIExcelData excelData, String kpiId) {
		if (kpiId.equalsIgnoreCase(KPICode.UNIT_TEST_COVERAGE.getKpiId()) ||
				kpiId.equalsIgnoreCase(KPICode.SONAR_TECH_DEBT.getKpiId()) ||
				kpiId.equalsIgnoreCase(KPICode.CODE_VIOLATIONS.getKpiId())) {
			excelData.setWeeks(versionDate);
		} else if (kpiId.equalsIgnoreCase(KPICode.SONAR_CODE_QUALITY.getKpiId())) {
			excelData.setMonth(versionDate);
		} else {
			excelData.setDayWeekMonth(versionDate);
		}
	}

	public static void populateInSprintAutomationExcelData(String sprint, List<TestCaseDetails> allTestList,
			List<TestCaseDetails> automatedList, Set<JiraIssue> linkedStories, List<KPIExcelData> kpiExcelData) {

		if (CollectionUtils.isNotEmpty(allTestList)) {
			List<String> conditionalList = automatedList.stream().map(TestCaseDetails::getNumber)
					.collect(Collectors.toList());
			allTestList.forEach(testIssue -> {
				String present = conditionalList.contains(testIssue.getNumber()) ? Constant.EXCEL_YES : Constant.EMPTY_STRING;
				Map<String, String> linkedStoriesMap = new HashMap<>();
				linkedStories.stream().filter(story -> testIssue.getDefectStoryID().contains(story.getNumber()))
						.forEach(story -> linkedStoriesMap.putIfAbsent(story.getNumber(), checkEmptyURL(story)));

				KPIExcelData excelData = new KPIExcelData();
				excelData.setSprintName(sprint);
				excelData.setTestCaseId(testIssue.getNumber());
				excelData.setLinkedStory(linkedStoriesMap);
				excelData.setAutomated(present);
				kpiExcelData.add(excelData);
			});
		}
	}

	private static String checkEmptyName(Object object) {
		String description = "";
		if (object instanceof JiraIssue) {
			JiraIssue jiraIssue = (JiraIssue) object;
			description = StringUtils.isEmpty(jiraIssue.getName()) ? Constant.EMPTY_STRING : jiraIssue.getName();
		}
		if (object instanceof KanbanJiraIssue) {
			KanbanJiraIssue jiraIssue = (KanbanJiraIssue) object;
			description = StringUtils.isEmpty(jiraIssue.getName()) ? Constant.EMPTY_STRING : jiraIssue.getName();
		}
		if (object instanceof KanbanIssueCustomHistory) {
			KanbanIssueCustomHistory jiraIssue = (KanbanIssueCustomHistory) object;
			description = StringUtils.isEmpty(jiraIssue.getDescription())
					? Constant.EMPTY_STRING
					: jiraIssue.getDescription();
		}
		if (object instanceof JiraIssueCustomHistory) {
			JiraIssueCustomHistory jiraIssue = (JiraIssueCustomHistory) object;
			description = StringUtils.isEmpty(jiraIssue.getDescription())
					? Constant.EMPTY_STRING
					: jiraIssue.getDescription();
		}
		if (object instanceof ResolutionTimeValidation) {
			ResolutionTimeValidation resolutionTimeValidation = (ResolutionTimeValidation) object;
			description = StringUtils.isEmpty(resolutionTimeValidation.getIssueDescription())
					? Constant.EMPTY_STRING
					: resolutionTimeValidation.getIssueDescription();
		}
		if (object instanceof IssueDetails) {
			IssueDetails issueDetails = (IssueDetails) object;
			description = StringUtils.isEmpty(issueDetails.getDesc()) ? Constant.EMPTY_STRING : issueDetails.getDesc();
		}

		return description;
	}

	protected static String checkEmptyURL(Object object) {
		String url = "";
		if (object instanceof JiraIssue) {
			JiraIssue jiraIssue = (JiraIssue) object;
			url = StringUtils.isEmpty(jiraIssue.getUrl()) ? Constant.EMPTY_STRING : jiraIssue.getUrl();
		}
		if (object instanceof KanbanJiraIssue) {
			KanbanJiraIssue jiraIssue = (KanbanJiraIssue) object;
			url = StringUtils.isEmpty(jiraIssue.getUrl()) ? Constant.EMPTY_STRING : jiraIssue.getUrl();
		}
		if (object instanceof KanbanIssueCustomHistory) {
			KanbanIssueCustomHistory jiraIssue = (KanbanIssueCustomHistory) object;
			url = StringUtils.isEmpty(jiraIssue.getUrl()) ? Constant.EMPTY_STRING : jiraIssue.getUrl();
		}
		if (object instanceof JiraIssueCustomHistory) {
			JiraIssueCustomHistory jiraIssue = (JiraIssueCustomHistory) object;
			url = StringUtils.isEmpty(jiraIssue.getUrl()) ? Constant.EMPTY_STRING : jiraIssue.getUrl();
		}
		if (object instanceof ResolutionTimeValidation) {
			ResolutionTimeValidation resolutionTimeValidation = (ResolutionTimeValidation) object;
			url = StringUtils.isEmpty(resolutionTimeValidation.getUrl())
					? Constant.EMPTY_STRING
					: resolutionTimeValidation.getUrl();
		}
		if (object instanceof IssueDetails) {
			IssueDetails issueDetails = (IssueDetails) object;
			url = StringUtils.isEmpty(issueDetails.getUrl()) ? Constant.EMPTY_STRING : issueDetails.getUrl();
		}
		if (object instanceof LeadTimeChangeData) {
			LeadTimeChangeData leadTimeChangeData = (LeadTimeChangeData) object;
			url = StringUtils.isEmpty(leadTimeChangeData.getUrl()) ? Constant.EMPTY_STRING : leadTimeChangeData.getUrl();
		}
		return url;
	}

	public static void populateChangeFailureRateExcelData(String projectName, ChangeFailureRateInfo changeFailureRateInfo,
			List<KPIExcelData> kpiExcelData) {
		List<String> buildJobNameList = changeFailureRateInfo.getBuildJobNameList();
		if (CollectionUtils.isNotEmpty(buildJobNameList)) {
			for (int i = 0; i < changeFailureRateInfo.getBuildJobNameList().size(); i++) {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProject(projectName);
				excelData.setJobName(buildJobNameList.get(i));
				excelData.setWeeks(changeFailureRateInfo.getDateList().get(i));
				excelData.setBuildCount(changeFailureRateInfo.getTotalBuildCountList().get(i).toString());
				excelData.setBuildFailureCount(changeFailureRateInfo.getTotalBuildFailureCountList().get(i).toString());
				excelData.setBuildFailurePercentage(changeFailureRateInfo.getBuildFailurePercentageList().get(i).toString());
				kpiExcelData.add(excelData);
			}
		}
	}

	public static void populateTestExcecutionExcelData(String sprintProjectName, TestExecution testDetail,
			KanbanTestExecution kanbanTestExecution, double executionPercentage, double passPercentage,
			List<KPIExcelData> kpiExcelData) {

		if (testDetail != null) {
			KPIExcelData excelData = new KPIExcelData();
			excelData.setSprintName(sprintProjectName);
			excelData.setTotalTest(testDetail.getTotalTestCases().toString());
			excelData.setExecutedTest(testDetail.getExecutedTestCase().toString());
			excelData.setExecutionPercentage(String.valueOf(executionPercentage));
			excelData.setPassedTest(testDetail.getPassedTestCase().toString());
			excelData.setPassedPercentage(String.valueOf(passPercentage));
			kpiExcelData.add(excelData);
		}
		if (kanbanTestExecution != null) {
			KPIExcelData excelData = new KPIExcelData();
			excelData.setProject(sprintProjectName);
			excelData.setTotalTest(kanbanTestExecution.getTotalTestCases().toString());
			excelData.setExecutedTest(kanbanTestExecution.getExecutedTestCase().toString());
			excelData.setExecutionPercentage(String.valueOf(executionPercentage));
			excelData.setPassedTest(kanbanTestExecution.getPassedTestCase().toString());
			excelData.setPassedPercentage(String.valueOf(passPercentage));
			excelData.setExecutionDate(kanbanTestExecution.getExecutionDate());
			kpiExcelData.add(excelData);
		}
	}

	public static void populateSprintVelocity(String sprint, Map<String, JiraIssue> totalStoriesMap,
			List<KPIExcelData> kpiExcelData, FieldMapping fieldMapping, CustomApiConfig customApiConfig) {

		if (MapUtils.isNotEmpty(totalStoriesMap)) {
			totalStoriesMap.forEach((storyId, jiraIssue) -> {
				KPIExcelData excelData = new KPIExcelData();
				setSpeedKPIExcelData(sprint, jiraIssue, fieldMapping, excelData, customApiConfig);
				setEstimateAndOrgTimeSpent(jiraIssue, excelData);
				kpiExcelData.add(excelData);
			});
		}
	}

	private static void setEstimateAndOrgTimeSpent(JiraIssue jiraIssue, KPIExcelData excelData) {
		excelData.setOriginalTimeEstimate(jiraIssue.getAggregateTimeOriginalEstimateMinutes() != null
				? String.valueOf((jiraIssue.getAggregateTimeOriginalEstimateMinutes() / 60))
				: "0");
		excelData.setTotalTimeSpent(
				jiraIssue.getTimeSpentInMinutes() != null ? String.valueOf((jiraIssue.getTimeSpentInMinutes() / 60)) : "0");
	}

	public static void populateSprintPredictability(String sprint, Set<IssueDetails> issueDetailsSet,
			List<KPIExcelData> kpiExcelData, FieldMapping fieldMapping, Map<String, JiraIssue> jiraIssueMap,
			CustomApiConfig customApiConfig) {
		if (CollectionUtils.isNotEmpty(issueDetailsSet)) {
			for (IssueDetails issueDetails : issueDetailsSet) {
				JiraIssue jiraIssue = jiraIssueMap.get(issueDetails.getSprintIssue().getNumber());
				if (jiraIssue != null) {
					KPIExcelData excelData = new KPIExcelData();
					setSpeedKPIExcelData(sprint, jiraIssue, fieldMapping, excelData, customApiConfig);
					setEstimateAndOrgTimeSpent(jiraIssue, excelData);
					kpiExcelData.add(excelData);
				}
			}
		}
	}

	public static void populateSprintCapacity(String sprintName, List<JiraIssue> totalStoriesList,
			List<KPIExcelData> kpiExcelData, Map<String, Double> loggedTimeIssueMap, FieldMapping fieldMapping,
			CustomApiConfig customApiConfig) {
		if (CollectionUtils.isNotEmpty(totalStoriesList)) {
			totalStoriesList.forEach(issue -> {
				KPIExcelData excelData = new KPIExcelData();
				setSpeedKPIExcelData(sprintName, issue, fieldMapping, excelData, customApiConfig);
				String daysEstimated = "0.0";
				excelData
						.setTotalTimeSpent(String.valueOf(roundingOff(loggedTimeIssueMap.getOrDefault(issue.getNumber(), 0d))));

				if (issue.getAggregateTimeOriginalEstimateMinutes() != null &&
						issue.getAggregateTimeOriginalEstimateMinutes() > 0) {
					daysEstimated = String
							.valueOf(roundingOff(Double.valueOf(issue.getAggregateTimeOriginalEstimateMinutes()) / 60));
				}
				excelData.setOriginalTimeEstimate(daysEstimated);
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateAverageResolutionTime(String sprintName,
			List<ResolutionTimeValidation> sprintWiseResolution, List<KPIExcelData> kpiExcelData) {

		if (CollectionUtils.isNotEmpty(sprintWiseResolution)) {
			sprintWiseResolution.stream().forEach(resolutionTimeValidation -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setSprintName(sprintName);
				Map<String, String> storyDetails = new HashMap<>();
				storyDetails.put(resolutionTimeValidation.getIssueNumber(), checkEmptyURL(resolutionTimeValidation));
				excelData.setStoryId(storyDetails);
				excelData.setIssueDesc(checkEmptyName(resolutionTimeValidation));
				excelData.setIssueType(resolutionTimeValidation.getIssueType());
				excelData.setResolutionTime(resolutionTimeValidation.getResolutionTime().toString());
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateSprintCountExcelData(String sprint, Map<String, JiraIssue> totalStoriesMap,
			List<KPIExcelData> kpiExcelData) {

		if (MapUtils.isNotEmpty(totalStoriesMap)) {
			totalStoriesMap.forEach((storyId, jiraIssue) -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setSprintName(sprint);
				Map<String, String> storyDetails = new HashMap<>();
				storyDetails.put(storyId, checkEmptyURL(jiraIssue));
				excelData.setStoryId(storyDetails);
				excelData.setIssueDesc(checkEmptyName(jiraIssue));

				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateLeadTime(List<CycleTimeValidationData> cycleTimeList, List<KPIExcelData> excelDataList) {
		for (CycleTimeValidationData leadTimeData : cycleTimeList) {
			KPIExcelData excelData = new KPIExcelData();
			Map<String, String> storyId = new HashMap<>();
			storyId.put(leadTimeData.getIssueNumber(), leadTimeData.getUrl());
			excelData.setIssueID(storyId);
			excelData.setIssueDesc(leadTimeData.getIssueDesc());
			excelData.setIssueType(leadTimeData.getIssueType());
			excelData.setCreatedDate(DateUtil.dateTimeConverter(leadTimeData.getIntakeDate().toString().split("T")[0],
					DateUtil.DATE_FORMAT, DateUtil.DISPLAY_DATE_FORMAT));
			excelData.setLiveDate(DateUtil.dateTimeConverter(leadTimeData.getLiveDate().toString().split("T")[0],
					DateUtil.DATE_FORMAT, DateUtil.DISPLAY_DATE_FORMAT));
			excelData.setLeadTime(CommonUtils.convertIntoDays(Math.toIntExact(leadTimeData.getLeadTime())));
			excelDataList.add(excelData);
		}
	}

	/**
	 * @param cycleTimeList
	 *          cycleTimeList
	 * @param excelDataList
	 *          excelDataList
	 */
	public static void populateCycleTime(List<CycleTimeValidationData> cycleTimeList, List<KPIExcelData> excelDataList) {
		for (CycleTimeValidationData leadTimeData : cycleTimeList) {
			KPIExcelData excelData = new KPIExcelData();
			Map<String, String> storyId = new HashMap<>();
			storyId.put(leadTimeData.getIssueNumber(), leadTimeData.getUrl());
			excelData.setIssueID(storyId);
			excelData.setIssueDesc(leadTimeData.getIssueDesc());
			excelData.setIssueType(leadTimeData.getIssueType());
			if (ObjectUtils.isNotEmpty(leadTimeData.getIntakeTime()))
				excelData.setIntakeToDOR(CommonUtils.convertIntoDays(Math.toIntExact(leadTimeData.getIntakeTime())));
			if (ObjectUtils.isNotEmpty(leadTimeData.getDorTime()))
				excelData.setDorToDod(CommonUtils.convertIntoDays(Math.toIntExact(leadTimeData.getDorTime())));
			if (ObjectUtils.isNotEmpty(leadTimeData.getDodTime()))
				excelData.setDodToLive(CommonUtils.convertIntoDays(Math.toIntExact(leadTimeData.getDodTime())));
			excelDataList.add(excelData);
		}
	}

	/**
	 * TO GET Constant.EXCEL_YES/"N" from complete list of defects if defect is
	 * present in conditional list then Constant.EXCEL_YES else
	 * Constant.EMPTY_STRING kpi specific
	 *
	 * @param sprint
	 * @param totalStoriesMap
	 * @param commitmentReliabilityValidationData
	 * @param kpiExcelData
	 */
	public static void populateCommittmentReliability(String sprint, Map<String, JiraIssue> totalStoriesMap,
			CommittmentReliabilityServiceImpl.CommitmentReliabilityValidationData commitmentReliabilityValidationData,
			List<KPIExcelData> kpiExcelData, FieldMapping fieldMapping, CustomApiConfig customApiConfig) {
		if (MapUtils.isNotEmpty(totalStoriesMap)) {
			Set<String> initialIssueNumber = commitmentReliabilityValidationData.getInitialIssueNumber().stream()
					.map(JiraIssue::getNumber).collect(Collectors.toSet());
			Set<String> addedIssues = commitmentReliabilityValidationData.getAddedIssues();
			Set<String> puntedIssues = commitmentReliabilityValidationData.getPuntedIssues();
			totalStoriesMap.forEach((storyId, jiraIssue) -> {
				KPIExcelData excelData = new KPIExcelData();
				setSpeedKPIExcelData(sprint, jiraIssue, fieldMapping, excelData, customApiConfig);
				setEstimateAndOrgTimeSpent(jiraIssue, excelData);
				if (initialIssueNumber.contains(storyId)) {
					excelData.setScopeValue(CommonConstant.INITIAL);
				}
				if (addedIssues.contains(storyId)) {
					excelData.setScopeValue(CommonConstant.ADDED);
				}
				// Removed Issue is implicit showing Initial is there in sprint.
				if (puntedIssues.contains(storyId)) {
					excelData.setScopeValue(CommonConstant.REMOVED);
				}

				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateCODExcelData(String projectName, List<JiraIssue> epicList,
			List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(epicList)) {
			epicList.forEach(epic -> {
				if (null != epic) {
					Map<String, String> epicLink = new HashMap<>();
					epicLink.put(epic.getNumber(), checkEmptyURL(epic));
					KPIExcelData excelData = new KPIExcelData();
					excelData.setProjectName(projectName);
					excelData.setEpicID(epicLink);
					excelData.setEpicName(checkEmptyName(epic));
					excelData.setCostOfDelay(epic.getCostOfDelay());
					setSquads(excelData, epic);
					String month = Constant.EMPTY_STRING;
					String epicEndDate = Constant.EMPTY_STRING;
					String dateToUse = epic.getEpicEndDate();
					if (dateToUse != null) {
						DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern(DateUtil.DATE_FORMAT)
								.toFormatter();
						LocalDate dateValue = LocalDate.parse(dateToUse.split("T")[0], formatter);
						month = dateValue.format(DateTimeFormatter.ofPattern(MONTH_YEAR_FORMAT));
						epicEndDate = dateValue.format(DateTimeFormatter.ofPattern(DateUtil.DISPLAY_DATE_FORMAT));
					}
					excelData.setMonth(month);
					excelData.setEpicEndDate(epicEndDate);
					kpiExcelData.add(excelData);
				}
			});
		}
	}

	public static void populatePIPredictabilityExcelData(String projectName, List<JiraIssue> epicList,
			List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(epicList)) {
			epicList.forEach(epic -> {
				if (null != epic) {
					Map<String, String> epicLink = new HashMap<>();
					epicLink.put(epic.getNumber(), checkEmptyURL(epic));
					KPIExcelData excelData = new KPIExcelData();
					excelData.setProjectName(projectName);
					excelData.setEpicID(epicLink);
					excelData.setEpicName(checkEmptyName(epic));
					excelData.setStatus(epic.getStatus());
					excelData.setPiName(epic.getReleaseVersions().get(0).getReleaseName());
					excelData.setPlannedValue(String.valueOf(epic.getEpicPlannedValue()));
					excelData.setAchievedValue(String.valueOf(epic.getEpicAchievedValue()));
					setSquads(excelData, epic);
					kpiExcelData.add(excelData);
				}
			});
		}
	}

	public static void populateKanbanCODExcelData(String projectName, List<KanbanJiraIssue> epicList,
			List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(epicList)) {
			epicList.forEach(epic -> {
				if (!epic.getProjectName().isEmpty()) {
					Map<String, String> epicLink = new HashMap<>();
					epicLink.put(epic.getNumber(), checkEmptyURL(epic));
					KPIExcelData excelData = new KPIExcelData();
					excelData.setProjectName(projectName);
					excelData.setEpicID(epicLink);
					excelData.setEpicName(checkEmptyName(epic));
					excelData.setCostOfDelay(epic.getCostOfDelay());
					String month = Constant.EMPTY_STRING;
					String epicEndDate = Constant.EMPTY_STRING;
					if (epic.getChangeDate() != null) {
						DateTimeFormatter formatter = new DateTimeFormatterBuilder().appendPattern(DateUtil.TIME_FORMAT)
								.optionalStart().appendPattern(".").appendFraction(ChronoField.MICRO_OF_SECOND, 1, 9, false)
								.optionalEnd().toFormatter();
						LocalDateTime dateTime = LocalDateTime.parse(epic.getChangeDate(), formatter);
						month = dateTime.format(DateTimeFormatter.ofPattern(MONTH_YEAR_FORMAT));
						epicEndDate = dateTime.format(DateTimeFormatter.ofPattern(DATE_YEAR_MONTH_FORMAT));
					}
					excelData.setMonth(month);
					excelData.setEpicEndDate(epicEndDate);
					kpiExcelData.add(excelData);
				}
			});
		}
	}

	public static void populateReleaseFreqExcelData(List<ProjectVersion> projectVersionList, String projectName,
			List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(projectVersionList)) {
			projectVersionList.forEach(pv -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProjectName(projectName);
				excelData.setReleaseName(pv.getName());
				excelData.setReleaseDesc(pv.getDescription());
				excelData.setReleaseEndDate(pv.getReleaseDate().toString(DateUtil.DISPLAY_DATE_FORMAT));
				excelData.setMonth(pv.getReleaseDate().toString(MONTH_YEAR_FORMAT));
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateDeploymentFrequencyExcelData(String projectName,
			DeploymentFrequencyInfo deploymentFrequencyInfo, List<KPIExcelData> kpiExcelData,
			Map<String, String> deploymentMapPipelineNameWise) {
		if (deploymentFrequencyInfo != null) {
			for (int i = 0; i < deploymentFrequencyInfo.getJobNameList().size(); i++) {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProjectName(projectName);
				excelData.setDate(deploymentFrequencyInfo.getDeploymentDateList().get(i));
				if (StringUtils
						.isNotEmpty(deploymentMapPipelineNameWise.get(deploymentFrequencyInfo.getJobNameList().get(i)))) {
					excelData.setJobName(deploymentMapPipelineNameWise.get(deploymentFrequencyInfo.getJobNameList().get(i)));
				} else {
					excelData.setJobName(deploymentFrequencyInfo.getJobNameList().get(i));
				}
				excelData.setWeeks(deploymentFrequencyInfo.getMonthList().get(i));
				excelData.setDeploymentEnvironment(deploymentFrequencyInfo.getEnvironmentList().get(i));
				kpiExcelData.add(excelData);
			}
		}
	}

	public static void populateDefectWithoutIssueLinkExcelData(List<JiraIssue> defectWithoutStory,
			List<KPIExcelData> kpiExcelData, String sprintName) {
		if (CollectionUtils.isNotEmpty(defectWithoutStory)) {
			defectWithoutStory.forEach(defect -> {
				if (null != defect) {
					KPIExcelData excelData = new KPIExcelData();
					Map<String, String> defectLink = new HashMap<>();
					defectLink.put(defect.getNumber(), checkEmptyURL(defect));
					excelData.setProjectName(sprintName);
					excelData.setDefectWithoutStoryLink(defectLink);
					excelData.setIssueDesc(checkEmptyName(defect));
					excelData.setPriority(defect.getPriority());
					kpiExcelData.add(excelData);
				}
			});
		}
	}

	public static void populateTestWithoutStoryExcelData(String projectName, Map<String, TestCaseDetails> totalTestMap,
			List<TestCaseDetails> testWithoutStory, List<KPIExcelData> kpiExcelData) {
		if (MapUtils.isNotEmpty(totalTestMap)) {
			List<String> testWithoutStoryIdList = testWithoutStory.stream().map(TestCaseDetails::getNumber)
					.collect(Collectors.toList());
			totalTestMap.forEach((testId, testCaseDetails) -> {
				String isDefectPresent = testWithoutStoryIdList.contains(testId) ? Constant.EMPTY_STRING : Constant.EXCEL_YES;
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProjectName(projectName);
				excelData.setTestCaseId(testId);
				excelData.setIsTestLinkedToStory(isDefectPresent);
				kpiExcelData.add(excelData);
			});
		}
	}

	/**
	 * Set speed Kpi's excel data
	 *
	 * @param sprint
	 * @param kpiExcelData
	 * @param allJiraIssueList
	 * @param totalPresentJiraIssue
	 * @param fieldMapping
	 */
	public static void populateSpeedKPIExcelData(String sprint, List<KPIExcelData> kpiExcelData,
			List<JiraIssue> allJiraIssueList, List<String> totalPresentJiraIssue, FieldMapping fieldMapping,
			CustomApiConfig customApiConfig) {

		if (CollectionUtils.isNotEmpty(allJiraIssueList)) {
			allJiraIssueList.stream().filter(issue -> totalPresentJiraIssue.contains(issue.getNumber()))
					.forEach(sprintIssue -> {
						KPIExcelData excelData = new KPIExcelData();
						setSpeedKPIExcelData(sprint, sprintIssue, fieldMapping, excelData, customApiConfig);
						setEstimateAndOrgTimeSpent(sprintIssue, excelData);
						kpiExcelData.add(excelData);
					});
		}
	}

	private static void setSpeedKPIExcelData(String sprint, JiraIssue jiraIssue, FieldMapping fieldMapping,
			KPIExcelData excelData, CustomApiConfig customApiConfig) {
		excelData.setSprintName(sprint);
		Map<String, String> storyDetails = new HashMap<>();
		storyDetails.put(jiraIssue.getNumber(), checkEmptyURL(jiraIssue));
		excelData.setIssueID(storyDetails);
		excelData.setIssueDesc(checkEmptyName(jiraIssue));
		excelData.setIssueType(jiraIssue.getTypeName());
		excelData.setPriority(setPriority(customApiConfig, jiraIssue));

		if (StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria()) &&
				fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT)) {
			Double roundingOff = roundingOff(Optional.ofNullable(jiraIssue.getStoryPoints()).orElse(0.0));
			excelData.setStoryPoints(roundingOff.toString());
		} else if (null != jiraIssue.getAggregateTimeOriginalEstimateMinutes()) {
			Double totalOriginalEstimate = Double.valueOf(jiraIssue.getAggregateTimeOriginalEstimateMinutes()) / 60;
			excelData.setStoryPoints(roundingOff(totalOriginalEstimate / fieldMapping.getStoryPointToHourMapping()) + "/" +
					roundingOff(totalOriginalEstimate) + " hrs");
		}

		excelData.setIssueStatus(jiraIssue.getStatus());
		setSquads(excelData, jiraIssue);
	}

	public static void populateCodeBuildTime(List<KPIExcelData> kpiExcelData, String projectName,
			CodeBuildTimeInfo codeBuildTimeInfo) {
		int maxSize = Math.max(codeBuildTimeInfo.getBuildJobList().size(), codeBuildTimeInfo.getPipeLineNameList().size());
		for (int i = 0; i < maxSize; i++) {
			KPIExcelData excelData = new KPIExcelData();
			excelData.setProjectName(projectName);
			if (i < codeBuildTimeInfo.getBuildJobList().size()) {
				excelData.setJobName(codeBuildTimeInfo.getBuildJobList().get(i));
			}
			if (i < codeBuildTimeInfo.getPipeLineNameList().size()) {
				excelData.setJobName(codeBuildTimeInfo.getPipeLineNameList().get(i));
			}
			Map<String, String> buildUrl = new HashMap<>();
			buildUrl.put(codeBuildTimeInfo.getBuildUrlList().get(i), codeBuildTimeInfo.getBuildUrlList().get(i));
			excelData.setBuildUrl(buildUrl);
			excelData.setStartTime(codeBuildTimeInfo.getBuildStartTimeList().get(i));
			excelData.setEndTime(codeBuildTimeInfo.getBuildEndTimeList().get(i));
			excelData.setWeeks(codeBuildTimeInfo.getWeeksList().get(i));
			excelData.setBuildStatus(codeBuildTimeInfo.getBuildStatusList().get(i));
			excelData.setDuration(codeBuildTimeInfo.getDurationList().get(i));
			kpiExcelData.add(excelData);
		}
	}

	/**
	 * populate excel data function for build frequency kpi
	 *
	 * @param kpiExcelData
	 * @param projectName
	 * @param buildFrequencyInfo
	 */
	public static void populateBuildFrequency(List<KPIExcelData> kpiExcelData, String projectName,
			BuildFrequencyInfo buildFrequencyInfo) {

		for (int i = 0; i < buildFrequencyInfo.getBuildJobList().size(); i++) {
			KPIExcelData excelData = new KPIExcelData();
			excelData.setProjectName(projectName);
			excelData.setJobName(buildFrequencyInfo.getBuildJobList().get(i));
			Map<String, String> buildUrl = new HashMap<>();
			buildUrl.put(buildFrequencyInfo.getBuildUrlList().get(i), buildFrequencyInfo.getBuildUrlList().get(i));
			excelData.setBuildUrl(buildUrl);
			excelData.setStartDate(buildFrequencyInfo.getBuildStartTimeList().get(i));
			excelData.setWeeks(buildFrequencyInfo.getWeeksList().get(i));
			kpiExcelData.add(excelData);
		}
	}

	public static void populateMeanTimeMergeExcelData(List<RepoToolValidationData> repoToolValidationDataList,
			List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(repoToolValidationDataList)) {
			repoToolValidationDataList.forEach(repoToolValidationData -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProject(repoToolValidationData.getProjectName());
				excelData.setRepo(repoToolValidationData.getRepoUrl());
				excelData.setBranch(repoToolValidationData.getBranchName());
				excelData.setAuthor(repoToolValidationData.getDeveloperName());
				excelData.setDaysWeeks(repoToolValidationData.getDate());
				excelData.setMeanTimetoMerge(repoToolValidationData.getMeanTimeToMerge().toString());
				excelData.setPrRaisedTime(repoToolValidationData.getPrRaisedTime());
				excelData.setPrMergedTime(repoToolValidationData.getPrActivityTime());
				excelData.setMrComments(repoToolValidationData.getMergeRequestComment());
				Map<String, String> mergeUrl = new HashMap<>();
				mergeUrl.put(repoToolValidationData.getMergeRequestUrl(), repoToolValidationData.getMergeRequestUrl());
				excelData.setMergeRequestUrl(mergeUrl);
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populatePickupTimeExcelData(List<RepoToolValidationData> repoToolValidationDataList,
			List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(repoToolValidationDataList)) {
			repoToolValidationDataList.forEach(repoToolValidationData -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProject(repoToolValidationData.getProjectName());
				excelData.setRepo(repoToolValidationData.getProjectName());
				excelData.setBranch(repoToolValidationData.getBranchName());
				excelData.setAuthor(repoToolValidationData.getDeveloperName());
				excelData.setDaysWeeks(repoToolValidationData.getDate());
				if (repoToolValidationData.getPickupTime() != null) {
					excelData.setPickupTime(String.format("%.2f", repoToolValidationData.getPickupTime()));
					excelData.setPrReviewTime(repoToolValidationData.getPrActivityTime());
				}
				excelData.setPrRaisedTime(repoToolValidationData.getPrRaisedTime());
				excelData.setNumberOfMerge(String.valueOf(repoToolValidationData.getMrCount()));
				Map<String, String> mergeUrl = new HashMap<>();
				mergeUrl.put(repoToolValidationData.getMergeRequestUrl(), repoToolValidationData.getMergeRequestUrl());
				excelData.setMergeRequestUrl(mergeUrl);
				excelData.setPrStatus(repoToolValidationData.getPrStatus());
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populatePRSizeExcelData(List<RepoToolValidationData> repoToolValidationDataList,
			List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(repoToolValidationDataList)) {
			repoToolValidationDataList.forEach(repoToolValidationData -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProject(repoToolValidationData.getProjectName());
				excelData.setRepo(repoToolValidationData.getRepoUrl());
				excelData.setBranch(repoToolValidationData.getBranchName());
				excelData.setAuthor(repoToolValidationData.getDeveloperName());
				excelData.setDaysWeeks(repoToolValidationData.getDate());
				excelData.setPrSize(String.valueOf(repoToolValidationData.getPrSize()));
				excelData.setNumberOfMerge(String.valueOf(repoToolValidationData.getMrCount()));
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateReworkRateExcelData(List<RepoToolValidationData> repoToolValidationDataList,
			List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(repoToolValidationDataList)) {
			repoToolValidationDataList.forEach(repoToolValidationData -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProject(repoToolValidationData.getProjectName());
				excelData.setRepo(repoToolValidationData.getRepoUrl());
				excelData.setBranch(repoToolValidationData.getBranchName());
				excelData.setDeveloper(repoToolValidationData.getDeveloperName());
				excelData.setDaysWeeks(repoToolValidationData.getDate());
				excelData.setReworkRate(String.format("%.2f", repoToolValidationData.getReworkRate()));
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateInnovationRateExcelData(List<RepoToolValidationData> repoToolValidationDataList,
			List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(repoToolValidationDataList)) {
			repoToolValidationDataList.forEach(repoToolValidationData -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProject(repoToolValidationData.getProjectName());
				excelData.setRepo(repoToolValidationData.getRepoUrl());
				excelData.setBranch(repoToolValidationData.getBranchName());
				excelData.setDeveloper(repoToolValidationData.getDeveloperName());
				excelData.setDaysWeeks(repoToolValidationData.getDate());
				excelData.setAddedLines(repoToolValidationData.getAddedLines());
				excelData.setTotalLineChanges(repoToolValidationData.getChangedLines());
				excelData.setInnovationRate(String.format("%.2f", repoToolValidationData.getInnovationRate()));
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateDefectRate(List<RepoToolValidationData> repoToolValidationDataList,
			List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(repoToolValidationDataList)) {
			repoToolValidationDataList.forEach(repoToolValidationData -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProject(repoToolValidationData.getProjectName());
				excelData.setRepo(repoToolValidationData.getRepoUrl());
				excelData.setBranch(repoToolValidationData.getBranchName());
				excelData.setAuthor(repoToolValidationData.getDeveloperName());
				excelData.setDaysWeeks(repoToolValidationData.getDate());
				excelData.setNumberOfMerge(String.valueOf(repoToolValidationData.getMrCount()));
				excelData.setDefectPRs(repoToolValidationData.getKpiPRs());
				excelData.setDefectRate(String.format("%.2f", repoToolValidationData.getDefectRate()));
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateCodeCommit(List<RepoToolValidationData> repoToolValidationDataList,
			List<KPIExcelData> kpiExcelData) {

		if (CollectionUtils.isNotEmpty(repoToolValidationDataList)) {
			repoToolValidationDataList.forEach(repoToolValidationData -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProject(repoToolValidationData.getProjectName());
				excelData.setRepo(repoToolValidationData.getRepoUrl());
				excelData.setBranch(repoToolValidationData.getBranchName());
				excelData.setDeveloper(repoToolValidationData.getDeveloperName());
				excelData.setDaysWeeks(repoToolValidationData.getDate());
				excelData.setNumberOfCommit(String.valueOf(repoToolValidationData.getCommitCount()));
				excelData.setNumberOfMerge(String.valueOf(repoToolValidationData.getMrCount()));
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateRevertRateExcelData(List<RepoToolValidationData> repoToolValidationDataList,
			List<KPIExcelData> kpiExcelData) {

		if (CollectionUtils.isNotEmpty(repoToolValidationDataList)) {
			repoToolValidationDataList.forEach(repoToolValidationData -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProject(repoToolValidationData.getProjectName());
				excelData.setRepo(repoToolValidationData.getRepoUrl());
				excelData.setBranch(repoToolValidationData.getBranchName());
				excelData.setAuthor(repoToolValidationData.getDeveloperName());
				excelData.setDaysWeeks(repoToolValidationData.getDate());
				excelData.setRevertRate(roundingOff(repoToolValidationData.getRevertRate()));
				excelData.setNumberOfMerge(String.valueOf(repoToolValidationData.getMrCount()));
				excelData.setRevertPrs(repoToolValidationData.getKpiPRs());
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populatePRSuccessRateExcelData(List<RepoToolValidationData> repoToolValidationDataList,
			List<KPIExcelData> kpiExcelData) {

		if (CollectionUtils.isNotEmpty(repoToolValidationDataList)) {
			repoToolValidationDataList.forEach(repoToolValidationData -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProject(repoToolValidationData.getProjectName());
				excelData.setRepo(repoToolValidationData.getRepoUrl());
				excelData.setBranch(repoToolValidationData.getBranchName());
				excelData.setAuthor(repoToolValidationData.getDeveloperName());
				excelData.setDaysWeeks(repoToolValidationData.getDate());
				excelData.setPRSccessRate(roundingOff(repoToolValidationData.getPRSuccessRate()));
				excelData.setNumberOfMerge(String.valueOf(repoToolValidationData.getKpiPRs()));
				excelData.setClosedPRs(repoToolValidationData.getMrCount());
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populatePRDeclineRateExcelData(List<RepoToolValidationData> repoToolValidationDataList,
			List<KPIExcelData> kpiExcelData) {

		if (CollectionUtils.isNotEmpty(repoToolValidationDataList)) {
			repoToolValidationDataList.forEach(repoToolValidationData -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProject(repoToolValidationData.getProjectName());
				excelData.setRepo(repoToolValidationData.getRepoUrl());
				excelData.setBranch(repoToolValidationData.getBranchName());
				excelData.setAuthor(repoToolValidationData.getDeveloperName());
				excelData.setDaysWeeks(repoToolValidationData.getDate());
				excelData.setPrDeclineRate(roundingOff(repoToolValidationData.getPrDeclineRate()));
				excelData.setDeclinedPRs(repoToolValidationData.getKpiPRs());
				excelData.setClosedPRs(repoToolValidationData.getMrCount());
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateKanbanLeadTime(List<KPIExcelData> kpiExcelData, String projectName,
			LeadTimeData leadTimeDataKanban) {

		if (!leadTimeDataKanban.getIssueNumber().isEmpty()) {
			for (int i = 0; i < leadTimeDataKanban.getIssueNumber().size(); i++) {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProjectName(projectName);
				Map<String, String> storyId = new HashMap<>();
				storyId.put(leadTimeDataKanban.getIssueNumber().get(i), leadTimeDataKanban.getUrlList().get(i));
				excelData.setStoryId(storyId);
				excelData.setIssueDesc(leadTimeDataKanban.getIssueDiscList().get(i));
				excelData.setOpenToTriage(leadTimeDataKanban.getOpenToTriage().get(i));
				excelData.setTriageToComplete(leadTimeDataKanban.getTriageToComplete().get(i));
				excelData.setCompleteToLive(leadTimeDataKanban.getCompleteToLive().get(i));
				excelData.setLeadTime(leadTimeDataKanban.getLeadTime().get(i));

				kpiExcelData.add(excelData);
			}
		}
	}

	public static void populateProductionDefectAgingExcelData(String projectName, List<JiraIssue> defectList,
			List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(defectList)) {
			defectList.forEach(defect -> {
				KPIExcelData excelData = new KPIExcelData();
				Map<String, String> defectLink = new HashMap<>();
				defectLink.put(defect.getNumber(), checkEmptyURL(defect));
				excelData.setProjectName(projectName);
				excelData.setDefectId(defectLink);
				excelData.setPriority(defect.getPriority());
				String date = Constant.EMPTY_STRING;
				if (defect.getCreatedDate() != null) {
					date = DateUtil.dateTimeConverter(defect.getCreatedDate(), DateUtil.DATE_FORMAT,
							DateUtil.DISPLAY_DATE_FORMAT);
				}
				excelData.setCreatedDate(date);
				excelData.setIssueDesc(checkEmptyName(defect));
				excelData.setStatus(defect.getJiraStatus());
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateOpenTicketByAgeingExcelData(String projectName, List<KanbanJiraIssue> kanbanJiraIssues,
			List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(kanbanJiraIssues)) {
			kanbanJiraIssues.forEach(kanbanIssues -> {
				KPIExcelData excelData = new KPIExcelData();
				Map<String, String> storyMap = new HashMap<>();
				storyMap.put(kanbanIssues.getNumber(), checkEmptyURL(kanbanIssues));
				excelData.setProject(projectName);
				excelData.setTicketIssue(storyMap);
				excelData.setPriority(kanbanIssues.getPriority());
				excelData.setCreatedDate(DateUtil.dateTimeConverter(kanbanIssues.getCreatedDate(), DateUtil.TIME_FORMAT,
						DateUtil.DISPLAY_DATE_FORMAT));
				excelData.setIssueStatus(kanbanIssues.getJiraStatus());
				kpiExcelData.add(excelData);
			});
		}
	}

	/**
	 * prepare data for excel for cumulative kpi of Kanban on the basis of field.
	 * field can be RCA/priority/status field values as per field of jira
	 *
	 * @param projectName
	 * @param jiraHistoryFieldAndDateWiseIssueMap
	 * @param fieldValues
	 * @param kanbanJiraIssues
	 * @param excelDataList
	 * @param kpiId
	 */
	public static void prepareExcelForKanbanCumulativeDataMap(String projectName,
			Map<String, Map<String, Set<String>>> jiraHistoryFieldAndDateWiseIssueMap, Set<String> fieldValues,
			Set<KanbanIssueCustomHistory> kanbanJiraIssues, List<KPIExcelData> excelDataList, String date, String kpiId) {

		Map<String, Set<String>> fieldWiseIssuesLatestMap = filterKanbanDataBasedOnFieldLatestCumulativeData(
				jiraHistoryFieldAndDateWiseIssueMap, fieldValues);

		Map<String, Set<String>> fieldWiseIssues = fieldWiseIssuesLatestMap.entrySet().stream()
				.sorted((i1, i2) -> i1.getKey().compareTo(i2.getKey()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

		fieldWiseIssues.entrySet().forEach(dateSet -> {
			String field = dateSet.getKey();
			dateSet.getValue().stream().forEach(values -> {
				KanbanIssueCustomHistory kanbanJiraIssue = kanbanJiraIssues.stream()
						.filter(issue -> issue.getStoryID().equalsIgnoreCase(values)).findFirst().get();
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProject(projectName);
				Map<String, String> ticketMap = new HashMap<>();
				ticketMap.put(kanbanJiraIssue.getStoryID(), checkEmptyURL(kanbanJiraIssue));
				excelData.setTicketIssue(ticketMap);
				if (kpiId.equalsIgnoreCase(KPICode.NET_OPEN_TICKET_COUNT_BY_STATUS.getKpiId())) {
					excelData.setIssueStatus(field);
				}
				if (kpiId.equalsIgnoreCase(KPICode.NET_OPEN_TICKET_COUNT_BY_RCA.getKpiId())) {
					excelData.setRootCause(Arrays.asList(field));
				}
				if (kpiId.equalsIgnoreCase(KPICode.TICKET_COUNT_BY_PRIORITY.getKpiId())) {
					excelData.setPriority(field);
				}
				excelData.setCreatedDate(DateUtil.dateTimeConverter(kanbanJiraIssue.getCreatedDate(), DateUtil.TIME_FORMAT,
						DateUtil.DISPLAY_DATE_FORMAT));
				excelData.setDayWeekMonth(date);
				excelDataList.add(excelData);
			});
		});
	}

	/**
	 * prepare excel data only Today Cumulative data so that only latest data values
	 * of field(status/rca/priority)
	 *
	 * @param jiraHistoryFieldAndDateWiseIssueMap
	 * @param fieldValues
	 * @return
	 */
	private static Map<String, Set<String>> filterKanbanDataBasedOnFieldLatestCumulativeData(
			Map<String, Map<String, Set<String>>> jiraHistoryFieldAndDateWiseIssueMap, Set<String> fieldValues) {
		String date = LocalDate.now().toString();
		Map<String, Set<String>> fieldWiseIssuesLatestMap = new HashMap<>();
		fieldValues.forEach(field -> {
			Set<String> ids = jiraHistoryFieldAndDateWiseIssueMap.get(field).getOrDefault(date, new HashSet<>()).stream()
					.filter(Objects::nonNull).collect(Collectors.toSet());
			fieldWiseIssuesLatestMap.put(field, ids);
		});
		return fieldWiseIssuesLatestMap;
	}

	public static void populateOpenVsClosedExcelData(String date, String projectName,
			List<KanbanJiraIssue> dateWiseIssueTypeList, List<KanbanIssueCustomHistory> dateWiseIssueClosedStatusList,
			List<KPIExcelData> excelDataList, String kpiId) {
		if (CollectionUtils.isNotEmpty(dateWiseIssueTypeList) ||
				CollectionUtils.isNotEmpty(dateWiseIssueClosedStatusList)) {
			dateWiseIssueTypeList.forEach(issue -> {
				KPIExcelData kpiExcelDataObject = new KPIExcelData();
				kpiExcelDataObject.setProject(projectName);
				kpiExcelDataObject.setDayWeekMonth(date);
				Map<String, String> storyDetails = new HashMap<>();
				storyDetails.put(issue.getNumber(), checkEmptyURL(issue));
				kpiExcelDataObject.setTicketIssue(storyDetails);
				if (kpiId.equalsIgnoreCase(KPICode.TICKET_OPEN_VS_CLOSED_RATE_BY_TYPE.getKpiId())) {
					kpiExcelDataObject.setIssueType(issue.getTypeName());
				} //
				if (kpiId.equalsIgnoreCase(KPICode.TICKET_OPEN_VS_CLOSE_BY_PRIORITY.getKpiId())) {
					kpiExcelDataObject.setIssuePriority(issue.getPriority());
				}
				kpiExcelDataObject.setStatus("Open");
				excelDataList.add(kpiExcelDataObject);
			});

			dateWiseIssueClosedStatusList.forEach(issue -> {
				KPIExcelData kpiExcelDataObject = new KPIExcelData();
				kpiExcelDataObject.setProject(projectName);
				kpiExcelDataObject.setDayWeekMonth(date);
				Map<String, String> storyDetails = new HashMap<>();
				storyDetails.put(issue.getStoryID(), checkEmptyURL(issue));
				kpiExcelDataObject.setTicketIssue(storyDetails);
				if (kpiId.equalsIgnoreCase(KPICode.TICKET_OPEN_VS_CLOSED_RATE_BY_TYPE.getKpiId())) {
					kpiExcelDataObject.setIssueType(issue.getStoryType());
				}
				if (kpiId.equalsIgnoreCase(KPICode.TICKET_OPEN_VS_CLOSE_BY_PRIORITY.getKpiId())) {
					kpiExcelDataObject.setIssuePriority(issue.getPriority());
				}
				kpiExcelDataObject.setStatus("Closed");
				excelDataList.add(kpiExcelDataObject);
			});
		}
	}

	public static void populateTicketVelocityExcelData(List<KanbanIssueCustomHistory> velocityList, String projectName,
			String date, List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(velocityList)) {
			velocityList.forEach(kanbanIssueCustomHistory -> {
				if (kanbanIssueCustomHistory.getStoryID() != null) {
					KPIExcelData excelData = new KPIExcelData();
					excelData.setProjectName(projectName);
					excelData.setDayWeekMonth(date);
					excelData.setIssueType(kanbanIssueCustomHistory.getStoryType());
					excelData.setSizeInStoryPoints(kanbanIssueCustomHistory.getEstimate());
					Map<String, String> storyId = new HashMap<>();
					storyId.put(kanbanIssueCustomHistory.getStoryID(), checkEmptyURL(kanbanIssueCustomHistory));
					excelData.setTicketIssue(storyId);
					kpiExcelData.add(excelData);
				}
			});
		}
	}

	public static void populateCodeBuildTimeExcelData(CodeBuildTimeInfo codeBuildTimeInfo, String projectName,
			List<KPIExcelData> kpiExcelData) {
		if (codeBuildTimeInfo != null)
			for (int i = 0; i < codeBuildTimeInfo.getBuildJobList().size(); i++) {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProjectName(projectName);
				excelData.setJobName(codeBuildTimeInfo.getBuildJobList().get(i));
				excelData.setStartTime(codeBuildTimeInfo.getBuildStartTimeList().get(i));
				excelData.setEndTime(codeBuildTimeInfo.getBuildEndTimeList().get(i));
				excelData.setDuration(codeBuildTimeInfo.getDurationList().get(i));
				Map<String, String> codeBuildUrl = new HashMap<>();
				codeBuildUrl.put(codeBuildTimeInfo.getBuildUrlList().get(i), codeBuildTimeInfo.getBuildUrlList().get(i));
				excelData.setBuildUrl(codeBuildUrl);
				excelData.setBuildStatus(codeBuildTimeInfo.getBuildStatusList().get(i));
				kpiExcelData.add(excelData);
			}
	}

	public static void populateCodeCommitKanbanExcelData(List<RepoToolValidationData> repoToolValidationDataList,
			List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(repoToolValidationDataList)) {
			repoToolValidationDataList.forEach(repoToolValidationData -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setProjectName(repoToolValidationData.getProjectName());
				excelData.setRepo(repoToolValidationData.getRepoUrl());
				excelData.setBranch(repoToolValidationData.getBranchName());
				excelData.setDeveloper(repoToolValidationData.getDeveloperName());
				excelData.setDaysWeeks(repoToolValidationData.getDate());
				excelData.setNumberOfCommit(String.valueOf(repoToolValidationData.getCommitCount()));
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateTeamCapacityKanbanExcelData(Double capacity, List<KPIExcelData> kpiExcelData,
			String projectName, CustomDateRange dateRange, String duration) {

		KPIExcelData excelData = new KPIExcelData();
		excelData.setProjectName(projectName);
		excelData.setStartDate(DateUtil.localDateTimeConverter(dateRange.getStartDate()));
		if (CommonConstant.DAYS.equalsIgnoreCase(duration)) {
			excelData.setEndDate(DateUtil.localDateTimeConverter(dateRange.getStartDate()));
		} else {
			excelData.setEndDate(DateUtil.localDateTimeConverter(dateRange.getEndDate()));
		}
		excelData.setEstimatedCapacity(df2.format(capacity));
		kpiExcelData.add(excelData);
	}

	/**
	 * Method to populate assignee name in kpi's
	 *
	 * @param jiraIssue
	 * @param object
	 */
	public static void populateAssignee(JiraIssue jiraIssue, Object object) {
		String assigneeName = jiraIssue.getAssigneeName() != null ? jiraIssue.getAssigneeName() : Constant.BLANK;
		if (object instanceof IterationKpiModalValue) {
			((IterationKpiModalValue) object).setAssignee(assigneeName);
		} else if (object instanceof KPIExcelData) {
			((KPIExcelData) object).setAssignee(assigneeName);

		} else if (object instanceof IssueKpiModalValue) {
			((IssueKpiModalValue) object).setAssignee(assigneeName);
		}
	}

	/**
	 * Common method to populate modal window of Iteration KPI's
	 *
	 * @param overAllModalValues
	 * @param modalValues
	 * @param jiraIssue
	 * @param fieldMapping
	 * @param modalObjectMap
	 */
	public static void populateIterationKPI(List<IterationKpiModalValue> overAllModalValues,
			List<IterationKpiModalValue> modalValues, JiraIssue jiraIssue, FieldMapping fieldMapping,
			Map<String, IterationKpiModalValue> modalObjectMap) {
		IterationKpiModalValue jiraIssueModalObject = modalObjectMap.get(jiraIssue.getNumber());
		jiraIssueModalObject.setIssueId(jiraIssue.getNumber());
		jiraIssueModalObject.setIssueURL(jiraIssue.getUrl());
		jiraIssueModalObject.setDescription(jiraIssue.getName());
		jiraIssueModalObject.setIssueStatus(jiraIssue.getStatus());
		jiraIssueModalObject.setIssueType(jiraIssue.getTypeName());
		jiraIssueModalObject.setPriority(jiraIssue.getPriority());
		KPIExcelUtility.populateAssignee(jiraIssue, jiraIssueModalObject);
		setIssueSizeForIssueKpiModal(jiraIssue, fieldMapping, jiraIssueModalObject);
		jiraIssueModalObject.setDueDate((StringUtils.isNotEmpty(jiraIssue.getDueDate()))
				? DateUtil.dateTimeConverter(jiraIssue.getDueDate(), DateUtil.TIME_FORMAT_WITH_SEC,
						DateUtil.DISPLAY_DATE_FORMAT)
				: Constant.BLANK);
		jiraIssueModalObject.setChangeDate(
				(StringUtils.isNotEmpty(jiraIssue.getChangeDate())) ? jiraIssue.getChangeDate().split("T")[0] : Constant.BLANK);
		jiraIssueModalObject.setCreatedDate((StringUtils.isNotEmpty(jiraIssue.getCreatedDate()))
				? jiraIssue.getCreatedDate().split("T")[0]
				: Constant.BLANK);
		jiraIssueModalObject.setUpdatedDate(
				(StringUtils.isNotEmpty(jiraIssue.getUpdateDate())) ? jiraIssue.getUpdateDate().split("T")[0] : Constant.BLANK);
		jiraIssueModalObject.setLabels(jiraIssue.getLabels());
		jiraIssueModalObject.setRootCauseList(jiraIssue.getRootCauseList());
		jiraIssueModalObject.setOwnersFullName(jiraIssue.getOwnersFullName());
		jiraIssueModalObject
				.setSprintName(StringUtils.isNotEmpty(jiraIssue.getSprintName()) ? jiraIssue.getSprintName() : Constant.BLANK);
		jiraIssueModalObject.setResolution(jiraIssue.getResolution());
		if (CollectionUtils.isNotEmpty(jiraIssue.getReleaseVersions())) {
			List<ReleaseVersion> releaseVersions = jiraIssue.getReleaseVersions();
			jiraIssueModalObject.setReleaseName(releaseVersions.get(releaseVersions.size() - 1).getReleaseName());
		} else
			jiraIssueModalObject.setReleaseName(Constant.BLANK);
		if (jiraIssue.getOriginalEstimateMinutes() != null) {
			jiraIssueModalObject
					.setOriginalEstimateMinutes(CommonUtils.convertIntoDays(jiraIssue.getOriginalEstimateMinutes()));
		} else {
			jiraIssueModalObject.setOriginalEstimateMinutes("0d");
		}
		if (jiraIssue.getRemainingEstimateMinutes() != null) {
			String remEstimate = CommonUtils.convertIntoDays(jiraIssue.getRemainingEstimateMinutes());
			jiraIssueModalObject.setRemainingEstimateMinutes(remEstimate);
			jiraIssueModalObject.setRemainingTimeInDays(remEstimate);
		} else {
			jiraIssueModalObject.setRemainingEstimateMinutes(Constant.BLANK);
			jiraIssueModalObject.setRemainingTimeInDays(Constant.BLANK);
		}
		jiraIssueModalObject.setTimeSpentInMinutes(CommonUtils.convertIntoDays(Objects.requireNonNullElse(jiraIssue.getTimeSpentInMinutes(), 0)));
		if (jiraIssue.getDevDueDate() != null)
			jiraIssueModalObject.setDevDueDate(DateUtil.dateTimeConverter(jiraIssue.getDevDueDate(),
					DateUtil.TIME_FORMAT_WITH_SEC, DateUtil.DISPLAY_DATE_FORMAT));
		else
			jiraIssueModalObject.setDevDueDate(Constant.BLANK);

		if (CollectionUtils.isNotEmpty(fieldMapping.getAdditionalFilterConfig())) {
			if (CollectionUtils.isNotEmpty(jiraIssue.getAdditionalFilters())) {
				jiraIssueModalObject.setSquads(jiraIssue.getAdditionalFilters().stream()
						.flatMap(
								additionalFilter -> additionalFilter.getFilterValues().stream().map(AdditionalFilterValue::getValue))
						.toList());

			} else {
				jiraIssueModalObject.setSquads(List.of(Constant.BLANK));
			}
		}

		if (modalValues != null && overAllModalValues != null) {
			modalValues.add(jiraIssueModalObject);
			overAllModalValues.add(jiraIssueModalObject);
		} else {
			modalObjectMap.computeIfPresent(jiraIssue.getNumber(), (k, v) -> jiraIssueModalObject);
		}
	}

	/**
	 * Common method to populate modal window of Iteration KPI's
	 *
	 * @param jiraIssue
	 * @param fieldMapping
	 * @param modalObjectMap
	 */
	public static void populateIssueModal(JiraIssue jiraIssue, FieldMapping fieldMapping,
			Map<String, IssueKpiModalValue> modalObjectMap) {
		IssueKpiModalValue issueKpiModalValue = modalObjectMap.get(jiraIssue.getNumber());
		issueKpiModalValue.setIssueId(jiraIssue.getNumber());
		issueKpiModalValue.setIssueURL(jiraIssue.getUrl());
		issueKpiModalValue.setDescription(jiraIssue.getName());
		issueKpiModalValue.setIssueStatus(jiraIssue.getStatus());
		issueKpiModalValue.setIssueType(jiraIssue.getTypeName());
		issueKpiModalValue.setPriority(jiraIssue.getPriority());
		KPIExcelUtility.populateAssignee(jiraIssue, issueKpiModalValue);
		setIssueSizeForIssueKpiModal(jiraIssue, fieldMapping, issueKpiModalValue);
		issueKpiModalValue.setDueDate(StringUtils.isNotEmpty(jiraIssue.getDueDate())
				? DateUtil.dateTimeConverter(jiraIssue.getDueDate(), DateUtil.TIME_FORMAT_WITH_SEC,
						DateUtil.DISPLAY_DATE_FORMAT)
				: Constant.BLANK);
		issueKpiModalValue.setChangeDate(
				(StringUtils.isNotEmpty(jiraIssue.getChangeDate())) ? jiraIssue.getChangeDate().split("T")[0] : Constant.BLANK);
		issueKpiModalValue.setCreatedDate((StringUtils.isNotEmpty(jiraIssue.getCreatedDate()))
				? jiraIssue.getCreatedDate().split("T")[0]
				: Constant.BLANK);
		issueKpiModalValue.setUpdatedDate(
				(StringUtils.isNotEmpty(jiraIssue.getUpdateDate())) ? jiraIssue.getUpdateDate().split("T")[0] : Constant.BLANK);
		issueKpiModalValue.setLabels(jiraIssue.getLabels());
		issueKpiModalValue.setRootCauseList(jiraIssue.getRootCauseList());
		issueKpiModalValue.setOwnersFullName(jiraIssue.getOwnersFullName());
		issueKpiModalValue
				.setSprintName(StringUtils.isNotEmpty(jiraIssue.getSprintName()) ? jiraIssue.getSprintName() : Constant.BLANK);
		issueKpiModalValue.setResolution(jiraIssue.getResolution());
		if (CollectionUtils.isNotEmpty(jiraIssue.getReleaseVersions())) {
			List<ReleaseVersion> releaseVersions = jiraIssue.getReleaseVersions();
			issueKpiModalValue.setReleaseName(releaseVersions.get(releaseVersions.size() - 1).getReleaseName());
		} else {
			issueKpiModalValue.setReleaseName(Constant.BLANK);
		}
		if (jiraIssue.getOriginalEstimateMinutes() != null) {
			issueKpiModalValue
					.setOriginalEstimateMinutes(CommonUtils.convertIntoDays(jiraIssue.getOriginalEstimateMinutes()));
		} else {
			issueKpiModalValue.setOriginalEstimateMinutes("0d");
		}
		if (jiraIssue.getRemainingEstimateMinutes() != null) {
			String remEstimate = CommonUtils.convertIntoDays(jiraIssue.getRemainingEstimateMinutes());
			issueKpiModalValue.setRemainingEstimateMinutes(remEstimate);
			issueKpiModalValue.setRemainingTimeInDays(remEstimate);
			issueKpiModalValue.setRemainingTime(jiraIssue.getRemainingEstimateMinutes());
		} else {
			issueKpiModalValue.setRemainingEstimateMinutes(Constant.BLANK);
			issueKpiModalValue.setRemainingTime(0);
		}
		issueKpiModalValue.setTimeSpentInMinutes(CommonUtils.convertIntoDays(Objects.requireNonNullElse(jiraIssue.getTimeSpentInMinutes(),0)));
		if (jiraIssue.getDevDueDate() != null)
			issueKpiModalValue.setDevDueDate(DateUtil.dateTimeConverter(jiraIssue.getDevDueDate(),
					DateUtil.TIME_FORMAT_WITH_SEC, DateUtil.DISPLAY_DATE_FORMAT));
		else
			issueKpiModalValue.setDevDueDate(Constant.BLANK);

		if (CollectionUtils.isNotEmpty(fieldMapping.getAdditionalFilterConfig())) {
			if (CollectionUtils.isNotEmpty(jiraIssue.getAdditionalFilters())) {
				issueKpiModalValue.setSquads(jiraIssue.getAdditionalFilters().stream()
						.flatMap(
								additionalFilter -> additionalFilter.getFilterValues().stream().map(AdditionalFilterValue::getValue))
						.toList());

			} else {
				issueKpiModalValue.setSquads(List.of(Constant.BLANK));
			}
		}

		List<String> testingPhase = CollectionUtils.isNotEmpty(jiraIssue.getEscapedDefectGroup())
				? jiraIssue.getEscapedDefectGroup()
				: List.of(UNDEFINED);
		issueKpiModalValue.setTestPhaseList(testingPhase);

		modalObjectMap.computeIfPresent(jiraIssue.getNumber(), (k, v) -> issueKpiModalValue);
	}

	private static void setIssueSizeForIssueKpiModal(JiraIssue jiraIssue, FieldMapping fieldMapping,
			IssueKpiModalValue issueKpiModalValue) {
		issueKpiModalValue.setIssueSize(Constant.BLANK);
		if (null != jiraIssue.getStoryPoints() && StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria()) &&
				fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT)) {
			issueKpiModalValue.setIssueSize(df2.format(jiraIssue.getStoryPoints()));
		}
		if (null != jiraIssue.getOriginalEstimateMinutes() &&
				StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria()) &&
				fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.ACTUAL_ESTIMATION)) {
			Double originalEstimateInHours = Double.valueOf(jiraIssue.getOriginalEstimateMinutes()) / 60;
			issueKpiModalValue.setIssueSize(roundingOff(originalEstimateInHours / fieldMapping.getStoryPointToHourMapping()) +
					"/" + roundingOff(originalEstimateInHours) + " hrs");
		}
	}

	/**
	 * This Method is used to populate Excel Data for Rejection Refinement KPI
	 *
	 * @param excelDataList
	 * @param issuesExcel
	 * @param weekAndTypeMap
	 * @param jiraDateMap
	 */
	public static void populateRefinementRejectionExcelData(List<KPIExcelData> excelDataList, List<JiraIssue> issuesExcel,
			Map<String, Map<String, List<JiraIssue>>> weekAndTypeMap, Map<String, LocalDateTime> jiraDateMap) {

		if (CollectionUtils.isNotEmpty(issuesExcel)) {
			issuesExcel.forEach(e -> {
				HashMap<String, String> data = getStatusNameAndWeekName(weekAndTypeMap, e);
				KPIExcelData excelData = new KPIExcelData();
				Map<String, String> epicLink = new HashMap<>();
				epicLink.put(e.getNumber(), checkEmptyURL(e));
				excelData
						.setChangeDate(
								DateUtil.localDateTimeConverter(LocalDate.parse(
										jiraDateMap.entrySet().stream().filter(f -> f.getKey().equalsIgnoreCase(e.getNumber())).findFirst()
												.get().getValue().toString().split("\\.")[0],
										DateTimeFormatter.ofPattern(DateUtil.TIME_FORMAT))));

				excelData.setIssueID(epicLink);
				excelData.setPriority(e.getPriority());
				excelData.setIssueDesc(e.getName());
				excelData.setStatus(e.getStatus());
				excelData.setIssueStatus(data.get(STATUS));
				excelData.setWeeks(data.get(WEEK));
				excelDataList.add(excelData);
			});
		}
	}

	/**
	 * This Method is used for fetching status and Weekname to show the data in
	 * excel data record
	 *
	 * @param weekAndTypeMap
	 * @param e
	 */
	private static HashMap<String, String> getStatusNameAndWeekName(
			Map<String, Map<String, List<JiraIssue>>> weekAndTypeMap, JiraIssue e) {
		HashMap<String, String> data = new HashMap<>();
		for (Map.Entry<String, Map<String, List<JiraIssue>>> weekEntry : weekAndTypeMap.entrySet()) {
			for (Map.Entry<String, List<JiraIssue>> typeEntry : weekEntry.getValue().entrySet()) {
				for (JiraIssue issue : typeEntry.getValue()) {
					if (issue.getNumber().equalsIgnoreCase(e.getNumber())) {
						data.put(STATUS, typeEntry.getKey());
						data.put(WEEK, weekEntry.getKey());
					}
				}
			}
		}
		return data;
	}

	public static void populateReleaseDefectRelatedExcelData(List<JiraIssue> jiraIssues, List<KPIExcelData> kpiExcelData,
			FieldMapping fieldMapping) {
		if (CollectionUtils.isNotEmpty(jiraIssues)) {
			jiraIssues.stream().forEach(jiraIssue -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setSprintName(jiraIssue.getSprintName());
				Map<String, String> issueDetails = new HashMap<>();
				issueDetails.put(jiraIssue.getNumber(), checkEmptyURL(jiraIssue));
				excelData.setIssueID(issueDetails);
				excelData.setIssueDesc(checkEmptyName(jiraIssue));
				excelData.setIssueStatus(jiraIssue.getStatus());
				excelData.setIssueType(jiraIssue.getTypeName());
				String testingPhase = CollectionUtils.isNotEmpty(jiraIssue.getEscapedDefectGroup())
						? jiraIssue.getEscapedDefectGroup().stream().findFirst().orElse(UNDEFINED)
						: UNDEFINED;
				excelData.setTestingPhase(testingPhase);
				populateAssignee(jiraIssue, excelData);
				excelData.setRootCause(jiraIssue.getRootCauseList());
				excelData.setPriority(jiraIssue.getPriority());
				if (StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria()) &&
						fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT)) {
					Double roundingOff = roundingOff(Optional.ofNullable(jiraIssue.getStoryPoints()).orElse(0.0));
					excelData.setStoryPoint(roundingOff.toString());
				} else if (null != jiraIssue.getAggregateTimeOriginalEstimateMinutes()) {
					Double totalOriginalEstimate = Double.valueOf(jiraIssue.getAggregateTimeOriginalEstimateMinutes()) / 60;
					excelData.setStoryPoint(roundingOff(totalOriginalEstimate / fieldMapping.getStoryPointToHourMapping()) + "/" +
							roundingOff(totalOriginalEstimate) + " hrs");
				}
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateBacklogCountExcelData(List<JiraIssue> jiraIssues, List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(jiraIssues)) {
			jiraIssues.stream().forEach(jiraIssue -> {
				KPIExcelData excelData = new KPIExcelData();
				Map<String, String> issueDetails = new HashMap<>();
				issueDetails.put(jiraIssue.getNumber(), checkEmptyURL(jiraIssue));
				excelData.setIssueID(issueDetails);
				excelData.setIssueDesc(checkEmptyName(jiraIssue));
				excelData.setIssueStatus(jiraIssue.getStatus());
				excelData.setIssueType(jiraIssue.getTypeName());
				populateAssignee(jiraIssue, excelData);
				excelData.setPriority(jiraIssue.getPriority());
				excelData.setStoryPoints(jiraIssue.getStoryPoints().toString());
				String date = Constant.EMPTY_STRING;
				if (jiraIssue.getCreatedDate() != null) {
					date = DateUtil.dateTimeConverter(jiraIssue.getCreatedDate(), DateUtil.DATE_FORMAT,
							DateUtil.DISPLAY_DATE_FORMAT);
				}
				excelData.setCreatedDate(date);
				String updateDate = Constant.EMPTY_STRING;
				if (jiraIssue.getUpdateDate() != null) {
					updateDate = DateUtil.dateTimeConverter(jiraIssue.getUpdateDate(), DateUtil.DATE_FORMAT,
							DateUtil.DISPLAY_DATE_FORMAT);
				}
				excelData.setUpdatedDate(updateDate);
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateFlowKPI(Map<String, Map<String, Integer>> dateTypeCountMap, List<KPIExcelData> excelData) {
		for (Map.Entry<String, Map<String, Integer>> entry : dateTypeCountMap.entrySet()) {
			String date = entry.getKey();
			Map<String, Integer> typeCountMap = entry.getValue();
			KPIExcelData kpiExcelData = new KPIExcelData();
			if (MapUtils.isNotEmpty(typeCountMap)) {
				kpiExcelData.setDate(DateUtil.dateTimeConverter(date, DateUtil.DATE_FORMAT, DateUtil.DISPLAY_DATE_FORMAT));
				kpiExcelData.setCount(typeCountMap);
				excelData.add(kpiExcelData);
			}
		}
	}

	public static void populateHappinessIndexExcelData(String sprintName, List<KPIExcelData> excelDataList,
			List<HappinessKpiData> happinessKpiSprintDataList) {
		Map<Pair<String, String>, List<Integer>> userRatingsForSprintMap = new HashMap<>();
		if (CollectionUtils.isNotEmpty(happinessKpiSprintDataList)) {
			happinessKpiSprintDataList.forEach(data -> {
				List<UserRatingData> userRatingList = data.getUserRatingList();
				userRatingList.forEach(user -> populateUserMapForHappinessIndexKpi(userRatingsForSprintMap, user));
			});

			userRatingsForSprintMap.forEach((k, v) -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setSprintName(sprintName);
				excelData.setUserName(k.getValue());
				Integer averageUserRatingPerSprint = v.stream().mapToInt(Integer::intValue).sum() / v.size();
				excelData.setSprintRating(averageUserRatingPerSprint);
				excelDataList.add(excelData);
			});
		}
	}

	private static void populateUserMapForHappinessIndexKpi(
			Map<Pair<String, String>, List<Integer>> userRatingsForSprintMap, UserRatingData user) {
		if (Objects.nonNull(user.getRating()) && !user.getRating().equals(0)) {
			Pair<String, String> userIdentifier = Pair.of(user.getUserId(), user.getUserName());
			List<Integer> userRatings = userRatingsForSprintMap.getOrDefault(userIdentifier, new ArrayList<>());
			userRatings.add(user.getRating());
			userRatingsForSprintMap.put(userIdentifier, userRatings);
		}
	}

	public static double roundingOff(double value) {
		return (double) Math.round(value * 100) / 100;
	}

	public static void populateBacklogDefectCountExcelData(List<JiraIssue> jiraIssues, List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(jiraIssues)) {
			jiraIssues.forEach(jiraIssue -> {
				KPIExcelData excelData = new KPIExcelData();
				Map<String, String> issueDetails = new HashMap<>();
				issueDetails.put(jiraIssue.getNumber(), checkEmptyURL(jiraIssue));
				excelData.setIssueID(issueDetails);
				excelData.setIssueDesc(checkEmptyName(jiraIssue));
				excelData.setIssueStatus(jiraIssue.getStatus());
				excelData.setIssueType(jiraIssue.getOriginalType());
				populateAssignee(jiraIssue, excelData);
				excelData.setPriority(jiraIssue.getPriority());
				excelData.setStoryPoints(jiraIssue.getStoryPoints().toString());
				List<String> sprintStatusList = Arrays.asList(CommonConstant.ACTIVE, CommonConstant.FUTURE);
				excelData.setSprintName(StringUtils.isNotEmpty(jiraIssue.getSprintName()) &&
						StringUtils.isNotEmpty(jiraIssue.getSprintAssetState()) &&
						sprintStatusList.contains(jiraIssue.getSprintAssetState()) ? jiraIssue.getSprintName() : Constant.BLANK);
				String date = Constant.EMPTY_STRING;
				if (jiraIssue.getCreatedDate() != null) {
					date = DateUtil.dateTimeConverter(jiraIssue.getCreatedDate(), DateUtil.DATE_FORMAT,
							DateUtil.DISPLAY_DATE_FORMAT);
				}
				excelData.setCreatedDate(date);
				String updateDate = Constant.EMPTY_STRING;
				if (jiraIssue.getUpdateDate() != null) {
					updateDate = DateUtil.dateTimeConverter(jiraIssue.getUpdateDate(), DateUtil.DATE_FORMAT,
							DateUtil.DISPLAY_DATE_FORMAT);
				}
				excelData.setUpdatedDate(updateDate);
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateReleaseDefectWithTestPhasesRelatedExcelData(List<JiraIssue> jiraIssues,
			List<KPIExcelData> kpiExcelData) {
		if (CollectionUtils.isNotEmpty(jiraIssues)) {
			jiraIssues.forEach(jiraIssue -> {
				KPIExcelData excelData = new KPIExcelData();
				Map<String, String> issueDetails = new HashMap<>();
				String testingPhase = CollectionUtils.isNotEmpty(jiraIssue.getEscapedDefectGroup())
						? jiraIssue.getEscapedDefectGroup().stream().findFirst().orElse(UNDEFINED)
						: UNDEFINED;
				issueDetails.put(jiraIssue.getNumber(), checkEmptyURL(jiraIssue));
				excelData.setIssueID(issueDetails);
				excelData.setIssueDesc(checkEmptyName(jiraIssue));
				excelData.setIssueType(jiraIssue.getTypeName());
				excelData.setPriority(jiraIssue.getPriority());
				excelData.setSprintName(jiraIssue.getSprintName());
				populateAssignee(jiraIssue, excelData);
				excelData.setIssueStatus(jiraIssue.getStatus());
				excelData.setTestingPhase(testingPhase);
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateScopeChurn(String sprintName, Map<String, List<JiraIssue>> totalSprintStoryMap,
			Map<String, String> addedIssueDateMap, Map<String, String> removedIssueDateMap, List<KPIExcelData> excelDataList,
			FieldMapping fieldMapping, CustomApiConfig customApiConfig) {
		if (MapUtils.isNotEmpty(totalSprintStoryMap)) {
			totalSprintStoryMap.entrySet().stream().flatMap(entry -> entry.getValue().stream().map(jiraIssue -> {
				KPIExcelData excelData = new KPIExcelData();
				setSpeedKPIExcelData(sprintName, jiraIssue, fieldMapping, excelData, customApiConfig);
				setEstimateAndOrgTimeSpent(jiraIssue, excelData);
				if (entry.getKey().equals(CommonConstant.ADDED)) {
					excelData.setScopeChange(entry.getKey());
					excelData.setScopeChangeDate(addedIssueDateMap.get(jiraIssue.getNumber()));
				} else {
					excelData.setScopeChange(entry.getKey());
					excelData.setScopeChangeDate(removedIssueDateMap.get(jiraIssue.getNumber()));
				}
				return excelData;
			})).forEach(excelDataList::add);
		}
	}

	public static void populateIterationReadinessExcelData(List<JiraIssue> jiraIssues, List<KPIExcelData> kpiExcelData,
			FieldMapping fieldMapping) {
		if (CollectionUtils.isNotEmpty(jiraIssues)) {
			jiraIssues.stream().forEach(jiraIssue -> {
				KPIExcelData excelData = new KPIExcelData();
				excelData.setSprintName(jiraIssue.getSprintName());
				Map<String, String> issueDetails = new HashMap<>();
				issueDetails.put(jiraIssue.getNumber(), checkEmptyURL(jiraIssue));
				excelData.setIssueID(issueDetails);
				excelData.setIssueDesc(checkEmptyName(jiraIssue));
				excelData.setIssueStatus(jiraIssue.getStatus());
				excelData.setIssueType(jiraIssue.getTypeName());
				if (StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria()) &&
						fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT)) {
					Double roundingOff = roundingOff(Optional.ofNullable(jiraIssue.getStoryPoints()).orElse(0.0));
					excelData.setStoryPoint(roundingOff.toString());
				} else if (null != jiraIssue.getAggregateTimeOriginalEstimateMinutes()) {
					Double totalOriginalEstimate = Double.valueOf(jiraIssue.getAggregateTimeOriginalEstimateMinutes()) / 60;
					excelData.setStoryPoint(roundingOff(totalOriginalEstimate / fieldMapping.getStoryPointToHourMapping()) + "/" +
							roundingOff(totalOriginalEstimate) + " hrs");
				}
				String date = Constant.EMPTY_STRING;
				if (!jiraIssue.getSprintBeginDate().isEmpty()) {
					date = DateUtil.dateTimeConverter(jiraIssue.getSprintBeginDate(), ITERATION_DATE_FORMAT,
							DateUtil.DISPLAY_DATE_FORMAT);
				}
				excelData.setSprintStartDate(StringUtils.isNotEmpty(date) ? date : Constant.BLANK);
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateLeadTimeForChangeExcelData(String projectName,
			Map<String, List<LeadTimeChangeData>> leadTimeMapTimeWise, List<KPIExcelData> kpiExcelData,
			String leadTimeConfigRepoTool) {

		if (MapUtils.isNotEmpty(leadTimeMapTimeWise)) {
			leadTimeMapTimeWise.forEach((weekOrMonthName, leadTimeListCurrentTime) -> {
				leadTimeListCurrentTime.stream().forEach(leadTimeChangeData -> {
					KPIExcelData excelData = new KPIExcelData();
					excelData.setProjectName(projectName);
					excelData.setWeeks(weekOrMonthName);
					excelData.setChangeCompletionDate(leadTimeChangeData.getClosedDate());
					if (CommonConstant.REPO.equals(leadTimeConfigRepoTool)) {
						excelData.setMergeRequestId(leadTimeChangeData.getMergeID());
						excelData.setBranch(leadTimeChangeData.getFromBranch());
					}
					Map<String, String> issueDetails = new HashMap<>();
					issueDetails.put(leadTimeChangeData.getStoryID(), checkEmptyURL(leadTimeChangeData));
					excelData.setStoryId(issueDetails);
					excelData.setLeadTimeForChange(leadTimeChangeData.getLeadTimeInDays());
					excelData.setReleaseDate(leadTimeChangeData.getReleaseDate());
					kpiExcelData.add(excelData);
				});
			});
		}
	}

	public static void populateEpicProgessExcelData(Map<String, String> epicWiseIssueSize,
			Map<String, JiraIssue> epicIssues, List<KPIExcelData> excelDataList,
			JiraIssueReleaseStatus jiraIssueReleaseStatus, Map<String, List<JiraIssue>> epicWiseJiraIssues) {
		epicWiseIssueSize.forEach((epicNumber, issue) -> {
			KPIExcelData excelData = new KPIExcelData();
			List<JiraIssue> jiraIssueList = epicWiseJiraIssues.get(epicNumber);
			JiraIssue jiraIssue = epicIssues.get(epicNumber);
			if (jiraIssue != null) {
				// filter by to do category
				List<JiraIssue> toDoJiraIssue = ReleaseKpiHelper.filterIssuesByStatus(jiraIssueList,
						jiraIssueReleaseStatus.getToDoList());
				// filter by inProgress category
				List<JiraIssue> inProgressJiraIssue = ReleaseKpiHelper.filterIssuesByStatus(jiraIssueList,
						jiraIssueReleaseStatus.getInProgressList());
				// filter by done category
				List<JiraIssue> doneJiraIssue = ReleaseKpiHelper.filterIssuesByStatus(jiraIssueList,
						jiraIssueReleaseStatus.getClosedList());
				Integer totalJiraSize = toDoJiraIssue.size() + inProgressJiraIssue.size() + doneJiraIssue.size();
				double toDoPercentage = roundingOff((100.0d * toDoJiraIssue.size()) / totalJiraSize);
				double inProgressPercentage = roundingOff((100.0d * inProgressJiraIssue.size()) / totalJiraSize);
				double donePercentage = roundingOff((100.0d * doneJiraIssue.size()) / totalJiraSize);
				Map<String, String> storyDetails = new HashMap<>();
				storyDetails.put(epicNumber, checkEmptyURL(jiraIssue));
				excelData.setEpicID(storyDetails);
				excelData.setEpicName(checkEmptyName(jiraIssue));
				excelData.setToDo(
						new StringBuilder().append(toDoJiraIssue.size()).append("/").append(toDoPercentage).append("%").toString());
				excelData.setInProgress(new StringBuilder().append(inProgressJiraIssue.size()).append("/")
						.append(inProgressPercentage).append("%").toString());
				excelData.setDone(
						new StringBuilder().append(doneJiraIssue.size()).append("/").append(donePercentage).append("%").toString());
				excelData.setEpicStatus(StringUtils.isNotEmpty(jiraIssue.getStatus()) ? jiraIssue.getStatus() : Constant.BLANK);
				excelData.setStoryPoint(issue);
				excelDataList.add(excelData);
			}
		});
	}

	/**
	 * Method to populate Modal Window of Mean Time to Recover
	 *
	 * @param projectName
	 *          Name of Project
	 * @param meanTimeRecoverMapTimeWise
	 *          Map<String, List<MeanTimeRecoverData>>
	 * @param kpiExcelData
	 *          List<KPIExcelData>
	 */
	public static void populateMeanTimeToRecoverExcelData(String projectName,
			Map<String, List<MeanTimeRecoverData>> meanTimeRecoverMapTimeWise, List<KPIExcelData> kpiExcelData) {
		if (MapUtils.isNotEmpty(meanTimeRecoverMapTimeWise)) {
			meanTimeRecoverMapTimeWise.forEach(
					(weekOrMonthName, meanRecoverListCurrentTime) -> meanRecoverListCurrentTime.forEach(meanTimeRecoverData -> {
						KPIExcelData excelData = new KPIExcelData();
						excelData.setProjectName(projectName);
						excelData.setWeeks(weekOrMonthName);
						Map<String, String> issueDetails = new HashMap<>();
						issueDetails.put(meanTimeRecoverData.getStoryID(),
								StringUtils.isEmpty(meanTimeRecoverData.getUrl())
										? Constant.EMPTY_STRING
										: meanTimeRecoverData.getUrl());
						excelData.setStoryId(issueDetails);
						excelData.setIssueType(meanTimeRecoverData.getIssueType());
						excelData.setIssueDesc(meanTimeRecoverData.getDesc());
						excelData.setCompletionDate(!StringUtils.isEmpty(meanTimeRecoverData.getClosedDate())
								? meanTimeRecoverData.getClosedDate()
								: Constant.BLANK);
						excelData.setCreatedDate(meanTimeRecoverData.getCreatedDate());
						excelData.setTimeToRecover(meanTimeRecoverData.getTimeToRecover());
						kpiExcelData.add(excelData);
					}));
		}
	}

	public static void populateFlowEfficiency(LinkedHashMap<JiraIssueCustomHistory, Double> flowEfficiency,
			List<String> waitTimeList, List<String> totalTimeList, List<KPIExcelData> excelDataList) {
		AtomicInteger i = new AtomicInteger();
		flowEfficiency.forEach((issue, value) -> {
			KPIExcelData kpiExcelData = new KPIExcelData();
			Map<String, String> url = new HashMap<>();
			url.put(issue.getStoryID(), checkEmptyURL(issue));
			kpiExcelData.setIssueID(url);
			kpiExcelData.setIssueType(issue.getStoryType());
			kpiExcelData.setIssueDesc(issue.getDescription());
			kpiExcelData.setSizeInStoryPoints(issue.getEstimate());
			kpiExcelData.setWaitTime(waitTimeList.get(i.get()));
			kpiExcelData.setTotalTime(totalTimeList.get(i.get()));
			kpiExcelData.setFlowEfficiency(value.longValue());
			excelDataList.add(kpiExcelData);
			i.set(i.get() + 1);
		});
	}

	/**
	 * Method to populate the Release BurnUp Excel
	 *
	 * @param jiraIssues
	 *          jiraIssues
	 * @param issueWiseReleaseTagDateMap
	 *          issueWiseReleaseTagDateMap
	 * @param completeDateIssueMap
	 *          completeDateIssueMap
	 * @param devCompleteDateIssueMap
	 *          devCompleteDateIssueMap
	 * @param kpiExcelData
	 *          kpiExcelData
	 * @param fieldMapping
	 *          fieldMapping
	 */
	public static void populateReleaseBurnUpExcelData(List<JiraIssue> jiraIssues,
			Map<String, LocalDate> issueWiseReleaseTagDateMap, Map<String, LocalDate> completeDateIssueMap,
			Map<String, LocalDate> devCompleteDateIssueMap, List<KPIExcelData> kpiExcelData, FieldMapping fieldMapping) {
		if (CollectionUtils.isNotEmpty(jiraIssues)) {
			jiraIssues.forEach(jiraIssue -> {
				KPIExcelData excelData = new KPIExcelData();
				Map<String, String> issueDetails = new HashMap<>();
				issueDetails.put(jiraIssue.getNumber(), checkEmptyURL(jiraIssue));
				excelData.setIssueID(issueDetails);
				excelData.setIssueDesc(checkEmptyName(jiraIssue));
				excelData.setIssueStatus(jiraIssue.getStatus());
				excelData.setIssueType(jiraIssue.getTypeName());
				populateAssignee(jiraIssue, excelData);
				excelData.setPriority(jiraIssue.getPriority());
				if (StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria()) &&
						fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT)) {
					double roundingOff = roundingOff(Optional.ofNullable(jiraIssue.getStoryPoints()).orElse(0.0));
					excelData.setStoryPoint(Double.toString(roundingOff));
				} else if (null != jiraIssue.getAggregateTimeOriginalEstimateMinutes()) {
					double totalOriginalEstimate = Double.valueOf(jiraIssue.getAggregateTimeOriginalEstimateMinutes()) / 60;
					excelData.setStoryPoint(roundingOff(totalOriginalEstimate / fieldMapping.getStoryPointToHourMapping()) + "/" +
							roundingOff(totalOriginalEstimate) + " hrs");
				}
				excelData.setLatestReleaseTagDate(
						DateUtil.dateTimeConverter(String.valueOf(issueWiseReleaseTagDateMap.get(jiraIssue.getNumber())),
								DateUtil.DATE_FORMAT, DateUtil.DISPLAY_DATE_FORMAT));
				String devDate = DateUtil.dateTimeConverter(String.valueOf(devCompleteDateIssueMap.get(jiraIssue.getNumber())),
						DateUtil.DATE_FORMAT, DateUtil.DISPLAY_DATE_FORMAT);
				excelData.setDevCompleteDate(StringUtils.isNotEmpty(devDate) ? devDate : Constant.BLANK);
				String completionDate = DateUtil.dateTimeConverter(
						String.valueOf(completeDateIssueMap.get(jiraIssue.getNumber())), DateUtil.DATE_FORMAT,
						DateUtil.DISPLAY_DATE_FORMAT);
				excelData.setCompletionDate(StringUtils.isNotEmpty(completionDate) ? completionDate : Constant.BLANK);
				kpiExcelData.add(excelData);
			});
		}
	}

	public static void populateBackLogData(List<IterationKpiModalValue> overAllmodalValues,
			List<IterationKpiModalValue> modalValues, JiraIssue jiraIssue, JiraIssueCustomHistory jiraCustomHistory,
			List<String> status) {
		IterationKpiModalValue iterationKpiModalValue = new IterationKpiModalValue();
		iterationKpiModalValue.setIssueType(jiraIssue.getTypeName());
		iterationKpiModalValue.setIssueURL(jiraIssue.getUrl());
		iterationKpiModalValue.setIssueId(jiraIssue.getNumber());
		iterationKpiModalValue.setDescription(jiraIssue.getName());
		iterationKpiModalValue.setPriority(jiraIssue.getPriority());
		if (ObjectUtils.isNotEmpty(jiraCustomHistory.getCreatedDate()))
			iterationKpiModalValue.setCreatedDate(
					DateUtil.dateTimeFormatter(jiraCustomHistory.getCreatedDate().toDate(), DateUtil.DISPLAY_DATE_FORMAT));
		Optional<JiraHistoryChangeLog> sprint = jiraCustomHistory.getStatusUpdationLog().stream()
				.filter(sprintDetails -> CollectionUtils.isNotEmpty(status) && status.contains(sprintDetails.getChangedTo()))
				.sorted(Comparator.comparing(JiraHistoryChangeLog::getUpdatedOn)).findFirst();
		if (sprint.isPresent()) {
			iterationKpiModalValue
					.setDorDate(DateUtil.dateTimeFormatter(sprint.get().getUpdatedOn(), DateUtil.DISPLAY_DATE_FORMAT));
		}
		iterationKpiModalValue.setIssueSize(Optional.ofNullable(jiraIssue.getStoryPoints()).orElse(0.0).toString());
		overAllmodalValues.add(iterationKpiModalValue);
		modalValues.add(iterationKpiModalValue);
	}

	private static void setSquads(KPIExcelData excelData, JiraIssue jiraIssue) {
		if (CollectionUtils.isNotEmpty(jiraIssue.getAdditionalFilters())) {
			excelData.setSquads(jiraIssue.getAdditionalFilters().stream()
					.flatMap(additionalFilter -> additionalFilter.getFilterValues().stream().map(AdditionalFilterValue::getValue))
					.toList());

		} else {
			excelData.setSquads(List.of(Constant.BLANK));
		}
	}

	public static void populateReleasePlanExcelData(List<JiraIssue> jiraIssues, List<KPIExcelData> kpiExcelData,
			FieldMapping fieldMapping) {
		if (CollectionUtils.isNotEmpty(jiraIssues)) {
			jiraIssues.forEach(jiraIssue -> {
				KPIExcelData excelData = new KPIExcelData();
				Map<String, String> issueDetails = new HashMap<>();
				issueDetails.put(jiraIssue.getNumber(), checkEmptyURL(jiraIssue));
				excelData.setIssueID(issueDetails);
				excelData.setIssueDesc(checkEmptyName(jiraIssue));
				excelData.setIssueStatus(jiraIssue.getStatus());
				excelData.setIssueType(jiraIssue.getTypeName());
				populateAssignee(jiraIssue, excelData);
				excelData.setPriority(jiraIssue.getPriority());
				if (StringUtils.isNotEmpty(fieldMapping.getEstimationCriteria()) &&
						fieldMapping.getEstimationCriteria().equalsIgnoreCase(CommonConstant.STORY_POINT)) {
					double roundingOff = roundingOff(Optional.ofNullable(jiraIssue.getStoryPoints()).orElse(0.0));
					excelData.setStoryPoint(Double.toString(roundingOff));
				} else if (null != jiraIssue.getAggregateTimeOriginalEstimateMinutes()) {
					double totalOriginalEstimate = Double.valueOf(jiraIssue.getAggregateTimeOriginalEstimateMinutes()) / 60;
					excelData.setStoryPoint(roundingOff(totalOriginalEstimate / fieldMapping.getStoryPointToHourMapping()) + "/" +
							roundingOff(totalOriginalEstimate) + " hrs");
				}
				excelData.setDueDate((StringUtils.isNotEmpty(jiraIssue.getDueDate()))
						? DateUtil.dateTimeConverter(jiraIssue.getDueDate(), DateUtil.TIME_FORMAT_WITH_SEC,
								DateUtil.DISPLAY_DATE_FORMAT)
						: Constant.BLANK);
				kpiExcelData.add(excelData);
			});
		}
	}
	
	public static void populateDefectWithReopenInfoExcelData(String sprint, List<KPIExcelData> kpiExcelData,
			CustomApiConfig customApiConfig, List<JiraIssue> storyList,
			Map<String, List<DefectTransitionInfo>> reopenedDefectInfoMap) {
		if (MapUtils.isNotEmpty(reopenedDefectInfoMap)) {
			reopenedDefectInfoMap
					.forEach((key, reopenTransitionList) -> reopenTransitionList.forEach(defectTransitionInfo -> {
						KPIExcelData excelData = new KPIExcelData();
						JiraIssue jiraIssue = defectTransitionInfo.getDefectJiraIssue();
						excelData.setSprintName(sprint);
						excelData.setDefectDesc(checkEmptyName(jiraIssue));
						Map<String, String> defectIdDetails = new HashMap<>();
						defectIdDetails.put(jiraIssue.getNumber(), checkEmptyURL(jiraIssue));
						excelData.setDefectId(defectIdDetails);
						setSquads(excelData, jiraIssue);
						excelData.setDefectPriority(setPriority(customApiConfig, jiraIssue));
						excelData.setRootCause(jiraIssue.getRootCauseList());
						excelData.setDefectStatus(jiraIssue.getStatus());
						excelData.setLabels(jiraIssue.getLabels());
						Integer totalTimeSpentInMinutes = Objects.requireNonNullElse(jiraIssue.getTimeSpentInMinutes(), 0);
						setStoryExcelData(storyList, jiraIssue, excelData, totalTimeSpentInMinutes, customApiConfig);

						excelData.setReopenDate(String.valueOf(defectTransitionInfo.getReopenDate()));
						excelData.setClosedDate(String.valueOf(defectTransitionInfo.getClosedDate()));
						excelData.setDurationToReopen(defectTransitionInfo.getReopenDuration() + "Hrs");
						kpiExcelData.add(excelData);
					}));
		}
	}
}
