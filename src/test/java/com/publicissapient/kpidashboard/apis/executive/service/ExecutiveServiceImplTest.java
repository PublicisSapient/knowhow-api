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
package com.publicissapient.kpidashboard.apis.executive.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardRequestDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.strategy.ExecutiveDashboardStrategy;
import com.publicissapient.kpidashboard.apis.executive.strategy.ExecutiveDashboardStrategyFactory;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceImpl;
import com.publicissapient.kpidashboard.apis.filter.service.AccountHierarchyServiceKanbanImpl;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;

/**
 * @author pkum34
 */
@RunWith(MockitoJUnitRunner.class)
public class ExecutiveServiceImplTest {

	@Mock
	private CacheService cacheService;

	@Mock
	private ExecutiveDashboardStrategyFactory strategyFactory;

	@Mock
	private ExecutiveDashboardStrategy kanbanStrategy;

	@Mock
	private ExecutiveDashboardStrategy scrumStrategy;

	@InjectMocks
	private ExecutiveServiceImpl service;
	@Mock
	private AccountHierarchyServiceImpl accountHierarchyService;
	@Mock
	private AccountHierarchyServiceKanbanImpl accountHierarchyServiceKanban;

	private KpiRequest kpiRequest;
	private ExecutiveDashboardRequestDTO requestDTO;
	private ExecutiveDashboardResponseDTO expectedResponse;
	private Map<String, ProjectBasicConfig> projectConfigMap;
	private Map<String, Object> hierarchyLevelMap;

	@Before
	public void setup() {

		when(strategyFactory.getStrategy("scrum")).thenReturn(scrumStrategy);
		when(strategyFactory.getStrategy("kanban")).thenReturn(kanbanStrategy);

		ExecutiveDashboardResponseDTO executiveDashboardRequestDTO = ExecutiveDashboardResponseDTO.builder().data(null)
				.build();
		when(scrumStrategy.getExecutiveDashboard(any(KpiRequest.class))).thenReturn(executiveDashboardRequestDTO);
		when(kanbanStrategy.getExecutiveDashboard(any())).thenReturn(executiveDashboardRequestDTO);
	}

	@Test
	public void testGetExecutiveDashboardScrum_withParentIdNullAndValidData() {
		ExecutiveDashboardRequestDTO req = new ExecutiveDashboardRequestDTO();
		req.setLevel(2);
		req.setLabel("Scrum Label");
		req.setParentId(null);

		HierarchyLevel hl = new HierarchyLevel();
		hl.setHierarchyLevelId("acc");
		hl.setHierarchyLevelName("Account");
		hl.setLevel(2);

		when(cacheService.getFullHierarchyLevel()).thenReturn(List.of(hl));

		AccountFilteredData afd = new AccountFilteredData();
		afd.setLevel(2);
		afd.setNodeId("N1");
		afd.setParentId(null);

		when(accountHierarchyService.getFilteredList(any())).thenReturn(Set.of(afd));

		ExecutiveDashboardResponseDTO res = service.getExecutiveDashboardScrum(req);

		assertNotNull(res);
		verify(scrumStrategy).getExecutiveDashboard(any(KpiRequest.class));
	}

	@Test
	public void testGetExecutiveDashboardScrum_withParentIdNotNullAndValidData() {
		ExecutiveDashboardRequestDTO req = new ExecutiveDashboardRequestDTO();
		req.setLevel(2);
		req.setLabel("Scrum Label");
		req.setParentId("parentId");

		HierarchyLevel hl = new HierarchyLevel();
		hl.setHierarchyLevelId("acc");
		hl.setHierarchyLevelName("Account");
		hl.setLevel(2);

		when(cacheService.getFullHierarchyLevel()).thenReturn(List.of(hl));

		AccountFilteredData afd = new AccountFilteredData();
		afd.setLevel(2);
		afd.setNodeId("N1");
		afd.setParentId("parentId");

		when(accountHierarchyService.getFilteredList(any())).thenReturn(Set.of(afd));

		ExecutiveDashboardResponseDTO res = service.getExecutiveDashboardScrum(req);

		assertNotNull(res);
		verify(scrumStrategy).getExecutiveDashboard(any(KpiRequest.class));
	}

	@Test
	public void testGetExecutiveDashboardScrum_withParentIdProvidedAndEmptyHierarchy() {
		ExecutiveDashboardRequestDTO req = new ExecutiveDashboardRequestDTO();
		req.setLevel(2);
		req.setLabel("Scrum Label");
		req.setParentId("PID");

		when(cacheService.getFullHierarchyLevel()).thenReturn(Collections.emptyList());

		ExecutiveDashboardResponseDTO res = service.getExecutiveDashboardScrum(req);

		assertNotNull(res);
		verify(scrumStrategy).getExecutiveDashboard(any(KpiRequest.class));
	}

