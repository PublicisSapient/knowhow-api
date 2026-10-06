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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import com.publicissapient.kpidashboard.apis.forecast.ForecastingManager;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.DorRevisionAnalyzer;
import com.publicissapient.kpidashboard.apis.util.DorRevisionAnalyzer.RevisionField;
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
 * Computes DOR Acceptance Rate (kpi228): the share of refined stories that pass through Definition
 * of Ready without a major rewrite.
 *
 * <p>A story is in the denominator when it was marked Ready and then entered development in the
 * reporting bucket. It falls into the numerator of failures when the Description and/or Acceptance
 * Criteria fields were substantively revised more than the configured number of times between the
 * Ready transition and the dev start.
 *
 * <pre>
 *   DOR Acceptance Rate = 1 - (stories with more than N substantive revisions / stories started)
 * </pre>
 *
 * <p>Data source: {@code jira_issue_custom_history} — {@code statusUpdationLog} for the Ready and
 * dev-start transitions, {@code descriptionUpdationLog} and {@code acceptanceCriteriaUpdationLog}
 * for the revisions. Template: kpi225 MidSprintReRefinementRateSlingshotServiceImpl.
 */
@Component
@Slf4j
public class DorAcceptanceRateServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String JIRA_HISTORY_DATA = "jiraIssueHistoryData";
	private static final String SPRINT_DATA = "sprintData";
	private static final String FILTER_SPRINT = "Sprint";
	private static final String FILTER_WEEKLY = "Weekly";
	private static final String OUTCOME_PASSED = "Passed DOR";
	private static final String OUTCOME_REWRITTEN = "Major Rewrite";
	private static final int SPRINT_DISPLAY_LIMIT = 12;
	private static final int DEFAULT_WEEK_COUNT = 12;
	private static final DateTimeFormatter WEEK_LABEL_FORMATTER =
			DateTimeFormatter.ofPattern(DateUtil.DISPLAY_DATE_FORMAT, Locale.ENGLISH);

	@Autowired private ConfigHelperService configHelperService;
	@Autowired private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;
	@Autowired private SprintRepository sprintRepository;

	@Autowired(required = false)
	private ForecastingManager forecastingManager;

	@Override
	public String getQualifierType() {
		return KPICode.DOR_ACCEPTANCE_RATE.name();
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
				"[DOR-ACCEPTANCE-RATE-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				root);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValue(root, nodeWiseKPIValue, KPICode.DOR_ACCEPTANCE_RATE);
		List<DataCount> aggregatedTrend =
				getAggregateTrendValues(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.DOR_ACCEPTANCE_RATE);

		DataCountGroup sprintGroup = new DataCountGroup();
		sprintGroup.setFilter(FILTER_SPRINT);
		sprintGroup.setValue(sprintDataCounts);

		DataCountGroup weeklyGroup = new DataCountGroup();
		weeklyGroup.setFilter(FILTER_WEEKLY);
		weeklyGroup.setValue(weeklyDataCounts);

		// Weekly first: the UI dropdown defaults to the first group, in line with other
		// KPIs
		kpiElement.setTrendValueList(List.of(weeklyGroup, sprintGroup));

		if (!aggregatedTrend.isEmpty()
				&& aggregatedTrend.get(0).getMaturity() != null
				&& aggregatedTrend.get(0).getMaturityValue() != null) {
			kpiElement.setOverallMaturity(aggregatedTrend.get(0).getMaturity());
			kpiElement.setOverAllMaturityValue(String.valueOf(aggregatedTrend.get(0).getMaturityValue()));
		}

		return kpiElement;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		List<String> allProjectIds = new ArrayList<>();
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
							fieldMapping == null
									? new ArrayList<>()
									: ObjectUtils.defaultIfNull(
											fieldMapping.getJiraStoryIdentificationKPI228(), new ArrayList<String>());

					Map<String, Object> mapOfProjectFiltersFH = new LinkedHashMap<>();
					if (CollectionUtils.isNotEmpty(issueTypes)) {
						mapOfProjectFiltersFH.put(
								JiraFeatureHistory.STORY_TYPE.getFieldValueInFeature(),
								CommonUtils.convertToPatternList(issueTypes));
					}
					uniqueProjectMapFH.put(projectId, mapOfProjectFiltersFH);
				});

		List<String> distinctProjectIds = allProjectIds.stream().distinct().toList();
		mapOfFiltersFH.put(
				JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(), distinctProjectIds);

		List<JiraIssueCustomHistory> historyDataList =
				jiraIssueCustomHistoryRepository.findForDorAnalysis(mapOfFiltersFH, uniqueProjectMapFH);

		Set<ObjectId> configIds =
				leafNodeList.stream()
						.map(node -> node.getProjectFilter().getBasicProjectConfigId())
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
				fieldMapping.getThresholdValueKPI228() != null
						? fieldMapping.getThresholdValueKPI228().toString()
						: null,
				KPICode.DOR_ACCEPTANCE_RATE.getKpiId());
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

		// History is not date-bounded here; the sprint and week buckets define the
		// reporting window
		Map<String, Object> resultMap = fetchKPIDataFromDb(projectLeafNodeList, null, null, null);

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
						.filter(sprint -> sprint.getBasicProjectConfigId() != null)
						.collect(Collectors.groupingBy(sprint -> sprint.getBasicProjectConfigId().toString()));

		projectLeafNodeList.forEach(
				node -> {
					String trendLineName = node.getProjectFilter().getName();
					String basicProjectConfigId =
							node.getProjectFilter().getBasicProjectConfigId().toString();
					FieldMapping fieldMapping =
							configHelperService
									.getFieldMappingMap()
									.get(node.getProjectFilter().getBasicProjectConfigId());

					DorConfig config = readConfig(fieldMapping);
					List<JiraIssueCustomHistory> issueHistoryList =
							projectWiseHistory.getOrDefault(basicProjectConfigId, new ArrayList<>());

					boolean canCompute =
							CollectionUtils.isNotEmpty(issueHistoryList)
									&& CollectionUtils.isNotEmpty(config.readyStatuses())
									&& CollectionUtils.isNotEmpty(config.devStartStatuses());

					// --- Sprint granularity ---
					List<TimeBucket> sprintBuckets =
							buildSprintBuckets(
									projectWiseSprints.getOrDefault(basicProjectConfigId, new ArrayList<>()));
					BucketResult sprintResult =
							evaluate(
									issueHistoryList, config, sprintBuckets, canCompute && !sprintBuckets.isEmpty());

					List<DataCount> projectSprintCounts =
							buildDataCounts(trendLineName, sprintResult.counts(), sprintResult.records());
					DataCount sprintWrapper = new DataCount();
					sprintWrapper.setData(trendLineName);
					sprintWrapper.setValue(projectSprintCounts);
					addForecast(sprintWrapper, projectSprintCounts);
					sprintDataCounts.add(sprintWrapper);

					// --- Weekly granularity ---
					List<TimeBucket> weeklyBuckets = buildWeekBuckets(previousTimeCount);
					BucketResult weeklyResult = evaluate(issueHistoryList, config, weeklyBuckets, canCompute);

					List<DataCount> projectWeeklyCounts =
							buildDataCounts(trendLineName, weeklyResult.counts(), weeklyResult.records());
					DataCount weeklyWrapper = new DataCount();
					weeklyWrapper.setData(trendLineName);
					weeklyWrapper.setValue(projectWeeklyCounts);
					addForecast(weeklyWrapper, projectWeeklyCounts);
					weeklyDataCounts.add(weeklyWrapper);

					mapTmp.get(node.getId()).setValue(projectWeeklyCounts);

					populateExcelData(requestTrackerId, excelData, weeklyResult.records(), trendLineName);
				});

		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(KPIExcelColumn.DOR_ACCEPTANCE_RATE.getColumns());
	}

	/** Set on the project wrapper: the UI reads forecasts from each series, not from the group. */
	private void addForecast(DataCount projectWrapper, List<DataCount> history) {
		if (forecastingManager != null && CollectionUtils.isNotEmpty(history)) {
			forecastingManager.addForecastsToDataCount(
					projectWrapper, history, KPICode.DOR_ACCEPTANCE_RATE.getKpiId());
		}
	}

	private DorConfig readConfig(FieldMapping fieldMapping) {
		if (fieldMapping == null) {
			return new DorConfig(
					Set.of(),
					Set.of(),
					EnumSet.of(RevisionField.DESCRIPTION),
					DorRevisionAnalyzer.DEFAULT_SUBSTANTIVE_CHANGE_PERCENT,
					DorRevisionAnalyzer.DEFAULT_MAJOR_REWRITE_REVISION_COUNT);
		}

		Set<RevisionField> revisionFields =
				ObjectUtils.defaultIfNull(
								fieldMapping.getDorRevisionFieldsKPI228(), new ArrayList<String>())
						.stream()
						.map(DorRevisionAnalyzer::parseField)
						.filter(java.util.Objects::nonNull)
						.collect(Collectors.toCollection(() -> EnumSet.noneOf(RevisionField.class)));
		if (revisionFields.isEmpty()) {
			revisionFields = EnumSet.of(RevisionField.DESCRIPTION);
		}

		Integer configuredAllowance = fieldMapping.getDorMajorRewriteRevisionCountKPI228();
		int allowance =
				configuredAllowance == null || configuredAllowance < 0
						? DorRevisionAnalyzer.DEFAULT_MAJOR_REWRITE_REVISION_COUNT
						: configuredAllowance;

		return new DorConfig(
				lowerCased(fieldMapping.getJiraStatusForReadyKPI228()),
				lowerCased(fieldMapping.getJiraStatusForInProgressKPI228()),
				revisionFields,
				fieldMapping.getDorSubstantiveChangePercentKPI228(),
				allowance);
	}

	private Set<String> lowerCased(List<String> values) {
		return ObjectUtils.defaultIfNull(values, new ArrayList<String>()).stream()
				.filter(java.util.Objects::nonNull)
				.map(value -> value.toLowerCase(Locale.ROOT))
				.collect(Collectors.toSet());
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

	/** Builds rolling week buckets, oldest first. */
	private List<TimeBucket> buildWeekBuckets(int count) {
		List<TimeBucket> buckets = new ArrayList<>();
		LocalDateTime cursor = DateUtil.getTodayTime().minusWeeks(count - 1L);
		for (int i = 0; i < count; i++) {
			LocalDate monday =
					cursor.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			LocalDate sunday = cursor.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
			String label =
					monday.format(WEEK_LABEL_FORMATTER) + " to " + sunday.format(WEEK_LABEL_FORMATTER);
			buckets.add(new TimeBucket(label, monday.atStartOfDay(), sunday.atTime(23, 59, 59)));
			cursor = cursor.plusWeeks(1);
		}
		return buckets;
	}

	/**
	 * Attributes every story to the bucket containing its dev start, then classifies it as passing or
	 * failing DOR. Stories that were never marked Ready, or never entered development, are excluded
	 * entirely — the KPI only reports on stories that actually claimed to be refined.
	 */
	private BucketResult evaluate(
			List<JiraIssueCustomHistory> issueHistoryList,
			DorConfig config,
			List<TimeBucket> buckets,
			boolean canCompute) {

		Map<String, long[]> counts = new LinkedHashMap<>();
		Map<String, List<DorRecord>> records = new LinkedHashMap<>();
		buckets.forEach(
				bucket -> {
					counts.put(bucket.label(), new long[] {0, 0});
					records.put(bucket.label(), new ArrayList<>());
				});

		if (!canCompute) {
			return new BucketResult(counts, records);
		}

		issueHistoryList.forEach(history -> evaluateIssue(history, config, buckets, counts, records));
		return new BucketResult(counts, records);
	}

	private void evaluateIssue(
			JiraIssueCustomHistory history,
			DorConfig config,
			List<TimeBucket> buckets,
			Map<String, long[]> counts,
			Map<String, List<DorRecord>> records) {

		List<JiraHistoryChangeLog> statusLog =
				ObjectUtils.defaultIfNull(history.getStatusUpdationLog(), new ArrayList<>());

		LocalDateTime readyTime = firstTransitionInto(statusLog, config.readyStatuses(), null);
		if (readyTime == null) {
			return; // never claimed to be refined
		}
		LocalDateTime devStartTime =
				firstTransitionInto(statusLog, config.devStartStatuses(), readyTime);
		if (devStartTime == null) {
			return; // marked Ready but development never started
		}

		// Sprint windows of parallel PODs overlap, so a story counts in every window
		// that contains
		// its dev start. Week buckets never overlap, so there it is a single bucket.
		List<TimeBucket> matchingBuckets =
				buckets.stream()
						.filter(b -> !devStartTime.isBefore(b.start()) && !devStartTime.isAfter(b.end()))
						.toList();
		if (matchingBuckets.isEmpty()) {
			return;
		}

		// Revisions are counted in the strict Definition-of-Ready window: between the
		// first
		// transition into a Ready status and the first transition into a dev-start
		// status.
		int descriptionRevisions =
				config.revisionFields().contains(RevisionField.DESCRIPTION)
						? countSubstantiveRevisions(
								history.getDescriptionUpdationLog(), readyTime, devStartTime, config)
						: 0;
		int acceptanceCriteriaRevisions =
				config.revisionFields().contains(RevisionField.ACCEPTANCE_CRITERIA)
						? countSubstantiveRevisions(
								history.getAcceptanceCriteriaUpdationLog(), readyTime, devStartTime, config)
						: 0;

		int substantiveRevisions = descriptionRevisions + acceptanceCriteriaRevisions;
		boolean majorRewrite = substantiveRevisions > config.majorRewriteAllowance();

		DorRecord record =
				new DorRecord(
						history.getStoryID(),
						history.getUrl(),
						history.getStoryType(),
						history.getDescription(),
						currentStatus(statusLog),
						DateUtil.tranformUTCLocalTimeToZFormat(readyTime),
						DateUtil.tranformUTCLocalTimeToZFormat(devStartTime),
						descriptionRevisions,
						acceptanceCriteriaRevisions,
						substantiveRevisions,
						majorRewrite ? OUTCOME_REWRITTEN : OUTCOME_PASSED);

		for (TimeBucket bucket : matchingBuckets) {
			counts.get(bucket.label())[0]++;
			if (majorRewrite) {
				counts.get(bucket.label())[1]++;
			}
			records.get(bucket.label()).add(record);
		}
	}

	/**
	 * Counts edits landing in {@code [from, to]} that changed enough of the text to be a real
	 * rewrite.
	 */
	private int countSubstantiveRevisions(
			List<JiraHistoryChangeLog> changeLog,
			LocalDateTime from,
			LocalDateTime to,
			DorConfig config) {
		List<JiraHistoryChangeLog> entries = changeLog == null ? List.of() : changeLog;
		return (int)
				entries.stream()
						.filter(entry -> entry.getUpdatedOn() != null)
						// getUpdatedOn() already normalises to UTC - converting again would shift twice
						.filter(
								entry -> !entry.getUpdatedOn().isBefore(from) && !entry.getUpdatedOn().isAfter(to))
						.filter(
								entry ->
										DorRevisionAnalyzer.isSubstantive(
												entry.getChangedFrom(),
												entry.getChangedTo(),
												config.substantiveChangePercent()))
						.count();
	}

	private LocalDateTime firstTransitionInto(
			List<JiraHistoryChangeLog> statusLog, Set<String> statuses, LocalDateTime notBefore) {
		return statusLog.stream()
				.filter(entry -> entry.getChangedTo() != null && entry.getUpdatedOn() != null)
				.filter(entry -> statuses.contains(entry.getChangedTo().toLowerCase(Locale.ROOT)))
				// getUpdatedOn() already normalises to UTC - converting again would shift twice
				.map(JiraHistoryChangeLog::getUpdatedOn)
				.filter(updatedOn -> notBefore == null || !updatedOn.isBefore(notBefore))
				.findFirst()
				.orElse(null);
	}

	private String currentStatus(List<JiraHistoryChangeLog> statusLog) {
		return statusLog.stream()
				.filter(entry -> entry.getChangedTo() != null && entry.getUpdatedOn() != null)
				.reduce((first, second) -> second)
				.map(JiraHistoryChangeLog::getChangedTo)
				.orElse("");
	}

	private List<DataCount> buildDataCounts(
			String trendLineName, Map<String, long[]> counts, Map<String, List<DorRecord>> records) {
		List<DataCount> dataCountList = new ArrayList<>();
		counts.forEach(
				(label, values) -> {
					long started = values[0];
					long rewritten = values[1];
					double rate =
							started == 0
									? 0.0
									: Math.round((1 - (rewritten * 1.0 / started)) * 100.0 * 100.0) / 100.0;

					DataCount dataCount = new DataCount();
					dataCount.setSProjectName(trendLineName);
					dataCount.setDate(label);
					dataCount.setSSprintID(label);
					dataCount.setSSprintName(label);
					dataCount.setValue(rate);
					dataCount.setData(String.format(Locale.ENGLISH, "%.2f", rate));

					Map<String, Object> hoverMap = new HashMap<>();
					hoverMap.put("Stories Started Dev", started);
					hoverMap.put("Stories with Major Rewrite", rewritten);
					hoverMap.put(
							"Stories Passed DOR",
							records.getOrDefault(label, new ArrayList<>()).stream()
									.filter(record -> OUTCOME_PASSED.equals(record.outcome()))
									.count());
					dataCount.setHoverValue(hoverMap);

					dataCountList.add(dataCount);
				});
		return dataCountList;
	}

	private void populateExcelData(
			String requestTrackerId,
			List<KPIExcelData> excelData,
			Map<String, List<DorRecord>> records,
			String projectName) {
		if (!requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			return;
		}
		records.forEach(
				(label, recs) ->
						recs.forEach(
								record -> {
									KPIExcelData row = new KPIExcelData();
									row.setProject(projectName);
									row.setDaysWeeks(label);
									row.setIssueID(
											Map.of(record.storyId(), record.url() == null ? "" : record.url()));
									row.setIssueType(record.issueType());
									row.setIssueDesc(record.description());
									row.setStatus(record.status());
									row.setReadyTime(record.readyDate());
									row.setDevStartDate(record.devStartDate());
									row.setDescriptionRevisions(String.valueOf(record.descriptionRevisions()));
									row.setAcceptanceCriteriaRevisions(
											String.valueOf(record.acceptanceCriteriaRevisions()));
									row.setSubstantiveRevisions(String.valueOf(record.substantiveRevisions()));
									row.setDorOutcome(record.outcome());
									excelData.add(row);
								}));
	}

	private LocalDateTime parseSprintDate(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		for (String format :
				new String[] {
					DateUtil.TIME_FORMAT_WITH_SEC,
					DateUtil.TIME_FORMAT_WITH_SEC_ZONE,
					DateUtil.TIME_FORMAT_WITH_SEC_DATE
				}) {
			try {
				return DateUtil.stringToLocalDateTime(raw, format);
			} catch (Exception ignored) { // NOSONAR - try the next supported format
				// try next format
			}
		}
		log.warn("[KPI228] Could not parse sprint date: {}", raw);
		return null;
	}

	private record TimeBucket(String label, LocalDateTime start, LocalDateTime end) {}

	private record BucketResult(Map<String, long[]> counts, Map<String, List<DorRecord>> records) {}

	private record DorConfig(
			Set<String> readyStatuses,
			Set<String> devStartStatuses,
			Set<RevisionField> revisionFields,
			Double substantiveChangePercent,
			int majorRewriteAllowance) {}

	private record DorRecord(
			String storyId,
			String url,
			String issueType,
			String description,
			String status,
			String readyDate,
			String devStartDate,
			int descriptionRevisions,
			int acceptanceCriteriaRevisions,
			int substantiveRevisions,
			String outcome) {}
}
