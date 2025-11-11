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
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.common.model.application.DataCount;

/**
 * Test class for ExponentialSmoothingForecaster.
 */
@RunWith(MockitoJUnitRunner.class)
public class ExponentialSmoothingForecasterTest {

	private ExponentialSmoothingForecaster exponentialSmoothingForecaster;

	@Before
	public void setUp() {
		exponentialSmoothingForecaster = new ExponentialSmoothingForecaster();
	}

	@Test
	public void testGetModelType() {
		// Act
		ForecastingModel modelType = exponentialSmoothingForecaster.getModelType();

		// Assert
		assertEquals(ForecastingModel.EXPONENTIAL_SMOOTHING, modelType);
	}

	@Test
	public void testCanForecast_WithSufficientData() {
		// Arrange
		List<DataCount> historicalData = createTestDataCounts(3);

		// Act
		boolean canForecast = exponentialSmoothingForecaster.canForecast(historicalData, "kpi46");

		// Assert
		assertTrue(canForecast);
	}

	@Test
	public void testCanForecast_WithInsufficientData() {
		// Arrange
		List<DataCount> historicalData = createTestDataCounts(1);

		// Act
		boolean canForecast = exponentialSmoothingForecaster.canForecast(historicalData, "kpi46");

		// Assert
		assertTrue(!canForecast);
	}

	@Test
	public void testCanForecast_WithNullData() {
		// Act
		boolean canForecast = exponentialSmoothingForecaster.canForecast(null, "kpi46");

		// Assert
		assertTrue(!canForecast);
	}

	@Test
	public void testCanForecast_WithEmptyData() {
		// Arrange
		List<DataCount> historicalData = new ArrayList<>();

		// Act
		boolean canForecast = exponentialSmoothingForecaster.canForecast(historicalData, "kpi46");

		// Assert
		assertTrue(!canForecast);
	}

	@Test
	public void testGenerateForecast_WithStableData() {
		// Arrange - Create stable data (low volatility)
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			DataCount dc = new DataCount();
			dc.setValue(100.0);
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = exponentialSmoothingForecaster.generateForecast(historicalData, "kpi46");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		// All forecasts should be the same (simple exponential smoothing)
		Double firstForecast = (Double) forecasts.get(0).getValue();
		for (DataCount forecast : forecasts) {
			assertEquals(firstForecast, (Double) forecast.getValue(), 0.01);
			assertTrue((Double) forecast.getValue() >= 0);
		}
	}

	@Test
	public void testGenerateForecast_WithVolatileData() {
		// Arrange - Create volatile data (high coefficient of variation)
		List<DataCount> historicalData = new ArrayList<>();
		double[] values = { 10, 50, 5, 45, 15, 40, 20, 35, 25, 30 };
		for (double value : values) {
			DataCount dc = new DataCount();
			dc.setValue(value);
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = exponentialSmoothingForecaster.generateForecast(historicalData, "kpi46");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		// All forecasts should be the same
		Double firstForecast = (Double) forecasts.get(0).getValue();
		for (DataCount forecast : forecasts) {
			assertEquals(firstForecast, (Double) forecast.getValue(), 0.01);
		}
	}

	@Test
	public void testGenerateForecast_WithTrendingData() {
		// Arrange - Create trending data
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			DataCount dc = new DataCount();
			dc.setValue(10.0 + i * 5.0);
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = exponentialSmoothingForecaster.generateForecast(historicalData, "kpi46");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		for (DataCount forecast : forecasts) {
			assertNotNull(forecast.getValue());
			assertTrue((Double) forecast.getValue() >= 0);
		}
	}

	@Test
	public void testGenerateForecast_WithInsufficientData() {
		// Arrange
		List<DataCount> historicalData = createTestDataCounts(2);

		// Act
		List<DataCount> forecasts = exponentialSmoothingForecaster.generateForecast(historicalData, "kpi46");

		// Assert
		assertNotNull(forecasts);
		assertFalse(forecasts.isEmpty());
	}

	@Test
	public void testGenerateForecast_WithNullData() {
		// Act
		List<DataCount> forecasts = exponentialSmoothingForecaster.generateForecast(null, "kpi46");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty());
	}

