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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.KpiDataCacheService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiDataProvider;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.Filters;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.forecast.ForecastingManager;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.model.DSRValidationData;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.NormalizedJira;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.SprintWiseStory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefectEscapeRateSlingshotServiceImpl
		extends JiraKPIService<Double, List<Object>, Map<String, Object>> {

	private static final String UATBUGKEY = "uatBugData";
	private static final String TOTALBUGKEY = "totalBugData";
	private static final String SPRINTSTORIES = "storyData";
	private static final String UAT = "Escaped Defects";
	private static final String TOTAL = "Total Defects";
	private static final String PROJFMAPPING = "projectFieldMapping";
	public static final String STORY_LIST_WO_DROP = "storyList";

	private static final String WEEKLY = "Weekly";
	private static final String BI_WEEKLY = "Bi-Weekly";
	private static final String MONTHLY = "Monthly";
	private static final String SPRINT = "Sprint";
	private static final DateTimeFormatter WEEK_LABEL_FORMATTER =
			DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
	private static final int MONTHLY_INPUT_WEEKS = 55;
	private static final int WEEKLY_DISPLAY_PERIODS = 12;
	private static final int BI_WEEKLY_INPUT_WEEKS = 24;
	private static final int MONTHLY_DISPLAY_PERIODS = 6;

	private final FilterHelperService filterHelperService;
	private final CacheService cacheService;
	private final KpiDataCacheService kpiDataCacheService;
	private final KpiDataProvider kpiDataProvider;

	@Autowired(required = false)
	private ForecastingManager forecastingManager;

	private List<String> sprintIdList = Collections.synchronizedList(new ArrayList<>());

	@Override
	public CacheService getCacheService() {
		return cacheService;
	}

	@Override
	public String getQualifierType() {
		return KPICode.DEFECT_ESCAPE_RATE_SLINGSHOT.name();
	}

	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {

		List<DataCount> trendValueList = new ArrayList<>();
		Node root = treeAggregatorDetail.getRoot();
		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();
		sprintIdList =
				treeAggregatorDetail.getMapOfListOfLeafNodes().get(CommonConstant.SPRINT_MASTER).stream()
						.map(node -> node.getSprintFilter().getId())
						.collect(Collectors.toList());

		// perProjectWeeklyDc holds weekly escape-rate DataCounts per project
		// (most-recent-first)
		Map<String, List<DataCount>> perProjectWeeklyDc = new LinkedHashMap<>();

		treeAggregatorDetail
				.getMapOfListOfLeafNodes()
				.forEach(
						(k, v) -> {
							if (Filters.getFilter(k) == Filters.SPRINT) {
								sprintWiseLeafNodeValue(mapTmp, v, trendValueList, kpiElement, kpiRequest);
							}
						});

		// Calendar-first: build Weekly/Bi-Weekly/Monthly from a full date-range fetch,
		// independent of which sprints are currently selected in the filter.
		buildCalendarGranularityData(
				treeAggregatorDetail.getMapOfListOfLeafNodes().get(CommonConstant.SPRINT_MASTER),
				kpiRequest,
				perProjectWeeklyDc);

		log.debug(
				"[DEFECT-ESCAPE-RATE-SLINGSHOT-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
				kpiRequest.getRequestTrackerId(),
				root);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedValueMap(root, nodeWiseKPIValue, KPICode.DEFECT_ESCAPE_RATE_SLINGSHOT);
		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMap(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.DEFECT_ESCAPE_RATE_SLINGSHOT);

		List<DataCountGroup> groups = buildMultiGranularityGroups(perProjectWeeklyDc, trendValueList);
		kpiElement.setTrendValueList(groups);

		return kpiElement;
	}

	@SuppressWarnings("unchecked")
	private List<DataCountGroup> buildMultiGranularityGroups(
			Map<String, List<DataCount>> perProjectWeeklyDc, List<DataCount> sprintTrendValueList) {

		String kpiId = KPICode.DEFECT_ESCAPE_RATE_SLINGSHOT.getKpiId();
		List<DataCountGroup> groups = new ArrayList<>();

		List<DataCount> weeklyOutput =
				buildGranularityOutput(perProjectWeeklyDc, WEEKLY_DISPLAY_PERIODS);
		DataCountGroup weeklyGroup = new DataCountGroup();
		weeklyGroup.setFilter(WEEKLY);
		weeklyGroup.setValue(weeklyOutput);
		Optional.ofNullable(forecastingManager)
				.ifPresent(
						fm -> fm.addForecastsToDataCount(weeklyGroup, flattenInner(weeklyOutput), kpiId));
		groups.add(weeklyGroup);

		List<DataCount> biWeeklyOutput = buildBiWeeklyEscapeRateOutput(perProjectWeeklyDc);
		DataCountGroup biWeeklyGroup = new DataCountGroup();
		biWeeklyGroup.setFilter(BI_WEEKLY);
		biWeeklyGroup.setValue(biWeeklyOutput);
		Optional.ofNullable(forecastingManager)
				.ifPresent(
						fm -> fm.addForecastsToDataCount(biWeeklyGroup, flattenInner(biWeeklyOutput), kpiId));
		groups.add(biWeeklyGroup);

		List<DataCount> monthlyOutput = buildMonthlyEscapeRateOutput(perProjectWeeklyDc);
		DataCountGroup monthlyGroup = new DataCountGroup();
		monthlyGroup.setFilter(MONTHLY);
		monthlyGroup.setValue(monthlyOutput);
		Optional.ofNullable(forecastingManager)
				.ifPresent(
						fm -> fm.addForecastsToDataCount(monthlyGroup, flattenInner(monthlyOutput), kpiId));
		groups.add(monthlyGroup);

		List<DataCount> sprintOutput = buildSprintEscapeRateOutput(sprintTrendValueList);
		DataCountGroup sprintGroup = new DataCountGroup();
		sprintGroup.setFilter(SPRINT);
		sprintGroup.setValue(sprintOutput);
		Optional.ofNullable(forecastingManager)
				.ifPresent(
						fm -> fm.addForecastsToDataCount(sprintGroup, flattenInner(sprintOutput), kpiId));
		groups.add(sprintGroup);

		return groups;
	}

	/** Extracts the inner DataCounts from project-wrapper DataCounts for use as forecasting input. */
	private List<DataCount> flattenInner(List<DataCount> wrappers) {
		List<DataCount> flat = new ArrayList<>();
		for (DataCount wrapper : wrappers) {
			if (wrapper.getValue() instanceof List<?> inner) {
				inner.forEach(
						item -> {
							if (item instanceof DataCount dc) flat.add(dc);
						});
			}
		}
		return flat;
	}

	/**
	 * Slices the most-recent N periods (most-recent-first) and reverses for display (oldest-first).
	 */
	private List<DataCount> buildGranularityOutput(
			Map<String, List<DataCount>> perProject, int maxPeriods) {
		List<DataCount> result = new ArrayList<>();
		for (Map.Entry<String, List<DataCount>> entry : perProject.entrySet()) {
			List<DataCount> periods = entry.getValue();
			if (maxPeriods > 0 && periods.size() > maxPeriods) {
				periods = periods.subList(0, maxPeriods);
			}
			List<DataCount> oldestFirst = new ArrayList<>(periods);
			Collections.reverse(oldestFirst);
			DataCount wrapper = new DataCount();
			wrapper.setData(entry.getKey());
			wrapper.setValue(oldestFirst);
			result.add(wrapper);
		}
		return result;
	}

	private List<DataCount> buildBiWeeklyEscapeRateOutput(Map<String, List<DataCount>> perProject) {
		int maxInput = BI_WEEKLY_INPUT_WEEKS;
		List<DataCount> result = new ArrayList<>();
		for (Map.Entry<String, List<DataCount>> entry : perProject.entrySet()) {
			List<DataCount> weeklyPeriods = entry.getValue();
			if (weeklyPeriods.size() > maxInput) {
				weeklyPeriods = weeklyPeriods.subList(0, maxInput);
			}
			// Group consecutive pairs (most-recent-first, so pair [0,1] is newest bi-week)
			List<DataCount> biWeeklyList = new ArrayList<>();
			for (int i = 0; i < weeklyPeriods.size(); i += 2) {
				List<DataCount> pair = weeklyPeriods.subList(i, Math.min(i + 2, weeklyPeriods.size()));
				// Reverse pair to get oldest-first for label construction
				List<DataCount> pairOldFirst = new ArrayList<>(pair);
				Collections.reverse(pairOldFirst);
				String firstId = pairOldFirst.get(0).getsSprintID();
				String lastId = pairOldFirst.get(pairOldFirst.size() - 1).getsSprintID();
				String startPart =
						firstId != null && firstId.contains(" to ") ? firstId.split(" to ")[0].trim() : firstId;
				String endPart =
						lastId != null && lastId.contains(" to ") ? lastId.split(" to ")[1].trim() : lastId;
				String biWeekLabel = startPart + " to " + endPart;
				int pairUat = extractCount(pair, UAT);
				int pairTotal = extractCount(pair, TOTAL);
				double rate = pairTotal > 0 ? Math.round(100.0 * pairUat / pairTotal) : 0.0;
				Map<String, Object> hoverValue = new HashMap<>();
				hoverValue.put(UAT, pairUat);
				hoverValue.put(TOTAL, pairTotal);
				DataCount dc = new DataCount();
				dc.setSProjectName(pair.get(0).getSProjectName());
				dc.setSSprintID(biWeekLabel);
				dc.setSSprintName(biWeekLabel);
				dc.setData(String.valueOf(rate));
				dc.setValue(rate);
				dc.setHoverValue(hoverValue);
				biWeeklyList.add(dc);
			}
			Collections.reverse(biWeeklyList); // oldest-first for display
			DataCount wrapper = new DataCount();
			wrapper.setData(entry.getKey());
			wrapper.setValue(biWeeklyList);
			result.add(wrapper);
		}
		return result;
	}

	private List<DataCount> buildMonthlyEscapeRateOutput(Map<String, List<DataCount>> perProject) {
		int maxMonthly = MONTHLY_DISPLAY_PERIODS;
		List<DataCount> result = new ArrayList<>();
		for (Map.Entry<String, List<DataCount>> entry : perProject.entrySet()) {
			List<DataCount> weeklyPeriods = entry.getValue();
			// Group into 28-day buckets using the week-start date in the sSSprintID label
			TreeMap<Long, List<DataCount>> buckets = new TreeMap<>();
			LocalDate anchor = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
			for (DataCount dc : weeklyPeriods) {
				String sprintId = dc.getsSprintID();
				if (sprintId == null) continue;
				try {
					String startPart =
							sprintId.contains(" to ") ? sprintId.split(" to ")[0].trim() : sprintId.trim();
					LocalDate weekStart = LocalDate.parse(startPart, WEEK_LABEL_FORMATTER);
					long daysDiff = anchor.toEpochDay() - weekStart.toEpochDay();
					long bucketIdx = daysDiff / 28;
					buckets.computeIfAbsent(bucketIdx, k -> new ArrayList<>()).add(dc);
				} catch (Exception e) {
					log.warn(
							"[DEFECT-ESCAPE-RATE-SLINGSHOT] Could not parse week label for monthly grouping: {}",
							sprintId);
				}
			}
			List<Map.Entry<Long, List<DataCount>>> sortedBuckets = new ArrayList<>(buckets.entrySet());
			if (sortedBuckets.size() > maxMonthly) {
				sortedBuckets = sortedBuckets.subList(0, maxMonthly); // lowest indices = most recent
			}
			List<DataCount> monthlyList = new ArrayList<>();
			for (Map.Entry<Long, List<DataCount>> bucketEntry : sortedBuckets) {
				List<DataCount> bucketWeeks = new ArrayList<>(bucketEntry.getValue());
				int monthUat = extractCount(bucketWeeks, UAT);
				int monthTotal = extractCount(bucketWeeks, TOTAL);
				double rate = monthTotal > 0 ? Math.round(100.0 * monthUat / monthTotal) : 0.0;
				// Label: oldest week-start to newest week-end in bucket
				String firstId = bucketWeeks.get(bucketWeeks.size() - 1).getsSprintID();
				String lastId = bucketWeeks.get(0).getsSprintID();
				String startPart =
						firstId != null && firstId.contains(" to ") ? firstId.split(" to ")[0].trim() : firstId;
				String endPart =
						lastId != null && lastId.contains(" to ") ? lastId.split(" to ")[1].trim() : lastId;
				String monthLabel = startPart + " to " + endPart;
				Map<String, Object> hoverValue = new HashMap<>();
				hoverValue.put(UAT, monthUat);
				hoverValue.put(TOTAL, monthTotal);
				DataCount dc = new DataCount();
				dc.setSProjectName(bucketWeeks.get(0).getSProjectName());
				dc.setSSprintID(monthLabel);
				dc.setSSprintName(monthLabel);
				dc.setData(String.valueOf(rate));
				dc.setValue(rate);
				dc.setHoverValue(hoverValue);
				monthlyList.add(dc);
			}
			Collections.reverse(monthlyList); // oldest-first
			DataCount wrapper = new DataCount();
			wrapper.setData(entry.getKey());
			wrapper.setValue(monthlyList);
			result.add(wrapper);
		}
		return result;
	}

	/** Wraps per-sprint "Overall" DataCounts (from the existing sprint computation) per project. */
	private List<DataCount> buildSprintEscapeRateOutput(List<DataCount> sprintTrendValueList) {
		Map<String, List<DataCount>> perProject = new LinkedHashMap<>();
		for (DataCount dc : sprintTrendValueList) {
			if (CommonConstant.OVERALL.equalsIgnoreCase(dc.getKpiGroup())) {
				perProject.computeIfAbsent(dc.getSProjectName(), k -> new ArrayList<>()).add(dc);
			}
		}
		List<DataCount> result = new ArrayList<>();
		perProject.forEach(
				(project, sprints) -> {
					DataCount wrapper = new DataCount();
					wrapper.setData(project);
					wrapper.setValue(sprints);
					result.add(wrapper);
				});
		return result;
	}

	private int extractCount(List<DataCount> dcs, String key) {
		return dcs.stream()
				.mapToInt(
						dc -> {
							if (dc.getHoverValue() == null) return 0;
							Object v = dc.getHoverValue().get(key);
							return v instanceof Number n ? n.intValue() : 0;
						})
				.sum();
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {

		Map<String, Object> resultListMap = new HashMap<>();
		Map<ObjectId, List<String>> projectWiseSprints = new HashMap<>();
		leafNodeList.forEach(
				leaf -> {
					ObjectId basicProjectConfigId = leaf.getProjectFilter().getBasicProjectConfigId();
					String sprint = leaf.getSprintFilter().getId();
					projectWiseSprints.putIfAbsent(basicProjectConfigId, new ArrayList<>());
					projectWiseSprints.get(basicProjectConfigId).add(sprint);
				});

		List<SprintWiseStory> sprintWiseStoryList = new ArrayList<>();
		List<JiraIssue> remainingDefect = new ArrayList<>();
		Map<String, FieldMapping> projFieldMapping = new HashMap<>();
		List<JiraIssue> storyListWoDrop = new ArrayList<>();
		boolean fetchCachedData =
				filterHelperService.isFilterSelectedTillSprintLevel(kpiRequest.getLevel(), false);
		projectWiseSprints.forEach(
				(basicProjectConfigId, sprintList) -> {
					Map<String, Object> result;
					if (fetchCachedData) {
						result =
								kpiDataCacheService.fetchDefectEscapeRateSlingshotData(
										kpiRequest,
										basicProjectConfigId,
										sprintIdList,
										KPICode.DEFECT_ESCAPE_RATE_SLINGSHOT.getKpiId());
					} else {
						result =
								kpiDataProvider.fetchDefectEscapeRateSlingshotData(
										kpiRequest, basicProjectConfigId, sprintList);
					}

					sprintWiseStoryList.addAll(
							(List<SprintWiseStory>) result.getOrDefault(SPRINTSTORIES, new ArrayList<>()));
					remainingDefect.addAll(
							(List<JiraIssue>) result.getOrDefault(TOTALBUGKEY, new ArrayList<>()));
					projFieldMapping.put(
							basicProjectConfigId.toString(),
							((Map<String, FieldMapping>) result.get(PROJFMAPPING))
									.get(basicProjectConfigId.toString()));
					storyListWoDrop.addAll(
							(List<JiraIssue>) result.getOrDefault(STORY_LIST_WO_DROP, new ArrayList<>()));
				});

		resultListMap.put(SPRINTSTORIES, sprintWiseStoryList);
		resultListMap.put(TOTALBUGKEY, remainingDefect);
		resultListMap.put(PROJFMAPPING, projFieldMapping);
		resultListMap.put(STORY_LIST_WO_DROP, storyListWoDrop);
		return resultListMap;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Double calculateKPIMetrics(Map<String, Object> filterComponentIdWiseDefectMap) {
		String requestTrackerId = getRequestTrackerId();
		Double escapeRatePercentage = 0d;
		List<JiraIssue> uatBugList = (List<JiraIssue>) filterComponentIdWiseDefectMap.get(UATBUGKEY);
		List<JiraIssue> totalBugList =
				(List<JiraIssue>) filterComponentIdWiseDefectMap.get(TOTALBUGKEY);
		if (CollectionUtils.isNotEmpty(uatBugList) && CollectionUtils.isNotEmpty(totalBugList)) {
			int uatDefectCount = uatBugList.size();
			int totalDefectCount = totalBugList.size();

			log.debug(
					"[JIRA-DEFECT-ESCAPE-RATE-SLINGSHOT][{}]. UAT Defect count: {}  Total Defect Count: {}",
					requestTrackerId,
					uatDefectCount,
					totalDefectCount);
			escapeRatePercentage = (double) Math.round((100.0 * uatDefectCount) / (totalDefectCount));
		}
		return escapeRatePercentage;
	}

	@SuppressWarnings("unchecked")
	private void sprintWiseLeafNodeValue(
			Map<String, Node> mapTmp,
			List<Node> sprintLeafNodeList,
			List<DataCount> trendValueList,
			KpiElement kpiElement,
			KpiRequest kpiRequest) {

		String requestTrackerId = getRequestTrackerId();

		Collections.sort(
				sprintLeafNodeList,
				(Node o1, Node o2) ->
						o1.getSprintFilter().getStartDate().compareTo(o2.getSprintFilter().getStartDate()));
		long time = System.currentTimeMillis();
		Map<String, Object> defectDataListMap =
				fetchKPIDataFromDb(sprintLeafNodeList, null, null, kpiRequest);
		log.info(
				"DefectEscapeRateSlingshot taking fetchKPIDataFromDb {}",
				System.currentTimeMillis() - time);

		List<SprintWiseStory> sprintWiseStoryList =
				(List<SprintWiseStory>) defectDataListMap.get(SPRINTSTORIES);
		Map<String, FieldMapping> projFieldMapping =
				(Map<String, FieldMapping>) defectDataListMap.get(PROJFMAPPING);

		Map<Pair<String, String>, List<SprintWiseStory>> sprintWiseMap =
				sprintWiseStoryList.stream()
						.collect(
								Collectors.groupingBy(
										sws -> Pair.of(sws.getBasicProjectConfigId(), sws.getSprint()),
										Collectors.toList()));
		List<JiraIssue> totalStoryWoDrop = (List<JiraIssue>) defectDataListMap.get(STORY_LIST_WO_DROP);
		List<JiraIssue> totalDefects = (List<JiraIssue>) defectDataListMap.get(TOTALBUGKEY);
		Map<Pair<String, String>, List<JiraIssue>> unlinkedDefect =
				totalDefects.stream()
						.filter(issue -> CollectionUtils.isEmpty(issue.getDefectStoryID()))
						.collect(
								Collectors.groupingBy(
										sws -> Pair.of(sws.getBasicProjectConfigId(), sws.getSprintID()),
										Collectors.toList()));

		List<KPIExcelData> excelData = new ArrayList<>();

		sprintLeafNodeList.forEach(
				node -> {
					Set<String> uatLabels = new HashSet<>();
					Map<String, List<DataCount>> dataCountMap = new HashMap<>();
					String trendLineName = node.getProjectFilter().getName();
					String currentSprintComponentId = node.getSprintFilter().getId();
					Pair<String, String> currentNodeIdentifier =
							Pair.of(
									node.getProjectFilter().getBasicProjectConfigId().toString(),
									currentSprintComponentId);
					FieldMapping fieldMapping =
							projFieldMapping.get(node.getProjectFilter().getBasicProjectConfigId().toString());

					List<SprintWiseStory> sprintWiseStories =
							sprintWiseMap.getOrDefault(currentNodeIdentifier, new ArrayList<>());
					List<String> totalStoryIdList = new ArrayList<>();
					sprintWiseStories.stream()
							.map(SprintWiseStory::getStoryList)
							.toList()
							.forEach(totalStoryIdList::addAll);
					List<JiraIssue> subCategoryWiseTotalBugList =
							totalDefects.stream()
									.filter(f -> CollectionUtils.containsAny(f.getDefectStoryID(), totalStoryIdList))
									.collect(Collectors.toList());

					if (fieldMapping != null && !fieldMapping.isExcludeUnlinkedDefects()) {
						subCategoryWiseTotalBugList.addAll(
								unlinkedDefect.getOrDefault(currentNodeIdentifier, new ArrayList<>()));
					}
					Map<String, List<JiraIssue>> uatDefect =
							checkUATDefect(subCategoryWiseTotalBugList, fieldMapping, uatLabels);

					Map<String, Double> finalMap = new HashMap<>();
					Map<String, Object> overallHowerMap = new HashMap<>();
					List<DSRValidationData> validationDataList = new ArrayList<>();
					int totalUatCount = 0;
					if (CollectionUtils.isNotEmpty(uatLabels)) {
						for (String lowerCaseLabel : uatLabels) {
							String label = lowerCaseLabel.toLowerCase();
							List<JiraIssue> issueList = uatDefect.getOrDefault(label, new ArrayList<>());
							int totalDefectCount = subCategoryWiseTotalBugList.size();
							Map<String, Object> howerMap = new HashMap<>();
							Double escapeRatePercentage = 0d;
							if (CollectionUtils.isNotEmpty(issueList)
									&& CollectionUtils.isNotEmpty(subCategoryWiseTotalBugList)) {
								createValidationData(issueList, label, validationDataList);
								int uatDefectCount = issueList.size();
								howerMap.put(UAT, uatDefectCount);
								escapeRatePercentage =
										(double) Math.round((100.0 * uatDefectCount) / (totalDefectCount));
							} else {
								howerMap.put(UAT, 0);
							}
							howerMap.put(TOTAL, totalDefectCount);
							finalMap.put(label, escapeRatePercentage);
							overallHowerMap.put(label, howerMap);
						}

						uatLabels.forEach(label -> finalMap.computeIfAbsent(label, val -> 0D));
						Double overAllCount = finalMap.values().stream().mapToDouble(val -> val).sum();
						finalMap.put(CommonConstant.OVERALL, overAllCount);

						totalUatCount = uatDefect.values().stream().mapToInt(List::size).sum();
						Map<String, Object> overHowerMap = new HashMap<>();
						overHowerMap.put(UAT, totalUatCount);
						overHowerMap.put(TOTAL, subCategoryWiseTotalBugList.size());
						overallHowerMap.put(CommonConstant.OVERALL, overHowerMap);
					}

					createDataCount(
							trendValueList,
							node,
							dataCountMap,
							trendLineName,
							finalMap,
							overallHowerMap,
							subCategoryWiseTotalBugList.size());

					String weekLabel = buildWeekLabel(node.getSprintFilter().getEndDate());
					populateExcel(
							requestTrackerId,
							subCategoryWiseTotalBugList,
							validationDataList,
							excelData,
							node,
							totalStoryWoDrop,
							fieldMapping,
							weekLabel);
					mapTmp.get(node.getId()).setValue(dataCountMap);
				});

		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(
				KPIExcelColumn.DEFECT_ESCAPE_RATE_SLINGSHOT.getColumns(
						sprintLeafNodeList, cacheService, filterHelperService));
	}

	/**
	 * Calendar-first: fetches all sprints for each project within the last MONTHLY_INPUT_WEEKS window
	 * and builds the perProjectWeeklyDc map used for Weekly/Bi-Weekly/Monthly granularities. Sprint
	 * view is unaffected (computed separately in sprintWiseLeafNodeValue).
	 */
	@SuppressWarnings("unchecked")
	private void buildCalendarGranularityData(
			List<Node> sprintLeafNodes,
			KpiRequest kpiRequest,
			Map<String, List<DataCount>> perProjectWeeklyDc) {

		if (CollectionUtils.isEmpty(sprintLeafNodes)) return;

		boolean fetchCachedData =
				filterHelperService.isFilterSelectedTillSprintLevel(kpiRequest.getLevel(), false);

		Map<ObjectId, String> projectIdToName = new LinkedHashMap<>();
		sprintLeafNodes.forEach(
				node ->
						projectIdToName.putIfAbsent(
								node.getProjectFilter().getBasicProjectConfigId(),
								node.getProjectFilter().getName()));

		projectIdToName.forEach(
				(basicProjectConfigId, projectName) -> {
					Map<String, Object> result;
					if (fetchCachedData) {
						result =
								kpiDataCacheService.fetchDefectEscapeRateSlingshotDataByDateRange(
										kpiRequest,
										basicProjectConfigId,
										KPICode.DEFECT_ESCAPE_RATE_SLINGSHOT.getKpiId());
					} else {
						result =
								kpiDataProvider.fetchDefectEscapeRateSlingshotDataByDateRange(
										kpiRequest, basicProjectConfigId);
					}

					List<JiraIssue> allDefects =
							(List<JiraIssue>) result.getOrDefault(TOTALBUGKEY, new ArrayList<>());
					FieldMapping fieldMapping =
							((Map<String, FieldMapping>) result.get(PROJFMAPPING))
									.get(basicProjectConfigId.toString());

					List<DataCount> weeklyDcs =
							buildDefectCreationDateEscapeRateCounts(allDefects, fieldMapping, projectName);
					if (!weeklyDcs.isEmpty()) {
						perProjectWeeklyDc.put(projectName, weeklyDcs);
					}
				});
	}

	/**
	 * Buckets defects by creation date into 7-day calendar windows and computes escape rate (UAT
	 * defects / total defects) per bucket. Returns most-recent-first.
	 */
	@SuppressWarnings("unchecked")
	private List<DataCount> buildDefectCreationDateEscapeRateCounts(
			List<JiraIssue> allDefects, FieldMapping fieldMapping, String projectName) {

		LocalDate anchor = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

		// Group defects by weekly bucket index (0 = most recent week ending on anchor)
		Map<Integer, List<JiraIssue>> defectsByBucket = new HashMap<>();
		for (JiraIssue defect : allDefects) {
			String createdStr = defect.getCreatedDate();
			if (createdStr == null || createdStr.length() < 10) continue;
			try {
				LocalDate createdDate = LocalDate.parse(createdStr.substring(0, 10));
				long daysFromAnchor = anchor.toEpochDay() - createdDate.toEpochDay();
				if (daysFromAnchor < 0 || daysFromAnchor >= (long) MONTHLY_INPUT_WEEKS * 7) continue;
				int bucketIdx = (int) (daysFromAnchor / 7);
				defectsByBucket.computeIfAbsent(bucketIdx, k -> new ArrayList<>()).add(defect);
			} catch (Exception e) {
				// skip defects with unparseable creation dates
			}
		}

		// Compute UAT vs total per bucket using the same UAT-detection logic as the
		// sprint path
		TreeMap<Integer, int[]> weekBuckets = new TreeMap<>();
		defectsByBucket.forEach(
				(bucketIdx, defects) -> {
					Set<String> uatLabels = new HashSet<>();
					Map<String, List<JiraIssue>> uatDefectMap =
							checkUATDefect(defects, fieldMapping, uatLabels);
					int uatCount = uatDefectMap.values().stream().mapToInt(List::size).sum();
					weekBuckets.put(bucketIdx, new int[] {uatCount, defects.size()});
				});

		if (weekBuckets.isEmpty()) return Collections.emptyList();

		// Pad with zeros so bi-weekly/monthly aggregation has a full continuous range
		int padTo = Math.max(weekBuckets.lastKey(), MONTHLY_INPUT_WEEKS - 1);
		for (int i = 0; i <= padTo; i++) {
			weekBuckets.putIfAbsent(i, new int[] {0, 0});
		}

		// Convert to DataCount list (most-recent-first = lowest bucket index first)
		List<DataCount> result = new ArrayList<>();
		for (Map.Entry<Integer, int[]> bucketEntry : weekBuckets.entrySet()) {
			int bucketIdx = bucketEntry.getKey();
			int[] counts = bucketEntry.getValue();
			int uatCount = counts[0];
			int totalCount = counts[1];
			double rate = totalCount > 0 ? Math.round(100.0 * uatCount / totalCount) : 0.0;

			LocalDate weekEnd = anchor.minusDays(7L * bucketIdx);
			LocalDate weekStart = weekEnd.minusDays(6);
			String weekLabel =
					weekStart.format(WEEK_LABEL_FORMATTER) + " to " + weekEnd.format(WEEK_LABEL_FORMATTER);

			Map<String, Object> hoverValue = new HashMap<>();
			hoverValue.put(UAT, uatCount);
			hoverValue.put(TOTAL, totalCount);

			DataCount dc = new DataCount();
			dc.setSProjectName(projectName);
			dc.setSSprintID(weekLabel);
			dc.setSSprintName(weekLabel);
			dc.setData(String.valueOf(rate));
			dc.setValue(rate);
			dc.setHoverValue(hoverValue);
			result.add(dc);
		}
		return result;
	}

	private String buildWeekLabel(String sprintEndDateStr) {
		if (sprintEndDateStr == null || sprintEndDateStr.length() < 10) return null;
		try {
			LocalDate sprintEnd = LocalDate.parse(sprintEndDateStr.substring(0, 10));
			LocalDate anchor = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
			long daysFromAnchor = anchor.toEpochDay() - sprintEnd.toEpochDay();
			if (daysFromAnchor < 0) daysFromAnchor = 0;
			int bucketIdx = (int) (daysFromAnchor / 7);
			LocalDate weekEnd = anchor.minusDays(7L * bucketIdx);
			LocalDate weekStart = weekEnd.minusDays(6);
			return weekStart.format(WEEK_LABEL_FORMATTER) + " to " + weekEnd.format(WEEK_LABEL_FORMATTER);
		} catch (Exception e) {
			return null;
		}
	}

	private void createValidationData(
			List<JiraIssue> issueList, String label, List<DSRValidationData> validationDataList) {
		issueList.forEach(
				issue -> validationDataList.add(new DSRValidationData(issue.getNumber(), label)));
	}

	private void populateExcel(
			String requestTrackerId,
			List<JiraIssue> sprintWiseSubCategoryWiseTotalBugListMap,
			List<DSRValidationData> subCategoryWiseUatBugList,
			List<KPIExcelData> excelData,
			Node node,
			List<JiraIssue> totalStoryWoDrop,
			FieldMapping fieldMapping,
			String daysWeeks) {

		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			Map<String, JiraIssue> totalBugList = new HashMap<>();
			sprintWiseSubCategoryWiseTotalBugListMap.forEach(
					bugs -> totalBugList.putIfAbsent(bugs.getNumber(), bugs));

			KPIExcelUtility.populateDefectEscapeRateSlingshotExcelData(
					node.getSprintFilter().getName(),
					totalBugList,
					subCategoryWiseUatBugList,
					excelData,
					fieldMapping,
					totalStoryWoDrop,
					daysWeeks);
		}
	}

	private void createDataCount(
			List<DataCount> trendValueList,
			Node node,
			Map<String, List<DataCount>> dataCountMap,
			String trendLineName,
			Map<String, Double> finalMap,
			Map<String, Object> overallHowerMap,
			int totalBug) {
		if (MapUtils.isNotEmpty(finalMap)) {
			finalMap.forEach(
					(label, value) -> {
						DataCount dataCount =
								getDataCountObject(node, trendLineName, overallHowerMap, label, value);
						trendValueList.add(dataCount);
						dataCountMap.computeIfAbsent(label, k -> new ArrayList<>()).add(dataCount);
					});
		} else {
			Map<String, Object> overHowerMap = new HashMap<>();
			overHowerMap.put(UAT, 0);
			overHowerMap.put(TOTAL, totalBug);
			Map<String, Object> defaultMap = new HashMap<>();
			defaultMap.put(CommonConstant.OVERALL, overHowerMap);

			DataCount dataCount =
					getDataCountObject(node, trendLineName, defaultMap, CommonConstant.OVERALL, 0D);
			trendValueList.add(dataCount);
			dataCountMap.computeIfAbsent(CommonConstant.OVERALL, k -> new ArrayList<>()).add(dataCount);
		}
	}

	private DataCount getDataCountObject(
			Node node,
			String trendLineName,
			Map<String, Object> overAllHoverValueMap,
			String key,
			Double value) {
		DataCount dataCount = new DataCount();
		dataCount.setData(String.valueOf(value));
		dataCount.setSProjectName(trendLineName);
		dataCount.setSSprintID(node.getSprintFilter().getId());
		dataCount.setSSprintName(node.getSprintFilter().getName());
		dataCount.setValue(value);
		dataCount.setKpiGroup(key);
		dataCount.setHoverValue((Map<String, Object>) overAllHoverValueMap.get(key));
		return dataCount;
	}

	private Map<String, List<JiraIssue>> checkUATDefect(
			List<JiraIssue> testCaseList, FieldMapping fieldMapping, Set<String> labels) {
		Map<String, List<JiraIssue>> uatMap = new HashMap<>();
		if (null != fieldMapping
				&& StringUtils.isNotEmpty(fieldMapping.getJiraBugRaisedByIdentification())
				&& CollectionUtils.isNotEmpty(fieldMapping.getJiraBugRaisedByValue())) {
			Set<String> jiraBugRaisedByValue = new HashSet<>();
			fieldMapping
					.getJiraBugRaisedByValue()
					.forEach(value -> jiraBugRaisedByValue.add(value.toLowerCase()));
			labels.addAll(jiraBugRaisedByValue);
			if (fieldMapping
					.getJiraBugRaisedByIdentification()
					.trim()
					.equalsIgnoreCase(Constant.LABELS)) {
				testCaseList.stream()
						.filter(
								testCase ->
										CollectionUtils.isNotEmpty(testCase.getLabels())
												&& testCase.getLabels().stream()
														.anyMatch(label -> jiraBugRaisedByValue.contains(label.toLowerCase())))
						.forEach(
								jIssue ->
										jIssue
												.getLabels()
												.forEach(
														label ->
																uatMap
																		.computeIfAbsent(label.toLowerCase(), k -> new ArrayList<>())
																		.add(jIssue)));
			} else {
				testCaseList.stream()
						.filter(
								f ->
										NormalizedJira.THIRD_PARTY_DEFECT_VALUE
												.getValue()
												.equalsIgnoreCase(f.getDefectRaisedBy()))
						.toList()
						.stream()
						.filter(issue -> CollectionUtils.isNotEmpty(issue.getUatDefectGroup()))
						.forEach(
								issue ->
										issue
												.getUatDefectGroup()
												.forEach(
														label ->
																uatMap
																		.computeIfAbsent(label.toLowerCase(), k -> new ArrayList<>())
																		.add(issue)));
			}
		}
		uatMap.keySet().removeIf(key -> !labels.contains(key));
		return uatMap;
	}

	@Override
	public Double calculateKpiValue(List<Double> valueList, String kpiName) {
		return calculateKpiValueForDouble(valueList, kpiName);
	}

	@Override
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI216(), KPICode.DEFECT_ESCAPE_RATE_SLINGSHOT.getKpiId());
	}
}
