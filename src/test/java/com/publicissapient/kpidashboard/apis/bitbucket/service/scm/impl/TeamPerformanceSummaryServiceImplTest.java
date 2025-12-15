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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.bitbucket.model.PerformanceSummary;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.model.ProjectFilter;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.ProjectHierarchy;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;

@RunWith(MockitoJUnitRunner.class)
public class TeamPerformanceSummaryServiceImplTest {

	@Mock
	private ScmKpiHelperService scmKpiHelperService;

	@Mock
	private KpiHelperService kpiHelperService;

	@Mock
	private ConfigHelperService configHelperService;

	@Mock
	private FilterHelperService filterHelperService;

	@InjectMocks
	private TeamPerformanceSummaryServiceImpl teamPerformanceSummaryService;

	private KpiRequest kpiRequest;
	private Node projectNode;
	private List<Tool> scmTools;
	private List<ScmCommits> commits;
	private List<ScmMergeRequests> mergeRequests;
	private ObjectId projectConfigId;

	@Before
	public void setUp() {
		projectConfigId = new ObjectId("507f1f77bcf86cd799439011");
		setupKpiRequest();
		setupProjectNode();
		setupScmTools();
		setupScmData();
	}

	private void setupKpiRequest() {
		kpiRequest = new KpiRequest();
		kpiRequest.setLevel(5);
		kpiRequest.setLabel("PROJECT");
		kpiRequest.setIds(new String[] { "7" });

		Map<String, List<String>> selectedMap = new HashMap<>();
		selectedMap.put(CommonConstant.DATE, List.of("WEEKS"));
		kpiRequest.setSelectedMap(selectedMap);
	}

	private void setupProjectNode() {
		projectNode = new Node();
		projectNode.setId("project1");
		projectNode.setName("Test Project");

		ProjectHierarchy projectHierarchy = new ProjectHierarchy();
		projectHierarchy.setBasicProjectConfigId(projectConfigId);
		projectNode.setProjectHierarchy(projectHierarchy);

		ProjectFilter projectFilter = new ProjectFilter("project1", "Test Project", projectConfigId);
		projectNode.setProjectFilter(projectFilter);
	}

	private void setupScmTools() {
		scmTools = new ArrayList<>();
		Tool tool = new Tool();
		tool.setBranch("main");
		scmTools.add(tool);
	}

	private void setupScmData() {
		commits = new ArrayList<>();
		ScmCommits commit = new ScmCommits();
		commit.setIsMergeCommit(false);
		commit.setAddedLines(100);
        commit.setRemovedLines(24);
		commits.add(commit);

		mergeRequests = new ArrayList<>();
		ScmMergeRequests mr = new ScmMergeRequests();
		mr.setPickedForReviewOn(System.currentTimeMillis());
		mr.setCreatedDate(System.currentTimeMillis() - 3600000);
		mergeRequests.add(mr);
	}

