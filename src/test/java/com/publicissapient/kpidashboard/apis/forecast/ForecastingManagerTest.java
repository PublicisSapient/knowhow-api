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

package com.publicissapient.kpidashboard.apis.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import com.publicissapient.kpidashboard.apis.appsetting.service.ConfigHelperService;
import com.publicissapient.kpidashboard.apis.enums.ForecastingModel;
import com.publicissapient.kpidashboard.apis.forecast.service.ForecastService;
import com.publicissapient.kpidashboard.apis.model.IterationKpiValue;
import com.publicissapient.kpidashboard.common.model.application.DataCount;
import com.publicissapient.kpidashboard.common.model.application.DataCountGroup;
import com.publicissapient.kpidashboard.common.model.application.KpiMaster;

/** Test class for ForecastingManager. */
@RunWith(MockitoJUnitRunner.class)
public class ForecastingManagerTest {

	@Mock private ConfigHelperService configHelperService;

	@Mock private ForecastService linearRegressionForecaster;

	@Mock private ForecastService exponentialSmoothingForecaster;

	@Mock private ForecastService arimaForecaster;

	@InjectMocks private ForecastingManager forecastingManager;

	private List<ForecastService> forecastServices;

	@Before
	public void setUp() {
		forecastServices = new ArrayList<>();
		forecastServices.add(linearRegressionForecaster);
		forecastServices.add(exponentialSmoothingForecaster);
		forecastServices.add(arimaForecaster);

		when(linearRegressionForecaster.getModelType()).thenReturn(ForecastingModel.LINEAR_REGRESSION);
		when(exponentialSmoothingForecaster.getModelType())
				.thenReturn(ForecastingModel.EXPONENTIAL_SMOOTHING);
		when(arimaForecaster.getModelType()).thenReturn(ForecastingModel.ARIMA);

		forecastingManager = new ForecastingManager(configHelperService, forecastServices);
		forecastingManager.init();
	}

	@Test
	public void testGenerateForecasts_WithLinearRegressionModel() {
		// Arrange
		String kpiId = "kpi113";
		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId(kpiId);
		kpiMaster.setForecastModel("linearRegression");
		List<KpiMaster> testKpiMasterList = new ArrayList<>();
		testKpiMasterList.add(kpiMaster);

		List<DataCount> historicalData = createTestDataCounts(5);
		List<DataCount> expectedForecasts = createTestDataCounts(1);

		when(configHelperService.loadKpiMaster()).thenReturn(testKpiMasterList);
		when(linearRegressionForecaster.canForecast(any(), anyString())).thenReturn(true);
		when(linearRegressionForecaster.generateForecast(any(), anyString()))
				.thenReturn(expectedForecasts);

		// Act
		List<DataCount> result = forecastingManager.generateForecasts(historicalData, kpiId);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		verify(linearRegressionForecaster, times(1)).canForecast(historicalData, kpiId);
		verify(linearRegressionForecaster, times(1)).generateForecast(historicalData, kpiId);
	}

	@Test
	public void testGenerateForecasts_WithExponentialSmoothingModel() {
		// Arrange
		String kpiId = "kpi46";
		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId(kpiId);
		kpiMaster.setForecastModel("exponentialSmoothing");
		List<KpiMaster> testKpiMasterList = new ArrayList<>();
		testKpiMasterList.add(kpiMaster);

		List<DataCount> historicalData = createTestDataCounts(5);
		List<DataCount> expectedForecasts = createTestDataCounts(1);

		when(configHelperService.loadKpiMaster()).thenReturn(testKpiMasterList);
		when(exponentialSmoothingForecaster.canForecast(any(), anyString())).thenReturn(true);
		when(exponentialSmoothingForecaster.generateForecast(any(), anyString()))
				.thenReturn(expectedForecasts);

		// Act
		List<DataCount> result = forecastingManager.generateForecasts(historicalData, kpiId);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		verify(exponentialSmoothingForecaster, times(1)).generateForecast(historicalData, kpiId);
	}

	@Test
	public void testGenerateForecasts_WithNoForecastModel() {
		// Arrange
		String kpiId = "kpi999";
		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId(kpiId);
		kpiMaster.setForecastModel(null);
		List<KpiMaster> testKpiMasterList = new ArrayList<>();
		testKpiMasterList.add(kpiMaster);

		List<DataCount> historicalData = createTestDataCounts(5);

		when(configHelperService.loadKpiMaster()).thenReturn(testKpiMasterList);

		// Act
		List<DataCount> result = forecastingManager.generateForecasts(historicalData, kpiId);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
		verify(linearRegressionForecaster, never()).generateForecast(any(), anyString());
		verify(exponentialSmoothingForecaster, never()).generateForecast(any(), anyString());
	}

