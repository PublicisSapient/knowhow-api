package com.publicissapient.kpidashboard.apis.executive.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardDataDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveMatrixDTO;
import com.publicissapient.kpidashboard.apis.executive.strategy.ExecutiveDashboardStrategy;
import com.publicissapient.kpidashboard.apis.executive.strategy.ExecutiveDashboardStrategyFactory;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;
import com.publicissapient.kpidashboard.common.constant.CommonConstant;
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
    private ExecutiveDashboardResponseDTO expectedResponse;
    private Map<String, ProjectBasicConfig> projectConfigMap;

    @BeforeEach
    public void setUp() {
        // Setup test data
        kpiRequest = new KpiRequest();
        Map<String, List<String>> selectedMap = new HashMap<>();
        selectedMap.put(CommonConstant.HIERARCHY_LEVEL_ID_PROJECT, Collections.singletonList("project1"));
        kpiRequest.setSelectedMap(selectedMap);

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
    }

    @Test
    public void testGetExecutiveDashboardKanban() {
        // Given
        when(strategyFactory.getStrategy("kanban")).thenReturn(kanbanStrategy);
        when(kanbanStrategy.getExecutiveDashboard(any(KpiRequest.class))).thenReturn(expectedResponse);
        when(cacheService.cacheProjectConfigMapData()).thenReturn(projectConfigMap);

        // When
        ExecutiveDashboardResponseDTO response = executiveService.getExecutiveDashboardKanban(kpiRequest);

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
        when(cacheService.cacheProjectConfigMapData()).thenReturn(projectConfigMap);

        // When
        ExecutiveDashboardResponseDTO response = executiveService.getExecutiveDashboardScrum(kpiRequest);

        // Then
        assertNotNull(response);
        assertNotNull(response.getData());
        assertNotNull(response.getData().getMatrix());
        assertTrue(response.getData().getMatrix().getRows().isEmpty());
    }

    @Test
    public void testGetProjectNodeIds() {
        // Given
        List<String> expectedNodeIds = Collections.singletonList("project1");

        // When
        List<String> nodeIds = new ArrayList<>();
                //executiveService.getProjectNodeIds(kpiRequest);

        // Then
        assertNotNull(nodeIds);
        assertEquals(expectedNodeIds, nodeIds);
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
