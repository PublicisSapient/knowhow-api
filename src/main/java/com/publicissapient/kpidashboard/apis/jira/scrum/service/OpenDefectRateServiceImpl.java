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

import static com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService.getDefectsWithoutDrop;
import static com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService.getDroppedDefectsFilters;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
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
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.NormalizedJira;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * This class fetches the open defect count KPI along with trend analysis. Trend analysis for Open
 * Defect Count KPI has total defect count at y-axis and sprint id at x-axis. {@link JiraKPIService}
 *
 * @author aksshriv1
 */
@Component
@Slf4j
public class OpenDefectRateServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String OPEN_DEFECT_DATA = "openBugKey";
	private static final String TOTAL_DEFECT_DATA = "totalBugKey";
	private static final String TOTAL = "Total Defects";
	private static final String DEV = "DeveloperKpi";
	public static final String TOTAL_DEFECTS = "totalDefects";
	public static final String DEFECT_HISTORY = "defectHistory";
	public static final String SPRINT_DETAILS = "sprintDetails";
	public static final String SUB_TASK_BUGS = "subTaskBugs";
	public static final String JIRA_ISSUE_CLOSED_DATE = "jiraIssueClosedDate";
	public static final String STORY_LIST = "storyList";
	public static final String OPEN_DEFECTS = "Open Defects";

	@Autowired private CacheService cacheService;
	@Autowired private CustomApiConfig customApiConfig;
	@Autowired private ConfigHelperService configHelperService;
	@Autowired private JiraIssueRepository jiraIssueRepository;
	@Autowired private FilterHelperService flterHelperService;
	@Autowired private SprintRepository sprintRepository;
	@Autowired private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

	@Override
	public String getQualifierType() {
		return KPICode.OPEN_DEFECT_RATE.name();
	}

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
				"[ODR-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				root);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValue(root, nodeWiseKPIValue, KPICode.OPEN_DEFECT_RATE);
		List<DataCount> trendValues =
				getTrendValues(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.OPEN_DEFECT_RATE);

		kpiElement.setTrendValueList(trendValues);
		return kpiElement;
	}

	/**
	 * This method populates KPI value to sprint leaf nodes. It also gives the trend analysis at
	 * sprint wise.
	 *
	 * @param mapTmp
	 * @param sprintLeafNodeList
	 * @param trendValueList
	 * @param kpiElement
	 * @param kpiRequest
	 */
	@SuppressWarnings("unchecked")
	private void sprintWiseLeafNodeValue(
			Map<String, Node> mapTmp,
			List<Node> sprintLeafNodeList,
			List<DataCount> trendValueList,
			KpiElement kpiElement,
			KpiRequest kpiRequest) {

		String requestTrackerId = getRequestTrackerId();

		sprintLeafNodeList.sort(
				(node1, node2) ->
						node1
								.getSprintFilter()
								.getStartDate()
								.compareTo(node2.getSprintFilter().getStartDate()));
		String startDate = sprintLeafNodeList.get(0).getSprintFilter().getStartDate();
		String endDate =
				sprintLeafNodeList.get(sprintLeafNodeList.size() - 1).getSprintFilter().getEndDate();

		Map<String, Object> storyDefectDataListMap =
				fetchKPIDataFromDb(sprintLeafNodeList, startDate, endDate, kpiRequest);

		List<JiraIssue> totalDefects = (List<JiraIssue>) storyDefectDataListMap.get(TOTAL_DEFECTS);
		List<JiraIssue> subTaskBugs = (List<JiraIssue>) storyDefectDataListMap.get(SUB_TASK_BUGS);
		List<JiraIssue> storyList = (List<JiraIssue>) storyDefectDataListMap.get(STORY_LIST);
		List<JiraIssueCustomHistory> defectsCustomHistory =
				(List<JiraIssueCustomHistory>) storyDefectDataListMap.get(DEFECT_HISTORY);
		List<SprintDetails> sprintDetails =
				(List<SprintDetails>) storyDefectDataListMap.get(SPRINT_DETAILS);

		Map<Pair<String, String>, Double> sprintWiseODRMap = new HashMap<>();
		Map<Pair<String, String>, Map<String, Object>> sprintWiseHowerMap = new HashMap<>();
		Map<Pair<String, String>, List<JiraIssue>> sprintWiseTotaldDefectListMap = new HashMap<>();
		Map<Pair<String, String>, List<JiraIssue>> sprintWiseOpendDefectListMap = new HashMap<>();
		List<KPIExcelData> excelData = new ArrayList<>();
		if (CollectionUtils.isNotEmpty(sprintDetails)) {

			sprintDetails.forEach(
					sd -> {
						List<JiraIssue> sprintWiseOpenDefectList = new ArrayList<>();
						List<JiraIssue> sprintWiseTotaldDefectList = new ArrayList<>();

						Map<String, Object> subCategoryWiseOpenAndTotalDefectList = new HashMap<>();

						List<String> notCompletedSprintIssues =
								KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(
										sd, CommonConstant.NOT_COMPLETED_ISSUES);
						Set<JiraIssue> totalSubTask = new HashSet<>();
						getSubtasks(subTaskBugs, totalSubTask, sd);
						List<String> totalIssues =
								new ArrayList<>(
										KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(
												sd, CommonConstant.TOTAL_ISSUES));
						List<JiraIssue> subCategoryWiseTotalDefectList =
								totalDefects.stream()
										.filter(f -> totalIssues.contains(f.getNumber()))
										.collect(Collectors.toList());
						subCategoryWiseTotalDefectList.addAll(totalSubTask);

						List<JiraIssue> subCategoryWiseOpenDefectList =
								totalDefects.stream()
										.filter(f -> notCompletedSprintIssues.contains(f.getNumber()))
										.collect(Collectors.toList());

						subCategoryWiseOpenDefectList.addAll(
								getNotCompletedSubTasksByHistory(totalSubTask, defectsCustomHistory, sd));

						double odrForCurrentLeaf = 0.0d;
						subCategoryWiseOpenAndTotalDefectList.put(
								OPEN_DEFECT_DATA, subCategoryWiseOpenDefectList);
						subCategoryWiseOpenAndTotalDefectList.put(
								TOTAL_DEFECT_DATA, subCategoryWiseTotalDefectList);
						if (CollectionUtils.isNotEmpty(subCategoryWiseOpenDefectList)
								&& CollectionUtils.isNotEmpty(subCategoryWiseTotalDefectList)) {

							odrForCurrentLeaf = calculateKPIMetrics(subCategoryWiseOpenAndTotalDefectList);

						} else if (CollectionUtils.isEmpty(subCategoryWiseTotalDefectList)) {

							odrForCurrentLeaf = 0.0d;
						}

						sprintWiseOpenDefectList.addAll(subCategoryWiseOpenDefectList);
						sprintWiseTotaldDefectList.addAll(subCategoryWiseTotalDefectList);
						Pair<String, String> sprint =
								Pair.of(sd.getBasicProjectConfigId().toString(), sd.getSprintID());
						sprintWiseOpendDefectListMap.put(sprint, sprintWiseOpenDefectList);
						sprintWiseTotaldDefectListMap.put(sprint, sprintWiseTotaldDefectList);
						sprintWiseODRMap.put(sprint, odrForCurrentLeaf);

						setHowerMap(
								sprintWiseHowerMap,
								sprint,
								subCategoryWiseOpenDefectList,
								sprintWiseTotaldDefectList);
					});
		}

		sprintLeafNodeList.forEach(
				node -> {
					String trendLineName = node.getProjectFilter().getName();

					Pair<String, String> currentNodeIdentifier =
							Pair.of(
									node.getProjectFilter().getBasicProjectConfigId().toString(),
									node.getSprintFilter().getId());

					double odrForCurrentLeaf;
					if (sprintWiseODRMap.containsKey(currentNodeIdentifier)) {

						odrForCurrentLeaf = sprintWiseODRMap.get(currentNodeIdentifier);
						List<JiraIssue> sprintWiseOpenDefectList =
								sprintWiseOpendDefectListMap.get(currentNodeIdentifier);
						List<JiraIssue> sprintWiseTotaldDefectList =
								sprintWiseTotaldDefectListMap.get(currentNodeIdentifier);
						populateExcelDataObject(
								requestTrackerId,
								node.getSprintFilter().getName(),
								excelData,
								sprintWiseOpenDefectList,
								sprintWiseTotaldDefectList,
								storyList);
					} else {
						odrForCurrentLeaf = 0.0d;
					}
					log.debug(
							"[ODR-SPRINT-WISE][{}]. ODR for sprint {}  is {}",
							requestTrackerId,
							node.getSprintFilter().getName(),
							odrForCurrentLeaf);

					DataCount dataCount = new DataCount();
					dataCount.setData(String.valueOf(Math.round(odrForCurrentLeaf)));
					dataCount.setSProjectName(trendLineName);
					dataCount.setSSprintID(node.getSprintFilter().getId());
					dataCount.setSSprintName(node.getSprintFilter().getName());
					dataCount.setSprintIds(new ArrayList<>(Arrays.asList(node.getSprintFilter().getId())));
					dataCount.setSprintNames(
							new ArrayList<>(Arrays.asList(node.getSprintFilter().getName())));
					dataCount.setValue(odrForCurrentLeaf);
					dataCount.setHoverValue(sprintWiseHowerMap.get(currentNodeIdentifier));
					mapTmp.get(node.getId()).setValue(new ArrayList<>(Arrays.asList(dataCount)));
					trendValueList.add(dataCount);
				});

		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(
				KPIExcelColumn.OPEN_DEFECT_RATE.getColumns(
						sprintLeafNodeList, cacheService, flterHelperService));
	}

	/**
	 * Sets map to show on hover of sprint node.
	 *
	 * @param sprintWiseHowerMap
	 * @param sprint
	 * @param open
	 * @param total
	 */
	private void setHowerMap(
			Map<Pair<String, String>, Map<String, Object>> sprintWiseHowerMap,
			Pair<String, String> sprint,
			List<JiraIssue> open,
			List<JiraIssue> total) {
		Map<String, Object> howerMap = new LinkedHashMap<>();
		if (CollectionUtils.isNotEmpty(open)) {
			howerMap.put(OPEN_DEFECTS, open.size());
		} else {
			howerMap.put(OPEN_DEFECTS, 0);
		}
		if (CollectionUtils.isNotEmpty(total)) {
			howerMap.put(TOTAL, total.size());
		} else {
			howerMap.put(TOTAL, 0);
		}
		sprintWiseHowerMap.put(sprint, howerMap);
	}

	private void populateExcelDataObject(
			String requestTrackerId,
			String sprintName,
			List<KPIExcelData> excelData,
			List<JiraIssue> sprintWiseOpenDefectList,
			List<JiraIssue> sprintWiseTotaldDefectList,
			List<JiraIssue> storyList) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {

			Map<String, JiraIssue> totalDefectList = new HashMap<>();
			sprintWiseTotaldDefectList.forEach(
					bugs -> totalDefectList.putIfAbsent(bugs.getNumber(), bugs));

			KPIExcelUtility.populateDefectRelatedExcelData(
					sprintName,
					totalDefectList,
					sprintWiseOpenDefectList,
					excelData,
					KPICode.OPEN_DEFECT_RATE.getKpiId(),
					customApiConfig,
					storyList);
		}
	}

	public List<JiraIssue> getNotCompletedSubTasksByHistory(
			Set<JiraIssue> totalSubTask,
			List<JiraIssueCustomHistory> subTaskHistory,
			SprintDetails sprintDetail) {
		List<JiraIssue> openSubtaskOfSprint = new ArrayList<>();
		LocalDateTime sprintEndDate =
				KpiHelperService.getParseDateFromSprint(
						sprintDetail.getCompleteDate(), sprintDetail.getEndDate());
		LocalDateTime sprintStartDate =
				KpiHelperService.getParseDateFromSprint(
						sprintDetail.getActivatedDate(), sprintDetail.getStartDate());

		totalSubTask.forEach(
				jiraIssue -> {
					JiraIssueCustomHistory jiraIssueCustomHistory =
							subTaskHistory.stream()
									.filter(
											issueCustomHistory ->
													issueCustomHistory.getStoryID().equalsIgnoreCase(jiraIssue.getNumber()))
									.findFirst()
									.orElse(new JiraIssueCustomHistory());
					Optional<JiraHistoryChangeLog> issueSprint =
							jiraIssueCustomHistory.getStatusUpdationLog().stream()
									.filter(
											jiraIssueSprint ->
													DateUtil.isWithinDateTimeRange(
															jiraIssueSprint.getUpdatedOn(), sprintStartDate, sprintEndDate))
									.reduce((a, b) -> b);
					if (issueSprint.isPresent()) openSubtaskOfSprint.add(jiraIssue);
				});
		return openSubtaskOfSprint;
	}

	private static void getSubtasks(
			List<JiraIssue> allSubTaskBugs, Set<JiraIssue> totalSubTask, SprintDetails sprintDetail) {
		LocalDateTime sprintEndDate =
				KpiHelperService.getParseDateFromSprint(
						sprintDetail.getCompleteDate(), sprintDetail.getEndDate());

		allSubTaskBugs.forEach(
				jiraIssue -> {
					LocalDateTime jiraCreatedDate =
							DateUtil.convertToUTCLocalDateTime(jiraIssue.getCreatedDate());

					if (CollectionUtils.isNotEmpty(jiraIssue.getSprintIdList())
							&& jiraIssue.getSprintIdList().contains(sprintDetail.getSprintID().split("_")[0])
							&& (sprintEndDate.isAfter(jiraCreatedDate))) {
						totalSubTask.add(jiraIssue);
					}
				});
	}

	@Override
	public Double calculateKPIMetrics(Map<String, Object> openAndTotalDefectDataMap) {
		int openDefectCount =
				((List<JiraIssue>) openAndTotalDefectDataMap.get(OPEN_DEFECT_DATA)).size();
		int totalDefectCount =
				((List<JiraIssue>) openAndTotalDefectDataMap.get(TOTAL_DEFECT_DATA)).size();
		return (double) Math.round((100.0 * openDefectCount) / (totalDefectCount));
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			final List<Node> leafNodeList,
			final String startDate,
			final String endDate,
			final KpiRequest kpiRequest) {

		List<String> sprintList = new ArrayList<>();
		List<String> basicProjectConfigIds = new ArrayList<>();
		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
		Map<String, Map<String, List<String>>> droppedDefects = new HashMap<>();
		Map<String, List<String>> projectWiseDefectClosedStatus = new HashMap<>();
		List<String> defectType = new ArrayList<>();
		leafNodeList.forEach(
				leaf -> {
					ObjectId basicProjectConfigId = leaf.getProjectFilter().getBasicProjectConfigId();
					FieldMapping fieldMapping =
							configHelperService.getFieldMappingMap().get(basicProjectConfigId);
					sprintList.add(leaf.getSprintFilter().getId());
					basicProjectConfigIds.add(basicProjectConfigId.toString());
					Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();
					defectType.add(NormalizedJira.DEFECT_TYPE.getValue());
					mapOfProjectFilters.put(JiraFeature.ISSUE_TYPE.getFieldValueInFeature(), defectType);
					uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);
					projectWiseDefectClosedStatus.put(
							basicProjectConfigId.toString(), fieldMapping.getJiraDefectRemovalStatusKPI191());
					getDroppedDefectsFilters(
							droppedDefects,
							basicProjectConfigId,
							fieldMapping.getResolutionTypeForRejectionKPI191(),
							fieldMapping.getJiraDefectRejectionStatusKPI191());
				});

		List<SprintDetails> sprintDetails = sprintRepository.findBySprintIDIn(sprintList);
		Set<String> totalNonBugIssues = new HashSet<>();
		Set<String> totalIssue = new HashSet<>();
		Set<String> totalIssueInSprint = new HashSet<>();
		sprintDetails.forEach(
				sprintDetail -> {
					if (CollectionUtils.isNotEmpty(sprintDetail.getTotalIssues())) {
						FieldMapping fieldMapping =
								configHelperService.getFieldMapping(sprintDetail.getBasicProjectConfigId());
						totalNonBugIssues.addAll(
								sprintDetail.getTotalIssues().stream()
										.filter(
												sprintIssue ->
														!fieldMapping.getJiradefecttype().contains(sprintIssue.getTypeName()))
										.map(SprintIssue::getNumber)
										.collect(Collectors.toSet()));
						totalIssue.addAll(
								KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(
										sprintDetail, CommonConstant.TOTAL_ISSUES));
					}
					totalIssueInSprint.addAll(
							KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(
									sprintDetail, CommonConstant.TOTAL_ISSUES));
					totalIssueInSprint.addAll(
							KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(
									sprintDetail, CommonConstant.COMPLETED_ISSUES_ANOTHER_SPRINT));
					totalIssueInSprint.addAll(
							KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(
									sprintDetail, CommonConstant.PUNTED_ISSUES));
					totalIssueInSprint.addAll(
							KpiDataHelper.getIssuesIdListBasedOnTypeFromSprintDetails(
									sprintDetail, CommonConstant.ADDED_ISSUES));
				});
		Map<String, Object> resultListMap = new HashMap<>();
		Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();
		/** additional filter * */
		KpiDataHelper.createAdditionalFilterMap(
				kpiRequest, mapOfFilters, Constant.SCRUM, flterHelperService);

		mapOfFilters.put(
				JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
				basicProjectConfigIds.stream().distinct().toList());

		if (CollectionUtils.isNotEmpty(totalIssue)) {
			List<JiraIssue> totalSprintReportDefects =
					jiraIssueRepository.findIssueByNumber(mapOfFilters, totalIssue, uniqueProjectMap);
			List<JiraIssue> storyListWoDrop = new ArrayList<>();
			getDefectsWithoutDrop(droppedDefects, totalSprintReportDefects, storyListWoDrop);
			List<JiraIssue> subTaskBugs =
					jiraIssueRepository
							.findLinkedDefects(mapOfFilters, totalNonBugIssues, uniqueProjectMap)
							.stream()
							.filter(jiraIssue -> !totalIssueInSprint.contains(jiraIssue.getNumber()))
							.toList();
			List<JiraIssue> defectListWoDrop = new ArrayList<>();
			getDefectsWithoutDrop(droppedDefects, subTaskBugs, defectListWoDrop);

			List<JiraIssueCustomHistory> defectsCustomHistory =
					jiraIssueCustomHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(
							subTaskBugs.stream().map(JiraIssue::getNumber).toList(),
							basicProjectConfigIds.stream().distinct().toList());

			resultListMap.put(TOTAL_DEFECTS, storyListWoDrop);
			resultListMap.put(SUB_TASK_BUGS, defectListWoDrop);
			resultListMap.put(DEFECT_HISTORY, defectsCustomHistory);
			resultListMap.put(SPRINT_DETAILS, sprintDetails);
			resultListMap.put(
					STORY_LIST, jiraIssueRepository.findIssueAndDescByNumber(new ArrayList<>(totalIssue)));
		}

		return resultListMap;
	}

	@Override
	public Double calculateKpiValue(List<Double> valueList, String kpiName) {
		return calculateKpiValueForDouble(valueList, kpiName);
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI191(), KPICode.OPEN_DEFECT_RATE.getKpiId());
	}
}
