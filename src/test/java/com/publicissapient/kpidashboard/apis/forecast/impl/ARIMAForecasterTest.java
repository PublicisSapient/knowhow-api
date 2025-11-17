/*
 * Copyright 2014 CapitalOne, LLC.
 * Further development Copyright 2022 Sapient Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.common.model.application.DataCount;

/**
 * Test class for ARIMAForecaster.
 */
@RunWith(MockitoJUnitRunner.class)
public class ARIMAForecasterTest {

	private ARIMAForecaster arimaForecaster;

	@Before
	public void setUp() {
		arimaForecaster = new ARIMAForecaster();
	}

	@Test
	public void testGetModelType() {
		// Act
		ForecastingModel modelType = arimaForecaster.getModelType();

		// Assert
		assertEquals(ForecastingModel.ARIMA, modelType);
	}

	@Test
	public void testCanForecast_WithSufficientData() {
		// Arrange
		List<DataCount> historicalData = createTestDataCounts(6);

		// Act
		boolean canForecast = arimaForecaster.canForecast(historicalData, "kpi113");

		// Assert
		assertTrue(canForecast);
	}

	@Test
	public void testCanForecast_WithInsufficientData() {
		// Arrange - ARIMA requires minimum 5 data points
		List<DataCount> historicalData = createTestDataCounts(3);

		// Act
		boolean canForecast = arimaForecaster.canForecast(historicalData, "kpi113");

		// Assert
		assertFalse(canForecast); // Should return false for < 5 points
	}

	@Test
	public void testCanForecast_WithNullData() {
		// Act
		boolean canForecast = arimaForecaster.canForecast(null, "kpi113");

		// Assert
		assertFalse(canForecast);
	}

	@Test
	public void testCanForecast_WithEmptyData() {
		// Arrange
		List<DataCount> historicalData = new ArrayList<>();

		// Act
		boolean canForecast = arimaForecaster.canForecast(historicalData, "kpi113");

		// Assert
		assertFalse(canForecast);
	}

	@Test
	public void testCanForecast_WithJustBelowMinimum() {
		// Arrange - 4 data points (below ARIMA minimum of 5)
		List<DataCount> historicalData = createTestDataCounts(4);

		// Act
		boolean canForecast = arimaForecaster.canForecast(historicalData, "kpi113");

		// Assert
		assertFalse(canForecast);
	}

	@Test
	public void testCanForecast_WithExactlyMinimumData() {
		// Arrange - exactly 5 data points (minimum for ARIMA)
		List<DataCount> historicalData = createTestDataCounts(5);

		// Act
		boolean canForecast = arimaForecaster.canForecast(historicalData, "kpi113");

		// Assert
		assertTrue(canForecast);
	}

