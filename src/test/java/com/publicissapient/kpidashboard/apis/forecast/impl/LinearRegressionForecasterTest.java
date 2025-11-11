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
 * Test class for LinearRegressionForecaster.
 */
@RunWith(MockitoJUnitRunner.class)
public class LinearRegressionForecasterTest {

	private LinearRegressionForecaster linearRegressionForecaster;

	@Before
	public void setUp() {
		linearRegressionForecaster = new LinearRegressionForecaster();
	}

	@Test
	public void testGetModelType() {
		// Act
		ForecastingModel modelType = linearRegressionForecaster.getModelType();

		// Assert
		assertEquals(ForecastingModel.LINEAR_REGRESSION, modelType);
	}

	@Test
	public void testCanForecast_WithSufficientData() {
		// Arrange
		List<DataCount> historicalData = createTestDataCounts(3);

		// Act
		boolean canForecast = linearRegressionForecaster.canForecast(historicalData, "kpi113");

		// Assert
		assertTrue(canForecast);
	}

	@Test
	public void testCanForecast_WithInsufficientData() {
		// Arrange
		List<DataCount> historicalData = createTestDataCounts(1);

		// Act
		boolean canForecast = linearRegressionForecaster.canForecast(historicalData, "kpi113");

		// Assert
		assertTrue(!canForecast);
	}

	@Test
	public void testCanForecast_WithNullData() {
		// Act
		boolean canForecast = linearRegressionForecaster.canForecast(null, "kpi113");

		// Assert
		assertTrue(!canForecast);
	}

	@Test
	public void testCanForecast_WithEmptyData() {
		// Arrange
		List<DataCount> historicalData = new ArrayList<>();

		// Act
		boolean canForecast = linearRegressionForecaster.canForecast(historicalData, "kpi113");

		// Assert
		assertTrue(!canForecast);
	}

	@Test
	public void testGenerateForecast_WithPositiveTrend() {
		// Arrange - Create upward trending data
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			DataCount dc = new DataCount();
			dc.setValue(10.0 + i * 5.0); // Linear increase
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = linearRegressionForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		// Forecast should follow increasing trend (be higher than last historical value)
		Double forecastValue = (Double) forecasts.get(0).getValue();
		Double lastHistoricalValue = (Double) historicalData.get(historicalData.size() - 1).getValue();
		assertTrue(forecastValue >= lastHistoricalValue);
	}

	@Test
	public void testGenerateForecast_WithNegativeTrend() {
		// Arrange - Create downward trending data
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			DataCount dc = new DataCount();
			dc.setValue(100.0 - i * 5.0); // Linear decrease
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = linearRegressionForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		// Forecast should be non-negative (clamped at 0)
		Double forecastValue = (Double) forecasts.get(0).getValue();
		assertTrue(forecastValue >= 0);
	}

	@Test
	public void testGenerateForecast_WithFlatTrend() {
		// Arrange - Create flat data (no trend)
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			DataCount dc = new DataCount();
			dc.setValue(50.0);
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = linearRegressionForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		// Forecast should be approximately the same as historical values
		Double forecastValue = (Double) forecasts.get(0).getValue();
		assertEquals(50.0, forecastValue, 5.0);
	}

	@Test
	public void testGenerateForecast_WithInsufficientData() {
		// Arrange
		List<DataCount> historicalData = createTestDataCounts(2);

		// Act
		List<DataCount> forecasts = linearRegressionForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertFalse(forecasts.isEmpty());
	}

	@Test
	public void testGenerateForecast_WithNullData() {
		// Act
		List<DataCount> forecasts = linearRegressionForecaster.generateForecast(null, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty());
	}

	@Test
	public void testGenerateForecast_WithEmptyData() {
		// Arrange
		List<DataCount> historicalData = new ArrayList<>();

		// Act
		List<DataCount> forecasts = linearRegressionForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty());
	}

	@Test
	public void testGenerateForecast_WithNegativeValues() {
		// Arrange - Create data that would result in negative forecasts
		List<DataCount> historicalData = new ArrayList<>();
		double[] values = { 50, 40, 30, 20, 10, 5 };
		for (double value : values) {
			DataCount dc = new DataCount();
			dc.setValue(value);
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = linearRegressionForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		// Forecast should be clamped to 0 (since trend is negative)
		Double forecastValue = (Double) forecasts.get(0).getValue();
		assertTrue(forecastValue >= 0);
	}

	@Test
	public void testGenerateForecast_PreservesMetadata() {
		// Arrange
		List<DataCount> historicalData = createTestDataCounts(5);

		// Act
		List<DataCount> forecasts = linearRegressionForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		for (DataCount forecast : forecasts) {
			assertEquals("TestProject", forecast.getSProjectName());
			assertEquals("TestGroup", forecast.getKpiGroup());
		}
	}

	@Test
	public void testGenerateForecast_LinearProgression() {
		// Arrange - Create perfect linear data
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			DataCount dc = new DataCount();
			dc.setValue(i * 10.0); // Perfect linear: 0, 10, 20, 30, 40
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = linearRegressionForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		// Should predict next value: 50 (approximately)
		assertEquals(50.0, (Double) forecasts.get(0).getValue(), 2.0);
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
		List<DataCount> forecasts = linearRegressionForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
	}

	@Test
	public void testGenerateForecast_WithExactlyMinimumData() {
		// Arrange - exactly 3 data points
		List<DataCount> historicalData = createTestDataCounts(3);

		// Act
		List<DataCount> forecasts = linearRegressionForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
	}

	@Test
	public void testGenerateForecast_WithVolatileData() {
		// Arrange - Create volatile but trending data
		List<DataCount> historicalData = new ArrayList<>();
		double[] values = { 10, 15, 12, 18, 16, 20, 19, 25, 22, 28 };
		for (double value : values) {
			DataCount dc = new DataCount();
			dc.setValue(value);
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = linearRegressionForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		assertNotNull(forecasts.get(0).getValue());
		assertTrue((Double) forecasts.get(0).getValue() >= 0);
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
		List<DataCount> forecasts = linearRegressionForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		assertEquals(0.0, (Double) forecasts.get(0).getValue(), 0.01);
	}

	@Test
	public void testGenerateForecast_SlopeCalculation() {
		// Arrange - Data with known slope (y = 2x + 10)
		List<DataCount> historicalData = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			DataCount dc = new DataCount();
			dc.setValue(2.0 * i + 10.0);
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			historicalData.add(dc);
		}

		// Act
		List<DataCount> forecasts = linearRegressionForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		// Next value should be: 2*5+10=20
		assertEquals(20.0, (Double) forecasts.get(0).getValue(), 1.0);
	}

	@Test
	public void testGenerateForecast_ConsistentSlope() {
		// Arrange
		List<DataCount> historicalData = createTestDataCounts(10);

		// Act
		List<DataCount> forecasts = linearRegressionForecaster.generateForecast(historicalData, "kpi113");

		// Assert
		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
		// Verify forecast is reasonable (should be around the trend)
		Double forecastValue = (Double) forecasts.get(0).getValue();
		assertNotNull(forecastValue);
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
