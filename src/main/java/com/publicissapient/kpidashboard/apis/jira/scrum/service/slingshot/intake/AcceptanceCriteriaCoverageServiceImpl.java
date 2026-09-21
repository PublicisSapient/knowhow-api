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
import java.util.Comparator;
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
import org.apache.commons.lang3.StringUtils;
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
import com.publicissapient.kpidashboard.apis.util.AcceptanceCriteriaCounter;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Acceptance Criteria Coverage (kpi227) — Slingshot / Intake.
 *
 * <p>Average number of acceptance criteria attached to a story at the moment it enters In Progress:
 *
 * <pre>
 * value = total_acceptance_criteria / stories_that_entered_in_progress
 * </pre>
 *
 * <p>The sampling moment is the story's <em>first</em> transition into one of the configured In
 * Progress statuses, read from {@code jira_issue_custom_history.statusUpdationLog}. Every story is
 * therefore counted exactly once, in the period in which development actually started — a story
 * that bounces in and out of In Progress does not skew the average.
 *
 * <p>The criteria themselves are read from {@code jira_issue.acceptanceCriteria}, collected by the
 * Jira processor from the custom field a project declares under <em>Custom field for Acceptance
 * Criteria</em>, and split into individual criteria by {@link AcceptanceCriteriaCounter}, which
 * understands Gherkin scenarios, bullet/checkbox/numbered lists and plain one-per-line text.
 *
 * <p><b>There is deliberately no target value.</b> The right number of acceptance criteria depends
 * on story size, so the trend is what matters, not the absolute number. To make that readable each
 * data point carries a drill-down with the full distribution across five bands — None (0), Thin
 * (1-2), Healthy (3-5), Detailed (6-7) and Over-specified (8+) — plus a hover showing the share of
 * stories that started work with no acceptance criteria at all. A team consistently shipping 0–1
 * criteria per story is carrying quality risk; a team at 8+ is over-specifying.
 *
 * <p><b>Known limitation:</b> Jira's changelog does not retain the historical value of a custom
 * text field, and the processor does not snapshot it, so the criteria counted are the ones on the
 * story <em>today</em>. Criteria added after development started are therefore included. The
 * transition still decides <em>which</em> stories are counted and in <em>which</em> period, which
 * is what makes the trend meaningful.
 *
 * <p>Data source: {@code jira_issue_custom_history} + {@code jira_issue} — no new processor
 * required.
 */
