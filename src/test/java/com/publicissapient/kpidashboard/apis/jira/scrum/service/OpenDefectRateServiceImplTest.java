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

package com.publicissapient.kpidashboard.apis.jira.scrum.service;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.CommonService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.FieldMappingDataFactory;
import com.publicissapient.kpidashboard.apis.data.JiraIssueDataFactory;
import com.publicissapient.kpidashboard.apis.data.JiraIssueHistoryDataFactory;
import com.publicissapient.kpidashboard.apis.data.KpiRequestFactory;
import com.publicissapient.kpidashboard.apis.data.SprintDetailsDataFactory;
import com.publicissapient.kpidashboard.apis.data.SprintWiseStoryDataFactory;
import com.publicissapient.kpidashboard.apis.enums.Filters;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.jira.service.JiraServiceR;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintWiseStory;
import com.publicissapient.kpidashboard.common.repository.application.FieldMappingRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueCustomHistoryRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;
import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class OpenDefectRateServiceImplTest {

    private static final String OPENBUGKEY = "openBugKey";
    private static final String TOTALBUGKEY = "totalBugKey";
    public Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
    public Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();
    List<JiraIssue> openBugList = new ArrayList<>();
    List<JiraIssue> totalBugList = new ArrayList<>();
    private List<AccountHierarchyData> accountHierarchyDataList = new ArrayList<>();
    private KpiRequest kpiRequest;
    private Map<String, Object> filterLevelMap;
    private Map<String, String> kpiWiseAggregation = new HashMap<>();

    @InjectMocks
    OpenDefectRateServiceImpl odrServiceImpl;
    @Mock
    JiraIssueRepository jiraIssueRepository;
    @Mock
    SprintRepository sprintRepository;
    @Mock
    CacheService cacheService;
    @Mock
    ConfigHelperService configHelperService;
    @Mock
    KpiHelperService kpiHelperService;
    @Mock
    ProjectBasicConfigRepository projectConfigRepository;
    @Mock
    FieldMappingRepository fieldMappingRepository;
    @Mock
    CustomApiConfig customApiSetting;
    @Mock
    JiraIssueCustomHistoryRepository jiraIssueCustomHistoryRepository;
    @Mock
    private JiraServiceR jiraKPIService;

    @Mock
    private FilterHelperService filterHelperService;
    @Mock
    private CommonService commonService;
    private List<SprintDetails> sprintDetailsList = new ArrayList<>();
    List<JiraIssue> totalIssueList = new ArrayList<>();
    private List<JiraIssueCustomHistory> jiraIssueCustomHistoryList = new ArrayList<>();

    @Before
    public void setup() {

        KpiRequestFactory kpiRequestFactory = KpiRequestFactory.newInstance();
        kpiRequest = kpiRequestFactory.findKpiRequest(KPICode.DEFECT_REMOVAL_EFFICIENCY.getKpiId());
        kpiRequest.setLabel("PROJECT");

        AccountHierarchyFilterDataFactory accountHierarchyFilterDataFactory = AccountHierarchyFilterDataFactory
                .newInstance();
        accountHierarchyDataList = accountHierarchyFilterDataFactory.getAccountHierarchyDataList();

        filterLevelMap = new LinkedHashMap<>();
        filterLevelMap.put("PROJECT", Filters.PROJECT);
        filterLevelMap.put("SPRINT", Filters.SPRINT);

        JiraIssueDataFactory jiraIssueDataFactory = JiraIssueDataFactory.newInstance();

        totalBugList = jiraIssueDataFactory.getBugs();
        totalIssueList = jiraIssueDataFactory.getJiraIssues();

        openBugList = totalBugList.stream().filter(bug -> bug.getStatus().equalsIgnoreCase("Open")).collect(Collectors.toList());
        SprintDetailsDataFactory sprintDetailsDataFactory = SprintDetailsDataFactory.newInstance();
        sprintDetailsList = sprintDetailsDataFactory.getSprintDetails();
        JiraIssueHistoryDataFactory jiraIssueHistoryDataFactory = JiraIssueHistoryDataFactory.newInstance();
        jiraIssueCustomHistoryList = jiraIssueHistoryDataFactory.getJiraIssueCustomHistory();
        ProjectBasicConfig projectConfig = new ProjectBasicConfig();
        projectConfig.setId(new ObjectId("6335363749794a18e8a4479b"));
        projectConfig.setProjectName("Scrum Project");
        projectConfigMap.put(projectConfig.getProjectName(), projectConfig);

        FieldMappingDataFactory fieldMappingDataFactory = FieldMappingDataFactory
                .newInstance("/json/default/scrum_project_field_mappings.json");
        FieldMapping fieldMapping = fieldMappingDataFactory.getFieldMappings().get(0);
        fieldMappingMap.put(fieldMapping.getBasicProjectConfigId(), fieldMapping);
        configHelperService.setProjectConfigMap(projectConfigMap);
        configHelperService.setFieldMappingMap(fieldMappingMap);
        when(configHelperService.getFieldMapping(projectConfig.getId())).thenReturn(fieldMapping);
        // set aggregation criteria kpi wise
        kpiWiseAggregation.put("openDefectRate", "percentile");
    }

    @After
    public void cleanup() {
        totalBugList = null;
        openBugList = null;
        jiraIssueRepository.deleteAll();
    }

    @Test
    public void testCalculateKPIMetrics() {
        Map<String, Object> filterComponentIdWiseDefectMap = new HashMap<>();
        filterComponentIdWiseDefectMap.put(OPENBUGKEY, openBugList);
        filterComponentIdWiseDefectMap.put(TOTALBUGKEY, totalBugList);
        Double dreValue = odrServiceImpl.calculateKPIMetrics(filterComponentIdWiseDefectMap);
        assertThat("ODR value :", dreValue, equalTo(10.0));
    }

    @Test
    public void testFetchKPIDataFromDbData() throws ApplicationException {
        TreeAggregatorDetail treeAggregatorDetail = KPIHelperUtil.getTreeLeafNodesGroupedByFilter(kpiRequest,
                accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
        List<Node> leafNodeList = new ArrayList<>();
        leafNodeList = KPIHelperUtil.getLeafNodes(treeAggregatorDetail.getRoot(), leafNodeList, false);
        String startDate = leafNodeList.get(0).getSprintFilter().getStartDate();
        String endDate = leafNodeList.get(leafNodeList.size() - 1).getSprintFilter().getEndDate();

        SprintWiseStoryDataFactory sprintWiseStoryDataFactory = SprintWiseStoryDataFactory.newInstance();
        List<SprintWiseStory> storyData = sprintWiseStoryDataFactory.getSprintWiseStories();
        Map<String, List<SprintDetails>> sprintWiseProjectData = sprintDetailsList.stream()
                .collect(Collectors.groupingBy(SprintDetails::getSprintID));

        JiraIssueDataFactory jiraIssueDataFactory = JiraIssueDataFactory.newInstance();
        List<JiraIssue> defectData = jiraIssueDataFactory.getBugs();

        when(sprintRepository.findBySprintIDIn(Mockito.any())).thenReturn(sprintDetailsList);
        when(jiraIssueRepository.findIssueByNumber(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(totalIssueList);
        when(jiraIssueRepository.findIssueAndDescByNumber(Mockito.any())).thenReturn(totalIssueList);
        when(jiraIssueCustomHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(Mockito.any(), Mockito.any()))
                .thenReturn(new ArrayList<>());
        when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
        Map<String, Object> defectDataListMap = odrServiceImpl.fetchKPIDataFromDb(leafNodeList, startDate, endDate,
                kpiRequest);
        assertNotNull(defectDataListMap);
    }

    @Test
    public void testGetODR() throws ApplicationException {

        TreeAggregatorDetail treeAggregatorDetail = KPIHelperUtil.getTreeLeafNodesGroupedByFilter(kpiRequest,
                accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);
        Map<String, List<String>> maturityRangeMap = new HashMap<>();
        maturityRangeMap.put("openDefectRate", Arrays.asList("-30", "30-10", "10-5", "5-2", "2-"));

        when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);

        String kpiRequestTrackerId = "Excel-Jira-5be544de025de212549176a9";
        when(cacheService.getFromApplicationCache(Constant.KPI_REQUEST_TRACKER_ID_KEY + KPISource.JIRA.name()))
                .thenReturn(kpiRequestTrackerId);
        when(odrServiceImpl.getRequestTrackerId()).thenReturn(kpiRequestTrackerId);
        when(sprintRepository.findBySprintIDIn(Mockito.any())).thenReturn(sprintDetailsList);
        when(jiraIssueRepository.findIssueAndDescByNumber(Mockito.any())).thenReturn(totalIssueList);
        when(jiraIssueRepository.findIssueByNumber(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(totalIssueList);
        when(jiraIssueRepository.findLinkedDefects(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(totalBugList);
        when(jiraIssueCustomHistoryRepository.findByStoryIDInAndBasicProjectConfigIdIn(Mockito.any(), Mockito.any()))
                .thenReturn(jiraIssueCustomHistoryList);
        try {
            KpiElement kpiElement = odrServiceImpl.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0),
                    treeAggregatorDetail);
            assertThat("ODR Value :", ((List<DataCount>) kpiElement.getTrendValueList()).size(), equalTo(1));
        } catch (Exception exception) {
            System.out.println(exception);
        }
    }

    @Test
    public void testQualifierType() {
        String kpiName = KPICode.OPEN_DEFECT_RATE.name();
        String type = odrServiceImpl.getQualifierType();
        assertThat("KPI NAME: ", type, equalTo(kpiName));
    }

    @Test
    public void testCalculateKpiValue() {
        List<Double> valueList = Arrays.asList(10D, 20D, 30D, 40D);
        String kpiId = "kpi191";
        Double result = odrServiceImpl.calculateKpiValue(valueList, kpiId);
        assertEquals(0.0, result);
    }

    @Test
    public void calculateThresholdValue() {
        FieldMapping fieldMapping = new FieldMapping();
        fieldMapping.setThresholdValueKPI191("15");
        Assert.assertEquals(Double.valueOf(15D), odrServiceImpl
                .calculateThresholdValue(fieldMapping.getThresholdValueKPI191(), KPICode.OPEN_DEFECT_RATE.getKpiId()));
    }
}