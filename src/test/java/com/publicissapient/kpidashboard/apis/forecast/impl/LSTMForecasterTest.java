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

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.common.model.application.DataCount;

@RunWith(MockitoJUnitRunner.class)
public class LSTMForecasterTest {

	private LSTMForecaster lstmForecaster;

	private List<DataCount> historicalData;
	private String kpiId;

	@Before
	public void setUp() {
		lstmForecaster = new LSTMForecaster();
		kpiId = "TEST_KPI_001";
		historicalData = createTestData();
	}

	@Test
	public void testGetModelType() {
		assertEquals(ForecastingModel.LSTM, lstmForecaster.getModelType());
	}

	@Test
	public void testCanForecast_WithSufficientData() {
		assertTrue(lstmForecaster.canForecast(historicalData, kpiId));
	}

	@Test
	public void testCanForecast_WithInsufficientData() {
		List<DataCount> insufficientData = createTestData().subList(0, 4);
		assertFalse(lstmForecaster.canForecast(insufficientData, kpiId));
	}

	@Test
	public void testCanForecast_WithNullData() {
		assertFalse(lstmForecaster.canForecast(null, kpiId));
	}

	@Test
	public void testCanForecast_WithEmptyData() {
		assertFalse(lstmForecaster.canForecast(new ArrayList<>(), kpiId));
	}

	@Test
	public void testGenerateForecast_WithValidData() {
		List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, kpiId);

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());

		DataCount forecast = forecasts.get(0);
		assertNotNull(forecast);
		assertTrue((Double) forecast.getValue() >= 0);
		if (forecast.getHoverValue() != null) {
			assertEquals("LSTM", forecast.getHoverValue().get("Model"));
		}
	}

	@Test
	public void testGenerateForecast_WithInsufficientData() {
		List<DataCount> insufficientData = createTestData().subList(0, 4);
		List<DataCount> forecasts = lstmForecaster.generateForecast(insufficientData, kpiId);

		assertTrue(forecasts.isEmpty());
	}

	@Test
	public void testGenerateForecast_WithIdenticalValues() {
		List<DataCount> identicalData = createIdenticalValueData();
		List<DataCount> forecasts = lstmForecaster.generateForecast(identicalData, kpiId);

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());

		DataCount forecast = forecasts.get(0);
		assertEquals(10.0, (Double) forecast.getValue(), 5.0);
	}

	@Test
	public void testGenerateForecast_WithTrendingData() {
		List<DataCount> trendingData = createTrendingData();
		List<DataCount> forecasts = lstmForecaster.generateForecast(trendingData, kpiId);

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());

		DataCount forecast = forecasts.get(0);
		assertTrue((Double) forecast.getValue() > 0);
	}

	@Test
	public void testGenerateForecast_WithLargeValues() {
		List<DataCount> largeValueData = createLargeValueData();
		List<DataCount> forecasts = lstmForecaster.generateForecast(largeValueData, kpiId);

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());

		DataCount forecast = forecasts.get(0);
		assertTrue((Double) forecast.getValue() > 0);
	}

	@Test
	public void testGenerateForecast_WithMinimalData() {
		List<DataCount> minimalData = createTestData().subList(0, 6);
		List<DataCount> forecasts = lstmForecaster.generateForecast(minimalData, kpiId);

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());
	}

	@Test
	public void testGenerateForecast_WithZeroValues() {
		List<DataCount> zeroData = createZeroValueData();
		List<DataCount> forecasts = lstmForecaster.generateForecast(zeroData, kpiId);

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());

		DataCount forecast = forecasts.get(0);
		assertEquals(0.0, (Double) forecast.getValue(), 1.0);
	}

	@Test
	public void testGenerateForecast_WithNegativeValues() {
		List<DataCount> negativeData = createNegativeValueData();
		List<DataCount> forecasts = lstmForecaster.generateForecast(negativeData, kpiId);

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());

		DataCount forecast = forecasts.get(0);
		assertTrue((Double) forecast.getValue() >= 0);
	}

	@Test
	public void testGenerateForecast_ForecastMetadata() {
		List<DataCount> forecasts = lstmForecaster.generateForecast(historicalData, kpiId);

		assertNotNull(forecasts);
		assertFalse(forecasts.isEmpty());

		DataCount forecast = forecasts.get(0);
		assertEquals("TestProject", forecast.getSProjectName());
		assertEquals("TestGroup", forecast.getKpiGroup());
		if (forecast.getHoverValue() != null) {
			assertEquals("LSTM", forecast.getHoverValue().get("Model"));
		}
	}

	private List<DataCount> createTestData() {
		List<DataCount> data = new ArrayList<>();
		double[] values = {5.0, 7.0, 6.0, 8.0, 9.0, 11.0, 10.0, 12.0};

		for (int i = 0; i < values.length; i++) {
			DataCount dataCount = new DataCount();
			dataCount.setValue(values[i]);
			dataCount.setSProjectName("TestProject");
			dataCount.setKpiGroup("TestGroup");
			dataCount.setDate(String.valueOf(2024010100 + i));
			data.add(dataCount);
		}
		return data;
	}

	private List<DataCount> createIdenticalValueData() {
		List<DataCount> data = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			DataCount dataCount = new DataCount();
			dataCount.setValue(10.0);
			dataCount.setSProjectName("TestProject");
			dataCount.setKpiGroup("TestGroup");
			dataCount.setDate(String.valueOf(2024010100 + i));
			data.add(dataCount);
		}
		return data;
	}

	private List<DataCount> createTrendingData() {
		List<DataCount> data = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			DataCount dataCount = new DataCount();
			dataCount.setValue(i + 1.0);
			dataCount.setSProjectName("TestProject");
			dataCount.setKpiGroup("TestGroup");
			dataCount.setDate(String.valueOf(2024010100 + i));
			data.add(dataCount);
		}
		return data;
	}

	private List<DataCount> createLargeValueData() {
		List<DataCount> data = new ArrayList<>();
		double[] values = {1000.0, 1100.0, 1050.0, 1200.0, 1150.0, 1300.0, 1250.0, 1400.0};

		for (int i = 0; i < values.length; i++) {
			DataCount dataCount = new DataCount();
			dataCount.setValue(values[i]);
			dataCount.setSProjectName("TestProject");
			dataCount.setKpiGroup("TestGroup");
			dataCount.setDate(String.valueOf(2024010100 + i));
			data.add(dataCount);
		}
		return data;
	}

	private List<DataCount> createZeroValueData() {
		List<DataCount> data = new ArrayList<>();
		for (int i = 0; i < 8; i++) {
			DataCount dataCount = new DataCount();
			dataCount.setValue(0.0);
			dataCount.setSProjectName("TestProject");
			dataCount.setKpiGroup("TestGroup");
			dataCount.setDate(String.valueOf(2024010100 + i));
			data.add(dataCount);
		}
		return data;
	}

	private List<DataCount> createNegativeValueData() {
		List<DataCount> data = new ArrayList<>();
		double[] values = {-5.0, -3.0, -7.0, -2.0, -4.0, -1.0, -6.0, -3.0};

		for (int i = 0; i < values.length; i++) {
			DataCount dataCount = new DataCount();
			dataCount.setValue(values[i]);
			dataCount.setSProjectName("TestProject");
			dataCount.setKpiGroup("TestGroup");
			dataCount.setDate(String.valueOf(2024010100 + i));
			data.add(dataCount);
		}
		return data;
	}
}
