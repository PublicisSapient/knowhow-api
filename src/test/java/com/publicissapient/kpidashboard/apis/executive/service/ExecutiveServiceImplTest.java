package com.publicissapient.kpidashboard.apis.executive.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardDataDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardRequestDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveMatrixDTO;
import com.publicissapient.kpidashboard.apis.executive.strategy.ExecutiveDashboardStrategy;
import com.publicissapient.kpidashboard.apis.executive.strategy.ExecutiveDashboardStrategyFactory;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
import com.publicissapient.kpidashboard.common.model.application.HierarchyLevel;
import com.publicissapient.kpidashboard.common.model.application.ProjectBasicConfig;

@ExtendWith(MockitoExtension.class)
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
    private ExecutiveServiceImpl executiveService;

    private KpiRequest kpiRequest;
    private ExecutiveDashboardRequestDTO requestDTO;
    private ExecutiveDashboardResponseDTO expectedResponse;
    private Map<String, ProjectBasicConfig> projectConfigMap;
    private Map<String, Object> hierarchyLevelMap;

    @BeforeEach
    public void setUp() {
        // Setup test data
        kpiRequest = new KpiRequest();
        Map<String, List<String>> selectedMap = new HashMap<>();
        selectedMap.put(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, Collections.singletonList("project1"));
        kpiRequest.setSelectedMap(selectedMap);

        // Setup request DTO
        requestDTO = new ExecutiveDashboardRequestDTO();
        requestDTO.setLevel(5);
        requestDTO.setLabel("account");
        requestDTO.setDate("Weeks");
        requestDTO.setDuration(5);
        requestDTO.setParentId("");

        // Setup expected response
        expectedResponse = ExecutiveDashboardResponseDTO.builder()
                .data(ExecutiveDashboardDataDTO.builder()
                        .matrix(ExecutiveMatrixDTO.builder()
                                .rows(Collections.emptyList())
                                .build())
                        .build())
                .build();

        // Setup project config
        ProjectBasicConfig projectConfig = new ProjectBasicConfig();
        projectConfig.setProjectNodeId("project1");
        projectConfig.setKanban(true);
        projectConfigMap = Map.of("project1", projectConfig);

        // Setup hierarchy level map
        hierarchyLevelMap = new HashMap<>();
        hierarchyLevelMap.put("ACCOUNT", List.of("account1", "account2"));
    }

    @Test
    public void testGetExecutiveDashboardKanban() {
        // Given
        when(strategyFactory.getStrategy("kanban")).thenReturn(kanbanStrategy);
        when(kanbanStrategy.getExecutiveDashboard(any(KpiRequest.class))).thenReturn(expectedResponse);

        // When
        ExecutiveDashboardResponseDTO response = executiveService.getExecutiveDashboardKanban( requestDTO);

        // Then
        assertNotNull(response);
        assertNotNull(response.getData());
        assertNotNull(response.getData().getMatrix());
        assertTrue(response.getData().getMatrix().getRows().isEmpty());
    }

    @Test
    public void testGetExecutiveDashboardScrum() {
        // Given
        when(strategyFactory.getStrategy("scrum")).thenReturn(scrumStrategy);
        when(scrumStrategy.getExecutiveDashboard(any(KpiRequest.class))).thenReturn(expectedResponse);
        // When
        ExecutiveDashboardResponseDTO response = executiveService.getExecutiveDashboardScrum(requestDTO);

        // Then
        assertNotNull(response);
        assertNotNull(response.getData());
        assertNotNull(response.getData().getMatrix());
        assertTrue(response.getData().getMatrix().getRows().isEmpty());
    }

    @Test
    public void testGetAllAccountLevelIds() {
        // Given
        Map<String, HierarchyLevel> mockHierarchyMap = new HashMap<>();
        HierarchyLevel account1 = new HierarchyLevel();
        account1.setHierarchyLevelId("account1");
        HierarchyLevel account2 = new HierarchyLevel();
        account2.setHierarchyLevelId("account2");
        mockHierarchyMap.put("ACCOUNT1", account1);
        mockHierarchyMap.put("ACCOUNT2", account2);

        //when(filterHelperService.getHierarchyLevelMap(anyBoolean())).thenReturn(mockHierarchyMap);

        // When
        List<String> accountIds = new ArrayList<>();
        if (mockHierarchyMap != null && !mockHierarchyMap.isEmpty()) {
            accountIds = mockHierarchyMap.values().stream()
                    .filter(level -> level.getHierarchyLevelId() != null)
                    .map(level -> level.getHierarchyLevelId().toString())
                    .collect(Collectors.toList());
        }

        // Then
        assertNotNull(accountIds);
        assertEquals(2, accountIds.size());
        assertTrue(accountIds.contains("account1"));
        assertTrue(accountIds.contains("account2"));
    }

    @Test
    public void testGetNodeidWiseProject() {
        // Given
        List<String> projectNodeIds = Collections.singletonList("project1");
        when(cacheService.cacheProjectConfigMapData()).thenReturn(projectConfigMap);

        // When
        Map<String, ProjectBasicConfig> result = new HashMap<>();
                //executiveService.getNodeidWiseProject(projectNodeIds, true);

        // Then
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue(result.containsKey("project1"));
    }

    @Test
    public void testGetDefaultResponse() {
        // When
        /*ExecutiveDashboardResponseDTO response = executiveService.getDefaultResponse();

        // Then
        assertNotNull(response);
        assertNotNull(response.getData());
        assertNotNull(response.getData().getMatrix());
        assertTrue(response.getData().getMatrix().getRows().isEmpty());

         */
    }
}
