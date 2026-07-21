/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.quality;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.JiraFeature;
import com.publicissapient.kpidashboard.apis.enums.JiraFeatureHistory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.MeanTimeRecoverData;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MeanTimeToRecoverSlingshotServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String JIRA_HISTORY_DATA = "jiraIssueHistoryData";
	private static final String STORY_ID = "storyID";
	private static final String PRODUCTION_INCIDENT_MTTR_SLINGSHOT =
			"productionIncidentMttrSlingshot";
	private static final int DEFAULT_WEEK_COUNT = 12;
	private static final DateTimeFormatter WEEK_LABEL_FORMATTER =
			DateTimeFormatter.ofPattern(DateUtil.DISPLAY_DATE_FORMAT, Locale.ENGLISH);

	@Autowired private ConfigHelperService configHelperService;
	@Autowired private JiraIssueRepository jiraIssueRepository;
	@Autowired private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

	@Override
	public String getQualifierType() {
		return KPICode.MEAN_TIME_TO_RECOVER_SLINGSHOT.name();
	}

	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {
		Node root = treeAggregatorDetail.getRoot();
		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();

		List<Node> projectList =
				treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT);
		calculateProjectWiseLeafNodeValue(mapTmp, projectList, kpiElement);

		log.debug(
				"[MEAN-TIME-TO-RECOVER-SLINGSHOT-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				root);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValue(root, nodeWiseKPIValue, KPICode.MEAN_TIME_TO_RECOVER_SLINGSHOT);
		List<DataCount> trendValues =
				getAggregateTrendValues(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.MEAN_TIME_TO_RECOVER_SLINGSHOT);
		kpiElement.setTrendValueList(trendValues);
		return kpiElement;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		List<String> allProjectIds = new ArrayList<>();
		List<String> labelsModeProjectIds = new ArrayList<>();
		List<String> customFieldModeProjectIds = new ArrayList<>();
		Set<String> allLabelValues = new LinkedHashSet<>();
		Set<String> allIssueTypes = new LinkedHashSet<>();
		Set<String> allDodStatuses = new LinkedHashSet<>();
		Map<String, Object> resultListMap = new HashMap<>();
		Map<String, List<String>> mapOfFiltersFH = new LinkedHashMap<>();
		Map<String, Map<String, Object>> uniqueProjectMapFH = new HashMap<>();

		leafNodeList.forEach(
				leafNode -> {
					ObjectId basicProjectConfigId = leafNode.getProjectFilter().getBasicProjectConfigId();
					FieldMapping fieldMapping =
							configHelperService.getFieldMappingMap().get(basicProjectConfigId);
					String projectId = basicProjectConfigId.toString();

					allProjectIds.add(projectId);

					String incidentMode =
							ObjectUtils.defaultIfNull(
									fieldMapping.getJiraProductionIncidentIdentificationKPI217(), "");
					if (incidentMode.equalsIgnoreCase(CommonConstant.LABELS)
							&& CollectionUtils.isNotEmpty(fieldMapping.getJiraProdIncidentRaisedByValue())) {
						labelsModeProjectIds.add(projectId);
						allLabelValues.addAll(fieldMapping.getJiraProdIncidentRaisedByValue());
					} else if (incidentMode.equalsIgnoreCase(CommonConstant.CUSTOM_FIELD)) {
						customFieldModeProjectIds.add(projectId);
					}

					if (CollectionUtils.isNotEmpty(fieldMapping.getJiraStoryIdentificationKPI217())) {
						allIssueTypes.addAll(fieldMapping.getJiraStoryIdentificationKPI217());
					}
					if (CollectionUtils.isNotEmpty(fieldMapping.getJiraDodKPI217())) {
						allDodStatuses.addAll(fieldMapping.getJiraDodKPI217());
					}

					Map<String, Object> mapOfProjectFiltersFH = new LinkedHashMap<>();
					if (CollectionUtils.isNotEmpty(fieldMapping.getJiraStoryIdentificationKPI217())) {
						mapOfProjectFiltersFH.put(
								JiraFeatureHistory.STORY_TYPE.getFieldValueInFeature(),
								CommonUtils.convertToPatternList(fieldMapping.getJiraStoryIdentificationKPI217()));
					}
					uniqueProjectMapFH.put(projectId, mapOfProjectFiltersFH);
				});

		List<String> distinctProjectIds =
				allProjectIds.stream().distinct().collect(Collectors.toList());
		mapOfFiltersFH.put(
				JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(), distinctProjectIds);

		boolean hasAnyConfig =
				!labelsModeProjectIds.isEmpty()
						|| !customFieldModeProjectIds.isEmpty()
						|| !allIssueTypes.isEmpty()
						|| !allDodStatuses.isEmpty();

		if (!hasAnyConfig) {
			resultListMap.put(JIRA_HISTORY_DATA, new ArrayList<>());
			return resultListMap;
		}

		List<JiraIssue> jiraIssueList = new ArrayList<>();

		// Labels mode — direct query on jira_issue.labels, no processor dependency
		if (!labelsModeProjectIds.isEmpty()) {
			Map<String, List<String>> filters =
					buildCommonFilters(labelsModeProjectIds, allIssueTypes, allDodStatuses);
			filters.put(JiraFeature.LABELS.getFieldValueInFeature(), new ArrayList<>(allLabelValues));
			jiraIssueList.addAll(
					jiraIssueRepository.findIssuesByDateAndFilters(filters, startDate, endDate));
		}

		// CustomField mode — processor stamps productionIncidentMttrSlingshot on
		// matching issues
		if (!customFieldModeProjectIds.isEmpty()) {
			Map<String, List<String>> filters =
					buildCommonFilters(customFieldModeProjectIds, allIssueTypes, allDodStatuses);
			jiraIssueList.addAll(
					jiraIssueRepository.findIssuesWithBoolean(
							filters, PRODUCTION_INCIDENT_MTTR_SLINGSHOT, Boolean.TRUE, startDate, endDate));
		}

		// No identification mode — issue type and/or DoD status are the only filters
		if (labelsModeProjectIds.isEmpty() && customFieldModeProjectIds.isEmpty()) {
			Map<String, List<String>> filters =
					buildCommonFilters(distinctProjectIds, allIssueTypes, allDodStatuses);
			jiraIssueList.addAll(
					jiraIssueRepository.findIssuesByDateAndFilters(filters, startDate, endDate));
		}

		List<JiraIssueCustomHistory> historyDataList = new ArrayList<>();
		if (CollectionUtils.isNotEmpty(jiraIssueList)) {
			List<String> issueIdList =
					jiraIssueList.stream().map(JiraIssue::getNumber).distinct().collect(Collectors.toList());
			mapOfFiltersFH.put(STORY_ID, issueIdList);
			historyDataList =
					jiraIssueCustomHistoryRepository.findIssuesByCreatedDateAndType(
							mapOfFiltersFH, uniqueProjectMapFH, startDate, endDate);
		}
		resultListMap.put(JIRA_HISTORY_DATA, historyDataList);
		return resultListMap;
	}

	private Map<String, List<String>> buildCommonFilters(
			List<String> projectIds, Set<String> issueTypes, Set<String> dodStatuses) {
		Map<String, List<String>> filters = new LinkedHashMap<>();
		filters.put(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(), projectIds);
		if (!issueTypes.isEmpty()) {
			filters.put(JiraFeature.ISSUE_TYPE.getFieldValueInFeature(), new ArrayList<>(issueTypes));
		}
		if (!dodStatuses.isEmpty()) {
			filters.put(
					JiraFeature.JIRA_ISSUE_STATUS.getFieldValueInFeature(), new ArrayList<>(dodStatuses));
		}
		return filters;
	}

	@Override
	public Double calculateKPIMetrics(Map<String, Object> stringObjectMap) {
		return null;
	}

	@Override
	public Double calculateKpiValue(List<Double> valueList, String kpiId) {
		return calculateKpiValueForDouble(valueList, kpiId);
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI217(), KPICode.MEAN_TIME_TO_RECOVER_SLINGSHOT.getKpiId());
	}

	@SuppressWarnings("unchecked")
	private void calculateProjectWiseLeafNodeValue(
			Map<String, Node> mapTmp, List<Node> projectLeafNodeList, KpiElement kpiElement) {
		Map<String, Object> durationFilter = KpiDataHelper.getDurationFilter(kpiElement);
		String weekOrMonthDefault =
				(String) durationFilter.getOrDefault(Constant.DURATION, CommonConstant.WEEK);
		// Determine count: if the caller explicitly set filterDuration use that value,
		// otherwise default to DEFAULT_WEEK_COUNT (12) rather than the shared helper's
		// 8.
		LinkedHashMap<?, ?> filterDurationRaw = (LinkedHashMap<?, ?>) kpiElement.getFilterDuration();
		int previousTimeCountForDb =
				filterDurationRaw != null
						? (int) durationFilter.getOrDefault(Constant.COUNT, DEFAULT_WEEK_COUNT)
						: DEFAULT_WEEK_COUNT;

		LocalDateTime localStartDate =
				weekOrMonthDefault.equalsIgnoreCase(CommonConstant.WEEK)
						? DateUtil.getTodayTime().minusWeeks(previousTimeCountForDb)
						: DateUtil.getTodayTime().minusMonths(previousTimeCountForDb);
		String startDate = localStartDate.toLocalDate().toString();
		String endDate = DateUtil.getTodayDate().toString();

		List<KPIExcelData> excelData = new ArrayList<>();
		Map<String, Object> resultMap =
				fetchKPIDataFromDb(projectLeafNodeList, startDate, endDate, null);

		if (MapUtils.isNotEmpty(resultMap)) {
			String requestTrackerId = getRequestTrackerId();

			List<JiraIssueCustomHistory> historyDataList =
					(List<JiraIssueCustomHistory>) resultMap.get(JIRA_HISTORY_DATA);

			Map<String, List<JiraIssueCustomHistory>> projectWiseJiraIssueHistoryDataList =
					historyDataList.stream()
							.collect(Collectors.groupingBy(JiraIssueCustomHistory::getBasicProjectConfigId));

			projectLeafNodeList.forEach(
					node -> {
						String trendLineName = node.getProjectFilter().getName();
						String basicProjectConfigId =
								node.getProjectFilter().getBasicProjectConfigId().toString();
						FieldMapping fieldMapping =
								configHelperService
										.getFieldMappingMap()
										.get(node.getProjectFilter().getBasicProjectConfigId());
						List<JiraIssueCustomHistory> jiraIssueHistoryDataList =
								projectWiseJiraIssueHistoryDataList.get(basicProjectConfigId);

						String weekOrMonth =
								(String) durationFilter.getOrDefault(Constant.DURATION, CommonConstant.WEEK);
						int previousTimeCount = previousTimeCountForDb;

						Map<String, List<MeanTimeRecoverData>> meanTimeRecoverMapTimeWise =
								weekOrMonth.equalsIgnoreCase(CommonConstant.WEEK)
										? getLastNWeek(previousTimeCount)
										: getLastNMonthCount(previousTimeCount);

						if (CollectionUtils.isNotEmpty(jiraIssueHistoryDataList)) {
							List<DataCount> dataCountList = new ArrayList<>();

							computeMeanTimeToRecover(
									jiraIssueHistoryDataList, weekOrMonth, meanTimeRecoverMapTimeWise, fieldMapping);

							meanTimeRecoverMapTimeWise.forEach(
									(weekOrMonthName, meanTimeRecoverListCurrentTime) -> {
										DataCount dataCount =
												buildDataCount(
														trendLineName, weekOrMonthName, meanTimeRecoverListCurrentTime);
										dataCountList.add(dataCount);
									});

							populateExcelData(
									excelData, requestTrackerId, trendLineName, meanTimeRecoverMapTimeWise);
							mapTmp.get(node.getId()).setValue(dataCountList);
						}
					});
			kpiElement.setExcelData(excelData);
			kpiElement.setExcelColumns(KPIExcelColumn.MEAN_TIME_TO_RECOVER_SLINGSHOT.getColumns());
		}
	}

	private DataCount buildDataCount(
			String trendLineName,
			String weekOrMonthName,
			List<MeanTimeRecoverData> meanTimeRecoverListCurrentTime) {
		List<Double> sortedTimes =
				meanTimeRecoverListCurrentTime.stream()
						.filter(data -> data.getTimeToRecover() != null)
						.map(data -> Double.parseDouble(data.getTimeToRecover()))
						.sorted()
						.collect(Collectors.toList());
		double medianTime = computeMedian(sortedTimes);

		DataCount dataCount = new DataCount();
		dataCount.setSProjectName(trendLineName);
		dataCount.setData(String.valueOf(medianTime));
		dataCount.setDate(weekOrMonthName);
		dataCount.setValue(medianTime);
		Map<String, Object> hoverMap = new HashMap<>();
		hoverMap.put("Issue Count", (long) sortedTimes.size());
		hoverMap.put("Median Time (Hrs)", medianTime);
		dataCount.setHoverValue(hoverMap);
		return dataCount;
	}

	private double computeMedian(List<Double> sortedValues) {
		if (sortedValues.isEmpty()) return 0.0;
		int n = sortedValues.size();
		int mid = n / 2;
		return n % 2 == 0
				? (sortedValues.get(mid - 1) + sortedValues.get(mid)) / 2.0
				: sortedValues.get(mid);
	}

	private void computeMeanTimeToRecover(
			List<JiraIssueCustomHistory> jiraIssueHistoryDataList,
			String weekOrMonth,
			Map<String, List<MeanTimeRecoverData>> meanTimeRecoverMapTimeWise,
			FieldMapping fieldMapping) {
		List<String> jiraDodKPI217 =
				ObjectUtils.defaultIfNull(fieldMapping.getJiraDodKPI217(), new ArrayList<>());
		String storyFirstStatus = ObjectUtils.defaultIfNull(fieldMapping.getStoryFirstStatus(), "");
		List<String> dodStatus =
				jiraDodKPI217.stream().map(String::toLowerCase).collect(Collectors.toList());

		jiraIssueHistoryDataList.forEach(
				jiraIssueHistoryData -> {
					LocalDateTime ticketClosedDate;
					LocalDateTime ticketCreatedDate =
							DateUtil.localDateTimeToUTC(
									DateUtil.convertDateTimeToLocalDateTime(jiraIssueHistoryData.getCreatedDate()));
					Map<String, LocalDateTime> closedStatusDateMap = new HashMap<>();

					jiraIssueHistoryData
							.getStatusUpdationLog()
							.forEach(
									statusChangeLog -> {
										if (CollectionUtils.isNotEmpty(dodStatus)
												&& dodStatus.contains(statusChangeLog.getChangedFrom().toLowerCase())
												&& statusChangeLog.getChangedTo().equalsIgnoreCase(storyFirstStatus)) {
											closedStatusDateMap.clear();
										}
										if (CollectionUtils.isNotEmpty(dodStatus)
												&& dodStatus.contains(statusChangeLog.getChangedTo().toLowerCase())) {
											if (closedStatusDateMap.containsKey(statusChangeLog.getChangedTo())) {
												closedStatusDateMap.clear();
											}
											closedStatusDateMap.put(
													statusChangeLog.getChangedTo(), statusChangeLog.getUpdatedOn());
										}
									});

					ticketClosedDate =
							closedStatusDateMap.values().stream()
									.filter(Objects::nonNull)
									.min(LocalDateTime::compareTo)
									.orElse(null);

					double meanTimeToRecoverInHrs = 0;
					if (ticketClosedDate != null && ticketCreatedDate != null) {
						ticketClosedDate = DateUtil.localDateTimeToUTC(ticketClosedDate);
						meanTimeToRecoverInHrs =
								Math.max(0, Duration.between(ticketCreatedDate, ticketClosedDate).toHours());
					}

					String weekOrMonthName = formatDateLabel(weekOrMonth, ticketCreatedDate);
					setRecoverData(
							meanTimeRecoverMapTimeWise,
							jiraIssueHistoryData,
							ticketClosedDate,
							ticketCreatedDate,
							meanTimeToRecoverInHrs,
							weekOrMonthName);
				});
	}

	private String formatDateLabel(String weekOrMonth, LocalDateTime currentDate) {
		if (weekOrMonth.equalsIgnoreCase(CommonConstant.WEEK)) {
			return buildWeekLabel(currentDate);
		}
		return currentDate.getYear() + Constant.DASH + currentDate.getMonthValue();
	}

	private String buildWeekLabel(LocalDateTime dateTime) {
		LocalDate monday =
				dateTime.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate sunday = dateTime.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
		return monday.format(WEEK_LABEL_FORMATTER) + " to " + sunday.format(WEEK_LABEL_FORMATTER);
	}

	private void setRecoverData(
			Map<String, List<MeanTimeRecoverData>> meanTimeRecoverMapTimeWise,
			JiraIssueCustomHistory jiraIssueHistoryData,
			LocalDateTime ticketClosedDate,
			LocalDateTime ticketCreatedDate,
			double meanTimeToRecoverInHrs,
			String weekOrMonthName) {
		MeanTimeRecoverData meanTimeRecoverData = new MeanTimeRecoverData();
		meanTimeRecoverData.setStoryID(jiraIssueHistoryData.getStoryID());
		meanTimeRecoverData.setUrl(jiraIssueHistoryData.getUrl());
		meanTimeRecoverData.setDesc(jiraIssueHistoryData.getDescription());
		meanTimeRecoverData.setIssueType(jiraIssueHistoryData.getStoryType());
		if (ticketClosedDate != null) {
			meanTimeRecoverData.setClosedDate(DateUtil.tranformUTCLocalTimeToZFormat(ticketClosedDate));
			meanTimeRecoverData.setTimeToRecover(String.valueOf(meanTimeToRecoverInHrs));
		}
		meanTimeRecoverData.setCreatedDate(DateUtil.tranformUTCLocalTimeToZFormat(ticketCreatedDate));
		meanTimeRecoverData.setDate(weekOrMonthName);
		meanTimeRecoverMapTimeWise.computeIfPresent(
				weekOrMonthName,
				(key, list) -> {
					list.add(meanTimeRecoverData);
					return list;
				});
	}

	private void populateExcelData(
			List<KPIExcelData> excelData,
			String requestTrackerId,
			String trendLineName,
			Map<String, List<MeanTimeRecoverData>> meanTimeRecoverMapTimeWise) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			KPIExcelUtility.populateMeanTimeToRecoverSlingshotExcelData(
					meanTimeRecoverMapTimeWise, excelData);
		}
	}

	private Map<String, List<MeanTimeRecoverData>> getLastNWeek(int count) {
		Map<String, List<MeanTimeRecoverData>> lastNWeek = new LinkedHashMap<>();
		LocalDateTime cursor = DateUtil.getTodayTime().minusWeeks(count - 1);
		for (int i = 0; i < count; i++) {
			String weekLabel = buildWeekLabel(cursor);
			lastNWeek.put(weekLabel, new ArrayList<>());
			cursor = cursor.plusWeeks(1);
		}
		return lastNWeek;
	}

	private Map<String, List<MeanTimeRecoverData>> getLastNMonthCount(int count) {
		Map<String, List<MeanTimeRecoverData>> lastNMonth = new LinkedHashMap<>();
		LocalDateTime cursor = DateUtil.getTodayTime().minusMonths(count - 1);
		for (int i = 0; i < count; i++) {
			lastNMonth.put(cursor.getYear() + Constant.DASH + cursor.getMonthValue(), new ArrayList<>());
			cursor = cursor.plusMonths(1);
		}
		return lastNMonth;
	}
}
