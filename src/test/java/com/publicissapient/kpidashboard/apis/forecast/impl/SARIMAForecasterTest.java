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

@RunWith(MockitoJUnitRunner.class)
public class SARIMAForecasterTest {

	private SARIMAForecaster sarimaForecaster;

	@Before
	public void setUp() {
		sarimaForecaster = new SARIMAForecaster();
	}

	@Test
	public void testGetModelType() {
		assertEquals(ForecastingModel.SARIMA, sarimaForecaster.getModelType());
	}

	@Test
	public void testCanForecast_WithSufficientData() {
		List<DataCount> historicalData = createSeasonalData(12);
		assertTrue(sarimaForecaster.canForecast(historicalData, "kpi113"));
	}

	@Test
	public void testCanForecast_WithInsufficientData() {
		List<DataCount> historicalData = createSeasonalData(5);
		assertFalse(sarimaForecaster.canForecast(historicalData, "kpi113"));
	}

	@Test
	public void testGenerateForecast_WithSeasonalData() {
		// Create quarterly seasonal pattern
		List<DataCount> historicalData = createQuarterlySeasonalData();

		List<DataCount> forecasts = sarimaForecaster.generateForecast(historicalData, "kpi113");

		assertNotNull(forecasts);
		assertEquals(1, forecasts.size());

		DataCount forecast = forecasts.get(0);
		assertNotNull(forecast.getValue());
		assertTrue((Double) forecast.getValue() >= 0);
		assertEquals("SARIMA", forecast.getForecastingModel());
	}

	@Test
	public void testGenerateForecast_WithNonSeasonalData() {
		List<DataCount> historicalData = createNonSeasonalData(10);

		List<DataCount> forecasts = sarimaForecaster.generateForecast(historicalData, "kpi113");

		assertNotNull(forecasts);
		assertTrue(forecasts.size() <= 1);
	}

	private List<DataCount> createSeasonalData(int count) {
		List<DataCount> dataCounts = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			DataCount dc = new DataCount();
			dc.setValue(10.0 + i);
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			dataCounts.add(dc);
		}
		return dataCounts;
	}

	private List<DataCount> createQuarterlySeasonalData() {
		List<DataCount> dataCounts = new ArrayList<>();
		// Create 3 years of quarterly data with seasonal pattern
		double[] seasonalPattern = {15.0, 25.0, 20.0, 10.0}; // Q1, Q2, Q3, Q4

		for (int year = 0; year < 3; year++) {
			for (int quarter = 0; quarter < 4; quarter++) {
				DataCount dc = new DataCount();
				dc.setValue(seasonalPattern[quarter] + (year * 2)); // Slight yearly increase
				dc.setSProjectName("TestProject");
				dc.setKpiGroup("TestGroup");
				dataCounts.add(dc);
			}
		}
		return dataCounts;
	}

	private List<DataCount> createNonSeasonalData(int count) {
		List<DataCount> dataCounts = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			DataCount dc = new DataCount();
			dc.setValue(15.0 + Math.random() * 5); // Random variation around 15
			dc.setSProjectName("TestProject");
			dc.setKpiGroup("TestGroup");
			dataCounts.add(dc);
		}
		return dataCounts;
	}
}