@Component
@Slf4j
public class AcceptanceCriteriaCoverageServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String STORY_RECORD_DATA = "acceptanceCriteriaStoryData";

	/**
	 * Key the history repository expects for the "status changed to" filter. Declared locally, as
	 * {@code BacklogKpiHelper} does, rather than reaching into the repository implementation.
	 */
	private static final String STATUS_CHANGED_TO_FILTER = "statusUpdationLog.story.changedTo";

	/** Coverage bands, in the order they are always reported. */
	static final String BAND_NONE = "None (0)";

	static final String BAND_THIN = "Thin (1-2)";
	static final String BAND_HEALTHY = "Healthy (3-5)";
	static final String BAND_DETAILED = "Detailed (6-7)";
	static final String BAND_OVER_SPECIFIED = "Over-specified (8+)";

	private static final List<String> BAND_ORDER =
			List.of(BAND_NONE, BAND_THIN, BAND_HEALTHY, BAND_DETAILED, BAND_OVER_SPECIFIED);

	/** Issue types treated as stories when a project has not configured any. */
	private static final List<String> DEFAULT_STORY_TYPES = List.of("Story");

	/** Statuses that mean "work started" when a project has not configured any. */
	private static final List<String> DEFAULT_IN_PROGRESS_STATUSES = List.of("In Progress");

	private static final int DEFAULT_WEEK_COUNT = 12;

	private static final String HOVER_STORIES = "Stories Entered In Progress";
	private static final String HOVER_TOTAL_CRITERIA = "Total Acceptance Criteria";
	private static final String HOVER_WITHOUT_CRITERIA = "Stories Without Acceptance Criteria";
	private static final String HOVER_WITHOUT_CRITERIA_PERCENT = "Stories Without AC %";

	/** Only the fields the KPI actually reads are pulled back from Mongo. */
	private static final Set<String> PROJECTION_FIELDS =
			Set.of("number", "name", "url", "typeName", "status", "jiraStatus", "acceptanceCriteria");

	private static final DateTimeFormatter WEEK_LABEL_FORMATTER =
			DateTimeFormatter.ofPattern(DateUtil.DISPLAY_DATE_FORMAT, Locale.ENGLISH);

	@Autowired private ConfigHelperService configHelperService;
	@Autowired private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;
	@Autowired private JiraIssueRepository jiraIssueRepository;

	@Override
	public String getQualifierType() {
		return KPICode.ACCEPTANCE_CRITERIA_COVERAGE.name();
	}

	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {
		log.info(
				"ACCEPTANCE-CRITERIA-COVERAGE -> requestTrackerId[{}]", kpiRequest.getRequestTrackerId());

		Node root = treeAggregatorDetail.getRoot();
		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();
		List<Node> projectList =
				treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT);

		calculateProjectWiseLeafNodeValue(mapTmp, projectList, kpiElement);

		log.debug(
				"[ACCEPTANCE-CRITERIA-COVERAGE-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				root);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new LinkedHashMap<>();
		calculateAggregatedValue(root, nodeWiseKPIValue, KPICode.ACCEPTANCE_CRITERIA_COVERAGE);
		List<DataCount> trendValues =
				getAggregateTrendValues(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.ACCEPTANCE_CRITERIA_COVERAGE);

		kpiElement.setTrendValueList(trendValues);
		return kpiElement;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {

		Map<String, Object> resultListMap = new HashMap<>();
		Map<String, List<StoryRecord>> projectWiseRecords = new LinkedHashMap<>();

		CollectionUtils.emptyIfNull(leafNodeList)
				.forEach(
						leafNode -> {
							ObjectId basicProjectConfigId = leafNode.getProjectFilter().getBasicProjectConfigId();
							String projectId = basicProjectConfigId.toString();
							FieldMapping fieldMapping =
									configHelperService.getFieldMappingMap().get(basicProjectConfigId);

							Set<String> inProgressStatuses = resolveInProgressStatuses(fieldMapping);

							// One call per project: the status list and the story types are project
							// specific, and folding several projects into a single query would AND
							// their configurations together.
							List<JiraIssueCustomHistory> histories =
									fetchHistories(
											projectId,
											resolveStoryTypes(fieldMapping),
											inProgressStatuses,
											startDate,
											endDate);

							Map<String, LocalDateTime> startedOn =
									firstInProgressTransition(histories, lowerCaseSet(inProgressStatuses));

							List<StoryRecord> records = enrich(projectId, histories, startedOn, fieldMapping);

							log.info(
									"Acceptance Criteria Coverage (kpi227) -> {} story(ies) entered In Progress for project {}",
									records.size(),
									leafNode.getProjectFilter().getName());

							projectWiseRecords.put(projectId, records);
						});

		resultListMap.put(STORY_RECORD_DATA, projectWiseRecords);
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
				fieldMapping.getThresholdValueKPI227(), KPICode.ACCEPTANCE_CRITERIA_COVERAGE.getKpiId());
	}

	@SuppressWarnings("unchecked")
	private void calculateProjectWiseLeafNodeValue(
			Map<String, Node> mapTmp, List<Node> projectLeafNodeList, KpiElement kpiElement) {

		if (CollectionUtils.isEmpty(projectLeafNodeList)) {
			return;
		}

		Map<String, Object> durationFilter = KpiDataHelper.getDurationFilter(kpiElement);
		String weekOrMonth =
				(String) durationFilter.getOrDefault(Constant.DURATION, CommonConstant.WEEK);
		int periodCount =
				kpiElement.getFilterDuration() != null
						? (int) durationFilter.getOrDefault(Constant.COUNT, DEFAULT_WEEK_COUNT)
						: DEFAULT_WEEK_COUNT;

		LocalDateTime windowStart = windowStart(weekOrMonth, periodCount);
		String startDate = windowStart.toLocalDate().toString();
		String endDate = DateUtil.getTodayDate().toString();

		String requestTrackerId = getRequestTrackerId();
		List<KPIExcelData> excelData = new ArrayList<>();

		Map<String, Object> resultMap =
				fetchKPIDataFromDb(projectLeafNodeList, startDate, endDate, null);
		if (MapUtils.isEmpty(resultMap)) {
			return;
		}

		Map<String, List<StoryRecord>> projectWiseRecords =
				(Map<String, List<StoryRecord>>) resultMap.get(STORY_RECORD_DATA);

		projectLeafNodeList.forEach(
				node -> {
					String projectName = node.getProjectFilter().getName();
					String projectId = node.getProjectFilter().getBasicProjectConfigId().toString();

					List<StoryRecord> records = projectWiseRecords.getOrDefault(projectId, new ArrayList<>());

					mapTmp
							.get(node.getId())
							.setValue(buildPeriodDataCounts(projectName, records, weekOrMonth, periodCount));

					populateExcelData(requestTrackerId, excelData, records, projectName, weekOrMonth);
				});

		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(KPIExcelColumn.ACCEPTANCE_CRITERIA_COVERAGE.getColumns());
	}

	// ────────────────────────────────────────────────────────────────────────
	// Data access
	// ────────────────────────────────────────────────────────────────────────

	/**
	 * Pulls the status history of the stories that touched one of the In Progress statuses inside the
	 * window. The query is intentionally driven by the transition date rather than the issue creation
	 * date, so a story refined months ago but started this week is still picked up.
	 */
	private List<JiraIssueCustomHistory> fetchHistories(
			String projectId,
			Set<String> storyTypes,
			Set<String> inProgressStatuses,
			String startDate,
			String endDate) {

		Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();
		mapOfFilters.put(
				JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(), List.of(projectId));

		Map<String, Object> projectFilters = new LinkedHashMap<>();
		projectFilters.put(
				STATUS_CHANGED_TO_FILTER,
				CommonUtils.convertToPatternList(new ArrayList<>(inProgressStatuses)));
		if (CollectionUtils.isNotEmpty(storyTypes)) {
			projectFilters.put(
					JiraFeatureHistory.STORY_TYPE.getFieldValueInFeature(),
					CommonUtils.convertToPatternList(new ArrayList<>(storyTypes)));
		}

		Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
		uniqueProjectMap.put(projectId, projectFilters);

		return jiraIssueCustomHistoryRepository.findByFilterAndFromStatusMapWithDateFilter(
				mapOfFilters, uniqueProjectMap, startDate, endDate);
	}

	/**
	 * First moment each story moved into an In Progress status. Mongo can match the status and the
	 * date against two different entries of the same array, so the transition is re-verified here on
	 * a single change log entry.
	 */
	static Map<String, LocalDateTime> firstInProgressTransition(
			List<JiraIssueCustomHistory> histories, Set<String> inProgressStatusesLower) {

		Map<String, LocalDateTime> startedOn = new LinkedHashMap<>();
		CollectionUtils.emptyIfNull(histories)
				.forEach(
						history ->
								ObjectUtils.defaultIfNull(
												history.getStatusUpdationLog(), new ArrayList<JiraHistoryChangeLog>())
										.stream()
										.filter(
												entry ->
														entry != null
																&& StringUtils.isNotBlank(entry.getChangedTo())
																&& inProgressStatusesLower.contains(
																		entry.getChangedTo().trim().toLowerCase(Locale.ROOT)))
										.map(AcceptanceCriteriaCoverageServiceImpl::safeUpdatedOn)
										.filter(Objects::nonNull)
										.min(Comparator.naturalOrder())
										.ifPresent(earliest -> startedOn.put(history.getStoryID(), earliest)));
		return startedOn;
	}

	/**
	 * {@link JiraHistoryChangeLog#getUpdatedOn()} converts to UTC on read and throws when the stored
	 * timestamp is null, which older documents can be. One undated entry must not fail the whole KPI,
	 * so it is simply skipped.
	 */
	private static LocalDateTime safeUpdatedOn(JiraHistoryChangeLog entry) {
		try {
			return entry.getUpdatedOn();
		} catch (NullPointerException missingTimestamp) { // NOSONAR - null timestamp means "unknown"
			return null;
		}
	}

	/** Joins the transition moment with the acceptance criteria currently on the story. */
	private List<StoryRecord> enrich(
			String projectId,
			List<JiraIssueCustomHistory> histories,
			Map<String, LocalDateTime> startedOn,
			FieldMapping fieldMapping) {

		if (startedOn.isEmpty()) {
			return new ArrayList<>();
		}

		Map<String, JiraIssue> issuesByNumber =
				jiraIssueRepository
						.findByNumberInAndBasicProjectConfigIdWithFields(
								startedOn.keySet(), projectId, PROJECTION_FIELDS)
						.stream()
						.collect(
								Collectors.toMap(JiraIssue::getNumber, issue -> issue, (first, second) -> first));

		AcceptanceCriteriaCounter.Format format =
				AcceptanceCriteriaCounter.parseFormat(
						fieldMapping == null ? null : fieldMapping.getAcceptanceCriteriaFormatKPI227());

		Map<String, JiraIssueCustomHistory> historyByStory =
				CollectionUtils.emptyIfNull(histories).stream()
						.collect(
								Collectors.toMap(
										JiraIssueCustomHistory::getStoryID,
										history -> history,
										(first, second) -> first));

		List<StoryRecord> records = new ArrayList<>();
		startedOn.forEach(
				(storyId, transition) -> {
					JiraIssue issue = issuesByNumber.get(storyId);
					JiraIssueCustomHistory history = historyByStory.get(storyId);

					AcceptanceCriteriaCounter.Result result =
							AcceptanceCriteriaCounter.count(
									issue == null ? null : issue.getAcceptanceCriteria(), format);

					records.add(
							new StoryRecord(
									storyId,
									issue != null ? issue.getUrl() : history != null ? history.getUrl() : null,
									issue != null
											? StringUtils.defaultString(issue.getTypeName())
											: history != null ? StringUtils.defaultString(history.getStoryType()) : "",
									issue != null
											? issue.getName()
											: history != null ? history.getDescription() : null,
									issue != null ? issue.getStatus() : null,
									transition,
									result.count(),
									result.format().name(),
									band(result.count())));
				});
		return records;
	}

	// ────────────────────────────────────────────────────────────────────────
	// Trend
	// ────────────────────────────────────────────────────────────────────────

	/**
	 * Builds the trend for one project — exactly one data point per period, including the periods in
	 * which no story started so the x-axis stays continuous.
	 */
	private List<DataCount> buildPeriodDataCounts(
			String projectName, List<StoryRecord> records, String weekOrMonth, int periodCount) {

		Map<String, List<StoryRecord>> byPeriod = emptyPeriods(weekOrMonth, periodCount);
		CollectionUtils.emptyIfNull(records)
				.forEach(
						record ->
								byPeriod.computeIfPresent(
										periodLabel(weekOrMonth, record.startedOn()),
										(period, bucket) -> {
											bucket.add(record);
											return bucket;
										}));

		List<DataCount> dataCountList = new ArrayList<>();
		byPeriod.forEach(
				(period, periodRecords) -> {
					long stories = periodRecords.size();
					long totalCriteria = periodRecords.stream().mapToLong(StoryRecord::criteriaCount).sum();
					long withoutCriteria =
							periodRecords.stream().filter(record -> record.criteriaCount() == 0).count();

					double average = stories == 0 ? 0d : round((double) totalCriteria / stories);

					Map<String, Object> hoverValue = new LinkedHashMap<>();
					hoverValue.put(HOVER_STORIES, stories);
					hoverValue.put(HOVER_TOTAL_CRITERIA, totalCriteria);
					hoverValue.put(HOVER_WITHOUT_CRITERIA, withoutCriteria);
					hoverValue.put(
							HOVER_WITHOUT_CRITERIA_PERCENT,
							stories == 0 ? 0d : round((withoutCriteria * 100d) / stories));

					DataCount dataCount = new DataCount();
					dataCount.setSProjectName(projectName);
					dataCount.setDate(period);
					dataCount.setSSprintID(period);
					dataCount.setSSprintName(period);
					dataCount.setValue(average);
					dataCount.setData(String.valueOf(average));
					dataCount.setKpiGroup(CommonConstant.OVERALL);
					dataCount.setHoverValue(hoverValue);
					dataCount.setDrillDown(buildBandDrillDown(periodRecords));
					dataCountList.add(dataCount);
				});

		return dataCountList;
	}

	/**
	 * Distribution of the period's stories across the five coverage bands. Every band is emitted —
	 * even with a {@code 0} count — so the breakdown keeps a stable shape across periods and
	 * projects.
	 */
	static List<CoverageBandValue> buildBandDrillDown(List<StoryRecord> periodRecords) {
		Map<String, Long> countByBand =
				CollectionUtils.emptyIfNull(periodRecords).stream()
						.collect(
								Collectors.groupingBy(
										StoryRecord::band, LinkedHashMap::new, Collectors.counting()));

		Set<String> bands = new LinkedHashSet<>(BAND_ORDER);
		bands.addAll(countByBand.keySet());

		return bands.stream()
				.map(band -> new CoverageBandValue(band, countByBand.getOrDefault(band, 0L)))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	/** Merges the per-project band distributions when several projects roll up into one node. */
	@Override
	public Object calculateDrillDownValue(List<Object> drillDownValues) {
		Map<String, Long> aggregated = new LinkedHashMap<>();
		BAND_ORDER.forEach(band -> aggregated.put(band, 0L));

		CollectionUtils.emptyIfNull(drillDownValues)
				.forEach(
						projectDrillDown -> {
							if (projectDrillDown instanceof List<?> entries) {
								entries.forEach(
										entry -> {
											if (entry instanceof CoverageBandValue drillDown) {
												aggregated.merge(drillDown.band(), drillDown.count(), Long::sum);
											}
										});
							}
						});

		return aggregated.entrySet().stream()
				.map(entry -> new CoverageBandValue(entry.getKey(), entry.getValue()))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	// ────────────────────────────────────────────────────────────────────────
	// Classification / configuration
	// ────────────────────────────────────────────────────────────────────────

	/**
	 * Places a story in a coverage band. There is no universal target, but the extremes are
	 * informative: nothing at all or a single criterion is a quality risk, eight or more usually
	 * means the story should have been split.
	 */
	static String band(int criteriaCount) {
		if (criteriaCount <= 0) {
			return BAND_NONE;
		}
		if (criteriaCount <= 2) {
			return BAND_THIN;
		}
		if (criteriaCount <= 5) {
			return BAND_HEALTHY;
		}
		if (criteriaCount <= 7) {
			return BAND_DETAILED;
		}
		return BAND_OVER_SPECIFIED;
	}

	private Set<String> resolveStoryTypes(FieldMapping fieldMapping) {
		List<String> configured =
				fieldMapping == null
						? new ArrayList<>()
						: ObjectUtils.defaultIfNull(
								fieldMapping.getJiraStoryIdentificationKPI227(), new ArrayList<>());
		return trimmedSet(CollectionUtils.isNotEmpty(configured) ? configured : DEFAULT_STORY_TYPES);
	}

	private Set<String> resolveInProgressStatuses(FieldMapping fieldMapping) {
		List<String> configured =
				fieldMapping == null
						? new ArrayList<>()
						: ObjectUtils.defaultIfNull(
								fieldMapping.getJiraStatusForInProgressKPI227(), new ArrayList<>());
		return trimmedSet(
				CollectionUtils.isNotEmpty(configured) ? configured : DEFAULT_IN_PROGRESS_STATUSES);
	}

	private static Set<String> trimmedSet(List<String> values) {
		return CollectionUtils.emptyIfNull(values).stream()
				.filter(StringUtils::isNotBlank)
				.map(String::trim)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static Set<String> lowerCaseSet(Set<String> values) {
		return values.stream()
				.map(value -> value.toLowerCase(Locale.ROOT))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	// ────────────────────────────────────────────────────────────────────────
	// Period helpers
	// ────────────────────────────────────────────────────────────────────────

	/** Pre-seeds the trend with every period in the window, oldest first. */
	private Map<String, List<StoryRecord>> emptyPeriods(String weekOrMonth, int periodCount) {
		Map<String, List<StoryRecord>> periods = new LinkedHashMap<>();
		boolean weekly = weekOrMonth.equalsIgnoreCase(CommonConstant.WEEK);
		LocalDateTime cursor =
				weekly
						? DateUtil.getTodayTime().minusWeeks(periodCount - 1L)
						: DateUtil.getTodayTime().minusMonths(periodCount - 1L);
		for (int i = 0; i < periodCount; i++) {
			periods.put(periodLabel(weekOrMonth, cursor), new ArrayList<>());
			cursor = weekly ? cursor.plusWeeks(1) : cursor.plusMonths(1);
		}
		return periods;
	}

	/**
	 * First instant of the oldest period that will be plotted. Fetching from exactly here keeps the
	 * excel export and the trend in agreement: every exported story belongs to a period the chart
	 * actually shows.
	 */
	static LocalDateTime windowStart(String weekOrMonth, int periodCount) {
		if (weekOrMonth.equalsIgnoreCase(CommonConstant.WEEK)) {
			return DateUtil.getTodayTime()
					.minusWeeks(periodCount - 1L)
					.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
					.toLocalDate()
					.atStartOfDay();
		}
		return DateUtil.getTodayTime()
				.minusMonths(periodCount - 1L)
				.withDayOfMonth(1)
				.toLocalDate()
				.atStartOfDay();
	}

	static String periodLabel(String weekOrMonth, LocalDateTime dateTime) {
		if (weekOrMonth.equalsIgnoreCase(CommonConstant.WEEK)) {
			LocalDate monday =
					dateTime.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			LocalDate sunday =
					dateTime.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
			return monday.format(WEEK_LABEL_FORMATTER) + " to " + sunday.format(WEEK_LABEL_FORMATTER);
		}
		return dateTime.getYear() + Constant.DASH + dateTime.getMonthValue();
	}

	private static double round(double value) {
		return Math.round(value * 100d) / 100d;
	}

	// ────────────────────────────────────────────────────────────────────────
	// Excel
	// ────────────────────────────────────────────────────────────────────────

	private void populateExcelData(
			String requestTrackerId,
			List<KPIExcelData> excelData,
			List<StoryRecord> records,
			String projectName,
			String weekOrMonth) {

		if (StringUtils.isEmpty(requestTrackerId)
				|| !requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			return;
		}
		records.stream()
				.sorted(Comparator.comparing(StoryRecord::startedOn).thenComparing(StoryRecord::storyId))
				.forEach(
						record -> {
							KPIExcelData row = new KPIExcelData();
							row.setDaysWeeks(periodLabel(weekOrMonth, record.startedOn()));
							row.setProject(projectName);
							row.setIssueID(Map.of(record.storyId(), StringUtils.defaultString(record.url())));
							row.setIssueType(record.issueType());
							row.setIssueDesc(record.description());
							row.setStatus(record.status());
							row.setInProgressDate(
									DateUtil.dateTimeConverter(
											record.startedOn().toLocalDate().toString(),
											DateUtil.DATE_FORMAT,
											DateUtil.DISPLAY_DATE_FORMAT));
							row.setAcceptanceCriteriaCount(String.valueOf(record.criteriaCount()));
							row.setAcceptanceCriteriaFormat(record.format());
							row.setAcceptanceCriteriaBand(record.band());
							excelData.add(row);
						});
	}

	/** Internal, per-story projection used for both the trend and the excel export. */
	record StoryRecord(
			String storyId,
			String url,
			String issueType,
			String description,
			String status,
			LocalDateTime startedOn,
			int criteriaCount,
			String format,
			String band) {}

	/**
	 * Drill-down entry exposed on every data point: how many stories of the period fall into a given
	 * coverage band. Serialised as {@code {"band": "Healthy (3-5)", "count": 7}}.
	 */
	public record CoverageBandValue(String band, long count) {}
}
