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
package com.publicissapient.kpidashboard.apis.executive.strategy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardDataDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.service.ProjectEfficiencyService;
import com.publicissapient.kpidashboard.apis.executive.service.ScrumKpiMaturity;
import com.publicissapient.kpidashboard.apis.model.KpiElement;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.apis.userboardconfig.service.UserBoardConfigService;
import com.publicissapient.kpidashboard.common.model.application.KpiCategory;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;
import com.publicissapient.kpidashboard.common.model.application.OrganizationHierarchy;
import com.publicissapient.kpidashboard.common.model.userboardconfig.BoardDTO;
import com.publicissapient.kpidashboard.common.model.userboardconfig.BoardKpisDTO;
import com.publicissapient.kpidashboard.common.model.userboardconfig.UserBoardConfigDTO;
import com.publicissapient.kpidashboard.common.repository.application.KpiCategoryRepository;

/**
 * @author pkum34
 */
@RunWith(MockitoJUnitRunner.class)
public class BaseExecutiveDashboardStrategyTest {

	@Mock
	private CacheService cacheService;
	@Mock
	private ProjectEfficiencyService projectEfficiencyService;
	@Mock
	private UserBoardConfigService userBoardConfigService;
	@Mock
	private ScrumKpiMaturity toolKpiMaturity;
	@Mock
	private KpiCategoryRepository kpiCategoryRepository;
	@Mock
	private ConfigHelperService configHelperService;

	private DummyStrategy strategy;

	@Before
	public void setup() {
		MockitoAnnotations.openMocks(this);

		strategy = new DummyStrategy(projectEfficiencyService, cacheService, userBoardConfigService, toolKpiMaturity,
				kpiCategoryRepository, configHelperService);

	}

	/** Dummy concrete class for testing */
	private static class DummyStrategy extends BaseExecutiveDashboardStrategy {
		public DummyStrategy(ProjectEfficiencyService projectEfficiencyService, CacheService cacheService,
				UserBoardConfigService userBoardConfigService, ScrumKpiMaturity scrumKpiMaturity,
				KpiCategoryRepository kpiCategoryRepository, ConfigHelperService configHelperService) {
			super("dummy", cacheService, projectEfficiencyService, userBoardConfigService, scrumKpiMaturity,
					kpiCategoryRepository, configHelperService);
		}

		@Override
		protected ExecutiveDashboardResponseDTO fetchDashboardData(KpiRequest kpiRequest) {
			// For testing, return a dummy response
			return ExecutiveDashboardResponseDTO.builder().data(null).build();
		}
	}

	@Test
	public void testGetStrategyType() {
		assertEquals("dummy", strategy.getStrategyType());
	}

	@Test
	public void testGetExecutiveDashboardDelegatesToFetch() {
		KpiRequest request = new KpiRequest();
		ExecutiveDashboardResponseDTO result = strategy.getExecutiveDashboard(request);
		assertNotNull(result);
		assertEquals(null, result.getData());
	}

	@Test
	public void testGetRequiredNodeIdsReturnsIds() {
		Map<String, List<String>> map = new HashMap<>();
		map.put("project", List.of("p1", "p2"));
		KpiRequest req = new KpiRequest();
		req.setLabel("project");
		req.setSelectedMap(map);

		List<String> ids = strategy.getRequiredNodeIds(req);
		assertEquals(2, ids.size());
		assertTrue(ids.contains("p1"));
	}

	@Test
	public void testGetDefaultResponse() {
		ExecutiveDashboardResponseDTO resp = strategy.getDefaultResponse();
		assertNotNull(resp);
		assertNotNull(resp.getData());
		assertTrue(((ExecutiveDashboardDataDTO) resp.getData()).getMatrix().getRows().isEmpty());
	}

	@Test
	public void testGetBoardsReturnsCategoryNamesPlusDora() {
		KpiCategory cat1 = new KpiCategory();
		cat1.setCategoryName("Quality");

		when(kpiCategoryRepository.findAll()).thenReturn(List.of(cat1));

		Set<String> boards = strategy.getBoards();
		assertTrue(boards.contains("quality"));
		assertTrue(boards.contains("dora"));
	}

	@Test
	public void testComputeBoardSummaryNA() throws Exception {
		String result = invokeComputeBoardSummary(Collections.emptyList());
		assertEquals("NA", result);
	}

	@Test
	public void testComputeBoardSummaryWithValues() throws Exception {
		KpiElement e1 = new KpiElement();
		e1.setOverallMaturity("2.0");
		KpiElement e2 = new KpiElement();
		e2.setOverallMaturity("4.0");

		String result = invokeComputeBoardSummary(List.of(e1, e2));
		assertEquals("3.0", result); // average
	}

	@Test
	public void testProcessProjectBatch_HappyPath() {
		String projectId = "p1";
		List<String> ids = List.of(projectId);
		Map<String, OrganizationHierarchy> hierarchyMap = new HashMap<>();
		OrganizationHierarchy hierarchy = new OrganizationHierarchy();
		hierarchy.setNodeDisplayName("Project One");
		hierarchyMap.put(projectId, hierarchy);

		// fake config with one board + one KPI

		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId("k1");
		kpiMaster.setCalculateMaturity(true);
		kpiMaster.setKpiSource("jira");

		BoardKpisDTO boardKpis = new BoardKpisDTO();
		boardKpis.setKpiId("k1");
		boardKpis.setKpiName("KPI One");
		boardKpis.setIsEnabled(true);
		boardKpis.setShown(true);
		boardKpis.setKpiDetail(kpiMaster);

		BoardDTO board = new BoardDTO();
		board.setBoardName("speed");
		board.setKpis(List.of(boardKpis));

		UserBoardConfigDTO config = new UserBoardConfigDTO();
		config.setScrum(List.of(board));

		when(userBoardConfigService.getBoardConfig(any(), any())).thenReturn(config);

		Set<String> boards = Set.of("speed", "quality", "value", "dora");
		KpiRequest request = new KpiRequest();
		request.setLabel("project");
		request.setSelectedMap(new ConcurrentHashMap<>());

		KpiElement kpiElement = new KpiElement();
		kpiElement.setKpiId("k1");
		kpiElement.setOverallMaturity("5.0");

		List<KpiElement> kpiElements = new ArrayList<>();
		kpiElements.add(kpiElement);
		when(toolKpiMaturity.getKpiElements(any(KpiRequest.class), anyMap())).thenReturn(kpiElements);
		ExecutorService executor = Executors.newFixedThreadPool(1);
		// Act
		Map<String, Map<String, String>> result = strategy.processProjectBatch(ids, request, hierarchyMap, boards,
				false, executor);

		// Assert
		assertNotNull(result);
		assertTrue(result.containsKey(projectId));
		assertEquals("5.0", result.get(projectId).get("speed"));
	}

	/** helper: call private computeBoardSummary */
	private String invokeComputeBoardSummary(List<KpiElement> elements) throws Exception {
		var method = BaseExecutiveDashboardStrategy.class.getDeclaredMethod("computeBoardSummary", List.class);
		method.setAccessible(true);
		return (String) method.invoke(strategy, elements);
	}
}
