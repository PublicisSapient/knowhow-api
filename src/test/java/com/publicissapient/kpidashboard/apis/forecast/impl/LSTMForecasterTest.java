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

import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.common.model.application.DataCount;

public class LSTMForecasterTest {

	private LSTMForecaster forecaster;

	@Before
	public void setUp() {
		forecaster = new LSTMForecaster();
	}

	@Test
	public void testGetModelType() {
		assertEquals(ForecastingModel.LSTM, forecaster.getModelType());
	}

	@Test
	public void testCanForecast_WithSufficientData() {
		List<DataCount> data = createDataCounts(10);
		assertTrue(forecaster.canForecast(data, "kpi1"));
	}

	@Test
	public void testCanForecast_WithMinimumData() {
		List<DataCount> data = createDataCounts(6);
		assertTrue(forecaster.canForecast(data, "kpi1"));
	}

	@Test
	public void testCanForecast_WithInsufficientData() {
		List<DataCount> data = createDataCounts(5);
		assertFalse(forecaster.canForecast(data, "kpi1"));
	}

	@Test
	public void testCanForecast_WithNullData() {
		assertFalse(forecaster.canForecast(null, "kpi1"));
	}

	@Test
	public void testCanForecast_WithEmptyData() {
		assertFalse(forecaster.canForecast(new ArrayList<>(), "kpi1"));
	}

	@Test
	public void testGenerateForecast_WithPositiveTrend() {
		List<DataCount> data = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			data.add(createDataCount(100.0 + i * 10.0));
		}
		List<DataCount> result = forecaster.generateForecast(data, "kpi1");
		assertNotNull(result);
		assertEquals(1, result.size());
		assertTrue((Double) result.get(0).getValue() >= 0);
	}

	@Test
	public void testGenerateForecast_WithNegativeTrend() {
		List<DataCount> data = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			data.add(createDataCount(200.0 - i * 10.0));
		}
		List<DataCount> result = forecaster.generateForecast(data, "kpi1");
		assertNotNull(result);
		assertEquals(1, result.size());
		assertTrue((Double) result.get(0).getValue() >= 0);
	}

	@Test
	public void testGenerateForecast_WithInsufficientData() {
		List<DataCount> data = createDataCounts(5);
		List<DataCount> result = forecaster.generateForecast(data, "kpi1");
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testGenerateForecast_WithNullData() {
		List<DataCount> result = forecaster.generateForecast(null, "kpi1");
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testGenerateForecast_WithEmptyData() {
		List<DataCount> result = forecaster.generateForecast(new ArrayList<>(), "kpi1");
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testGenerateForecast_PreservesMetadata() {
		List<DataCount> data = createDataCounts(10);
		List<DataCount> result = forecaster.generateForecast(data, "kpi1");
		assertNotNull(result);
		assertFalse(result.isEmpty());
		assertEquals("TestProject", result.get(0).getSProjectName());
		assertEquals("TestGroup", result.get(0).getKpiGroup());
		assertEquals("lstm", result.get(0).getForecastingModel());
	}

	@Test
	public void testGenerateForecast_WithVolatileData() {
		List<DataCount> data = new ArrayList<>();
		double[] values = {100, 120, 95, 130, 110, 140, 105, 150, 115, 160};
		for (double value : values) {
			data.add(createDataCount(value));
		}
		List<DataCount> result = forecaster.generateForecast(data, "kpi1");
		assertNotNull(result);
		assertEquals(1, result.size());
		assertTrue((Double) result.get(0).getValue() >= 0);
	}

	private List<DataCount> createDataCounts(int count) {
		List<DataCount> data = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			data.add(createDataCount(i * 10.0));
		}
		return data;
	}

	private DataCount createDataCount(double value) {
		DataCount dc = new DataCount();
		dc.setValue(value);
		dc.setSProjectName("TestProject");
		dc.setKpiGroup("TestGroup");
		return dc;
	}
}
