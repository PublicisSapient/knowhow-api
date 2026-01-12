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

package com.publicissapient.kpidashboard.apis.forecast.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.PullRequestsValue;

@RunWith(MockitoJUnitRunner.class)
public class ThetaMethodForecasterTest {

	@InjectMocks private ThetaMethodForecaster thetaMethodForecaster;

	private String kpiId;

	@Before
	public void setUp() {
		kpiId = "kpi113";
	}

	@Test
	public void testGetModelType() {
		ForecastingModel result = thetaMethodForecaster.getModelType();
		assertEquals(ForecastingModel.THETA_METHOD, result);
	}

	@Test
	public void testGenerateForecast_WithValidData() {
		List<DataCount> historicalData = createDataCounts(5);

		List<DataCount> forecasts = thetaMethodForecaster.generateForecast(historicalData, kpiId);

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		assertNotNull(forecasts.get(0).getValue());
	}

	@Test
	public void testGenerateForecast_WithMinimumData() {
		List<DataCount> historicalData = createDataCounts(3);

		List<DataCount> forecasts = thetaMethodForecaster.generateForecast(historicalData, kpiId);

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
	}

	@Test
	public void testGenerateForecast_WithInsufficientData() {
		List<DataCount> historicalData = createDataCounts(2);

		List<DataCount> forecasts = thetaMethodForecaster.generateForecast(historicalData, kpiId);

		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty());
	}

	@Test
	public void testGenerateForecast_WithNullData() {
		List<DataCount> forecasts = thetaMethodForecaster.generateForecast(null, kpiId);

		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty());
	}

	@Test
	public void testGenerateForecast_WithEmptyData() {
		List<DataCount> historicalData = new ArrayList<>();

		List<DataCount> forecasts = thetaMethodForecaster.generateForecast(historicalData, kpiId);

		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty());
	}

	@Test
	public void testGenerateForecast_WithBubblePoints() {
		List<DataCount> historicalData = createDataCountsWithBubblePoints(5);

		List<DataCount> forecasts = thetaMethodForecaster.generateForecast(historicalData, kpiId);

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
	}

	@Test
	public void testCanForecast_WithValidData() {
		List<DataCount> historicalData = createDataCounts(5);

		boolean result = thetaMethodForecaster.canForecast(historicalData, kpiId);

		assertTrue(result);
	}

	@Test
	public void testCanForecast_WithInsufficientData() {
		List<DataCount> historicalData = createDataCounts(2);

		boolean result = thetaMethodForecaster.canForecast(historicalData, kpiId);

		assertFalse(result);
	}

	@Test
	public void testCanForecast_WithNullData() {
		boolean result = thetaMethodForecaster.canForecast(null, kpiId);

		assertFalse(result);
	}

	@Test
	public void testCanForecast_WithEmptyData() {
		List<DataCount> historicalData = new ArrayList<>();

		boolean result = thetaMethodForecaster.canForecast(historicalData, kpiId);

		assertFalse(result);
	}

	@Test
	public void testExtractValues_WithDirectValues() {
		List<DataCount> dataCounts = createDataCounts(5);

		List<Double> values = thetaMethodForecaster.extractValues(dataCounts);

		assertNotNull(values);
		assertEquals(5, values.size());
	}

	@Test
	public void testExtractValues_WithBubblePoints() {
		List<DataCount> dataCounts = createDataCountsWithBubblePoints(3);

		List<Double> values = thetaMethodForecaster.extractValues(dataCounts);

		assertNotNull(values);
		assertTrue(values.size() >= 3);
	}

	@Test
	public void testExtractValues_WithMixedData() {
		List<DataCount> dataCounts = new ArrayList<>();
		dataCounts.add(createDataCountWithValue(100.0));
		dataCounts.add(createDataCountWithBubblePoints(2));
		dataCounts.add(createDataCountWithValue(120.0));

		List<Double> values = thetaMethodForecaster.extractValues(dataCounts);

		assertNotNull(values);
		assertTrue(values.size() >= 4);
	}

	@Test
	public void testExtractValues_WithNullValues() {
		List<DataCount> dataCounts = new ArrayList<>();
		DataCount dc = new DataCount();
		dc.setValue(null);
		dataCounts.add(dc);

		List<Double> values = thetaMethodForecaster.extractValues(dataCounts);

		assertNotNull(values);
		assertTrue(values.isEmpty());
	}

	@Test
	public void testGenerateForecast_WithIncreasingTrend() {
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			historicalData.add(createDataCountWithValue(100.0 + i * 10));
		}

		List<DataCount> forecasts = thetaMethodForecaster.generateForecast(historicalData, kpiId);

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
	}

	@Test
	public void testGenerateForecast_WithDecreasingTrend() {
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			historicalData.add(createDataCountWithValue(150.0 - i * 10));
		}

		List<DataCount> forecasts = thetaMethodForecaster.generateForecast(historicalData, kpiId);

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
	}

	@Test
	public void testGenerateForecast_WithFlatTrend() {
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			historicalData.add(createDataCountWithValue(100.0));
		}

		List<DataCount> forecasts = thetaMethodForecaster.generateForecast(historicalData, kpiId);

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
	}

	private List<DataCount> createDataCounts(int count) {
		List<DataCount> dataCounts = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			dataCounts.add(createDataCountWithValue(100.0 + i * 5));
		}
		return dataCounts;
	}

	private DataCount createDataCountWithValue(Double value) {
		DataCount dc = new DataCount();
		dc.setValue(value);
		dc.setSProjectName("TestProject");
		dc.setKpiGroup("TestGroup");
		return dc;
	}

	private List<DataCount> createDataCountsWithBubblePoints(int count) {
		List<DataCount> dataCounts = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			dataCounts.add(createDataCountWithBubblePoints(2));
		}
		return dataCounts;
	}

	private DataCount createDataCountWithBubblePoints(int bubbleCount) {
		DataCount dc = new DataCount();
		List<PullRequestsValue> bubblePoints = new ArrayList<>();
		for (int i = 0; i < bubbleCount; i++) {
			PullRequestsValue bp = new PullRequestsValue();
			bp.setSize(String.valueOf(50.0 + i * 10));
			bubblePoints.add(bp);
		}
		dc.setBubblePoints(bubblePoints);
		dc.setSProjectName("TestProject");
		dc.setKpiGroup("TestGroup");
		return dc;
	}
}
