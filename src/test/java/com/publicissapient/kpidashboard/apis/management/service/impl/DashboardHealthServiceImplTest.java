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

import com.publicissapient.kpidashboard.apis.management.configs.DashboardProperties;
import com.publicissapient.kpidashboard.apis.management.model.DashboardConfiguration;
import com.publicissapient.kpidashboard.apis.management.service.DashboardApiHealthChecker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.search.Search;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardHealthServiceImplTest {

	@Mock
	private MeterRegistry meterRegistry;

	@Mock
	private HealthEndpoint healthEndpoint;

	@Mock
	private DashboardProperties dashboardProperties;

	@Mock
	private DashboardApiHealthChecker apiHealthChecker;

	@InjectMocks
	private DashboardHealthServiceImpl dashboardHealthService;

	private DashboardConfiguration dashboard;
	private Timer globalTimer;
	private Timer apiTimer;
	private MockedStatic<ServletUriComponentsBuilder> uriComponentsBuilderMock;

	@BeforeEach
	void setUp() {
		globalTimer = mock(Timer.class, withSettings().lenient());
		Search searchMock = mock(Search.class, withSettings().lenient());
		when(searchMock.timer()).thenReturn(globalTimer);
		when(meterRegistry.find("http.server.requests")).thenReturn(searchMock);

		dashboard = new DashboardConfiguration();
		dashboard.setName("TestDashboard");
		dashboard.setApis(List.of("/api/test1", "/api/test2"));
		when(dashboardProperties.getDashboards()).thenReturn(List.of(dashboard));

		when(healthEndpoint.health()).thenReturn(Health.up().build());

		apiTimer = mock(Timer.class, withSettings().lenient());
		when(apiTimer.count()).thenReturn(50L);
		when(apiTimer.totalTime(any())).thenReturn(500.0);
		when(apiTimer.max(any())).thenReturn(250.0);
		when(apiHealthChecker.getTimer("/api/test1")).thenReturn(apiTimer);
		when(apiHealthChecker.getTimer("/api/test2")).thenReturn(apiTimer);
		when(apiHealthChecker.getApiStatus("/api/test1")).thenReturn(Status.UP);
		when(apiHealthChecker.getApiStatus("/api/test2")).thenReturn(Status.DOWN);

		ServletUriComponentsBuilder builder = mock(ServletUriComponentsBuilder.class, withSettings().lenient());
		UriComponents uriComponents = mock(UriComponents.class, withSettings().lenient());
		when(uriComponents.toUriString()).thenReturn("http://localhost");
		when(builder.build()).thenReturn(uriComponents);
		uriComponentsBuilderMock = mockStatic(ServletUriComponentsBuilder.class, Mockito.CALLS_REAL_METHODS);
		uriComponentsBuilderMock.when(ServletUriComponentsBuilder::fromCurrentContextPath).thenReturn(builder);
	}

	@AfterEach
	void tearDown() {
		if (uriComponentsBuilderMock != null) {
			uriComponentsBuilderMock.close();
		}
	}

	@Test
	void testHealthDetails() {
		Map<String, Object> details = dashboardHealthService.healthDetails();
		assertNotNull(details);
		assertTrue(details.containsKey("appHealth"));
		assertTrue(details.containsKey("metrics"));
		assertTrue(details.containsKey("dashboardStatuses"));
		assertTrue(details.containsKey("dashboards"));

		Health health = (Health) details.get("appHealth");
		assertEquals(Status.UP, health.getStatus());

		Map<String, Object> metrics = (Map<String, Object>) details.get("metrics");
		assertEquals(0L, metrics.getOrDefault(Statistic.COUNT.name().toLowerCase(), 0L));

		Map<String, Object> links = (Map<String, Object>) details.get("dashboards");
		assertTrue(links.containsKey("TestDashboard"));
		assertEquals("http://localhost/actuator/dashboard-health/TestDashboard", links.get("TestDashboard"));

		Map<String, Object> statuses = (Map<String, Object>) details.get("dashboardStatuses");
		assertEquals(Status.DOWN, statuses.get("TestDashboard"));
	}

	@Test
	void testDashboardHealth_found() {
		Map<String, Object> result = dashboardHealthService.dashboardHealth("TestDashboard");
		assertNotNull(result);
		assertEquals("TestDashboard", result.get("dashboardName"));
		assertEquals(Status.DOWN, result.get("status"));

		Object apiMetricsObj = result.get("apiMetrics");
		assertTrue(apiMetricsObj instanceof List);
		List<?> apiMetrics = (List<?>) apiMetricsObj;
		assertEquals(2, apiMetrics.size());

		@SuppressWarnings("unchecked")
		Map<String, Object> metric1 = (Map<String, Object>) apiMetrics.get(0);
		assertEquals("/api/test1", metric1.get("uri"));
		assertEquals("UP", metric1.get("status"));
		assertEquals(50L, metric1.get(Statistic.COUNT.name().toLowerCase()));
		assertEquals(500.0, metric1.get(Statistic.TOTAL_TIME.name().toLowerCase()));
		assertEquals(250.0, metric1.get(Statistic.MAX.name().toLowerCase()));
	}

	@Test
	void testDashboardHealth_notFound() {
		Exception ex = assertThrows(IllegalArgumentException.class,
				() -> dashboardHealthService.dashboardHealth("NonExistent"));
		assertTrue(ex.getMessage().contains("Dashboard not found"));
	}

	@Test
	void testGetAppMetrics() {
		Map<String, Object> metrics = dashboardHealthService.getAppMetrics();
		assertNotNull(metrics);
		assertEquals(0L, metrics.getOrDefault(Statistic.COUNT.name().toLowerCase(), 0L));
	}
}
