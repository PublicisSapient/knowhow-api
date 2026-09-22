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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Computes Mid-Sprint Re-Refinement Rate: % of stories that enter development (transition to a
 * configured dev-start status) and then move backward into a configured refinement status within
 * the same period. Supports two granularities — Sprint (default) and Weekly — via DataCountGroup.
 *
 * <p>Data source: {@code jira_issue_custom_history.statusUpdationLog}. Template: kpi222
 * RefinementCycleTimeSlingshotServiceImpl / kpi205 SprintVelocitySlingshotServiceImpl.
 */
@Component
@Slf4j
public class MidSprintReRefinementRateSlingshotServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String JIRA_HISTORY_DATA = "jiraIssueHistoryData";
	private static final String SPRINT_DATA = "sprintData";
	private static final String FILTER_SPRINT = "Sprint";
	private static final String FILTER_WEEKLY = "Weekly";
	private static final int SPRINT_DISPLAY_LIMIT = 12;
	private static final int DEFAULT_WEEK_COUNT = 12;
	private static final int HISTORY_FETCH_WEEKS = 26;
	private static final DateTimeFormatter WEEK_LABEL_FORMATTER =
			DateTimeFormatter.ofPattern(DateUtil.DISPLAY_DATE_FORMAT, Locale.ENGLISH);

	@Autowired private ConfigHelperService configHelperService;
	@Autowired private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;
	@Autowired private SprintRepository sprintRepository;

	@Override
	public String getQualifierType() {
		return KPICode.MID_SPRINT_RE_REFINEMENT_RATE_SLINGSHOT.name();
	}

	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {
		Node root = treeAggregatorDetail.getRoot();
		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();
		List<Node> projectList =
				treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT);

		List<KPIExcelData> excelData = new ArrayList<>();
		List<DataCount> sprintDataCounts = new ArrayList<>();
		List<DataCount> weeklyDataCounts = new ArrayList<>();

		calculateProjectWiseLeafNodeValue(
				mapTmp, projectList, kpiElement, excelData, sprintDataCounts, weeklyDataCounts);

		log.debug(
				"[MID-SPRINT-RE-REFINEMENT-RATE-SLINGSHOT-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				root);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValue(
				root, nodeWiseKPIValue, KPICode.MID_SPRINT_RE_REFINEMENT_RATE_SLINGSHOT);
		List<DataCount> aggregatedTrend =
				getAggregateTrendValues(
						kpiRequest,
						kpiElement,
						nodeWiseKPIValue,
						KPICode.MID_SPRINT_RE_REFINEMENT_RATE_SLINGSHOT);

		// Sprint group first (default), Weekly group second
		DataCountGroup sprintGroup = new DataCountGroup();
		sprintGroup.setFilter(FILTER_SPRINT);
		sprintGroup.setValue(sprintDataCounts);

		DataCountGroup weeklyGroup = new DataCountGroup();
		weeklyGroup.setFilter(FILTER_WEEKLY);
		weeklyGroup.setValue(weeklyDataCounts);

		kpiElement.setTrendValueList(List.of(sprintGroup, weeklyGroup));

		if (!aggregatedTrend.isEmpty()
				&& aggregatedTrend.get(0).getMaturity() != null
				&& aggregatedTrend.get(0).getMaturityValue() != null) {
			kpiElement.setOverallMaturity(aggregatedTrend.get(0).getMaturity());
			kpiElement.setOverAllMaturityValue(String.valueOf(aggregatedTrend.get(0).getMaturityValue()));
		}

		return kpiElement;
	}

	@SuppressWarnings("unchecked")
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
									fieldMapping.getJiraStoryIdentificationKPI225(), new ArrayList<String>());
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

		// Widen history fetch to cover sprint history (up to 26 weeks)
		String historyStartDate =
				DateUtil.getTodayTime().minusWeeks(HISTORY_FETCH_WEEKS).toLocalDate().toString();

		List<JiraIssueCustomHistory> historyDataList =
				jiraIssueCustomHistoryRepository.findIssuesByCreatedDateAndType(
						mapOfFiltersFH, uniqueProjectMapFH, historyStartDate, endDate);

		// Fetch closed sprints for sprint granularity
		Set<ObjectId> configIds =
				leafNodeList.stream()
						.map(n -> n.getProjectFilter().getBasicProjectConfigId())
						.collect(Collectors.toSet());
		List<SprintDetails> closedSprints =
				sprintRepository.findByBasicProjectConfigIdInAndStateInOrderByStartDateDesc(
						configIds, List.of(SprintDetails.SPRINT_STATE_CLOSED));

		resultListMap.put(JIRA_HISTORY_DATA, historyDataList);
		resultListMap.put(SPRINT_DATA, closedSprints);
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
				fieldMapping.getThresholdValueKPI225() != null
						? fieldMapping.getThresholdValueKPI225().toString()
						: null,
				KPICode.MID_SPRINT_RE_REFINEMENT_RATE_SLINGSHOT.getKpiId());
	}

	@SuppressWarnings("unchecked")
	private void calculateProjectWiseLeafNodeValue(
			Map<String, Node> mapTmp,
			List<Node> projectLeafNodeList,
			KpiElement kpiElement,
			List<KPIExcelData> excelData,
			List<DataCount> sprintDataCounts,
			List<DataCount> weeklyDataCounts) {

		Map<String, Object> durationFilter = KpiDataHelper.getDurationFilter(kpiElement);
		LinkedHashMap<?, ?> filterDurationRaw = (LinkedHashMap<?, ?>) kpiElement.getFilterDuration();
		int previousTimeCount =
				filterDurationRaw != null
						? (int) durationFilter.getOrDefault(Constant.COUNT, DEFAULT_WEEK_COUNT)
						: DEFAULT_WEEK_COUNT;

		String startDate =
				DateUtil.getTodayTime().minusWeeks(HISTORY_FETCH_WEEKS).toLocalDate().toString();
		String endDate = DateUtil.getTodayDate().toString();

		Map<String, Object> resultMap =
				fetchKPIDataFromDb(projectLeafNodeList, startDate, endDate, null);

		if (MapUtils.isEmpty(resultMap)) {
			return;
		}

		String requestTrackerId = getRequestTrackerId();
		List<JiraIssueCustomHistory> historyDataList =
				(List<JiraIssueCustomHistory>) resultMap.get(JIRA_HISTORY_DATA);
		List<SprintDetails> allClosedSprints = (List<SprintDetails>) resultMap.get(SPRINT_DATA);

		Map<String, List<JiraIssueCustomHistory>> projectWiseHistory =
				historyDataList.stream()
						.collect(Collectors.groupingBy(JiraIssueCustomHistory::getBasicProjectConfigId));

		Map<String, List<SprintDetails>> projectWiseSprints =
				allClosedSprints.stream()
						.filter(s -> s.getBasicProjectConfigId() != null)
						.collect(Collectors.groupingBy(s -> s.getBasicProjectConfigId().toString()));

		// Use the first project's leaf node to drive the per-project loop
		// (standard single-project pattern in Slingshot KPIs)
		projectLeafNodeList.forEach(
				node -> {
					String trendLineName = node.getProjectFilter().getName();
					String basicProjectConfigId =
							node.getProjectFilter().getBasicProjectConfigId().toString();
					FieldMapping fieldMapping =
							configHelperService
									.getFieldMappingMap()
									.get(node.getProjectFilter().getBasicProjectConfigId());

					Set<String> devStartStatusesLower =
							ObjectUtils.defaultIfNull(
											fieldMapping.getJiraStatusStartDevKPI225(), new ArrayList<String>())
									.stream()
									.map(s -> s.toLowerCase(Locale.ROOT))
									.collect(Collectors.toSet());

					Set<String> returnStatusesLower =
							ObjectUtils.defaultIfNull(
											fieldMapping.getJiraStatusReturnToRefinementKPI225(), new ArrayList<String>())
									.stream()
									.map(s -> s.toLowerCase(Locale.ROOT))
									.collect(Collectors.toSet());

					List<JiraIssueCustomHistory> issueHistoryList =
							projectWiseHistory.getOrDefault(basicProjectConfigId, new ArrayList<>());

					boolean canCompute =
							CollectionUtils.isNotEmpty(issueHistoryList)
									&& CollectionUtils.isNotEmpty(devStartStatusesLower)
									&& CollectionUtils.isNotEmpty(returnStatusesLower);

					// --- Sprint granularity ---
					List<SprintDetails> projectSprints =
							projectWiseSprints.getOrDefault(basicProjectConfigId, new ArrayList<>());
					List<TimeBucket> sprintBuckets = buildSprintBuckets(projectSprints);

					Map<String, long[]> sprintCounts = new LinkedHashMap<>();
					Map<String, List<ReRefinementRecord>> sprintRecords = new LinkedHashMap<>();
					sprintBuckets.forEach(
							b -> {
								sprintCounts.put(b.label(), new long[] {0, 0});
								sprintRecords.put(b.label(), new ArrayList<>());
							});

					if (canCompute && !sprintBuckets.isEmpty()) {
						processIssues(
								issueHistoryList,
								devStartStatusesLower,
								returnStatusesLower,
								sprintBuckets,
								sprintCounts,
								sprintRecords);
					}

					List<DataCount> projectSprintCounts =
							buildDataCounts(trendLineName, sprintCounts, sprintRecords);
					DataCount sprintWrapper = new DataCount();
					sprintWrapper.setData(trendLineName);
					sprintWrapper.setValue(projectSprintCounts);
					sprintDataCounts.add(sprintWrapper);

					// --- Weekly granularity ---
					List<TimeBucket> weeklyBuckets = buildWeekBuckets(previousTimeCount);

					Map<String, long[]> weeklyCounts = new LinkedHashMap<>();
					Map<String, List<ReRefinementRecord>> weeklyRecords = new LinkedHashMap<>();
					weeklyBuckets.forEach(
							b -> {
								weeklyCounts.put(b.label(), new long[] {0, 0});
								weeklyRecords.put(b.label(), new ArrayList<>());
							});

					if (canCompute) {
						processIssues(
								issueHistoryList,
								devStartStatusesLower,
								returnStatusesLower,
								weeklyBuckets,
								weeklyCounts,
								weeklyRecords);
					}

					List<DataCount> projectWeeklyCounts =
							buildDataCounts(trendLineName, weeklyCounts, weeklyRecords);
					DataCount weeklyWrapper = new DataCount();
					weeklyWrapper.setData(trendLineName);
					weeklyWrapper.setValue(projectWeeklyCounts);
					weeklyDataCounts.add(weeklyWrapper);

					// Set leaf node value from weekly for aggregation/maturity
					mapTmp.get(node.getId()).setValue(projectWeeklyCounts);

					populateExcelData(requestTrackerId, excelData, weeklyRecords, trendLineName);
				});

		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(KPIExcelColumn.MID_SPRINT_RE_REFINEMENT_RATE_SLINGSHOT.getColumns());
	}

	/** Builds sprint buckets from the last N closed sprints, oldest first. */
	private List<TimeBucket> buildSprintBuckets(List<SprintDetails> closedSprints) {
		List<SprintDetails> limited =
				closedSprints.size() > SPRINT_DISPLAY_LIMIT
						? closedSprints.subList(0, SPRINT_DISPLAY_LIMIT)
						: closedSprints;
		List<SprintDetails> oldestFirst = new ArrayList<>(limited);
		Collections.reverse(oldestFirst);

		List<TimeBucket> buckets = new ArrayList<>();
		for (SprintDetails sprint : oldestFirst) {
			LocalDateTime start = parseSprintDate(sprint.getStartDate());
			String endRaw =
					sprint.getCompleteDate() != null ? sprint.getCompleteDate() : sprint.getEndDate();
			LocalDateTime end = endRaw != null ? parseSprintDate(endRaw) : null;
			if (start == null || end == null) {
				continue;
			}
			buckets.add(new TimeBucket(sprint.getSprintName(), start, end));
		}
		return buckets;
	}

	/** Builds 12-week rolling window buckets, oldest first. */
	private List<TimeBucket> buildWeekBuckets(int count) {
		List<TimeBucket> buckets = new ArrayList<>();
		LocalDateTime cursor = DateUtil.getTodayTime().minusWeeks(count - 1L);
		for (int i = 0; i < count; i++) {
			java.time.LocalDate monday =
					cursor.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			java.time.LocalDate sunday =
					cursor.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
			String label =
					monday.format(WEEK_LABEL_FORMATTER) + " to " + sunday.format(WEEK_LABEL_FORMATTER);
			LocalDateTime start = monday.atStartOfDay();
			LocalDateTime end = sunday.atTime(23, 59, 59);
			buckets.add(new TimeBucket(label, start, end));
			cursor = cursor.plusWeeks(1);
		}
		return buckets;
	}

	/**
	 * Event-based bucketing: - Denominator for bucket B = issues whose first dev-start transition
	 * falls in B. - Numerator for bucket B = issues that had a backward (return-to-refinement)
	 * transition in B, regardless of which bucket their dev-start fell in (cross-period returns
	 * count). An issue with no dev-start is excluded from all buckets. An issue may contribute to one
	 * denominator bucket and one or more numerator buckets.
	 */
	private void processIssues(
			List<JiraIssueCustomHistory> issueHistoryList,
			Set<String> devStartStatusesLower,
			Set<String> returnStatusesLower,
			List<TimeBucket> buckets,
			Map<String, long[]> counts,
			Map<String, List<ReRefinementRecord>> records) {

		issueHistoryList.forEach(
				history -> {
					List<JiraHistoryChangeLog> statusLog =
							ObjectUtils.defaultIfNull(history.getStatusUpdationLog(), new ArrayList<>());

					// Find first dev-start transition
					LocalDateTime devStartTime = null;
					for (JiraHistoryChangeLog entry : statusLog) {
						if (entry.getChangedTo() != null
								&& devStartStatusesLower.contains(
										entry.getChangedTo().toString().toLowerCase(Locale.ROOT))) {
							devStartTime = DateUtil.localDateTimeToUTC(entry.getUpdatedOn());
							break;
						}
					}
					if (devStartTime == null) {
						return; // never reached dev — exclude entirely
					}

					final LocalDateTime devStart = devStartTime;
					final String devStartFormatted = DateUtil.tranformUTCLocalTimeToZFormat(devStart);

					// Denominator: which bucket contains the dev-start?
					TimeBucket devBucket =
							buckets.stream()
									.filter(b -> !devStart.isBefore(b.start()) && !devStart.isAfter(b.end()))
									.findFirst()
									.orElse(null);
					if (devBucket != null) {
						counts.get(devBucket.label())[0]++;
					}

					// Numerator + Excel: each return-to-refinement transition after dev-start is
					// attributed to the bucket that contains the RETURN event (event-based). An
					// issue
					// is counted at most once per return-bucket (first qualifying return wins).
					Set<String> numeratorBuckets = new LinkedHashSet<>();
					for (JiraHistoryChangeLog entry : statusLog) {
						if (entry.getChangedTo() == null || entry.getUpdatedOn() == null) {
							continue;
						}
						LocalDateTime returnTime = DateUtil.localDateTimeToUTC(entry.getUpdatedOn());
						if (!returnTime.isAfter(devStart)) {
							continue; // must be after dev started
						}
						if (!returnStatusesLower.contains(
								entry.getChangedTo().toString().toLowerCase(Locale.ROOT))) {
							continue;
						}
						TimeBucket returnBucket =
								buckets.stream()
										.filter(b -> !returnTime.isBefore(b.start()) && !returnTime.isAfter(b.end()))
										.findFirst()
										.orElse(null);
						if (returnBucket == null) {
							continue;
						}
						if (numeratorBuckets.add(returnBucket.label())) {
							// First return in this bucket for this issue
							counts.get(returnBucket.label())[1]++;
							records
									.get(returnBucket.label())
									.add(
											new ReRefinementRecord(
													history.getStoryID(),
													history.getUrl(),
													history.getStoryType(),
													history.getDescription(),
													devStartFormatted,
													DateUtil.tranformUTCLocalTimeToZFormat(returnTime),
													entry.getChangedTo().toString(),
													"1"));
						}
					}
				});
	}

	private List<DataCount> buildDataCounts(
			String trendLineName,
			Map<String, long[]> counts,
			Map<String, List<ReRefinementRecord>> records) {
		List<DataCount> dataCountList = new ArrayList<>();
		counts.forEach(
				(label, vals) -> {
					long started = vals[0];
					long returned = vals[1];
					double rate =
							started == 0 ? 0.0 : Math.round((returned * 100.0 / started) * 100.0) / 100.0;

					DataCount dc = new DataCount();
					dc.setSProjectName(trendLineName);
					dc.setDate(label);
					dc.setSSprintID(label);
					dc.setSSprintName(label);
					dc.setValue(rate);
					dc.setData(String.format("%.2f", rate));

					Map<String, Object> hoverMap = new HashMap<>();
					hoverMap.put("Stories Started Dev", started);
					hoverMap.put("Returned to Refinement", returned);
					hoverMap.put("Re-Refinement Rate (%)", rate);
					dc.setHoverValue(hoverMap);

					dataCountList.add(dc);
				});
		return dataCountList;
	}

	private void populateExcelData(
			String requestTrackerId,
			List<KPIExcelData> excelData,
			Map<String, List<ReRefinementRecord>> records,
			String projectName) {
		if (!requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			return;
		}
		records.forEach(
				(label, recs) ->
						recs.forEach(
								rec -> {
									KPIExcelData row = new KPIExcelData();
									row.setProject(projectName);
									row.setDaysWeeks(label);
									row.setIssueID(Map.of(rec.storyId(), rec.url()));
									row.setIssueType(rec.issueType());
									row.setIssueDesc(rec.description());
									row.setDevStartDate(rec.devStartDate());
									row.setFirstReturnDate(rec.firstReturnDate());
									row.setReturnToStatus(rec.returnToStatus());
									row.setReturnCount(rec.returnCount());
									excelData.add(row);
								}));
	}

	private LocalDateTime parseSprintDate(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		for (String fmt :
				new String[] {
					DateUtil.TIME_FORMAT_WITH_SEC,
					DateUtil.TIME_FORMAT_WITH_SEC_ZONE,
					DateUtil.TIME_FORMAT_WITH_SEC_DATE
				}) {
			try {
				return DateUtil.stringToLocalDateTime(raw, fmt);
			} catch (Exception ignored) {
				// try next format
			}
		}
		log.warn("[KPI225] Could not parse sprint date: {}", raw);
		return null;
	}

	private record TimeBucket(String label, LocalDateTime start, LocalDateTime end) {}

	private record ReRefinementRecord(
			String storyId,
			String url,
			String issueType,
			String description,
			String devStartDate,
			String firstReturnDate,
			String returnToStatus,
			String returnCount) {}
}
