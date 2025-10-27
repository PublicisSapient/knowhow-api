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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;

import com.publicissapient.kpidashboard.apis.config.DashboardConfig;
import com.publicissapient.kpidashboard.apis.model.ApiDetailDto;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.search.Search;

@ExtendWith(MockitoExtension.class)
public class MetricsServiceImplTest {

	private static final String TEST_API_PATH = "/api/test";
	private static final String METRIC_NAME = "http.server.requests";
	private static final String URI_TAG = "uri";
	private static final String STATUS_TAG = "status";
	private static final String STATUS_UP = Status.UP.getCode();
	private static final String STATUS_DOWN = Status.DOWN.getCode();
	private static final double ERROR_THRESHOLD = 5.0;

	@Mock private MeterRegistry meterRegistry;

	@Mock private DashboardConfig dashboardConfig;

	@Mock private MetricsEndpoint metricsEndpoint;

	@Mock private Search search;

	@InjectMocks private MetricsServiceImpl metricsService;

	@BeforeEach
	public void setup() {
		Mockito.lenient().when(dashboardConfig.getMaxApiErrorThreshold()).thenReturn(ERROR_THRESHOLD);
	}

	@Test
	@DisplayName("Test getApiMetrics with valid metrics")
	public void testGetApiMetricsWithValidMetrics() {
		// Setup MetricDescriptor with measurements
		MetricsEndpoint.MetricDescriptor metricDescriptor = mockMetricDescriptor(10.0, 100.0, 500.0);
		when(metricsEndpoint.metric(eq(METRIC_NAME), any())).thenReturn(metricDescriptor);

		// Setup meters for error rate calculation
		Collection<Meter> meters = createMeters(false);
		when(meterRegistry.find(METRIC_NAME)).thenReturn(search);
		when(search.tag(URI_TAG, TEST_API_PATH)).thenReturn(search);
		when(search.meters()).thenReturn(meters);

		// Execute
		ApiDetailDto result = metricsService.getApiMetrics(TEST_API_PATH);

		// Verify
		assertNotNull(result);
		assertEquals(TEST_API_PATH, result.getName());
		assertEquals(STATUS_UP, result.getStatus());
		assertEquals(10.0, result.getMax());
		assertEquals(100, result.getCount());
		assertEquals(500.0, result.getTotalTime());
		assertEquals(0.0, result.getErrorRate());
		assertEquals(ERROR_THRESHOLD, result.getErrorThreshold());

		// Verify interactions
		verify(metricsEndpoint).metric(eq(METRIC_NAME), any());
		verify(meterRegistry).find(METRIC_NAME);
	}

	@Test
	@DisplayName("Test getApiMetrics with null MetricDescriptor")
	public void testGetApiMetricsWithNullMetricDescriptor() {
		// Setup
		when(metricsEndpoint.metric(eq(METRIC_NAME), any())).thenReturn(null);

		// Execute
		ApiDetailDto result = metricsService.getApiMetrics(TEST_API_PATH);

		// Verify
		assertNotNull(result);
		assertEquals(TEST_API_PATH, result.getName());
		assertEquals(STATUS_UP, result.getStatus());
		assertEquals(0.0, result.getMax());
		assertEquals(0, result.getCount());
		assertEquals(0.0, result.getTotalTime());
		assertEquals(0.0, result.getErrorRate());
		assertEquals(ERROR_THRESHOLD, result.getErrorThreshold());
	}

	@Test
	@DisplayName("Test getApiMetrics with empty measurements")
	public void testGetApiMetricsWithEmptyMeasurements() {
		// Setup
		MetricsEndpoint.MetricDescriptor metricDescriptor = mockEmptyMetricDescriptor();
		when(metricsEndpoint.metric(eq(METRIC_NAME), any())).thenReturn(metricDescriptor);

		// Execute
		ApiDetailDto result = metricsService.getApiMetrics(TEST_API_PATH);

		// Verify
		assertNotNull(result);
		assertEquals(TEST_API_PATH, result.getName());
		assertEquals(STATUS_UP, result.getStatus());
		assertEquals(0.0, result.getMax());
		assertEquals(0, result.getCount());
		assertEquals(0.0, result.getTotalTime());
		assertEquals(0.0, result.getErrorRate());
		assertEquals(ERROR_THRESHOLD, result.getErrorThreshold());
	}

