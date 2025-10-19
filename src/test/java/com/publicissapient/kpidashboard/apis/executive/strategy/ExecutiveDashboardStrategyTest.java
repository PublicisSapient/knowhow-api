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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.executive.dto.ExecutiveDashboardResponseDTO;
import com.publicissapient.kpidashboard.apis.model.KpiRequest;

@ExtendWith(MockitoExtension.class)
public class ExecutiveDashboardStrategyTest {

	@Mock private ExecutiveDashboardStrategyFactory strategyFactory;

	@Mock private KanbanExecutiveDashboardStrategy kanbanStrategy;

	@Mock private ScrumExecutiveDashboardStrategy scrumStrategy;

	@Mock private KpiRequest kpiRequest;

	@Before
	public void setUp() {
		MockitoAnnotations.openMocks(this);
		when(strategyFactory.getStrategy("kanban")).thenReturn(kanbanStrategy);
		when(strategyFactory.getStrategy("scrum")).thenReturn(scrumStrategy);
	}

	@Test
	public void testKanbanStrategy() {
		ExecutiveDashboardResponseDTO response =
				strategyFactory.getStrategy("kanban").getExecutiveDashboard(kpiRequest);

		// Then
		assertNull(response);
		verify(kanbanStrategy).getExecutiveDashboard(kpiRequest);
	}

	@Test
	public void testScrumStrategy() {
		ExecutiveDashboardResponseDTO response =
				strategyFactory.getStrategy("scrum").getExecutiveDashboard(kpiRequest);
		assertNull(response);
		verify(scrumStrategy).getExecutiveDashboard(kpiRequest);
	}

	@Test
	public void testStrategyFactoryWithInvalidType() {
		// Given
		String invalidType = "invalid";
		when(strategyFactory.getStrategy(invalidType))
				.thenThrow(new IllegalArgumentException("No strategy found for type: " + invalidType));

		// When / Then
		Exception exception =
				assertThrows(
						IllegalArgumentException.class,
						() -> {
							strategyFactory.getStrategy(invalidType);
						});

		assertEquals("No strategy found for type: " + invalidType, exception.getMessage());
	}
}
