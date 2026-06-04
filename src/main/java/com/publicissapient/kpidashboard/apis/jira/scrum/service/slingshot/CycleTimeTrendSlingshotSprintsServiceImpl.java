package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.model.IterationKpiFilters;
import com.publicissapient.kpidashboard.apis.model.IterationKpiFiltersOptions;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.common.model.application.CycleTimeValidationData;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CycleTimeTrendSlingshotSprintsServiceImpl extends CycleTimeTrendSlingshotStrategy {

	private static final String ISSUE_COUNT = "Issue Count";
	private static final String SEARCH_BY_ISSUE_TYPE = "Filter by issue type";
	private static final String SEARCH_BY_CYCLE_GROUP = "Filter by cycle group";
	private static final String OVERALL = "Overall";
	private static final String HISTORY = "history";
	private static final String SPRINT_DETAILS = "sprints";
	public static final String X_AXIS_LABEL = "Sprint";

	private final ConfigHelperService configHelperService;

	@Override
	public void projectWiseLeafNodeValue(
			KpiElement kpiElement,
			Node leafNode,
			Map<String, Object> resultMap,
			String requestTrackerId) {

		List<KPIExcelData> excelData = new ArrayList<>();
		Set<String> issueTypesSet = new LinkedHashSet<>();
		Set<String> groupMapSet = new LinkedHashSet<>();
		groupMapSet.add(OVERALL);
		FieldMapping fieldMapping =
				configHelperService
						.getFieldMappingMap()
						.get(leafNode.getProjectFilter().getBasicProjectConfigId());
		List<SprintDetails> sprintDetails = (List<SprintDetails>) resultMap.get(SPRINT_DETAILS);
		List<JiraIssueCustomHistory> allIssueHistory =
				(List<JiraIssueCustomHistory>) resultMap.get(HISTORY);

		Map<String, List<JiraIssueCustomHistory>> sprintWiseJiraIssuesMap = new LinkedHashMap<>();
		Map<String, Map<String, List<Double>>> filterMap = new LinkedHashMap<>();
		List<CycleTimeValidationData> cycleTimeList = new ArrayList<>();

		initializeRangeMapForProjects(sprintWiseJiraIssuesMap, allIssueHistory, sprintDetails);
		filterDataBasedOnXAxisRangeWise(
				sprintWiseJiraIssuesMap,
				filterMap,
				fieldMapping,
				issueTypesSet,
				groupMapSet,
				cycleTimeList);

		Map<String, List<DataCount>> datacountMap = new LinkedHashMap<>();

		filterMap.forEach(
				(key, map) -> {
					List<DataCount> dataCountList = new ArrayList<>();
					map.forEach(
							(sprint, data) -> {
								Map<String, Object> hoverMap = new HashMap<>();
								hoverMap.put(ISSUE_COUNT, data.size());
								DataCount dataCount = new DataCount();
								double totalDays =
										Math.round((data.stream().mapToDouble(Double::doubleValue).sum() / 1440) * 10.0)
												/ 10.0;
								dataCount.setValue(totalDays);
								dataCount.setData(String.valueOf(totalDays));
								dataCount.setSSprintName(sprint);
								dataCount.setSSprintID(sprint);
								dataCount.setDate(sprint);
								dataCount.setSProjectName(leafNode.getProjectFilter().getName());
								dataCount.setKpiGroup(key);
								dataCount.setHoverValue(hoverMap);
								dataCountList.add(dataCount);
							});
					datacountMap.put(key, dataCountList);
				});

		populateExcelDataObject(requestTrackerId, cycleTimeList, excelData);
		leafNode.setValue(datacountMap);
		// Create kpi level filters
		IterationKpiFiltersOptions filter1 =
				new IterationKpiFiltersOptions(SEARCH_BY_CYCLE_GROUP, groupMapSet);
		IterationKpiFiltersOptions filter2 =
				new IterationKpiFiltersOptions(SEARCH_BY_ISSUE_TYPE, issueTypesSet);
		IterationKpiFilters iterationKpiFilters = new IterationKpiFilters(filter1, filter2);
		kpiElement.setFilters(iterationKpiFilters);
		kpiElement.setExcelData(excelData);
		kpiElement.setLabelXAxis(X_AXIS_LABEL);
		kpiElement.setExcelColumns(KPIExcelColumn.CYCLE_TIME_TREND_SLINGSHOT.getColumns());
	}

	private static void initializeRangeMapForProjects(
			Map<String, List<JiraIssueCustomHistory>> sprintWiseJiraIssuesMap,
			List<JiraIssueCustomHistory> jiraIssueCustomHistoryList,
			List<SprintDetails> sprintDetailsList) {
		Map<String, JiraIssueCustomHistory> historyMap =
				jiraIssueCustomHistoryList.stream()
						.collect(Collectors.toMap(JiraIssueCustomHistory::getStoryID, history -> history));
		sprintDetailsList.forEach(
				sprintDetails ->
						sprintDetails
								.getTotalIssues()
								.forEach(
										issue ->
												sprintWiseJiraIssuesMap
														.computeIfAbsent(sprintDetails.getSprintName(), k -> new ArrayList<>())
														.add(historyMap.get(issue.getNumber()))));
	}
}
