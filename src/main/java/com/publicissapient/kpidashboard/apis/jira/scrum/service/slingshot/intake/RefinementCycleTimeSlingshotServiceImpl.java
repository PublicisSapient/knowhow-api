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

package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot.intake;

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
import java.util.Optional;
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
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Computes Refinement Cycle Time: median days from issue creation to the first transition into any
 * configured "Ready" status, reported as a 12-week rolling weekly trend. P85 is surfaced in the
 * hover tooltip.
 *
 * <p>Data source: {@code jira_issue_custom_history.statusUpdationLog} — no new processor required.
 * Template: KPI217 MeanTimeToRecoverSlingshotServiceImpl.
 */
@Component
@Slf4j
public class RefinementCycleTimeSlingshotServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String JIRA_HISTORY_DATA = "jiraIssueHistoryData";
	private static final int DEFAULT_WEEK_COUNT = 12;
	private static final DateTimeFormatter WEEK_LABEL_FORMATTER =
			DateTimeFormatter.ofPattern(DateUtil.DISPLAY_DATE_FORMAT, Locale.ENGLISH);

	@Autowired private ConfigHelperService configHelperService;
	@Autowired private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

	@Override
	public String getQualifierType() {
		return KPICode.REFINEMENT_CYCLE_TIME_SLINGSHOT.name();
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
				"[REFINEMENT-CYCLE-TIME-SLINGSHOT-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				root);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValue(root, nodeWiseKPIValue, KPICode.REFINEMENT_CYCLE_TIME_SLINGSHOT);
		List<DataCount> trendValues =
				getAggregateTrendValues(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.REFINEMENT_CYCLE_TIME_SLINGSHOT);
		kpiElement.setTrendValueList(trendValues);
		return kpiElement;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		List<String> allProjectIds = new ArrayList<>();
		Set<String> allIssueTypes = new LinkedHashSet<>();
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

					List<String> issueTypes =
							ObjectUtils.defaultIfNull(
									fieldMapping.getJiraStoryIdentificationKPI222(), new ArrayList<>());
					allIssueTypes.addAll(issueTypes);

					Map<String, Object> mapOfProjectFiltersFH = new LinkedHashMap<>();
					if (CollectionUtils.isNotEmpty(issueTypes)) {
						mapOfProjectFiltersFH.put(
								JiraFeatureHistory.STORY_TYPE.getFieldValueInFeature(),
								CommonUtils.convertToPatternList(issueTypes));
					}
					uniqueProjectMapFH.put(projectId, mapOfProjectFiltersFH);
				});

		List<String> distinctProjectIds =
				allProjectIds.stream().distinct().collect(Collectors.toList());
		mapOfFiltersFH.put(
				JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(), distinctProjectIds);

		List<JiraIssueCustomHistory> historyDataList =
				jiraIssueCustomHistoryRepository.findIssuesByCreatedDateAndType(
						mapOfFiltersFH, uniqueProjectMapFH, startDate, endDate);

		resultListMap.put(JIRA_HISTORY_DATA, historyDataList);
		return resultListMap;
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
				fieldMapping.getThresholdValueKPI222(), KPICode.REFINEMENT_CYCLE_TIME_SLINGSHOT.getKpiId());
	}

	@SuppressWarnings("unchecked")
	private void calculateProjectWiseLeafNodeValue(
			Map<String, Node> mapTmp, List<Node> projectLeafNodeList, KpiElement kpiElement) {
		Map<String, Object> durationFilter = KpiDataHelper.getDurationFilter(kpiElement);
		String weekOrMonthDefault =
				(String) durationFilter.getOrDefault(Constant.DURATION, CommonConstant.WEEK);
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

			Map<String, List<JiraIssueCustomHistory>> projectWiseHistory =
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

						List<String> readyStatuses =
								ObjectUtils.defaultIfNull(
										fieldMapping.getJiraStatusForRefinementKPI222(), new ArrayList<>());
						Set<String> readyStatusesLower =
								readyStatuses.stream()
										.map(s -> s.toLowerCase(Locale.ROOT))
										.collect(Collectors.toSet());

						Set<String> startStatusesLower =
								ObjectUtils.defaultIfNull(
												fieldMapping.getJiraStatusToStartRefinementKPI222(),
												new ArrayList<String>())
										.stream()
										.map(s -> s.toLowerCase(Locale.ROOT))
										.collect(Collectors.toSet());

						List<JiraIssueCustomHistory> issueHistoryList =
								projectWiseHistory.getOrDefault(basicProjectConfigId, new ArrayList<>());

						String weekOrMonth =
								(String) durationFilter.getOrDefault(Constant.DURATION, CommonConstant.WEEK);

						Map<String, List<Double>> weekBuckets =
								weekOrMonth.equalsIgnoreCase(CommonConstant.WEEK)
										? buildWeekBuckets(previousTimeCountForDb)
										: buildMonthBuckets(previousTimeCountForDb);

						// per-issue records for Excel export
						Map<String, List<RefinementRecord>> weekRecords = new LinkedHashMap<>();
						weekBuckets.keySet().forEach(k -> weekRecords.put(k, new ArrayList<>()));

						if (CollectionUtils.isNotEmpty(issueHistoryList)
								&& CollectionUtils.isNotEmpty(readyStatusesLower)) {
							processIssues(
									issueHistoryList,
									startStatusesLower,
									readyStatusesLower,
									weekOrMonth,
									weekBuckets,
									weekRecords);
						}

						List<DataCount> dataCountList =
								buildDataCounts(trendLineName, weekBuckets, weekRecords);
						mapTmp.get(node.getId()).setValue(dataCountList);

						populateExcelData(requestTrackerId, excelData, weekRecords);
					});

			kpiElement.setExcelData(excelData);
			kpiElement.setExcelColumns(KPIExcelColumn.REFINEMENT_CYCLE_TIME_SLINGSHOT.getColumns());
		}
	}

	private void processIssues(
			List<JiraIssueCustomHistory> issueHistoryList,
			Set<String> startStatusesLower,
			Set<String> readyStatusesLower,
			String weekOrMonth,
			Map<String, List<Double>> weekBuckets,
			Map<String, List<RefinementRecord>> weekRecords) {

		issueHistoryList.forEach(
				history -> {
					LocalDateTime createdTime =
							DateUtil.localDateTimeToUTC(
									DateUtil.convertDateTimeToLocalDateTime(history.getCreatedDate()));

					// Start time: first transition into a configured "refinement start" status,
					// or issue creation date when no start status is configured.
					LocalDateTime startTime = createdTime;
					if (!startStatusesLower.isEmpty()) {
						Optional<JiraHistoryChangeLog> firstStartEntry =
								history.getStatusUpdationLog().stream()
										.filter(
												log ->
														log.getChangedTo() != null
																&& startStatusesLower.contains(
																		log.getChangedTo().toString().toLowerCase(Locale.ROOT)))
										.findFirst();
						if (firstStartEntry.isEmpty()) {
							return; // start status configured but never reached — skip
						}
						startTime = DateUtil.localDateTimeToUTC(firstStartEntry.get().getUpdatedOn());
					}

					Optional<JiraHistoryChangeLog> firstReadyEntry =
							history.getStatusUpdationLog().stream()
									.filter(
											log ->
													log.getChangedTo() != null
															&& readyStatusesLower.contains(
																	log.getChangedTo().toString().toLowerCase(Locale.ROOT)))
									.findFirst();

					if (firstReadyEntry.isEmpty()) {
						return; // issue never reached Ready — skip
					}

					LocalDateTime readyTime =
							DateUtil.localDateTimeToUTC(firstReadyEntry.get().getUpdatedOn());

					double daysDuration =
							Math.max(0, Duration.between(startTime, readyTime).toMinutes()) / 1440.0;
					daysDuration = Math.round(daysDuration * 100.0) / 100.0;

					// bucket by createdTime so the issue always lands in its creation week
					String bucketKey =
							weekOrMonth.equalsIgnoreCase(CommonConstant.WEEK)
									? buildWeekLabel(createdTime)
									: createdTime.getYear() + Constant.DASH + createdTime.getMonthValue();

					if (weekBuckets.containsKey(bucketKey)) {
						weekBuckets.get(bucketKey).add(daysDuration);
						weekRecords
								.get(bucketKey)
								.add(
										new RefinementRecord(
												history.getStoryID(),
												history.getUrl(),
												history.getStoryType(),
												history.getDescription(),
												DateUtil.tranformUTCLocalTimeToZFormat(startTime),
												DateUtil.tranformUTCLocalTimeToZFormat(readyTime),
												String.format("%.2f", daysDuration)));
					}
				});
	}

	private List<DataCount> buildDataCounts(
			String trendLineName,
			Map<String, List<Double>> weekBuckets,
			Map<String, List<RefinementRecord>> weekRecords) {
		List<DataCount> dataCountList = new ArrayList<>();
		weekBuckets.forEach(
				(bucketKey, durations) -> {
					double medianVal = median(durations);
					double p85Val = percentile(durations, 85);

					DataCount dataCount = new DataCount();
					dataCount.setSProjectName(trendLineName);
					dataCount.setDate(bucketKey);
					dataCount.setValue(medianVal);
					dataCount.setData(String.format("%.2f", medianVal));

					Map<String, Object> hoverMap = new HashMap<>();
					hoverMap.put("Issue Count", (long) durations.size());
					hoverMap.put("Median (Days)", medianVal);
					hoverMap.put("P85 (Days)", p85Val);
					dataCount.setHoverValue(hoverMap);

					dataCountList.add(dataCount);
				});
		return dataCountList;
	}

	private Map<String, List<Double>> buildWeekBuckets(int count) {
		Map<String, List<Double>> buckets = new LinkedHashMap<>();
		LocalDateTime cursor = DateUtil.getTodayTime().minusWeeks(count - 1);
		for (int i = 0; i < count; i++) {
			buckets.put(buildWeekLabel(cursor), new ArrayList<>());
			cursor = cursor.plusWeeks(1);
		}
		return buckets;
	}

	private Map<String, List<Double>> buildMonthBuckets(int count) {
		Map<String, List<Double>> buckets = new LinkedHashMap<>();
		LocalDateTime cursor = DateUtil.getTodayTime().minusMonths(count - 1);
		for (int i = 0; i < count; i++) {
			buckets.put(cursor.getYear() + Constant.DASH + cursor.getMonthValue(), new ArrayList<>());
			cursor = cursor.plusMonths(1);
		}
		return buckets;
	}

	private String buildWeekLabel(LocalDateTime dateTime) {
		LocalDate monday =
				dateTime.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
		LocalDate sunday = dateTime.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
		return monday.format(WEEK_LABEL_FORMATTER) + " to " + sunday.format(WEEK_LABEL_FORMATTER);
	}

	private double median(List<Double> values) {
		if (values.isEmpty()) return 0.0;
		List<Double> sorted = values.stream().sorted().collect(Collectors.toList());
		int n = sorted.size();
		double raw = n % 2 == 0 ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0 : sorted.get(n / 2);
		return Math.round(raw * 100.0) / 100.0;
	}

	private double percentile(List<Double> values, int p) {
		if (values.isEmpty()) return 0.0;
		List<Double> sorted = values.stream().sorted().collect(Collectors.toList());
		int idx = (int) Math.ceil(p / 100.0 * sorted.size()) - 1;
		double raw = sorted.get(Math.max(0, idx));
		return Math.round(raw * 100.0) / 100.0;
	}

	private void populateExcelData(
			String requestTrackerId,
			List<KPIExcelData> excelData,
			Map<String, List<RefinementRecord>> weekRecords) {
		if (!requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			return;
		}
		weekRecords.forEach(
				(bucketKey, records) ->
						records.forEach(
								rec -> {
									KPIExcelData row = new KPIExcelData();
									row.setDaysWeeks(bucketKey);
									row.setIssueID(Map.of(rec.storyId(), rec.url()));
									row.setIssueType(rec.issueType());
									row.setIssueDesc(rec.description());
									row.setStartTime(rec.createdTime());
									row.setReadyTime(rec.readyTime());
									row.setRefinementCycleTime(rec.cycleTimeDays());
									excelData.add(row);
								}));
	}

	private record RefinementRecord(
			String storyId,
			String url,
			String issueType,
			String description,
			String createdTime,
			String readyTime,
			String cycleTimeDays) {}
}