	@Test
	@DisplayName("Test getApiMetrics with exception")
	public void testGetApiMetricsWithException() {
		// Setup
		when(metricsEndpoint.metric(eq(METRIC_NAME), any()))
				.thenThrow(new RuntimeException("Test exception"));

		// Execute
		ApiDetailDto result = metricsService.getApiMetrics(TEST_API_PATH);

		// Verify
		assertNotNull(result);
		assertEquals(TEST_API_PATH, result.getName());
		assertEquals(STATUS_DOWN, result.getStatus());
		assertEquals(0.0, result.getMax());
		assertEquals(0, result.getCount());
		assertEquals(0.0, result.getTotalTime());
		assertEquals(0.0, result.getErrorRate());
		assertEquals(ERROR_THRESHOLD, result.getErrorThreshold());
	}

	@Test
	@DisplayName("Test getApiMetrics with high error rate")
	public void testGetApiMetricsWithHighErrorRate() {
		// Setup MetricDescriptor with measurements
		MetricsEndpoint.MetricDescriptor metricDescriptor = mockMetricDescriptor(10.0, 100.0, 500.0);
		when(metricsEndpoint.metric(eq(METRIC_NAME), any())).thenReturn(metricDescriptor);

		// Setup meters for error rate calculation with high error rate
		Collection<Meter> meters = createMeters(true);
		when(meterRegistry.find(METRIC_NAME)).thenReturn(search);
		when(search.tag(URI_TAG, TEST_API_PATH)).thenReturn(search);
		when(search.meters()).thenReturn(meters);

		// Execute
		ApiDetailDto result = metricsService.getApiMetrics(TEST_API_PATH);

		// Verify
		assertNotNull(result);
		assertEquals(TEST_API_PATH, result.getName());
		assertEquals(STATUS_DOWN, result.getStatus());
		assertEquals(10.0, result.getMax());
		assertEquals(100, result.getCount());
		assertEquals(500.0, result.getTotalTime());
		assertTrue(result.getErrorRate() > ERROR_THRESHOLD);
		assertEquals(ERROR_THRESHOLD, result.getErrorThreshold());
	}

	@Test
	@DisplayName("Test getApisMetrics with multiple API paths")
	public void testGetApisMetrics() {
		// Setup
		List<String> apiPaths = Arrays.asList("/api/test1", "/api/test2", "/api/test3");

		// Setup MetricDescriptor with measurements
		MetricsEndpoint.MetricDescriptor metricDescriptor = mockMetricDescriptor(10.0, 100.0, 500.0);
		when(metricsEndpoint.metric(eq(METRIC_NAME), any())).thenReturn(metricDescriptor);

		// Setup meters for error rate calculation
		Collection<Meter> meters = createMeters(false);
		when(meterRegistry.find(METRIC_NAME)).thenReturn(search);
		when(search.tag(eq(URI_TAG), anyString())).thenReturn(search);
		when(search.meters()).thenReturn(meters);

		// Execute
		List<ApiDetailDto> results = metricsService.getApisMetrics(apiPaths);

		// Verify
		assertNotNull(results);
		assertEquals(3, results.size());

		// Verify each result
		for (int i = 0; i < results.size(); i++) {
			ApiDetailDto result = results.get(i);
			assertEquals(apiPaths.get(i), result.getName());
			assertEquals(STATUS_UP, result.getStatus());
			assertEquals(10.0, result.getMax());
			assertEquals(100, result.getCount());
			assertEquals(500.0, result.getTotalTime());
			assertEquals(0.0, result.getErrorRate());
			assertEquals(ERROR_THRESHOLD, result.getErrorThreshold());
		}

		// Verify interactions
		verify(metricsEndpoint, times(3)).metric(eq(METRIC_NAME), any());
	}

	@Test
	@DisplayName("Test getApisMetrics when total request zero")
	public void testGetApisMetricsTotalRequestZero() {
		// Setup
		List<String> apiPaths = Arrays.asList("/api/test1", "/api/test2", "/api/test3");

		// Setup MetricDescriptor with measurements
		MetricsEndpoint.MetricDescriptor metricDescriptor = mockMetricDescriptor(10.0, 100.0, 500.0);
		when(metricsEndpoint.metric(eq(METRIC_NAME), any())).thenReturn(metricDescriptor);

		// Setup meters for error rate calculation
		Collection<Meter> meters = new ArrayList<>();
		when(meterRegistry.find(METRIC_NAME)).thenReturn(search);
		when(search.tag(eq(URI_TAG), anyString())).thenReturn(search);
		when(search.meters()).thenReturn(meters);

		// Execute
		List<ApiDetailDto> results = metricsService.getApisMetrics(apiPaths);

		// Verify
		assertNotNull(results);
		assertEquals(3, results.size());

		// Verify interactions
		verify(metricsEndpoint, times(3)).metric(eq(METRIC_NAME), any());
	}

