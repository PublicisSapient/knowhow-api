package com.publicissapient.kpidashboard.apis.jira.scrum.service.slingshot;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.enums.JiraFeatureHistory;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.BacklogKpiHelper;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.CycleTimeValidationData;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.publicissapient.kpidashboard.common.constant.CommonConstant.HIERARCHY_LEVEL_ID_PROJECT;

@Slf4j
@Service
@RequiredArgsConstructor
public class CycleTimeSlingshotServiceImpl extends JiraKPIService<Long, List<Object>, Map<String, Object>> {

    private final ConfigHelperService configHelperService;
    private final JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;
    private final JiraIssueRepository jiraIssueRepository;

    @Override
    public String getQualifierType() {
        return "";
    }

    @Override
    public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail) throws ApplicationException {
        log.info("LEAD-TIME -> requestTrackerId[{}]", kpiRequest.getRequestTrackerId());
        List<Node> projectList =
                treeAggregatorDetail.getMapOfListOfProjectNodes().get(HIERARCHY_LEVEL_ID_PROJECT);
        Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();

        // in case if only projects or sprint filters are applied
        projectWiseLeafNodeValue(kpiElement, mapTmp, projectList, kpiRequest);

        Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
        calculateAggregatedMultipleValueGroupMap(
                treeAggregatorDetail.getRoot(), nodeWiseKPIValue, KPICode.CYCLE_TIME);
        Map<String, List<DataCount>> trendValuesMap =
                getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.CYCLE_TIME);

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

    private void projectWiseLeafNodeValue(
            KpiElement kpiElement,
            Map<String, Node> mapTmp,
            List<Node> leafNodeList,
            KpiRequest kpiRequest) {
        KpiElement leadTimeReq =
                kpiRequest.getKpiList().stream()
                        .filter(k -> k.getKpiId().equalsIgnoreCase("kpi171"))
                        .findFirst()
                        .orElse(new KpiElement());

        LinkedHashMap<String, Object> filterDuration =
                (LinkedHashMap<String, Object>) leadTimeReq.getFilterDuration();
        int value; // Default value for 'value'
        String duration; // Default value for 'duration'
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
        } else if (duration.equalsIgnoreCase(CommonConstant.MONTH)) {
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
        List<CycleTimeValidationData> cycleTimeList = new ArrayList<>();
        List<KPIExcelData> excelData = new ArrayList<>();
        Set<String> issueTypeFilter = new LinkedHashSet<>();

    }

    @Override
    public Long calculateKPIMetrics(Map<String, Object> stringObjectMap) {
        return 0L;
    }

    @Override
    public Map<String, Object> fetchKPIDataFromDb(List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
        Map<String, Object> resultListMap = new HashMap<>();
        leafNodeList.forEach(
                leafNode -> {
                    Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();

                    ObjectId basicProjectConfigId = leafNode.getProjectFilter().getBasicProjectConfigId();
                    Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();

                    FieldMapping fieldMapping =
                            configHelperService.getFieldMappingMap().get(basicProjectConfigId);

                    if (Optional.ofNullable(fieldMapping.getJiraIssueTypeKPI200()).isPresent()) {

                        KpiDataHelper.prepareFieldMappingDefectTypeTransformation(
                                mapOfProjectFilters,
                                fieldMapping.getJiradefecttype(),
                                fieldMapping.getJiraIssueTypeKPI200(),
                                JiraFeatureHistory.STORY_TYPE.getFieldValueInFeature());
                        uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);
                    }

                    List<String> status = new ArrayList<>();
                    Map<String, List<String>> issueTypesByGroups = fieldMapping.getJiraIssueStatusGroupByCategoryKPI200();
                    status.addAll(issueTypesByGroups.values().stream().flatMap())
                    if (Optional.ofNullable(fieldMapping.getJiraDodKPI171()).isPresent()) {
                        status.addAll(fieldMapping.getJiraDodKPI171());
                    }

                    if (Optional.ofNullable(fieldMapping.getJiraDorKPI171()).isPresent()) {
                        status.addAll(fieldMapping.getJiraDorKPI171());
                    }

                    if (Optional.ofNullable(fieldMapping.getJiraLiveStatusKPI171()).isPresent()) {
                        status.addAll(fieldMapping.getJiraLiveStatusKPI171());
                    }
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
}