	@Test
	public void testGetExecutiveDashboardScrum_withExceptionInGetNodeIds() {
		ExecutiveDashboardRequestDTO req = new ExecutiveDashboardRequestDTO();
		req.setLevel(2);
		req.setLabel("Scrum Label");
		req.setParentId("PID");

		when(cacheService.getFullHierarchyLevel()).thenThrow(new RuntimeException("Cache error"));

		ExecutiveDashboardResponseDTO res = service.getExecutiveDashboardScrum(req);

		assertNotNull(res);
		verify(scrumStrategy).getExecutiveDashboard(any(KpiRequest.class));
	}

	@Test
	public void testGetExecutiveDashboardKanban_withValidData() {
		ExecutiveDashboardRequestDTO req = new ExecutiveDashboardRequestDTO();
		req.setLevel(3);
		req.setLabel("Kanban Label");
		req.setParentId(null);

		HierarchyLevel hl = new HierarchyLevel();
		hl.setHierarchyLevelId("project");
		hl.setHierarchyLevelName("Project");
		hl.setLevel(3);

		when(cacheService.getFullKanbanHierarchyLevel()).thenReturn(List.of(hl));

		AccountFilteredData afd = new AccountFilteredData();
		afd.setLevel(3);
		afd.setNodeId("K1");
		afd.setParentId(null);

		when(accountHierarchyServiceKanban.getFilteredList(any())).thenReturn(Set.of(afd));

		ExecutiveDashboardResponseDTO res = service.getExecutiveDashboardKanban(req);

		assertNotNull(res);
		verify(kanbanStrategy).getExecutiveDashboard(any(KpiRequest.class));
	}

	@Test
	public void testGetExecutiveDashboardKanban_withInValidLevel() {
		ExecutiveDashboardRequestDTO req = new ExecutiveDashboardRequestDTO();
		req.setLevel(3);
		req.setLabel("Kanban Label");
		req.setParentId(null);

		HierarchyLevel hl = new HierarchyLevel();
		hl.setHierarchyLevelId("project");
		hl.setHierarchyLevelName("Project");
		hl.setLevel(4);

		when(cacheService.getFullKanbanHierarchyLevel()).thenReturn(List.of(hl));

		AccountFilteredData afd = new AccountFilteredData();
		afd.setLevel(3);
		afd.setNodeId("K1");
		afd.setParentId(null);

		ExecutiveDashboardResponseDTO res = service.getExecutiveDashboardKanban(req);

		assertNotNull(res);
		verify(kanbanStrategy).getExecutiveDashboard(any(KpiRequest.class));
	}

	@Test
	public void testGetExecutiveDashboardKanban_withEmptyHierarchy() {
		ExecutiveDashboardRequestDTO req = new ExecutiveDashboardRequestDTO();
		req.setLevel(3);
		req.setLabel("Kanban Label");
		req.setParentId("PID");

		when(cacheService.getFullKanbanHierarchyLevel()).thenReturn(Collections.emptyList());

		ExecutiveDashboardResponseDTO res = service.getExecutiveDashboardKanban(req);

		assertNotNull(res);
		verify(kanbanStrategy).getExecutiveDashboard(any(KpiRequest.class));
	}

	@Test
	public void testGetExecutiveDashboardKanban_withExceptionInGetNodeIds() {
		ExecutiveDashboardRequestDTO req = new ExecutiveDashboardRequestDTO();
		req.setLevel(3);
		req.setLabel("Kanban Label");
		req.setParentId("PID");

		when(cacheService.getFullKanbanHierarchyLevel()).thenThrow(new RuntimeException("Cache error"));

		ExecutiveDashboardResponseDTO res = service.getExecutiveDashboardKanban(req);

		assertNotNull(res);
		verify(kanbanStrategy).getExecutiveDashboard(any(KpiRequest.class));
	}

	@Test
	public void testCreateSelectedMap() {
		Map<String, List<String>> map = invokeCreateSelectedMap();
		assertTrue(map.containsKey("bu"));
		assertTrue(map.containsKey("ver"));
		assertTrue(map.containsKey("acc"));
		assertTrue(map.containsKey("port"));
		assertTrue(map.containsKey("project"));
		assertTrue(map.containsKey("release"));
		assertTrue(map.containsKey("sqd"));
		assertTrue(map.containsKey("date"));
		assertEquals(List.of("Weeks"), map.get("date"));
	}

	// helper to access private static method
	private Map<String, List<String>> invokeCreateSelectedMap() {
		return org.springframework.test.util.ReflectionTestUtils.invokeMethod(ExecutiveServiceImpl.class,
				"createSelectedMap");
	}

}
