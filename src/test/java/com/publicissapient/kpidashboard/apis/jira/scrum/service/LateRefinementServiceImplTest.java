/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.jira.scrum.service;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.constant.Constant;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.FieldMappingDataFactory;
import com.publicissapient.kpidashboard.apis.data.JiraIssueDataFactory;
import com.publicissapient.kpidashboard.apis.data.JiraIssueHistoryDataFactory;
import com.publicissapient.kpidashboard.apis.data.KpiRequestFactory;
import com.publicissapient.kpidashboard.apis.data.SprintDetailsDataFactory;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.jira.service.iterationdashboard.JiraIterationServiceR;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.TreeAggregatorDetail;
import com.publicissapient.kpidashboard.apis.util.KPIHelperUtil;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssue;
import com.publicissapient.kpidashboard.common.model.jira.JiraIssueCustomHistory;
import com.publicissapient.kpidashboard.common.model.jira.SprintDetails;
import com.publicissapient.kpidashboard.common.model.jira.SprintIssue;
import com.publicissapient.kpidashboard.common.repository.application.FieldMappingRepository;
import com.publicissapient.kpidashboard.common.repository.application.ProjectBasicConfigRepository;
import com.publicissapient.kpidashboard.common.repository.jira.JiraIssueRepository;
import com.publicissapient.kpidashboard.common.repository.jira.SprintRepository;

@RunWith(MockitoJUnitRunner.class)
public class LateRefinementServiceImplTest {

    @Mock
    CacheService cacheService;
    @Mock
    private JiraIssueRepository jiraIssueRepository;
    @Mock
    private ConfigHelperService configHelperService;
    @Mock
    private SprintRepository sprintRepository;
    @Mock
    private ProjectBasicConfigRepository projectConfigRepository;

    @Mock
    private FieldMappingRepository fieldMappingRepository;

    @InjectMocks
    private LateRefinementServiceImpl lateRefinementService;

    @Mock
    private JiraIterationServiceR jiraService;

    private List<JiraIssue> storyList = new ArrayList<>();
    private Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
    private Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();
    private SprintDetails sprintDetails = new SprintDetails();
    private List<AccountHierarchyData> accountHierarchyDataList = new ArrayList<>();
    private List<JiraIssueCustomHistory> jiraIssueCustomHistoryList = new ArrayList<>();
    private KpiRequest kpiRequest;

    @Before
    public void setup() {
        KpiRequestFactory kpiRequestFactory = KpiRequestFactory.newInstance();
        kpiRequest = kpiRequestFactory.findKpiRequest("kpi119");
        kpiRequest.setLabel("PROJECT");

        AccountHierarchyFilterDataFactory accountHierarchyFilterDataFactory = AccountHierarchyFilterDataFactory
                .newInstance();
        accountHierarchyDataList = accountHierarchyFilterDataFactory.getAccountHierarchyDataList();

        setMockProjectConfig();
        setMockFieldMapping();
        sprintDetails = SprintDetailsDataFactory.newInstance().getSprintDetails().get(0);
        List<String> jiraIssueList = sprintDetails.getTotalIssues().stream().filter(Objects::nonNull)
                .map(SprintIssue::getNumber).distinct().collect(Collectors.toList());
        JiraIssueDataFactory jiraIssueDataFactory = JiraIssueDataFactory.newInstance();
        storyList = jiraIssueDataFactory.findIssueByNumberList(jiraIssueList);
        JiraIssueHistoryDataFactory jiraIssueHistoryDataFactory = JiraIssueHistoryDataFactory.newInstance();
        jiraIssueCustomHistoryList = jiraIssueHistoryDataFactory.getJiraIssueCustomHistory();

    }

    private void setMockProjectConfig() {
        ProjectBasicConfig projectConfig = new ProjectBasicConfig();
        projectConfig.setId(new ObjectId("6335363749794a18e8a4479b"));
        projectConfig.setProjectName("Scrum Project");
        projectConfigMap.put(projectConfig.getProjectName(), projectConfig);
    }

    private void setMockFieldMapping() {
        FieldMappingDataFactory fieldMappingDataFactory = FieldMappingDataFactory
                .newInstance("/json/default/scrum_project_field_mappings.json");
        FieldMapping fieldMapping = fieldMappingDataFactory.getFieldMappings().get(0);
        fieldMappingMap.put(fieldMapping.getBasicProjectConfigId(), fieldMapping);
        configHelperService.setFieldMappingMap(fieldMappingMap);
    }


    @Test
    public void testGetKpiDataProject_activeSprint() throws ApplicationException {
        TreeAggregatorDetail treeAggregatorDetail = KPIHelperUtil.getTreeLeafNodesGroupedByFilter(kpiRequest,
                accountHierarchyDataList, new ArrayList<>(), "hierarchyLevelOne", 5);

        sprintDetails.setState("ACTIVE");
        when(jiraService.getCurrentSprintDetails()).thenReturn(sprintDetails);

        sprintDetails.setStartDate("2022-09-28T17:00:00.000Z");
        when(configHelperService.getFieldMappingMap()).thenReturn(fieldMappingMap);
        when(jiraService.getJiraIssuesCustomHistoryForCurrentSprint()).thenReturn(jiraIssueCustomHistoryList);
        when(jiraService.getJiraIssuesForCurrentSprint()).thenReturn(storyList);
        try {
            KpiElement kpiElement = lateRefinementService.getKpiData(kpiRequest, kpiRequest.getKpiList().get(0),
                    treeAggregatorDetail.getMapOfListOfLeafNodes().get("sprint").get(0));
            assertNotNull(kpiElement.getTrendValueList());

        } catch (ApplicationException enfe) {

        }

    }

    @Test
    public void testGetQualifierType() {
        assertThat(lateRefinementService.getQualifierType(), equalTo("LATE_REFINEMENT"));
    }

    @After
    public void cleanup() {
        jiraIssueRepository.deleteAll();

    }
}
