/*
 *   Copyright 2014 CapitalOne, LLC.
 *   Further development Copyright 2022 Sapient Corporation.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.publicissapient.kpidashboard.apis.management.service.impl;

import com.publicissapient.kpidashboard.apis.config.DashboardConfig;
import com.publicissapient.kpidashboard.apis.model.ApiDetailDto;
import com.publicissapient.kpidashboard.apis.model.HealthResponseDto;
import com.publicissapient.kpidashboard.apis.management.service.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardHealthServiceImplTest {

	@InjectMocks
	private DashboardHealthServiceImpl dashboardHealthService;

	@Mock
	private DashboardConfig dashboardConfig;

	@Mock
	private MetricsService metricsService;

	@BeforeEach
	void setUp() {
		// Setup common mock interactions
		Mockito.lenient().when(dashboardConfig.getHealthApiBasePath()).thenReturn("/api/health");
	}

	@Test
	void getDashboardHealth_ReturnsAggregatedHealthResponse() {
		DashboardConfig.BoardType boardTypeConfig = mock(DashboardConfig.BoardType.class);
		when(dashboardConfig.getTypes()).thenReturn(Collections.singletonMap("type1", boardTypeConfig));
		HealthResponseDto boardHealth = HealthResponseDto.builder().status("UP").max(100).count(2).totalTime(200)
				.build();
		DashboardHealthServiceImpl spyService = spy(dashboardHealthService);
		doReturn(boardHealth).when(spyService).getBoardTypeHealth("type1");

		HealthResponseDto result = spyService.getDashboardHealth();

		assertEquals("UP", result.getStatus());
		assertEquals(100, result.getMax());
		assertEquals(2, result.getCount());
		assertEquals(200, result.getTotalTime());
		assertTrue(result.getComponents().containsKey("type1"));
	}

	@Test
	void getBoardTypeHealth_WithValidBoardType_ReturnsAggregatedHealthResponse() {
		DashboardConfig.BoardType boardTypeConfig = mock(DashboardConfig.BoardType.class);
		when(dashboardConfig.getTypes()).thenReturn(Collections.singletonMap("type1", boardTypeConfig));
		when(boardTypeConfig.getBoards())
				.thenReturn(Collections.singletonMap("dashboard1", mock(DashboardConfig.Board.class)));
		DashboardHealthServiceImpl spyService = spy(dashboardHealthService);
		HealthResponseDto dashboardHealth = HealthResponseDto.builder().status("UP").max(50).count(1).totalTime(50)
				.build();
		doReturn(dashboardHealth).when(spyService).getDashboardDetailHealth("type1", "dashboard1");

		HealthResponseDto result = spyService.getBoardTypeHealth("type1");

		assertEquals("UP", result.getStatus());
		assertEquals(50, result.getMax());
		assertEquals(1, result.getCount());
		assertEquals(50, result.getTotalTime());
		assertTrue(result.getComponents().containsKey("dashboard1"));
	}

	@Test
	void getDashboardDetailHealth_WithValidDashboard_ReturnsHealthResponse() {
		DashboardConfig.BoardType boardTypeConfig = mock(DashboardConfig.BoardType.class);
		DashboardConfig.Board board = mock(DashboardConfig.Board.class);
		when(dashboardConfig.getTypes()).thenReturn(Collections.singletonMap("type1", boardTypeConfig));
		when(boardTypeConfig.getBoards()).thenReturn(Collections.singletonMap("dashboard1", board));
		when(board.getApis()).thenReturn(Arrays.asList("api1", "api2"));
		ApiDetailDto api1 = ApiDetailDto.builder().status("UP").max(10).count(1).totalTime(10).build();
		ApiDetailDto api2 = ApiDetailDto.builder().status("UP").max(20).count(2).totalTime(40).build();
		when(metricsService.getApisMetrics(Arrays.asList("api1", "api2"))).thenReturn(Arrays.asList(api1, api2));

		HealthResponseDto result = dashboardHealthService.getDashboardDetailHealth("type1", "dashboard1");

		assertEquals("UP", result.getStatus());
		assertEquals(20, result.getMax());
		assertEquals(3, result.getCount());
		assertEquals(50, result.getTotalTime());
		assertNotNull(result.getDetails());
		assertEquals(2, result.getDetails().getApis().size());
	}

	@Test
	void getBoardTypeHealth_WithInvalidBoardType_ThrowsIllegalArgumentException() {
		when(dashboardConfig.getTypes()).thenReturn(Collections.emptyMap());

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
			dashboardHealthService.getBoardTypeHealth("invalidType");
		});

		assertEquals("Board type not found: invalidType", exception.getMessage());
	}

	@Test
	void getDashboardDetailHealth_WithInvalidDashboard_ThrowsIllegalArgumentException() {
		DashboardConfig.BoardType boardTypeConfig = mock(DashboardConfig.BoardType.class);
		when(dashboardConfig.getTypes()).thenReturn(Collections.singletonMap("type1", boardTypeConfig));
		when(boardTypeConfig.getBoards()).thenReturn(Collections.emptyMap());

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
			dashboardHealthService.getDashboardDetailHealth("type1", "invalidDashboard");
		});

		assertEquals("Dashboard not found: invalidDashboard in board type type1", exception.getMessage());
	}
}