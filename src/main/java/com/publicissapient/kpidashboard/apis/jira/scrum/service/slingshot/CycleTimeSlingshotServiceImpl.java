package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.enums.JiraFeatureHistory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
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
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.CycleTimeValidationData;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.DataValue;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.dto.CycleTimeGroup;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CycleTimeSlingshotServiceImpl
		extends JiraKPIService<Long, List<Object>, Map<String, Object>> {

	private static final String SEARCH_BY_ISSUE_TYPE = "Issue Type";
	private static final String SEARCH_BY_DURATION = "Duration";
	private static final Set<String> durationFilter =
			new LinkedHashSet<>(
					Arrays.asList(
							"Past 6 Months", "Past 3 Months", "Past Month", "Past 2 Weeks", "Past Week"));

	private final ConfigHelperService configHelperService;
	private final JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

	@Override
	public String getQualifierType() {
		return KPICode.CYCLE_TIME_SLINGSHOT.name();
	}

	@Override
	public KpiElement getKpiData(
			KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail)
			throws ApplicationException {
		log.info("CYCLE TIME SLINGSHOT -> requestTrackerId[{}]", kpiRequest.getRequestTrackerId());
		List<Node> projectList =
				treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT);
		Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();

		// in case if only projects or sprint filters are applied
		projectWiseLeafNodeValue(kpiElement, mapTmp, projectList, kpiRequest);

		Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
		calculateAggregatedMultipleValueGroupMap(
				treeAggregatorDetail.getRoot(), nodeWiseKPIValue, KPICode.CYCLE_TIME_SLINGSHOT);
		Map<String, List<DataCount>> trendValuesMap =
				getTrendValuesMapUnSorted(
						kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.CYCLE_TIME_SLINGSHOT);

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
	public Double calculateThresholdValue(FieldMapping fieldMapping) {
		return calculateThresholdValue(
				fieldMapping.getThresholdValueKPI202(), KPICode.CYCLE_TIME_TREND_SLINGSHOT.getKpiId());
	}

	private void projectWiseLeafNodeValue(
			KpiElement kpiElement,
			Map<String, Node> mapTmp,
			List<Node> leafNodeList,
			KpiRequest kpiRequest) {
		KpiElement leadTimeReq =
				kpiRequest.getKpiList().stream()
						.filter(k -> k.getKpiId().equalsIgnoreCase("kpi202"))
						.findFirst()
						.orElse(new KpiElement());

		LinkedHashMap<String, Object> filterDuration =
				(LinkedHashMap<String, Object>) leadTimeReq.getFilterDuration();
		int value;
		String duration;
		String startDate = null;
		String endDate = DateUtil.getTodayDate().toString();

		if (filterDuration != null) {
			value = (int) filterDuration.getOrDefault("value", 6);
			duration = (String) filterDuration.getOrDefault("duration", CommonConstant.MONTH);
		} else {
			value = 6;
			duration = CommonConstant.MONTH;
		}
		if (duration.equalsIgnoreCase(CommonConstant.WEEK)) {
			startDate = DateUtil.getTodayDate().minusWeeks(value).toString();
		} else {
			startDate = DateUtil.getTodayDate().minusMonths(value).toString();
		}
		Map<String, Map<Integer, String>> durationLabels = new HashMap<>();
		durationLabels.put(CommonConstant.WEEK, Map.of(1, "Past Week", 2, "Past 2 Weeks"));
		durationLabels.put(
				CommonConstant.MONTH, Map.of(1, "Past Month", 3, "Past 3 Months", 6, "Past 6 Months"));

		String label =
				Optional.ofNullable(durationLabels.get(duration)).map(m -> m.get(value)).orElse("Unknown");
		Map<String, Object> resultMap =
				fetchKPIDataFromDb(leafNodeList, startDate, endDate, kpiRequest);
		List<KPIExcelData> excelData = new ArrayList<>();
		Set<String> issueTypeFilter = new LinkedHashSet<>();
		List<CycleTimeValidationData> cycleTimeValidationDataList = new ArrayList<>();
		leafNodeList.forEach(
				leafNode -> {
					ObjectId basicProjectConfigId = leafNode.getProjectFilter().getBasicProjectConfigId();
					FieldMapping fieldMapping =
							configHelperService.getFieldMappingMap().get(basicProjectConfigId);
					List<JiraIssueCustomHistory> jiraIssueCustomHistoryList =
							(List<JiraIssueCustomHistory>) resultMap.get(basicProjectConfigId.toString());
					Map<String, List<DataValue>> cycleMap =
							getCycleTimeDataCount(
									jiraIssueCustomHistoryList,
									fieldMapping,
									issueTypeFilter,
									cycleTimeValidationDataList);
					Map<String, List<DataCount>> dataCountMap =
							getDataCountObject(leafNode.getProjectFilter().getName(), cycleMap, label);
					mapTmp.get(leafNode.getId()).setValue(dataCountMap);
				});
		populateExcelDataObject(
				kpiRequest.getRequestTrackerId(), cycleTimeValidationDataList, excelData);
		IterationKpiFiltersOptions filter1 =
				new IterationKpiFiltersOptions(SEARCH_BY_DURATION, durationFilter);
		IterationKpiFiltersOptions filter2 =
				new IterationKpiFiltersOptions(SEARCH_BY_ISSUE_TYPE, issueTypeFilter);
		IterationKpiFilters iterationKpiFilters = new IterationKpiFilters(filter1, filter2);
		// Modal Heads Options
		kpiElement.setFilters(iterationKpiFilters);
		kpiElement.setExcelData(excelData);
		kpiElement.setModalHeads(KPIExcelColumn.CYCLE_TIME.getColumns());
	}

	private Map<String, List<DataValue>> getCycleTimeDataCount(
			List<JiraIssueCustomHistory> jiraIssueCustomHistoriesList,
			FieldMapping fieldMapping,
			Set<String> issueTypeFilter,
			List<CycleTimeValidationData> cycleTimeList) {

		Map<String, List<DataValue>> cycleMap = new LinkedHashMap<>();
		if (CollectionUtils.isNotEmpty(jiraIssueCustomHistoriesList)) {
			jiraIssueCustomHistoriesList.forEach(
					history ->
							processIssueHistory(history, fieldMapping, issueTypeFilter, cycleTimeList, cycleMap));
		}
		return cycleMap;
	}

	private void processIssueHistory(
			JiraIssueCustomHistory history,
			FieldMapping fieldMapping,
			Set<String> issueTypeFilter,
			List<CycleTimeValidationData> cycleTimeList,
			Map<String, List<DataValue>> cycleMap) {
		List<DataValue> dataValueList = new ArrayList<>();
		LinkedHashMap<String, String> cycleTimeByGroup = new LinkedHashMap<>();
		Iterator<CycleTimeGroup> iterator =
				fieldMapping.getJiraIssueStatusGroupByCategoryKPI202().iterator();
		CycleTimeGroup current = iterator.hasNext() ? iterator.next() : null;
		while (current != null) {
			double minsDiff =
					calculateGroupMinutes(current.getStatuses(), history.getStatusUpdationLog(), iterator);
			if (minsDiff > 0) {
				double diffDays = Math.round((minsDiff / 1440) * 10.0) / 10.0;
				DataValue dataValue = new DataValue();
				dataValue.setName(current.getLabel());
				dataValue.setData(Double.toString(diffDays));
				dataValue.setValue(diffDays);
				dataValueList.add(dataValue);
				cycleTimeByGroup.put(current.getLabel(), diffDays + " Days");
				issueTypeFilter.add(history.getStoryType());
			}
			current = iterator.hasNext() ? iterator.next() : null;
		}
		if (CollectionUtils.isNotEmpty(dataValueList)) {
			cycleTimeList.add(
					CycleTimeValidationData.builder()
							.issueNumber(history.getStoryID())
							.url(history.getUrl())
							.issueType(history.getStoryType())
							.issueDesc(history.getDescription())
							.groupMap(cycleTimeByGroup)
							.build());
			cycleMap.put(history.getStoryID() + "#" + history.getStoryType(), dataValueList);
		}
	}

	private double calculateGroupMinutes(
			List<String> currentStatuses,
			List<JiraHistoryChangeLog> changeLogs,
			Iterator<CycleTimeGroup> iterator) {
		LocalDateTime windowStart = null;
		LocalDateTime windowEnd = null;
		double minsDiff = 0;
		boolean isPresent = false;
		for (JiraHistoryChangeLog log : changeLogs) {
			if (currentStatuses.contains(log.getChangedTo())) {
				if (windowStart == null) windowStart = log.getUpdatedOn();
				windowEnd = log.getUpdatedOn();
			} else if (windowStart != null) {
				isPresent = true;
				if (currentStatuses.contains(log.getChangedFrom())) windowEnd = log.getUpdatedOn();
				minsDiff += KpiDataHelper.calWeekMinutes(windowStart, windowEnd);
				windowStart = windowEnd = null;
			}
		}
		if (windowStart != null && iterator.hasNext()) {
			isPresent = true;
			minsDiff += KpiDataHelper.calWeekMinutes(windowStart, LocalDateTime.now());
		}
		return isPresent ? minsDiff : 0;
	}

	private Map<String, List<DataCount>> getDataCountObject(
			String trendLineName, Map<String, List<DataValue>> cycleMap, String duration) {
		Map<String, List<DataCount>> dataCountMap = new HashMap<>();
		cycleMap.forEach(
				(key, value) -> {
					String[] issueFilter = key.split("#");
					String filterKey = duration + "#" + issueFilter[1];
					DataCount dataCount = new DataCount();
					dataCount.setSProjectName(trendLineName);
					dataCount.setDataValue(value);
					dataCount.setKpiGroup(filterKey);
					dataCount.setSubFilter(issueFilter[0]);
					dataCount.setHoverValue(new HashMap<>());
					dataCountMap.computeIfAbsent(filterKey, k -> new ArrayList<>()).add(dataCount);
				});
		return dataCountMap;
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

	private void populateExcelDataObject(
			String requestTrackerId,
			List<CycleTimeValidationData> cycleTimeList,
			List<KPIExcelData> excelData) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			KPIExcelUtility.populateCycleTimeSlingshot(cycleTimeList, excelData);
		}
	}
}
