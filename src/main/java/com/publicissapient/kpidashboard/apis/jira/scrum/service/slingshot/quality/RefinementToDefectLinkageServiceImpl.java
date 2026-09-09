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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
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
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Refinement-to-Defect Linkage (kpi225) — Slingshot / Quality.
 *
 * <p>Percentage of production defects whose root cause traces back to a missed, ambiguous or wrong
 * acceptance criterion at Definition of Ready:
 *
 * <pre>
 * value = refinement_root_cause_count / total_production_defects * 100
 * </pre>
 *
 * <p><b>This is the only metric in the blueprint that depends on manual tagging.</b> There is no
 * automated way to prove that a production defect was born in refinement, so the signal has to come
 * from a lightweight classification made at incident review: every production defect is tagged with
 * exactly one root cause out of {@code refinement | design | code | infra | external}. KnowHow
 * reads that tag from the Jira RCA field already wired through the global <em>Root Cause</em> field
 * mapping (persisted on {@link JiraIssue#getRootCauseList()}), so no extra processor is needed —
 * only the discipline of tagging.
 *
 * <p>Because the number is only as good as the tagging behind it, every data point also carries a
 * <em>Root Cause Coverage %</em> hover value (tagged defects / total defects). A low linkage
 * percentage sitting on top of low coverage means "we do not know", not "we are fine" — read the
 * two together.
 *
 * <p>Each point additionally carries a drill-down with the full root-cause distribution ({@code
 * [{"category": "Refinement", "count": 7}, ...]}) so the split can be inspected without a second
 * query, and the categories roll up correctly when several projects are aggregated.
 *
 * <p>Closing this loop is expensive to collect and worth it: it is the only feedback path that
 * makes bad refinement visible in production terms.
 *
 * <p>Data source: {@code jira_issue} — no new processor required.
 */
@Service
@Slf4j
public class RefinementToDefectLinkageServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String PRODUCTION_DEFECT_DATA = "productionDefectData";

	/** Canonical root-cause buckets used at incident review. */
	static final String CATEGORY_REFINEMENT = "Refinement";

	static final String CATEGORY_DESIGN = "Design";
	static final String CATEGORY_CODE = "Code";
	static final String CATEGORY_INFRA = "Infra";
	static final String CATEGORY_EXTERNAL = "External";
	static final String CATEGORY_OTHER = "Other";

	/** Production defect that carries no root cause tag at all — the tagging gap. */
	static final String CATEGORY_UNTAGGED = "Untagged";

	/** Stable drill-down ordering so the breakdown reads the same on every data point. */
	private static final List<String> CATEGORY_ORDER =
			List.of(
					CATEGORY_REFINEMENT,
					CATEGORY_DESIGN,
					CATEGORY_CODE,
					CATEGORY_INFRA,
					CATEGORY_EXTERNAL,
					CATEGORY_OTHER,
					CATEGORY_UNTAGGED);

	/**
	 * Bridges the wording teams actually use in their Jira RCA field onto the five canonical buckets.
	 * Anything unrecognised falls into {@link #CATEGORY_OTHER} rather than being silently counted as
	 * refinement.
	 */
	private static final Map<String, String> CANONICAL_ALIASES =
			Map.ofEntries(
					Map.entry("refinement", CATEGORY_REFINEMENT),
					Map.entry("requirement", CATEGORY_REFINEMENT),
					Map.entry("requirements", CATEGORY_REFINEMENT),
					Map.entry("requirement gap", CATEGORY_REFINEMENT),
					Map.entry("acceptance criteria", CATEGORY_REFINEMENT),
					Map.entry("missing acceptance criteria", CATEGORY_REFINEMENT),
					Map.entry("ambiguous acceptance criteria", CATEGORY_REFINEMENT),
					Map.entry("wrong acceptance criteria", CATEGORY_REFINEMENT),
					Map.entry("analysis", CATEGORY_REFINEMENT),
					Map.entry("design", CATEGORY_DESIGN),
					Map.entry("architecture", CATEGORY_DESIGN),
					Map.entry("code", CATEGORY_CODE),
					Map.entry("coding", CATEGORY_CODE),
					Map.entry("development", CATEGORY_CODE),
					Map.entry("infra", CATEGORY_INFRA),
					Map.entry("infrastructure", CATEGORY_INFRA),
					Map.entry("environment", CATEGORY_INFRA),
					Map.entry("configuration", CATEGORY_INFRA),
					Map.entry("data", CATEGORY_INFRA),
					Map.entry("external", CATEGORY_EXTERNAL),
					Map.entry("third party", CATEGORY_EXTERNAL),
					Map.entry("3rd party", CATEGORY_EXTERNAL),
					Map.entry("vendor", CATEGORY_EXTERNAL),
					Map.entry("upstream", CATEGORY_EXTERNAL));

	/** Issue types treated as defects when a project has not configured any. */
	private static final List<String> DEFAULT_DEFECT_ISSUE_TYPES = List.of("Bug", "Defect");

	/** RCA values counted as "refinement" when a project has not configured any. */
	private static final List<String> DEFAULT_REFINEMENT_ROOT_CAUSES = List.of(CATEGORY_REFINEMENT);

	private static final int DEFAULT_WEEK_COUNT = 12;

	private static final String HOVER_REFINEMENT_DEFECTS = "Refinement Root Cause";
	private static final String HOVER_TOTAL_DEFECTS = "Total Defects";
	private static final String HOVER_TAGGED_DEFECTS = "Root Cause Tagged";
	private static final String HOVER_COVERAGE = "Root Cause Coverage %";

	/** Only the fields the KPI actually reads are pulled back from Mongo. */
	private static final Set<String> PROJECTION_FIELDS =
			Set.of(
					"number",
					"name",
					"url",
					"typeName",
					"status",
					"jiraStatus",
					"priority",
					"createdDate",
					"rootCauseList",
					"labels",
					"productionDefect",
					"basicProjectConfigId",
					"projectName");

	private static final DateTimeFormatter WEEK_LABEL_FORMATTER =
			DateTimeFormatter.ofPattern(DateUtil.DISPLAY_DATE_FORMAT, Locale.ENGLISH);

	@Autowired private ConfigHelperService configHelperService;
	@Autowired private JiraIssueRepository jiraIssueRepository;

	@Override
	public String getQualifierType() {
		return KPICode.REFINEMENT_TO_DEFECT_LINKAGE.name();
	}

	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {
		log.info(
				"REFINEMENT-TO-DEFECT-LINKAGE -> requestTrackerId[{}]", kpiRequest.getRequestTrackerId());

		Node root = treeAggregatorDetail.getRoot();
		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();
		List<Node> projectList =
				treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT);

		calculateProjectWiseLeafNodeValue(mapTmp, projectList, kpiElement);

		log.debug(
				"[REFINEMENT-TO-DEFECT-LINKAGE-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				root);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new LinkedHashMap<>();
		calculateAggregatedValue(root, nodeWiseKPIValue, KPICode.REFINEMENT_TO_DEFECT_LINKAGE);
		List<DataCount> trendValues =
				getAggregateTrendValues(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.REFINEMENT_TO_DEFECT_LINKAGE);

		kpiElement.setTrendValueList(trendValues);
		return kpiElement;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {

		Map<String, Object> resultListMap = new HashMap<>();
		Map<String, List<JiraIssue>> projectWiseProductionDefects = new LinkedHashMap<>();

		CollectionUtils.emptyIfNull(leafNodeList)
				.forEach(
						leafNode -> {
							ObjectId basicProjectConfigId = leafNode.getProjectFilter().getBasicProjectConfigId();
							FieldMapping fieldMapping =
									configHelperService.getFieldMappingMap().get(basicProjectConfigId);

							List<JiraIssue> defects =
									jiraIssueRepository
											.findByTypeNameInAndBasicProjectConfigIdAndCreatedDateBetweenWithFields(
													resolveDefectIssueTypes(fieldMapping),
													basicProjectConfigId.toString(),
													startDate,
													endDate,
													PROJECTION_FIELDS);

							List<JiraIssue> productionDefects =
									defects.stream()
											.filter(defect -> isProductionDefect(defect, fieldMapping))
											.toList();

							log.info(
									"Refinement-to-Defect Linkage (kpi225) -> {} production defect(s) out of {} defect(s) for project {}",
									productionDefects.size(),
									defects.size(),
									leafNode.getProjectFilter().getName());

							projectWiseProductionDefects.put(basicProjectConfigId.toString(), productionDefects);
						});

		resultListMap.put(PRODUCTION_DEFECT_DATA, projectWiseProductionDefects);
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
				fieldMapping.getThresholdValueKPI225(), KPICode.REFINEMENT_TO_DEFECT_LINKAGE.getKpiId());
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

		String startDate =
				DateUtil.dateTimeFormatter(windowStart.toLocalDate().atStartOfDay(), DateUtil.TIME_FORMAT);
		String endDate = DateUtil.dateTimeFormatter(DateUtil.getTodayTime(), DateUtil.TIME_FORMAT);

		String requestTrackerId = getRequestTrackerId();
		List<KPIExcelData> excelData = new ArrayList<>();

		Map<String, Object> resultMap =
				fetchKPIDataFromDb(projectLeafNodeList, startDate, endDate, null);
		if (MapUtils.isEmpty(resultMap)) {
			return;
		}

		Map<String, List<JiraIssue>> projectWiseProductionDefects =
				(Map<String, List<JiraIssue>>) resultMap.get(PRODUCTION_DEFECT_DATA);

		projectLeafNodeList.forEach(
				node -> {
					ObjectId basicProjectConfigId = node.getProjectFilter().getBasicProjectConfigId();
					String projectName = node.getProjectFilter().getName();
					FieldMapping fieldMapping =
							configHelperService.getFieldMappingMap().get(basicProjectConfigId);

					Set<String> refinementRootCauses = resolveRefinementRootCauses(fieldMapping);

					List<DefectRecord> records =
							buildRecords(
									projectWiseProductionDefects.getOrDefault(
											basicProjectConfigId.toString(), new ArrayList<>()),
									refinementRootCauses,
									weekOrMonth);

					mapTmp
							.get(node.getId())
							.setValue(buildPeriodDataCounts(projectName, records, weekOrMonth, periodCount));

					populateExcelData(requestTrackerId, excelData, records, projectName);
				});

		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(KPIExcelColumn.REFINEMENT_TO_DEFECT_LINKAGE.getColumns());
	}

	/**
	 * Projects the raw production defects onto the per-defect record used by both the trend and the
	 * excel export, dropping any defect whose creation date cannot be parsed.
	 */
	private List<DefectRecord> buildRecords(
			List<JiraIssue> productionDefects, Set<String> refinementRootCauses, String weekOrMonth) {

		List<DefectRecord> records = new ArrayList<>();
		CollectionUtils.emptyIfNull(productionDefects)
				.forEach(
						defect -> {
							LocalDateTime createdOn = parseCreatedDate(defect.getCreatedDate());
							if (createdOn == null) {
								log.debug(
										"Refinement-to-Defect Linkage (kpi225): skipping defect {} — unparseable created date '{}'",
										defect.getNumber(),
										defect.getCreatedDate());
								return;
							}
							List<String> rootCauses =
									CollectionUtils.emptyIfNull(defect.getRootCauseList()).stream()
											.filter(StringUtils::isNotBlank)
											.map(String::trim)
											.toList();
							records.add(
									new DefectRecord(
											defect.getNumber(),
											defect.getUrl(),
											StringUtils.defaultString(defect.getTypeName()),
											defect.getName(),
											defect.getStatus(),
											defect.getPriority(),
											createdOn.toLocalDate().format(WEEK_LABEL_FORMATTER),
											rootCauses,
											resolveCategory(rootCauses, refinementRootCauses),
											periodLabel(weekOrMonth, createdOn)));
						});
		return records;
	}

	/**
	 * Builds the trend for one project — exactly one data point per period, including the periods
	 * with no production defects at all so the x-axis stays continuous.
	 */
	private List<DataCount> buildPeriodDataCounts(
			String projectName, List<DefectRecord> records, String weekOrMonth, int periodCount) {

		Map<String, List<DefectRecord>> byPeriod = emptyPeriods(weekOrMonth, periodCount);
		CollectionUtils.emptyIfNull(records)
				.forEach(
						defectRecord ->
								byPeriod.computeIfPresent(
										defectRecord.period(),
										(period, bucket) -> {
											bucket.add(defectRecord);
											return bucket;
										}));

		List<DataCount> dataCountList = new ArrayList<>();
		byPeriod.forEach(
				(period, periodRecords) -> {
					long total = periodRecords.size();
					long refinement =
							periodRecords.stream()
									.filter(defectRecord -> CATEGORY_REFINEMENT.equals(defectRecord.category()))
									.count();
					long tagged =
							periodRecords.stream()
									.filter(defectRecord -> !CATEGORY_UNTAGGED.equals(defectRecord.category()))
									.count();

					double linkage = total == 0 ? 0d : round((refinement * 100d) / total);

					Map<String, Object> hoverValue = new LinkedHashMap<>();
					hoverValue.put(HOVER_REFINEMENT_DEFECTS, refinement);
					hoverValue.put(HOVER_TOTAL_DEFECTS, total);
					hoverValue.put(HOVER_TAGGED_DEFECTS, tagged);
					hoverValue.put(HOVER_COVERAGE, total == 0 ? 0d : round((tagged * 100d) / total));

					DataCount dataCount = new DataCount();
					dataCount.setSProjectName(projectName);
					dataCount.setDate(period);
					dataCount.setSSprintID(period);
					dataCount.setSSprintName(period);
					dataCount.setValue(linkage);
					dataCount.setData(String.valueOf(linkage));
					dataCount.setKpiGroup(CommonConstant.OVERALL);
					dataCount.setHoverValue(hoverValue);
					dataCount.setDrillDown(buildCategoryDrillDown(periodRecords));
					dataCountList.add(dataCount);
				});

		return dataCountList;
	}

	/**
	 * Full root-cause distribution for one period. Every canonical category is emitted — even with a
	 * {@code 0} count — so the breakdown keeps a stable shape across periods and projects.
	 */
	static List<RootCauseDrillDownValue> buildCategoryDrillDown(List<DefectRecord> periodRecords) {
		Map<String, Long> countByCategory =
				CollectionUtils.emptyIfNull(periodRecords).stream()
						.collect(
								Collectors.groupingBy(
										DefectRecord::category, LinkedHashMap::new, Collectors.counting()));

		Set<String> categories = new LinkedHashSet<>(CATEGORY_ORDER);
		categories.addAll(countByCategory.keySet());

		return categories.stream()
				.map(
						category ->
								new RootCauseDrillDownValue(category, countByCategory.getOrDefault(category, 0L)))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	/** Merges the per-project root-cause breakdowns when several projects roll up into one node. */
	@Override
	public Object calculateDrillDownValue(List<Object> drillDownValues) {
		Map<String, Long> aggregated = new LinkedHashMap<>();
		CATEGORY_ORDER.forEach(category -> aggregated.put(category, 0L));

		CollectionUtils.emptyIfNull(drillDownValues)
				.forEach(
						projectDrillDown -> {
							if (projectDrillDown instanceof List<?> entries) {
								entries.forEach(
										entry -> {
											if (entry instanceof RootCauseDrillDownValue drillDown) {
												aggregated.merge(drillDown.category(), drillDown.count(), Long::sum);
											}
										});
							}
						});

		return aggregated.entrySet().stream()
				.map(entry -> new RootCauseDrillDownValue(entry.getKey(), entry.getValue()))
				.collect(Collectors.toCollection(ArrayList::new));
	}

	// ────────────────────────────────────────────────────────────────────────
	// Classification
	// ────────────────────────────────────────────────────────────────────────

	/**
	 * Maps the manually applied RCA tag(s) onto one canonical bucket. The project configured values
	 * always win, so a team whose RCA field says "AC missing" still lands in {@code Refinement}
	 * without needing that wording to be known upfront.
	 */
	static String resolveCategory(List<String> rootCauses, Set<String> refinementRootCauses) {
		if (CollectionUtils.isEmpty(rootCauses)) {
			return CATEGORY_UNTAGGED;
		}
		boolean refinementTagged =
				rootCauses.stream()
						.anyMatch(
								rootCause -> refinementRootCauses.contains(rootCause.toLowerCase(Locale.ROOT)));
		if (refinementTagged) {
			return CATEGORY_REFINEMENT;
		}
		return rootCauses.stream()
				.map(rootCause -> CANONICAL_ALIASES.get(rootCause.toLowerCase(Locale.ROOT)))
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(CATEGORY_OTHER);
	}

	/**
	 * A defect counts as a production defect either because it carries one of the configured labels,
	 * or because the Jira processor already stamped it through the global Production Defect Mapping.
	 */
	private boolean isProductionDefect(JiraIssue defect, FieldMapping fieldMapping) {
		if (fieldMapping == null) {
			return defect.isProductionDefect();
		}
		String identification =
				StringUtils.trimToEmpty(fieldMapping.getJiraProductionDefectIdentificationKPI225());
		Set<String> configuredValues = lowerCaseSet(fieldMapping.getJiraProductionDefectValueKPI225());

		if (CommonConstant.LABELS.equalsIgnoreCase(identification)
				&& CollectionUtils.isNotEmpty(configuredValues)) {
			return CollectionUtils.emptyIfNull(defect.getLabels()).stream()
					.filter(StringUtils::isNotBlank)
					.anyMatch(label -> configuredValues.contains(label.trim().toLowerCase(Locale.ROOT)));
		}
		return defect.isProductionDefect();
	}

	private Set<String> resolveDefectIssueTypes(FieldMapping fieldMapping) {
		List<String> configured =
				fieldMapping == null
						? new ArrayList<>()
						: ObjectUtils.defaultIfNull(fieldMapping.getJiraIssueTypeKPI225(), new ArrayList<>());
		List<String> effective =
				CollectionUtils.isNotEmpty(configured) ? configured : DEFAULT_DEFECT_ISSUE_TYPES;
		return effective.stream()
				.filter(StringUtils::isNotBlank)
				.map(String::trim)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private Set<String> resolveRefinementRootCauses(FieldMapping fieldMapping) {
		List<String> configured =
				fieldMapping == null
						? new ArrayList<>()
						: ObjectUtils.defaultIfNull(
								fieldMapping.getJiraRefinementRootCauseValuesKPI225(), new ArrayList<>());
		List<String> effective =
				CollectionUtils.isNotEmpty(configured) ? configured : DEFAULT_REFINEMENT_ROOT_CAUSES;
		return lowerCaseSet(effective);
	}

	private static Set<String> lowerCaseSet(List<String> values) {
		return CollectionUtils.emptyIfNull(values).stream()
				.filter(StringUtils::isNotBlank)
				.map(value -> value.trim().toLowerCase(Locale.ROOT))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	// ────────────────────────────────────────────────────────────────────────
	// Period helpers
	// ────────────────────────────────────────────────────────────────────────

	/** Pre-seeds the trend with every period in the window, oldest first. */
	private Map<String, List<DefectRecord>> emptyPeriods(String weekOrMonth, int periodCount) {
		Map<String, List<DefectRecord>> periods = new LinkedHashMap<>();
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
	 * First instant of the oldest period that will be plotted. Fetching from exactly here — rather
	 * than a whole period earlier — keeps the excel export and the trend in agreement: every exported
	 * defect belongs to a period the chart actually shows.
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

	private static String periodLabel(String weekOrMonth, LocalDateTime dateTime) {
		if (weekOrMonth.equalsIgnoreCase(CommonConstant.WEEK)) {
			LocalDate monday =
					dateTime.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
			LocalDate sunday =
					dateTime.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
			return monday.format(WEEK_LABEL_FORMATTER) + " to " + sunday.format(WEEK_LABEL_FORMATTER);
		}
		return dateTime.getYear() + Constant.DASH + dateTime.getMonthValue();
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

	private static double round(double value) {
		return Math.round(value * 100d) / 100d;
	}

	private void populateExcelData(
			String requestTrackerId,
			List<KPIExcelData> excelData,
			List<DefectRecord> records,
			String projectName) {

		if (StringUtils.isEmpty(requestTrackerId)
				|| !requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			return;
		}
		records.stream()
				.sorted(Comparator.comparing(DefectRecord::period).thenComparing(DefectRecord::issueId))
				.forEach(
						defectRecord -> {
							KPIExcelData row = new KPIExcelData();
							row.setDaysWeeks(defectRecord.period());
							row.setProject(projectName);
							row.setIssueID(
									Map.of(defectRecord.issueId(), StringUtils.defaultString(defectRecord.url())));
							row.setIssueType(defectRecord.issueType());
							row.setIssueDesc(defectRecord.description());
							row.setPriority(defectRecord.priority());
							row.setStatus(defectRecord.status());
							row.setCreatedDate(defectRecord.createdDate());
							row.setRootCause(
									CollectionUtils.isEmpty(defectRecord.rootCauses())
											? new ArrayList<>()
											: new ArrayList<>(defectRecord.rootCauses()));
							row.setRootCauseCategory(defectRecord.category());
							excelData.add(row);
						});
	}

	/** Internal, per-defect projection used for both the trend and the excel export. */
	record DefectRecord(
			String issueId,
			String url,
			String issueType,
			String description,
			String status,
			String priority,
			String createdDate,
			List<String> rootCauses,
			String category,
			String period) {}

	/**
	 * Drill-down entry exposed on every data point: how many production defects of the period fall
	 * into a given root-cause category. Serialised as {@code {"category": "Refinement", "count": 7}}.
	 */
	public record RootCauseDrillDownValue(String category, long count) {}
}
