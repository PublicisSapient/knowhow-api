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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.publicissapient.kpidashboard.apis.errors.ExecutiveDataException;
import org.bson.types.ObjectId;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.service.KanbanKpiMaturity;
import com.publicissapient.kpidashboard.apis.executive.service.ProjectEfficiencyService;
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
public class KanbanExecutiveDashboardStrategyTest {

	@Mock
	private CacheService cacheService;

	@Mock
	private ProjectEfficiencyService projectEfficiencyService;

	@Mock
	private UserBoardConfigService userBoardConfigService;

	@Mock
	private KanbanKpiMaturity kanbanKpiMaturity;

	@Mock
	private KpiCategoryRepository kpiCategoryRepository;

	@Mock
	private ConfigHelperService configHelperService;

	@Mock
	private Executor kanbanExecutiveTaskExecutor;

	@InjectMocks
	private KanbanExecutiveDashboardStrategy kanbanStrategy;

	@Before
	public void setup() {

		ReflectionTestUtils.setField(kanbanStrategy, "kanbanExecutiveTaskExecutor",
				Executors.newSingleThreadExecutor());
		when(userBoardConfigService.getBoardConfig(any(), any())).thenReturn(createUserBoard());
	}

	@Test
	public void testGetStrategyType() {
		assertEquals("kanban", kanbanStrategy.getStrategyType());
	}

	@Test
	public void testGetExecutor() {
		Executor executor = kanbanStrategy.getExecutor();
		assertNotNull(executor);
	}

	@Test
	public void testFetchDashboardData_EmptyProjectIds() {
		KpiRequest request = new KpiRequest();
		request.setIds(Collections.emptyList().toArray(new String[0]));
		request.setSelectedMap(createSelectedMap());

		assertThrows(ExecutiveDataException.class, () -> kanbanStrategy.fetchDashboardData(request) );
	}

	@Test
	public void testFetchDashboardData_ValidProject() {
		// Setup test data
		KpiRequest request = createTestKpiRequest();
		createTestProjectConfigs();

		// Execute
		ExecutiveDashboardResponseDTO response = kanbanStrategy.fetchDashboardData(request);

		// Verify
		assertNotNull(response);
		assertNotNull(response.getData());
	}

	@Test
	public void testFetchDashboardData_ExceptionHandling() {
		// Setup test data
		KpiRequest request = createTestKpiRequest();
		assertThrows(ExecutiveDataException.class, () -> kanbanStrategy.fetchDashboardData(request) );
	}

	@Test
	public void testFetchDashboardData_EmptyBoards() {
		KpiRequest request = createTestKpiRequest();
		assertThrows(ExecutiveDataException.class, () -> kanbanStrategy.fetchDashboardData(request) );
	}

	@Test
	public void testFetchDashboardData_NullKpiElements() {
		KpiRequest request = createTestKpiRequest();
		assertThrows(ExecutiveDataException.class, () -> kanbanStrategy.fetchDashboardData(request) );
	}

	// Helper methods
	private KpiRequest createTestKpiRequest() {
		KpiRequest request = new KpiRequest();
		request.setIds(Collections.singletonList("5c0f32fe00a9b83a7cbc4f0c").toArray(new String[0]));
		request.setLevel(5);
		request.setLabel("project");
		request.setSelectedMap(createSelectedMap());
		request.getSelectedMap().get("project").add("5c0f32fe00a9b83a7cbc4f0c");
		return request;
	}

	private void createTestProjectConfigs() {
		List<OrganizationHierarchy> hierarchyList = new ArrayList<>();
		hierarchyList.add(createOrgHierarchy("5c0f32fe00a9b83a7cbc4f0c", "Test Project"));
		when(configHelperService.loadAllOrganizationHierarchy()).thenReturn(hierarchyList);

	}

	private OrganizationHierarchy createOrgHierarchy(String id, String name) {
		OrganizationHierarchy hierarchy = new OrganizationHierarchy();
		hierarchy.setId(new ObjectId(id));
		hierarchy.setNodeId(id);
		hierarchy.setNodeName(name);
		hierarchy.setNodeDisplayName(name);
		return hierarchy;
	}

	private KpiElement createTestKpiElement(String kpiId, String maturity) {
		KpiElement element = new KpiElement();
		element.setKpiId(kpiId);
		element.setOverallMaturity(maturity);
		return element;
	}

	private Map<String, Object> createTestEfficiencyData() {
		Map<String, Object> efficiency = new HashMap<>();
		efficiency.put("overall", 4.2);
		efficiency.put("delivery", 4.0);
		efficiency.put("quality", 4.3);
		efficiency.put("stability", 4.2);
		return efficiency;
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

	private static UserBoardConfigDTO createUserBoard() {
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
		config.setKanban(List.of(board1));

		return config;

	}
}