	@Test
	public void testGenerateForecasts_WithInvalidForecastModel() {
		// Arrange
		String kpiId = "kpi123";
		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId(kpiId);
		kpiMaster.setForecastModel("invalidModel");
		List<KpiMaster> testKpiMasterList = new ArrayList<>();
		testKpiMasterList.add(kpiMaster);

		List<DataCount> historicalData = createTestDataCounts(5);

		when(configHelperService.loadKpiMaster()).thenReturn(testKpiMasterList);

		// Act
		List<DataCount> result = forecastingManager.generateForecasts(historicalData, kpiId);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testGenerateForecasts_WithNullHistoricalData() {
		// Arrange
		String kpiId = "kpi113";

		// Act
		List<DataCount> result = forecastingManager.generateForecasts(null, kpiId);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testGenerateForecasts_WithEmptyHistoricalData() {
		// Arrange
		String kpiId = "kpi113";
		List<DataCount> historicalData = new ArrayList<>();

		// Act
		List<DataCount> result = forecastingManager.generateForecasts(historicalData, kpiId);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testGenerateForecasts_WhenCannotForecast() {
		// Arrange
		String kpiId = "kpi113";
		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId(kpiId);
		kpiMaster.setForecastModel("linearRegression");
		List<KpiMaster> testKpiMasterList = new ArrayList<>();
		testKpiMasterList.add(kpiMaster);

		List<DataCount> historicalData = createTestDataCounts(2); // Insufficient data

		when(configHelperService.loadKpiMaster()).thenReturn(testKpiMasterList);
		when(linearRegressionForecaster.canForecast(any(), anyString())).thenReturn(false);

		// Act
		List<DataCount> result = forecastingManager.generateForecasts(historicalData, kpiId);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
		verify(linearRegressionForecaster, never()).generateForecast(any(), anyString());
	}

	@Test
	public void testGenerateForecasts_WithException() {
		// Arrange
		String kpiId = "kpi113";
		List<DataCount> historicalData = createTestDataCounts(5);

		when(configHelperService.loadKpiMaster()).thenThrow(new RuntimeException("Test exception"));

		// Act
		List<DataCount> result = forecastingManager.generateForecasts(historicalData, kpiId);

		// Assert
		assertNotNull(result);
		assertTrue(result.isEmpty());
	}

	@Test
	public void testInit_WithNullServices() {
		// Arrange
		ForecastingManager manager = new ForecastingManager(configHelperService, null);

		// Act
		manager.init();

		// Assert - should not throw exception
	}

	@Test
	public void testAddForecastsToDataCount_WithConfiguredForecast() {
		// Arrange
		String kpiId = "kpi113";
		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId(kpiId);
		kpiMaster.setForecastModel("linearRegression");
		List<KpiMaster> testKpiMasterList = new ArrayList<>();
		testKpiMasterList.add(kpiMaster);

		DataCount dataCount = new DataCount("TestNode", "maturity", 100.0, createTestDataCounts(3));
		List<DataCount> historicalData = createTestDataCounts(5);
		List<DataCount> expectedForecasts = createTestDataCounts(1);

		when(configHelperService.loadKpiMaster()).thenReturn(testKpiMasterList);
		when(linearRegressionForecaster.canForecast(any(), anyString())).thenReturn(true);
		when(linearRegressionForecaster.generateForecast(any(), anyString()))
				.thenReturn(expectedForecasts);

		// Act
		forecastingManager.addForecastsToDataCount(dataCount, historicalData, kpiId);

		// Assert
		assertNotNull(dataCount.getForecasts());
		assertEquals(1, dataCount.getForecasts().size());
		verify(linearRegressionForecaster, times(1)).canForecast(historicalData, kpiId);
		verify(linearRegressionForecaster, times(1)).generateForecast(historicalData, kpiId);
	}

	@Test
	public void testAddForecastsToDataCount_ForIterationKPI() {
		// Arrange
		String kpiId = "kpi125";
		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId(kpiId);
		kpiMaster.setForecastModel("linearRegression");
		List<KpiMaster> testKpiMasterList = new ArrayList<>();
		testKpiMasterList.add(kpiMaster);

		IterationKpiValue dataCount = new IterationKpiValue();
		List<DataCount> historicalData = createTestDataCounts(5);
		List<DataCount> expectedForecasts = createTestDataCounts(1);

		when(configHelperService.loadKpiMaster()).thenReturn(testKpiMasterList);
		when(linearRegressionForecaster.canForecast(any(), anyString())).thenReturn(true);
		when(linearRegressionForecaster.generateForecast(any(), anyString()))
				.thenReturn(expectedForecasts);

		// Act
		forecastingManager.addForecastsToDataCount(dataCount, historicalData, kpiId);

		// Assert
		assertNotNull(dataCount.getForecasts());
		assertEquals(1, dataCount.getForecasts().size());
		verify(linearRegressionForecaster, times(1)).canForecast(historicalData, kpiId);
		verify(linearRegressionForecaster, times(1)).generateForecast(historicalData, kpiId);
	}

	@Test
	public void testAddForecastsToDataCount_WhenTypeIsNotSupportedClass() {
		// Arrange
		String kpiId = "kpi125";
		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId(kpiId);
		kpiMaster.setForecastModel("linearRegression");
		List<KpiMaster> testKpiMasterList = new ArrayList<>();
		testKpiMasterList.add(kpiMaster);

		DataCount dataCount = new DataCount();
		List<DataCount> historicalData = createTestDataCounts(-1);

		// Act
		forecastingManager.addForecastsToDataCount(dataCount, historicalData, kpiId);

		// Assert
		assertNull(dataCount.getForecasts());
	}

	@Test
	public void testAddForecastsToDataCount_WithNoForecastConfigured() {
		// Arrange
		String kpiId = "kpi999";
		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId(kpiId);
		kpiMaster.setForecastModel(null);
		List<KpiMaster> testKpiMasterList = new ArrayList<>();
		testKpiMasterList.add(kpiMaster);

		DataCount dataCount = new DataCount("TestNode", "maturity", 100.0, createTestDataCounts(3));
		List<DataCount> historicalData = createTestDataCounts(5);

		when(configHelperService.loadKpiMaster()).thenReturn(testKpiMasterList);

		// Act
		forecastingManager.addForecastsToDataCount(dataCount, historicalData, kpiId);

		// Assert
		assertTrue(dataCount.getForecasts() == null || dataCount.getForecasts().isEmpty());
	}

	@Test
	public void testAddForecastsToDataCount_WithNullDataCount() {
		// Arrange
		List<DataCount> historicalData = createTestDataCounts(5);

		// Act & Assert - should not throw exception
		forecastingManager.addForecastsToDataCount(null, historicalData, "kpi123");
	}

	@Test
	public void testGenerateForecasts_WithRefinementRejectionRate() {
		// Arrange
		String kpiId = "kpi139";
		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId(kpiId);
		kpiMaster.setForecastModel("exponentialSmoothing");
		List<KpiMaster> testKpiMasterList = new ArrayList<>();
		testKpiMasterList.add(kpiMaster);

		List<DataCount> historicalData = createTestDataCounts(5);
		List<DataCount> expectedForecasts = createTestDataCounts(1);
		expectedForecasts.get(0).setData("58.94");
		expectedForecasts.get(0).setValue(58.94);

		when(configHelperService.loadKpiMaster()).thenReturn(testKpiMasterList);
		when(exponentialSmoothingForecaster.canForecast(any(), anyString())).thenReturn(true);
		when(exponentialSmoothingForecaster.generateForecast(any(), anyString()))
				.thenReturn(expectedForecasts);

		// Act
		List<DataCount> result = forecastingManager.generateForecasts(historicalData, kpiId);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("58.94", result.get(0).getData());
		assertEquals(58.94, (Double) result.get(0).getValue(), 0.01);
		verify(exponentialSmoothingForecaster, times(1)).generateForecast(historicalData, kpiId);
	}

	@Test
	public void testGenerateForecasts_WithFlowEfficiency() {
		// Arrange
		String kpiId = "kpi170";
		KpiMaster kpiMaster = new KpiMaster();
		kpiMaster.setKpiId(kpiId);
		kpiMaster.setForecastModel("exponentialSmoothing");
		List<KpiMaster> testKpiMasterList = new ArrayList<>();
		testKpiMasterList.add(kpiMaster);

		List<DataCount> historicalData = createTestDataCounts(5);
		List<DataCount> expectedForecasts = createTestDataCounts(1);
		expectedForecasts.get(0).setData("75.2");
		expectedForecasts.get(0).setValue(75.2);

		when(configHelperService.loadKpiMaster()).thenReturn(testKpiMasterList);
		when(exponentialSmoothingForecaster.canForecast(any(), anyString())).thenReturn(true);
		when(exponentialSmoothingForecaster.generateForecast(any(), anyString()))
				.thenReturn(expectedForecasts);

		// Act
		List<DataCount> result = forecastingManager.generateForecasts(historicalData, kpiId);

		// Assert
		assertNotNull(result);
		assertEquals(1, result.size());
		assertEquals("75.2", result.get(0).getData());
		assertEquals(75.2, (Double) result.get(0).getValue(), 0.01);
		verify(exponentialSmoothingForecaster, times(1)).generateForecast(historicalData, kpiId);
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
