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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardApiHealthCheckerImplTest {

	@Mock
	private MeterRegistry meterRegistry;

	@Mock
	private Search builder;

	@Mock
	private Timer timer;

	@InjectMocks
	private DashboardApiHealthCheckerImpl dashboardApiHealthChecker;

	@BeforeEach
	void setUp() {
		MockitoAnnotations.openMocks(this);
	}

	/**
	 * Test getTimer with no extra tags.
	 */
	@Test
	void testGetTimer_NoExtraTags() {
		String apiPath = "/test/api";

		when(meterRegistry.find("http.server.requests")).thenReturn(builder);
		when(builder.tags("uri", apiPath)).thenReturn(builder);
		when(builder.timer()).thenReturn(timer);

		Timer result = dashboardApiHealthChecker.getTimer(apiPath);

		assertNotNull(result);
		verify(builder).tags("uri", apiPath);
		verify(builder).timer();
	}

	/**
	 * Test getTimer with extra tags.
	 */
	@Test
	void testGetTimer_WithExtraTags() {
		String apiPath = "/test/api";
		String[] extraTags = { "status", "200" };

		when(meterRegistry.find("http.server.requests")).thenReturn(builder);
		when(builder.tags("uri", apiPath)).thenReturn(builder);
		when(builder.tags(extraTags)).thenReturn(builder);
		when(builder.timer()).thenReturn(timer);

		Timer result = dashboardApiHealthChecker.getTimer(apiPath, extraTags);

		assertNotNull(result);
		verify(builder).tags("uri", apiPath);
		verify(builder).tags(extraTags);
		verify(builder).timer();
	}

	/**
	 * Test isApiHealthy returns false when main timer is null.
	 */
	@Test
	void testIsApiHealthy_MainTimerNull() {
		String apiPath = "/test/api";

		DashboardApiHealthCheckerImpl spyChecker = Mockito.spy(dashboardApiHealthChecker);
		doReturn(null).when(spyChecker).getTimer(apiPath);

		boolean healthy = spyChecker.isApiHealthy(apiPath);

		assertFalse(healthy, "API should be unhealthy when main timer is null");
	}

	/**
	 * Test isApiHealthy returns false when main timer count is zero.
	 */
	@Test
	void testIsApiHealthy_MainTimerCountZero() {
		String apiPath = "/test/api";

		Timer mainTimer = mock(Timer.class);
		when(mainTimer.count()).thenReturn(0L);

		DashboardApiHealthCheckerImpl spyChecker = Mockito.spy(dashboardApiHealthChecker);
		doReturn(mainTimer).when(spyChecker).getTimer(apiPath);

		boolean healthy = spyChecker.isApiHealthy(apiPath);

		assertFalse(healthy, "API should be unhealthy when main timer count is zero");
	}

	/**
	 * Test getApiStatus returns Status.UP when API is healthy.
	 */
	@Test
	void testGetApiStatus_Up() {
		String apiPath = "/test/api";

		DashboardApiHealthCheckerImpl spyChecker = Mockito.spy(dashboardApiHealthChecker);
		doReturn(true).when(spyChecker).isApiHealthy(apiPath);

		Status status = spyChecker.getApiStatus(apiPath);

		assertEquals(Status.UP, status);
	}

	/**
	 * Test getApiStatus returns Status.DOWN when API is unhealthy.
	 */
	@Test
	void testGetApiStatus_Down() {
		String apiPath = "/test/api";

		DashboardApiHealthCheckerImpl spyChecker = Mockito.spy(dashboardApiHealthChecker);
		doReturn(false).when(spyChecker).isApiHealthy(apiPath);

		Status status = spyChecker.getApiStatus(apiPath);

		assertEquals(Status.DOWN, status);
	}

	@Test
	void testIsApiHealthy_ErrorRateLessThan10Percent() {
		String apiPath = "/test/api";

		Timer mainTimer = mock(Timer.class);
		Timer errorTimer = mock(Timer.class);

		DashboardApiHealthCheckerImpl spyChecker = Mockito.spy(dashboardApiHealthChecker);

		doReturn(mainTimer).when(spyChecker).getTimer(apiPath);
		doReturn(errorTimer).when(spyChecker).getTimer(apiPath, "status", "500");

		when(mainTimer.count()).thenReturn(100L);
		when(errorTimer.count()).thenReturn(5L);

		boolean healthy = spyChecker.isApiHealthy(apiPath);

		assertTrue(healthy, "API should be healthy when error rate is less than 10%");
	}

}
