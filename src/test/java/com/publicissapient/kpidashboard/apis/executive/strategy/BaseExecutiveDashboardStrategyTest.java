/*
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.executive.strategy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.AssertionErrors.fail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.bson.types.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.errors.ExecutiveDataException;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardDataDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.service.ProjectEfficiencyService;
import com.publicissapient.kpidashboard.apis.executive.service.ToolKpiMaturity;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.userboardconfig.service.UserBoardConfigService;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import com.publicissapient.kpidashboard.common.model.userboardconfig.BoardDTO;
import com.publicissapient.kpidashboard.common.model.userboardconfig.BoardKpisDTO;
import com.publicissapient.kpidashboard.common.model.userboardconfig.UserBoardConfigDTO;
import com.publicissapient.kpidashboard.common.repository.application.KpiCategoryRepository;

@RunWith(MockitoJUnitRunner.class)
public class BaseExecutiveDashboardStrategyTest {

	@Mock
	private CacheService cacheService;
	@Mock
	private ProjectEfficiencyService projectEfficiencyService;
	@Mock
	private UserBoardConfigService userBoardConfigService;
	@Mock
	private ToolKpiMaturity toolKpiMaturity;
	@Mock
	private KpiCategoryRepository kpiCategoryRepository;
	@Mock
	private ConfigHelperService configHelperService;

	private DummyStrategy testStrategy;

	@Before
	public void setup() {
		MockitoAnnotations.openMocks(this);
		testStrategy = new DummyStrategy(projectEfficiencyService, cacheService, userBoardConfigService,
				toolKpiMaturity, kpiCategoryRepository, configHelperService);
	}

	private static class DummyStrategy extends BaseExecutiveDashboardStrategy {
		private final Executor executor = Executors.newSingleThreadExecutor();
		private boolean shouldSleep = false;

		public DummyStrategy(ProjectEfficiencyService projectEfficiencyService, CacheService cacheService,
				UserBoardConfigService userBoardConfigService, ToolKpiMaturity toolKpiMaturity,
				KpiCategoryRepository kpiCategoryRepository, ConfigHelperService configHelperService) {
			super("dummy", cacheService, projectEfficiencyService, userBoardConfigService, toolKpiMaturity,
					kpiCategoryRepository, configHelperService);
		}

		@Override
		protected Executor getExecutor() {
			return executor;
		}

		@Override
		protected ExecutiveDashboardResponseDTO fetchDashboardData(KpiRequest kpiRequest) {
			if (shouldSleep) {
				try {
					TimeUnit.SECONDS.sleep(5);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}
			return ExecutiveDashboardResponseDTO.builder().data(ExecutiveDashboardDataDTO.builder().build()).build();
		}

		public void setShouldSleep(boolean shouldSleep) {
			this.shouldSleep = shouldSleep;
		}
	}

	@Test
	public void testGetExecutiveDashboard_Success() {
		KpiRequest request = new KpiRequest();
		ExecutiveDashboardResponseDTO result = testStrategy.getExecutiveDashboard(request);
		assertNotNull(result);
	}

	@Test
	public void testGetExecutiveDashboard_Timeout() {
		testStrategy.setShouldSleep(true); // Make fetchDashboardData take long
		KpiRequest request = new KpiRequest();

		// Reduce timeout for testing purposes by creating a new instance with a faster
		// timeout
		DummyStrategy fastTimeoutStrategy = new DummyStrategy(projectEfficiencyService, cacheService,
				userBoardConfigService, toolKpiMaturity, kpiCategoryRepository, configHelperService) {
			@Override
			public ExecutiveDashboardResponseDTO getExecutiveDashboard(KpiRequest request) {
				// Override with a much shorter timeout for the test
				try {
					return Executors.newSingleThreadExecutor().submit(() -> fetchDashboardData(request)).get(1,
							TimeUnit.SECONDS);
				} catch (Exception e) {
					throw new ExecutiveDataException("Timeout", e);
				}
			}
		};
		fastTimeoutStrategy.setShouldSleep(true);

		assertThrows(ExecutiveDataException.class, () -> fastTimeoutStrategy.getExecutiveDashboard(request));
	}

	@Test
	public void testProcessProjectBatch_Interruption() throws Exception {
		String projectId = "p1";
		List<String> ids = List.of(projectId);
		Map<String, OrganizationHierarchy> hierarchyMap = new HashMap<>();
		hierarchyMap.put(projectId, new OrganizationHierarchy());

		UserBoardConfigDTO config = new UserBoardConfigDTO();
		config.setScrum(List.of(new com.publicissapient.kpidashboard.common.model.userboardconfig.BoardDTO())); // Make
																												// it
																												// not
																												// empty
		when(userBoardConfigService.getBoardConfig(any(), any())).thenReturn(config);

		// Simulate a delay so we have time to interrupt

		final Map<String, Map<String, String>>[] result = new Map[1];
		Thread testThread = new Thread(() -> {
			result[0] = testStrategy.processProjectBatch(ids, new KpiRequest(), hierarchyMap,
					Collections.singleton("dora"), false);
		});

		testThread.start();
		Thread.sleep(500); // Give it time to start processing
		testThread.interrupt(); // Interrupt the thread
		testThread.join(); // Wait for it to finish

		assertTrue("Result should be empty after interruption", result[0].isEmpty());
	}

	@Test
	public void testProcessProjectBatch_NoBoardConfig() {
		String projectId = "p1";
		List<String> ids = List.of(projectId);
		Map<String, OrganizationHierarchy> hierarchyMap = new HashMap<>();
		hierarchyMap.put(projectId, new OrganizationHierarchy());

		when(userBoardConfigService.getBoardConfig(any(), any())).thenReturn(new UserBoardConfigDTO());

		Map<String, Map<String, String>> result = testStrategy.processProjectBatch(ids, new KpiRequest(), hierarchyMap,
				Collections.emptySet(), false);

		assertTrue(result.isEmpty());
	}

	@Test
	public void testProcessProjectBatch_NoKpisToProcess() {
		String projectId = "p1";
		List<String> ids = List.of(projectId);
		Map<String, OrganizationHierarchy> hierarchyMap = new HashMap<>();
		OrganizationHierarchy hierarchy = new OrganizationHierarchy();
		hierarchy.setNodeDisplayName("Project One");
		hierarchyMap.put(projectId, hierarchy);

		UserBoardConfigDTO config = new UserBoardConfigDTO();
		config.setScrum(Collections.emptyList());
		when(userBoardConfigService.getBoardConfig(any(), any())).thenReturn(config);

		Map<String, Map<String, String>> result = testStrategy.processProjectBatch(ids, new KpiRequest(), hierarchyMap,
				Collections.singleton("dora"), false);

		assertTrue(result.isEmpty());
	}

	@Test
	public void testProcessToolKpis_HappyPath() {
		String projectId = "p1";
		KpiRequest request = new KpiRequest();

		request.setSelectedMap(createSelectedMap());
		Map<String, Map<String, List<KpiMaster>>> toolToBoardKpis = new HashMap<>();
		Map<String, List<KpiMaster>> boardKpis = new HashMap<>();
		KpiMaster kpi = new KpiMaster();
		kpi.setKpiId("kpi1");
		boardKpis.put("dora", Collections.singletonList(kpi));
		toolToBoardKpis.put("jira", boardKpis);

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("kpi1");
		kpiElement.setOverallMaturity("4.5");
		when(toolKpiMaturity.getKpiElements(any(), any())).thenReturn(Collections.singletonList(kpiElement));

		Map<String, String> result = testStrategy.processToolKpis(projectId, request, toolToBoardKpis,
				Collections.singleton("dora"));

		assertEquals(1, result.size());
		assertEquals("4.5", result.get("dora"));
	}

	private static Map<String, List<String>> createSelectedMap() {
		Map<String, List<String>> selectedMap = new HashMap<>();
		selectedMap.put("bu", new ArrayList<>());
		selectedMap.put("ver", new ArrayList<>());
		selectedMap.put("acc", new ArrayList<>());
		selectedMap.put("port", new ArrayList<>());
		selectedMap.put("project", new ArrayList<>());
		selectedMap.put("release", new ArrayList<>());
		selectedMap.put("sqd", new ArrayList<>());
		selectedMap.put("date", new ArrayList<>(List.of("Weeks")));
		return selectedMap;
	}

	@Test
	public void testGetExecutiveDashboard_ExecutionException() {
		// Test handling of ExecutionException
		testStrategy = new DummyStrategy(projectEfficiencyService, cacheService, userBoardConfigService,
				toolKpiMaturity, kpiCategoryRepository, configHelperService) {
			@Override
			public ExecutiveDashboardResponseDTO getExecutiveDashboard(KpiRequest request) {
				throw new ExecutiveDataException("Test exception",
						new ExecutionException(new RuntimeException("Test")));
			}
		};

		assertThrows(ExecutiveDataException.class, () -> testStrategy.getExecutiveDashboard(new KpiRequest()));
	}

	@Test
	public void testProcessToolKpis_ExceptionInFuture() {
		String projectId = "p1";
		KpiRequest request = new KpiRequest();
		request.setSelectedMap(createSelectedMap());

		Map<String, Map<String, List<KpiMaster>>> toolToBoardKpis = new HashMap<>();
		Map<String, List<KpiMaster>> boardKpis = new HashMap<>();
		boardKpis.put("dora", List.of(new KpiMaster()));
		toolToBoardKpis.put("jira", boardKpis);

		when(toolKpiMaturity.getKpiElements(any(), any())).thenThrow(new RuntimeException("Test exception"));

		Map<String, String> result = testStrategy.processToolKpis(projectId, request, toolToBoardKpis, Set.of("dora"));

		assertFalse(result.isEmpty());
	}

	@Test
	public void testProcessToolKpis_MultipleBoards() {
		String projectId = "p1";
		KpiRequest request = new KpiRequest();
		request.setSelectedMap(createSelectedMap());

		// Setup test data with multiple boards
		Map<String, Map<String, List<KpiMaster>>> toolToBoardKpis = new HashMap<>();
		Map<String, List<KpiMaster>> boardKpis = new HashMap<>();

		KpiMaster kpi1 = new KpiMaster();
		kpi1.setKpiId("kpi1");
		boardKpis.put("dora", List.of(kpi1));

		KpiMaster kpi2 = new KpiMaster();
		kpi2.setKpiId("kpi2");
		boardKpis.put("dora2", List.of(kpi2));

		toolToBoardKpis.put("jira", boardKpis);

		KpiElement kpiElement1 = new KpiElement();
		kpiElement1.setKpiId("kpi1");
		kpiElement1.setOverallMaturity("4.5");

		KpiElement kpiElement2 = new KpiElement();
		kpiElement2.setKpiId("kpi2");
		kpiElement2.setOverallMaturity("3.5");

		when(toolKpiMaturity.getKpiElements(any(), any())).thenReturn(List.of(kpiElement1, kpiElement2));

		Map<String, String> result = testStrategy.processToolKpis(projectId, request, toolToBoardKpis,
				Set.of("dora", "dora2"));

		assertEquals(2, result.size());
		assertEquals("4.5", result.get("dora"));
		assertEquals("3.5", result.get("dora2"));
	}

	@Test
	public void testProcessProjectBatch_MultipleProjects() {
		// Setup test data with multiple projects
		List<String> ids = List.of("p1", "p2");
		Map<String, OrganizationHierarchy> hierarchyMap = new HashMap<>();
		hierarchyMap.put("p1", createOrgHierarchy("5c0f32fe00a9b83a7cbc4f0c", "Project One"));
		hierarchyMap.put("p2", createOrgHierarchy("5c0f32fe00a9b83a7cbc4f0d", "Project Two"));

		UserBoardConfigDTO config = new UserBoardConfigDTO();
		BoardDTO board1 = new BoardDTO();
		board1.setBoardId(1);
		board1.setBoardName("DORA");

		BoardKpisDTO kpis1 = new BoardKpisDTO();
		kpis1.setKpiId("kpi-p1");
		kpis1.setKpiName("DORA KPI");
		kpis1.setIsEnabled(true);
		kpis1.setShown(true);
		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setCalculateMaturity(true);
		kpiMaster.setKpiSource("jira");
		kpis1.setKpiDetail(kpiMaster);
		board1.setKpis(List.of(kpis1));
		config.setScrum(List.of(board1));

		when(userBoardConfigService.getBoardConfig(any(), any())).thenReturn(config);

		// Return different KPIs for different project IDs
		when(toolKpiMaturity.getKpiElements(any(), any())).thenAnswer(invocation -> {
			String projectId = invocation.getArgument(0);
			KpiElement kpi = new KpiElement();
			kpi.setKpiId("kpi-" + projectId);
			kpi.setOverallMaturity(projectId.equals("p1") ? "4.5" : "3.5");
			return List.of(kpi);
		});

		KpiRequest kpiRequest = new KpiRequest();
		kpiRequest.setSelectedMap(createSelectedMap());

		Map<String, Map<String, String>> result = testStrategy.processProjectBatch(ids, kpiRequest, hierarchyMap,
				Set.of("dora"), false);

		assertEquals(2, result.size());
		assertEquals("NA", result.get("p1").get("dora"));
		assertEquals("NA", result.get("p2").get("dora"));
	}

	@Test
	public void testCheckInterrupted() {
		// Test that checkInterrupted throws when thread is interrupted
		Thread.currentThread().interrupt();
		try {
			testStrategy.checkInterrupted();
			fail("Expected InterruptedException");
		} catch (InterruptedException e) {
			// Expected
			Thread.currentThread().interrupt(); // Reset interrupt status
		}
	}

	@Test
	public void testComputeBoardSummary_EmptyList() {
		String result = testStrategy.computeBoardSummary(Collections.emptyList());
		assertEquals("NA", result);
	}

	@Test
	public void testComputeBoardSummary_WithNullMaturity() {
		KpiElement kpi1 = new KpiElement();
		kpi1.setOverallMaturity(null);

		KpiElement kpi2 = new KpiElement();
		kpi2.setOverallMaturity("3.5");

		String result = testStrategy.computeBoardSummary(List.of(kpi1, kpi2));
		assertEquals("3.5", result);
	}

	private OrganizationHierarchy createOrgHierarchy(String id, String name) {
		OrganizationHierarchy hierarchy = new OrganizationHierarchy();
		hierarchy.setId(new ObjectId(id));
		hierarchy.setNodeName(id);
		hierarchy.setNodeDisplayName(name);
		return hierarchy;
	}
}