	@Test
	public void testGenerateForecast_WithEmptyData() {
		// Arrange
		List<DataCount> historicalData = new ArrayList<>();

		// Act
		List<DataCount> forecasts = exponentialSmoothingForecaster.generateForecast(historicalData, "kpi46");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty());
	}

	@Test
	public void testGenerateForecast_WithNegativeValues() {
		// Arrange - Create data with negative values (should be clamped to 0)
		List<DataCount> historicalData = new ArrayList<>();
		double[] values = { 10, 5, 0, -5, -10, -15 };
		for (double value : values) {
			DataCount dc = new DataCount();
			dc.setValue(value);
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = exponentialSmoothingForecaster.generateForecast(historicalData, "kpi46");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
	}

	@Test
	public void testGenerateForecast_WithLargeDataSet() {
		// Arrange
		List<DataCount> historicalData = createTestDataCounts(10);

		// Act
		List<DataCount> forecasts = exponentialSmoothingForecaster.generateForecast(historicalData, "kpi46");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		assertNotNull(forecasts.get(0).getValue());
	}

	@Test
	public void testGenerateForecast_PreservesMetadata() {
		// Arrange
		List<DataCount> historicalData = createTestDataCounts(5);

		// Act
		List<DataCount> forecasts = exponentialSmoothingForecaster.generateForecast(historicalData, "kpi46");

		// Assert
		assertNotNull(forecasts);
		for (DataCount forecast : forecasts) {
			assertEquals("TestProject", forecast.getSProjectName());
			assertEquals("TestGroup", forecast.getKpiGroup());
		}
	}

	@Test
	public void testGenerateForecast_WithMixedDataTypes() {
		// Arrange - Create data with mixed numeric types
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			DataCount dc = new DataCount();
			if (i % 2 == 0) {
				dc.setValue(i * 5);
			} else {
				dc.setValue((double) i * 5);
			}
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = exponentialSmoothingForecaster.generateForecast(historicalData, "kpi46");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
	}

	@Test
	public void testGenerateForecast_WithExactlyMinimumData() {
		// Arrange - exactly 3 data points
		List<DataCount> historicalData = createTestDataCounts(3);

		// Act
		List<DataCount> forecasts = exponentialSmoothingForecaster.generateForecast(historicalData, "kpi46");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
	}

	@Test
	public void testGenerateForecast_AllZeroValues() {
		// Arrange
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			DataCount dc = new DataCount();
			dc.setValue(0.0);
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = exponentialSmoothingForecaster.generateForecast(historicalData, "kpi46");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		for (DataCount forecast : forecasts) {
			assertEquals(0.0, (Double) forecast.getValue(), 0.01);
		}
	}

	@Test
	public void testGenerateForecast_ConsistentForecastValues() {
		// Arrange - Verify that forecast is reasonable for simple exponential smoothing
		List<DataCount> historicalData = createTestDataCounts(10);

		// Act
		List<DataCount> forecasts = exponentialSmoothingForecaster.generateForecast(historicalData, "kpi46");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		Double forecastValue = (Double) forecasts.get(0).getValue();
		assertNotNull(forecastValue);
		assertTrue(forecastValue >= 0);
	}

	@Test
	public void testGenerateForecast_AlphaCalculation() {
		// Arrange - Create data to test alpha calculation based on coefficient of variation
		List<DataCount> historicalData = new ArrayList<>();
		// Low variation data (alpha should be ~0.3)
		for (int i = 0; i < 10; i++) {
			DataCount dc = new DataCount();
			dc.setValue(100.0 + i);
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = exponentialSmoothingForecaster.generateForecast(historicalData, "kpi46");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		// Forecast should be close to the last smoothed value
		Double forecast = (Double) forecasts.get(0).getValue();
		assertTrue(forecast > 0);
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
