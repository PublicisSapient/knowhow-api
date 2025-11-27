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
package com.publicissapient.kpidashboard.apis.kpimaturity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.common.service.CacheService;
import com.publicissapient.kpidashboard.apis.config.CustomApiConfig;
import com.publicissapient.kpidashboard.apis.userboardconfig.service.UserBoardConfigService;
import com.publicissapient.kpidashboard.common.repository.application.KpiCategoryRepository;

@ExtendWith(MockitoExtension.class)
class KanbanExecutiveDashboardServiceTest {

	@Mock
	private ProjectEfficiencyService projectEfficiencyService;

	@Mock
	private CacheService cacheService;

	@Mock
	private UserBoardConfigService userBoardConfigService;

	@Mock
	private KanbanKpiMaturity kanbanKpiMaturity;

	@Mock
	private KpiCategoryRepository kpiCategoryRepository;

	@Mock
	private ConfigHelperService configHelperService;

	@Mock
	private CustomApiConfig customApiConfig;

	@Mock
	private Executor kanbanExecutiveTaskExecutor;

	@InjectMocks
	private KanbanExecutiveDashboardService kanbanExecutiveDashboardService;

	@BeforeEach
	void setUp() {

		// Initialize the spy with proper constructor
		kanbanExecutiveDashboardService = new KanbanExecutiveDashboardService(
				projectEfficiencyService,
				cacheService,
				userBoardConfigService,
				kanbanKpiMaturity,
				kpiCategoryRepository,
				configHelperService,
				customApiConfig);

		// Set the executor field using reflection
		ReflectionTestUtils.setField(kanbanExecutiveDashboardService, "kanbanExecutiveTaskExecutor", kanbanExecutiveTaskExecutor);
	}

	@Test
	void when_ServiceCreated_Then_StrategyTypeIsKanban() {
		// Act
		String strategyType = (String) ReflectionTestUtils.getField(kanbanExecutiveDashboardService, "strategyType");

		// Assert
		assertEquals("kanban", strategyType);
	}

	@Test
	void when_GetExecutorCalled_Then_ReturnsKanbanExecutiveTaskExecutor() {
		// Act
		Executor result = kanbanExecutiveDashboardService.getExecutor();

		// Assert
		assertSame(kanbanExecutiveTaskExecutor, result);
	}

	@Test
	void when_FetchDashboardDataCalledWithNullKpiRequest_Then_ThrowsNullPointerException() {
		// Act & Assert
		assertThrows(NullPointerException.class,
				() -> kanbanExecutiveDashboardService.fetchDashboardData(null));
	}

	@Test
	void when_ConstructorCalledWithAllDependencies_Then_InitializesCorrectly() {
		// Act
		KanbanExecutiveDashboardService service = new KanbanExecutiveDashboardService(
				projectEfficiencyService,
				cacheService,
				userBoardConfigService,
				kanbanKpiMaturity,
				kpiCategoryRepository,
				configHelperService,
				customApiConfig);

		// Assert
		assertNotNull(service);
		assertEquals("kanban", ReflectionTestUtils.getField(service, "strategyType"));
	}
}
