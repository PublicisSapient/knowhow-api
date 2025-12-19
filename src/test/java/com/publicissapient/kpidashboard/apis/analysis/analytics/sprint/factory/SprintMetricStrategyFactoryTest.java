/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.factory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.enums.SprintMetricType;
import com.publicissapient.kpidashboard.apis.analysis.analytics.sprint.strategy.SprintMetricStrategy;

@RunWith(MockitoJUnitRunner.class)
public class SprintMetricStrategyFactoryTest {

	@InjectMocks private SprintMetricStrategyFactory factory;

	@Mock private SprintMetricStrategy groomingDayOneStrategy;

	@Mock private SprintMetricStrategy devCompletionBreachStrategy;

	@Mock private SprintMetricStrategy missingEstimationStrategy;

	@Mock private SprintMetricStrategy scopeAddedPercentageStrategy;

	@Mock private SprintMetricStrategy scopeChangePercentageStrategy;

	@Mock private SprintMetricStrategy scopeChangePostStartStrategy;

	@Mock private SprintMetricStrategy spilloverAgeStrategy;

	@Mock private SprintMetricStrategy sprintReadinessNextSprintStrategy;

	private List<SprintMetricStrategy> strategies;

	@Before
	public void setUp() {
		// Setup mock strategies
		when(groomingDayOneStrategy.getMetricType()).thenReturn(SprintMetricType.GROOMING_DAY_ONE);
		when(devCompletionBreachStrategy.getMetricType())
				.thenReturn(SprintMetricType.DEV_COMPLETION_BREACH);
		when(missingEstimationStrategy.getMetricType()).thenReturn(SprintMetricType.MISSING_ESTIMATION);
		when(scopeAddedPercentageStrategy.getMetricType())
				.thenReturn(SprintMetricType.SCOPE_ADDED_PERCENTAGE);
		when(scopeChangePercentageStrategy.getMetricType())
				.thenReturn(SprintMetricType.SCOPE_CHANGE_PERCENTAGE);
		when(scopeChangePostStartStrategy.getMetricType())
				.thenReturn(SprintMetricType.SCOPE_CHANGE_POST_START);
		when(spilloverAgeStrategy.getMetricType()).thenReturn(SprintMetricType.SPILLOVER_AGE);
		when(sprintReadinessNextSprintStrategy.getMetricType())
				.thenReturn(SprintMetricType.SPRINT_READINESS_NEXT_SPRINT);

		// Create strategy list
		strategies =
				Arrays.asList(
						groomingDayOneStrategy,
						devCompletionBreachStrategy,
						missingEstimationStrategy,
						scopeAddedPercentageStrategy,
						scopeChangePercentageStrategy,
						scopeChangePostStartStrategy,
						spilloverAgeStrategy,
						sprintReadinessNextSprintStrategy);

		// Recreate factory with mocked strategies
		factory = new SprintMetricStrategyFactory(strategies);
		factory.init();
	}

	@Test
	public void testInit_AllStrategiesRegistered() {
		// Verify all strategies are registered
		for (SprintMetricType metricType : SprintMetricType.values()) {
			SprintMetricStrategy strategy = factory.getStrategy(metricType);
			assertNotNull("Strategy should be registered for " + metricType, strategy);
			assertEquals(metricType, strategy.getMetricType());
		}
	}

	@Test
	public void testGetStrategy_GroomingDayOne() {
		SprintMetricStrategy strategy = factory.getStrategy(SprintMetricType.GROOMING_DAY_ONE);
		assertNotNull(strategy);
		assertEquals(SprintMetricType.GROOMING_DAY_ONE, strategy.getMetricType());
		assertEquals(groomingDayOneStrategy, strategy);
	}

