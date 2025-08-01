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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.enums.Filters;
import com.publicissapient.kpidashboard.apis.enums.JiraFeature;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.JiraKPIService;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.common.constant.NormalizedJira;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.jira.JiraHistoryChangeLog;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
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
            "s1", new SLAData(24, "s1", "Hours"),
            "s2", new SLAData(48, "s2", "Hours"),
            "s3", new SLAData(5, "s3", "Days"),
            "s4", new SLAData(10, "s4","Days")
    );

    private static final Random RANDOM = new Random();

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
        calculateAggregatedValue(root, nodeWiseKPIValue, KPICode.DEFECTS_BREACHED_SLAS);
        List<DataCount> trendValues = getTrendValues(kpiRequest, kpiElement, nodeWiseKPIValue, KPICode.DEFECTS_BREACHED_SLAS);
        kpiElement.setTrendValueList(trendValues);
        kpiElement.setNodeWiseKPIValue(nodeWiseKPIValue);

        kpiElement.setTrendValueList(trendValueList);
        return kpiElement;
    }

    private void calculateDefectsBreachSLAsForSprints(
            Map<String, Node> nodeIdMap,
            List<Node> sprintLeafNodes,
            List<DataCount> trendValueList,
            KpiElement kpiElement,
            KpiRequest kpiRequest
    ) {
        List<DefectsBreachedSLAsKPIData> kpiDataFromDatabase = fetchKPIDataFromDbV2(sprintLeafNodes, kpiRequest);

        if(CollectionUtils.isNotEmpty(kpiDataFromDatabase)) {
            kpiDataFromDatabase.forEach(defectsBreachedSLAsKPIData -> {
                DataCount dataCount = new DataCount();
                dataCount.setSProjectName(defectsBreachedSLAsKPIData.getProjectName());
                List<SprintDataForKPI195> sprintDataForKPI195List =
                        defectsBreachedSLAsKPIData.getSprintDataForKPI195List();
                sprintDataForKPI195List.forEach(sprintDataForKPI195 -> {
                    dataCount.setSSprintID(sprintDataForKPI195.getSprintId());
                    dataCount.setSSprintName(sprintDataForKPI195.getSprintName());
                    populateDataCountWithKPICalculatedValueForASprint(dataCount,
                            sprintDataForKPI195.getJiraIssueDataForKPI195List());
                    trendValueList.add(dataCount);
                    nodeIdMap.get(sprintDataForKPI195.getSprintNodeId()).setValue(List.of(dataCount));
                });
            });
        }
    }

    private void populateDataCountWithKPICalculatedValueForASprint(
            DataCount dataCount,
            List<JiraIssueDataForKPI195> jiraIssueDataForKPI195List
    ) {
        Map<String, SeverityJiraIssuesDefectsBreachedSLAData> severityByJiraIssueDefectsData = new HashMap<>();
        jiraIssueDataForKPI195List.forEach(jiraIssueDataForKPI195 -> {
            severityByJiraIssueDefectsData.putIfAbsent(jiraIssueDataForKPI195.getSlaData().getGeneralSeverity(),
                    new SeverityJiraIssuesDefectsBreachedSLAData());
            SeverityJiraIssuesDefectsBreachedSLAData severityJiraIssuesDefectsBreachedSLAData =
                    severityByJiraIssueDefectsData.get(jiraIssueDataForKPI195.getSlaData().getGeneralSeverity());
            severityJiraIssuesDefectsBreachedSLAData.setSolvedIssues(severityJiraIssuesDefectsBreachedSLAData.getSolvedIssues() + 1);

            if(defectBreachedSLA(jiraIssueDataForKPI195)) {
                severityJiraIssuesDefectsBreachedSLAData.setBreachedIssues(severityJiraIssuesDefectsBreachedSLAData.getBreachedIssues() + 1);
            }
        });

        int totalResolvedDefects = 0;
        int totalBreachedDefects = 0;

        List<SeverityJiraDefectDrillDownValue> severityJiraDefectDrillDownValueList = new ArrayList<>();

        for(Map.Entry<String, SeverityJiraIssuesDefectsBreachedSLAData> entry : severityByJiraIssueDefectsData.entrySet()) {
            SeverityJiraIssuesDefectsBreachedSLAData severityJiraIssuesDefectsBreachedSLAData =
                    severityByJiraIssueDefectsData.get(entry.getKey());
            double defectsBreachedSLAForSeverity =
                    Math.floor((severityJiraIssuesDefectsBreachedSLAData.breachedIssues / (double) severityJiraIssuesDefectsBreachedSLAData.solvedIssues) * 100);
            severityJiraDefectDrillDownValueList.add(new SeverityJiraDefectDrillDownValue(entry.getKey(), defectsBreachedSLAForSeverity));
            totalResolvedDefects += severityJiraIssuesDefectsBreachedSLAData.solvedIssues;
            totalBreachedDefects += severityJiraIssuesDefectsBreachedSLAData.breachedIssues;
        }
        dataCount.setDrillDown(severityJiraDefectDrillDownValueList);

        Map<String, Object> hoverValueMap = new HashMap<>();
        double defectsBreachedSLAForSprint =
                Math.floor((totalBreachedDefects / (double) totalResolvedDefects) * 100);

        hoverValueMap.put("totalResolvedIssues", totalBreachedDefects);
        hoverValueMap.put("breachedPercentage", defectsBreachedSLAForSprint);
        dataCount.setHoverValue(hoverValueMap);
        dataCount.setValue(defectsBreachedSLAForSprint);
    }

    private boolean defectBreachedSLA(JiraIssueDataForKPI195 jiraIssueDataForKPI195) {
        SLAData slaData = jiraIssueDataForKPI195.getSlaData();
        LocalDateTime createdDate = jiraIssueDataForKPI195.getCreatedDate();
        LocalDateTime closedDate = jiraIssueDataForKPI195.getClosedDate();

        Duration durationToCloseTheDefect = Duration.between(createdDate, closedDate);

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

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    private static class DefectsBreachedSLAsKPIData {
        private String basicProjectConfigId;
        private String projectName;

        private FieldMapping projectKPISettingsFieldMapping;

        private List<SprintDataForKPI195> sprintDataForKPI195List;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    private static class SprintDataForKPI195 {
        private String sprintId;
        private String sprintName;
        private String sprintNodeId;

        private List<JiraIssueDataForKPI195> jiraIssueDataForKPI195List;
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
    private static class JiraIssueDataForKPI195 {
        private String issueKey;
        private String url;
        private String priority;
        private String status;
        private String severity;
        private String timeSpentInHours;
        private String resolutionTime;
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

    private void testKPIExcelData() {
        /*
            Excel data:
            - Sprint Name
            - Defect ID (solvedDefects.number)
            - Story ID
            - Defect Priority
            - Defect Severity
            - Defect Status
            - Time Spent (In Hours)
            - Resolution Time
            - Defect SLA
            - SLA Breached (Y / N)
         */
        List<KPIExcelData> kpiExcelDataList = new ArrayList<>();
        KPIExcelData kpiExcelData = new KPIExcelData();
        kpiExcelData.setSprintName("Sprint name");
        kpiExcelData.setDefectId(Collections.emptyMap());
        kpiExcelData.setStoryId(Collections.emptyMap()); //get it from the issue id
        kpiExcelData.setDefectPriority("");
        kpiExcelData.setStatus("");
        kpiExcelData.setTotalTimeSpent("");
        kpiExcelData.setResolutionTime("");

    }

    public List<DefectsBreachedSLAsKPIData> fetchKPIDataFromDbV2(
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
                            .jiraIssueDataForKPI195List(new ArrayList<>())
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
                                .jiraIssueDataForKPI195List(new ArrayList<>())
                        .build());
                defectsBreachedSLAsKPIDataBuilder.sprintDataForKPI195List(sprintDataForKPI195List);
                defectsBreachedSLAsKPIDataList.add(defectsBreachedSLAsKPIDataBuilder.build());
            }
        });

        defectsBreachedSLAsKPIDataList.forEach(defectsBreachedSLAsKPIData -> {
            String basicProjectConfigId = defectsBreachedSLAsKPIData.getBasicProjectConfigId();
            Set<String> sprintIdsSet = defectsBreachedSLAsKPIData.getSprintDataForKPI195List().stream().map(SprintDataForKPI195::getSprintId).collect(Collectors.toSet());
            FieldMapping fieldMapping = defectsBreachedSLAsKPIData.getProjectKPISettingsFieldMapping();

            Criteria criteria = new Criteria();
            criteria =
                    criteria.and(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature()).is(basicProjectConfigId);
            if(CollectionUtils.isNotEmpty(fieldMapping.getIssueTypeKPI195())) {
                criteria = criteria.and(JiraFeature.ISSUE_TYPE.getFieldValueInFeature()).in(CommonUtils.convertToPatternList(fieldMapping.getIssueTypeKPI195()));
            }
            if(CollectionUtils.isNotEmpty(fieldMapping.getJiraLabelsQAKPI195())) {
                criteria =
                        criteria.and(JiraFeature.LABELS.getFieldValueInFeature())
                                .in(CommonUtils.convertToPatternList(fieldMapping.getJiraLabelsQAKPI195()));
            }
            List<JiraIssue> issuesCorrespondingToTheIssueTypesWithDefectLinkagesAndSelectedLabels =
                    mongoTemplate.find(new Query(criteria), JiraIssue.class);
            // Extracting all issues based on the remaining KPI setting filters and the issue numbers extracted in
            // the previous step
            if(CollectionUtils.isNotEmpty(issuesCorrespondingToTheIssueTypesWithDefectLinkagesAndSelectedLabels)) {
                Criteria criteria2 = new Criteria();
                criteria2 = criteria2.and(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature()).is(basicProjectConfigId)
                        .and(JiraFeature.SPRINT_ID.getFieldValueInFeature()).in(sprintIdsSet)
                        .and(JiraFeature.DEFECT_STORY_ID.getFieldValueInFeature())
                        .in(issuesCorrespondingToTheIssueTypesWithDefectLinkagesAndSelectedLabels.stream()
                                .map(com.publicissapient.kpidashboard.common.model.jira.JiraIssue::getNumber)
                                .collect(Collectors.toSet()))
                        .and(JiraFeature.ISSUE_TYPE.getFieldValueInFeature()).is(NormalizedJira.DEFECT_TYPE.getValue())
                        .and(JiraFeature.STATUS.getFieldValueInFeature()).in(CommonUtils.convertToPatternList(fieldMapping.getJiraDodQAKPI195()));
                if(CollectionUtils.isNotEmpty(fieldMapping.getDefectPriorityKPI195())) {
                    criteria2 = criteria2.and(JiraFeature.DEFECT_PRIORITY.getFieldValueInFeature()).nin(CommonUtils.convertToPatternList(fieldMapping.getDefectPriorityKPI195()));
                }
                if(CollectionUtils.isNotEmpty(fieldMapping.getResolutionTypeForRejectionQAKPI195())) {
                    criteria2 = criteria2.and("resolution")
                            .nin(CommonUtils.convertToPatternList(fieldMapping.getResolutionTypeForRejectionQAKPI195()));
                }
                if(CollectionUtils.isNotEmpty(fieldMapping.getIncludeRCAForQAKPI195())) {
                    criteria2 = criteria2.and("rootCauseList")
                            .in(CommonUtils.convertToPatternList(fieldMapping.getIncludeRCAForQAKPI195()));
                }
                List<JiraIssue> issuesAfterApplyingAllFilters = mongoTemplate.find(new Query(criteria2), JiraIssue.class);

                if(CollectionUtils.isNotEmpty(issuesAfterApplyingAllFilters)) {
                    Criteria criteria3 = new Criteria();
                    criteria3 = criteria3.and("storyID").in(issuesAfterApplyingAllFilters.stream().map(JiraIssue::getNumber).collect(Collectors.toSet()));
                    List<JiraIssueCustomHistory> jiraIssueCustomHistoryList = mongoTemplate.find(new Query(criteria3), JiraIssueCustomHistory.class);

                    if(CollectionUtils.isNotEmpty(jiraIssueCustomHistoryList)) {
                        jiraIssueCustomHistoryList.forEach(jiraIssueCustomHistory -> {
                            List<JiraHistoryChangeLog> statusUpdateChangelog =
                                    jiraIssueCustomHistory.getStatusUpdationLog();
                            statusUpdateChangelog.sort(Comparator.comparing(JiraHistoryChangeLog::getUpdatedOn));
                            JiraHistoryChangeLog lastStatusUpdate =
                                    statusUpdateChangelog.get(statusUpdateChangelog.size() - 1);

                            if(fieldMapping.getJiraDodQAKPI195().stream().anyMatch(status -> status.equalsIgnoreCase(lastStatusUpdate.getChangedTo()))) {
                                Optional<JiraIssue> jiraIssueOptional = issuesAfterApplyingAllFilters.stream()
                                        .filter(jiraIssue1 -> jiraIssue1.getNumber()
                                                .equalsIgnoreCase(jiraIssueCustomHistory.getStoryID()))
                                        .findFirst();
                                if(jiraIssueOptional.isPresent()) {
                                    JiraIssue jiraIssue = jiraIssueOptional.get();
                                    JiraIssueDataForKPI195.JiraIssueDataForKPI195Builder jiraIssueDataForKPI195Builder = JiraIssueDataForKPI195.builder()
                                            .issueKey(jiraIssueCustomHistory.getStoryID())
                                            .closedDate(lastStatusUpdate.getUpdatedOn());

                                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS");
                                    LocalDateTime dateTime = LocalDateTime.parse(jiraIssue.getCreatedDate(), formatter);
                                    jiraIssueDataForKPI195Builder.createdDate(dateTime)
                                            .url(jiraIssue.getUrl())
                                            .priority(jiraIssue.getPriority())
                                            .status(jiraIssue.getStatus());

                                    String issueSeverity = TEMP_SEVERITIES.get(RANDOM.nextInt(0, TEMP_SEVERITIES.size()));

                                    jiraIssueDataForKPI195Builder.severity(issueSeverity)
                                            .slaData(determineDefectSLABasedOnSeverity(issueSeverity));

                                    Optional<SprintDataForKPI195> sprintDataForKPI195Optional =
                                            defectsBreachedSLAsKPIData.getSprintDataForKPI195List()
                                                    .stream()
                                                    .filter(sprintDataForKPI195 -> sprintDataForKPI195.getSprintId().equals(jiraIssue.getSprintID()))
                                                    .findFirst();
                                    sprintDataForKPI195Optional.ifPresent(sprintDataForKPI195 ->
                                            sprintDataForKPI195.getJiraIssueDataForKPI195List().add(jiraIssueDataForKPI195Builder.build())
                                    );
                                }
                            }
                        });
                    }
                }
            }
        });
        return defectsBreachedSLAsKPIDataList;
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

    @Override
    public Map<String, Object> fetchKPIDataFromDb(List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
		return Collections.emptyMap();
    }

    @Override
    public Long calculateKPIMetrics(Map<String, Object> stringObjectMap) {
        return 0L;
    }
}