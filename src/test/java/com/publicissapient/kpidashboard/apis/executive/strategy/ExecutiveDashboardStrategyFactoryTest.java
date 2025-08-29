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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class ExecutiveDashboardStrategyFactoryTest {

	private ExecutiveDashboardStrategyFactory factory;

	private ExecutiveDashboardStrategy scrumStrategy;
	private ExecutiveDashboardStrategy kanbanStrategy;

	@Before
	public void setup() {
		// Mock strategies
		scrumStrategy = mock(ExecutiveDashboardStrategy.class);
		kanbanStrategy = mock(ExecutiveDashboardStrategy.class);

		when(scrumStrategy.getStrategyType()).thenReturn("scrum");
		when(kanbanStrategy.getStrategyType()).thenReturn("kanban");

		// Initialize factory with list of strategies
		factory = new ExecutiveDashboardStrategyFactory(List.of(scrumStrategy, kanbanStrategy));
	}

	@Test
	public void testGetStrategy_Scrum() {
		ExecutiveDashboardStrategy result = factory.getStrategy("scrum");
		assertNotNull(result);
		assertEquals(scrumStrategy, result);
	}

	@Test
	public void testGetStrategy_Kanban() {
		ExecutiveDashboardStrategy result = factory.getStrategy("kanban");
		assertNotNull(result);
		assertEquals(kanbanStrategy, result);
	}

	@Test
	public void testGetStrategy_CaseInsensitive() {
		ExecutiveDashboardStrategy result = factory.getStrategy("SCRUM");
		assertNotNull(result);
		assertEquals(scrumStrategy, result);
	}

	@Test
	public void testGetStrategy_UnknownType() {
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> factory.getStrategy("unknown"));
		assertEquals("No strategy found for type: unknown", exception.getMessage());
	}

	@Test
	public void testFactoryWithEmptyStrategyList() {
		ExecutiveDashboardStrategyFactory emptyFactory = new ExecutiveDashboardStrategyFactory(List.of());
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
				() -> emptyFactory.getStrategy("scrum"));
		assertEquals("No strategy found for type: scrum", exception.getMessage());
	}
}