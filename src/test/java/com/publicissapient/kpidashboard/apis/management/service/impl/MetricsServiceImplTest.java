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

import com.publicissapient.kpidashboard.apis.management.configs.DashboardConfig;
import com.publicissapient.kpidashboard.apis.management.dto.ApiDetailDto;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MetricsServiceImplTest {

	@InjectMocks
	private MetricsServiceImpl metricsService;

	@Mock
	private MeterRegistry meterRegistry;

	@Mock
	private DashboardConfig dashboardConfig;

	@Mock
	private Timer timer;

	@Test
	void getApiMetrics_ReturnsDefaultApiDetailDto_WhenTimerNotFound() {
		try (var searchMockedStatic = mockStatic(Search.class)) {
			Search search = mock(Search.class);
			when(Search.in(meterRegistry)).thenReturn(search);
			when(search.name("http.server.requests")).thenReturn(search);
			when(search.tag("uri", "/api/none")).thenReturn(search);
			when(search.timer()).thenReturn(null);

			ApiDetailDto result = metricsService.getApiMetrics("/api/none");

			assertEquals("/api/none", result.getName());
			assertEquals("UP", result.getStatus());
			assertEquals(0, result.getMax());
			assertEquals(0, result.getCount());
			assertEquals(0, result.getTotalTime());
		}
	}

	@Test
	void getApiMetrics_ReturnsDownStatus_WhenExceptionThrown() {
		try (var searchMockedStatic = mockStatic(Search.class)) {
			Search search = mock(Search.class);
			when(Search.in(meterRegistry)).thenThrow(new RuntimeException("Registry error"));

			ApiDetailDto result = metricsService.getApiMetrics("/api/error");

			assertEquals("/api/error", result.getName());
			assertEquals("DOWN", result.getStatus());
			assertEquals(0, result.getMax());
			assertEquals(0, result.getCount());
			assertEquals(0, result.getTotalTime());
		}
	}

	@Test
	void getApisMetrics_ReturnsListOfApiDetailDto_ForMultipleApis() {
		MetricsServiceImpl spyService = spy(metricsService);
		ApiDetailDto dto1 = ApiDetailDto.builder().name("/api/one").status("UP").max(1).count(1).totalTime(1).build();
		ApiDetailDto dto2 = ApiDetailDto.builder().name("/api/two").status("DOWN").max(2).count(2).totalTime(2).build();
		doReturn(dto1).when(spyService).getApiMetrics("/api/one");
		doReturn(dto2).when(spyService).getApiMetrics("/api/two");

		List<ApiDetailDto> result = spyService.getApisMetrics(List.of("/api/one", "/api/two"));

		assertEquals(2, result.size());
		assertEquals("UP", result.get(0).getStatus());
		assertEquals("DOWN", result.get(1).getStatus());
	}

	@Test
	void isApiHealthy_ReturnsTrue_WhenErrorRateIsZero() {
		try (var searchMockedStatic = mockStatic(Search.class)) {
			Search search = mock(Search.class);
			when(Search.in(meterRegistry)).thenReturn(search);
			when(search.name("http.server.requests")).thenReturn(search);
			when(search.tag("uri", "/api/none")).thenReturn(search);
			when(search.timer()).thenReturn(null);
			when(dashboardConfig.getMaxApiErrorThreshold()).thenReturn(0.1);

			boolean result = metricsService.isApiHealthy("/api/none");

			assertTrue(result);
		}
	}

	@Test
	void isApiHealthy_ReturnsTrue_WhenTotalRequestsIsZero() {
		try (var searchMockedStatic = mockStatic(Search.class)) {
			Search search = mock(Search.class);
			when(Search.in(meterRegistry)).thenReturn(search);
			when(search.name("http.server.requests")).thenReturn(search);
			when(search.tag("uri", "/api/zero")).thenReturn(search);
			when(search.timer()).thenReturn(timer);
			when(timer.count()).thenReturn(0L);
			when(dashboardConfig.getMaxApiErrorThreshold()).thenReturn(0.1);

			boolean result = metricsService.isApiHealthy("/api/zero");

			assertTrue(result);
		}
	}

	@Test
	void isApiHealthy_ReturnsFalse_WhenErrorRateAboveThreshold() {
		try (var searchMockedStatic = mockStatic(Search.class)) {
			Search search = mock(Search.class);
			when(Search.in(meterRegistry)).thenReturn(search);
			when(search.name("http.server.requests")).thenReturn(search);
			when(search.tag("uri", "/api/withErrors")).thenReturn(search);
			when(search.timer()).thenReturn(timer);
			when(timer.count()).thenReturn(10L);
			var meterId = mock(io.micrometer.core.instrument.Meter.Id.class);
			io.micrometer.core.instrument.Tag errorTag = io.micrometer.core.instrument.Tag.of("error", "500");
			io.micrometer.core.instrument.Tag okTag = io.micrometer.core.instrument.Tag.of("error", "none");
			when(meterId.getTags()).thenReturn(List.of(errorTag, okTag, errorTag));
			when(timer.getId()).thenReturn(meterId);
			when(dashboardConfig.getMaxApiErrorThreshold()).thenReturn(0.1);

			boolean result = metricsService.isApiHealthy("/api/withErrors");

			assertFalse(result);
		}
	}

	@Test
	void isApiHealthy_ReturnsFalse_WhenExceptionIsThrown() {
		try (var searchMockedStatic = mockStatic(Search.class)) {
			Search search = mock(Search.class);
			when(Search.in(meterRegistry)).thenReturn(search);
			when(search.name("http.server.requests")).thenReturn(search);
			when(search.tag("uri", "/api/exception")).thenReturn(search);
			when(search.timer()).thenReturn(timer);
			when(timer.count()).thenReturn(5L);
			when(timer.getId()).thenThrow(new RuntimeException("Timer error"));

			boolean result = metricsService.isApiHealthy("/api/exception");

			assertFalse(result);
		}
	}

	@Test
	void getApiMetrics_ReturnsDownStatus_WhenErrorRateAboveThreshold() {
		try (var searchMockedStatic = mockStatic(Search.class)) {
			Search search = mock(Search.class);
			when(Search.in(meterRegistry)).thenReturn(search);
			when(search.name("http.server.requests")).thenReturn(search);
			when(search.tag("uri", "/api/unhealthy")).thenReturn(search);
			when(search.timer()).thenReturn(timer);
			when(timer.max(TimeUnit.SECONDS)).thenReturn(5.0);
			when(timer.count()).thenReturn(10L);
			when(timer.totalTime(TimeUnit.SECONDS)).thenReturn(50.0);
			var meterId = mock(io.micrometer.core.instrument.Meter.Id.class);
			io.micrometer.core.instrument.Tag errorTag = io.micrometer.core.instrument.Tag.of("error", "500");
			io.micrometer.core.instrument.Tag okTag = io.micrometer.core.instrument.Tag.of("error", "none");
			when(meterId.getTags()).thenReturn(List.of(errorTag, okTag, errorTag));
			when(timer.getId()).thenReturn(meterId);
			when(dashboardConfig.getMaxApiErrorThreshold()).thenReturn(0.1);

			ApiDetailDto result = metricsService.getApiMetrics("/api/unhealthy");

			assertEquals("/api/unhealthy", result.getName());
			assertEquals("DOWN", result.getStatus());
			assertEquals(5.0, result.getMax());
			assertEquals(10, result.getCount());
			assertEquals(50.0, result.getTotalTime());
		}
	}
}