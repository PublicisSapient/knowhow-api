/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
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
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.Filters;
import com.publicissapient.kpidashboard.apis.enums.JiraFeature;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.model.DefectTransitionInfo;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.NormalizedJira;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Service to calculate defect reopen rate and perform trend analysis (reopened defects vs resolved
 * defects in %).{@link JiraKPIService}
 *
 * @author shunaray
 */
@Slf4j
@Component
public class DefectReopenRateQualityServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {
	@Autowired private JiraIssueRepository jiraIssueRepository;
	@Autowired private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;
	@Autowired private SprintRepository sprintRepository;
	@Autowired private ConfigHelperService configHelperService;
	@Autowired private CustomApiConfig customApiConfig;
	@Autowired private FilterHelperService filterHelperService;
	@Autowired private CacheService cacheService;

	private static final String SPRINT_SUBTASK_DEFECTS = "totalSprintSubtaskDefects";
	public static final String AVERAGE_TIME_TO_REOPEN = "Average Time to Reopen";
	public static final String SPRINT_DETAILS = "sprintWiseSprintDetails";
	public static final String REOPENED_DEFECTS = "Reopened Defects";
	public static final String COMPLETED_DEFECTS = "Completed Defects";
	private static final String PROJECT_WISE_DEFECT_HISTORY = "projectWiseDefectHistory";
	private static final String TOTAL_DEFECT_DATA = "totalDefectWithoutDrop";
	public static final String STORY_LIST = "storyList";

