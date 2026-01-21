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

package com.publicissapient.kpidashboard.apis.bitbucket.service.scm.strategy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.publicissapient.kpidashboard.apis.enums.KPICode;

@ExtendWith(MockitoExtension.class)
class KpiStrategyRegistryTest {

	private KpiStrategyRegistry registry;

	@Mock private KpiCalculationStrategy<?> trendStrategy;

	@Mock private KpiCalculationStrategy<?> nonTrendStrategy;

	@BeforeEach
	void setUp() {
		when(trendStrategy.getStrategyType()).thenReturn("REPO_TOOL_MEAN_TIME_TO_MERGE_TREND");
		when(nonTrendStrategy.getStrategyType()).thenReturn("REPO_TOOL_MEAN_TIME_TO_MERGE_NON_TREND");

		registry = new KpiStrategyRegistry(List.of(trendStrategy, nonTrendStrategy));
	}

	@Test
	void testGetStrategy_WithLineChartType() {
		KpiCalculationStrategy<?> result =
				registry.getStrategy(KPICode.REPO_TOOL_MEAN_TIME_TO_MERGE, "line");

		assertNotNull(result);
		assertEquals(trendStrategy, result);
	}

	@Test
	void testGetStrategy_WithBarChartType() {
		KpiCalculationStrategy<?> result =
				registry.getStrategy(KPICode.REPO_TOOL_MEAN_TIME_TO_MERGE, "bar");

		assertNotNull(result);
		assertEquals(nonTrendStrategy, result);
	}

	@Test
	void testGetStrategy_WithLineChartTypeCaseInsensitive() {
		KpiCalculationStrategy<?> result =
				registry.getStrategy(KPICode.REPO_TOOL_MEAN_TIME_TO_MERGE, "LINE");

		assertNotNull(result);
		assertEquals(trendStrategy, result);
	}
}
