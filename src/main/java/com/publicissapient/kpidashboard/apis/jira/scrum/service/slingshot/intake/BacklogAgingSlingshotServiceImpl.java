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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
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
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Backlog Aging (kpi224) — Slingshot / Intake.
 *
 * <p>Shows the distribution of how long items have been sitting in the backlog without being
 * refined or closed. For every Jira issue whose current status is one of the configured backlog
 * statuses (e.g. {@code Backlog}, {@code To Do}) the age is computed as {@code now() - created_at}
 * and the issue is dropped into one of four fixed histogram buckets: {@code 0-30}, {@code 30-90},
 * {@code 90-180} and {@code 180+} days.
 *
 * <p>The KPI exposes no filters. Instead every bucket carries a {@code drillDown} breakdown by
 * issue type — {@code [{"issueType": "Story", "count": 12}, ...]} — so a single bar can be split
 * without re-querying.
 *
 * <p>Stale backlogs are mostly noise — but the noise hides real demand. A healthy backlog has a
 * working set that turns over and a clear policy for retiring items older than ~6 months, so the
 * {@code 180+} bucket is the one to watch.
 *
 * <p>Data source: {@code jira_issue} — no new processor required.
 */
@Service
@Slf4j
public class BacklogAgingSlingshotServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String BACKLOG_ISSUE_DATA = "backlogIssueData";

	/** Drill-down bucket used when an issue carries no resolvable issue type. */
	private static final String UNKNOWN_ISSUE_TYPE = "Other";

	private static final String X_AXIS_LABEL = "Age (Days)";

	private static final String BUCKET_0_30 = "0-30 Days";
	private static final String BUCKET_30_90 = "30-90 Days";
	private static final String BUCKET_90_180 = "90-180 Days";
	private static final String BUCKET_180_PLUS = "180+ Days";

	/** Fixed histogram buckets, ordered youngest → oldest. */
	private static final List<String> AGE_BUCKETS =
			List.of(BUCKET_0_30, BUCKET_30_90, BUCKET_90_180, BUCKET_180_PLUS);

	private static final double BUCKET_1_UPPER_BOUND = 30d;
	private static final double BUCKET_2_UPPER_BOUND = 90d;
	private static final double BUCKET_3_UPPER_BOUND = 180d;

	/** Fallback backlog statuses used when a project has not configured any. */
	private static final List<String> DEFAULT_BACKLOG_STATUSES =
			List.of("Backlog", "To Do", "Open", "New");

	private static final String HOVER_ISSUE_COUNT = "Issue Count";
	private static final String HOVER_BACKLOG_SHARE = "% of Backlog";
	private static final String HOVER_MEDIAN_AGE = "Median Age (Days)";
	private static final String HOVER_OLDEST_AGE = "Oldest (Days)";

	private static final DateTimeFormatter DISPLAY_DATE_FORMATTER =
			DateTimeFormatter.ofPattern(DateUtil.DISPLAY_DATE_FORMAT, Locale.ENGLISH);

	@Autowired private ConfigHelperService configHelperService;
	@Autowired private JiraIssueRepository jiraIssueRepository;

	@Override
	public String getQualifierType() {
		return KPICode.BACKLOG_AGING_SLINGSHOT.name();
	}

	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {
		log.info("BACKLOG AGING SLINGSHOT -> requestTrackerId[{}]", kpiRequest.getRequestTrackerId());

		Node root = treeAggregatorDetail.getRoot();
		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();
		List<Node> projectList =
				treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT);

		calculateProjectWiseLeafNodeValue(mapTmp, projectList, kpiElement);

		log.debug(
				"[BACKLOG-AGING-SLINGSHOT-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				root);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new LinkedHashMap<>();
		calculateAggregatedValue(root, nodeWiseKPIValue, KPICode.BACKLOG_AGING_SLINGSHOT);
		List<DataCount> trendValues =
				getAggregateTrendValues(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.BACKLOG_AGING_SLINGSHOT);

		kpiElement.setTrendValueList(trendValues);
		kpiElement.setxAxisValues(new ArrayList<>(AGE_BUCKETS));
		kpiElement.setLabelXAxis(X_AXIS_LABEL);
		return kpiElement;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		Map<String, Object> resultListMap = new HashMap<>();
		Map<String, List<JiraIssue>> projectWiseBacklog = new LinkedHashMap<>();

		CollectionUtils.emptyIfNull(leafNodeList)
				.forEach(
						leafNode -> {
							ObjectId basicProjectConfigId = leafNode.getProjectFilter().getBasicProjectConfigId();
							FieldMapping fieldMapping =
									configHelperService.getFieldMappingMap().get(basicProjectConfigId);

							Set<String> backlogStatuses = resolveBacklogStatuses(fieldMapping);
							Set<String> issueTypes = resolveIssueTypes(fieldMapping);

							List<JiraIssue> backlogIssues =
									jiraIssueRepository.findBacklogIssuesByStatusAndType(
											basicProjectConfigId.toString(), backlogStatuses, issueTypes);

							// Items that already met the Definition of Ready are no longer "un-refined"
							// backlog, so they are dropped when the project configured such statuses.
							Set<String> refinedStatuses = lowerCaseSet(getRefinedStatuses(fieldMapping));
							if (!refinedStatuses.isEmpty()) {
								backlogIssues =
										backlogIssues.stream()
												.filter(
														issue ->
																issue.getStatus() == null
																		|| !refinedStatuses.contains(
																				issue.getStatus().toLowerCase(Locale.ROOT)))
												.toList();
							}

							log.info(
									"Backlog Aging (kpi224) -> {} backlog issue(s) fetched for project {}",
									backlogIssues.size(),
									leafNode.getProjectFilter().getName());

							projectWiseBacklog.put(basicProjectConfigId.toString(), backlogIssues);
						});

		resultListMap.put(BACKLOG_ISSUE_DATA, projectWiseBacklog);
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
				fieldMapping.getThresholdValueKPI224(), KPICode.BACKLOG_AGING_SLINGSHOT.getKpiId());
	}

	@SuppressWarnings("unchecked")
	private void calculateProjectWiseLeafNodeValue(
			Map<String, Node> mapTmp, List<Node> projectLeafNodeList, KpiElement kpiElement) {

		if (CollectionUtils.isEmpty(projectLeafNodeList)) {
			return;
		}

		String requestTrackerId = getRequestTrackerId();
		List<KPIExcelData> excelData = new ArrayList<>();

		Map<String, Object> resultMap = fetchKPIDataFromDb(projectLeafNodeList, null, null, null);
		if (MapUtils.isEmpty(resultMap)) {
			return;
		}

		Map<String, List<JiraIssue>> projectWiseBacklog =
				(Map<String, List<JiraIssue>>) resultMap.get(BACKLOG_ISSUE_DATA);

		LocalDateTime now = DateUtil.getTodayTime();

		projectLeafNodeList.forEach(
				node -> {
					String projectName = node.getProjectFilter().getName();
					String basicProjectConfigId =
							node.getProjectFilter().getBasicProjectConfigId().toString();

					List<JiraIssue> backlogIssues =
							projectWiseBacklog.getOrDefault(basicProjectConfigId, new ArrayList<>());

					List<BacklogAgingRecord> records = buildRecords(backlogIssues, now);

					mapTmp.get(node.getId()).setValue(buildBucketDataCounts(projectName, records));

					populateExcelData(requestTrackerId, excelData, records);
				});

		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(KPIExcelColumn.BACKLOG_AGING_SLINGSHOT.getColumns());
	}

	/**
	 * Converts the raw backlog issues into aging records, dropping any issue for which the creation
	 * date is missing or unparseable.
	 */
	private List<BacklogAgingRecord> buildRecords(List<JiraIssue> backlogIssues, LocalDateTime now) {
		List<BacklogAgingRecord> records = new ArrayList<>();
		CollectionUtils.emptyIfNull(backlogIssues)
				.forEach(
						issue -> {
							LocalDateTime createdOn = parseCreatedDate(issue.getCreatedDate());
							if (createdOn == null) {
								log.debug(
										"Backlog Aging (kpi224): skipping issue {} — unparseable created date '{}'",
										issue.getNumber(),
										issue.getCreatedDate());
								return;
							}
							double ageInDays = ageInDays(createdOn, now);
							records.add(
									new BacklogAgingRecord(
											issue.getNumber(),
											issue.getUrl(),
											StringUtils.defaultIfBlank(issue.getTypeName(), UNKNOWN_ISSUE_TYPE),
											issue.getName(),
											issue.getStatus(),
											issue.getPriority(),
											createdOn.toLocalDate().format(DISPLAY_DATE_FORMATTER),
											ageInDays,
											resolveBucket(ageInDays)));
						});
		return records;
	}

	/**
	 * Builds the histogram for one project: exactly one data point per age bucket, always all four of
	 * them so the x-axis stays stable even when a bucket is empty. Every data point carries an
	 * issue-type drill-down so the bar can be broken down without needing a KPI filter.
	 */
	private List<DataCount> buildBucketDataCounts(
			String projectName, List<BacklogAgingRecord> records) {

		Map<String, List<BacklogAgingRecord>> bucketed = new LinkedHashMap<>();
		AGE_BUCKETS.forEach(bucket -> bucketed.put(bucket, new ArrayList<>()));
		CollectionUtils.emptyIfNull(records)
				.forEach(issueRecord -> bucketed.get(issueRecord.bucket()).add(issueRecord));

		// Drill-down keys: every issue type present in this project's backlog,
		// alphabetically ordered so the breakdown stays identical across all buckets.
		List<String> backlogIssueTypes =
				CollectionUtils.emptyIfNull(records).stream()
						.map(BacklogAgingRecord::issueType)
						.distinct()
						.sorted()
						.toList();

		int total = CollectionUtils.emptyIfNull(records).size();
		List<DataCount> dataCountList = new ArrayList<>();

		AGE_BUCKETS.forEach(
				bucket -> {
					List<BacklogAgingRecord> bucketRecords = bucketed.get(bucket);
					long count = bucketRecords.size();

					Map<String, Object> hoverValue = new LinkedHashMap<>();
					hoverValue.put(HOVER_ISSUE_COUNT, count);
					hoverValue.put(HOVER_BACKLOG_SHARE, total == 0 ? 0.0 : round((count * 100d) / total));
					hoverValue.put(
							HOVER_MEDIAN_AGE,
							median(bucketRecords.stream().map(BacklogAgingRecord::ageInDays).toList()));
					hoverValue.put(
							HOVER_OLDEST_AGE,
							round(
									bucketRecords.stream()
											.mapToDouble(BacklogAgingRecord::ageInDays)
											.max()
											.orElse(0d)));

					DataCount dataCount = new DataCount();
					dataCount.setSProjectName(projectName);
					dataCount.setSSprintID(bucket);
					dataCount.setSSprintName(bucket);
					// both `date` and `subFilter` survive the hierarchy roll-up, so the bucket
					// label is always available to the chart whatever level is selected
					dataCount.setDate(bucket);
					dataCount.setSubFilter(bucket);
					dataCount.setValue((double) count);
					dataCount.setData(String.valueOf(count));
					dataCount.setHoverValue(hoverValue);
					dataCount.setDrillDown(buildIssueTypeDrillDown(bucketRecords, backlogIssueTypes));
					dataCountList.add(dataCount);
				});

		return dataCountList;
	}

	/**
	 * Breaks a single bucket down by issue type so the histogram bar can be stacked. An entry is
	 * emitted for every issue type known to the series — even with a {@code 0} count — so that the
	 * chart keeps a stable set of stack keys across all four buckets.
	 */
	static List<BacklogAgingDrillDownValue> buildIssueTypeDrillDown(
			List<BacklogAgingRecord> bucketRecords, List<String> drillDownIssueTypes) {

		Map<String, Long> countByIssueType =
				CollectionUtils.emptyIfNull(bucketRecords).stream()
						.collect(
								Collectors.groupingBy(
										BacklogAgingRecord::issueType, LinkedHashMap::new, Collectors.counting()));

		Set<String> issueTypes = new LinkedHashSet<>(CollectionUtils.emptyIfNull(drillDownIssueTypes));
		issueTypes.addAll(countByIssueType.keySet());

		return issueTypes.stream()
				.map(
						issueType ->
								new BacklogAgingDrillDownValue(
										issueType, countByIssueType.getOrDefault(issueType, 0L)))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	/**
	 * Merges the per-project issue-type drill-downs of a bucket into a single breakdown when several
	 * projects are rolled up into one node.
	 */
	@Override
	public Object calculateDrillDownValue(List<Object> drillDownValues) {
		Map<String, Long> aggregated = new LinkedHashMap<>();
		CollectionUtils.emptyIfNull(drillDownValues)
				.forEach(
						projectDrillDown -> {
							if (projectDrillDown instanceof List<?> entries) {
								entries.forEach(
										entry -> {
											if (entry instanceof BacklogAgingDrillDownValue drillDown) {
												aggregated.merge(drillDown.issueType(), drillDown.count(), Long::sum);
											}
										});
							}
						});

		return aggregated.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(entry -> new BacklogAgingDrillDownValue(entry.getKey(), entry.getValue()))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	/** Maps an age in days onto one of the four fixed histogram buckets. */
	static String resolveBucket(double ageInDays) {
		if (ageInDays < BUCKET_1_UPPER_BOUND) {
			return BUCKET_0_30;
		}
		if (ageInDays < BUCKET_2_UPPER_BOUND) {
			return BUCKET_30_90;
		}
		if (ageInDays < BUCKET_3_UPPER_BOUND) {
			return BUCKET_90_180;
		}
		return BUCKET_180_PLUS;
	}

	private Set<String> resolveBacklogStatuses(FieldMapping fieldMapping) {
		List<String> configured =
				fieldMapping == null
						? new ArrayList<>()
						: ObjectUtils.defaultIfNull(
								fieldMapping.getJiraBacklogStatusKPI224(), new ArrayList<>());
		List<String> effective =
				CollectionUtils.isNotEmpty(configured) ? configured : DEFAULT_BACKLOG_STATUSES;
		return effective.stream()
				.filter(StringUtils::isNotBlank)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private Set<String> resolveIssueTypes(FieldMapping fieldMapping) {
		List<String> configured =
				fieldMapping == null
						? new ArrayList<>()
						: ObjectUtils.defaultIfNull(fieldMapping.getJiraIssueTypeKPI224(), new ArrayList<>());
		return configured.stream()
				.filter(StringUtils::isNotBlank)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private List<String> getRefinedStatuses(FieldMapping fieldMapping) {
		return fieldMapping == null
				? new ArrayList<>()
				: ObjectUtils.defaultIfNull(
						fieldMapping.getJiraStatusForRefinedKPI224(), new ArrayList<>());
	}

	private static Set<String> lowerCaseSet(List<String> values) {
		return CollectionUtils.emptyIfNull(values).stream()
				.filter(StringUtils::isNotBlank)
				.map(value -> value.trim().toLowerCase(Locale.ROOT))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/** Age in whole-ish days (2 decimals), never negative. */
	static double ageInDays(LocalDateTime createdOn, LocalDateTime now) {
		long minutes = Duration.between(createdOn, now).toMinutes();
		return round(Math.max(0L, minutes) / 1440d);
	}

	/**
	 * Jira created dates are persisted in a handful of shapes ({@code 2024-10-17T23:08:15.6740000},
	 * {@code 2024-10-17T23:08:15}, {@code 2024-10-17}). Parse defensively so one odd document cannot
	 * fail the whole KPI.
	 */
	static LocalDateTime parseCreatedDate(String createdDate) {
		if (StringUtils.isBlank(createdDate)) {
			return null;
		}
		try {
			return DateUtil.convertToUTCLocalDateTime(createdDate);
		} catch (Exception firstAttempt) { // NOSONAR - fall through to the lenient parsers
			for (String pattern :
					Arrays.asList(DateUtil.TIME_FORMAT, DateUtil.DATE_TIME_FORMAT, DateUtil.DATE_FORMAT)) {
				try {
					return DateUtil.stringToLocalDateTime(createdDate.split("\\.")[0], pattern);
				} catch (Exception ignored) { // NOSONAR - try the next pattern
					// no-op
				}
			}
		}
		return null;
	}

	private static double median(List<Double> values) {
		if (CollectionUtils.isEmpty(values)) {
			return 0d;
		}
		List<Double> sorted = values.stream().sorted(Comparator.naturalOrder()).toList();
		int size = sorted.size();
		double raw =
				size % 2 == 0
						? (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2d
						: sorted.get(size / 2);
		return round(raw);
	}

	private static double round(double value) {
		return Math.round(value * 100d) / 100d;
	}

	private void populateExcelData(
			String requestTrackerId, List<KPIExcelData> excelData, List<BacklogAgingRecord> records) {
		if (StringUtils.isEmpty(requestTrackerId)
				|| !requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			return;
		}
		records.stream()
				.sorted(Comparator.comparingDouble(BacklogAgingRecord::ageInDays).reversed())
				.forEach(
						issueRecord -> {
							KPIExcelData row = new KPIExcelData();
							row.setAgingBucket(issueRecord.bucket());
							row.setIssueID(
									Map.of(issueRecord.issueId(), StringUtils.defaultString(issueRecord.url())));
							row.setIssueType(issueRecord.issueType());
							row.setIssueDesc(issueRecord.description());
							row.setStatus(issueRecord.status());
							row.setPriority(issueRecord.priority());
							row.setCreatedDate(issueRecord.createdDate());
							row.setAgeInDays(String.format(Locale.ENGLISH, "%.2f", issueRecord.ageInDays()));
							excelData.add(row);
						});
	}

	/** Internal, per-issue projection used for both the histogram and the excel export. */
	record BacklogAgingRecord(
			String issueId,
			String url,
			String issueType,
			String description,
			String status,
			String priority,
			String createdDate,
			double ageInDays,
			String bucket) {}

	/**
	 * Drill-down entry exposed on every bucket of the histogram: how many backlog items of a given
	 * issue type fall into that age bucket. Serialised as {@code {"issueType": "Story", "count":
	 * 12}}.
	 */
	public record BacklogAgingDrillDownValue(String issueType, long count) {}
}