	/** {@inheritDoc} */
	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {

		List<DataCount> trendValueList = new ArrayList<>();
		Node root = treeAggregatorDetail.getRoot();
		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();

		treeAggregatorDetail
				.getMapOfListOfLeafNodes()
				.forEach(
						(k, v) -> {
							if (Filters.getFilter(k) == Filters.SPRINT) {
								sprintWiseLeafNodeValue(mapTmp, v, trendValueList, kpiElement, kpiRequest);
							}
						});

		log.debug(
				"[DEFECT-REOPEN-RATE-QUALITY-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				root);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(root, nodeWiseKPIValue, KPICode.DEFECT_REOPEN_RATE_QUALITY);
		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMap(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.DEFECT_REOPEN_RATE_QUALITY);

		trendValuesMap =
				KPIHelperUtil.sortTrendMapByKeyOrder(
						trendValuesMap,
						Arrays.asList(
								CommonConstant.OVERALL,
								Constant.P1,
								Constant.P2,
								Constant.P3,
								Constant.P4,
								Constant.MISC));
		Map<String, Map<String, List<DataCount>>> issueTypeProjectWiseDc = new LinkedHashMap<>();
		trendValuesMap.forEach(
				(issueType, dataCounts) -> {
					Map<String, List<DataCount>> projectWiseDc =
							dataCounts.stream().collect(Collectors.groupingBy(DataCount::getData));
					issueTypeProjectWiseDc.put(issueType, projectWiseDc);
				});

		List<DataCountGroup> dataCountGroups = new ArrayList<>();
		issueTypeProjectWiseDc.forEach(
				(issueType, projectWiseDc) -> {
					DataCountGroup dataCountGroup = new DataCountGroup();
					List<DataCount> dataList = new ArrayList<>();
					projectWiseDc.forEach((key, value) -> dataList.addAll(value));
					dataCountGroup.setFilter(issueType);
					dataCountGroup.setValue(dataList);
					dataCountGroups.add(dataCountGroup);
				});

		kpiElement.setTrendValueList(dataCountGroups);
		return kpiElement;
	}

	/**
	 * This method populates KPI value to sprint leaf nodes. It also gives the trend analysis at
	 * sprint wise.
	 *
	 * @param mapTmp node id map
	 * @param sprintLeafNodeList sprint nodes list
	 * @param trendValueList list to hold trend nodes data
	 * @param kpiElement the KpiElement
	 * @param kpiRequest the KpiRequest
	 */
	@SuppressWarnings("unchecked")
	private void sprintWiseLeafNodeValue(
			Map<String, Node> mapTmp,
			List<Node> sprintLeafNodeList,
			List<DataCount> trendValueList,
			KpiElement kpiElement,
			KpiRequest kpiRequest) {

		String requestTrackerId = getRequestTrackerId();
		sprintLeafNodeList.sort(Comparator.comparing(node -> node.getSprintFilter().getStartDate()));

		Map<String, Object> resultListMap =
				fetchKPIDataFromDb(sprintLeafNodeList, null, null, kpiRequest);
		List<JiraIssue> storyList =
				(List<JiraIssue>) resultListMap.getOrDefault(STORY_LIST, new ArrayList<>());
		List<JiraIssue> totalDefectList = (List<JiraIssue>) resultListMap.get(TOTAL_DEFECT_DATA);
		List<JiraIssue> totalSubtaskDefectList =
				(List<JiraIssue>) resultListMap.get(SPRINT_SUBTASK_DEFECTS);
		Map<String, List<JiraIssueCustomHistory>> projectWiseDefectHistoryList =
				(Map<String, List<JiraIssueCustomHistory>>) resultListMap.get(PROJECT_WISE_DEFECT_HISTORY);
		List<SprintDetails> sprintDetails = (List<SprintDetails>) resultListMap.get(SPRINT_DETAILS);

		Map<Pair<String, String>, Map<String, Long>> sprintWiseReopenedDefectsPriorityMap =
				new HashMap<>();
		Map<Pair<String, String>, Map<String, Long>> sprintWiseCompletedDefectsPriorityMap =
				new HashMap<>();
		Map<Pair<String, String>, Map<String, List<DefectTransitionInfo>>>
				sprintWiseReopenedDefectTransitionMap = new HashMap<>();
		Map<Pair<String, String>, List<JiraIssue>> sprintWiseReOpenedDefectListMap = new HashMap<>();
		Map<Pair<String, String>, List<JiraIssue>> sprintWiseCompletedDefectListMap = new HashMap<>();

		List<KPIExcelData> excelData = new ArrayList<>();
		Set<String> projectWisePriorityList = new HashSet<>();
		sprintDetails.forEach(
				sd -> {
					final String basicProjectConfigId = sd.getBasicProjectConfigId().toString();
					Pair<String, String> sprintFilter = Pair.of(basicProjectConfigId, sd.getSprintID());
					List<JiraIssueCustomHistory> projectDefectHistory =
							projectWiseDefectHistoryList.getOrDefault(
									basicProjectConfigId, Collections.emptyList());
					List<String> completedSprintIssues =
							KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(
									sd, CommonConstant.COMPLETED_ISSUES);

					FieldMapping fieldMapping =
							configHelperService.getFieldMapping(sd.getBasicProjectConfigId());
					// For finding the completed Defect we are taking combination of DodStatus &
					// DefectRejectionStatus
					List<String> dodStatus =
							Optional.ofNullable(sd.getCompletedIssues()).orElse(Collections.emptySet()).stream()
									.map(SprintIssue::getStatus)
									.map(String::toLowerCase)
									.collect(Collectors.toList());
					String defectRejectionStatus =
							Optional.ofNullable(fieldMapping)
									.map(FieldMapping::getJiraDefectRejectionStatusKPI190)
									.orElse("");
					List<String> dodAndDefectRejStatus = new ArrayList<>(dodStatus);
					if (StringUtils.isNotEmpty(defectRejectionStatus))
						dodAndDefectRejStatus.add(defectRejectionStatus.toLowerCase());

					List<JiraIssue> sprintSubtask =
							KpiDataHelper.getTotalSprintSubTasks(
									totalSubtaskDefectList.stream()
											.filter(
													jiraIssue ->
															CollectionUtils.isNotEmpty(jiraIssue.getSprintIdList())
																	&& jiraIssue
																			.getSprintIdList()
																			.contains(sd.getSprintID().split("_")[0]))
											.collect(Collectors.toList()),
									sd,
									projectDefectHistory,
									dodAndDefectRejStatus);

					final String defectReopenStatusKPI190 =
							Optional.ofNullable(fieldMapping)
									.map(FieldMapping::getDefectReopenStatusKPI190)
									.orElse("");
					List<JiraIssue> sprintReOpenedDefects =
							KpiHelperService.getSprintReOpenedDefects(
									sd,
									projectDefectHistory,
									dodStatus,
									totalDefectList,
									defectReopenStatusKPI190,
									sprintWiseReopenedDefectTransitionMap.computeIfAbsent(
											sprintFilter, k -> new HashMap<>()));

					List<JiraIssue> sprintCompletedDefects =
							totalDefectList.stream()
									.filter(element -> completedSprintIssues.contains(element.getNumber()))
									.filter(
											element -> dodAndDefectRejStatus.contains(element.getStatus().toLowerCase()))
									.collect(Collectors.toList());

					sprintCompletedDefects.addAll(
							KpiDataHelper.getCompletedSubTasksByHistory(
									sprintSubtask, projectDefectHistory, sd, dodAndDefectRejStatus, new HashMap<>()));

					sprintWiseReOpenedDefectListMap.put(sprintFilter, sprintReOpenedDefects);
					sprintWiseCompletedDefectListMap.put(sprintFilter, sprintCompletedDefects);

					Map<String, Long> priorityCountMapReopenedDefects =
							KPIHelperUtil.setpriorityScrum(sprintReOpenedDefects, customApiConfig);
					Map<String, Long> priorityCountMapCompletedDefects =
							KPIHelperUtil.setpriorityScrum(sprintCompletedDefects, customApiConfig);
					projectWisePriorityList.addAll(priorityCountMapReopenedDefects.keySet());
					projectWisePriorityList.addAll(priorityCountMapCompletedDefects.keySet());
					sprintWiseReopenedDefectsPriorityMap.put(sprintFilter, priorityCountMapReopenedDefects);
					sprintWiseCompletedDefectsPriorityMap.put(sprintFilter, priorityCountMapCompletedDefects);
				});

		sprintLeafNodeList.forEach(
				node -> {
					String trendLineName = node.getProjectFilter().getName();
					Pair<String, String> currentNodeIdentifier =
							Pair.of(
									node.getProjectFilter().getBasicProjectConfigId().toString(),
									node.getSprintFilter().getId());

					Map<String, List<DataCount>> dataCountMap = new HashMap<>();
					Map<String, Long> reopenedDefectsPriorityMap =
							sprintWiseReopenedDefectsPriorityMap.getOrDefault(
									currentNodeIdentifier, new HashMap<>());
					Map<String, Long> completedDefectsPriorityMap =
							sprintWiseCompletedDefectsPriorityMap.getOrDefault(
									currentNodeIdentifier, new HashMap<>());
					final Map<String, List<DefectTransitionInfo>> reopenDefectTransitionMap =
							sprintWiseReopenedDefectTransitionMap.getOrDefault(
									currentNodeIdentifier, new HashMap<>());
					Map<String, Long> reopenedFinalMap = new HashMap<>();
					Map<String, Long> completedFinalMap = new HashMap<>();
					if (CollectionUtils.isNotEmpty(projectWisePriorityList)) {
						projectWisePriorityList.forEach(
								priority -> {
									Long reopenedWiseCount = reopenedDefectsPriorityMap.getOrDefault(priority, 0L);
									Long completedWiseCount = completedDefectsPriorityMap.getOrDefault(priority, 0L);
									reopenedFinalMap.put(StringUtils.capitalize(priority), reopenedWiseCount);
									completedFinalMap.put(StringUtils.capitalize(priority), completedWiseCount);
								});
						projectWisePriorityList.forEach(
								priority -> reopenedFinalMap.computeIfAbsent(priority, val -> 0L));
						projectWisePriorityList.forEach(
								priority -> completedFinalMap.computeIfAbsent(priority, val -> 0L));
						Long reopenedOverAllCount =
								reopenedFinalMap.values().stream().mapToLong(val -> val).sum();
						Long completedOverAllCount =
								completedFinalMap.values().stream().mapToLong(val -> val).sum();
						reopenedFinalMap.put(CommonConstant.OVERALL, reopenedOverAllCount);
						completedFinalMap.put(CommonConstant.OVERALL, completedOverAllCount);
						Map<String, Double> reopenRateByPriorityMap =
								avgReopenRateByPriority(reopenDefectTransitionMap, customApiConfig);
						projectWisePriorityList.add(CommonConstant.OVERALL);
						projectWisePriorityList.forEach(
								priority -> {
									DataCount dataCount =
											getDataCountObject(
													node,
													trendLineName,
													StringUtils.capitalize(priority),
													reopenedFinalMap,
													completedFinalMap,
													reopenRateByPriorityMap);
									trendValueList.add(dataCount);
									dataCountMap.computeIfAbsent(priority, k -> new ArrayList<>()).add(dataCount);
								});

						populateExcelDataObject(
								requestTrackerId,
								node.getSprintFilter().getName(),
								excelData,
								reopenDefectTransitionMap,
								customApiConfig,
								storyList);
					}
					mapTmp.get(node.getId()).setValue(dataCountMap);
				});

		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(
				KPIExcelColumn.DEFECT_REOPEN_RATE_QUALITY.getColumns(
						sprintLeafNodeList, cacheService, filterHelperService));
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		Map<String, Object> resultListMap = new HashMap<>();

		Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();
		List<String> sprintList = new ArrayList<>();
		List<String> basicProjectConfigIds = new ArrayList<>();
		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
		Map<String, Map<String, List<String>>> droppedDefectMap = new HashMap<>();
		List<String> defectType = new ArrayList<>();

		leafNodeList.forEach(
				leaf -> {
					ObjectId basicProjectConfigId = leaf.getProjectFilter().getBasicProjectConfigId();
					Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();

					FieldMapping fieldMapping =
							configHelperService.getFieldMappingMap().get(basicProjectConfigId);
					sprintList.add(leaf.getSprintFilter().getId());
					basicProjectConfigIds.add(basicProjectConfigId.toString());
					defectType.add(NormalizedJira.DEFECT_TYPE.getValue());

					KpiHelperService.getDroppedDefectsFilters(
							droppedDefectMap,
							basicProjectConfigId,
							fieldMapping.getResolutionTypeForRejectionKPI190(),
							fieldMapping.getJiraDefectRejectionStatusKPI190());

					mapOfProjectFilters.put(
							JiraFeature.ISSUE_TYPE.getFieldValueInFeature(),
							CommonUtils.convertToPatternList(defectType));
					uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);
				});

		List<SprintDetails> sprintDetails = sprintRepository.findBySprintIDIn(sprintList);

		Set<String> totalSprintReportStories = new HashSet<>();
		Set<String> taggedIssuesInSprint = new HashSet<>();
		sprintDetails.forEach(
				sprintDetail -> {
					if (CollectionUtils.isNotEmpty(sprintDetail.getTotalIssues())) {
						FieldMapping fieldMapping =
								configHelperService.getFieldMapping(sprintDetail.getBasicProjectConfigId());
						totalSprintReportStories.addAll(
								sprintDetail.getTotalIssues().stream()
										.filter(
												sprintIssue ->
														!fieldMapping.getJiradefecttype().contains(sprintIssue.getTypeName()))
										.map(SprintIssue::getNumber)
										.collect(Collectors.toSet()));
					}
					taggedIssuesInSprint.addAll(
							KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(
									sprintDetail, CommonConstant.TOTAL_ISSUES));
					taggedIssuesInSprint.addAll(
							KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(
									sprintDetail, CommonConstant.COMPLETED_ISSUES_ANOTHER_SPRINT));
					taggedIssuesInSprint.addAll(
							KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(
									sprintDetail, CommonConstant.PUNTED_ISSUES));
					taggedIssuesInSprint.addAll(
							KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(
									sprintDetail, CommonConstant.ADDED_ISSUES));
				});

		/* * additional filter **/
		KpiDataHelper.createAdditionalFilterMap(
				kpiRequest, mapOfFilters, Constant.SCRUM, filterHelperService);

		mapOfFilters.put(
				JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				basicProjectConfigIds.stream().distinct().collect(Collectors.toList()));
		List<JiraIssue> totalDefectList = new ArrayList<>();
		List<JiraIssue> defectListWoDrop = new ArrayList<>();

		// fetching all defects for the projects
		List<JiraIssue> projectDefectList =
				jiraIssueRepository.findIssuesByFilterAndProjectMapFilter(mapOfFilters, uniqueProjectMap);

		List<JiraIssue> totalSprintSubTaskDefects =
				jiraIssueRepository
						.findLinkedDefects(mapOfFilters, totalSprintReportStories, uniqueProjectMap)
						.stream()
						.filter(jiraIssue -> !taggedIssuesInSprint.contains(jiraIssue.getNumber()))
						.collect(Collectors.toList());

		totalDefectList.addAll(projectDefectList);
		totalDefectList.addAll(totalSprintSubTaskDefects);
		KpiHelperService.getDefectsWithoutDrop(droppedDefectMap, totalDefectList, defectListWoDrop);
		List<JiraIssueCustomHistory> defectsCustomHistoryList =
				jiraIssueCustomHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(
						defectListWoDrop.stream().map(JiraIssue::getNumber).toList(),
						basicProjectConfigIds.stream().distinct().collect(Collectors.toList()));
		Set<String> linkedStoryIdsOfDefect =
				defectListWoDrop.stream()
						.map(JiraIssue::getDefectStoryID)
						.filter(Objects::nonNull)
						.flatMap(Set::stream)
						.collect(Collectors.toSet());
		Map<String, List<JiraIssueCustomHistory>> projectWiseDefectHistory =
				defectsCustomHistoryList.stream()
						.collect(Collectors.groupingBy(JiraIssueCustomHistory::getBasicProjectConfigId));
		resultListMap.put(SPRINT_SUBTASK_DEFECTS, totalSprintSubTaskDefects);
		resultListMap.put(PROJECT_WISE_DEFECT_HISTORY, projectWiseDefectHistory);
		resultListMap.put(SPRINT_DETAILS, sprintDetails);
		String requestTrackerId = getRequestTrackerId();
		// fetch linked story data only for Excel request
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			resultListMap.put(
					STORY_LIST,
					jiraIssueRepository.findIssueAndDescByNumber(new ArrayList<>(linkedStoryIdsOfDefect)));
		}
		resultListMap.put(TOTAL_DEFECT_DATA, defectListWoDrop);
		return resultListMap;
	}

	private DataCount getDataCountObject(
			Node node,
			String trendLineName,
			String priority,
			Map<String, Long> reopenedFinalMap,
			Map<String, Long> completedFinalMap,
			Map<String, Double> reopenRateByPriorityMap) {
		DataCount dataCount = new DataCount();
		final Long reopenedCount = reopenedFinalMap.getOrDefault(priority, 0L);
		final Long completedCount = completedFinalMap.getOrDefault(priority, 0L);
		double value =
				completedCount == 0 ? 0.0 : (double) Math.round((100.0 * reopenedCount) / completedCount);
		dataCount.setData(String.valueOf(value));
		dataCount.setSProjectName(trendLineName);
		dataCount.setSSprintID(node.getSprintFilter().getId());
		dataCount.setSSprintName(node.getSprintFilter().getName());
		dataCount.setValue(value);
		dataCount.setKpiGroup(priority);
		Map<String, Object> hoverValueMap = new LinkedHashMap<>();
		hoverValueMap.put(REOPENED_DEFECTS, reopenedCount);
		hoverValueMap.put(COMPLETED_DEFECTS, completedCount);
		hoverValueMap.put(
				AVERAGE_TIME_TO_REOPEN, reopenRateByPriorityMap.getOrDefault(priority, 0.0) + " Hrs");
		dataCount.setHoverValue(hoverValueMap);
		return dataCount;
	}

	private void populateExcelDataObject(
			String requestTrackerId,
			String sprintName,
			List<KPIExcelData> excelData,
			Map<String, List<DefectTransitionInfo>> reopenedDefectInfoMap,
			CustomApiConfig customApiConfig,
			List<JiraIssue> storyList) {

		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			KPIExcelUtility.populateDefectWithReopenInfoExcelData(
					sprintName, excelData, customApiConfig, storyList, reopenedDefectInfoMap);
		}
	}

	private Map<String, Double> avgReopenRateByPriority(
			Map<String, List<DefectTransitionInfo>> reopenedDefectInfoMap,
			CustomApiConfig customApiConfig) {
		List<DefectTransitionInfo> reopenedDurationList =
				reopenedDefectInfoMap.values().stream()
						.flatMap(List::stream)
						.filter(info -> info.getReopenDate() != null)
						.toList();

		Map<String, Double> avgByPriority =
				reopenedDurationList.stream()
						.collect(
								Collectors.groupingBy(
										info ->
												StringUtils.capitalize(
														KPIHelperUtil.mappingPriority(info.getPriority(), customApiConfig)),
										Collectors.averagingDouble(DefectTransitionInfo::getReopenDuration)));

		avgByPriority.replaceAll((k, v) -> round(v));

		double overallAvg =
				reopenedDurationList.stream()
						.mapToDouble(DefectTransitionInfo::getReopenDuration)
						.average()
						.orElse(0.0);
		avgByPriority.put(CommonConstant.OVERALL, round(overallAvg));
		return avgByPriority;
	}

	/** {@inheritDoc} */
	@Override
	public Double calculateKpiValue(List<Double> valueList, String kpiName) {
		return calculateKpiValueForDouble(valueList, kpiName);
	}

	/** {@inheritDoc} */
	@Override
	public Double calculateKPIMetrics(Map<String, Object> reOpenedAndTotalCompletedDefectDataMap) {
		return null;
	}

	/** {@inheritDoc} */
	@Override
	public String getQualifierType() {
		return KPICode.DEFECT_REOPEN_RATE_QUALITY.name();
	}

	/** {@inheritDoc} */
	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI190(), KPICode.DEFECT_REOPEN_RATE_QUALITY.getKpiId());
	}
}
