package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.application.CycleTimeValidationData;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.dto.CycleTimeGroup;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;

public abstract class CycleTimeTrendSlingshotStrategy {

	private static final String OVERALL = "Overall";

	abstract void projectWiseLeafNodeValue(
			KpiElement kpiElement, Node leafNode, Map<String, Object> resultMap, String requestTrackerId);

	protected void filterDataBasedOnXAxisRangeWise(
			Map<String, List<JiraIssueCustomHistory>> sprintWiseJiraIssuesMap,
			Map<String, Map<String, List<Double>>> filterMap,
			FieldMapping fieldMapping,
			Set<String> issueTypesSet,
			Set<String> groupMapSet,
			List<CycleTimeValidationData> cycleTimeList) {

		sprintWiseJiraIssuesMap.forEach(
				(sprint, projectWiseJiraIssueList) ->
						projectWiseJiraIssueList.forEach(
								history ->
										processHistory(
												filterMap,
												fieldMapping,
												issueTypesSet,
												groupMapSet,
												sprint,
												history,
												cycleTimeList)));
	}

	private void processHistory(
			Map<String, Map<String, List<Double>>> filterMap,
			FieldMapping fieldMapping,
			Set<String> issueTypesSet,
			Set<String> groupMapSet,
			String sprint,
			JiraIssueCustomHistory history,
			List<CycleTimeValidationData> cycleTimeList) {
		if (history != null) {
			List<CycleTimeGroup> cycleTimeGroups = fieldMapping.getJiraIssueStatusGroupByCategoryKPI202();
			LinkedHashMap<String, String> cycleTimeByGroup = new LinkedHashMap<>();
			cycleTimeGroups.forEach(g -> cycleTimeByGroup.put(g.getLabel(), ""));
			Iterator<CycleTimeGroup> iterator = cycleTimeGroups.iterator();
			CycleTimeGroup current = iterator.hasNext() ? iterator.next() : null;
			while (current != null) {
				setCycleTime(current, history, filterMap, sprint, iterator.hasNext(), cycleTimeByGroup);
				groupMapSet.add(current.getLabel());
				issueTypesSet.add(history.getStoryType());
				current = iterator.hasNext() ? iterator.next() : null;
			}
			boolean hasData = cycleTimeByGroup.values().stream().anyMatch(v -> !v.isEmpty());
			boolean alreadyAdded =
					cycleTimeList.stream().anyMatch(c -> history.getStoryID().equals(c.getIssueNumber()));
			if (hasData && !alreadyAdded) {
				cycleTimeList.add(
						CycleTimeValidationData.builder()
								.issueNumber(history.getStoryID())
								.url(history.getUrl())
								.issueType(history.getStoryType())
								.issueDesc(history.getDescription())
								.sprintName(sprint)
								.groupMap(cycleTimeByGroup)
								.build());
			}
		}
	}

	private void setCycleTime(
			CycleTimeGroup current,
			JiraIssueCustomHistory history,
			Map<String, Map<String, List<Double>>> filterMap,
			String range,
			Boolean hasNext,
			LinkedHashMap<String, String> cycleTimeByGroup) {
		LocalDateTime windowStart = null;
		LocalDateTime windowEnd = null;
		double minsDiff = 0;
		for (JiraHistoryChangeLog log : history.getStatusUpdationLog()) {
			if (current.getStatuses().contains(log.getChangedTo())) {
				if (windowStart == null) windowStart = log.getUpdatedOn();
				windowEnd = log.getUpdatedOn();
			} else if (windowStart != null) {
				if (current.getStatuses().contains(log.getChangedFrom())) windowEnd = log.getUpdatedOn();
				minsDiff += KpiDataHelper.calWeekMinutes(windowStart, windowEnd);
				windowStart = windowEnd = null;
			}
		}
		if (windowStart != null && hasNext) {
			windowEnd = LocalDateTime.now();
			minsDiff += KpiDataHelper.calWeekMinutes(windowStart, windowEnd);
		}
		if (minsDiff > 0) {

			Map<String, List<Double>> overallMap =
					filterMap.computeIfAbsent(
							OVERALL + "#" + history.getStoryType(), k -> new LinkedHashMap<>());

			overallMap.computeIfAbsent(range, k -> new ArrayList<>()).add(minsDiff);
			filterMap.put(OVERALL + "#" + history.getStoryType(), overallMap);

			Map<String, List<Double>> cycleTimeByfilterMap =
					filterMap.computeIfAbsent(
							current.getLabel() + "#" + history.getStoryType(), k -> new LinkedHashMap<>());

			cycleTimeByfilterMap.computeIfAbsent(range, k -> new ArrayList<>()).add(minsDiff);
			filterMap.put(current.getLabel() + "#" + history.getStoryType(), cycleTimeByfilterMap);
			cycleTimeByGroup.put(
					current.getLabel(), (Math.round((minsDiff / 1440) * 10.0) / 10.0) + " Days");
		}
	}

	protected void addMissingIssuesToCycleTimeList(
			List<JiraIssueCustomHistory> allIssueHistory,
			List<CycleTimeValidationData> cycleTimeList,
			FieldMapping fieldMapping,
			Set<String> issueTypesSet,
			Set<String> groupMapSet) {
		Set<String> processedIds =
				cycleTimeList.stream()
						.map(CycleTimeValidationData::getIssueNumber)
						.collect(Collectors.toSet());
		allIssueHistory.stream()
				.filter(h -> h != null && !processedIds.contains(h.getStoryID()))
				.forEach(
						h ->
								processHistory(
										new HashMap<>(),
										fieldMapping,
										issueTypesSet,
										groupMapSet,
										"",
										h,
										cycleTimeList));
	}

	protected void populateExcelDataObject(
			String requestTrackerId,
			List<CycleTimeValidationData> cycleTimeList,
			List<KPIExcelData> excelData) {
		if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {
			KPIExcelUtility.populateCycleTimeSlingshot(cycleTimeList, excelData);
		}
	}
}
