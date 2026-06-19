package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.backlogdashboard.JiraBacklogKPIService;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.dto.CycleTimeGroup;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.util.DateUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class FlowLoadSlingshotServiceImpl extends JiraBacklogKPIService<Double, List<Object>> {
	private static final String ISSUE_HISTORY = "Issue History";

	@Autowired private CustomApiConfig customApiConfig;
	@Autowired private ConfigHelperService configHelperService;

	@Override
	public String getQualifierType() {
		return KPICode.FLOW_LOAD_SLINGSHOT.name();
	}

	@Override
	public Map<String, Object> fetchKPIDataFromDb(
			Node leafNode, String startDate, String endDate, KpiRequest kpiRequest) {
		Map<String, Object> resultListMap = new HashMap<>();

		if (leafNode != null) {
			log.info("Flow Load kpi -> Requested project : {}", leafNode.getProjectFilter().getName());
			FieldMapping fieldMapping =
					configHelperService
							.getFieldMappingMap()
							.get(leafNode.getProjectFilter().getBasicProjectConfigId());

			List<JiraIssueCustomHistory> issuesHistory = getJiraIssuesCustomHistoryFromBaseClass();

			if (CollectionUtils.isNotEmpty(fieldMapping.getJiraIssueTypeNamesKPI206())) {
				List<String> issueTypes =
						fieldMapping.getJiraIssueTypeNamesKPI206().stream().map(String::toLowerCase).toList();
				issuesHistory = getJiraIssuesCustomHistoryFromBaseClass();
				issuesHistory =
						issuesHistory.stream()
								.filter(
										jiraIssue ->
												jiraIssue.getStoryType() != null
														&& issueTypes.contains(jiraIssue.getStoryType().toLowerCase()))
								.toList();
			}

			log.info(
					"Flow Load Slingshot (kpi206) -> issues fetched: {} for project: {}",
					issuesHistory != null ? issuesHistory.size() : 0,
					leafNode.getProjectFilter().getName());

			resultListMap.put(ISSUE_HISTORY, issuesHistory);
		}

		return resultListMap;
	}

	@Override
	public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode)
			throws ApplicationException {
		List<DataCount> trendValueList = new ArrayList<>();
		projectWiseLeafNodeValue(projectNode, trendValueList, kpiElement, kpiRequest);

		return kpiElement;
	}

	private void projectWiseLeafNodeValue(
			Node leafNode, List<DataCount> trendValueList, KpiElement kpiElement, KpiRequest kpiRequest) {

		int monthToSubtract = customApiConfig.getSlingShotFlowKpiMonthCount();

		LocalDate endDate = DateUtil.getTodayDate();
		LocalDate startDate = endDate.minusMonths(monthToSubtract);

		String requestTrackerId = getRequestTrackerId();
		List<KPIExcelData> excelData = new ArrayList<>();

		Map<String, Object> resultMap = fetchKPIDataFromDb(leafNode, "", "", kpiRequest);

		List<JiraIssueCustomHistory> jiraIssueCustomHistories =
				(List<JiraIssueCustomHistory>) resultMap.get(ISSUE_HISTORY);

		Map<String, List<Pair<LocalDate, LocalDate>>> statusesWithStartAndEndDate = new HashMap<>();
		FieldMapping fieldMapping =
				configHelperService
						.getFieldMappingMap()
						.get(leafNode.getProjectFilter().getBasicProjectConfigId());

		// Iterating Over All issues history's statusUpdationLog and saving start and
		// end date for each status
		if (CollectionUtils.isNotEmpty(jiraIssueCustomHistories)) {
			jiraIssueCustomHistories.forEach(
					jiraIssueCustomHistory ->
							createDateRangeForStatuses(
									endDate,
									startDate,
									statusesWithStartAndEndDate,
									jiraIssueCustomHistory,
									fieldMapping));
		}

		Map<String, Map<String, Integer>> dateWithStatusCount = new HashMap<>();
		LocalDate tempStartDate = startDate;
		while (tempStartDate.compareTo(endDate) <= 0) {
			dateWithStatusCount.put(tempStartDate.toString(), new HashMap<>());
			tempStartDate = tempStartDate.plusDays(1);
		}
		long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 2;
		Map<String, Map<String, Integer>> finalDateWithStatusCount = dateWithStatusCount;
		// Marking startDate index with plus 1 and end date next index with -1
		// StartDate and EndDate index are calculated with respect to startDate
		// Now compute the prefix sum, Since the beginning is marked with one, all the
		// values after beginning will be incremented by one. Now as increment is only
		// targeted only till the end of the range, the decrement on index endDate+1
		// prevents that for every range present after endDate.
		calculateStatusCountForEachDay(
				startDate,
				statusesWithStartAndEndDate,
				Math.toIntExact(totalDays),
				finalDateWithStatusCount);
		dateWithStatusCount =
				dateWithStatusCount.entrySet().stream()
						.sorted(Map.Entry.comparingByKey())
						.collect(
								Collectors.toMap(
										Map.Entry::getKey,
										Map.Entry::getValue,
										(oldValue, newValue) -> oldValue,
										LinkedHashMap::new));

		if (MapUtils.isNotEmpty(dateWithStatusCount)) {
			populateTrendValueList(trendValueList, dateWithStatusCount);
			Map<String, Map<String, List<String>>> dateStatusIdsMap =
					buildDateStatusIdsMap(jiraIssueCustomHistories, startDate, endDate, fieldMapping);
			addGroupEntriesTo(dateStatusIdsMap, fieldMapping);
			dateStatusIdsMap =
					dateStatusIdsMap.entrySet().stream()
							.sorted(Map.Entry.comparingByKey())
							.collect(
									Collectors.toMap(
											Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
			populateExcelDataObject(requestTrackerId, excelData, dateStatusIdsMap);
			log.debug(
					"FlowLoadServiceImpl -> request id : {} dateWithStatusCount : {}",
					requestTrackerId,
					dateWithStatusCount);
		}
		kpiElement.setExcelData(excelData);
		kpiElement.setExcelColumns(buildDynamicExcelColumns(dateWithStatusCount, fieldMapping));

		if (customApiConfig.isSlingshotFlowLoadMultiFilter()
				&& MapUtils.isNotEmpty(dateWithStatusCount)) {
			Map<String, Map<String, Integer>> dateWithGroupCount =
					aggregateByGroup(dateWithStatusCount, fieldMapping);
			List<DataCount> groupTrendValues = new ArrayList<>();
			populateTrendValueList(groupTrendValues, dateWithGroupCount);

			List<DataCountGroup> groups = new ArrayList<>();
			DataCountGroup statusGroup = new DataCountGroup();
			statusGroup.setFilter("Status");
			statusGroup.setValue(trendValueList);
			groups.add(statusGroup);
			DataCountGroup groupGroup = new DataCountGroup();
			groupGroup.setFilter("Group");
			groupGroup.setValue(groupTrendValues);
			groups.add(groupGroup);
			kpiElement.setTrendValueList(groups);
		} else {
			kpiElement.setTrendValueList(trendValueList);
		}
	}

	private List<String> buildDynamicExcelColumns(
			Map<String, Map<String, Integer>> dateWithStatusCount, FieldMapping fieldMapping) {
		List<String> columns = new ArrayList<>(KPIExcelColumn.FLOW_LOAD_SLINGSHOT.getColumns());

		Set<String> statuses =
				dateWithStatusCount.values().stream()
						.flatMap(m -> m.keySet().stream())
						.collect(Collectors.toCollection(TreeSet::new));
		columns.addAll(statuses);

		List<CycleTimeGroup> cycleTimeGroups = fieldMapping.getJiraIssueStatusGroupByCategoryKPI206();
		if (CollectionUtils.isNotEmpty(cycleTimeGroups)) {
			cycleTimeGroups.stream()
					.map(CycleTimeGroup::getLabel)
					.filter(Objects::nonNull)
					.distinct()
					.forEach(columns::add);
		}

		return columns;
	}

	private void addGroupEntriesTo(
			Map<String, Map<String, List<String>>> dateStatusIdsMap, FieldMapping fieldMapping) {
		List<CycleTimeGroup> cycleTimeGroups = fieldMapping.getJiraIssueStatusGroupByCategoryKPI206();
		if (CollectionUtils.isEmpty(cycleTimeGroups)) return;

		Map<String, String> statusToGroupLabel = new HashMap<>();
		for (CycleTimeGroup group : cycleTimeGroups) {
			for (String status : group.getStatuses()) {
				statusToGroupLabel.put(status.replace(" ", "-"), group.getLabel());
			}
		}

		dateStatusIdsMap.forEach(
				(date, statusIdsMap) -> {
					Map<String, List<String>> groupIdsMap = new LinkedHashMap<>();
					statusIdsMap.forEach(
							(status, ids) -> {
								String groupLabel = statusToGroupLabel.get(status);
								if (groupLabel != null) {
									groupIdsMap.computeIfAbsent(groupLabel, k -> new ArrayList<>()).addAll(ids);
								}
							});
					statusIdsMap.putAll(groupIdsMap);
				});
	}

	private Map<String, Map<String, Integer>> aggregateByGroup(
			Map<String, Map<String, Integer>> dateWithStatusCount, FieldMapping fieldMapping) {
		List<CycleTimeGroup> cycleTimeGroups = fieldMapping.getJiraIssueStatusGroupByCategoryKPI206();
		if (CollectionUtils.isEmpty(cycleTimeGroups)) return new LinkedHashMap<>();

		Map<String, String> statusToGroupLabel = new HashMap<>();
		for (CycleTimeGroup group : cycleTimeGroups) {
			for (String status : group.getStatuses()) {
				statusToGroupLabel.put(status.replace(" ", "-"), group.getLabel());
			}
		}

		Map<String, Map<String, Integer>> dateWithGroupCount = new LinkedHashMap<>();
		dateWithStatusCount.forEach(
				(date, statusCountMap) -> {
					Map<String, Integer> groupCountMap = new LinkedHashMap<>();
					statusCountMap.forEach(
							(status, count) -> {
								String groupLabel = statusToGroupLabel.get(status);
								if (groupLabel != null) {
									groupCountMap.merge(groupLabel, count, Integer::sum);
								}
							});
					if (!groupCountMap.isEmpty()) {
						dateWithGroupCount.put(date, groupCountMap);
					}
				});
		return dateWithGroupCount;
	}

	private Map<String, Map<String, List<String>>> buildDateStatusIdsMap(
			List<JiraIssueCustomHistory> jiraIssueCustomHistories,
			LocalDate startDate,
			LocalDate endDate,
			FieldMapping fieldMapping) {

		Map<String, Map<String, List<String>>> dateStatusIdsMap = new HashMap<>();
		LocalDate current = startDate;
		while (!current.isAfter(endDate)) {
			dateStatusIdsMap.put(current.toString(), new HashMap<>());
			current = current.plusDays(1);
		}

		if (CollectionUtils.isNotEmpty(jiraIssueCustomHistories)) {
			for (JiraIssueCustomHistory issue : jiraIssueCustomHistories) {
				List<JiraHistoryChangeLog> statusChangeLog = issue.getStatusUpdationLog();
				int size = statusChangeLog.size();
				if (size == 0) continue;
				String storyID = issue.getStoryID();

				List<Pair<String, Pair<LocalDate, LocalDate>>> ranges =
						extractStatusRanges(issue, statusChangeLog, size, startDate, endDate, fieldMapping);

				for (Pair<String, Pair<LocalDate, LocalDate>> range : ranges) {
					String status = range.getKey();
					LocalDate rangeStart = range.getValue().getKey();
					LocalDate rangeEnd = range.getValue().getValue();
					LocalDate day = rangeStart;
					while (!day.isAfter(rangeEnd)) {
						Map<String, List<String>> statusIds = dateStatusIdsMap.get(day.toString());
						if (statusIds != null) {
							statusIds.computeIfAbsent(status, k -> new ArrayList<>()).add(storyID);
						}
						day = day.plusDays(1);
					}
				}
			}
		}

		dateStatusIdsMap.entrySet().removeIf(e -> e.getValue().isEmpty());
		return dateStatusIdsMap;
	}

	private List<Pair<String, Pair<LocalDate, LocalDate>>> extractStatusRanges(
			JiraIssueCustomHistory issue,
			List<JiraHistoryChangeLog> statusChangeLog,
			int size,
			LocalDate startDate,
			LocalDate endDate,
			FieldMapping fieldMapping) {

		List<Pair<String, Pair<LocalDate, LocalDate>>> ranges = new ArrayList<>();

		if (size == 0) return ranges;

		if (DateUtil.convertJodaDateTimeToLocalDateTime(issue.getCreatedDate())
				.toLocalDate()
				.isAfter(endDate)) {
			return ranges;
		}

		for (int i = 0; i + 1 < size; i++) {
			JiraHistoryChangeLog changeLog = statusChangeLog.get(i);
			JiraHistoryChangeLog nextChangeLog = statusChangeLog.get(i + 1);
			addRangeIfValid(
					ranges,
					fieldMapping,
					changeLog.getChangedTo(),
					startDate,
					endDate,
					changeLog.getUpdatedOn().toLocalDate(),
					nextChangeLog.getUpdatedOn().toLocalDate());
		}

		JiraHistoryChangeLog lastLog = statusChangeLog.get(size - 1);
		LocalDate lastStatusDate = lastLog.getUpdatedOn().toLocalDate();
		if (!lastStatusDate.isAfter(endDate)) {
			LocalDate effectiveIntervalStart =
					lastStatusDate.isBefore(startDate) ? startDate : lastStatusDate;
			addRangeIfValid(
					ranges,
					fieldMapping,
					lastLog.getChangedTo(),
					startDate,
					endDate,
					effectiveIntervalStart,
					endDate);
		}

		return ranges;
	}

	private void addRangeIfValid(
			List<Pair<String, Pair<LocalDate, LocalDate>>> ranges,
			FieldMapping fieldMapping,
			String status,
			LocalDate startDate,
			LocalDate endDate,
			LocalDate intervalStart,
			LocalDate intervalEnd) {
		if (!isStatusValid(fieldMapping, status)) return;
		if (intervalEnd.isBefore(startDate) || intervalStart.isAfter(endDate)) return;
		if (intervalStart.isBefore(startDate)) intervalStart = startDate;
		if (intervalEnd.isAfter(endDate)) intervalEnd = endDate;
		ranges.add(Pair.of(status.replace(" ", "-"), Pair.of(intervalStart, intervalEnd)));
	}

	private void calculateStatusCountForEachDay(
			LocalDate startDate,
			Map<String, List<Pair<LocalDate, LocalDate>>> statusesWithStartAndEndDate,
			int totalDays,
			Map<String, Map<String, Integer>> finalDateWithStatusCount) {
		statusesWithStartAndEndDate.forEach(
				(status, listOfStartAndEndDate) -> {
					Integer[] data = new Integer[totalDays];
					Arrays.fill(data, 0);
					List<Integer> list = Arrays.asList(data);
					List<Integer> statusCountPresentInEachDay = list;
					listOfStartAndEndDate.forEach(
							intervalRange -> {
								int startIndex = (int) ChronoUnit.DAYS.between(startDate, intervalRange.getKey());
								int endIndex = (int) ChronoUnit.DAYS.between(startDate, intervalRange.getValue());
								statusCountPresentInEachDay.set(
										startIndex, statusCountPresentInEachDay.get(startIndex) + 1);
								statusCountPresentInEachDay.set(
										endIndex + 1, statusCountPresentInEachDay.get(endIndex + 1) - 1);
							});
					if (statusCountPresentInEachDay.get(0) != 0)
						finalDateWithStatusCount
								.get(startDate.toString())
								.put(status, statusCountPresentInEachDay.get(0));
					int prevValue = statusCountPresentInEachDay.get(0);
					for (int i = 1; i < totalDays - 1; i++) {
						statusCountPresentInEachDay.set(i, statusCountPresentInEachDay.get(i) + prevValue);
						prevValue = statusCountPresentInEachDay.get(i);
						if (statusCountPresentInEachDay.get(i) != 0) {
							finalDateWithStatusCount
									.get(startDate.plusDays(i).toString())
									.put(status, statusCountPresentInEachDay.get(i));
						}
					}
				});
	}

	private void createDateRangeForStatuses(
			LocalDate endDate,
			LocalDate startDate,
			Map<String, List<Pair<LocalDate, LocalDate>>> statusesWithStartAndEndDate,
			JiraIssueCustomHistory jiraIssueCustomHistory,
			FieldMapping fieldMapping) {
		List<JiraHistoryChangeLog> statusChangeLog = jiraIssueCustomHistory.getStatusUpdationLog();
		int size = statusChangeLog.size();

		if (size == 0) return;

		if (DateUtil.convertJodaDateTimeToLocalDateTime(jiraIssueCustomHistory.getCreatedDate())
				.toLocalDate()
				.isAfter(endDate)) {
			return;
		}

		for (int index = 0; index + 1 < size; index++) {
			JiraHistoryChangeLog changeLog = statusChangeLog.get(index);
			JiraHistoryChangeLog nextChangeLog = statusChangeLog.get(index + 1);
			savingDateRangeInMap(
					startDate,
					endDate,
					statusesWithStartAndEndDate,
					changeLog.getChangedTo(),
					changeLog.getUpdatedOn().toLocalDate(),
					nextChangeLog.getUpdatedOn().toLocalDate(),
					fieldMapping);
		}

		JiraHistoryChangeLog lastChangeLog = statusChangeLog.get(size - 1);
		LocalDate lastStatusDate = lastChangeLog.getUpdatedOn().toLocalDate();
		if (lastStatusDate.isAfter(endDate)) return;

		LocalDate effectiveIntervalStart =
				lastStatusDate.isBefore(startDate) ? startDate : lastStatusDate;
		savingDateRangeInMap(
				startDate,
				endDate,
				statusesWithStartAndEndDate,
				lastChangeLog.getChangedTo(),
				effectiveIntervalStart,
				endDate,
				fieldMapping);
	}

	private void savingDateRangeInMap(
			LocalDate startDate,
			LocalDate endDate,
			Map<String, List<Pair<LocalDate, LocalDate>>> statusesWithStartAndEndDate,
			String status,
			LocalDate intervalStartDate,
			LocalDate intervalEndDate,
			FieldMapping fieldMapping) {
		if (isStatusValid(fieldMapping, status)) {
			if (intervalEndDate.isBefore(startDate) || intervalStartDate.isAfter(endDate)) return;
			if (intervalStartDate.isBefore(startDate)) intervalStartDate = startDate;
			if (intervalEndDate.isAfter(endDate)) intervalEndDate = endDate;
			Pair<LocalDate, LocalDate> intervalRange = Pair.of(intervalStartDate, intervalEndDate);
			status = status.replace(" ", "-");
			statusesWithStartAndEndDate
					.computeIfAbsent(status, key -> new ArrayList<>())
					.add(intervalRange);
		}
	}

	private boolean isStatusValid(FieldMapping fieldMapping, String status) {
		Map<Long, String> doneStatusMap = getJiraIssueReleaseStatus().getClosedList();
		List<String> doneStatus = new ArrayList<>();
		List<CycleTimeGroup> cycleTimeGroupList =
				fieldMapping.getJiraIssueStatusGroupByCategoryKPI206();
		if (doneStatusMap != null)
			doneStatus = doneStatusMap.values().stream().map(String::toLowerCase).toList();
		if (CollectionUtils.isEmpty(cycleTimeGroupList)) return false;

		Set<String> validStatuses =
				cycleTimeGroupList.stream()
						.flatMap(cycleTimeGroup -> cycleTimeGroup.getStatuses().stream())
						.collect(Collectors.toSet());
		String statusLower = status.toLowerCase();
		return !doneStatus.contains(statusLower) && (validStatuses.contains(status));
	}

	private void populateTrendValueList(
			List<DataCount> dataList, Map<String, Map<String, Integer>> dateWithStatusCount) {
		for (Map.Entry<String, Map<String, Integer>> entry : dateWithStatusCount.entrySet()) {
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

	private void populateExcelDataObject(
			String requestTrackerId,
			List<KPIExcelData> excelData,
			Map<String, Map<String, List<String>>> dateStatusIdsMap) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())
				&& !Objects.isNull(dateStatusIdsMap)) {
			KPIExcelUtility.populateFlowKPIWithIds(dateStatusIdsMap, excelData);
		}
	}
}