	@Test
	public void testGetStrategy_DevCompletionBreach() {
		SprintMetricStrategy strategy = factory.getStrategy(SprintMetricType.DEV_COMPLETION_BREACH);
		assertNotNull(strategy);
		assertEquals(SprintMetricType.DEV_COMPLETION_BREACH, strategy.getMetricType());
		assertEquals(devCompletionBreachStrategy, strategy);
	}

	@Test
	public void testGetStrategy_MissingEstimation() {
		SprintMetricStrategy strategy = factory.getStrategy(SprintMetricType.MISSING_ESTIMATION);
		assertNotNull(strategy);
		assertEquals(SprintMetricType.MISSING_ESTIMATION, strategy.getMetricType());
		assertEquals(missingEstimationStrategy, strategy);
	}

	@Test
	public void testGetStrategy_ScopeAddedPercentage() {
		SprintMetricStrategy strategy = factory.getStrategy(SprintMetricType.SCOPE_ADDED_PERCENTAGE);
		assertNotNull(strategy);
		assertEquals(SprintMetricType.SCOPE_ADDED_PERCENTAGE, strategy.getMetricType());
		assertEquals(scopeAddedPercentageStrategy, strategy);
	}

	@Test
	public void testGetStrategy_ScopeChangePercentage() {
		SprintMetricStrategy strategy = factory.getStrategy(SprintMetricType.SCOPE_CHANGE_PERCENTAGE);
		assertNotNull(strategy);
		assertEquals(SprintMetricType.SCOPE_CHANGE_PERCENTAGE, strategy.getMetricType());
		assertEquals(scopeChangePercentageStrategy, strategy);
	}

	@Test
	public void testGetStrategy_ScopeChangePostStart() {
		SprintMetricStrategy strategy = factory.getStrategy(SprintMetricType.SCOPE_CHANGE_POST_START);
		assertNotNull(strategy);
		assertEquals(SprintMetricType.SCOPE_CHANGE_POST_START, strategy.getMetricType());
		assertEquals(scopeChangePostStartStrategy, strategy);
	}

	@Test
	public void testGetStrategy_SpilloverAge() {
		SprintMetricStrategy strategy = factory.getStrategy(SprintMetricType.SPILLOVER_AGE);
		assertNotNull(strategy);
		assertEquals(SprintMetricType.SPILLOVER_AGE, strategy.getMetricType());
		assertEquals(spilloverAgeStrategy, strategy);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetStrategy_NullMetricType_ThrowsException() {
		factory.getStrategy(null);
	}

	@Test
	public void testGetEnabledMetricTypes_ReturnsEnabledMetrics() {
		List<SprintMetricType> enabledMetrics = factory.getEnabledMetricTypes();
		assertNotNull(enabledMetrics);
		assertFalse("Should have at least one enabled metric", enabledMetrics.isEmpty());

		// Verify only enabled metrics are returned
		for (SprintMetricType metricType : enabledMetrics) {
			assertTrue("Only enabled metrics should be returned", metricType.isEnabled());
		}
	}

	@Test
	public void testGetEnabledMetricTypes_ExcludesDisabledMetrics() {
		List<SprintMetricType> enabledMetrics = factory.getEnabledMetricTypes();

		// Count total metrics vs enabled metrics
		long totalMetrics = SprintMetricType.values().length;
		long enabledCount = enabledMetrics.size();

		// Verify that disabled metrics are excluded
		for (SprintMetricType metricType : SprintMetricType.values()) {
			if (!metricType.isEnabled()) {
				assertFalse(
						"Disabled metric should not be in enabled list", enabledMetrics.contains(metricType));
			}
		}
	}

	@Test
	public void testFactoryReturnsSameStrategyInstance() {
		// Verify factory returns same instance for multiple calls
		SprintMetricStrategy strategy1 = factory.getStrategy(SprintMetricType.GROOMING_DAY_ONE);
		SprintMetricStrategy strategy2 = factory.getStrategy(SprintMetricType.GROOMING_DAY_ONE);

		assertEquals("Factory should return same strategy instance", strategy1, strategy2);
	}
}
