package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;
import static java.time.temporal.ChronoUnit.DAYS;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.enums.JiraFeatureHistory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.model.IterationKpiFilters;
import com.publicissapient.kpidashboard.apis.model.IterationKpiFiltersOptions;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.BacklogKpiHelper;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.dto.CycleTimeGroup;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CycleTimeTrendSlingshotServiceImpl
		extends JiraKPIService<Long, List<Object>, Map<String, Object>> {

	private static final String ISSUE_COUNT = "Issue Count";
	private static final String HISTORY = "history";
	private static final String OVERALL = "Overall";
	private static final String SEARCH_BY_ISSUE_TYPE = "Filter by issue type";
	private static final String SEARCH_BY_CYCLE_GROUP = "Filter by cycle group";

	@Autowired ConfigHelperService configHelperService;

	@Autowired CustomApiConfig customApiConfig;

	@Autowired JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

	@Autowired KpiHelperService kpiHelperService;

	@Override
	public String getQualifierType() {
		return KPICode.CYCLE_TIME_TREND_SLINGSHOT.name();
	}

	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {
		log.info("CYCLE TIME SLINGSHOT -> requestTrackerId[{}]", kpiRequest.getRequestTrackerId());
		List<Node> projectList =
				treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT);

		// in case if only projects or sprint filters are applied
		projectWiseLeafNodeValue(kpiElement, projectList.get(0), kpiRequest);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new LinkedHashMap<>();
		calculateAggregatedMultipleValueGroupMap(
				projectList.get(0), nodeWiseKPIValue, KPICode.CYCLE_TIME_TREND_SLINGSHOT);
		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMapUnSorted(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.CYCLE_TIME_TREND_SLINGSHOT);

		Map<String, Map<String, List<DataCount>>> priorityTypeProjectWiseDc = new LinkedHashMap<>();
		trendValuesMap.forEach(
				(priority, dataCounts) -> {
					Map<String, List<DataCount>> projectWiseDc =
							dataCounts.stream().collect(Collectors.groupingBy(DataCount::getData));
					priorityTypeProjectWiseDc.put(priority, projectWiseDc);
				});

		List<DataCountGroup> dataCountGroups = new ArrayList<>();
		priorityTypeProjectWiseDc.forEach(
				(filter, projectWiseDc) -> {
					DataCountGroup dataCountGroup = new DataCountGroup();
					List<DataCount> dataList = new ArrayList<>();
					projectWiseDc.forEach((key, value) -> dataList.addAll(value));
					// split for filters
					String[] issueFilter = filter.split("#");
					dataCountGroup.setFilter1(issueFilter[0]);
					dataCountGroup.setFilter2(issueFilter[1]);
					dataCountGroup.setValue(dataList);
					dataCountGroups.add(dataCountGroup);
				});

		kpiElement.setTrendValueList(dataCountGroups);
		kpiElement.setNodeWiseKPIValue(nodeWiseKPIValue);

		return kpiElement;
	}

	@Override
	public Long calculateKPIMetrics(Map<String, Object> stringObjectMap) {
		return 0L;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		Map<String, Object> resultListMap = new HashMap<>();
		leafNodeList.forEach(
				leafNode -> {
					Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();

					ObjectId basicProjectConfigId = leafNode.getProjectFilter().getBasicProjectConfigId();
					Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();

					FieldMapping fieldMapping =
							configHelperService.getFieldMappingMap().get(basicProjectConfigId);

					if (Optional.ofNullable(fieldMapping.getJiraIssueTypeKPI202()).isPresent()) {

						KpiDataHelper.prepareFieldMappingDefectTypeTransformation(
								mapOfProjectFilters,
								fieldMapping.getJiradefecttype(),
								fieldMapping.getJiraIssueTypeKPI202(),
								JiraFeatureHistory.STORY_TYPE.getFieldValueInFeature());
						uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);
					}

					List<CycleTimeGroup> issueTypesByGroups =
							fieldMapping.getJiraIssueStatusGroupByCategoryKPI202();

					List<String> status =
							new ArrayList<>(
									issueTypesByGroups.stream()
											.map(CycleTimeGroup::getStatuses)
											.flatMap(Collection::stream)
											.toList());
					mapOfProjectFilters.put(
							"statusUpdationLog.story.changedTo", CommonUtils.convertToPatternList(status));
					uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);

					List<JiraIssueCustomHistory> jiraIssueCustomHistoryList =
							jiraIssueCustomHistoryRepository.findByBasicProjectConfigIdIn(
									basicProjectConfigId.toString());
					List<JiraIssueCustomHistory> filteredProjectHistory =
							BacklogKpiHelper.filterProjectHistories(
									jiraIssueCustomHistoryList, uniqueProjectMap, startDate, endDate);

					resultListMap.put(basicProjectConfigId.toString(), filteredProjectHistory);
				});
		return resultListMap;
	}

	private void projectWiseLeafNodeValue(
			KpiElement kpiElement, Node leafNode, KpiRequest kpiRequest) {
		String requestTrackerId = getRequestTrackerId();

		String startDate = DateUtil.getTodayDate().minusMonths(6).toString();
		String endDate = DateUtil.getTodayDate().toString();
		Map<String, Object> resultMap =
				fetchKPIDataFromDb(List.of(leafNode), startDate, endDate, kpiRequest);

		List<KPIExcelData> excelData = new ArrayList<>();
		Set<String> issueTypesSet = new LinkedHashSet<>();
		Set<String> groupMapSet = new LinkedHashSet<>();
		List<String> rangeList = customApiConfig.getFlowEfficiencyXAxisRange();
		FieldMapping fieldMapping =
				leafNode != null
						? configHelperService
								.getFieldMappingMap()
								.get(leafNode.getProjectFilter().getBasicProjectConfigId())
						: new FieldMapping();
		List<JiraIssueCustomHistory> allIssueHistory =
				(List<JiraIssueCustomHistory>)
						resultMap.get(leafNode.getProjectFilter().getBasicProjectConfigId().toString());

		Map<String, Map<String, List<JiraIssueCustomHistory>>> rangeAndStatusWiseJiraIssueMap =
				new LinkedHashMap<>();
		Map<String, Map<String, List<Double>>> filterMap = new LinkedHashMap<>();
		filterDataBasedOnXAxisRangeWise(
				rangeList,
				allIssueHistory,
				rangeAndStatusWiseJiraIssueMap,
				filterMap,
				fieldMapping,
				issueTypesSet,
				groupMapSet);

		Map<String, List<DataCount>> datacountMap = new LinkedHashMap<>();

		filterMap.forEach(
				(key, map) -> {
					List<DataCount> dataCountList = new ArrayList<>();
					rangeList.forEach(
							range -> {
								List<Double> dataList = map.getOrDefault(range, List.of());
								Map<String, Object> hoverMap = new HashMap<>();
								hoverMap.put(ISSUE_COUNT, dataList.size());
								DataCount dataCount = new DataCount();
								double totalDays =
										Math.round(
														((dataList.stream()
																				.collect(Collectors.summingDouble(Double::doubleValue)))
																		/ 1440)
																* 10.0)
												/ 10.0;
								dataCount.setValue(totalDays);
								dataCount.setData(String.valueOf(totalDays));
								dataCount.setSSprintName(range);
								dataCount.setSSprintID(range);
								dataCount.setDate(range);
								dataCount.setSProjectName(leafNode.getProjectFilter().getName());
								dataCount.setKpiGroup(key);
								dataCount.setHoverValue(hoverMap);
								dataCountList.add(dataCount);
							});
					datacountMap.put(key, dataCountList);
				});

		if (leafNode != null) leafNode.setValue(datacountMap);
		// Create kpi level filters
		IterationKpiFiltersOptions filter1 =
				new IterationKpiFiltersOptions(SEARCH_BY_CYCLE_GROUP, groupMapSet);
		IterationKpiFiltersOptions filter2 =
				new IterationKpiFiltersOptions(SEARCH_BY_ISSUE_TYPE, issueTypesSet);
		IterationKpiFilters iterationKpiFilters = new IterationKpiFilters(filter1, filter2);
		List<String> xAxisRange = new ArrayList<>(rangeList);
		Collections.reverse(xAxisRange);
		kpiElement.setFilters(iterationKpiFilters);
		kpiElement.setxAxisValues(xAxisRange);
		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(KPIExcelColumn.CYCLE_TIME_SLINGSHOT.getColumns());
	}

	private void filterDataBasedOnXAxisRangeWise(
			List<String> xAxisRange,
			List<JiraIssueCustomHistory> projectWiseJiraIssueList,
			Map<String, Map<String, List<JiraIssueCustomHistory>>> rangeWiseJiraIssuesMap,
			Map<String, Map<String, List<Double>>> filterMap,
			FieldMapping fieldMapping,
			Set<String> issueTypesSet,
			Set<String> groupMapSet) {
		Map<Long, String> monthRangeMap = new HashMap<>();
		BacklogKpiHelper.initializeRangeMapForProjects(
				rangeWiseJiraIssuesMap, xAxisRange, monthRangeMap);

		projectWiseJiraIssueList.forEach(
				history -> {
					Iterator<CycleTimeGroup> iterator =
							fieldMapping.getJiraIssueStatusGroupByCategoryKPI202().iterator();
					CycleTimeGroup current = iterator.hasNext() ? iterator.next() : null;
					while (current != null) {
						LocalDateTime windowStart = null;
						LocalDateTime windowEnd = null;
						double minsDiff = 0;
						for (JiraHistoryChangeLog log : history.getStatusUpdationLog()) {
							if (current.getStatuses().contains(log.getChangedTo())) {
								if (windowStart == null) windowStart = log.getUpdatedOn();
								windowEnd = log.getUpdatedOn();
							} else if (windowStart != null) {
								if (current.getStatuses().contains(log.getChangedFrom()))
									windowEnd = log.getUpdatedOn();
								minsDiff += KpiDataHelper.calWeekMinutes(windowStart, windowEnd);
								windowStart = null;
							}
						}
						if (windowStart != null && iterator.hasNext()) {
							windowEnd = LocalDateTime.now();
							minsDiff += KpiDataHelper.calWeekMinutes(windowStart, windowEnd);
						}
						if (minsDiff > 0) {

							long daysBetween = DAYS.between(windowEnd.toLocalDate(), LocalDate.now());

							Map<String, List<Double>> cycleTimeByfilterMap =
									filterMap.computeIfAbsent(
											  current.getLabel() + "#" + history.getStoryType(), k -> new HashMap<>());

							double finalMinsDiff = minsDiff;
							monthRangeMap.forEach(
									(noOfDay, range) -> {
										if (noOfDay > daysBetween) {
											cycleTimeByfilterMap
													.computeIfAbsent(range, k -> new ArrayList<>())
													.add(finalMinsDiff);
										}
									});
							filterMap.put(
									current.getLabel() + "#" + history.getStoryType(), cycleTimeByfilterMap);
							groupMapSet.add(current.getLabel());
							issueTypesSet.add(history.getStoryType());
						}
						current = iterator.hasNext() ? iterator.next() : null;
					}
				});
	}
}
