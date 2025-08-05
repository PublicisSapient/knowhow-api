/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.apis.jira.scrum.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.codehaus.plexus.util.StringUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.enums.Filters;
import com.publicissapient.kpidashboard.apis.enums.JiraFeature;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.NormalizedJira;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@AllArgsConstructor
public class DefectsBreachedSlasServiceImpl extends JiraKPIService<Long, List<Object>, Map<String, Object>> {

    private final CustomApiConfig customApiConfig;

    private final ConfigHelperService configHelperService;

    private final MongoTemplate mongoTemplate;

    private static final List<String> TEMP_SEVERITIES = List.of("1", "2", "3", "4", "Major", "High", "Low", "Minor");

    private static final Map<String, SLAData> SEVERITY_SLA_MAP = Map.of(
            "s1", new SLAData(24, "s1", SLATimeUnit.HOURS.getTimeUnitValue()),
            "s2", new SLAData(48, "s2", SLATimeUnit.HOURS.getTimeUnitValue()),
            "s3", new SLAData(5, "s3", SLATimeUnit.DAYS.getTimeUnitValue()),
            "s4", new SLAData(10, "s4", SLATimeUnit.DAYS.getTimeUnitValue())
    );

    private static final Random RANDOM = new Random();

    @Getter
    @AllArgsConstructor
    private enum SLATimeUnit {
        HOURS("Hours"),
        DAYS("Days");

