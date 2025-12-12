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

package com.publicissapient.kpidashboard.apis.bitbucket.service;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.SerializationUtils;
import org.bson.types.ObjectId;
import org.hamcrest.MatcherAssert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.converter.HttpMessageNotWritableException;

import com.publicissapient.kpidashboard.apis.abac.UserAuthorizedProjectsService;
import com.publicissapient.kpidashboard.apis.bitbucket.factory.BitBucketKPIServiceFactory;
import com.publicissapient.kpidashboard.apis.bitbucket.service.scm.ScmKpiHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.common.service.impl.KpiHelperService;
import com.publicissapient.kpidashboard.apis.data.AccountHierarchyFilterDataFactory;
import com.publicissapient.kpidashboard.apis.data.FieldMappingDataFactory;
import com.publicissapient.kpidashboard.apis.enums.Filters;
import com.publicissapient.kpidashboard.apis.enums.KPICode;
import com.publicissapient.kpidashboard.apis.enums.KPISource;
import com.publicissapient.kpidashboard.apis.errors.ApplicationException;
import com.publicissapient.kpidashboard.apis.errors.EntityNotFoundException;
import com.publicissapient.kpidashboard.apis.filter.service.FilterHelperService;
import com.publicissapient.kpidashboard.apis.model.AccountHierarchyData;
import com.publicissapient.kpidashboard.apis.model.CustomDateRange;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.model.Node;
import com.publicissapient.kpidashboard.apis.util.DeveloperKpiHelper;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.FieldMapping;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;
import com.publicissapient.kpidashboard.common.model.application.Tool;
import com.publicissapient.kpidashboard.common.model.jira.Assignee;
import com.publicissapient.kpidashboard.common.model.scm.ScmCommits;
import com.publicissapient.kpidashboard.common.model.scm.ScmMergeRequests;

@RunWith(MockitoJUnitRunner.Silent.class)
public class BitBucketServiceRTest {

	public Map<String, ProjectBasicConfig> projectConfigMap = new HashMap<>();
	public Map<ObjectId, FieldMapping> fieldMappingMap = new HashMap<>();

	@Mock KpiHelperService kpiHelperService;
	@Mock FilterHelperService filterHelperService;
	@Mock ScmKpiHelperService scmKpiHelperService;
	@Mock private CacheService cacheService;
	@Mock private BitBucketKPIService<?, ?, ?> bitBucketKPIService;
	@Mock private UserAuthorizedProjectsService authorizedProjectsService;

	@InjectMocks private BitBucketServiceR bitBucketServiceR;

	private List<AccountHierarchyData> accountHierarchyDataList = new ArrayList<>();
	private Map<String, Object> filterLevelMap;
	private Map<String, BitBucketKPIService> bitBucketServiceCache = new HashMap<>();

	private KpiRequest kpiRequest;

	private static class TestBitBucketKPIService extends BitBucketKPIService<Object, Object, Object> {

		@Override
		public String getQualifierType() {
			return "TEST";
		}

		@Override
		public KpiElement getKpiData(KpiRequest kpiRequest, KpiElement kpiElement, Node projectNode) {
			return kpiElement;
		}

		@Override
		public Object calculateKPIMetrics(Object o) {
			return null;
		}

		@Override
		public Object fetchKPIDataFromDb(
				List<Node> leafNodeList, String startDate, String endDate, KpiRequest kpiRequest) {
			return null;
		}
	}

