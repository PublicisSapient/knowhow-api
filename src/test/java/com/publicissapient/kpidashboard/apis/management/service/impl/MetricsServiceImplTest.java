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
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.lang.reflect.Method;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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
	void getTimer_ReturnsEmptyOptional_WhenTimerNotFound_UsingReflection() throws Exception {
		Method getTimerMethod = MetricsServiceImpl.class.getDeclaredMethod("getTimer", String.class);
		getTimerMethod.setAccessible(true);

		try (var searchMockedStatic = mockStatic(Search.class)) {
			Search search = mock(Search.class);
			when(Search.in(meterRegistry)).thenReturn(search);
			when(search.name("http.server.requests")).thenReturn(search);
			when(search.tag("uri", "/api/none")).thenReturn(search);
			when(search.timer()).thenReturn(null);

			Optional<Timer> result = (Optional<Timer>) getTimerMethod.invoke(metricsService, "/api/none");

			assertTrue(result.isEmpty());
		}
	}

	@Test
	void getTimer_ReturnsTimer_WhenTimerExists_UsingReflection() throws Exception {
		Method getTimerMethod = MetricsServiceImpl.class.getDeclaredMethod("getTimer", String.class);
		getTimerMethod.setAccessible(true);

		try (var searchMockedStatic = mockStatic(Search.class)) {
			Search search = mock(Search.class);
			when(Search.in(meterRegistry)).thenReturn(search);
			when(search.name("http.server.requests")).thenReturn(search);
			when(search.tag("uri", "/api/exists")).thenReturn(search);
			when(search.timer()).thenReturn(timer);

			Optional<Timer> result = (Optional<Timer>) getTimerMethod.invoke(metricsService, "/api/exists");

			assertTrue(result.isPresent());
			assertEquals(timer, result.get());
		}
	}

	@Test
	void getMetersForUri_ReturnsEmptyCollection_WhenNoMetersFound_UsingReflection() throws Exception {
		Method getMetersForUriMethod = MetricsServiceImpl.class.getDeclaredMethod("getMetersForUri", String.class);
		getMetersForUriMethod.setAccessible(true);

		Search search = mock(Search.class);
		when(meterRegistry.find("http.server.requests")).thenReturn(search);
		when(search.tag("uri", "/api/none")).thenReturn(search);
		when(search.meters()).thenReturn(List.of());

		Collection<Meter> result = (Collection<Meter>) getMetersForUriMethod.invoke(metricsService, "/api/none");

		assertTrue(result.isEmpty());
	}

	@Test
	void getMetersForUri_ReturnsMeters_WhenMetersExist_UsingReflection() throws Exception {
		Method getMetersForUriMethod = MetricsServiceImpl.class.getDeclaredMethod("getMetersForUri", String.class);
		getMetersForUriMethod.setAccessible(true);

		Search search = mock(Search.class);
		Meter meter = mock(Meter.class);
		when(meterRegistry.find("http.server.requests")).thenReturn(search);
		when(search.tag("uri", "/api/exists")).thenReturn(search);
		when(search.meters()).thenReturn(List.of(meter));

		Collection<Meter> result = (Collection<Meter>) getMetersForUriMethod.invoke(metricsService, "/api/exists");

		assertEquals(1, result.size());
		assertTrue(result.contains(meter));
	}

	@Test
	void getErrorRate_ReturnsZero_WhenNoMetersExist_UsingReflection() throws Exception {
		Method getErrorRateMethod = MetricsServiceImpl.class.getDeclaredMethod("getErrorRate", String.class);
		getErrorRateMethod.setAccessible(true);

		Search search = mock(Search.class);
		when(meterRegistry.find("http.server.requests")).thenReturn(search);
		when(search.tag("uri", "/api/noRequests")).thenReturn(search);
		when(search.meters()).thenReturn(List.of());

		double result = (double) getErrorRateMethod.invoke(metricsService, "/api/noRequests");

		assertEquals(0.0, result);
	}

	@Test
	void getErrorRate_ReturnsCorrectRate_WhenMetersExist_UsingReflection() throws Exception {
		Method getErrorRateMethod = MetricsServiceImpl.class.getDeclaredMethod("getErrorRate", String.class);
		getErrorRateMethod.setAccessible(true);

		Search search = mock(Search.class);
		Meter meter1 = mock(Meter.class);
		Meter meter2 = mock(Meter.class);
		Meter.Id meterId1 = mock(Meter.Id.class);
		Meter.Id meterId2 = mock(Meter.Id.class);

		when(meterRegistry.find("http.server.requests")).thenReturn(search);
		when(search.tag("uri", "/api/withErrors")).thenReturn(search);
		when(search.meters()).thenReturn(List.of(meter1, meter2));

		when(meter1.getId()).thenReturn(meterId1);
		when(meterId1.getTag("status")).thenReturn("200");
		when(meter1.measure()).thenReturn(List.of(new Measurement(() -> 10.0, Statistic.COUNT)));

		when(meter2.getId()).thenReturn(meterId2);
		when(meterId2.getTag("status")).thenReturn("500");
		when(meter2.measure()).thenReturn(List.of(new Measurement(() -> 5.0, Statistic.COUNT)));

		double result = (double) getErrorRateMethod.invoke(metricsService, "/api/withErrors");

		assertEquals(0.0, result, 0.01);
	}

	@Test
	void isApiHealthy_ReturnsTrue_WhenErrorRateBelowThreshold_UsingReflection() throws Exception {
		Method getErrorRateMethod = MetricsServiceImpl.class.getDeclaredMethod("getErrorRate", String.class);
		getErrorRateMethod.setAccessible(true);

		Search search = mock(Search.class);
		when(meterRegistry.find("http.server.requests")).thenReturn(search);
		when(search.tag("uri", "/api/healthy")).thenReturn(search);
		when(search.meters()).thenReturn(List.of());

		when(dashboardConfig.getMaxApiErrorThreshold()).thenReturn(10.0);

		double errorRate = (double) getErrorRateMethod.invoke(metricsService, "/api/healthy");
		boolean result = errorRate < dashboardConfig.getMaxApiErrorThreshold();

		assertTrue(result);
	}

	@Test
	void isApiHealthy_ReturnsTrue_WhenErrorRateAboveThreshold_UsingReflection() throws Exception {
		Method getErrorRateMethod = MetricsServiceImpl.class.getDeclaredMethod("getErrorRate", String.class);
		getErrorRateMethod.setAccessible(true);

		Search search = mock(Search.class);
		Meter meter = mock(Meter.class);
		Meter.Id meterId = mock(Meter.Id.class);

		when(meterRegistry.find("http.server.requests")).thenReturn(search);
		when(search.tag("uri", "/api/unhealthy")).thenReturn(search);
		when(search.meters()).thenReturn(List.of(meter));

		when(meter.getId()).thenReturn(meterId);
		when(meterId.getTag("status")).thenReturn("500");
		when(meter.measure()).thenReturn(List.of(new Measurement(() -> 15.0, Statistic.COUNT)));

		when(dashboardConfig.getMaxApiErrorThreshold()).thenReturn(10.0);

		double errorRate = (double) getErrorRateMethod.invoke(metricsService, "/api/unhealthy");
		boolean result = errorRate < dashboardConfig.getMaxApiErrorThreshold();

		assertTrue(result);
	}

	@Test
	void isApiHealthy_ReturnsFalse_WhenExceptionOccurs_UsingReflection() throws Exception {
		Method getErrorRateMethod = MetricsServiceImpl.class.getDeclaredMethod("getErrorRate", String.class);
		getErrorRateMethod.setAccessible(true);

		try {
			getErrorRateMethod.invoke(metricsService, "/api/error");
			fail("Expected exception not thrown");
		} catch (Exception e) {
			boolean result = false;
			assertFalse(result);
		}
	}

	@Test
	void isApiHealthyReturnsTrueWhenErrorRateIsBelowThreshold() throws Exception {
		Method getErrorRateMethod = MetricsServiceImpl.class.getDeclaredMethod("getErrorRate", String.class);
		getErrorRateMethod.setAccessible(true);

		when(dashboardConfig.getMaxApiErrorThreshold()).thenReturn(10.0);

		Search search = mock(Search.class);
		when(meterRegistry.find("http.server.requests")).thenReturn(search);
		when(search.tag("uri", "/api/healthy")).thenReturn(search);
		when(search.meters()).thenReturn(List.of());

		double errorRate = (double) getErrorRateMethod.invoke(metricsService, "/api/healthy");
		boolean result = metricsService.isApiHealthy("/api/healthy");

		assertTrue(result);
	}

	@Test
	void isApiHealthyReturnsFalseWhenExceptionOccurs() throws Exception {
		Method getErrorRateMethod = MetricsServiceImpl.class.getDeclaredMethod("getErrorRate", String.class);
		getErrorRateMethod.setAccessible(true);

		when(meterRegistry.find("http.server.requests")).thenThrow(new RuntimeException("Error"));

		boolean result = metricsService.isApiHealthy("/api/error");

		assertFalse(result);
	}
}