	@Test
	@DisplayName("Test isApiHealthy with healthy API")
	public void testIsApiHealthyWithHealthyApi() {
		// Setup meters for error rate calculation
		Collection<Meter> meters = createMeters(false);
		when(meterRegistry.find(METRIC_NAME)).thenReturn(search);
		when(search.tag(URI_TAG, TEST_API_PATH)).thenReturn(search);
		when(search.meters()).thenReturn(meters);

		// Execute
		boolean result = metricsService.isApiHealthy(TEST_API_PATH);

		// Verify
		assertTrue(result);
	}

	@Test
	@DisplayName("Test isApiHealthy Exception Handling")
	public void testIsApiHealthyExceptionHandling() {
		when(meterRegistry.find(METRIC_NAME)).thenReturn(search);
		when(search.tag(URI_TAG, TEST_API_PATH)).thenReturn(search);
		when(search.meters()).thenThrow(new RuntimeException("Test exception"));

		// Execute
		boolean result = metricsService.isApiHealthy(TEST_API_PATH);

		// Verify
		assertFalse(result);
	}

	@Test
	@DisplayName("Test isApiHealthy with unhealthy API")
	public void testIsApiHealthyWithUnhealthyApi() {
		Collection<Meter> meters = createMeters(true);
		when(meterRegistry.find(METRIC_NAME)).thenReturn(search);
		when(search.tag(URI_TAG, TEST_API_PATH)).thenReturn(search);
		when(search.meters()).thenReturn(meters);

		// Execute
		boolean result = metricsService.isApiHealthy(TEST_API_PATH);

		// Verify
		assertFalse(result);
	}

	private MetricsEndpoint.MetricDescriptor mockMetricDescriptor(
			double max, double count, double totalTime) {
		List<MetricsEndpoint.Sample> measurements = new ArrayList<>();

		MetricsEndpoint.Sample maxSample = mock(MetricsEndpoint.Sample.class);
		when(maxSample.getStatistic()).thenReturn(Statistic.MAX);
		when(maxSample.getValue()).thenReturn(max);
		measurements.add(maxSample);

		MetricsEndpoint.Sample countSample = mock(MetricsEndpoint.Sample.class);
		when(countSample.getStatistic()).thenReturn(Statistic.COUNT);
		when(countSample.getValue()).thenReturn(count);
		measurements.add(countSample);

		MetricsEndpoint.Sample totalTimeSample = mock(MetricsEndpoint.Sample.class);
		when(totalTimeSample.getStatistic()).thenReturn(Statistic.TOTAL_TIME);
		when(totalTimeSample.getValue()).thenReturn(totalTime);
		measurements.add(totalTimeSample);

		MetricsEndpoint.MetricDescriptor descriptor = mock(MetricsEndpoint.MetricDescriptor.class);
		when(descriptor.getMeasurements()).thenReturn(measurements);

		return descriptor;
	}

	private MetricsEndpoint.MetricDescriptor mockEmptyMetricDescriptor() {
		MetricsEndpoint.MetricDescriptor descriptor = mock(MetricsEndpoint.MetricDescriptor.class);
		when(descriptor.getMeasurements()).thenReturn(Collections.emptyList());
		return descriptor;
	}

	private static Collection<Meter> createMeters(boolean highErrorRate) {
		List<Meter> meters = new ArrayList<>();

		// Add a 200 status meter
		Meter successMeter = createMeter("200", 90);
		meters.add(successMeter);

		// Add a 404 status meter
		Meter notFoundMeter = createMeter("404", 10);
		meters.add(notFoundMeter);

		if (highErrorRate) {
			// Add a 500 status meter with high count for high error rate
			Meter errorMeter = createMeter("500", 30);
			meters.add(errorMeter);
		}

		return meters;
	}

	private static Meter createMeter(String status, double count) {
		// Create meter ID with status tag
		Id meterId = mock(Id.class);
		when(meterId.getTag(STATUS_TAG)).thenReturn(status);

		// Create measurement with COUNT statistic
		Measurement measurement = mock(Measurement.class);
		when(measurement.getStatistic()).thenReturn(Statistic.COUNT);
		when(measurement.getValue()).thenReturn(count);

		// Create meter with ID and measurement
		Meter meter = mock(Meter.class);
		when(meter.getId()).thenReturn(meterId);
		when(meter.measure()).thenReturn(Collections.singletonList(measurement));

		return meter;
	}

	@Test
	@DisplayName("Test getApiMetrics with null API path")
	public void testGetApiMetricsWithNullApiPath() {
		// Execute
		ApiDetailDto result = metricsService.getApiMetrics(null);

		// Verify
		assertNotNull(result);
		assertNull(result.getName());
		assertEquals(STATUS_UP, result.getStatus());
	}
}