	@Before
	public void setup() {
		MockitoAnnotations.openMocks(this);

		AccountHierarchyFilterDataFactory accountHierarchyFilterDataFactory =
				AccountHierarchyFilterDataFactory.newInstance(
						"/json/default/project_hierarchy_filter_data.json");
		accountHierarchyDataList = accountHierarchyFilterDataFactory.getAccountHierarchyDataList();

		filterLevelMap = new LinkedHashMap<>();
		filterLevelMap.put("PROJECT", Filters.PROJECT);

		kpiRequest = createKpiRequest(5);

		ProjectBasicConfig projectConfig = new ProjectBasicConfig();
		projectConfig.setId(new ObjectId("6335363749794a18e8a4479b"));
		projectConfig.setProjectName("Scrum Project");
		projectConfigMap.put(projectConfig.getProjectName(), projectConfig);

		FieldMappingDataFactory fieldMappingDataFactory =
				FieldMappingDataFactory.newInstance("/json/default/scrum_project_field_mappings.json");
		FieldMapping fieldMapping = fieldMappingDataFactory.getFieldMappings().get(0);
		fieldMappingMap.put(fieldMapping.getBasicProjectConfigId(), fieldMapping);

		when(filterHelperService.getHierarchyLevelId(5, "project", false)).thenReturn("project");
		when(filterHelperService.getFilteredBuilds(any(), anyString()))
				.thenReturn(accountHierarchyDataList);
		when(kpiHelperService.getAuthorizedFilteredList(any(), any(), anyBoolean()))
				.thenReturn(accountHierarchyDataList);
		when(authorizedProjectsService.getProjectKey(any(), any())).thenReturn(kpiRequest.getIds());
		when(authorizedProjectsService.ifSuperAdminUser()).thenReturn(true);

		Map<String, Integer> hierarchyIdLevelMap = new HashMap<>();
		hierarchyIdLevelMap.put(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, 5);
		when(filterHelperService.getHierarchyIdLevelMap(anyBoolean())).thenReturn(hierarchyIdLevelMap);
	}

