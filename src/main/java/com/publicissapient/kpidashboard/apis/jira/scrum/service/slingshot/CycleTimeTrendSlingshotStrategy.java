package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.dto.CycleTimeGroup;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;

public abstract class CycleTimeTrendSlingshotStrategy {

	private static final String OVERALL = "Overall";

	abstract void projectWiseLeafNodeValue(
			KpiElement kpiElement, Node leafNode, Map<String, Object> resultMap);

	protected void filterDataBasedOnXAxisRangeWise(
			Map<String, List<JiraIssueCustomHistory>> sprintWiseJiraIssuesMap,
			Map<String, Map<String, List<Double>>> filterMap,
			FieldMapping fieldMapping,
			Set<String> issueTypesSet,
			Set<String> groupMapSet) {

		sprintWiseJiraIssuesMap.forEach(
				(sprint, projectWiseJiraIssueList) ->
						projectWiseJiraIssueList.forEach(
								history -> {
									if (history != null) {
										Iterator<CycleTimeGroup> iterator =
												fieldMapping.getJiraIssueStatusGroupByCategoryKPI202().iterator();
										CycleTimeGroup current = iterator.hasNext() ? iterator.next() : null;
										while (current != null) {
											setCycleTime(current, history, filterMap, sprint, iterator.hasNext());
											groupMapSet.add(current.getLabel());
											issueTypesSet.add(history.getStoryType());
											current = iterator.hasNext() ? iterator.next() : null;
										}
									}
								}));
	}

	private void setCycleTime(
			CycleTimeGroup current,
			JiraIssueCustomHistory history,
			Map<String, Map<String, List<Double>>> filterMap,
			String range,
			Boolean hasNext) {
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
				windowStart = null;
			}
		}
		if (windowStart != null && hasNext) {
			windowEnd = LocalDateTime.now();
			minsDiff += KpiDataHelper.calWeekMinutes(windowStart, windowEnd);
		}
		if (minsDiff > 0) {

			Map<String, List<Double>> overallMap =
					filterMap.computeIfAbsent(OVERALL + "#" + history.getStoryType(), k -> new HashMap<>());

			overallMap.computeIfAbsent(range, k -> new ArrayList<>()).add(minsDiff);
			filterMap.put(OVERALL + "#" + history.getStoryType(), overallMap);

			Map<String, List<Double>> cycleTimeByfilterMap =
					filterMap.computeIfAbsent(
							current.getLabel() + "#" + history.getStoryType(), k -> new HashMap<>());

			cycleTimeByfilterMap.computeIfAbsent(range, k -> new ArrayList<>()).add(minsDiff);
			filterMap.put(current.getLabel() + "#" + history.getStoryType(), cycleTimeByfilterMap);
		}
	}
}
