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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
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
import com.publicissapient.kpidashboard.apis.model.AccountFilterRequest;
import com.publicissapient.kpidashboard.apis.model.AccountFilteredData;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;

/**
 * @author pkum34
 */
@RunWith(MockitoJUnitRunner.class)
public class ExecutiveServiceImplTest {

	@Mock private CacheService cacheService;

	@Mock private ExecutiveDashboardStrategyFactory strategyFactory;

	@Mock private ExecutiveDashboardStrategy kanbanStrategy;

	@Mock private ExecutiveDashboardStrategy scrumStrategy;

	@InjectMocks private ExecutiveServiceImpl service;
	@Mock private AccountHierarchyServiceImpl accountHierarchyService;
	@Mock private AccountHierarchyServiceKanbanImpl accountHierarchyServiceKanban;

	private KpiRequest kpiRequest;
	private ExecutiveDashboardRequestDTO requestDTO;
	private ExecutiveDashboardResponseDTO expectedResponse;
	private Map<String, ProjectBasicConfig> projectConfigMap;
	private Map<String, Object> hierarchyLevelMap;

	@Before
	public void setup() {

		when(strategyFactory.getStrategy("scrum")).thenReturn(scrumStrategy);
		when(strategyFactory.getStrategy("kanban")).thenReturn(kanbanStrategy);

		ExecutiveDashboardResponseDTO executiveDashboardRequestDTO =
				ExecutiveDashboardResponseDTO.builder().data(null).build();
		when(scrumStrategy.getExecutiveDashboard(any(KpiRequest.class)))
				.thenReturn(executiveDashboardRequestDTO);
		when(kanbanStrategy.getExecutiveDashboard(any())).thenReturn(executiveDashboardRequestDTO);
	}

	@Test
	public void testGetExecutiveDashboardScrum_withParentIdNullAndValidData() {
		ExecutiveDashboardRequestDTO req = ExecutiveDashboardRequestDTO.builder().build();
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
		ExecutiveDashboardRequestDTO req = ExecutiveDashboardRequestDTO.builder().build();
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
		ExecutiveDashboardRequestDTO req = ExecutiveDashboardRequestDTO.builder().build();
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
		ExecutiveDashboardRequestDTO req = ExecutiveDashboardRequestDTO.builder().build();
		req.setLevel(2);
		req.setLabel("Scrum Label");
		req.setParentId("PID");

		when(cacheService.getFullHierarchyLevel()).thenThrow(new RuntimeException("Cache error"));

		assertThrows(RuntimeException.class, () -> service.getExecutiveDashboardScrum(req));
	}

	@Test
	public void testGetExecutiveDashboardKanban_withValidData() {
		ExecutiveDashboardRequestDTO req = ExecutiveDashboardRequestDTO.builder().build();
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
		ExecutiveDashboardRequestDTO req = ExecutiveDashboardRequestDTO.builder().build();
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
		ExecutiveDashboardRequestDTO req = ExecutiveDashboardRequestDTO.builder().build();
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
		ExecutiveDashboardRequestDTO req = ExecutiveDashboardRequestDTO.builder().build();
		req.setLevel(3);
		req.setLabel("Kanban Label");
		req.setParentId("PID");

		when(cacheService.getFullKanbanHierarchyLevel()).thenThrow(new RuntimeException("Cache error"));

		assertThrows(RuntimeException.class, () -> service.getExecutiveDashboardKanban(req));
	}

	@Test
	public void testCreateSelectedMap() {
		// Setup test data
		HierarchyLevel level1 = new HierarchyLevel();
		level1.setHierarchyLevelId("level1");
		level1.setHierarchyLevelName("Level 1");
		level1.setLevel(1);

		HierarchyLevel level2 = new HierarchyLevel();
		level2.setHierarchyLevelId("level2");
		level2.setHierarchyLevelName("Level 2");
		level2.setLevel(2);

		when(cacheService.getFullHierarchyLevel()).thenReturn(List.of(level1, level2));

		// Execute
		Map<String, List<String>> map = invokeCreateSelectedMap(false);

		// Verify
		assertTrue(map.containsKey("level1"));
		assertTrue(map.containsKey("level2"));
		assertTrue(map.get("level1").isEmpty());
		assertTrue(map.get("level2").isEmpty());

		// Verify Kanban hierarchy
		when(cacheService.getFullKanbanHierarchyLevel()).thenReturn(List.of(level1));
		map = invokeCreateSelectedMap(true);
		assertTrue(map.containsKey("level1"));
		assertFalse(map.containsKey("level2"));
	}

