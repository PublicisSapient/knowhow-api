/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 
 *    http://www.apache.org/licenses/LICENSE-2.0
 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/

package com.publicissapient.kpidashboard.apis.jira.scrum.service;

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
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.ReopenedDefectInfo;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import javax.ws.rs.core.Feature;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service to calculate defect reopen rate and perform trend analysis (reopened
 * defects vs resolved defects in %).{@link JiraKPIService}
 *
 * @author shunaray
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefectReopenRateQualityServiceImpl extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private final JiraIssueRepository jiraIssueRepository;
	private final JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;
	private final SprintRepository sprintRepository;
	private final ConfigHelperService configHelperService;
	private final CustomApiConfig customApiConfig;
	private final FilterHelperService filterHelperService;
	private final CacheService cacheService;

	private static final String SPRINT_SUBTASK_DEFECTS = "totalSprintSubtaskDefects";
	public static final String SPRINT_DETAILS = "sprintWiseSprintDetails";
	public static final String REOPENED_DEFECTS = "Reopened Defects";
	public static final String COMPLETED_DEFECTS = "Completed Defects";
	private static final String DEFECT_HISTORY = "subTaskBugsHistory";
	public static final String UNCHECKED = "unchecked";
	private static final String TOTAL_DEFECT_DATA = "totalDefectWithoutDrop";
	public static final String STORY_LIST = "storyList";
	private static final String DEV = "DeveloperKpi";

	/** {@inheritDoc} */
	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement,
			TreeAggregatorDetail treeAggregatorDetail) throws ApplicationException {

		List<DataCount> trendValueList = new ArrayList<>();
		Node root = treeAggregatorDetail.getRoot();
		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();

		treeAggregatorDetail.getMapOfListOfLeafNodes().forEach((k, v) -> {
			if (Filters.getFilter(k) == Filters.SPRINT) {
				sprintWiseLeafNodeValue(mapTmp, v, trendValueList, kpiElement, kpiRequest);
			}
		});

		log.debug("[DEFECT-REOPEN-RATE-QUALITY-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(), root);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(root, nodeWiseKPIValue, KPICode.DEFECT_REOPEN_RATE_QUALITY);
		Map<String, List<DataCount>> trendValuesMap = getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue,
				KPICode.DEFECT_REOPEN_RATE_QUALITY);

		trendValuesMap = sortTrendValueMap(trendValuesMap, Arrays.asList(CommonConstant.OVERALL, Constant.P1,
				Constant.P2, Constant.P3, Constant.P4, Constant.MISC));
		Map<String, Map<String, List<DataCount>>> issueTypeProjectWiseDc = new LinkedHashMap<>();
		trendValuesMap.forEach((issueType, dataCounts) -> {
			Map<String, List<DataCount>> projectWiseDc = dataCounts.stream()
					.collect(Collectors.groupingBy(DataCount::getData));
			issueTypeProjectWiseDc.put(issueType, projectWiseDc);
		});

		List<DataCountGroup> dataCountGroups = new ArrayList<>();
		issueTypeProjectWiseDc.forEach((issueType, projectWiseDc) -> {
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
	 * This method populates KPI value to sprint leaf nodes. It also gives the trend
	 * analysis at sprint wise.
	 *
	 * @param mapTmp
	 *            node id map
	 * @param sprintLeafNodeList
	 *            sprint nodes list
	 * @param trendValueList
	 *            list to hold trend nodes data
	 * @param kpiElement
	 *            the KpiElement
	 * @param kpiRequest
	 *            the KpiRequest
	 */
	@SuppressWarnings(UNCHECKED)
	private void sprintWiseLeafNodeValue(Map<String, Node> mapTmp, List<Node> sprintLeafNodeList,
			List<DataCount> trendValueList, KpiElement kpiElement, KpiRequest kpiRequest) {

		String requestTrackerId = getRequestTrackerId();
		sprintLeafNodeList.sort(Comparator.comparing(node -> node.getSprintFilter().getStartDate()));

		Map<String, Object> resultListMap = fetchKPIDataFromDb(sprintLeafNodeList, null, null, kpiRequest);
		List<JiraIssue> storyList = (List<JiraIssue>) resultListMap.get(STORY_LIST);
		List<JiraIssue> totalDefectList = (List<JiraIssue>) resultListMap.get(TOTAL_DEFECT_DATA);
		List<JiraIssue> totalSubtaskDefectList = (List<JiraIssue>) resultListMap.get(SPRINT_SUBTASK_DEFECTS);
		List<JiraIssueCustomHistory> defectHistoryList = (List<JiraIssueCustomHistory>) resultListMap
				.get(DEFECT_HISTORY);
		List<SprintDetails> sprintDetails = (List<SprintDetails>) resultListMap.get(SPRINT_DETAILS);

		Map<Pair<String, String>, Map<String, Long>> sprintWiseDCReopenedDefectsPriorityMap = new HashMap<>();
		Map<Pair<String, String>, Map<String, Long>> sprintWiseDCCompletedDefectsPriorityMap = new HashMap<>();
		Map<Pair<String, String>, List<JiraIssue>> sprintWiseReOpenedDefectListMap = new HashMap<>();
		Map<Pair<String, String>, List<JiraIssue>> sprintWiseCompletedDefectListMap = new HashMap<>();

		List<KPIExcelData> excelData = new ArrayList<>();
		Set<String> projectWisePriorityList = new HashSet<>();
		Map<String, ReopenedDefectInfo> reopenedDefectInfoMap = new HashMap<>();
		sprintDetails.forEach(sd -> {
			List<String> completedSprintIssues = KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(sd,
					CommonConstant.COMPLETED_ISSUES);

			FieldMapping fieldMapping = configHelperService.getFieldMapping(sd.getBasicProjectConfigId());
			// For finding the completed Defect we are taking combination of DodStatus &
			// DefectRejectionStatus
			List<String> dodStatus = Optional.ofNullable(fieldMapping)
					.map(FieldMapping::getJiraDefectClosedStatusKPI190).orElse(Collections.emptyList()).stream()
					.map(String::toLowerCase).toList();
			String defectRejectionStatus = Optional.ofNullable(fieldMapping)
					.map(FieldMapping::getJiraDefectRejectionStatusKPI190).orElse("");
			List<String> dodAndDefectRejStatus = new ArrayList<>(dodStatus);
			if (StringUtils.isNotEmpty(defectRejectionStatus))
				dodAndDefectRejStatus.add(defectRejectionStatus.toLowerCase());

			List<JiraIssue> sprintSubtask = KpiDataHelper.getTotalSprintSubTasks(totalSubtaskDefectList.stream()
					.filter(jiraIssue -> CollectionUtils.isNotEmpty(jiraIssue.getSprintIdList())
							&& jiraIssue.getSprintIdList().contains(sd.getSprintID().split("_")[0]))
					.collect(Collectors.toList()), sd, defectHistoryList, dodAndDefectRejStatus);

			List<JiraIssue> sprintReOpenedDefects = KpiHelperService.getSprintReOpenedDefects(sd, defectHistoryList,
					dodStatus, totalDefectList, reopenedDefectInfoMap);

			List<JiraIssue> sprintCompletedDefects = totalDefectList.stream()
					.filter(element -> completedSprintIssues.contains(element.getNumber()))
					.filter(element -> dodAndDefectRejStatus.contains(element.getStatus().toLowerCase()))
					.collect(Collectors.toList());

			sprintCompletedDefects.addAll(KpiDataHelper.getCompletedSubTasksByHistory(sprintSubtask, defectHistoryList,
					sd, dodAndDefectRejStatus, new HashMap<>()));

			Pair<String, String> sprintFilter = Pair.of(sd.getBasicProjectConfigId().toString(), sd.getSprintID());

			sprintWiseReOpenedDefectListMap.put(sprintFilter, sprintReOpenedDefects);
			sprintWiseCompletedDefectListMap.put(sprintFilter, sprintCompletedDefects);

			Map<String, Long> priorityCountMapReopenedDefects = KPIHelperUtil.setpriorityScrum(sprintReOpenedDefects,
					customApiConfig);
			Map<String, Long> priorityCountMapCompletedDefects = KPIHelperUtil.setpriorityScrum(sprintCompletedDefects,
					customApiConfig);
			projectWisePriorityList.addAll(priorityCountMapReopenedDefects.keySet());
			projectWisePriorityList.addAll(priorityCountMapCompletedDefects.keySet());
			sprintWiseDCReopenedDefectsPriorityMap.put(sprintFilter, priorityCountMapReopenedDefects);
			sprintWiseDCCompletedDefectsPriorityMap.put(sprintFilter, priorityCountMapCompletedDefects);
		});

		sprintLeafNodeList.forEach(node -> {
			String trendLineName = node.getProjectFilter().getName();
			Pair<String, String> currentNodeIdentifier = Pair
					.of(node.getProjectFilter().getBasicProjectConfigId().toString(), node.getSprintFilter().getId());

			Map<String, List<DataCount>> dataCountMap = new HashMap<>();
			Map<String, Long> reopenedDefectsPriorityMap = sprintWiseDCReopenedDefectsPriorityMap
					.getOrDefault(currentNodeIdentifier, new HashMap<>());
			Map<String, Long> completedDefectsPriorityMap = sprintWiseDCCompletedDefectsPriorityMap
					.getOrDefault(currentNodeIdentifier, new HashMap<>());
			Map<String, Long> reopenedFinalMap = new HashMap<>();
			Map<String, Long> completedFinalMap = new HashMap<>();
			Map<String, Object> reopenedOverAllHoverValueMap = new HashMap<>();
			Map<String, Object> completedOverAllHoverValueMap = new HashMap<>();
			if (CollectionUtils.isNotEmpty(projectWisePriorityList)) {
				projectWisePriorityList.forEach(priority -> {
					Long reopenedWiseCount = reopenedDefectsPriorityMap.getOrDefault(priority, 0L);
					Long completedWiseCount = completedDefectsPriorityMap.getOrDefault(priority, 0L);
					reopenedFinalMap.put(StringUtils.capitalize(priority), reopenedWiseCount);
					completedFinalMap.put(StringUtils.capitalize(priority), completedWiseCount);
					reopenedOverAllHoverValueMap.put(StringUtils.capitalize(priority), reopenedWiseCount.intValue());
					completedOverAllHoverValueMap.put(StringUtils.capitalize(priority), completedWiseCount.intValue());
				});
				projectWisePriorityList.forEach(priority -> reopenedFinalMap.computeIfAbsent(priority, val -> 0L));
				projectWisePriorityList.forEach(priority -> completedFinalMap.computeIfAbsent(priority, val -> 0L));
				Long reopenedOverAllCount = reopenedFinalMap.values().stream().mapToLong(val -> val).sum();
				Long completedOverAllCount = completedFinalMap.values().stream().mapToLong(val -> val).sum();
				reopenedFinalMap.put(CommonConstant.OVERALL, reopenedOverAllCount);
				completedFinalMap.put(CommonConstant.OVERALL, completedOverAllCount);
				projectWisePriorityList.add(CommonConstant.OVERALL);
				projectWisePriorityList.forEach((priority) -> {
					DataCount dataCount = getDataCountObject(node, trendLineName, StringUtils.capitalize(priority),
							reopenedFinalMap, completedFinalMap);
					trendValueList.add(dataCount);
					dataCountMap.computeIfAbsent(priority, k -> new ArrayList<>()).add(dataCount);
				});

				populateExcelDataObject(requestTrackerId, node.getSprintFilter().getName(), excelData,
						sprintWiseReOpenedDefectListMap.get(currentNodeIdentifier), reopenedDefectInfoMap,
						customApiConfig, storyList);
			}
			mapTmp.get(node.getId()).setValue(dataCountMap);
		});

		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(KPIExcelColumn.DEFECT_REOPEN_RATE_QUALITY.getColumns(sprintLeafNodeList,
				cacheService, filterHelperService));
	}

	/** {@inheritDoc} */
	@Override
	public Map<String, Object> fetchKPIDataFromDb(List<Node> leafNodeList, String startDate, String endDate,
			KpiRequest kpiRequest) {
		Map<String, Object> resultListMap = new HashMap<>();
		Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();
		List<String> sprintList = new ArrayList<>();
		List<String> basicProjectConfigIds = new ArrayList<>();
		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
		Map<String, Map<String, List<String>>> droppedDefectMap = new HashMap<>();
		List<String> defectType = new ArrayList<>();

		leafNodeList.forEach(leaf -> {
			ObjectId basicProjectConfigId = leaf.getProjectFilter().getBasicProjectConfigId();
			Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();

			FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigId);
			sprintList.add(leaf.getSprintFilter().getId());
			basicProjectConfigIds.add(basicProjectConfigId.toString());
			defectType.add(NormalizedJira.DEFECT_TYPE.getValue());
			KpiHelperService.getDroppedDefectsFilters(droppedDefectMap, basicProjectConfigId,
					fieldMapping.getResolutionTypeForRejectionKPI190(),
					fieldMapping.getJiraDefectRejectionStatusKPI190());
			mapOfProjectFilters.put(JiraFeature.ISSUE_TYPE.getFieldValueInFeature(),
					CommonUtils.convertToPatternList(defectType));
			uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);
		});

		List<SprintDetails> sprintDetails = sprintRepository.findBySprintIDIn(sprintList);

		Set<String> totalSprintReportStories = new HashSet<>();
		Set<String> totalIssue = new HashSet<>();
		Set<String> totalIssueInSprint = new HashSet<>();
		sprintDetails.forEach(sprintDetail -> {
			if (CollectionUtils.isNotEmpty(sprintDetail.getTotalIssues())) {
				FieldMapping fieldMapping = configHelperService.getFieldMapping(sprintDetail.getBasicProjectConfigId());
				totalSprintReportStories.addAll(sprintDetail.getTotalIssues().stream()
						.filter(sprintIssue -> !fieldMapping.getJiradefecttype().contains(sprintIssue.getTypeName()))
						.map(SprintIssue::getNumber).collect(Collectors.toSet()));
				totalIssue.addAll(KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(sprintDetail,
						CommonConstant.TOTAL_ISSUES));
			}
			totalIssueInSprint.addAll(KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(sprintDetail,
					CommonConstant.TOTAL_ISSUES));
			totalIssueInSprint.addAll(KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(sprintDetail,
					CommonConstant.COMPLETED_ISSUES_ANOTHER_SPRINT));
			totalIssueInSprint.addAll(KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(sprintDetail,
					CommonConstant.PUNTED_ISSUES));
			totalIssueInSprint.addAll(KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(sprintDetail,
					CommonConstant.ADDED_ISSUES));

		});

		/* * additional filter **/
		KpiDataHelper.createAdditionalFilterMap(kpiRequest, mapOfFilters, Constant.SCRUM, DEV, filterHelperService);

		mapOfFilters.put(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				basicProjectConfigIds.stream().distinct().collect(Collectors.toList()));
		List<JiraIssue> totalDefectList = new ArrayList<>();
		List<JiraIssue> defectListWoDrop = new ArrayList<>();

		if (CollectionUtils.isNotEmpty(totalIssue)) {
			List<JiraIssue> totalSprintReportDefects = jiraIssueRepository.findIssueByNumber(mapOfFilters, totalIssue,
					uniqueProjectMap);

			List<JiraIssue> totalSubTaskDefects = jiraIssueRepository
					.findLinkedDefects(mapOfFilters, totalSprintReportStories, uniqueProjectMap).stream()
					.filter(jiraIssue -> !totalIssueInSprint.contains(jiraIssue.getNumber()))
					.collect(Collectors.toList());

			totalDefectList.addAll(totalSprintReportDefects);
			totalDefectList.addAll(totalSubTaskDefects);
			KpiHelperService.getDefectsWithoutDrop(droppedDefectMap, totalDefectList, defectListWoDrop);
			List<JiraIssueCustomHistory> defectsCustomHistoryList = jiraIssueCustomHistoryRepository
					.findByStoryIDInAndBasicProjectConfigIdIn(
							defectListWoDrop.stream().map(JiraIssue::getNumber).toList(),
							basicProjectConfigIds.stream().distinct().collect(Collectors.toList()));

			resultListMap.put(SPRINT_SUBTASK_DEFECTS, totalSubTaskDefects);
			resultListMap.put(DEFECT_HISTORY, defectsCustomHistoryList);
			resultListMap.put(SPRINT_DETAILS, sprintDetails);
			resultListMap.put(STORY_LIST, jiraIssueRepository.findIssueAndDescByNumber(new ArrayList<>(totalIssue)));
		}
		resultListMap.put(TOTAL_DEFECT_DATA, defectListWoDrop);
		return resultListMap;
	}

	private DataCount getDataCountObject(Node node, String trendLineName, String priority,
			Map<String, Long> reopenedFinalMap, Map<String, Long> completedFinalMap) {
		DataCount dataCount = new DataCount();
		double value = (double) Math.round(
				(100.0 * reopenedFinalMap.getOrDefault(priority, 0L)) / (completedFinalMap.getOrDefault(priority, 0L)));
		dataCount.setData(String.valueOf(value));
		dataCount.setSProjectName(trendLineName);
		dataCount.setSSprintID(node.getSprintFilter().getId());
		dataCount.setSSprintName(node.getSprintFilter().getName());
		dataCount.setValue(value);
		dataCount.setKpiGroup(priority);
		Map<String, Object> hoverValueMap = new LinkedHashMap<>();
		hoverValueMap.put(REOPENED_DEFECTS, reopenedFinalMap.getOrDefault(priority, 0L));
		hoverValueMap.put(COMPLETED_DEFECTS, completedFinalMap.getOrDefault(priority, 0L));
		dataCount.setHoverValue(hoverValueMap);
		return dataCount;
	}

	private void populateExcelDataObject(String requestTrackerId, String sprintName, List<KPIExcelData> excelData,
			List<JiraIssue> sprintWiseDefectDataList, Map<String, ReopenedDefectInfo> reopenedDefectInfoMap,
			CustomApiConfig customApiConfig, List<JiraIssue> storyList) {

		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			KPIExcelUtility.populateDefectWithReopenInfoExcelData(sprintName, sprintWiseDefectDataList, excelData,
					customApiConfig, storyList, reopenedDefectInfoMap);
		}
	}

	private Map<String, List<DataCount>> sortTrendValueMap(Map<String, List<DataCount>> trendMap,
			List<String> keyOrder) {
		Map<String, List<DataCount>> sortedMap = new LinkedHashMap<>();
		keyOrder.forEach(order -> {
			if (null != trendMap.get(order)) {
				sortedMap.put(order, trendMap.get(order));
			}
		});
		return sortedMap;
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
		return calculateThresholdValue(fieldMapping.getThresholdValueKPI190(),
				KPICode.DEFECT_REOPEN_RATE_QUALITY.getKpiId());
	}

}
