/*******************************************************************************
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 ******************************************************************************/
package com.publicissapient.kpidashboard.apis.zephyr.service;

import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.enums.JiraFeature;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPIExcelColumn;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KPIExcelData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.CommonUtils;
import com.publicissapient.kpidashboard.apis.util.KPIExcelUtility;
import com.publicissapient.kpidashboard.apis.util.KpiDataHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.constant.NormalizedJira;
import com.publicissapient.kpidashboard.common.constant.ProcessorConstants;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.ProjectToolConfig;
import com.publicissapient.kpidashboard.common.model.zephyr.TestCaseDetails;
import com.publicissapient.kpidashboard.common.model.zephyr.TestCaseExecutionData;
import com.publicissapient.kpidashboard.common.repository.zephyr.TestCaseDetailsRepository;
import com.publicissapient.kpidashboard.common.util.DateUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TestExecutionTimeKanbanServiceImpl  extends ZephyrKPIService<Double, List<Object>, Map<String, Object>>{
    private static final String TOOL_ZEPHYR = ProcessorConstants.ZEPHYR;
    private static final String TOOL_JIRA_TEST = ProcessorConstants.JIRA_TEST;
    private static final String TESTCASEKEY = "testCaseData";
    private static final String AUTOMATED_TESTCASE_KEY = "automatedTestCaseData";
    private static final String MANUAL_TESTCASE_KEY = "manualTestCaseData";
    private static final String NIN = "nin";
    private static final String AVGEXECUTIONTIME = "avgExecutionTimeSec";
    private static final String COUNT = "count";
    @Autowired
    private CustomApiConfig customApiConfig;
    @Autowired
    private KpiHelperService kpiHelperService;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private TestCaseDetailsRepository testCaseDetailsRepository;


    @Override
    public String getQualifierType() {
        return KPICode.TEST_EXECUTION_TIME_KANBAN.toString();
    }

    @Override
    public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, TreeAggregatorDetail treeAggregatorDetail) throws ApplicationException {
        log.info("[TEST EXECUTION TIME-KANBAN-LEAF-NODE-VALUE][{}]", kpiRequest.getRequestTrackerId());
        Node root = treeAggregatorDetail.getRoot();
        Map<String, Node> mapTmp = treeAggregatorDetail.getMapTmp();
        List<Node> projectList = treeAggregatorDetail.getMapOfListOfProjectNodes()
                .get(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT);

        dateWiseLeafNodeValue(mapTmp, projectList, kpiElement, kpiRequest);

        log.debug("[TEST EXECUTION TIME-KANBAN-LEAF-NODE-VALUE][{}]. Values of leaf node after KPI calculation {}",
                kpiRequest.getRequestTrackerId(), root);

        Map<Pair<String, String>, Node> nodeWiseKPIValue = new HashMap<>();

        calculateAggregatedValue(root, nodeWiseKPIValue, KPICode.TEST_EXECUTION_TIME_KANBAN);
        List<DataCount> trendValues = getTrendValues(kpiRequest, kpiElement, nodeWiseKPIValue,
                KPICode.TEST_EXECUTION_TIME_KANBAN);

        kpiElement.setTrendValueList(trendValues);

        kpiElement.setNodeWiseKPIValue(nodeWiseKPIValue);

        return kpiElement;
    }

    @Override
    public Double calculateKPIMetrics(Map<String, Object> stringObjectMap) {
        return 0.0;
    }

    @Override
    public Map<String, Object> fetchKPIDataFromDb(List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
        return fetchTestExecutionTimeKPIDataFromDb(leafNodeList);
    }

    /**
     * Populates KPI value to sprint leaf nodes and gives the trend analysis at
     * sprint wise.
     *
     * @param mapTmp
     * @param leafNodeList
     * @param kpiElement
     * @param kpiRequest
     */
    @SuppressWarnings("unchecked")
    private void dateWiseLeafNodeValue(Map<String, Node> mapTmp, List<Node> leafNodeList, KpiElement kpiElement,
                                       KpiRequest kpiRequest) {
        Map<String, Object> resultMap = fetchKPIDataFromDb(leafNodeList, null, null, kpiRequest);

        kpiWithoutFilter(resultMap, mapTmp, leafNodeList, kpiElement, kpiRequest);
    }

    private void kpiWithoutFilter(Map<String, Object> projectWiseJiraIssue, Map<String, Node> mapTmp,
                                  List<Node> leafNodeList, KpiElement kpiElement, KpiRequest kpiRequest) {
        List<KPIExcelData> excelData = new ArrayList<>();
        String requestTrackerId = getKanbanRequestTrackerId();
        Map<String, List<TestCaseDetails>> total = (Map<String, List<TestCaseDetails>>) projectWiseJiraIssue
                .get(TESTCASEKEY);
        Map<String, List<TestCaseDetails>> automated = (Map<String, List<TestCaseDetails>>) projectWiseJiraIssue
                .get(AUTOMATED_TESTCASE_KEY);

        Map<String, List<TestCaseDetails>> manualTestCases = (Map<String, List<TestCaseDetails>>) projectWiseJiraIssue
                .get(MANUAL_TESTCASE_KEY);
        leafNodeList.forEach(node -> {
            String projectNodeId = node.getId();
            String projectName = node.getProjectFilter().getName();
            String basicProjectConfId = node.getProjectFilter().getBasicProjectConfigId().toString();

            List<TestCaseDetails> totalTest = total.get(basicProjectConfId);
            List<TestCaseDetails> automatedTest = automated.get(basicProjectConfId);
            List<TestCaseDetails> manualTest = manualTestCases.get(basicProjectConfId);

            if (CollectionUtils.isNotEmpty(automatedTest) || CollectionUtils.isNotEmpty(totalTest)) {
                LocalDateTime currentDate = DateUtil.getTodayTime();
                List<DataCount> dc = new ArrayList<>();

                for (int i = 0; i < kpiRequest.getKanbanXaxisDataPoints(); i++) {
                    Map<String, Object> hoverMap = new LinkedHashMap<>();
                    // fetch date range based on period for which request came
                    CustomDateRange dateRange = KpiDataHelper.getStartAndEndDateTimeForDataFiltering(currentDate,
                            kpiRequest.getDuration());

                    List<TestCaseDetails> totalTestList = filterKanbanTotalDataBasedOnStartAndEndDate(totalTest,
                            dateRange.getEndDateTime());

                    List<TestCaseDetails> automatedTestList = filterKanbanAutomatedDataBasedOnStartAndEndDate(automatedTest,
                            dateRange.getEndDateTime());

                    List<TestCaseDetails> manualTestList = filterKanbanManualDataBasedOnStartAndEndDate(manualTest,
                            dateRange.getEndDateTime());

                    setHoverMap(automatedTestList, manualTestList, totalTestList, hoverMap);


                    double executionTimeForCurrentLeaf = 0.0;
                    executionTimeForCurrentLeaf = getValueFromHoverMap(hoverMap, "TOTAL", AVGEXECUTIONTIME, Double.class);

                    String date = getRange(dateRange, kpiRequest);
                    DataCount dcObj = getDataCountObject(executionTimeForCurrentLeaf, projectName, date, projectNodeId, hoverMap);
                    dc.add(dcObj);

                    populateExcelDataObject(requestTrackerId, excelData, totalTestList, automatedTestList, manualTestList, projectName, date);

                    if (kpiRequest.getDuration().equalsIgnoreCase(CommonConstant.WEEK)) {
                        currentDate = currentDate.minusWeeks(1);
                    } else if (kpiRequest.getDuration().equalsIgnoreCase(CommonConstant.MONTH)) {
                        currentDate = currentDate.minusMonths(1);
                    } else {
                        currentDate = currentDate.minusDays(1);
                    }
                }
                mapTmp.get(node.getId()).setValue(dc);
            }
        });
        kpiElement.setExcelData(excelData);
        kpiElement.setExcelColumns(KPIExcelColumn.TEST_EXECUTION_TIME_KANBAN.getColumns());
    }

    @SuppressWarnings("unchecked")
    public static <T> T getValueFromHoverMap(Map<String, Object> hoverMap, String category, String key, Class<T> type) {
        if (hoverMap == null || !hoverMap.containsKey(category)) {
            return null;
        }

        Object categoryObj = hoverMap.get(category);
        if (!(categoryObj instanceof Map)) {
            return null;
        }

        Map<String, Object> categoryMap = (Map<String, Object>) categoryObj;
        Object value = categoryMap.get(key);

        if (value == null) {
            return null;
        }

        if (type.isInstance(value)) {
            return type.cast(value);
        }

        // attempt conversion if possible (e.g. Integer -> Double)
        if (type == Double.class && value instanceof Number) {
            return (T) Double.valueOf(((Number) value).doubleValue());
        }
        if (type == Integer.class && value instanceof Number) {
            return (T) Integer.valueOf(((Number) value).intValue());
        }
        if (type == Long.class && value instanceof Number) {
            return (T) Long.valueOf(((Number) value).longValue());
        }

        return null; // type mismatch
    }

    private List<TestCaseDetails> filterKanbanTotalDataBasedOnStartAndEndDate(List<TestCaseDetails> tests,
                                                                              LocalDateTime endDate) {
        Predicate<TestCaseDetails> predicate = issue -> DateUtil.convertToUTCLocalDateTime(issue.getCreatedDate())
                .isBefore(endDate);
        return tests.stream().filter(predicate).collect(Collectors.toList());
    }

    private List<TestCaseDetails> filterKanbanAutomatedDataBasedOnStartAndEndDate(List<TestCaseDetails> tests,
                                                                                  LocalDateTime endDate) {
        Predicate<TestCaseDetails> predicate = issue -> StringUtils.isNotEmpty(issue.getTestAutomatedDate())
                && DateUtil.convertToUTCLocalDateTime(issue.getTestAutomatedDate()).isBefore(endDate);
        return Optional.ofNullable(tests).orElse(Collections.emptyList()).stream().filter(predicate)
                .collect(Collectors.toList());
    }

    private List<TestCaseDetails> filterKanbanManualDataBasedOnStartAndEndDate(List<TestCaseDetails> tests,
                                                                                  LocalDateTime endDate) {
        Predicate<TestCaseDetails> predicate = issue -> "No".equalsIgnoreCase(issue.getIsTestAutomated())
                && DateUtil.convertToUTCLocalDateTime(issue.getCreatedDate()).isBefore(endDate);
        return Optional.ofNullable(tests).orElse(Collections.emptyList()).stream().filter(predicate)
                .collect(Collectors.toList());
    }

    private String getRange(CustomDateRange dateRange, KpiRequest kpiRequest) {
        String range = null;
        if (kpiRequest.getDuration().equalsIgnoreCase(CommonConstant.WEEK)) {
            range = DateUtil.tranformUTCLocalTimeToZFormat(dateRange.getStartDateTime()) + " to "
                    + DateUtil.tranformUTCLocalTimeToZFormat(dateRange.getEndDateTime());
        } else if (kpiRequest.getDuration().equalsIgnoreCase(CommonConstant.MONTH)) {
            range = DateUtil.tranformUTCLocalTimeToZFormat(dateRange.getStartDateTime());
        } else {
            range = DateUtil.tranformUTCLocalTimeToZFormat(dateRange.getStartDateTime());
        }
        return range;
    }

    /**
     * @param automated
     * @param total
     * @param hoverMap
     */
    private void setHoverMap(List<TestCaseDetails> automated,List<TestCaseDetails> manual, List<TestCaseDetails> total, Map<String, Object> hoverMap) {
        populateCategoryData("TOTAL", total, hoverMap );
        populateCategoryData("MANUAL", manual, hoverMap );
        populateCategoryData("AUTOMATED", automated, hoverMap );
    }

    private DataCount getDataCountObject(double automation, String projectName, String date, String projectNodeId,
                                         Map<String, Object> hoverMap) {
        DataCount dataCount = new DataCount();
        dataCount.setData(String.valueOf(automation));
        dataCount.setSProjectName(projectName);
        dataCount.setDate(date);
        dataCount.setSSprintID(projectNodeId);
        dataCount.setSSprintName(projectName);
        dataCount.setSprintIds(new ArrayList<>(Arrays.asList(projectNodeId)));
        dataCount.setSprintNames(new ArrayList<>(Arrays.asList(projectName)));
        dataCount.setHoverValue(hoverMap);
        dataCount.setValue(automation);
        return dataCount;
    }

    /**
     * populates the validation data node of the KPI element.
     *
     * @param requestTrackerId
     * @param totalTest
     * @param automatedTest
     * @param dateProjectKey
     */
    private void populateExcelDataObject(String requestTrackerId, List<KPIExcelData> excelData,
                                         List<TestCaseDetails> totalTest, List<TestCaseDetails> automatedTest, List<TestCaseDetails> manualTest, String dateProjectKey, String date) {

        if (requestTrackerId.toLowerCase().contains(KPISource.EXCEL.name().toLowerCase())) {

            KPIExcelUtility.populateTestExecutionTimeExcelData(dateProjectKey, totalTest, automatedTest, manualTest, excelData,
                    KPICode.TEST_EXECUTION_TIME_KANBAN.getKpiId(), date);
        }
    }

    /**
     * Returns regression kpi data
     *
     * @param leafNodeList
     * @param
     * @return Map of automated and all regression test cases
     */
    public Map<String, Object> fetchTestExecutionTimeKPIDataFromDb(List<Node> leafNodeList) {
        Map<String, Object> resultListMap = new HashMap<>();
        if (CollectionUtils.isNotEmpty(leafNodeList)) {
            Map<String, List<String>> mapOfFilters = new LinkedHashMap<>();
            Map<String, Map<String, Object>> uniqueProjectMap = new HashMap<>();
            List<String> basicProjectConfigIds = new ArrayList<>();
            Map<ObjectId, Map<String, List<ProjectToolConfig>>> toolMap = (Map<ObjectId, Map<String, List<ProjectToolConfig>>>) cacheService
                    .cacheProjectToolConfigMapData();
            leafNodeList.forEach(leaf -> {
                ObjectId basicProjectConfigId = leaf.getProjectFilter().getBasicProjectConfigId();
                Map<String, Object> mapOfProjectFilters = new LinkedHashMap<>();
                basicProjectConfigIds.add(basicProjectConfigId.toString());

                List<ProjectToolConfig> zephyrTools = getToolConfigBasedOnProcessors(toolMap, basicProjectConfigId,
                        TOOL_ZEPHYR);

                List<ProjectToolConfig> jiraTestTools = getToolConfigBasedOnProcessors(toolMap, basicProjectConfigId,
                        TOOL_JIRA_TEST);

                List<String> regressionLabels = new ArrayList<>();
                List<String> regressionAutomationFolderPath = new ArrayList<>();
                // if Zephyr scale as a tool is setup with project
                if (CollectionUtils.isNotEmpty(zephyrTools)) {
                    setZephyrScaleConfig(zephyrTools, regressionLabels, regressionAutomationFolderPath);
                }
                if (CollectionUtils.isNotEmpty(jiraTestTools)) {
                    setZephyrSquadConfig(jiraTestTools, regressionLabels, mapOfProjectFilters);
                }

                if (CollectionUtils.isNotEmpty(regressionLabels)) {
                    mapOfProjectFilters.put(JiraFeature.LABELS.getFieldValueInFeature(),
                            CommonUtils.convertToPatternList(regressionLabels));
                }
                if (CollectionUtils.isNotEmpty(regressionAutomationFolderPath)) {
                    mapOfProjectFilters.put(JiraFeature.ATM_TEST_FOLDER.getFieldValueInFeature(),
                            CommonUtils.convertTestFolderToPatternList(regressionAutomationFolderPath));
                }

                uniqueProjectMap.put(basicProjectConfigId.toString(), mapOfProjectFilters);
            });

            mapOfFilters.put(JiraFeature.BASIC_PROJECT_CONFIG_ID.getFieldValueInFeature(),
                    basicProjectConfigIds.stream().distinct().collect(Collectors.toList()));
            mapOfFilters.put(JiraFeature.CAN_TEST_AUTOMATED.getFieldValueInFeature(),
                    Arrays.asList(NormalizedJira.YES_VALUE.getValue()));
            mapOfFilters.put(JiraFeature.ISSUE_TYPE.getFieldValueInFeature(),
                    Arrays.asList(NormalizedJira.TEST_TYPE.getValue()));

            List<TestCaseDetails> testCasesList = testCaseDetailsRepository.findTestDetails(mapOfFilters, uniqueProjectMap,
                    NIN);

            Map<String, List<TestCaseDetails>> towerWiseTotalMap = testCasesList.stream()
                    .collect(Collectors.groupingBy(TestCaseDetails::getBasicProjectConfigId, Collectors.toList()));

            Map<String, List<TestCaseDetails>> towerWiseAutomatedMap = testCasesList.stream()
                    .filter(feature -> NormalizedJira.YES_VALUE.getValue().equals(feature.getIsTestAutomated()))
                    .collect(Collectors.groupingBy(TestCaseDetails::getBasicProjectConfigId, Collectors.toList()));

            Map<String, List<TestCaseDetails>> towerWiseManualMap = testCasesList.stream()
                    .filter(feature -> NormalizedJira.NO_VALUE.getValue().equals(feature.getIsTestAutomated()))
                    .collect(Collectors.groupingBy(TestCaseDetails::getBasicProjectConfigId, Collectors.toList()));

            resultListMap.put(TESTCASEKEY, towerWiseTotalMap);
            resultListMap.put(AUTOMATED_TESTCASE_KEY, towerWiseAutomatedMap);
            resultListMap.put(MANUAL_TESTCASE_KEY, towerWiseManualMap);
        }
        return resultListMap;
    }

    /**
     * Helper method to compute count & average execution time for a category
     */
    private void populateCategoryData(String category, List<TestCaseDetails> testCases, Map<String, Object> hoverMap) {
        if (CollectionUtils.isNotEmpty(testCases)) {
            int totalCount = testCases.size();

            // Compute per-test average execution time in **minutes**
            long avgExecTimeMin = Math.round(
                    testCases.stream().mapToDouble(tc -> {
                        if (CollectionUtils.isEmpty(tc.getExecutions())) {
                            return 0.0;
                        }
                        double sum = tc.getExecutions().stream()
                                .filter(exec -> exec.getExecutionTime() != null)
                                .mapToDouble(TestCaseExecutionData::getExecutionTime)
                                .sum();

                        long count = tc.getExecutions().stream()
                                .filter(exec -> exec.getExecutionTime() != null)
                                .count();

                        return count > 0 ? (sum / count) : 0.0;
                    }).average().orElse(0.0) / 1000.0 / 60.0 // ms → sec → min
            );

            // Put results into hover map
            Map<String, Object> categoryData = new HashMap<>();
            categoryData.put(COUNT, totalCount);
            categoryData.put(AVGEXECUTIONTIME, avgExecTimeMin); // in minutes

            hoverMap.put(category, categoryData);
        } else {
            Map<String, Object> categoryData = new HashMap<>();
            categoryData.put(COUNT, 0);
            categoryData.put(AVGEXECUTIONTIME, 0L); // 0 minutes
            hoverMap.put(category, categoryData);
        }
    }


}
