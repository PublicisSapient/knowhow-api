package com.publicissapient.kpidashboard.apis.executive.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;

@ExtendWith(MockitoExtension.class)
class ExecutiveDashboardStrategyTest {

    @Mock
    private ExecutiveDashboardStrategyFactory strategyFactory;

    @Mock
    private KanbanExecutiveDashboardStrategy kanbanStrategy;

    @Mock
    private ScrumExecutiveDashboardStrategy scrumStrategy;

    @Mock
    private KpiRequest kpiRequest;

    @BeforeEach
    void setUp() {
        when(strategyFactory.getStrategy("kanban")).thenReturn(kanbanStrategy);
        when(strategyFactory.getStrategy("scrum")).thenReturn(scrumStrategy);
    }

    @Test
    void testKanbanStrategy() {
        // Given
        //ExecutiveDashboardResponseDTO expectedResponse = new ExecutiveDashboardResponseDTO();
        //when(kanbanStrategy.getExecutiveDashboard(kpiRequest)).thenReturn(expectedResponse);

        // When
        ExecutiveDashboardResponseDTO response = strategyFactory.getStrategy("kanban")
                .getExecutiveDashboard(kpiRequest);

        // Then
        assertNotNull(response);
       // assertEquals(expectedResponse, response);
        verify(kanbanStrategy).getExecutiveDashboard(kpiRequest);
    }

    @Test
    void testScrumStrategy() {
        // Given
       // ExecutiveDashboardResponseDTO expectedResponse = new ExecutiveDashboardResponseDTO();
        //when(scrumStrategy.getExecutiveDashboard(kpiRequest)).thenReturn(expectedResponse);

        // When
        ExecutiveDashboardResponseDTO response = strategyFactory.getStrategy("scrum")
                .getExecutiveDashboard(kpiRequest);

        // Then
        assertNotNull(response);
        //assertEquals(expectedResponse, response);
        verify(scrumStrategy).getExecutiveDashboard(kpiRequest);
    }

    @Test
    void testStrategyFactoryWithInvalidType() {
        // Given
        String invalidType = "invalid";
        when(strategyFactory.getStrategy(invalidType)).thenThrow(new IllegalArgumentException("No strategy found for type: " + invalidType));

        // When / Then
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            strategyFactory.getStrategy(invalidType);
        });

        assertEquals("No strategy found for type: " + invalidType, exception.getMessage());
    }
}