	@Test
	public void testGetTeamPerformanceSummary_Success() {
		try (MockedStatic<DeveloperKpiHelper> mockedHelper = Mockito.mockStatic(DeveloperKpiHelper.class)) {
			List<AccountHierarchyData> accountHierarchyDataList = new ArrayList<>();
			AccountHierarchyData accountHierarchyData = new AccountHierarchyData();
			List<Node> nodeList = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				nodeList.add(i == 4 ? projectNode : new Node());
			}
			accountHierarchyData.setNode(nodeList);
			accountHierarchyDataList.add(accountHierarchyData);

			when(filterHelperService.getHierarchyLevelId(anyInt(), anyString(), anyBoolean())).thenReturn("PROJECT");
			when(filterHelperService.getFilteredBuilds(any(), anyString())).thenReturn(accountHierarchyDataList);
			when(kpiHelperService.getAuthorizedFilteredList(any(), any(), anyBoolean()))
					.thenReturn(accountHierarchyDataList);

			mockedHelper.when(() -> DeveloperKpiHelper.getScmToolsForProject(any(), any(), any()))
					.thenReturn(scmTools);
			mockedHelper.when(() -> DeveloperKpiHelper.getBranchSubFilter(any(), anyString())).thenReturn("main");
			mockedHelper.when(() -> DeveloperKpiHelper.filterCommitsForBranch(any(), any())).thenReturn(commits);
			mockedHelper.when(() -> DeveloperKpiHelper.filterMergeRequestsForBranch(any(), any()))
					.thenReturn(mergeRequests);

			Map<String, Object> scmDataMap = new HashMap<>();
			scmDataMap.put("commitList", commits);
			scmDataMap.put("mrList", mergeRequests);

			when(scmKpiHelperService.getMergeRequests(any(), any())).thenReturn(mergeRequests);
			when(scmKpiHelperService.getCommitDetails(any(), any())).thenReturn(commits);

			List<PerformanceSummary> result = teamPerformanceSummaryService.getTeamPerformanceSummary(kpiRequest);

			assertNotNull(result);
			assertFalse(result.isEmpty());
		}
	}

	@Test
	public void testGetTeamPerformanceSummary_NoScmTools() {
		try (MockedStatic<DeveloperKpiHelper> mockedHelper = Mockito.mockStatic(DeveloperKpiHelper.class)) {
			List<AccountHierarchyData> accountHierarchyDataList = new ArrayList<>();
			AccountHierarchyData accountHierarchyData = new AccountHierarchyData();
			List<Node> nodeList = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				nodeList.add(i == 4 ? projectNode : new Node());
			}
			accountHierarchyData.setNode(nodeList);
			accountHierarchyDataList.add(accountHierarchyData);

			when(filterHelperService.getHierarchyLevelId(anyInt(), anyString(), anyBoolean())).thenReturn("PROJECT");
			when(filterHelperService.getFilteredBuilds(any(), anyString())).thenReturn(accountHierarchyDataList);
			when(kpiHelperService.getAuthorizedFilteredList(any(), any(), anyBoolean()))
					.thenReturn(accountHierarchyDataList);

			mockedHelper.when(() -> DeveloperKpiHelper.getScmToolsForProject(any(), any(), any()))
					.thenReturn(Collections.emptyList());

			List<PerformanceSummary> result = teamPerformanceSummaryService.getTeamPerformanceSummary(kpiRequest);

			assertNotNull(result);
			assertTrue(result.isEmpty());
		}
	}

	@Test
	public void testGetTeamPerformanceSummary_NoFilteredData() {
		when(filterHelperService.getHierarchyLevelId(anyInt(), anyString(), anyBoolean())).thenReturn("PROJECT");
		when(filterHelperService.getFilteredBuilds(any(), anyString())).thenReturn(Collections.emptyList());

		List<PerformanceSummary> result = teamPerformanceSummaryService.getTeamPerformanceSummary(kpiRequest);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testGetTeamPerformanceSummary_NoAuthorizedData() {
		List<AccountHierarchyData> accountHierarchyDataList = new ArrayList<>();
		AccountHierarchyData accountHierarchyData = new AccountHierarchyData();
		List<Node> nodeList = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			nodeList.add(i == 4 ? projectNode : new Node());
		}
		accountHierarchyData.setNode(nodeList);
		accountHierarchyDataList.add(accountHierarchyData);

		when(filterHelperService.getHierarchyLevelId(anyInt(), anyString(), anyBoolean())).thenReturn("PROJECT");
		when(filterHelperService.getFilteredBuilds(any(), anyString())).thenReturn(accountHierarchyDataList);
		when(kpiHelperService.getAuthorizedFilteredList(any(), any(), anyBoolean()))
				.thenReturn(Collections.emptyList());

		List<PerformanceSummary> result = teamPerformanceSummaryService.getTeamPerformanceSummary(kpiRequest);

		assertNotNull(result);
		assertTrue(result.isEmpty());
	}
}
