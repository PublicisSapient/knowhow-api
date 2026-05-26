package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.enums.JiraFeatureHistory;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.model.IterationKpiFilters;
import com.publicissapient.kpidashboard.apis.model.IterationKpiFiltersOptions;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.util.BacklogKpiHelper;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.dto.CycleTimeGroup;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

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

@Service
@RequiredArgsConstructor
public class CycleTimeTrendSlingshotDurationRangeServiceImpl extends CycleTimeTrendSlingshotStrategy {

    private static final String ISSUE_COUNT = "Issue Count";
    private static final String SEARCH_BY_ISSUE_TYPE = "Filter by issue type";
    private static final String SEARCH_BY_CYCLE_GROUP = "Filter by cycle group";
    private static final String MONTHS = "Months";
    private static final String OVERALL = "Overall";
    private static final String HISTORY = "history";

    private final CustomApiConfig customApiConfig;
    private final ConfigHelperService configHelperService;

    public void projectWiseLeafNodeValue(
            KpiElement kpiElement, Node leafNode, Map<String, Object> resultMap) {

        List<KPIExcelData> excelData = new ArrayList<>();
        Set<String> issueTypesSet = new LinkedHashSet<>();
        Set<String> groupMapSet = new LinkedHashSet<>();
        groupMapSet.add(OVERALL);
        List<String> rangeList = customApiConfig.getFlowEfficiencyXAxisRange();
        FieldMapping fieldMapping =
                configHelperService
                        .getFieldMappingMap()
                        .get(leafNode.getProjectFilter().getBasicProjectConfigId());
        List<JiraIssueCustomHistory> allIssueHistory = (List<JiraIssueCustomHistory>) resultMap.get(HISTORY);

        Map<String, List<JiraIssueCustomHistory>> rangeAndStatusWiseJiraIssueMap =
                new LinkedHashMap<>();
        Map<String, Map<String, List<Double>>> filterMap = new LinkedHashMap<>();

        Map<String, Map<String, Object>> uniqueProjectMap =
                getUniqueProjectMap(leafNode.getProjectFilter().getBasicProjectConfigId());
        initializeRangeMapForProjects(
                rangeAndStatusWiseJiraIssueMap, allIssueHistory, rangeList, uniqueProjectMap);
        filterDataBasedOnXAxisRangeWise(
                rangeAndStatusWiseJiraIssueMap, filterMap, fieldMapping, issueTypesSet, groupMapSet);

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
                                                (dataList.stream().mapToDouble(Double::doubleValue).sum() / 1440)
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

        leafNode.setValue(datacountMap);
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

    private Map<String, Map<String, Object>> getUniqueProjectMap(ObjectId basicProjectConfigId) {
        Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
        Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();

        FieldMapping fieldMapping = configHelperService.getFieldMappingMap().get(basicProjectConfigId);

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
        return uniqueProjectMap;
    }


    public static void initializeRangeMapForProjects(
            Map<String, List<JiraIssueCustomHistory>> rangeWiseJiraIssuesMap,
            List<JiraIssueCustomHistory> jiraIssueCustomHistoryList,
            List<String> xAxisRange,
            Map<String, Map<String, Object>> uniqueProjectMap) {

        xAxisRange.forEach(
                range -> {
                    String currentDate = DateUtil.getTodayDate().toString();
                    String startDate;
                    String[] rangeSplit = range.trim().split(" ");
                    if (rangeSplit[2].contains(MONTHS)) {
                        startDate =
                                DateUtil.getTodayDate().minusMonths(Integer.parseInt(rangeSplit[1])).toString();
                    } else {
                        startDate =
                                DateUtil.getTodayDate().minusWeeks(Integer.parseInt(rangeSplit[1])).toString();
                    }
                    rangeWiseJiraIssuesMap.put(
                            range,
                            BacklogKpiHelper.filterProjectHistories(
                                    jiraIssueCustomHistoryList, uniqueProjectMap, startDate, currentDate));
                });
    }

}