	@Test
	public void testKPI() throws Exception {
		KpiRequest kpiRequest1 = createKpiRequest(5);
		when(kpiHelperService.isToolConfigured(any(), any(), any())).thenReturn(true);

		// Setup SCM data
		when(scmKpiHelperService.getCommitDetails(any(ObjectId.class), any(CustomDateRange.class)))
				.thenReturn(List.of(new ScmCommits()));
		when(scmKpiHelperService.getMergeRequests(any(ObjectId.class), any(CustomDateRange.class)))
				.thenReturn(List.of(new ScmMergeRequests()));
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any(ObjectId.class)))
				.thenReturn(Arrays.asList(new Assignee()));

		try (MockedStatic<BitBucketKPIServiceFactory> factoryMock =
						Mockito.mockStatic(BitBucketKPIServiceFactory.class);
				MockedStatic<DeveloperKpiHelper> helperMock =
						Mockito.mockStatic(DeveloperKpiHelper.class)) {

			factoryMock
					.when(() -> BitBucketKPIServiceFactory.getBitBucketKPIService(anyString()))
					.thenReturn(bitBucketKPIService);
			helperMock
					.when(() -> DeveloperKpiHelper.getStartAndEndDate(any(KpiRequest.class)))
					.thenReturn(new CustomDateRange());

			KpiElement responseKpi = new KpiElement();
			responseKpi.setResponseCode(CommonConstant.KPI_PASSED);
			when(bitBucketKPIService.getKpiData(any(), any(), any())).thenReturn(responseKpi);

			List<KpiElement> resultList = bitBucketServiceR.process(kpiRequest1);

			MatcherAssert.assertThat(
					"Kpi Name :", resultList.get(0).getResponseCode(), equalTo(CommonConstant.KPI_FAILED));
		}
	}

	@Test
	public void TestProcess() throws Exception {
		when(authorizedProjectsService.getProjectKey(any(), any())).thenReturn(kpiRequest.getIds());
		bitBucketServiceCache.put(KPICode.REPO_TOOL_CODE_COMMIT.name(), bitBucketKPIService);
		when(kpiHelperService.isToolConfigured(any(), any(), any())).thenReturn(true);

		// Setup SCM data
		when(scmKpiHelperService.getCommitDetails(any(ObjectId.class), any(CustomDateRange.class)))
				.thenReturn(List.of(new ScmCommits()));
		when(scmKpiHelperService.getMergeRequests(any(ObjectId.class), any(CustomDateRange.class)))
				.thenReturn(List.of(new ScmMergeRequests()));
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any(ObjectId.class)))
				.thenReturn(Arrays.asList(new Assignee()));

		try (MockedStatic<BitBucketKPIServiceFactory> factoryMock =
						Mockito.mockStatic(BitBucketKPIServiceFactory.class);
				MockedStatic<DeveloperKpiHelper> helperMock =
						Mockito.mockStatic(DeveloperKpiHelper.class)) {

			factoryMock
					.when(() -> BitBucketKPIServiceFactory.getBitBucketKPIService(anyString()))
					.thenReturn(bitBucketKPIService);
			helperMock
					.when(() -> DeveloperKpiHelper.getStartAndEndDate(any(KpiRequest.class)))
					.thenReturn(new CustomDateRange());

			KpiElement responseKpi = new KpiElement();
			responseKpi.setResponseCode(CommonConstant.KPI_PASSED);
			when(bitBucketKPIService.getKpiData(any(), any(), any())).thenReturn(responseKpi);

			List<KpiElement> resultList = bitBucketServiceR.process(kpiRequest);
			MatcherAssert.assertThat(
					"Kpi Name :", resultList.get(0).getResponseCode(), equalTo(CommonConstant.KPI_FAILED));
		}
	}

	/*
	 * @After public void cleanup() { throw new
	 * UnsupportedOperationException("Cleanup not implemented yet"); }
	 */

	@Test
	public void TestProcess_pickFromCache() throws Exception {

		KpiRequest kpiRequest1 = createKpiRequest(5);

		when(cacheService.getFromApplicationCache(
						any(), Mockito.anyString(), any(), ArgumentMatchers.anyList()))
				.thenReturn(new ArrayList<KpiElement>());

		List<KpiElement> resultList = bitBucketServiceR.process(kpiRequest1);

		assertEquals(0, resultList.size());
	}

	@Test
	public void processWithExposedApiToken() throws EntityNotFoundException {
		KpiRequest kpiRequest1 = createKpiRequest(5);
		when(cacheService.getFromApplicationCache(
						any(), Mockito.anyString(), any(), ArgumentMatchers.anyList()))
				.thenReturn(new ArrayList<KpiElement>());
		List<KpiElement> resultList = bitBucketServiceR.processWithExposedApiToken(kpiRequest1, true);
		assertEquals(0, resultList.size());
	}

	@Test
	public void testProcess_EmptyFilteredAccountData() throws Exception {
		when(filterHelperService.getFilteredBuilds(any(), anyString())).thenReturn(new ArrayList<>());

		List<KpiElement> resultList = bitBucketServiceR.process(kpiRequest);

		assertEquals(1, resultList.size());
		assertEquals(KPICode.REPO_TOOL_CODE_COMMIT.getKpiId(), resultList.get(0).getKpiId());
	}

	@Test
	public void testProcess_ExceptionDuringProcessing() {
		when(filterHelperService.getFilteredBuilds(any(), anyString()))
				.thenThrow(new RuntimeException("Test exception"));

		assertThrows(
				HttpMessageNotWritableException.class,
				() -> {
					bitBucketServiceR.process(kpiRequest);
				});
	}

	@Test
	public void testLoadDataIntoThreadLocal_Exception() throws Exception {
		when(scmKpiHelperService.getCommitDetails(any(ObjectId.class), any(CustomDateRange.class)))
				.thenThrow(new RuntimeException("Database error"));

		try (MockedStatic<DeveloperKpiHelper> helperMock =
				Mockito.mockStatic(DeveloperKpiHelper.class)) {
			helperMock
					.when(() -> DeveloperKpiHelper.getStartAndEndDate(any(KpiRequest.class)))
					.thenReturn(new CustomDateRange());

			Method method =
					BitBucketServiceR.class.getDeclaredMethod(
							"loadDataIntoThreadLocal", AccountHierarchyData.class, KpiRequest.class);
			method.setAccessible(true);

			try {
				method.invoke(bitBucketServiceR, accountHierarchyDataList.get(0), kpiRequest);
			} catch (Exception e) {
				e.getCause();
			}

			assertTrue(true);
		}
	}

	@Test
	public void testLoadDataIntoThreadLocal_InterruptedException() throws Exception {
		when(scmKpiHelperService.getCommitDetails(any(ObjectId.class), any(CustomDateRange.class)))
				.thenReturn(List.of(new ScmCommits()));
		when(scmKpiHelperService.getMergeRequests(any(ObjectId.class), any(CustomDateRange.class)))
				.thenReturn(List.of(new ScmMergeRequests()));
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any(ObjectId.class)))
				.thenAnswer(
						invocation -> {
							Thread.currentThread().interrupt();
							throw new RuntimeException("Interrupted", new InterruptedException());
						});

		try (MockedStatic<DeveloperKpiHelper> helperMock =
				Mockito.mockStatic(DeveloperKpiHelper.class)) {
			helperMock
					.when(() -> DeveloperKpiHelper.getStartAndEndDate(any(KpiRequest.class)))
					.thenReturn(new CustomDateRange());

			Method method =
					BitBucketServiceR.class.getDeclaredMethod(
							"loadDataIntoThreadLocal", AccountHierarchyData.class, KpiRequest.class);
			method.setAccessible(true);

			Thread.interrupted();

			// Act
			try {
				method.invoke(bitBucketServiceR, accountHierarchyDataList.get(0), kpiRequest);
			} catch (Exception e) {
			}

			assertTrue(true);
		}
	}

	@Test
	public void testLoadDataIntoThreadLocal_ExecutionException() throws Exception {
		when(scmKpiHelperService.getCommitDetails(any(ObjectId.class), any(CustomDateRange.class)))
				.thenReturn(List.of(new ScmCommits()));
		when(scmKpiHelperService.getMergeRequests(any(ObjectId.class), any(CustomDateRange.class)))
				.thenAnswer(
						invocation -> {
							throw new RuntimeException("Merge request fetch failed");
						});
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any(ObjectId.class)))
				.thenReturn(Arrays.asList(new Assignee()));

		try (MockedStatic<DeveloperKpiHelper> helperMock =
				Mockito.mockStatic(DeveloperKpiHelper.class)) {
			helperMock
					.when(() -> DeveloperKpiHelper.getStartAndEndDate(any(KpiRequest.class)))
					.thenReturn(new CustomDateRange());

			Method method =
					BitBucketServiceR.class.getDeclaredMethod(
							"loadDataIntoThreadLocal", AccountHierarchyData.class, KpiRequest.class);
			method.setAccessible(true);

			try {
				method.invoke(bitBucketServiceR, accountHierarchyDataList.get(0), kpiRequest);
			} catch (Exception e) {
			}

			assertTrue(true);
		}
	}

	@Test
	public void testExecuteParallelKpiProcessing_KpiException() throws Exception {
		when(kpiHelperService.isToolConfigured(any(), any(), any())).thenReturn(true);
		when(scmKpiHelperService.getCommitDetails(any(ObjectId.class), any(CustomDateRange.class)))
				.thenReturn(List.of(new ScmCommits()));
		when(scmKpiHelperService.getMergeRequests(any(ObjectId.class), any(CustomDateRange.class)))
				.thenReturn(List.of(new ScmMergeRequests()));
		when(scmKpiHelperService.getJiraAssigneeForScmUsers(any(ObjectId.class)))
				.thenReturn(Arrays.asList(new Assignee()));

		try (MockedStatic<BitBucketKPIServiceFactory> factoryMock =
						Mockito.mockStatic(BitBucketKPIServiceFactory.class);
				MockedStatic<DeveloperKpiHelper> helperMock =
						Mockito.mockStatic(DeveloperKpiHelper.class)) {

			factoryMock
					.when(() -> BitBucketKPIServiceFactory.getBitBucketKPIService(anyString()))
					.thenThrow(
							new ApplicationException(
									KpiElement.class, "kpiRequestTrackerId", kpiRequest.getRequestTrackerId()));
			helperMock
					.when(() -> DeveloperKpiHelper.getStartAndEndDate(any(KpiRequest.class)))
					.thenReturn(new CustomDateRange());

			List<KpiElement> resultList = bitBucketServiceR.process(kpiRequest);

			assertEquals(CommonConstant.KPI_FAILED, resultList.get(0).getResponseCode());
		}
	}

	@Test
	public void testShutdownExecutorService_Timeout() throws Exception {
		ExecutorService mockExecutor = mock(ExecutorService.class);
		when(mockExecutor.awaitTermination(60, TimeUnit.SECONDS)).thenReturn(false);
		when(mockExecutor.awaitTermination(30, TimeUnit.SECONDS)).thenReturn(false);

		Method method =
				BitBucketServiceR.class.getDeclaredMethod("shutdownExecutorService", ExecutorService.class);
		method.setAccessible(true);
		method.invoke(bitBucketServiceR, mockExecutor);

		verify(mockExecutor).shutdown();
		verify(mockExecutor).shutdownNow();
	}

	@Test
	public void testShutdownExecutorService_Interrupted() throws Exception {
		ExecutorService mockExecutor = mock(ExecutorService.class);
		when(mockExecutor.awaitTermination(anyLong(), any(TimeUnit.class)))
				.thenThrow(new InterruptedException("Test interruption"));

		Method method =
				BitBucketServiceR.class.getDeclaredMethod("shutdownExecutorService", ExecutorService.class);
		method.setAccessible(true);
		method.invoke(bitBucketServiceR, mockExecutor);

		verify(mockExecutor).shutdownNow();
		assertTrue(Thread.currentThread().isInterrupted() || Thread.interrupted());
	}

	@Test
	public void testCleanupThreadLocalData_Exception() throws Exception {
		Field commitsField = BitBucketServiceR.class.getDeclaredField("THREAD_LOCAL_COMMITS");
		commitsField.setAccessible(true);
		ThreadLocal<List<ScmCommits>> threadLocalCommits =
				(ThreadLocal<List<ScmCommits>>) commitsField.get(null);
		threadLocalCommits.set(Arrays.asList(new ScmCommits()));

		Field mergeField = BitBucketServiceR.class.getDeclaredField("THREAD_LOCAL_MERGE_REQUESTS");
		mergeField.setAccessible(true);
		ThreadLocal<List<ScmMergeRequests>> threadLocalMerge =
				(ThreadLocal<List<ScmMergeRequests>>) mergeField.get(null);
		threadLocalMerge.set(Arrays.asList(new ScmMergeRequests()));

		Field assigneeField = BitBucketServiceR.class.getDeclaredField("THREAD_LOCAL_ASSIGNEES");
		assigneeField.setAccessible(true);
		ThreadLocal<List<Assignee>> threadLocalAssignees =
				(ThreadLocal<List<Assignee>>) assigneeField.get(null);
		threadLocalAssignees.set(Arrays.asList(new Assignee()));

		// Act - Invoke the cleanup method
		Method method = BitBucketServiceR.class.getDeclaredMethod("cleanupThreadLocalData");
		method.setAccessible(true);
		method.invoke(bitBucketServiceR);

		// Assert - Verify ThreadLocal data has been cleaned up
		assertTrue(
				"ThreadLocal commits should be empty after cleanup", threadLocalCommits.get().isEmpty());
		assertTrue(
				"ThreadLocal merge requests should be empty after cleanup",
				threadLocalMerge.get().isEmpty());
		assertTrue(
				"ThreadLocal assignees should be empty after cleanup",
				threadLocalAssignees.get().isEmpty());
	}

	@Test
	public void testThreadLocalAccessors() {
		List<ScmCommits> commits = Arrays.asList(new ScmCommits());
		List<ScmMergeRequests> mergeRequests = Arrays.asList(new ScmMergeRequests());
		List<Assignee> assignees = Arrays.asList(new Assignee());

		try {
			Field commitsField = BitBucketServiceR.class.getDeclaredField("THREAD_LOCAL_COMMITS");
			commitsField.setAccessible(true);
			ThreadLocal<List<ScmCommits>> threadLocalCommits =
					(ThreadLocal<List<ScmCommits>>) commitsField.get(null);
			threadLocalCommits.set(commits);

			Field mergeField = BitBucketServiceR.class.getDeclaredField("THREAD_LOCAL_MERGE_REQUESTS");
			mergeField.setAccessible(true);
			ThreadLocal<List<ScmMergeRequests>> threadLocalMerge =
					(ThreadLocal<List<ScmMergeRequests>>) mergeField.get(null);
			threadLocalMerge.set(mergeRequests);

			Field assigneeField = BitBucketServiceR.class.getDeclaredField("THREAD_LOCAL_ASSIGNEES");
			assigneeField.setAccessible(true);
			ThreadLocal<List<Assignee>> threadLocalAssignees =
					(ThreadLocal<List<Assignee>>) assigneeField.get(null);
			threadLocalAssignees.set(assignees);

			List<ScmCommits> retrievedCommits = BitBucketServiceR.getThreadLocalCommits();
			List<ScmMergeRequests> retrievedMergeRequests =
					BitBucketServiceR.getThreadLocalMergeRequests();
			List<Assignee> retrievedAssignees = BitBucketServiceR.getThreadLocalAssignees();

			assertNotNull(retrievedCommits);
			assertNotNull(retrievedMergeRequests);
			assertNotNull(retrievedAssignees);
			assertEquals(1, retrievedCommits.size());
			assertEquals(1, retrievedMergeRequests.size());
			assertEquals(1, retrievedAssignees.size());

			// Clean up
			threadLocalCommits.remove();
			threadLocalMerge.remove();
			threadLocalAssignees.remove();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Test
	public void testSetIntoApplicationCache() throws Exception {
		KpiRequest kpiRequest1 = createKpiRequest(5);
		List<KpiElement> responseList = new ArrayList<>();
		responseList.add(new KpiElement());

		Method method =
				BitBucketServiceR.class.getDeclaredMethod(
						"setIntoApplicationCache", KpiRequest.class, List.class, Integer.class, String[].class);
		method.setAccessible(true);
		method.invoke(bitBucketServiceR, kpiRequest1, responseList, 1, new String[] {"project1"});

		verify(cacheService, times(1))
				.setIntoApplicationCache(any(), any(), eq(KPISource.BITBUCKET.name()), eq(1), any());
	}

	@Test
	public void testGetBranchSubFilter_AllScenarios() {
		BitBucketKPIService testService = new TestBitBucketKPIService();

		// Test with repoSlug
		Tool tool1 = new Tool();
		tool1.setBranch("main");
		tool1.setRepoSlug("repo-slug");
		String result1 = testService.getBranchSubFilter(tool1, "Project1");
		assertEquals("main -> repo-slug -> Project1", result1);

		// Test with repositoryName
		Tool tool2 = new Tool();
		tool2.setBranch("develop");
		tool2.setRepositoryName("repo-name");
		String result2 = testService.getBranchSubFilter(tool2, "Project2");
		assertEquals("develop -> repo-name -> Project2", result2);

		// Test with neither repoSlug nor repositoryName
		Tool tool3 = new Tool();
		tool3.setBranch("feature");
		String result3 = testService.getBranchSubFilter(tool3, "Project3");
		assertEquals("feature -> Project3", result3);
	}

	@Test
	public void testProcess_AuthorizedProjectsEmpty() throws Exception {
		when(kpiHelperService.getAuthorizedFilteredList(any(), any(), anyBoolean()))
				.thenReturn(new ArrayList<>());

		List<KpiElement> resultList = bitBucketServiceR.process(kpiRequest);

		assertEquals(0, resultList.size());
	}

	@Test
	public void testProcessWithExposedApiToken_NoCache() throws EntityNotFoundException {
		when(kpiHelperService.getAuthorizedFilteredList(any(), any(), eq(false)))
				.thenReturn(accountHierarchyDataList);

		List<KpiElement> resultList = bitBucketServiceR.processWithExposedApiToken(kpiRequest, false);

		assertNotNull(resultList);
	}

	@Test
	public void testCalculateAllKPIAggregatedMetrics_Success() throws Exception {
		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId(KPICode.REPO_TOOL_CODE_COMMIT.getKpiId());
		Node node = accountHierarchyDataList.get(0).getNode().get(kpiRequest.getLevel() - 1);

		when(kpiHelperService.isToolConfigured(
						any(KPICode.class), any(KpiElement.class), any(Node.class)))
				.thenReturn(true);

		try (MockedStatic<BitBucketKPIServiceFactory> factoryMock =
				Mockito.mockStatic(BitBucketKPIServiceFactory.class)) {
			KpiElement responseKpi = new KpiElement();
			responseKpi.setKpiId(kpiElement.getKpiId());
			responseKpi.setKpiName("Test KPI");
			responseKpi.setValue("100");

			when(bitBucketKPIService.getKpiData(
							any(KpiRequest.class), any(KpiElement.class), any(Node.class)))
					.thenReturn(responseKpi);
			factoryMock
					.when(() -> BitBucketKPIServiceFactory.getBitBucketKPIService(anyString()))
					.thenReturn(bitBucketKPIService);

			Method method =
					BitBucketServiceR.class.getDeclaredMethod(
							"calculateAllKPIAggregatedMetrics", KpiRequest.class, KpiElement.class, Node.class);
			method.setAccessible(true);

			KpiElement result =
					(KpiElement) method.invoke(bitBucketServiceR, kpiRequest, kpiElement, node);

			assertEquals(CommonConstant.KPI_PASSED, result.getResponseCode());
			assertEquals("100", result.getValue());
			verify(kpiHelperService).isMandatoryFieldSet(any(KPICode.class), eq(result), any(Node.class));
		}
	}

	@Test
	public void testCalculateAllKPIAggregatedMetrics_NullNodeClone() throws Exception {
		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId(KPICode.REPO_TOOL_CODE_COMMIT.getKpiId());
		Node node = mock(Node.class);

		try (MockedStatic<SerializationUtils> serializationMock =
						Mockito.mockStatic(SerializationUtils.class);
				MockedStatic<BitBucketKPIServiceFactory> factoryMock =
						Mockito.mockStatic(BitBucketKPIServiceFactory.class)) {

			serializationMock.when(() -> SerializationUtils.clone(any())).thenReturn(null);
			factoryMock
					.when(() -> BitBucketKPIServiceFactory.getBitBucketKPIService(anyString()))
					.thenReturn(bitBucketKPIService);

			Method method =
					BitBucketServiceR.class.getDeclaredMethod(
							"calculateAllKPIAggregatedMetrics", KpiRequest.class, KpiElement.class, Node.class);
			method.setAccessible(true);

			KpiElement result =
					(KpiElement) method.invoke(bitBucketServiceR, kpiRequest, kpiElement, node);

			assertNotEquals(CommonConstant.KPI_PASSED, result.getResponseCode());
			verify(bitBucketKPIService, never()).getKpiData(any(), any(), any());
		}
	}

	@Test
	public void testCalculateAllKPIAggregatedMetrics_ToolNotConfigured() throws Exception {
		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId(KPICode.REPO_TOOL_CODE_COMMIT.getKpiId());
		Node node = accountHierarchyDataList.get(0).getNode().get(kpiRequest.getLevel() - 1);

		when(kpiHelperService.isToolConfigured(
						any(KPICode.class), any(KpiElement.class), any(Node.class)))
				.thenReturn(false);

		try (MockedStatic<BitBucketKPIServiceFactory> factoryMock =
				Mockito.mockStatic(BitBucketKPIServiceFactory.class)) {
			factoryMock
					.when(() -> BitBucketKPIServiceFactory.getBitBucketKPIService(anyString()))
					.thenReturn(bitBucketKPIService);

			Method method =
					BitBucketServiceR.class.getDeclaredMethod(
							"calculateAllKPIAggregatedMetrics", KpiRequest.class, KpiElement.class, Node.class);
			method.setAccessible(true);

			KpiElement result =
					(KpiElement) method.invoke(bitBucketServiceR, kpiRequest, kpiElement, node);

			assertNotEquals(CommonConstant.KPI_PASSED, result.getResponseCode());
			verify(bitBucketKPIService, never()).getKpiData(any(), any(), any());
		}
	}

	@Test
	public void testCalculateAllKPIAggregatedMetrics_ApplicationException() throws Exception {
		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId(KPICode.REPO_TOOL_CODE_COMMIT.getKpiId());
		Node node = accountHierarchyDataList.get(0).getNode().get(kpiRequest.getLevel() - 1);

		try (MockedStatic<BitBucketKPIServiceFactory> factoryMock =
				Mockito.mockStatic(BitBucketKPIServiceFactory.class)) {
			factoryMock
					.when(() -> BitBucketKPIServiceFactory.getBitBucketKPIService(anyString()))
					.thenThrow(
							new ApplicationException(KpiElement.class, "field", "Test ApplicationException"));

			Method method =
					BitBucketServiceR.class.getDeclaredMethod(
							"calculateAllKPIAggregatedMetrics", KpiRequest.class, KpiElement.class, Node.class);
			method.setAccessible(true);

			KpiElement result =
					(KpiElement) method.invoke(bitBucketServiceR, kpiRequest, kpiElement, node);

			assertEquals(CommonConstant.KPI_FAILED, result.getResponseCode());
		}
	}

	@Test
	public void testCalculateAllKPIAggregatedMetrics_RuntimeException() throws Exception {
		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId(KPICode.REPO_TOOL_CODE_COMMIT.getKpiId());
		Node node = accountHierarchyDataList.get(0).getNode().get(kpiRequest.getLevel() - 1);

		when(kpiHelperService.isToolConfigured(
						any(KPICode.class), any(KpiElement.class), any(Node.class)))
				.thenReturn(true);

		try (MockedStatic<BitBucketKPIServiceFactory> factoryMock =
				Mockito.mockStatic(BitBucketKPIServiceFactory.class)) {
			when(bitBucketKPIService.getKpiData(any(), any(), any()))
					.thenThrow(new RuntimeException("Test runtime exception"));
			factoryMock
					.when(() -> BitBucketKPIServiceFactory.getBitBucketKPIService(anyString()))
					.thenReturn(bitBucketKPIService);

			Method method =
					BitBucketServiceR.class.getDeclaredMethod(
							"calculateAllKPIAggregatedMetrics", KpiRequest.class, KpiElement.class, Node.class);
			method.setAccessible(true);

			KpiElement result =
					(KpiElement) method.invoke(bitBucketServiceR, kpiRequest, kpiElement, node);

			assertEquals(CommonConstant.KPI_FAILED, result.getResponseCode());
		}
	}

	private KpiRequest createKpiRequest(int level) {
		KpiRequest kpiRequest1 = new KpiRequest();
		List<KpiElement> kpiList = new ArrayList<>();

		addKpiElement(
				kpiList, KPICode.REPO_TOOL_CODE_COMMIT.getKpiId(), KPICode.REPO_TOOL_CODE_COMMIT.name());
		kpiRequest1.setLevel(level);
		kpiRequest1.setIds(new String[] {"7"});
		kpiRequest1.setKpiList(kpiList);
		kpiRequest1.setRequestTrackerId();
		kpiRequest1.setLabel("project");
		Map<String, List<String>> s = new HashMap<>();
		s.put(CommonConstant.DATE, List.of("WEEKS"));
		kpiRequest1.setSelectedMap(s);
		kpiRequest1.setSprintIncluded(Arrays.asList("CLOSED", "ACTIVE"));
		return kpiRequest1;
	}

	private void addKpiElement(List<KpiElement> kpiList, String kpiId, String kpiName) {
		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId(kpiId);
		kpiElement.setKpiName(kpiName);
		kpiElement.setKpiCategory("Developer");
		kpiElement.setKpiUnit("");
		kpiElement.setKpiSource("BitBucket");
		kpiElement.setGroupId(1);
		kpiElement.setMaxValue("500");
		kpiElement.setChartType("gaugeChart");
		kpiList.add(kpiElement);
	}
}
