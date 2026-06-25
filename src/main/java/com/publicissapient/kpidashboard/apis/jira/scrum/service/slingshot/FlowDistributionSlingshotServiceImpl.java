package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.backlogdashboard.JiraBacklogKPIService;
import com.publicissapient.kpidashboard.apis.model.*;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class FlowDistributionSlingshotServiceImpl
		extends JiraBacklogKPIService<Double, List<Object>> {
	public static final String BACKLOG_CUSTOM_HISTORY = "backlogCustomHistory";
	public static final String FIELD_MAPPING = "fieldMapping";
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	@Autowired private ConfigHelperService configHelperService;
	@Autowired private CustomApiConfig customApiConfig;
	@Autowired private JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;

	// storyType have more than two word the stackChart hover fn break
	private static String combineType(String storyType) {
		// logic to combine multiple words into a single key
		return storyType.replaceAll("\\s+", "-");
	}

	/**
	 * Returns the LocalDateTime when the issue last transitioned to a closed status, or null if it
	 * has never reached a closed status.
	 */
	private LocalDateTime getClosedDate(JiraIssueCustomHistory issue, List<String> closedStatuses) {
		if (CollectionUtils.isEmpty(closedStatuses)) {
			return null;
		}
		return issue.getStatusUpdationLog().stream()
				.filter(log -> closedStatuses.contains(log.getChangedTo()))
				.map(JiraHistoryChangeLog::getUpdatedOn)
				.filter(Objects::nonNull)
				.max(LocalDateTime::compareTo)
				.orElse(null);
	}

	@Override
	public String getQualifierType() {
		return KPICode.FLOW_DISTRIBUTION_SLINGSHOT.name();
	}

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
			throws ApplicationException {
		List<DataCount> trendValueList = new ArrayList<>();
		projectWiseLeafNodeValue(projectNode, trendValueList, kpiElement, kpiRequest);

		log.info("FlowDistributionServiceImpl -> getKpiData ->  : {}", kpiElement);
		return kpiElement;
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			Node leafNode, String startDate, String endDate, KpiRequest kpiRequest) {
		Map<String, Object> resultListMap = new HashMap<>();

		if (leafNode != null) {
			log.info(
					"Flow Distribution kpi -> Requested project : {}", leafNode.getProjectFilter().getName());
			FieldMapping fieldMapping =
					configHelperService
							.getFieldMappingMap()
							.get(leafNode.getProjectFilter().getBasicProjectConfigId());

			List<JiraIssueCustomHistory> jiraIssueCustomHistoryList = new ArrayList<>();

			if (CollectionUtils.isNotEmpty(fieldMapping.getJiraIssueTypeNamesKPI207())) {
				jiraIssueCustomHistoryList = getJiraIssuesCustomHistoryFromBaseClass();
				jiraIssueCustomHistoryList =
						jiraIssueCustomHistoryList.stream()
								.filter(
										jiraIssueCustomHistory ->
												fieldMapping
														.getJiraIssueTypeNamesKPI207()
														.contains(jiraIssueCustomHistory.getStoryType()))
								.collect(Collectors.toList());
			}

			resultListMap.put(BACKLOG_CUSTOM_HISTORY, new ArrayList<>(jiraIssueCustomHistoryList));
			resultListMap.put(FIELD_MAPPING, fieldMapping);
		}
		return resultListMap;
	}

	/**
	 * Populates KPI value to leaf nodes and gives the trend analysis at project level.
	 *
	 * @param leafNode
	 * @param kpiElement
	 * @param kpiRequest
	 */
	@SuppressWarnings("unchecked")
	private void projectWiseLeafNodeValue(
			Node leafNode, List<DataCount> trendValueList, KpiElement kpiElement, KpiRequest kpiRequest) {

		// this method fetch dates for past history data
		CustomDateRange dateRange =
				KpiDataHelper.getMonthsForPastDataHistory(customApiConfig.getSlingShotFlowKpiMonthCount());

		// get start and end date in yyyy-mm-dd format
		String startDate = dateRange.getStartDate().format(DATE_FORMATTER);
		String endDate = dateRange.getEndDate().format(DATE_FORMATTER);

		String requestTrackerId = getRequestTrackerId();
		List<KPIExcelData> excelData = new ArrayList<>();

		Map<String, Object> resultMap = fetchKPIDataFromDb(leafNode, startDate, endDate, kpiRequest);

		List<JiraIssueCustomHistory> jiraIssueCustomHistories =
				(List<JiraIssueCustomHistory>) resultMap.get(BACKLOG_CUSTOM_HISTORY);
		FieldMapping fieldMapping = (FieldMapping) resultMap.get(FIELD_MAPPING);

		if (CollectionUtils.isNotEmpty(jiraIssueCustomHistories)) {

			List<String> closedStatuses =
					CollectionUtils.isNotEmpty(fieldMapping.getJiraIssueClosedStateKPI207())
							? fieldMapping.getJiraIssueClosedStateKPI207()
							: Collections.emptyList();

			Map<String, Map<String, Integer>> groupByDateAndTypeCount =
					jiraIssueCustomHistories.stream()
							.filter(issue -> getClosedDate(issue, closedStatuses) != null)
							.collect(
									Collectors.groupingBy(
											issue -> getClosedDate(issue, closedStatuses).toLocalDate().toString(),
											Collectors.groupingBy(
													issue -> combineType(issue.getStoryType()),
													Collectors.summingInt(issue -> 1))));

			Map<String, Map<String, List<String>>> groupByDateAndTypeIds =
					jiraIssueCustomHistories.stream()
							.filter(issue -> getClosedDate(issue, closedStatuses) != null)
							.collect(
									Collectors.groupingBy(
											issue -> getClosedDate(issue, closedStatuses).toLocalDate().toString(),
											Collectors.groupingBy(
													issue -> combineType(issue.getStoryType()),
													Collectors.mapping(
															JiraIssueCustomHistory::getStoryID, Collectors.toList()))));

			// Sort the groupByDateAndTypeCount map by date in ascending order
			TreeMap<String, Map<String, Integer>> sortedByDateTypeCountMap =
					new TreeMap<>(groupByDateAndTypeCount);

			TreeMap<String, Map<String, List<String>>> sortedByDateTypeIdsMap =
					new TreeMap<>(groupByDateAndTypeIds);

			// Get the map from the start date or the immediate next date for explore table
			Map<String, Map<String, List<String>>> mapAfterStartDateIds =
					sortedByDateTypeIdsMap.entrySet().stream()
							.filter(entry -> entry.getKey().compareTo(startDate) >= 0)
							.findFirst()
							.map(Map.Entry::getKey)
							.map(sortedByDateTypeIdsMap::tailMap)
							.orElse(new TreeMap<>());

			if (customApiConfig.isFlowDistributionIncludeHistoricalData()) {
				// fetching the start date backlog type count
				Map<String, Integer> startDateTypeCount =
						startDateTypeCount(startDate, sortedByDateTypeCountMap);

				// adding start date backlog count for cumulativeAddition
				addStartDateTypeCount(startDate, sortedByDateTypeCountMap, startDateTypeCount);
			}

			Map<String, Map<String, Integer>> cumulativeAddedCountMap =
					createCumulativeTypeCount(startDate, endDate, sortedByDateTypeCountMap);

			populateTrendValueList(trendValueList, cumulativeAddedCountMap);
			populateExcelDataObject(requestTrackerId, excelData, mapAfterStartDateIds);
			log.info(
					"FlowDistributionServiceImpl -> request id : {} dateWiseCountMap : {}",
					requestTrackerId,
					cumulativeAddedCountMap);

			List<String> dynamicColumns =
					new ArrayList<>(KPIExcelColumn.FLOW_DISTRIBUTION_SLINGSHOT.getColumns());
			groupByDateAndTypeIds.values().stream()
					.flatMap(m -> m.keySet().stream())
					.distinct()
					.forEach(dynamicColumns::add);
			kpiElement.setExcelColumns(dynamicColumns);
		} else {
			kpiElement.setExcelColumns(KPIExcelColumn.FLOW_DISTRIBUTION_SLINGSHOT.getColumns());
		}
		kpiElement.setExcelData(excelData);
		kpiElement.setTrendValueList(trendValueList);
	}

	/**
	 * for populating the trendValueList
	 *
	 * @param dataList
	 * @param dateTypeCountMap
	 */
	private void populateTrendValueList(
			List<DataCount> dataList, Map<String, Map<String, Integer>> dateTypeCountMap) {
		for (Map.Entry<String, Map<String, Integer>> entry : dateTypeCountMap.entrySet()) {
			String date = entry.getKey();
			Map<String, Integer> typeCountMap = entry.getValue();
			DataCount dc = new DataCount();
			dc.setDate(
					DateUtil.tranformUTCLocalTimeToZFormat(
							LocalDateTime.parse(date + DateUtil.ZERO_TIME_FORMAT)));
			dc.setValue(typeCountMap);
			dataList.add(dc);
		}
	}

	/**
	 * populate the Excel for modal window
	 *
	 * @param requestTrackerId
	 * @param excelData
	 * @param dateTypeIdsMap
	 */
	private void populateExcelDataObject(
			String requestTrackerId,
			List<KPIExcelData> excelData,
			Map<String, Map<String, List<String>>> dateTypeIdsMap) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())
				&& !Objects.isNull(dateTypeIdsMap)) {
			KPIExcelUtility.populateFlowKPIWithIds(dateTypeIdsMap, excelData);
		}
	}

	/**
	 * Method to create cumulative type count from start date to end date
	 *
	 * @param startDate
	 * @param endDate
	 * @param sortedByDateTypeCountMap
	 * @return
	 */
	private Map<String, Map<String, Integer>> createCumulativeTypeCount(
			String startDate,
			String endDate,
			TreeMap<String, Map<String, Integer>> sortedByDateTypeCountMap) {

		Map<String, Map<String, Integer>> cumulativeAddedCountMap = new LinkedHashMap<>();

		LocalDate currentDate = LocalDate.parse(startDate);
		LocalDate lastDate = LocalDate.parse(endDate);
		Map<String, Integer> accumulatedMap = null;

		while (!currentDate.isAfter(lastDate)) {
			String currentDateString = currentDate.toString();
			Map<String, Integer> currentMap =
					sortedByDateTypeCountMap.getOrDefault(currentDateString, new HashMap<>());

			if (accumulatedMap != null) {
				for (Map.Entry<String, Integer> entry : currentMap.entrySet()) {
					String key = entry.getKey();
					int value = entry.getValue();
					accumulatedMap.put(key, accumulatedMap.getOrDefault(key, 0) + value);
				}
			} else {
				accumulatedMap = new HashMap<>(currentMap);
			}
			cumulativeAddedCountMap.put(currentDateString, new HashMap<>(accumulatedMap));
			currentDate = currentDate.plusDays(1);
		}
		return cumulativeAddedCountMap;
	}

	/**
	 * For fetching start date backlog type count
	 *
	 * @param startDate
	 * @param sortedByDateTypeCountMap
	 * @return
	 */
	private Map<String, Integer> startDateTypeCount(
			String startDate, TreeMap<String, Map<String, Integer>> sortedByDateTypeCountMap) {
		Map<String, Integer> startDateTypeCount = new HashMap<>();
		for (Map.Entry<String, Map<String, Integer>> entry : sortedByDateTypeCountMap.entrySet()) {
			String date = entry.getKey();
			Map<String, Integer> typeCountMap = entry.getValue();

			// If we've reached the start date, break
			if (date.compareTo(startDate) >= 0) {
				break;
			}
			// Otherwise, add up the type count for this date
			typeCountMap.forEach(
					(type, count) ->
							startDateTypeCount.put(type, startDateTypeCount.getOrDefault(type, 0) + count));
		}
		return startDateTypeCount;
	}

	/**
	 * Adding start date type count
	 *
	 * @param startDate
	 * @param sortedByDateTypeCountMap
	 * @param tillStartDateTypeCount
	 */
	private void addStartDateTypeCount(
			String startDate,
			TreeMap<String, Map<String, Integer>> sortedByDateTypeCountMap,
			Map<String, Integer> tillStartDateTypeCount) {
		if (sortedByDateTypeCountMap.containsKey(startDate)) {
			Map<String, Integer> startDateTypeCount = sortedByDateTypeCountMap.get(startDate);

			for (Map.Entry<String, Integer> entry : tillStartDateTypeCount.entrySet()) {
				String key = entry.getKey();
				Integer value = entry.getValue();
				startDateTypeCount.put(key, startDateTypeCount.getOrDefault(key, 0) + value);
			}
		} else {
			sortedByDateTypeCountMap.put(startDate, tillStartDateTypeCount);
		}
	}
}