	@Test
	public void testGetHierarchyLevels() {
		// Test Scrum hierarchy
		HierarchyLevel scrumLevel = new HierarchyLevel();
		scrumLevel.setHierarchyLevelId("scrum");
		when(cacheService.getFullHierarchyLevel()).thenReturn(List.of(scrumLevel));

		List<HierarchyLevel> result = invokeGetHierarchyLevels(false);
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("scrum", result.get(0).getHierarchyLevelId());

		// Test Kanban hierarchy
		HierarchyLevel kanbanLevel = new HierarchyLevel();
		kanbanLevel.setHierarchyLevelId("kanban");
		when(cacheService.getFullKanbanHierarchyLevel()).thenReturn(List.of(kanbanLevel));

		result = invokeGetHierarchyLevels(true);
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("kanban", result.get(0).getHierarchyLevelId());

		// Test empty hierarchy
		when(cacheService.getFullHierarchyLevel()).thenReturn(Collections.emptyList());
		result = invokeGetHierarchyLevels(false);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testGetNodeIds_WithException() {
		KpiRequest kpiRequest = new KpiRequest();
		kpiRequest.setLevel(1);
		kpiRequest.setLabel("test");

		when(cacheService.getFullHierarchyLevel()).thenThrow(new RuntimeException("Test exception"));
		Set<String> result = invokeGetNodeIds(kpiRequest, false, new AccountFilterRequest(), null);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testGetNodeIds_WithEmptyHierarchy() {
		KpiRequest kpiRequest = new KpiRequest();
		kpiRequest.setLevel(1);
		kpiRequest.setLabel("test");

		when(cacheService.getFullHierarchyLevel()).thenReturn(Collections.emptyList());
		Set<String> strings = invokeGetNodeIds(kpiRequest, false, new AccountFilterRequest(), null);

		assertTrue(strings.isEmpty());
	}

	// Helper methods to access private methods for testing
	@SuppressWarnings("unchecked")
	public Map<String, List<String>> invokeCreateSelectedMap(boolean isKanban) {
		try {
			java.lang.reflect.Method method =
					ExecutiveServiceImpl.class.getDeclaredMethod("createSelectedMap", boolean.class);
			method.setAccessible(true);
			return (Map<String, List<String>>) method.invoke(service, isKanban);
		} catch (Exception e) {
			throw new RuntimeException("Failed to invoke createSelectedMap", e);
		}
	}

	public List<HierarchyLevel> invokeGetHierarchyLevels(boolean isKanban) {
		try {
			java.lang.reflect.Method method =
					ExecutiveServiceImpl.class.getDeclaredMethod("getHierarchyLevels", boolean.class);
			method.setAccessible(true);
			return (List<HierarchyLevel>) method.invoke(service, isKanban);
		} catch (Exception e) {
			throw new RuntimeException("Failed to invoke getHierarchyLevels", e);
		}
	}

	public Set<String> invokeGetNodeIds(
			KpiRequest kpiRequest,
			boolean isKanban,
			AccountFilterRequest accountFilterRequest,
			String parentId) {
		try {
			java.lang.reflect.Method method =
					ExecutiveServiceImpl.class.getDeclaredMethod(
							"getNodeIds",
							KpiRequest.class,
							boolean.class,
							AccountFilterRequest.class,
							String.class);
			method.setAccessible(true);
			return (Set<String>)
					method.invoke(service, kpiRequest, isKanban, accountFilterRequest, parentId);
		} catch (Exception e) {
			throw new RuntimeException("Failed to invoke getNodeIds", e);
		}
	}
}