        private final String timeUnitValue;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    private static class DefectsBreachedSLAsKPIData {
        private String basicProjectConfigId;
        private String projectName;

        private FieldMapping projectKPISettingsFieldMapping;

        private List<SprintDataForKPI195> sprintDataForKPI195List;

        private Set<String> severitiesFoundAcrossSprintIssues;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    private static class SprintDataForKPI195 {
        private String sprintId;
        private String sprintName;
        private String sprintNodeId;

        private List<JiraDefectIssueDataForKPI195> jiraDefectIssueDataForKPI195List;
    }

    @Data
    private static class SeverityJiraIssuesDefectsBreachedSLAData {
        private int solvedIssues;
        private int breachedIssues;
    }

    @Data
    @AllArgsConstructor
    private static class SeverityJiraDefectDrillDownValue {
        private String severity;

        private double breachedPercentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    private static class JiraDefectIssueDataForKPI195 {
        private String issueKey;
        private String url;
        private String priority;
        private String status;
        private String severity;
        private String timeSpentInHours;
        private String resolutionTime;

        private Set<String> issueIdsDefectRelatesTo;

        private SLAData slaData;

        private LocalDateTime createdDate;
        private LocalDateTime closedDate;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class SLAData {
        private int sla;

        private String generalSeverity;
        private String timeUnit;
    }

    @Override
    public String getQualifierType() {
        return KPICode.DEFECTS_BREACHED_SLAS.name();
    }

    @Override
    public KpiElement getKpiData(
            KpiRequest kpiRequest,
            KpiElement kpiElement,
            TreeAggregatorDetail treeAggregatorDetail
    ) throws ApplicationException {

        List<DataCount> trendValueList = new ArrayList<>();
        Node root = treeAggregatorDetail.getRoot();

        Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();

        Map<String, List<Node>> userAccessibleDataByType = treeAggregatorDetail.getMapOfListOfLeafNodes();

        userAccessibleDataByType.keySet()
                .stream()
                .filter(dataType -> Filters.getFilter(dataType).equals(Filters.SPRINT))
                .findFirst()
                .ifPresent(dataType ->
                        calculateDefectsBreachSLAsForSprints(
                                mapTmp, userAccessibleDataByType.get(dataType), trendValueList, kpiElement, kpiRequest));

        Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();
        calculateAggregatedValueMap(root, nodeWiseKPIValue, KPICode.DEFECTS_BREACHED_SLAS);
        Map<String, List<DataCount>> trendValuesMap =
                getTrendValuesMap(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.DEFECTS_BREACHED_SLAS);

        trendValuesMap = KPIHelperUtil.sortTrendMapByKeyOrder(trendValuesMap, Arrays.asList(CommonConstant.OVERALL,
                Constant.DSE_1, Constant.DSE_2, Constant.DSE_3, Constant.DSE_4));
        Map<String, Map<String, List<DataCount>>> issueTypeProjectWiseDc = new LinkedHashMap<>();
        trendValuesMap.forEach((issueType, dataCounts) -> {
            Map<String, List<DataCount>> projectWiseDc = dataCounts.stream()
                    .collect(Collectors.groupingBy(DataCount::getData));
            issueTypeProjectWiseDc.put(issueType, projectWiseDc);
        });

        List<DataCountGroup> dataCountGroups = new ArrayList<>();
        issueTypeProjectWiseDc.forEach((issueType, projectWiseDc) -> {
            DataCountGroup dataCountGroup = new DataCountGroup();
            List<DataCount> dataList = new ArrayList<>();
            projectWiseDc.forEach((key, value) -> dataList.addAll(value));
            dataCountGroup.setFilter(issueType);
            dataCountGroup.setValue(dataList);
            dataCountGroups.add(dataCountGroup);
        });

        kpiElement.setTrendValueList(dataCountGroups);

        return kpiElement;
    }

    @Override
    public Map<String, Object> fetchKPIDataFromDb(List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		return Collections.emptyMap();
    }

    @Override
    public Long calculateKPIMetrics(Map<String, Object> stringObjectMap) {
        return 0L;
    }

    private List<DefectsBreachedSLAsKPIData> constructKPIData(
            List<Node> sprintLeafNodesList, KpiRequest kpiRequest
    ) {
        List<DefectsBreachedSLAsKPIData> defectsBreachedSLAsKPIDataList = new ArrayList<>();

        // Sort the sprints by start date in ascending order
        sprintLeafNodesList.sort(Comparator.comparing(node -> node.getSprintFilter().getStartDate()));

        sprintLeafNodesList.forEach(sprintLeafNode -> {
            String basicProjectConfigStringId = sprintLeafNode.getProjectFilter().getBasicProjectConfigId().toString();
            String sprintId = sprintLeafNode.getSprintFilter().getId();
            String sprintNodeId = sprintLeafNode.getId();

            Optional<DefectsBreachedSLAsKPIData> defectsBreachedSLAsKPIDataOptional =
                    defectsBreachedSLAsKPIDataList.stream()
                            .filter(defectsBreachedSLAsKPIData ->
                                    basicProjectConfigStringId.equals(defectsBreachedSLAsKPIData.getBasicProjectConfigId())).findFirst();

            if(defectsBreachedSLAsKPIDataOptional.isPresent()) {
                DefectsBreachedSLAsKPIData defectsBreachedSLAsKPIData = defectsBreachedSLAsKPIDataOptional.get();

                Optional<SprintDataForKPI195> sprintDataForKPI195Optional = defectsBreachedSLAsKPIData.getSprintDataForKPI195List()
                        .stream().filter(sprintDataForKPI195 -> sprintId.equals(sprintDataForKPI195.getSprintId())).findFirst();

                if(sprintDataForKPI195Optional.isEmpty()) {
                    defectsBreachedSLAsKPIData.getSprintDataForKPI195List().add(SprintDataForKPI195.builder()
                            .sprintId(sprintId)
                            .sprintName(sprintLeafNode.getSprintFilter().getName())
                            .sprintNodeId(sprintNodeId)
                            .jiraDefectIssueDataForKPI195List(new ArrayList<>())
                            .build());
                }
            } else {
                DefectsBreachedSLAsKPIData.DefectsBreachedSLAsKPIDataBuilder defectsBreachedSLAsKPIDataBuilder =
                        DefectsBreachedSLAsKPIData.builder()
                                .basicProjectConfigId(basicProjectConfigStringId)
                                .projectName(sprintLeafNode.getProjectFilter().getName())
                                .projectKPISettingsFieldMapping(configHelperService.getFieldMappingMap()
                                        .get(sprintLeafNode.getProjectFilter().getBasicProjectConfigId())
                                );

                List<SprintDataForKPI195> sprintDataForKPI195List = new ArrayList<>();
                sprintDataForKPI195List.add(SprintDataForKPI195.builder()
                        .sprintId(sprintId)
                        .sprintName(sprintLeafNode.getSprintFilter().getName())
                        .sprintNodeId(sprintNodeId)
                        .jiraDefectIssueDataForKPI195List(new ArrayList<>())
                        .build());
                defectsBreachedSLAsKPIDataBuilder.sprintDataForKPI195List(sprintDataForKPI195List);
                defectsBreachedSLAsKPIDataList.add(defectsBreachedSLAsKPIDataBuilder.build());
            }
        });

        defectsBreachedSLAsKPIDataList.forEach(defectsBreachedSLAsKPIData -> {
            String basicProjectConfigId = defectsBreachedSLAsKPIData.getBasicProjectConfigId();
            Set<String> sprintIdsSet = defectsBreachedSLAsKPIData.getSprintDataForKPI195List().stream().map(SprintDataForKPI195::getSprintId).collect(Collectors.toSet());
            FieldMapping fieldMapping = defectsBreachedSLAsKPIData.getProjectKPISettingsFieldMapping();
            // Extracting all issues based on the remaining KPI setting filters and the issue numbers extracted in
            // the previous step
                Criteria criteria = new Criteria();
                criteria = criteria.and(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature()).is(basicProjectConfigId)
                        .and(JiraFeature.SPRINT_ID.getFieldValueInFeature()).in(sprintIdsSet)
                        .and(JiraFeature.ISSUE_TYPE.getFieldValueInFeature()).is(NormalizedJira.DEFECT_TYPE.getValue())
                        .and(JiraFeature.STATUS.getFieldValueInFeature()).in(CommonUtils.convertToPatternList(fieldMapping.getIncludedDefectClosureStatusesKPI195()));
                if(CollectionUtils.isNotEmpty(fieldMapping.getExcludedDefectPrioritiesKPI195())) {
                    criteria = criteria.and(JiraFeature.DEFECT_PRIORITY.getFieldValueInFeature()).nin(CommonUtils.convertToPatternList(fieldMapping.getExcludedDefectPrioritiesKPI195()));
                }
                if(CollectionUtils.isNotEmpty(fieldMapping.getExcludedDefectResolutionTypesKPI195())) {
                    criteria = criteria.and("resolution")
                            .nin(CommonUtils.convertToPatternList(fieldMapping.getExcludedDefectResolutionTypesKPI195()));
                }
                if(CollectionUtils.isNotEmpty(fieldMapping.getIncludedDefectRootCausesKPI195())) {
                    criteria = criteria.and("rootCauseList")
                            .in(CommonUtils.convertToPatternList(fieldMapping.getIncludedDefectRootCausesKPI195()));
                }
                List<JiraIssue> issuesAfterApplyingAllFilters = mongoTemplate.find(new Query(criteria), JiraIssue.class);

                if(CollectionUtils.isNotEmpty(issuesAfterApplyingAllFilters)) {
                    Criteria criteria3 = new Criteria();
                    criteria3 =
                            criteria3.and(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature()).is(basicProjectConfigId)
                                    .and("storyID").in(issuesAfterApplyingAllFilters.stream().map(JiraIssue::getNumber).collect(Collectors.toSet()));
                    List<JiraIssueCustomHistory> jiraIssueCustomHistoryList = mongoTemplate.find(new Query(criteria3), JiraIssueCustomHistory.class);

                    if(CollectionUtils.isNotEmpty(jiraIssueCustomHistoryList)) {
                        jiraIssueCustomHistoryList.forEach(jiraIssueCustomHistory -> {
                            List<JiraHistoryChangeLog> statusUpdateChangelog =
                                    jiraIssueCustomHistory.getStatusUpdationLog();
                            statusUpdateChangelog.sort(Comparator.comparing(JiraHistoryChangeLog::getUpdatedOn));
                            JiraHistoryChangeLog lastStatusUpdate =
                                    statusUpdateChangelog.get(statusUpdateChangelog.size() - 1);

                            if(fieldMapping.getIncludedDefectClosureStatusesKPI195()
                                    .stream()
                                    .anyMatch(status -> status.equalsIgnoreCase(lastStatusUpdate.getChangedTo()))
                            ) {
                                Optional<JiraIssue> jiraIssueOptional = issuesAfterApplyingAllFilters.stream()
                                        .filter(jiraIssue1 -> jiraIssue1.getNumber()
                                                .equalsIgnoreCase(jiraIssueCustomHistory.getStoryID()))
                                        .findFirst();
                                if(jiraIssueOptional.isPresent()) {
                                    JiraIssue jiraIssue = jiraIssueOptional.get();
                                    JiraDefectIssueDataForKPI195.JiraDefectIssueDataForKPI195Builder jiraDefectIssueDataForKPI195Builder =
                                            JiraDefectIssueDataForKPI195.builder()
                                                    .issueKey(jiraIssueCustomHistory.getStoryID())
                                                    .closedDate(lastStatusUpdate.getUpdatedOn())
                                                    .issueIdsDefectRelatesTo(jiraIssue.getDefectStoryID())
                                                    .timeSpentInHours(String.valueOf(Math.floor(jiraIssue.getTimeSpentInMinutes() / 60.0)));

                                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS");
                                    LocalDateTime dateTime = LocalDateTime.parse(jiraIssue.getCreatedDate(), formatter);
                                    jiraDefectIssueDataForKPI195Builder.createdDate(dateTime)
                                            .url(jiraIssue.getUrl())
                                            .priority(jiraIssue.getPriority())
                                            .status(jiraIssue.getStatus());

                                    String issueSeverity = TEMP_SEVERITIES.get(RANDOM.nextInt(0, TEMP_SEVERITIES.size()));

                                    jiraDefectIssueDataForKPI195Builder.severity(issueSeverity)
                                            .slaData(determineDefectSLABasedOnSeverity(issueSeverity));

                                    Optional<SprintDataForKPI195> sprintDataForKPI195Optional =
                                            defectsBreachedSLAsKPIData.getSprintDataForKPI195List()
                                                    .stream()
                                                    .filter(sprintDataForKPI195 -> sprintDataForKPI195.getSprintId().equals(jiraIssue.getSprintID()))
                                                    .findFirst();
                                    sprintDataForKPI195Optional.ifPresent(sprintDataForKPI195 ->
                                            sprintDataForKPI195.getJiraDefectIssueDataForKPI195List().add(jiraDefectIssueDataForKPI195Builder.build())
                                    );
                                }
                            }
                        });
                    }
                }
        });
        return defectsBreachedSLAsKPIDataList;
    }

    private void calculateDefectsBreachSLAsForSprints(
            Map<String, Node> nodeIdMap,
            List<Node> sprintLeafNodes,
            List<DataCount> trendValueList,
            KpiElement kpiElement,
            KpiRequest kpiRequest
    ) {
        List<DefectsBreachedSLAsKPIData> kpiDataFromDatabase = constructKPIData(sprintLeafNodes, kpiRequest);

        if(CollectionUtils.isNotEmpty(kpiDataFromDatabase)) {
            List<KPIExcelData> kpiExcelDataList = new ArrayList<>();
            kpiDataFromDatabase.forEach(defectsBreachedSLAsKPIData -> {
                List<SprintDataForKPI195> sprintDataForKPI195List =
                        defectsBreachedSLAsKPIData.getSprintDataForKPI195List();
                sprintDataForKPI195List.forEach(sprintDataForKPI195 ->
                        populateDataCountWithKPICalculatedValueForASprint(
                                nodeIdMap,
                                defectsBreachedSLAsKPIData.getProjectName(),
                                sprintDataForKPI195,
                                trendValueList,
                                kpiExcelDataList
                        )
                );
            });
            kpiElement.setExcelData(kpiExcelDataList);
            kpiElement.setExcelColumns(KPIExcelColumn.DEFECTS_BREACHED_SLAS.getColumns());
        }
    }

    private void populateDataCountWithKPICalculatedValueForASprint(
            Map<String, Node> nodeIdMap,
            String projectName,
            SprintDataForKPI195 sprintDataForKPI195,
            List<DataCount> trendValueList,
            List<KPIExcelData> kpiExcelDataList
    ) {
        Map<String, List<DataCount>> severityDataCountMap = new HashMap<>();
        Map<String, SeverityJiraIssuesDefectsBreachedSLAData> severityByJiraIssueDefectsData = new HashMap<>();
        sprintDataForKPI195.getJiraDefectIssueDataForKPI195List().forEach(jiraDefectIssueDataForKPI195 -> {
            Map<String, String> jiraIssueIdAndUrlDefectRelatesToMap = new HashMap<>();
            if(StringUtils.isNotEmpty(jiraDefectIssueDataForKPI195.getUrl()) &&
                    CollectionUtils.isNotEmpty(jiraDefectIssueDataForKPI195.getIssueIdsDefectRelatesTo())) {
                String jiraDefectUrl = jiraDefectIssueDataForKPI195.getUrl();
                jiraDefectIssueDataForKPI195.getIssueIdsDefectRelatesTo()
                        .forEach(issueId ->
                                jiraIssueIdAndUrlDefectRelatesToMap.put(
                                        issueId,
                                        String.format("%s%s", jiraDefectUrl.substring(0, jiraDefectUrl.lastIndexOf("/") + 1), issueId)
                                )
                        );
            }

            KPIExcelData.KPIExcelDataBuilder kpiExcelDataBuilder = KPIExcelData.builder()
                    .sprintName(sprintDataForKPI195.getSprintName())
                    .defectId(Map.of(jiraDefectIssueDataForKPI195.getIssueKey(), jiraDefectIssueDataForKPI195.getUrl()))
                    .storyId(jiraIssueIdAndUrlDefectRelatesToMap)
                    .defectPriority(jiraDefectIssueDataForKPI195.getPriority())
                    .defectSeverity(jiraDefectIssueDataForKPI195.getSeverity())
                    .defectStatus(jiraDefectIssueDataForKPI195.getStatus())
                    .totalTimeSpent(jiraDefectIssueDataForKPI195.getTimeSpentInHours())
                    .defectSLA(String.format("%s %s", jiraDefectIssueDataForKPI195.getSlaData().getSla(),
                            jiraDefectIssueDataForKPI195.getSlaData().getTimeUnit()));
            Duration resolutionTime = calculateResolutionTimeForDefect(jiraDefectIssueDataForKPI195);
            long totalSeconds = resolutionTime.getSeconds();
            kpiExcelDataBuilder.resolutionTime(
                    String.format("%d days %d hours %d minutes",
                            (totalSeconds / (24 * 3600)),
                            ((totalSeconds / (24 * 3600)) / 3600),
                            ((totalSeconds % 3600) / 60))
            );
            severityByJiraIssueDefectsData.putIfAbsent(jiraDefectIssueDataForKPI195.getSlaData().getGeneralSeverity(),
                    new SeverityJiraIssuesDefectsBreachedSLAData());
            SeverityJiraIssuesDefectsBreachedSLAData severityJiraIssuesDefectsBreachedSLAData =
                    severityByJiraIssueDefectsData.get(jiraDefectIssueDataForKPI195.getSlaData().getGeneralSeverity());
            severityJiraIssuesDefectsBreachedSLAData.setSolvedIssues(severityJiraIssuesDefectsBreachedSLAData.getSolvedIssues() + 1);

            if(defectBreachedSLA(jiraDefectIssueDataForKPI195)) {
                severityJiraIssuesDefectsBreachedSLAData.setBreachedIssues(severityJiraIssuesDefectsBreachedSLAData.getBreachedIssues() + 1);
                kpiExcelDataBuilder.slaBreached("Y");
            } else {
                kpiExcelDataBuilder.slaBreached("N");
            }
            kpiExcelDataList.add(kpiExcelDataBuilder.build());
        });

        int totalResolvedDefects = 0;
        int totalBreachedDefects = 0;

        List<SeverityJiraDefectDrillDownValue> severityJiraDefectDrillDownValueList = new ArrayList<>();

        severityDataCountMap.computeIfAbsent(CommonConstant.OVERALL, k -> new ArrayList<>());

        for(Map.Entry<String, List<String>> entry : customApiConfig.getSeverity().entrySet()) {
            if(severityByJiraIssueDefectsData.containsKey(entry.getKey().toLowerCase())) {
                SeverityJiraIssuesDefectsBreachedSLAData severityJiraIssuesDefectsBreachedSLAData =
                        severityByJiraIssueDefectsData.get(entry.getKey());
                double defectsBreachedSLAForSeverity =
                        Math.floor((severityJiraIssuesDefectsBreachedSLAData.breachedIssues / (double) severityJiraIssuesDefectsBreachedSLAData.solvedIssues) * 100);
                severityJiraDefectDrillDownValueList.add(new SeverityJiraDefectDrillDownValue(entry.getKey(), defectsBreachedSLAForSeverity));
                totalResolvedDefects += severityJiraIssuesDefectsBreachedSLAData.solvedIssues;
                totalBreachedDefects += severityJiraIssuesDefectsBreachedSLAData.breachedIssues;

                Map<String, Object> hoverValueMap = new HashMap<>();
                hoverValueMap.put("totalResolvedIssues", severityJiraIssuesDefectsBreachedSLAData.solvedIssues);
                hoverValueMap.put("breachedPercentage", defectsBreachedSLAForSeverity);

                DataCount dataCount = DataCount.builder()
                        .data(String.valueOf(defectsBreachedSLAForSeverity))
                        .sProjectName(projectName)
                        .sSprintID(sprintDataForKPI195.getSprintId())
                        .sSprintName(sprintDataForKPI195.getSprintName())
                        .hoverValue(hoverValueMap)
                        .value(defectsBreachedSLAForSeverity)
                        .kpiGroup(StringUtils.capitalise(entry.getKey()))
                        .build();
                severityDataCountMap.computeIfAbsent(StringUtils.capitalise(entry.getKey()), k -> new ArrayList<>());
                severityDataCountMap.get(StringUtils.capitalise(entry.getKey())).add(dataCount);
                trendValueList.add(dataCount);
            } else {
                DataCount dataCount = DataCount.builder()
                        .data("0.0")
                        .sProjectName(projectName)
                        .sSprintID(sprintDataForKPI195.getSprintId())
                        .sSprintName(sprintDataForKPI195.getSprintName())
                        .value(0.0)
                        .kpiGroup(StringUtils.capitalise(entry.getKey()))
                        .build();
                severityDataCountMap.computeIfAbsent(StringUtils.capitalise(entry.getKey()), k -> new ArrayList<>());
                severityDataCountMap.get(StringUtils.capitalise(entry.getKey())).add(dataCount);
                trendValueList.add(dataCount);
            }
        }

        Map<String, Object> hoverValueMap = new HashMap<>();
        double defectsBreachedSLAForSprint =
                Math.floor((totalBreachedDefects / (double) totalResolvedDefects) * 100);

        hoverValueMap.put("totalResolvedIssues", totalBreachedDefects);
        hoverValueMap.put("breachedPercentage", defectsBreachedSLAForSprint);

        DataCount dataCount = DataCount.builder()
                .sProjectName(projectName)
                .sSprintID(sprintDataForKPI195.getSprintId())
                .sSprintName(sprintDataForKPI195.getSprintName())
                .hoverValue(hoverValueMap)
                .value(defectsBreachedSLAForSprint)
                .drillDown(severityJiraDefectDrillDownValueList)
                .kpiGroup(CommonConstant.OVERALL)
                .build();
        severityDataCountMap.get(CommonConstant.OVERALL).add(dataCount);
        trendValueList.add(dataCount);
        nodeIdMap.get(sprintDataForKPI195.getSprintNodeId()).setValue(severityDataCountMap);
    }

    private boolean defectBreachedSLA(JiraDefectIssueDataForKPI195 jiraDefectIssueDataForKPI195) {
        Duration durationToCloseTheDefect = calculateResolutionTimeForDefect(jiraDefectIssueDataForKPI195);

        SLAData slaData = jiraDefectIssueDataForKPI195.getSlaData();
        long slaInMillis;

        if(slaData.getTimeUnit().equalsIgnoreCase("Hours")) {
            slaInMillis = TimeUnit.HOURS.toMillis(slaData.getSla());
            return durationToCloseTheDefect.toMillis() > slaInMillis;
        }
        if(slaData.getTimeUnit().equalsIgnoreCase("Days")) {
            slaInMillis = TimeUnit.DAYS.toMillis(slaData.getSla());
            return durationToCloseTheDefect.toMillis() > slaInMillis;
        }

        return false;
    }

    private Duration calculateResolutionTimeForDefect(JiraDefectIssueDataForKPI195 jiraDefectIssueDataForKPI195) {
        LocalDateTime createdDate = jiraDefectIssueDataForKPI195.getCreatedDate();
        LocalDateTime closedDate = jiraDefectIssueDataForKPI195.getClosedDate();

        return Duration.between(createdDate, closedDate);
    }

    private SLAData determineDefectSLABasedOnSeverity(String jiraIssueSeverity) {
        Map<String, List<String>> severitiesMap = customApiConfig.getSeverity();

        Optional<String> severityOptional =
                severitiesMap.keySet()
                        .stream()
                        .filter(severity -> severitiesMap.get(severity)
                                .stream()
                                .anyMatch(severityMapping -> severityMapping.equalsIgnoreCase(jiraIssueSeverity)))
                        .findFirst();

        return severityOptional.map(SEVERITY_SLA_MAP::get).orElse(null);
    }
}