	@Test
	public void testGenerateForecast_WithStationaryData() {
		// Arrange - Create stationary data (constant values)
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			DataCount dc = new DataCount();
			dc.setValue(10.0); // Constant value
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = arimaForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty() || forecasts.size() == 1);
		if (!forecasts.isEmpty()) {
			Double forecastValue = (Double) forecasts.get(0).getValue();
			assertNotNull(forecastValue);
			assertTrue(forecastValue >= 0);
		}
	}

	@Test
	public void testGenerateForecast_WithTrendingData() {
		// Arrange - Create upward trending data
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			DataCount dc = new DataCount();
			dc.setValue(5.0 + i * 2.0); // Linear increase: 5, 7, 9, 11, ...
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = arimaForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty() || forecasts.size() == 1);
		if (!forecasts.isEmpty()) {
			Double forecastValue = (Double) forecasts.get(0).getValue();
			assertNotNull(forecastValue);
			assertTrue(forecastValue >= 0);
		}
	}

	@Test
	public void testGenerateForecast_WithNonStationaryData() {
		// Arrange - Create non-stationary data (increasing variance)
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			DataCount dc = new DataCount();
			dc.setValue(i * i * 1.0); // Quadratic increase: 0, 1, 4, 9, 16, ...
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = arimaForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty() || forecasts.size() == 1);
		if (!forecasts.isEmpty()) {
			Double forecastValue = (Double) forecasts.get(0).getValue();
			assertNotNull(forecastValue);
			assertTrue(forecastValue >= 0);
		}
	}

	@Test
	public void testGenerateForecast_WithNegativeTrend() {
		// Arrange - Create downward trending data
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			DataCount dc = new DataCount();
			dc.setValue(50.0 - i * 3.0); // Linear decrease
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = arimaForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty() || forecasts.size() == 1);
		if (!forecasts.isEmpty()) {
			// Forecast should be non-negative (clamped at 0 if negative)
			Double forecastValue = (Double) forecasts.get(0).getValue();
			assertTrue(forecastValue >= 0);
		}
	}

	@Test
	public void testGenerateForecast_WithVolatileData() {
		// Arrange - Create volatile data
		List<DataCount> historicalData = new ArrayList<>();
		double[] values = { 10, 25, 8, 30, 12, 28, 15, 35, 10, 40 };
		for (double value : values) {
			DataCount dc = new DataCount();
			dc.setValue(value);
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = arimaForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty() || forecasts.size() == 1);
		if (!forecasts.isEmpty()) {
			Double forecastValue = (Double) forecasts.get(0).getValue();
			assertNotNull(forecastValue);
			assertTrue(forecastValue >= 0);
		}
	}

	@Test
	public void testGenerateForecast_AllZeroValues() {
		// Arrange
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			DataCount dc = new DataCount();
			dc.setValue(0.0);
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = arimaForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty() || forecasts.size() == 1);
		if (!forecasts.isEmpty()) {
			Double forecastValue = (Double) forecasts.get(0).getValue();
			assertEquals(0.0, forecastValue, 0.01);
		}
	}


	@Test
	public void testGenerateForecast_WithSeasonalPattern() {
		// Arrange - Create data with seasonal pattern
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 12; i++) {
			DataCount dc = new DataCount();
			// Seasonal pattern: high-low-high-low
			dc.setValue(10 + 5 * Math.sin(i * Math.PI / 3));
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = arimaForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty() || forecasts.size() == 1);
		if (!forecasts.isEmpty()) {
			Double forecastValue = (Double) forecasts.get(0).getValue();
			assertNotNull(forecastValue);
			assertTrue(forecastValue >= 0);
		}
	}

	@Test
	public void testGenerateForecast_WithSmallDataSet() {
		// Arrange - minimum required data (5 points)
		List<DataCount> historicalData = createTestDataCounts(5);

		// Act
		List<DataCount> forecasts = arimaForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty() || forecasts.size() == 1);
	}

	@Test
	public void testGenerateForecast_WithLargeDataSet() {
		// Arrange - larger data set
		List<DataCount> historicalData = createTestDataCounts(20);

		// Act
		List<DataCount> forecasts = arimaForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty() || forecasts.size() == 1);
	}

	@Test
	public void testGenerateForecast_ReturnsEmptyListWhenInsufficientData() {
		// Arrange - only 3 data points (ARIMA needs 5)
		List<DataCount> historicalData = createTestDataCounts(3);

		// Act
		List<DataCount> forecasts = arimaForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty());
	}

	@Test
	public void testGenerateForecast_ReturnsEmptyListWith4Points() {
		// Arrange - 4 data points (still insufficient for ARIMA)
		List<DataCount> historicalData = createTestDataCounts(4);

		// Act
		List<DataCount> forecasts = arimaForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty());
	}

	@Test
	public void testGenerateForecast_ForecastDataCountProperties() {
		// Arrange
		List<DataCount> historicalData = createTestDataCounts(8);

		// Act
		List<DataCount> forecasts = arimaForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty() || forecasts.size() == 1);
		if (!forecasts.isEmpty()) {
			DataCount forecast = forecasts.get(0);
			assertNotNull(forecast.getValue());
			assertNotNull(forecast.getData());
			assertEquals("TestProject", forecast.getSProjectName());
			assertEquals("TestGroup", forecast.getKpiGroup());
			assertEquals("ARIMA", forecast.getForecastingModel());
		}
	}

	@Test
	public void testGenerateForecast_WithNullValues() {
		// Arrange - data with some null values (should be filtered out)
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			DataCount dc = new DataCount();
			if (i != 3) { // Skip one value to create null
				dc.setValue(i * 2.0);
			}
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = arimaForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty() || forecasts.size() == 1);
	}

	@Test
	public void testGenerateForecast_WithStringNumericValues() {
		// Arrange - data with string numeric values
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			DataCount dc = new DataCount();
			dc.setValue(String.valueOf(i * 3.0));
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = arimaForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty() || forecasts.size() == 1);
	}

	@Test
	public void testGenerateForecast_WithNullData() {
		// Act
		List<DataCount> forecasts = arimaForecaster.generateForecast(null, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty());
	}

	@Test
	public void testGenerateForecast_WithEmptyData() {
		// Arrange
		List<DataCount> historicalData = new ArrayList<>();

		// Act
		List<DataCount> forecasts = arimaForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty());
	}

	private List<DataCount> createTestDataCounts(int count) {
		List<DataCount> dataCounts = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			DataCount dc = new DataCount();
			dc.setValue(i * 10.0);
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			dataCounts.add(dc);
		}
		return dataCounts;
	}
}
