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

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.common.model.application.DataCount;

/**
 * Test class for LSTMForecaster.
 *
 * @author KnowHOW Development Team
 * @since 14.2.0
 */
class LSTMForecasterTest {

	private LSTMForecaster lstmForecaster;

	@BeforeEach
	void setUp() {
		lstmForecaster = new LSTMForecaster();
	}

	@Test
	void testGetModelType() {
		assertEquals(ForecastingModel.LSTM, lstmForecaster.getModelType());
	}

	@Test
	void testGenerateForecast_EmptyData() {
		List<DataCount> historicalData = new ArrayList<>();

		List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, "EMPTY_KPI");

		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty());
	}

	@Test
	void testGenerateForecast_InsufficientData() {
		List<DataCount> historicalData = createTestData(new double[] {10.0, 15.0, 20.0});

		List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, "INSUFFICIENT_KPI");

		assertNotNull(forecasts);
		assertTrue(forecasts.isEmpty());
	}

	@Test
	void testGenerateForecast_MinimumData() {
		List<DataCount> historicalData =
				createTestData(new double[] {10.0, 15.0, 20.0, 25.0, 30.0, 35.0});

		List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, "MINIMUM_KPI");

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());

		DataCount forecast = forecasts.get(0);
		assertNotNull(forecast.getValue());
		assertTrue(((Double) forecast.getValue()) >= 0);
		assertEquals("lstm", forecast.getForecastingModel());
	}

	@Test
	void testGenerateForecast_TrendingData() {
		List<DataCount> historicalData =
				createTestData(new double[] {10.0, 15.0, 20.0, 25.0, 30.0, 35.0, 40.0, 45.0});

		List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, "TRENDING_KPI");

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());

		DataCount forecast = forecasts.get(0);
		assertNotNull(forecast.getValue());
		assertTrue(((Double) forecast.getValue()) >= 0);
	}

	@Test
	void testGenerateForecast_VolatileData() {
		List<DataCount> historicalData =
				createTestData(new double[] {161.0, 329.43, 72.0, 312.25, 179.25, 137.5, 0.0, 0.0});

		List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, "VOLATILE_KPI");

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());

		DataCount forecast = forecasts.get(0);
		assertNotNull(forecast.getValue());
		assertTrue(((Double) forecast.getValue()) >= 0);
	}

	@Test
	void testGenerateForecast_AllZeros() {
		List<DataCount> historicalData = createTestData(new double[] {0.0, 0.0, 0.0, 0.0, 0.0, 0.0});

		List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, "ZERO_KPI");

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());

		DataCount forecast = forecasts.get(0);
		assertNotNull(forecast.getValue());
		assertTrue(((Double) forecast.getValue()) >= 0);
	}

	@Test
	void testGenerateForecast_ConstantValues() {
		List<DataCount> historicalData =
				createTestData(new double[] {25.0, 25.0, 25.0, 25.0, 25.0, 25.0});

		List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, "CONSTANT_KPI");

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());

		DataCount forecast = forecasts.get(0);
		assertNotNull(forecast.getValue());
		assertTrue(((Double) forecast.getValue()) >= 0);
	}

	@Test
	void testGenerateForecast_PreservesMetadata() {
		List<DataCount> historicalData =
				createTestData(new double[] {10.0, 15.0, 20.0, 25.0, 30.0, 35.0});

		List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, "METADATA_KPI");

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());

		DataCount forecast = forecasts.get(0);
		assertEquals("TestProject", forecast.getSProjectName());
		assertEquals("TestGroup", forecast.getKpiGroup());
		assertEquals("lstm", forecast.getForecastingModel());
	}

	private List<DataCount> createTestData(double[] values) {
		List<DataCount> dataList = new ArrayList<>();

		for (int i = 0; i < values.length; i++) {
			DataCount dataCount = new DataCount();
			dataCount.setValue(values[i]);
			dataCount.setData(String.valueOf(values[i]));
			dataCount.setSProjectName("TestProject");
			dataCount.setKpiGroup("TestGroup");
			dataList.add(dataCount);
		}

		return dataList;
	}
}
